package com.akto.billing;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import com.akto.log.LoggerMaker;
import com.akto.log.CacheLoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import org.json.JSONObject;

import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dao.usage.UsageMetricInfoDao;
import com.akto.dao.usage.UsageMetricsDao;
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
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UsageMetricUtils {
    private static final Logger logger = LoggerFactory.getLogger(UsageMetricUtils.class);
    private static final LoggerMaker loggerMaker = new LoggerMaker(UsageMetricUtils.class);
    private static final CacheLoggerMaker cacheLoggerMaker = new CacheLoggerMaker(UsageMetricUtils.class);

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
                cacheLoggerMaker.errorAndAddToDb("Failed to sync usage metric with Akto. Error - " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            } finally {
                if (response != null) {
                    response.close(); // Manually close the response body
                }
            }
        } catch (Exception e) {
            cacheLoggerMaker.errorAndAddToDb("Failed to execute usage metric. Error - " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
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

            logger.info("Sending event to mixpanel: " + eventName);

            AktoMixpanel aktoMixpanel = new AktoMixpanel();
            aktoMixpanel.sendEvent(distinct_id, eventName, props);
        } catch (Exception e) {
            cacheLoggerMaker.errorAndAddToDb("Failed to execute usage metric in Mixpanel. Error - " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
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
            loggerMaker.errorAndAddToDb(e, "Failed to check metered overage. Error - " + e.getMessage(),
                    LogDb.DASHBOARD);
        }

        // allow access by default and in case of errors.
        return false;
    }

    public static BasicDBObject fetchFromBillingService(String apiName, BasicDBObject reqBody) {
        String json = reqBody.toJson();

        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
                .url(UsageUtils.getUsageServiceUrl() + "/api/"+apiName)
                .post(body)
                .build();

        OkHttpClient client = new OkHttpClient();
        Response response = null;

        try {
            response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                return null;
            }

            return BasicDBObject.parse(responseBody.string());

        } catch (IOException e) {
            loggerMaker.errorAndAddToDb(e, "Failed to sync organization with Akto. Error - " +  e.getMessage(), LogDb.DASHBOARD);
            return null;
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    public static boolean checkActiveEndpointOverage(int accountId){
        return checkMeteredOverage(accountId, MetricTypes.ACTIVE_ENDPOINTS.name());
    }

    public static boolean checkTestRunsOverage(int accountId){
        return checkMeteredOverage(accountId, MetricTypes.TEST_RUNS.name());
    }

}
