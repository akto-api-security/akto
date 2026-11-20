package com.akto.util;

import com.akto.billing.UsageMetricUtils;
import com.akto.dao.billing.OrganizationUsageDao;
import com.akto.dao.context.Context;
import com.akto.dao.usage.UsageMetricsDao;
import com.akto.dto.Config;
import com.akto.dto.billing.Organization;
import com.akto.dto.billing.OrganizationFlags;
import com.akto.dto.billing.OrganizationUsage;
import com.akto.dto.usage.MetricTypes;
import com.akto.dto.usage.UsageMetric;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.notifications.email.SendgridEmail;
import com.akto.stigg.StiggReporterClient;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import org.bson.conversions.Bson;

import java.io.IOException;
import java.util.*;

import static com.akto.dto.billing.OrganizationUsage.*;

public class UsageCalculator {
    private static final LoggerMaker loggerMaker = new LoggerMaker(UsageCalculator.class, LogDb.BILLING);
    public static final UsageCalculator instance = new UsageCalculator();

    private UsageCalculator() {}

    boolean statusDataSinks = false;
    String currentCalcOrg = "";

    boolean statusAggregateUsage = false;

    public void sendOrgUsageDataToAllSinks(Organization o, boolean justRun) {
        boolean differentOrg = o != null && o.getId()!=null ? !currentCalcOrg.equals(o.getId()) : true;
        justRun &= differentOrg;
        if (statusDataSinks && !justRun) {
            throw new IllegalStateException("Data sinks already going on. Returning...");
        }
        try {
            if(o == null){
                return;
            }
            currentCalcOrg = o.getId();
            statusDataSinks = true;
            Bson filterQ =
                    Filters.and(
                            Filters.eq(ORG_ID, o.getId()),
                            Filters.or(Filters.eq(SINKS, new BasicDBObject()), Filters.exists(SINKS, false))
                    );
            List<OrganizationUsage> pendingUsages =
                    OrganizationUsageDao.instance.findAll(filterQ);

            if(pendingUsages.isEmpty()) {
                loggerMaker.infoAndAddToDb("No pending items for org: " + o.getId(), LoggerMaker.LogDb.BILLING);
                return;
            }
            loggerMaker.infoAndAddToDb("Found "+pendingUsages.size()+" items for org: " + o.getId(), LoggerMaker.LogDb.BILLING);

            pendingUsages.sort(
                    (o1, o2)->
                            o2.getDate() == o1.getDate() ?
                                    (o2.getCreationEpoch() - o1.getCreationEpoch()) :
                                    (o2.getDate() - o1.getDate()));

            OrganizationUsage lastUsageItem = pendingUsages.get(0);
            loggerMaker.infoAndAddToDb("Shortlisting: " + lastUsageItem, LoggerMaker.LogDb.BILLING);


            syncBillingEodWithStigg(lastUsageItem);


            Bson ignoreFilterQ = Filters.and(filterQ, Filters.ne(OrganizationUsage.CREATION_EPOCH, lastUsageItem.getCreationEpoch()));
            Bson ignoreUpdateQ = Updates.set(SINKS, new BasicDBObject("ignore", Context.now()));
            UpdateResult updateResult = OrganizationUsageDao.instance.updateMany(ignoreFilterQ, ignoreUpdateQ);
            loggerMaker.infoAndAddToDb("marked "+updateResult.getModifiedCount()+" items as ignored", LoggerMaker.LogDb.BILLING);
        } finally {
            statusDataSinks = false;
            currentCalcOrg = "";
        }

    }

    private UsageMetric getUsageMetricObj(OrganizationUsage ou, MetricTypes metricTypes, int usage) {
        UsageMetric usageMetric = new UsageMetric(
                ou.getOrgId(),
                -1,
                metricTypes,
                ou.getCreationEpoch(),
                Context.now(),
                "SAAS",
                "billing");
        usageMetric.setUsage(usage);
        return usageMetric;
    }

    /*
     * Stigg feature id for a metric, null if it has none (not configured, or not a reported metric).
     * The retired MCP_ASSET_COUNT is deliberately not mapped: it is never reported, even if
     * a usage document written before the retirement still carries it.
     */
    static String featureIdFor(MetricTypes metricType, Config.StiggConfig stiggConfig) {
        if (stiggConfig == null) {
            return null;
        }
        switch (metricType) {
            case ACTIVE_ENDPOINTS:
                return stiggConfig.getActiveEndpointsLabel();
            case ACTIVE_ACCOUNTS:
                return stiggConfig.getActiveAccountsLabel();
            case TEST_RUNS:
                return stiggConfig.getTestRunsLabel();
            case CUSTOM_TESTS:
                return stiggConfig.getCustomTestsLabel();
            case AI_ASSET_COUNT:
                return stiggConfig.getAiAssetsLabel();
            case ENDPOINT_ASSET_COUNT:
                return stiggConfig.getEndpointAssetsLabel();
            default:
                return null;
        }
    }

    static final class StiggReport {
        final BasicDBList updateList = new BasicDBList();
        final List<UsageMetric> usageMetrics = new ArrayList<>();
        final List<String> skipped = new ArrayList<>();
    }

    /*
     * One metric that cannot be reported (unknown to this build, or no Stigg feature id configured)
     * is skipped and listed, it does not stop the org's other metrics from being reported.
     */
    StiggReport buildStiggReport(OrganizationUsage lastUsageItem, Config.StiggConfig stiggConfig) {
        StiggReport report = new StiggReport();

        for(Map.Entry<String, Integer> entry: lastUsageItem.getOrgMetricMap().entrySet()) {
            MetricTypes metricType;
            try {
                metricType = MetricTypes.valueOf(entry.getKey());
            } catch (IllegalArgumentException e) {
                loggerMaker.errorAndAddToDb("Unknown metric type, not reported: " + entry.getKey() + " org: " + lastUsageItem.getOrgId(), LoggerMaker.LogDb.BILLING);
                report.skipped.add(entry.getKey());
                continue;
            }

            String featureId = featureIdFor(metricType, stiggConfig);
            if (featureId == null) {
                loggerMaker.errorAndAddToDb("Feature id not found for metric: " + metricType + ", not reported. org: " + lastUsageItem.getOrgId(), LoggerMaker.LogDb.BILLING);
                report.skipped.add(entry.getKey());
                continue;
            }

            int value = entry.getValue();

            BasicDBObject updateObject = StiggReporterClient.instance.getUpdateObject(value, lastUsageItem.getOrgId(), featureId);
            report.updateList.add(updateObject);
            report.usageMetrics.add(getUsageMetricObj(lastUsageItem, metricType, value));
        }

        return report;
    }

    private void syncBillingEodWithStigg(OrganizationUsage lastUsageItem) {
        loggerMaker.infoAndAddToDb(String.format("Syncing usage for organization %s to Stigg", lastUsageItem.getOrgId()), LoggerMaker.LogDb.BILLING);
        Map<String, Integer> sinks = lastUsageItem.getSinks();

        if (sinks == null) {
            sinks = new HashMap<>();
        }

        StiggReport report = buildStiggReport(lastUsageItem, StiggReporterClient.instance.getStiggConfig());
        BasicDBList updateList = report.updateList;
        List<UsageMetric> usageMetrics = report.usageMetrics;
        if (!report.skipped.isEmpty()) {
            loggerMaker.errorAndAddToDb("Usage metrics skipped for org " + lastUsageItem.getOrgId() + " " + lastUsageItem.getDate() + ": " + report.skipped + ", reported: " + updateList.size(), LoggerMaker.LogDb.BILLING);
        }

        try {
            if (updateList.size() > 0 ){
                StiggReporterClient.instance.reportUsageBulk(lastUsageItem.getOrgId(), updateList);
                sinks.put(OrganizationUsage.DataSink.STIGG.toString(), Context.now());
            } else {
                loggerMaker.infoAndAddToDb("update list empty for stigg " + lastUsageItem.getOrgId() + " " + lastUsageItem.getDate(), LoggerMaker.LogDb.BILLING);
            }
        } catch (IOException e) {
            String errLog = "error while saving to Stigg: " + lastUsageItem.getOrgId() + " " + lastUsageItem.getDate();
            loggerMaker.errorAndAddToDb(e, errLog, LoggerMaker.LogDb.BILLING);
        }

        try {
            if(usageMetrics.size() > 0){
                UsageMetricUtils.syncUsageMetricsWithMixpanel(usageMetrics);
                sinks.put(OrganizationUsage.DataSink.MIXPANEL.toString(), Context.now());
            } else {
                loggerMaker.infoAndAddToDb("update list empty for mixpanel " + lastUsageItem.getOrgId() + " " + lastUsageItem.getDate(), LoggerMaker.LogDb.BILLING);
            }
        } catch (Exception e){
            String errLog = "error while saving to Mixpanel: " + lastUsageItem.getOrgId() + " " + lastUsageItem.getDate();
            loggerMaker.errorAndAddToDb(e, errLog, LoggerMaker.LogDb.BILLING);
        }

        OrganizationUsageDao.instance.updateOne(
            Filters.and(
                Filters.eq(OrganizationUsage.ORG_ID, lastUsageItem.getOrgId()),
                Filters.eq(OrganizationUsage.DATE, lastUsageItem.getDate()),
                Filters.eq(CREATION_EPOCH, lastUsageItem.getCreationEpoch())
            ),
            Updates.set(SINKS, sinks)
        );

    }

    static boolean shouldProcessIncomplete(String orgId, OrganizationFlags flags) {

        if(orgId == null){
            return false;
        }

        if (flags == null) {
            return false;
        }
        if (flags.getAggregateIncomplete() == null || flags.getAggregateIncomplete().isEmpty()) {
            return false;
        }
        return flags.getAggregateIncomplete().contains(orgId);
    }

    public void aggregateUsageForOrg(Organization o, int usageLowerBound, int usageUpperBound, OrganizationFlags flags) {
        if (statusAggregateUsage) {
            throw new IllegalStateException("Aggregation already going on");
        }
        try {
            statusAggregateUsage = true;
            String organizationId = o.getId();
            String organizationName = o.getName();
            Set<Integer> accounts = o.getAccounts();
            int hour = DateUtils.getHour(usageLowerBound + 1000);

            loggerMaker.infoAndAddToDb(String.format("Reporting usage for organization %s - %s for hour %d", organizationId, organizationName, hour), LoggerMaker.LogDb.BILLING);

            loggerMaker.infoAndAddToDb(String.format("Calculating Consolidated and account wise usage for organization %s - %s", organizationId, organizationName), LoggerMaker.LogDb.BILLING);

            Map<String, Integer> consolidatedUsage = new HashMap<String, Integer>();

            // Calculate account wise usage and consolidated usage
            for (MetricTypes metricType : MetricTypes.measured()) {
                String metricTypeString = metricType.toString();

                for (int account : accounts) {
                    UsageMetric usageMetric = UsageMetricsDao.instance.findLatestOne(
                            Filters.and(
                                    Filters.eq(UsageMetric.ORGANIZATION_ID, organizationId),
                                    Filters.eq(UsageMetric.ACCOUNT_ID, account),
                                    Filters.eq(UsageMetric.METRIC_TYPE, metricType),
                                    Filters.and(
                                            Filters.gte(UsageMetric.AKTO_SAVE_EPOCH, usageLowerBound),
                                            Filters.lt(UsageMetric.AKTO_SAVE_EPOCH, usageUpperBound)
                                    )
                            )
                    );

                    int usage = 0;

                    if (usageMetric != null) {
                        usage = usageMetric.getUsage();
                    } else {
                        String err = "Missing account id: " + account + " orgId: " + organizationId+ " metricType: " + metricTypeString + " hour: " + hour;
                        loggerMaker.infoAndAddToDb(err);
                        if (!shouldProcessIncomplete(organizationId, flags)) {
                            continue;
                        }
                    }

                    int currentConsolidateUsage = consolidatedUsage.getOrDefault(metricTypeString, 0);
                    int updatedConsolidateUsage = currentConsolidateUsage + usage;

                    consolidatedUsage.put(metricTypeString, updatedConsolidateUsage);
                }
            }

            if (consolidatedUsage.isEmpty()) {
                String msg = "No usage found for organization: " + organizationId + " (" + organizationName + ") skipping updates... [usageLowerBound=" + usageLowerBound + ", usageUpperBound=" + usageUpperBound + "]";
                throw new IllegalStateException(msg);
            }

            int date = DateUtils.getDateYYYYMMDD(usageLowerBound);

            OrganizationUsage usage = OrganizationUsageDao.instance.findOne(ORG_ID, organizationId, DATE, date);

            if (usage == null) {
                loggerMaker.infoAndAddToDb("Inserting new usage for ("+ organizationId + date +")", LoggerMaker.LogDb.BILLING);
                Map<String, Map<String, Integer>> hourlyUsage = new HashMap<>();
                hourlyUsage.put(String.valueOf(hour), consolidatedUsage);
                OrganizationUsageDao.instance.insertOne(
                        new OrganizationUsage(organizationId, date, Context.now(), consolidatedUsage, new HashMap<>(), hourlyUsage)
                );

                if (date % 100 > 24 || organizationName.endsWith("@akto.io")) {
                    SendgridEmail.getInstance().send(SendgridEmail.getInstance().buildBillingEmail(
                        o.getAdminEmail(),
                        o.getAdminEmail(),
                        consolidatedUsage.getOrDefault(MetricTypes.ACTIVE_ENDPOINTS.toString(), 0),
                        consolidatedUsage.getOrDefault(MetricTypes.TEST_RUNS.toString(), 0),
                        consolidatedUsage.getOrDefault(MetricTypes.CUSTOM_TESTS.toString(), 0),
                        consolidatedUsage.getOrDefault(MetricTypes.ACTIVE_ACCOUNTS.toString(), 0)
                    ));
                }

            } else {
                loggerMaker.infoAndAddToDb("Found usage for ("+ organizationId + date +")", LoggerMaker.LogDb.BILLING);
                Map<String, Map<String, Integer>> hourlyUsage = usage.getHourlyUsage();
                hourlyUsage.put(String.valueOf(hour), consolidatedUsage);
                Bson updates = Updates.combine(Updates.unset(SINKS), Updates.set(ORG_METRIC_MAP, consolidatedUsage), Updates.set(HOURLY_USAGE, hourlyUsage));
                OrganizationUsageDao.instance.updateOne(ORG_ID, organizationId, DATE, date, updates);
            }

            loggerMaker.infoAndAddToDb(String.format("Consolidated and account wise usage for organization %s - %s calculated", organizationId, organizationName), LoggerMaker.LogDb.BILLING);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, String.format("Error while reporting usage for organization %s - %s. Error: %s", o.getId(), o.getName(), e.getMessage()), LoggerMaker.LogDb.BILLING);
        } finally {
            statusAggregateUsage = false;
        }
    }

    public boolean isStatusDataSinks() {
        return statusDataSinks;
    }

    public boolean isStatusAggregateUsage() {
        return statusAggregateUsage;
    }
}
