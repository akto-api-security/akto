package com.akto.usage;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.json.JSONObject;

import com.akto.billing.UsageMetricUtils;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dao.usage.UsageMetricInfoDao;
import com.akto.dao.usage.UsageMetricsDao;
import com.akto.dto.AccountSettings;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.Organization;
import com.akto.dto.usage.MetricTypes;
import com.akto.dto.usage.UsageMetric;
import com.akto.dto.usage.UsageMetricInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.DashboardMode;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndReplaceOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;

public class UsageMetricHandler {

    private static final LoggerMaker loggerMaker = new LoggerMaker(UsageMetricHandler.class, LogDb.DASHBOARD);

    private static void updateOrgMeteredUsage(Organization organization) {

        Set<Integer> accounts = organization.getAccounts();
        HashMap<String, FeatureAccess> featureWiseAllowed = organization.getFeatureWiseAllowed();
        featureWiseAllowed = updateFeatureMapWithLocalUsageMetrics(featureWiseAllowed, accounts);
        organization.setFeatureWiseAllowed(featureWiseAllowed);

        OrganizationsDao.instance.updateOne(
                Filters.eq(Organization.ID, organization.getId()),
                Updates.set(Organization.FEATURE_WISE_ALLOWED, organization.getFeatureWiseAllowed()));
    }

    public static FeatureAccess calcAndFetchFeatureAccessUsingDeltaUsage(MetricTypes metricType, int accountId, int deltaUsage) {
        FeatureAccess featureAccess = FeatureAccess.fullAccess;

        try {
            AccountSettings accountSettings = AccountSettingsDao.instance.findOne(
                    AccountSettingsDao.generateFilter());

            // Get organization to which account belongs to
            Organization organization = OrganizationsDao.instance.findOne(
                    Filters.in(Organization.ACCOUNTS, accountId));

            if (organization == null) {
                throw new Exception("Organization not found for account: " + accountId);
            }

            featureAccess = UsageMetricUtils.getFeatureAccess(organization, metricType);
            int usageBefore = featureAccess.getUsage();
            int usageAfter = Math.max(usageBefore + deltaUsage, 0);
            featureAccess.setUsage(usageAfter);
            if (!featureAccess.checkBooleanOrUnlimited() &&
                    featureAccess.getUsage() >= featureAccess.getUsageLimit()) {
                if (featureAccess.getOverageFirstDetected() == -1) {
                    int now = Context.now();
                    String logMessage = String.format("Overage detected for %s at %s", metricType, now);
                    loggerMaker.infoAndAddToDb(logMessage);
                    featureAccess.setOverageFirstDetected(now);
                    raiseMixpanelEvent(organization, accountId,
                            featureAccess.getUsage() - featureAccess.getUsageLimit(), featureAccess.getUsageLimit());
                    sendToSlack(organization, accountId, featureAccess.getUsage(),
                            featureAccess.getUsageLimit(), metricType.toString());
                }
            } else {
                featureAccess.setOverageFirstDetected(-1);
            }
            organization.getFeatureWiseAllowed().put(metricType.name(), featureAccess);
            
            String organizationId = organization.getId();
            String dashboardVersion = accountSettings.getDashboardVersion();            
            UsageMetric usageMetric = createUsageMetric(organizationId, accountId, metricType, dashboardVersion);
            usageMetric.setRecordedAt(Context.now());
            usageMetric.setUsage(usageAfter);
            usageMetric.setAktoSaveEpoch(Context.now());

            usageMetric = UsageMetricsDao.instance.getMCollection().findOneAndReplace(
                    Filters.and(
                            UsageMetricsDao.generateFilter(organizationId, accountId, metricType),
                            Filters.eq(UsageMetric.SYNCED_WITH_AKTO, false),
                            Filters.eq(UsageMetric.SYNC_EPOCH, usageMetric.getSyncEpoch())),
                    usageMetric, new FindOneAndReplaceOptions().upsert(true)
                            .returnDocument(ReturnDocument.AFTER));

            OrganizationsDao.instance.updateOne(
                    Filters.eq(Organization.ID, organization.getId()),
                    Updates.set(Organization.FEATURE_WISE_ALLOWED, organization.getFeatureWiseAllowed()));                

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error while calculating usage limit " + e.toString(), LogDb.DASHBOARD);
        }

        return featureAccess;
    }

    public static UsageMetric createUsageMetric(String organizationId, int accountId, MetricTypes metricType, String dashboardVersion){
        UsageMetricInfo usageMetricInfo = UsageMetricInfoDao.instance.findOne(
                UsageMetricsDao.generateFilter(organizationId, accountId, metricType));

        if (usageMetricInfo == null) {
            usageMetricInfo = new UsageMetricInfo(organizationId, accountId, metricType);
            UsageMetricInfoDao.instance.insertOne(usageMetricInfo);
        }

        int syncEpoch = usageMetricInfo.getSyncEpoch();
        int measureEpoch = usageMetricInfo.getMeasureEpoch();

        // Reset measureEpoch every month
        if (Context.now() - measureEpoch > UsageMetricInfo.MEASURE_PERIOD) {
            if (syncEpoch > Context.now() - 86400) {
                measureEpoch = Context.now();

                UsageMetricInfoDao.instance.updateOne(
                        UsageMetricsDao.generateFilter(organizationId, accountId, metricType),
                        Updates.set(UsageMetricInfo.MEASURE_EPOCH, measureEpoch));
            }
        }

        String dashboardMode = DashboardMode.getDashboardMode().toString();

        UsageMetric usageMetric = new UsageMetric(
                organizationId, accountId, metricType, syncEpoch, measureEpoch,
                dashboardMode, dashboardVersion);

        return usageMetric;
    }

    public static void calcAndSyncAccountUsage(int accountId){
        try {

            AccountSettings accountSettings = AccountSettingsDao.instance.findOne(
                    AccountSettingsDao.generateFilter());

            if (accountSettings == null) {
                loggerMaker.errorAndAddToDb("AccountSettings not found for account: " + accountId, LogDb.DASHBOARD);
                return;
            }
            
            // Get organization to which account belongs to
            Organization organization = OrganizationsDao.instance.findOne(
                    Filters.in(Organization.ACCOUNTS, accountId)
            );

            if (organization == null) {
                loggerMaker.errorAndAddToDb("Organization not found for account: " + accountId, LogDb.DASHBOARD);
                return;
            }

            loggerMaker.infoAndAddToDb(String.format("Measuring usage for %s / %d ", organization.getName(), accountId), LogDb.DASHBOARD);

            String organizationId = organization.getId();
            String dashboardVersion = accountSettings.getDashboardVersion();

            for (MetricTypes metricType : MetricTypes.values()) {

                UsageMetric usageMetric = createUsageMetric(organizationId, accountId, metricType, dashboardVersion);

                //calculate usage for metric
                UsageMetricCalculator.calculateUsageMetric(usageMetric);
                usageMetric.setAktoSaveEpoch(Context.now());

                UsageMetricsDao.instance.insertOne(usageMetric);
                loggerMaker.infoAndAddToDb("Usage metric inserted: " + usageMetric.getId(), LogDb.DASHBOARD);

                UsageMetricUtils.syncUsageMetricWithAkto(usageMetric);

                UsageMetricUtils.syncUsageMetricWithMixpanel(usageMetric);
                loggerMaker.infoAndAddToDb(String.format("Synced usage metric %s  %s/%d %s",
                                usageMetric.getId().toString(), usageMetric.getOrganizationId(), usageMetric.getAccountId(), usageMetric.getMetricType().toString()),
                        LogDb.DASHBOARD
                );

                updateOrgMeteredUsage(organization);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, String.format("Error while measuring usage for account %d. Error: %s", accountId, e.getMessage()), LogDb.DASHBOARD);
        }
    }

    public static HashMap<String, FeatureAccess> updateFeatureMapWithLocalUsageMetrics(HashMap<String, FeatureAccess> featureWiseAllowed, Set<Integer> accounts){

        if (featureWiseAllowed == null) {
            featureWiseAllowed = new HashMap<>();
        }

        // since an org can have multiple accounts, we need to consolidate the usage.
        Map<String, FeatureAccess> consolidatedOrgUsage = UsageMetricsDao.instance.findLatestUsageMetricsForOrganization(accounts);

        for (Map.Entry<String, FeatureAccess> entry : featureWiseAllowed.entrySet()) {
            String featureLabel = entry.getKey();
            FeatureAccess featureAccess = entry.getValue();

            if (consolidatedOrgUsage.containsKey(featureLabel)) {
                FeatureAccess orgUsage = consolidatedOrgUsage.get(featureLabel);
                featureAccess.setUsage(orgUsage.getUsage());

                if(!featureAccess.checkBooleanOrUnlimited() && featureAccess.getUsage() >= featureAccess.getUsageLimit()) {
                    if(featureAccess.getOverageFirstDetected() == -1){
                        featureAccess.setOverageFirstDetected(orgUsage.getOverageFirstDetected());
                    }
                } else {
                    featureAccess.setOverageFirstDetected(-1);
                }
                featureWiseAllowed.put(featureLabel, featureAccess);
            }
        }
        return featureWiseAllowed;
    }

    private static void raiseMixpanelEvent(Organization organization, int accountId, int overage, int usageLimit) {
        try {
            String eventName = "OVERAGE_DETECTED";
            JSONObject props = new JSONObject();
            props.put("Overage", overage);
            props.put("Usage limit", usageLimit);
            String instanceType =  System.getenv("AKTO_INSTANCE_TYPE");
            if (instanceType == null || instanceType.isEmpty()){
                instanceType = "DASHBOARD";
            }
            props.put("Source", instanceType);

            UsageMetricUtils.raiseUsageMixpanelEvent(organization, accountId, eventName, props);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in raising mixpanel event");
        }
    }

    private static void sendToSlack(Organization organization, int accountId, int usage, int usageLimit,
            String feature) {
        if (organization == null) {
            return;
        }
        String txt = String.format("Overage found for feature: %s, Org: %s %s acc: %s limit: %s usage: %s.",
                feature, organization.getId(), organization.getAdminEmail(), accountId, usageLimit, usage);
        UsageMetricUtils.sendToUsageSlack(txt);
    }
}
