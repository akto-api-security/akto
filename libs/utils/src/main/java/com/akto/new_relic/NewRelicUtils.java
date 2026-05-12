package com.akto.new_relic;

import com.akto.dao.NewRelicIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.billing.Organization;
import com.akto.dto.metrics.MetricData;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.monitoring.ModuleInfo.ModuleType;
import com.akto.dto.new_relic_integration.NewRelicIntegration;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.utils.AccountUtils;
import com.akto.utils.OrganizationUtils;
import com.mongodb.BasicDBObject;
import com.newrelic.telemetry.Attributes;
import com.newrelic.telemetry.OkHttpPoster;
import com.newrelic.telemetry.TelemetryClient;
import com.newrelic.telemetry.events.Event;
import com.newrelic.telemetry.events.EventBatch;
import com.newrelic.telemetry.metrics.Count;
import com.newrelic.telemetry.metrics.Gauge;
import com.newrelic.telemetry.metrics.Metric;
import com.newrelic.telemetry.metrics.MetricBuffer;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
public class NewRelicUtils {

    private static final LoggerMaker loggerMaker = new LoggerMaker(NewRelicUtils.class, LogDb.DB_ABS);

    private static final ConcurrentHashMap<Integer, TelemetryClient> clientCache = new ConcurrentHashMap<>();

    private NewRelicUtils() {}

    public static Attributes getIdAttributes() {
        Attributes idAttrs = new Attributes();
        try {
            int accountId = Context.accountId.get();
            Account account = AccountUtils.fetchAccount(accountId);
            Organization organization = OrganizationUtils.fetchOrganization(accountId);

            String accountName = account != null && account.getName() != null ? account.getName() : "";
            String organizationId = organization != null && organization.getId() != null ? organization.getId() : "";
            String organizationName = organization != null && organization.getName() != null ? organization.getName() : "";

            idAttrs
                .put("akto.account.id", accountId)
                .put("akto.account.name", accountName)
                .put("akto.organization.id", organizationId)
                .put("akto.organization.name", organizationName);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Failed to get ID attributes for current context");
        }
        return idAttrs;
    }

    public static Attributes getModuleAttributes(ModuleInfo moduleInfo) {
        Attributes moduleAttrs = new Attributes();
        if (moduleInfo != null) {
            String moduleId = moduleInfo.getId() != null ? moduleInfo.getId() : "";
            String moduleName = moduleInfo.getName() != null ? moduleInfo.getName() : "";
            String moduleType = moduleInfo.getModuleType() != null ? moduleInfo.getModuleType().name() : "";
            String currentVersion = moduleInfo.getCurrentVersion() != null ? moduleInfo.getCurrentVersion() : "";
            int startedTs = moduleInfo.getStartedTs();
            int lastHeartbeatReceived = moduleInfo.getLastHeartbeatReceived();

            moduleAttrs
                .put("akto.module.id", moduleId)
                .put("akto.module.type", moduleType)
                .put("akto.module.name", moduleName)
                .put("akto.module.currentVersion", currentVersion)
                .put("akto.module.startedTs", startedTs)
                .put("akto.module.lastHeartbeatReceived", lastHeartbeatReceived);
        }
        return moduleAttrs;
    }

    public static String formatMetricName(String name) {
        if (name == null || name.isEmpty()) {
            return "akto.metric.unknown";
        }
        return "akto.metric." + name.toLowerCase();
    }

     public static TelemetryClient initClientForAccount() throws Exception {
        int accountId = Context.accountId.get();

        TelemetryClient existing = clientCache.get(accountId);
        if (existing != null) return existing;

        NewRelicIntegration nri = NewRelicIntegrationDao.instance.findOne(new BasicDBObject());
        if (nri == null || nri.getApiKey() == null || nri.getApiKey().isEmpty()) {
            throw new Exception("No New Relic integration found.");
        }
        TelemetryClient newClient = TelemetryClient.create(
            () -> new OkHttpPoster(Duration.of(10, ChronoUnit.SECONDS)), nri.getApiKey());

        TelemetryClient winner = clientCache.putIfAbsent(accountId, newClient);
        return winner != null ? winner : newClient;
    }

    public static void forwardMetrics(List<MetricData> mdList) {
        if (mdList == null || mdList.isEmpty()) {
            return;
        }

        TelemetryClient client;
        try {
            client = NewRelicUtils.initClientForAccount();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, String.format("Failed to initialize New Relic client for account %d: %s", Context.accountId.get(), e.getMessage()));
            return;
        }

        try {
            Attributes idAttrs = NewRelicUtils.getIdAttributes();

            List<Metric> newRelicMetrics = new ArrayList<>();

            long endMs = System.currentTimeMillis();
            long startMs = endMs - 120_000L; // 120s flush window matches AllMetrics schedule in modules

            for (MetricData md : mdList) {
                if (md.getModuleType() != null && md.getModuleType().equals(ModuleType.TRAFFIC_COLLECTOR.toString())) {
                    continue; // todo: handle traffic collector metrics
                }

                long tsMs = (long) md.getTimestamp() * 1000L;

                ModuleInfo moduleInfo = md.getModuleInfo();
                Attributes moduleAttrs = NewRelicUtils.getModuleAttributes(moduleInfo);

                Attributes metricAttrs = new Attributes();
                metricAttrs.putAll(moduleAttrs);
                metricAttrs.putAll(idAttrs);

                String metricName = formatMetricName(md.getMetricId());

                Metric newRelicMetric;
                if (md.getMetricType() == MetricData.MetricType.SUM) {
                    newRelicMetric = new Count(metricName, md.getValue(), startMs, endMs, metricAttrs);
                } else {
                    newRelicMetric = new Gauge(metricName, md.getValue(), tsMs, metricAttrs);
                }
                newRelicMetrics.add(newRelicMetric);
            }

            NewRelicUtils.sendBatch(client, newRelicMetrics);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Failed to forward metrics to New Relic");
        }
    }


    public static void forwardModuleHeartbeatEvent(ModuleInfo moduleInfo) {
        if (moduleInfo == null) {
            return;
        }

        TelemetryClient client;
        try {
            client = NewRelicUtils.initClientForAccount();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, String.format("Failed to initialize New Relic client for account %d: %s", Context.accountId.get(), e.getMessage()));
            return;
        }

        try {
            long now = System.currentTimeMillis();

            Attributes idAttrs = NewRelicUtils.getIdAttributes();
            Attributes moduleAttrs = NewRelicUtils.getModuleAttributes(moduleInfo);

            Attributes eventAttrs = new Attributes();
            eventAttrs.putAll(idAttrs);
            eventAttrs.putAll(moduleAttrs);

            Event event = new Event("AktoModuleHeartbeatReceived", eventAttrs, now);
            client.sendBatch(new EventBatch(Collections.singletonList(event)));
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Failed to forward module heartbeat received event for moduleId=" + moduleInfo.getId());
        }
    }

    public static void sendBatch(TelemetryClient client, List<Metric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return;
        }
        try {
            MetricBuffer buffer = buildMetricBuffer();
            for (Metric metric : metrics) {
                buffer.addMetric(metric);
            }

            loggerMaker.info(String.format("Forwarding %d metrics to New Relic", metrics.size()));
            client.sendBatch(buffer.createBatch());
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Failed to send metric batch of size " + metrics.size());
        }
    }

    private static MetricBuffer buildMetricBuffer() {
        MetricBuffer.Builder builder = MetricBuffer.builder()
                .instrumentationProvider("akto-telemetry");
        return builder.build();
    }
}
