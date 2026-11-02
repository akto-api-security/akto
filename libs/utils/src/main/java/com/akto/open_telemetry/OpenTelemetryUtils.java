package com.akto.open_telemetry;

import com.akto.dao.OpenTelemetryIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.OpenTelemetryIntegration;
import com.akto.dto.billing.Organization;
import com.akto.dto.metrics.MetricData;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.monitoring.ModuleInfo.ModuleType;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.utils.AccountUtils;
import com.akto.utils.OrganizationUtils;
import com.mongodb.BasicDBObject;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.metrics.DoubleGauge;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporterBuilder;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporterBuilder;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class OpenTelemetryUtils {

    private static final LoggerMaker loggerMaker = new LoggerMaker(OpenTelemetryUtils.class, LogDb.DB_ABS);

    private static final ConcurrentHashMap<Integer, OpenTelemetryClient> clientCache = new ConcurrentHashMap<>();

    private OpenTelemetryUtils() {}

    public static class OpenTelemetryClient {
        public final SdkMeterProvider meterProvider;
        public final SdkLoggerProvider loggerProvider;

        public OpenTelemetryClient(SdkMeterProvider meterProvider, SdkLoggerProvider loggerProvider) {
            this.meterProvider = meterProvider;
            this.loggerProvider = loggerProvider;
        }
    }

    public static Attributes getIdAttributes() {
        try {
            int accountId = Context.accountId.get();
            Account account = AccountUtils.fetchAccount(accountId);
            Organization organization = OrganizationUtils.fetchOrganization(accountId);

            String accountName = account != null && account.getName() != null ? account.getName() : "";
            String organizationId = organization != null && organization.getId() != null ? organization.getId() : "";
            String organizationName = organization != null && organization.getName() != null ? organization.getName() : "";

            return Attributes.builder()
                    .put(AttributeKey.longKey("akto.account.id"), (long) accountId)
                    .put(AttributeKey.stringKey("akto.account.name"), accountName)
                    .put(AttributeKey.stringKey("akto.organization.id"), organizationId)
                    .put(AttributeKey.stringKey("akto.organization.name"), organizationName)
                    .build();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Failed to get ID attributes for current context");
            return Attributes.empty();
        }
    }

    public static Attributes getModuleAttributes(ModuleInfo moduleInfo) {
        if (moduleInfo == null) {
            return Attributes.empty();
        }

        String moduleId = moduleInfo.getId() != null ? moduleInfo.getId() : "";
        String moduleName = moduleInfo.getName() != null ? moduleInfo.getName() : "";
        String moduleType = moduleInfo.getModuleType() != null ? moduleInfo.getModuleType().name() : "";
        String currentVersion = moduleInfo.getCurrentVersion() != null ? moduleInfo.getCurrentVersion() : "";
        int startedTs = moduleInfo.getStartedTs();
        int lastHeartbeatReceived = moduleInfo.getLastHeartbeatReceived();

        return Attributes.builder()
                .put(AttributeKey.stringKey("akto.module.id"), moduleId)
                .put(AttributeKey.stringKey("akto.module.type"), moduleType)
                .put(AttributeKey.stringKey("akto.module.name"), moduleName)
                .put(AttributeKey.stringKey("akto.module.currentVersion"), currentVersion)
                .put(AttributeKey.longKey("akto.module.startedTs"), (long) startedTs)
                .put(AttributeKey.longKey("akto.module.lastHeartbeatReceived"), (long) lastHeartbeatReceived)
                .build();
    }

    public static String formatMetricName(String name) {
        if (name == null || name.isEmpty()) {
            return "akto.metric.unknown";
        }
        return "akto.metric." + name.toLowerCase();
    }

    public static OpenTelemetryClient initClientForAccount() throws Exception {
        int accountId = Context.accountId.get();

        OpenTelemetryClient existing = clientCache.get(accountId);
        if (existing != null) return existing;

        OpenTelemetryIntegration openTelemetryIntegration = OpenTelemetryIntegrationDao.instance.findOne(new BasicDBObject());
        if (openTelemetryIntegration == null || openTelemetryIntegration.getEndpoint() == null || openTelemetryIntegration.getEndpoint().isEmpty()) {
            throw new Exception("No OpenTelemetry integration found.");
        }

        OpenTelemetryClient newClient = buildClient(
                openTelemetryIntegration.getEndpoint(),
                openTelemetryIntegration.getApiKey(),
                openTelemetryIntegration.getHeaderName());
        OpenTelemetryClient winner = clientCache.putIfAbsent(accountId, newClient);
        return winner != null ? winner : newClient;
    }

    // Builds an OTLP (metrics + logs) client for a given endpoint/auth.
    // Shared by the customer integration (initClientForAccount) and Akto's
    // own collector (initAktoInfraClient) so the mapping logic isn't duplicated.
    private static OpenTelemetryClient buildClient(String endpoint, String apiKey, String headerName) {
        if (apiKey != null && !apiKey.isEmpty()) {
            try {
                apiKey = URLDecoder.decode(apiKey, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                // UTF-8 is always supported
            }
        }

        Resource resource = Resource.getDefault().toBuilder()
                .put(AttributeKey.stringKey("service.name"), "akto-telemetry")
                .build();

        OtlpHttpMetricExporterBuilder metricExporterBuilder = OtlpHttpMetricExporter.builder()
                .setEndpoint(endpoint + "/v1/metrics")
                .setTimeout(Duration.ofSeconds(10));
        if (apiKey != null && !apiKey.isEmpty()) {
            metricExporterBuilder.addHeader(headerName, apiKey);
        }

        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .setResource(resource)
                .registerMetricReader(
                        PeriodicMetricReader.builder(metricExporterBuilder.build())
                                .setInterval(Duration.ofSeconds(60))
                                .build())
                .build();

        OtlpHttpLogRecordExporterBuilder logExporterBuilder = OtlpHttpLogRecordExporter.builder()
                .setEndpoint(endpoint + "/v1/logs")
                .setTimeout(Duration.ofSeconds(10));
        if (apiKey != null && !apiKey.isEmpty()) {
            logExporterBuilder.addHeader(headerName, apiKey);
        }

        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .setResource(resource)
                .addLogRecordProcessor(
                        BatchLogRecordProcessor.builder(logExporterBuilder.build()).build())
                .build();

        return new OpenTelemetryClient(meterProvider, loggerProvider);
    }

    // Akto's OWN collector — an always-on forward, independent of the
    // customer-facing OpenTelemetryIntegration. Enabled only when configured
    // via environment on the deployment:
    //   AKTO_INFRA_OTEL_ENDPOINT  OTLP/HTTP base URL, e.g. http://10.2.32.15:4318
    //   AKTO_INFRA_OTEL_TOKEN     bearer token the collector enforces
    // Returns null (no-op) when not configured. Single global client (one endpoint).
    private static volatile OpenTelemetryClient aktoInfraClient;

    public static OpenTelemetryClient initAktoInfraClient() {
        OpenTelemetryClient existing = aktoInfraClient;
        if (existing != null) return existing;

        String endpoint = System.getenv("AKTO_INFRA_OTEL_ENDPOINT");
        String token = System.getenv("AKTO_INFRA_OTEL_TOKEN");
        if (endpoint == null || endpoint.isEmpty() || token == null || token.isEmpty()) {
            return null;
        }

        synchronized (OpenTelemetryUtils.class) {
            if (aktoInfraClient == null) {
                aktoInfraClient = buildClient(endpoint, "Bearer " + token, "Authorization");
            }
            return aktoInfraClient;
        }
    }

    // OTEL infra-push config, parsed ONCE from env (env is immutable for the
    // process's lifetime, so there's nothing to re-read). A change needs a restart.
    private static volatile boolean otelInfraPushConfigInitialized = false;
    private static boolean otelInfraPushEnabled = false;      // overall flag AND endpoint present
    private static boolean otelInfraPushAllAccounts = false;  // "*" / "all"
    private static Set<Integer> otelInfraPushAccounts = Collections.emptySet();

    private static void initOtelInfraPushConfig() {
        if (otelInfraPushConfigInitialized) return;
        synchronized (OpenTelemetryUtils.class) {
            if (otelInfraPushConfigInitialized) return;

            boolean enabled = "true".equalsIgnoreCase(System.getenv("AKTO_INFRA_OTEL_ENABLED"));
            String endpoint = System.getenv("AKTO_INFRA_OTEL_ENDPOINT");
            boolean hasEndpoint = endpoint != null && !endpoint.isEmpty();

            boolean all = false;
            Set<Integer> set = new HashSet<>();
            String accounts = System.getenv("AKTO_INFRA_OTEL_ACCOUNTS");
            if (accounts != null && !accounts.trim().isEmpty()) {
                accounts = accounts.trim();
                if (accounts.equals("*") || accounts.equalsIgnoreCase("all")) {
                    all = true;
                } else {
                    for (String a : accounts.split(",")) {
                        try {
                            set.add(Integer.parseInt(a.trim()));
                        } catch (NumberFormatException ignore) {
                            // skip malformed ids
                        }
                    }
                }
            }

            otelInfraPushEnabled = enabled && hasEndpoint;
            otelInfraPushAllAccounts = all;
            otelInfraPushAccounts = set;
            otelInfraPushConfigInitialized = true;
        }
    }

    // Single decision point for whether to push metrics to Akto's OTEL collector.
    // Reads pre-parsed cached config (O(1) set lookup) — no getenv/split per call.
    // Controlled via env (parsed once at first call):
    //   AKTO_INFRA_OTEL_ENABLED   "true" to arm the feature (kill switch)
    //   AKTO_INFRA_OTEL_ENDPOINT  must be set (nowhere to send otherwise)
    //   AKTO_INFRA_OTEL_ACCOUNTS  comma-separated account IDs, or "*"/"all".
    //                             Empty/unset => no account pushes yet.
    public static boolean shouldForwardToAktoInfra(int accountId) {
        initOtelInfraPushConfig();
        if (!otelInfraPushEnabled) return false;
        if (otelInfraPushAllAccounts) return true;
        return otelInfraPushAccounts.contains(accountId);
    }

    // Customer-facing forward — ships metrics to the customer's own endpoint
    // (configured via OpenTelemetryIntegration in Mongo).
    public static void forwardMetrics(List<MetricData> mdList) {
        if (mdList == null || mdList.isEmpty()) {
            return;
        }

        OpenTelemetryClient client;
        try {
            client = OpenTelemetryUtils.initClientForAccount();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, String.format("Failed to initialize Open Telemetry client for account %d: %s", Context.accountId.get(), e.getMessage()));
            return;
        }
        forward(client, mdList, "customer OpenTelemetry endpoint");
    }

    // Akto-infra forward — ships the same metrics to Akto's own collector
    // (configured via AKTO_INFRA_OTEL_* env). No-op if not configured.
    public static void forwardMetricsToAktoInfra(List<MetricData> mdList) {
        if (mdList == null || mdList.isEmpty()) {
            return;
        }

        OpenTelemetryClient client = OpenTelemetryUtils.initAktoInfraClient();
        if (client == null) {
            return;
        }
        forward(client, mdList, "Akto infra collector");
    }

    // Shared mapping loop: MetricData -> OTLP (SUM->counter, else gauge),
    // with account/org/module attributes. Used by both forwards above.
    private static void forward(OpenTelemetryClient client, List<MetricData> mdList, String target) {
        try {
            Meter meter = client.meterProvider.get("akto-telemetry");
            Attributes idAttrs = OpenTelemetryUtils.getIdAttributes();

            loggerMaker.info("Forwarding " + mdList.size() + " metrics to " + target + " for account " + Context.accountId.get());
            for (MetricData md : mdList) {
                if (md.getModuleType() != null && md.getModuleType().equals(ModuleType.TRAFFIC_COLLECTOR.toString())) {
                    continue; // todo: handle traffic collector metrics
                }

                ModuleInfo moduleInfo = md.getModuleInfo();
                Attributes moduleAttrs = OpenTelemetryUtils.getModuleAttributes(moduleInfo);

                Attributes metricAttrs = Attributes.builder()
                        .putAll(moduleAttrs)
                        .putAll(idAttrs)
                        .build();

                String metricName = formatMetricName(md.getMetricId());

                if (md.getMetricType() == MetricData.MetricType.SUM) {
                    LongCounter counter = meter.counterBuilder(metricName).build();
                    counter.add((long) md.getValue(), metricAttrs);
                } else {
                    DoubleGauge gauge = meter.gaugeBuilder(metricName).build();
                    gauge.set(md.getValue(), metricAttrs);
                }
            }

            client.meterProvider.forceFlush();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, String.format("Failed to forward metrics to %s for account %d: %s", target, Context.accountId.get(), e.getMessage()));
        }
    }

    public static void forwardModuleHeartbeatEvent(ModuleInfo moduleInfo) {
        if (moduleInfo == null) {
            return;
        }

        OpenTelemetryClient client;
        try {
            client = OpenTelemetryUtils.initClientForAccount();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, String.format("Failed to initialize OpenTelemetry client for account %d: %s", Context.accountId.get(), e.getMessage()));
            return;
        }

        try {
            Attributes idAttrs = OpenTelemetryUtils.getIdAttributes();
            Attributes moduleAttrs = OpenTelemetryUtils.getModuleAttributes(moduleInfo);

            Attributes eventAttrs = Attributes.builder()
                    .putAll(idAttrs)
                    .putAll(moduleAttrs)
                    .build();

            Logger otelLogger = client.loggerProvider.get("akto-telemetry");
            otelLogger.logRecordBuilder()
                    .setEventName("AktoModuleHeartbeatReceived")
                    .setTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                    .setAllAttributes(eventAttrs)
                    .setSeverity(Severity.INFO)
                    .emit();

            client.loggerProvider.forceFlush();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Failed to forward module heartbeat event for moduleId=" + moduleInfo.getId());
        }
    }
}
