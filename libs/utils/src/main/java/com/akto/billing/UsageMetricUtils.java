package com.akto.billing;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import org.json.JSONObject;

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
import com.akto.mixpanel.AktoMixpanel;
import com.akto.util.DashboardMode;
import com.akto.util.EmailAccountName;
import com.akto.util.UsageUtils;
import com.google.gson.Gson;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class UsageMetricUtils {
    private static final LoggerMaker loggerMaker = new LoggerMaker(UsageMetricUtils.class);

    public static void syncUsageMetricWithAkto(UsageMetric usageMetric) {
        try {
            Gson gson = new Gson();
            Map<String, UsageMetric> wrapper = new HashMap<>();
            wrapper.put("usageMetric", usageMetric);
            String json = gson.toJson(wrapper);

            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(json, JSON);

            Request request = new Request.Builder()
                    .url(UsageUtils.getUsageServiceUrl() + "/api/ingestUsage")
                    .post(body)
                    .build();

            OkHttpClient client = new OkHttpClient();
            Response response = null;

            try {
                response = client.newCall(request).execute();
                if (!response.isSuccessful()) {
                    throw new IOException("Unexpected code " + response);
                }

                UsageMetricsDao.instance.updateOne(
                        Filters.eq(UsageMetric.ID, usageMetric.getId()),
                        Updates.set(UsageMetric.SYNCED_WITH_AKTO, true)
                );

                UsageMetricInfoDao.instance.updateOne(
                        Filters.and(
                                Filters.eq(UsageMetricInfo.ORGANIZATION_ID, usageMetric.getOrganizationId()),
                                Filters.eq(UsageMetricInfo.ACCOUNT_ID, usageMetric.getAccountId()),
                                Filters.eq(UsageMetricInfo.METRIC_TYPE, usageMetric.getMetricType())
                        ),
                        Updates.set(UsageMetricInfo.SYNC_EPOCH, Context.now())
                );
            } catch (IOException e) {
                loggerMaker.errorAndAddToDb("Failed to sync usage metric with Akto. Error - " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            } finally {
                if (response != null) {
                    response.close(); // Manually close the response body
                }
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Failed to execute usage metric. Error - " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
        }
    }

    public static void syncUsageMetricWithMixpanel(UsageMetric usageMetric) {
        try {
            String organizationId = usageMetric.getOrganizationId();
            Organization organization = OrganizationsDao.instance.findOne(
                    Filters.and(
                            Filters.eq(Organization.ID, organizationId)
                    )
            );

            if (organization == null) {
                return;
            }

            String adminEmail = organization.getAdminEmail();
            String dashboardMode = usageMetric.getDashboardMode();
            String eventName = String.valueOf(usageMetric.getMetricType());
            String distinct_id = adminEmail + "_" + dashboardMode;

            EmailAccountName emailAccountName = new EmailAccountName(adminEmail);
            String accountName = emailAccountName.getAccountName();

            JSONObject props = new JSONObject();
            props.put("Email ID", adminEmail);
            props.put("Account Name", accountName);
            props.put("Organization Id", organizationId);
            props.put("Account Id", usageMetric.getAccountId());
            props.put("Metric Type", usageMetric.getMetricType());
            props.put("Dashboard Version", usageMetric.getDashboardVersion());
            props.put("Dashboard Mode", usageMetric.getDashboardMode());
            props.put("Usage", usageMetric.getUsage());
            props.put("Organization Name", organization.getName());
            props.put("Source", "Dashboard");

            System.out.println("Sending event to mixpanel: " + eventName);

            AktoMixpanel aktoMixpanel = new AktoMixpanel();
            aktoMixpanel.sendEvent(distinct_id, eventName, props);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Failed to execute usage metric in Mixpanel. Error - " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
        }
    }

    public static boolean checkMeteredOverage(int accountId, String featureLabel) {

        try {

            if (!DashboardMode.isMetered()) {
                return false;
            }

            Organization organization = OrganizationsDao.instance.findOne(
                    Filters.in(Organization.ACCOUNTS, accountId));

            if (organization == null) {
                throw new Exception("Organization not found");
            }

            HashMap<String, FeatureAccess> featureWiseAllowed = organization.getFeatureWiseAllowed();

            if (featureWiseAllowed == null || featureWiseAllowed.isEmpty()) {
                throw new Exception("feature map not found or empty for organization " + organization.getId());
            }

            FeatureAccess featureAccess = featureWiseAllowed.getOrDefault(featureLabel, null);

            int gracePeriod = organization.getGracePeriod();

            // stop access if feature is not found or overage
            return featureAccess == null || !featureAccess.getIsGranted()
                    || featureAccess.checkOverageAfterGrace(gracePeriod);

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Failed to check metered overage. Error - " + e.getMessage(),
                    LogDb.DASHBOARD);
        }

        // allow access by default and in case of errors.
        return false;
    }

    public static boolean checkActiveEndpointOverage(int accountId){
        return checkMeteredOverage(accountId, MetricTypes.ACTIVE_ENDPOINTS.name());
    }

    public static boolean checkTestRunsOverage(int accountId){
        return checkMeteredOverage(accountId, MetricTypes.TEST_RUNS.name());
    }

    public static UsageMetric calcAndSaveUsageMetrics(MetricTypes[] metricTypes){
        int accountId = Context.accountId.get();

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

                UsageMetric usageMetric = new UsageMetric(
                        organizationId, accountId, metricType, syncEpoch, measureEpoch,
                        dashboardMode, dashboardVersion);

                // calculate usage for metric
                UsageMetricCalculator.calculateUsageMetric(usageMetric);

                UsageMetricsDao.instance.insertOne(usageMetric);
                loggerMaker.infoAndAddToDb("Usage metric inserted: " + usageMetric.getId(), LogDb.DASHBOARD);
                return usageMetric;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e,
                    String.format("Error while measuring usage for account %d. Error: %s", accountId, e.getMessage()),
                    LogDb.DASHBOARD);
        }
        return null;
    }

    public static void calcAndSyncUsageMetrics(MetricTypes[] metricTypes) {

        UsageMetric usageMetric = calcAndSaveUsageMetrics(metricTypes);

        try {

            if (usageMetric == null) {
                throw new Exception("Usage metric is null");
            }

            UsageMetricUtils.syncUsageMetricWithAkto(usageMetric);

            UsageMetricUtils.syncUsageMetricWithMixpanel(usageMetric);
            loggerMaker.infoAndAddToDb(String.format("Synced usage metric %s  %s/%d %s",
                    usageMetric.getId().toString(), usageMetric.getOrganizationId(), usageMetric.getAccountId(),
                    usageMetric.getMetricType().toString()),
                    LogDb.DASHBOARD);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e,
                    String.format("Error while syncing usage metric %s  %s/%d %s",
                            usageMetric.getId().toString(), usageMetric.getOrganizationId(), usageMetric.getAccountId(),
                            usageMetric.getMetricType().toString()),
                    LogDb.DASHBOARD);
        }
    }

}
