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

import java.time.Duration;
import java.util.List;
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

        String endpoint = openTelemetryIntegration.getEndpoint();
        String apiKey = openTelemetryIntegration.getApiKey();

        Resource resource = Resource.getDefault().toBuilder()
                .put(AttributeKey.stringKey("service.name"), "akto-telemetry")
                .build();

        OtlpHttpMetricExporterBuilder metricExporterBuilder = OtlpHttpMetricExporter.builder()
                .setEndpoint(endpoint + "/v1/metrics")
                .setTimeout(Duration.ofSeconds(10));
        if (apiKey != null && !apiKey.isEmpty()) {
            metricExporterBuilder.addHeader("api-key", apiKey);
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
            logExporterBuilder.addHeader("api-key", apiKey);
        }

        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .setResource(resource)
                .addLogRecordProcessor(
                        BatchLogRecordProcessor.builder(logExporterBuilder.build()).build())
                .build();

        OpenTelemetryClient newClient = new OpenTelemetryClient(meterProvider, loggerProvider);
        OpenTelemetryClient winner = clientCache.putIfAbsent(accountId, newClient);
        return winner != null ? winner : newClient;
    }

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

        try {
            Meter meter = client.meterProvider.get("akto-telemetry");
            Attributes idAttrs = OpenTelemetryUtils.getIdAttributes();

            loggerMaker.info("Forwarding " + mdList.size() + " metrics to Open Telemetry endpoint for account " + Context.accountId.get());
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
            loggerMaker.errorAndAddToDb(e, String.format("Failed to forward metrics to Open Telemetry endpoint for account %d: %s", Context.accountId.get(), e.getMessage()));
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
