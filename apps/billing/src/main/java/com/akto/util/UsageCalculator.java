package com.akto.util;

import com.akto.billing.UsageMetricUtils;
import com.akto.dao.billing.OrganizationUsageDao;
import com.akto.dao.context.Context;
import com.akto.dao.usage.UsageMetricsDao;
import com.akto.dto.billing.Organization;
import com.akto.dto.billing.OrganizationUsage;
import com.akto.dto.usage.MetricTypes;
import com.akto.dto.usage.UsageMetric;
import com.akto.log.LoggerMaker;
import com.akto.stigg.StiggReporterClient;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import org.bson.conversions.Bson;

import java.io.IOException;
import java.util.*;

import static com.akto.dto.billing.OrganizationUsage.*;

public class UsageCalculator {
    private static final LoggerMaker loggerMaker = new LoggerMaker(UsageCalculator.class);
    public static final UsageCalculator instance = new UsageCalculator();

    private UsageCalculator() {}

    private static int epochToDateInt(int epoch) {
        Date dateFromEpoch = new Date( epoch * 1000L );
        Calendar cal = Calendar.getInstance();
        cal.setTime(dateFromEpoch);
        return cal.get(Calendar.YEAR) * 10000 + cal.get(Calendar.MONTH) * 100 + cal.get(Calendar.DAY_OF_MONTH);

    }

    boolean statusDataSinks = false;

    boolean statusAggregateUsage = false;

    public void sendOrgUsageDataToAllSinks(Organization o) {
        if (statusDataSinks) {
            throw new IllegalStateException("Data sinks already going on. Returning...");
        }
        try {
            statusDataSinks = true;
            Bson filterQ =
                    Filters.and(
                            Filters.eq(ORG_ID, o.getId()),
                            Filters.or(Filters.eq(SINKS, new BasicDBObject()), Filters.exists(SINKS, false))
                    );
            List<OrganizationUsage> pendingUsages =
                    OrganizationUsageDao.instance.findAll(filterQ);

            loggerMaker.infoAndAddToDb("Found "+pendingUsages.size()+" items for org: " + o.getId(), LoggerMaker.LogDb.BILLING);

            pendingUsages.sort(
                    (o1, o2)->
                            o2.getDate() == o1.getDate() ?
                                    (o2.getCreationEpoch() - o1.getCreationEpoch()) :
                                    (o2.getDate() - o1.getDate()));

            OrganizationUsage lastUsageItem = pendingUsages.get(0);
            loggerMaker.infoAndAddToDb("Shortlisting: " + lastUsageItem, LoggerMaker.LogDb.BILLING);
            int today = epochToDateInt(Context.now());
            //if (lastUsageItem.getDate() == today) return;

            for (OrganizationUsage.DataSink dataSink : OrganizationUsage.DataSink.values()) {
                switch (dataSink) {
                    case STIGG:
                        syncBillingEodWithStigg(lastUsageItem);
                        break;
                    case SLACK:
                        //syncBillingEodWithSlack(lastUsageItem);
                        break;
                    default:
                        throw new IllegalStateException("Not a valid data sink. Found: " + dataSink);
                }
            }

            Bson ignoreFilterQ = Filters.and(filterQ, Filters.ne(OrganizationUsage.CREATION_EPOCH, lastUsageItem.getCreationEpoch()));
            Bson ignoreUpdateQ = Updates.set(SINKS, new BasicDBObject("ignore", Context.now()));
            UpdateResult updateResult = OrganizationUsageDao.instance.updateMany(ignoreFilterQ, ignoreUpdateQ);
            loggerMaker.infoAndAddToDb("marked "+updateResult.getModifiedCount()+" items as ignored", LoggerMaker.LogDb.BILLING);
        } finally {
            statusDataSinks = false;
        }

    }

    private void syncBillingEodWithSlack(OrganizationUsage lastUsageItem) {

    }

    private void syncBillingEodWithMixpanel(OrganizationUsage ou, MetricTypes metricTypes, int usage) {
        try {
            UsageMetric usageMetric = new UsageMetric(
                    ou.getOrgId(),
                    -1,
                    metricTypes,
                    ou.getCreationEpoch(),
                    Context.now(),
                    "SAAS",
                    "billing"
            );
            usageMetric.setUsage(usage);
            UsageMetricUtils.syncUsageMetricWithMixpanel(usageMetric);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("can't sync to mixpanel", LoggerMaker.LogDb.BILLING);
        }
    }

    private void syncBillingEodWithStigg(OrganizationUsage lastUsageItem) {
        loggerMaker.infoAndAddToDb(String.format("Syncing usage for organization %s to Stigg", lastUsageItem.getOrgId()), LoggerMaker.LogDb.BILLING);
        Map<String, Integer> sinks = lastUsageItem.getSinks();

        for(Map.Entry<String, Integer> entry: lastUsageItem.getOrgMetricMap().entrySet()) {
            MetricTypes metricType = MetricTypes.valueOf(entry.getKey());
            String featureId =  metricType.getLabel();
            int value = entry.getValue();

            try {
                StiggReporterClient.instance.reportUsage(value, lastUsageItem.getOrgId(), featureId);
                sinks.put(OrganizationUsage.DataSink.STIGG.toString(), Context.now());
                syncBillingEodWithMixpanel(lastUsageItem, metricType, value);
            } catch (IOException e) {
                String errLog = "error while saving to Stigg: " + lastUsageItem.getOrgId() + " " + lastUsageItem.getDate() + " " + featureId;
                loggerMaker.errorAndAddToDb(errLog, LoggerMaker.LogDb.BILLING);
            }
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

    public void aggregateUsageForOrg(Organization o, int usageLowerBound, int usageUpperBound) {
        if (statusAggregateUsage) {
            throw new IllegalStateException("Aggregation already going on");
        }
        try {
            statusAggregateUsage = true;
            String organizationId = o.getId();
            String organizationName = o.getName();
            Set<Integer> accounts = o.getAccounts();

            loggerMaker.infoAndAddToDb(String.format("Reporting usage for organization %s - %s", organizationId, organizationName), LoggerMaker.LogDb.BILLING);

            loggerMaker.infoAndAddToDb(String.format("Calculating Consolidated and account wise usage for organization %s - %s", organizationId, organizationName), LoggerMaker.LogDb.BILLING);

            Map<String, Integer> consolidatedUsage = new HashMap<String, Integer>();

            // Calculate account wise usage and consolidated usage
            for (MetricTypes metricType : MetricTypes.values()) {
                String metricTypeString = metricType.toString();
                consolidatedUsage.put(metricTypeString, 0);

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
                    }

                    int currentConsolidateUsage = consolidatedUsage.get(metricTypeString);
                    int updatedConsolidateUsage = currentConsolidateUsage + usage;

                    consolidatedUsage.put(metricTypeString, updatedConsolidateUsage);
                }
            }

            int date = epochToDateInt(usageLowerBound);

            OrganizationUsage usage = OrganizationUsageDao.instance.findOne(ORG_ID, organizationId, DATE, date);

            if (usage == null) {
                loggerMaker.infoAndAddToDb("Inserting new usage for ("+ organizationId + date +")", LoggerMaker.LogDb.BILLING);
                OrganizationUsageDao.instance.insertOne(
                        new OrganizationUsage(organizationId, date, Context.now(), consolidatedUsage, new HashMap<>())
                );
            } else {
                loggerMaker.infoAndAddToDb("Found usage for ("+ organizationId + date +")", LoggerMaker.LogDb.BILLING);
                Bson updates = Updates.combine(Updates.unset(SINKS), Updates.set(ORG_METRIC_MAP, consolidatedUsage));
                OrganizationUsageDao.instance.updateOne(ORG_ID, organizationId, DATE, date, updates);
            }

            loggerMaker.infoAndAddToDb(String.format("Consolidated and account wise usage for organization %s - %s calculated", organizationId, organizationName), LoggerMaker.LogDb.BILLING);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(String.format("Error while reporting usage for organization %s - %s. Error: %s", o.getId(), o.getName(), e.getMessage()), LoggerMaker.LogDb.BILLING);
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
