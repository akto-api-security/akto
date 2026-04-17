package com.akto.new_relic;

import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.billing.Organization;
import com.akto.dto.metrics.MetricData;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.monitoring.ModuleInfo.ModuleType;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;
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

    private static volatile TelemetryClient telemetryClient;
    private static volatile Attributes commonAttributes;

    private static final ConcurrentHashMap<Integer, Organization> orgCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Account> accountCache = new ConcurrentHashMap<>();

    private NewRelicUtils() {}

    /**
     * Returns the Organization for the given accountId.
     * Serves from cache if already fetched; otherwise queries and caches the result.
     * Returns null if not found or on error.
     */
    public static Organization fetchOrganization(int accountId) {
        Organization cached = orgCache.get(accountId);
        if (cached != null) {
            return cached;
        }
        try {
            Organization org = OrganizationsDao.instance.findOne(Filters.in(Organization.ACCOUNTS, accountId));
            if (org != null) {
                orgCache.put(accountId, org);
            }
            return org;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Failed to fetch organization for accountId=" + accountId);
            return null;
        }
    }

    /**
     * Returns the Account for the given accountId.
     * Serves from cache if already fetched; otherwise queries Context.getAccount() and caches the result.
     * Returns null if not found or on error.
     */
    public static Account fetchAccount(int accountId) {
        Account cached = accountCache.get(accountId);
        if (cached != null) {
            return cached;
        }
        try {
            Context.accountId.set(accountId);
            Account account = Context.getAccount();
            if (account != null) {
                accountCache.put(accountId, account);
            }
            return account;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Failed to fetch account for accountId=" + accountId);
            return null;
        }
    }

    public static synchronized void init(String apiKey, String serviceName, String environment) {
        if (telemetryClient != null) {
            return;
        }
        try {
            telemetryClient = TelemetryClient.create(
                    () -> new OkHttpPoster(Duration.of(10, ChronoUnit.SECONDS)), apiKey);
            commonAttributes = new Attributes()
                    .put("service.name", serviceName)
                    .put("environment", environment)
                    .put("mode", "forwarder");
            loggerMaker.infoAndAddToDb("New Relic telemetry client initialized for service=" + serviceName + ", environment=" + environment);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Failed to initialize New Relic telemetry client");
        }
    }

    public static Attributes getIdAttributes() {
        Attributes idAttrs = new Attributes();
        try {
            int accountId = Context.accountId.get();
            Account account = fetchAccount(accountId);
            Organization organization = fetchOrganization(accountId);

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

    public static void forwardMetrics(List<MetricData> mdList) {
        if (!isInitialized() || mdList == null || mdList.isEmpty()) {
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

            NewRelicUtils.sendBatch(newRelicMetrics);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Failed to forward metrics to New Relic");
        }
    }


    public static void forwardModuleHeartbeatEvent(ModuleInfo moduleInfo) {
        if (!isInitialized() || moduleInfo == null) {
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
            telemetryClient.sendBatch(new EventBatch(Collections.singletonList(event)));
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Failed to forward module heartbeat received event for moduleId=" + moduleInfo.getId());
        }
    }

    public static void sendBatch(List<Metric> metrics) {
        if (!isInitialized() || metrics == null || metrics.isEmpty()) {
            return;
        }
        try {
            MetricBuffer buffer = buildMetricBuffer();
            for (Metric metric : metrics) {
                buffer.addMetric(metric);
            }

            loggerMaker.info(String.format("Forwarding %d metrics to New Relic", metrics.size()));
            telemetryClient.sendBatch(buffer.createBatch());
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Failed to send metric batch of size " + metrics.size());
        }
    }

    private static boolean isInitialized() {
        if (telemetryClient == null) {
            loggerMaker.info("New Relic telemetry client is not initialized. Call NewRelicUtils.init() first.");
            return false;
        }
        return true;
    }

    private static MetricBuffer buildMetricBuffer() {
        MetricBuffer.Builder builder = MetricBuffer.builder()
                .instrumentationProvider("akto-telemetry");
        if (commonAttributes != null) {
            builder.attributes(commonAttributes);
        }
        return builder.build();
    }
}
