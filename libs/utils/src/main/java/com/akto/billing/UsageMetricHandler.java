package com.akto.billing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private static final LoggerMaker loggerMaker = new LoggerMaker(UsageMetricHandler.class);

    static class CalcReturn {
        int measureEpoch;
        Organization organization;
        List<UsageMetric> usageMetrics = new ArrayList<>();

        public CalcReturn(int measureEpoch, Organization organization, List<UsageMetric> usageMetrics) {
            this.measureEpoch = measureEpoch;
            this.organization = organization;
            this.usageMetrics = usageMetrics;
        }
    }

    private static CalcReturn calcAndSaveUsageMetrics(MetricTypes[] metricTypes, int accountId) {

        CalcReturn calcReturn = new CalcReturn(Context.now(), null, new ArrayList<>());

        try {

            AccountSettings accountSettings = AccountSettingsDao.instance.findOne(
                    AccountSettingsDao.generateFilter());

            // Get organization to which account belongs to
            Organization organization = OrganizationsDao.instance.findOne(
                    Filters.in(Organization.ACCOUNTS, accountId));

            if (organization == null) {
                throw new Exception("Organization not found for account: " + accountId);
            }

            loggerMaker.infoAndAddToDb(String.format("Measuring usage for %s / %d ", organization.getName(), accountId),
                    LogDb.DASHBOARD);

            String organizationId = organization.getId();

            for (MetricTypes metricType : metricTypes) {
                UsageMetricInfo usageMetricInfo = UsageMetricInfoDao.instance.findOne(
                        UsageMetricsDao.generateFilter(organizationId, accountId, metricType));

                if (usageMetricInfo == null) {
                    usageMetricInfo = new UsageMetricInfo(organizationId, accountId, metricType);
                    UsageMetricInfoDao.instance.insertOne(usageMetricInfo);
                }

                int syncEpoch = usageMetricInfo.getSyncEpoch();
                int measureEpoch = usageMetricInfo.getMeasureEpoch();

                // Reset measureEpoch every month
                if (Context.now() - measureEpoch > 2629746) {
                    if (syncEpoch > Context.now() - 86400) {
                        measureEpoch = Context.now();

                        UsageMetricInfoDao.instance.updateOne(
                                UsageMetricsDao.generateFilter(organizationId, accountId, metricType),
                                Updates.set(UsageMetricInfo.MEASURE_EPOCH, measureEpoch));
                    }

                }

                String dashboardMode = DashboardMode.getDashboardMode().toString();
                String dashboardVersion = accountSettings.getDashboardVersion();

                calcReturn.measureEpoch = measureEpoch;

                UsageMetric usageMetric = new UsageMetric(
                        organizationId, accountId, metricType, syncEpoch, measureEpoch,
                        dashboardMode, dashboardVersion);

                // calculate usage for metric
                UsageMetricCalculator.calculateUsageMetric(usageMetric);

                /*
                 * Save a single usage metric per sync cycle,
                 * until it is synced with akto.
                 */
                usageMetric = UsageMetricsDao.instance.getMCollection().findOneAndReplace(
                        Filters.and(
                                UsageMetricsDao.generateFilter(organizationId, accountId, metricType),
                                Filters.eq(UsageMetric.SYNCED_WITH_AKTO, false),
                                Filters.eq(UsageMetric.SYNC_EPOCH, syncEpoch)),
                        usageMetric, new FindOneAndReplaceOptions().upsert(true)
                                .returnDocument(ReturnDocument.AFTER));

                loggerMaker.infoAndAddToDb("Usage metric inserted: " + usageMetric.getId(), LogDb.DASHBOARD);
                calcReturn.usageMetrics.add(usageMetric);
            }

            updateOrgMeteredUsage(organization);

            calcReturn.organization = organization;

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e,
                    String.format("Error while measuring usage for account %d. Error: %s", accountId, e.getMessage()),
                    LogDb.DASHBOARD);
        }

        return calcReturn;

    }

    private static void updateOrgMeteredUsage(Organization organization) {

        String organizationId = organization.getId();

        // since an org can have multiple accounts, we need to consolidate the usage.
        Map<String, Integer> consolidatedOrgUsage = UsageMetricsDao.instance.findLatestUsageMetricsForOrganization(organizationId);
        
        HashMap<String, FeatureAccess> featureWiseAllowed = organization.getFeatureWiseAllowed();
        
        if (featureWiseAllowed == null) {
            featureWiseAllowed = new HashMap<>();
        }

        for (Map.Entry<String, Integer> entry : consolidatedOrgUsage.entrySet()) {

            String featureLabel = entry.getKey();
            int usage = entry.getValue();

            FeatureAccess featureAccess = featureWiseAllowed.get(featureLabel);

            if (featureAccess != null) {
                featureAccess.setUsage(usage);
                if (featureAccess.getUsage() >= featureAccess.getUsageLimit()) {
                    if (featureAccess.getOverageFirstDetected() == -1) {
                        featureAccess.setOverageFirstDetected(Context.now());
                    }
                } else {
                    featureAccess.setOverageFirstDetected(-1);
                }
            }
            featureWiseAllowed.put(featureLabel, featureAccess);
        }
        organization.setFeatureWiseAllowed(featureWiseAllowed);

        OrganizationsDao.instance.updateOne(
                Filters.eq(Organization.ID, organization.getId()),
                Updates.set(Organization.FEATURE_WISE_ALLOWED, organization.getFeatureWiseAllowed()));
    }

    public static FeatureAccess calcAndFetchFeatureAccess(MetricTypes metricTypes, int accountId) {

        FeatureAccess featureAccess = FeatureAccess.fullAccess;
        try {
            CalcReturn calcReturn = calcAndSaveUsageMetrics(new MetricTypes[] { metricTypes }, accountId);

            Organization organization = calcReturn.organization;
            int measureEpoch = calcReturn.measureEpoch;

            HashMap<String, FeatureAccess> featureWiseAllowed = organization.getFeatureWiseAllowed();
            if (featureWiseAllowed == null || featureWiseAllowed.isEmpty()) {
                throw new Exception("feature map not found or empty for organization " + organization.getId());
            }

            String featureLabel = metricTypes.toString();

            // if feature not found, then the user is not entitled to access the feature
            featureAccess = featureWiseAllowed.getOrDefault(featureLabel, FeatureAccess.noAccess);
            int gracePeriod = organization.getGracePeriod();
            featureAccess.setGracePeriod(gracePeriod);
            featureAccess.setMeasureEpoch(measureEpoch);

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error while calculating usage limit " + e.toString(), LogDb.DASHBOARD);
        }

        return featureAccess;
    }

    public static void calcAndSyncUsageMetrics(MetricTypes[] metricTypes, int accountId) {

        CalcReturn calcReturn = calcAndSaveUsageMetrics(metricTypes, accountId);

        for (UsageMetric usageMetric : calcReturn.usageMetrics) {
            try {
                UsageMetricUtils.syncUsageMetricWithAkto(usageMetric);
                UsageMetricUtils.syncUsageMetricWithMixpanel(usageMetric);
                loggerMaker.infoAndAddToDb(String.format("Synced usage metric %s  %s/%d %s",
                        usageMetric.getId().toString(), usageMetric.getOrganizationId(),
                        usageMetric.getAccountId(), usageMetric.getMetricType().toString()), LogDb.DASHBOARD);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error while syncing usage metric", LogDb.DASHBOARD);
            }
        }
    }

}
