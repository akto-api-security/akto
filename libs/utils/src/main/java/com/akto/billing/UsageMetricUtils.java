package com.akto.billing;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import com.akto.log.LoggerMaker;
import com.akto.log.CacheLoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import org.json.JSONException;
import org.json.JSONObject;

import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dao.usage.UsageMetricInfoDao;
import com.akto.dao.usage.UsageMetricsDao;
import com.akto.dto.Config;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.Organization;
import com.akto.dto.billing.SyncLimit;
import com.akto.dto.usage.MetricTypes;
import com.akto.dto.usage.UsageMetric;
import com.akto.dto.usage.UsageMetricInfo;
import com.akto.mixpanel.AktoMixpanel;
import com.akto.util.DashboardMode;
import com.akto.util.EmailAccountName;
import com.akto.util.UsageUtils;
import com.akto.util.http_util.CoreHTTPClient;
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
    private static final LoggerMaker loggerMaker = new LoggerMaker(UsageMetricUtils.class, LogDb.DASHBOARD);
    private static final CacheLoggerMaker cacheLoggerMaker = new CacheLoggerMaker(UsageMetricUtils.class);
    private static final OkHttpClient client = CoreHTTPClient.client.newBuilder().build();

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

    public static JSONObject getUsageMetricsProps(UsageMetric usageMetric, Organization organization)
            throws JSONException {
        String adminEmail = organization.getAdminEmail();
        EmailAccountName emailAccountName = new EmailAccountName(adminEmail);
        String accountName = emailAccountName.getAccountName();
        String organizationId = usageMetric.getOrganizationId();
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
        return props;
    }

    public static void syncUsageMetricWithMixpanel(UsageMetric usageMetric) {
        String distinct_id = "";
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
            distinct_id = adminEmail + "_" + dashboardMode;

            JSONObject props = getUsageMetricsProps(usageMetric, organization);

            logger.info("Sending event to mixpanel: " + eventName);

            AktoMixpanel aktoMixpanel = new AktoMixpanel();
            aktoMixpanel.sendEvent(distinct_id, eventName, props);
        } catch (Exception e) {
            cacheLoggerMaker.errorAndAddToDb("Failed to execute usage metric in Mixpanel for distinct_id: " + distinct_id + ". Error - " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
        }
    }

    public static boolean checkMeteredOverage(int accountId, MetricTypes metricType) {
        FeatureAccess featureAccess = getFeatureAccess(accountId, metricType);
        return featureAccess.checkInvalidAccess();
    }

    public static void syncUsageMetricsWithMixpanel(List<UsageMetric> usageMetrics) {
        try {
            UsageMetric usageMetric = usageMetrics.get(0);
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
            String distinct_id = adminEmail + "_" + dashboardMode;

            Map<String, JSONObject> map = new HashMap<>();
            JSONObject props = getUsageMetricsProps(usageMetric, organization);

            for (UsageMetric metric : usageMetrics) {
                JSONObject jo = new JSONObject(props);
                jo.put("Metric Type", metric.getMetricType());
                int metricData = metric.getUsage();
                if(metricData == 0){
                    continue;
                }
                jo.put("Usage", metricData);
                String eventName = String.valueOf(metric.getMetricType());
                map.put(eventName, jo);
                logger.info("Sending event to mixpanel: " + eventName);
            }

            if(map.size() == 0){
                return;
            }

            AktoMixpanel aktoMixpanel = new AktoMixpanel();
            aktoMixpanel.sendBulkEvents(distinct_id, map);
        } catch (Exception e) {
            cacheLoggerMaker.errorAndAddToDb("Failed to execute usage metric in Mixpanel. Error - " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
        }
    }


    public static BasicDBObject fetchFromBillingService(String apiName, BasicDBObject reqBody) {
        String json = reqBody.toJson();

        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
                .url(UsageUtils.getUsageServiceUrl() + "/api/"+apiName)
                .post(body)
                .build();

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
        return checkMeteredOverage(accountId, MetricTypes.ACTIVE_ENDPOINTS);
    }

    public static boolean checkTestRunsOverage(int accountId){
        return checkMeteredOverage(accountId, MetricTypes.TEST_RUNS);
    }


    /**
     * Intelligently checks for agentic overage based on available limits:
     * - If only MCP limit exists: check MCP overage only
     * - If only GenAI limit exists: check GenAI overage only
     * - If both limits exist: check if either limit is exceeded
     */
    public static boolean checkCombinedAgenticOverage(int accountId) {
        try {
            Organization organization = OrganizationsDao.instance.findOneByAccountId(accountId);
            if (organization == null) {
                return false;
            }

            // Get both MCP and GenAI limits
            FeatureAccess mcpAccess = getFeatureAccess(organization, MetricTypes.MCP_ASSET_COUNT);
            FeatureAccess aiAccess = getFeatureAccess(organization, MetricTypes.AI_ASSET_COUNT);
            
            boolean hasMcpLimit = !mcpAccess.checkBooleanOrUnlimited();
            boolean hasAiLimit = !aiAccess.checkBooleanOrUnlimited();
            
            // Case 1: No limits - no overage possible
            if (!hasMcpLimit && !hasAiLimit) {
                return false;
            }
            
            // Case 2: Only MCP limit exists - check MCP overage only
            if (hasMcpLimit && !hasAiLimit) {
                return mcpAccess.getUsage() >= mcpAccess.getUsageLimit();
            }
            
            // Case 3: Only GenAI limit exists - check GenAI overage only  
            if (!hasMcpLimit && hasAiLimit) {
                return aiAccess.getUsage() >= aiAccess.getUsageLimit();
            }
            
            // Case 4: Both limits exist - check if either is exceeded
            if (hasMcpLimit && hasAiLimit) {
                boolean mcpOverage = mcpAccess.getUsage() >= mcpAccess.getUsageLimit();
                boolean aiOverage = aiAccess.getUsage() >= aiAccess.getUsageLimit();
                return mcpOverage || aiOverage;
            }
            
            return false;
            
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error checking combined agentic overage for account: " + accountId, LogDb.DASHBOARD);
            return false;
        }
    }

    /**
     * Gets an intelligent sync limit for agentic assets based on available limits:
     * - If only MCP limit exists: use MCP limit
     * - If only GenAI limit exists: use GenAI limit  
     * - If both limits exist: use combined agentic approach (sum of both)
     */
    public static SyncLimit getCombinedAgenticSyncLimit(int accountId) {
        try {
            Organization organization = OrganizationsDao.instance.findOneByAccountId(accountId);
            if (organization == null) {
                return SyncLimit.noLimit;
            }

            // Get both MCP and GenAI limits
            FeatureAccess mcpAccess = getFeatureAccess(organization, MetricTypes.MCP_ASSET_COUNT);
            FeatureAccess aiAccess = getFeatureAccess(organization, MetricTypes.AI_ASSET_COUNT);
            
            boolean hasMcpLimit = !mcpAccess.checkBooleanOrUnlimited();
            boolean hasAiLimit = !aiAccess.checkBooleanOrUnlimited();
            
            // Case 1: No limits at all - unlimited
            if (!hasMcpLimit && !hasAiLimit) {
                return SyncLimit.noLimit;
            }
            
            // Case 2: Only MCP limit exists - use standalone MCP
            if (hasMcpLimit && !hasAiLimit) {
                int mcpUsageLeft = Math.max(mcpAccess.getUsageLimit() - mcpAccess.getUsage(), 0);
                if (mcpAccess.getUsage() >= mcpAccess.getUsageLimit()) {
                    return new SyncLimit(true, 0);
                }
                return new SyncLimit(true, mcpUsageLeft);
            }
            
            // Case 3: Only GenAI limit exists - use standalone GenAI
            if (!hasMcpLimit && hasAiLimit) {
                int aiUsageLeft = Math.max(aiAccess.getUsageLimit() - aiAccess.getUsage(), 0);
                if (aiAccess.getUsage() >= aiAccess.getUsageLimit()) {
                    return new SyncLimit(true, 0);
                }
                return new SyncLimit(true, aiUsageLeft);
            }
            
            // Case 4: Both limits exist - use combined agentic approach (sum)
            if (hasMcpLimit && hasAiLimit) {
                int mcpUsageLeft = Math.max(mcpAccess.getUsageLimit() - mcpAccess.getUsage(), 0);
                int aiUsageLeft = Math.max(aiAccess.getUsageLimit() - aiAccess.getUsage(), 0);
                
                // If either limit is exceeded, no usage left
                if (mcpAccess.getUsage() >= mcpAccess.getUsageLimit() ||
                    aiAccess.getUsage() >= aiAccess.getUsageLimit()) {
                    return new SyncLimit(true, 0);
                }
                
                // Sum both remaining usages for maximum agentic flexibility
                int combinedUsageLeft = mcpUsageLeft + aiUsageLeft;
                return new SyncLimit(true, combinedUsageLeft);
            }
            
            // Fallback - no limit
            return SyncLimit.noLimit;
            
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error getting combined agentic sync limit for account: " + accountId, LogDb.DASHBOARD);
            return SyncLimit.noLimit;
        }
    }

    public static FeatureAccess getFeatureAccess(int accountId, MetricTypes metricType) {
        FeatureAccess featureAccess = FeatureAccess.fullAccess;
        try {
            if (!DashboardMode.isMetered()) {
                return featureAccess;
            }
            Organization organization = OrganizationsDao.instance.findOneByAccountId(accountId);
            featureAccess = getFeatureAccess(organization, metricType);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetching usage metric acc: " + accountId, LogDb.DASHBOARD);
        }
        return featureAccess;
    }

    public static FeatureAccess getFeatureAccessSaas(int accountId, String featureLabel) {
        /*
         * No access in case of billing service down.
         * For selected features only.
         */
        FeatureAccess featureAccess = FeatureAccess.noAccess;
        try {
            if (!DashboardMode.isMetered()) {
                return featureAccess;
            }
            Organization organization = OrganizationsDao.instance.findOneByAccountId(accountId);
            if (organization == null) {
                return featureAccess;
            }
            HashMap<String, FeatureAccess> featureWiseAllowed = organization.getFeatureWiseAllowed();
            if (featureWiseAllowed == null || featureWiseAllowed.isEmpty()) {
                return DashboardMode.isOnPremDeployment() ? FeatureAccess.fullAccess : FeatureAccess.noAccess; // case of on-prem customers without internet access
            }
            featureAccess = featureWiseAllowed.getOrDefault(featureLabel, FeatureAccess.noAccess);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetching featureLabel acc: " + accountId, LogDb.DASHBOARD);
        }
        return featureAccess;
    }

    public static FeatureAccess getFeatureAccess(Organization organization, MetricTypes metricType) {
        FeatureAccess featureAccess = FeatureAccess.fullAccess;
        try {
            if (!DashboardMode.isMetered()) {
                return featureAccess;
            }
            if (organization == null) {
                throw new Exception("Organization not found");
            }
            HashMap<String, FeatureAccess> featureWiseAllowed = organization.getFeatureWiseAllowed();
            if (featureWiseAllowed == null || featureWiseAllowed.isEmpty()) {
                throw new Exception("feature map not found or empty for organization " + organization.getId());
            }
            String featureLabel = metricType.name();
            featureAccess = featureWiseAllowed.getOrDefault(featureLabel, FeatureAccess.noAccess);
            int gracePeriod = organization.getGracePeriod();
            featureAccess.setGracePeriod(gracePeriod);
        } catch (Exception e) {
            String orgId = "";
            if (organization != null && organization.getId() != null) {
                orgId = organization.getId();
            }
            loggerMaker.errorAndAddToDb(e, "Error in fetching usage metric org: " + orgId, LogDb.DASHBOARD);
        }
        return featureAccess;
    }

    public static void raiseUsageMixpanelEvent(Organization organization, int accountId, String eventName, JSONObject additionalProps){
        try {
            if (organization == null) {
                return;
            }

            String adminEmail = organization.getAdminEmail();
            String dashboardMode = organization.isOnPrem() ? DashboardMode.ON_PREM.toString() : DashboardMode.SAAS.toString();
            String distinct_id = adminEmail + "_" + dashboardMode;
            EmailAccountName emailAccountName = new EmailAccountName(adminEmail);
            String accountName = emailAccountName.getAccountName();
            String organizationId = organization.getId();
            
            JSONObject props = new JSONObject();
            props.put("Email ID", adminEmail);
            props.put("Account Name", accountName);
            props.put("Dashboard Mode", dashboardMode);
            props.put("Organization Id", organizationId);
            props.put("Account Id", accountId);
            props.put("Dashboard Mode", dashboardMode);
            props.put("Organization Name", organization.getName());
            props.put("Source", "Dashboard");

            Iterator<?> keys = additionalProps.keys();

            while (keys.hasNext()) {
                Object key = keys.next();
                if (key instanceof String) {
                    String keyString = (String) key;
                    Object value = additionalProps.get(keyString);
                    props.put(keyString, value);
                }
            }

            loggerMaker.infoAndAddToDb("Sending event to mixpanel: " + eventName);

            AktoMixpanel aktoMixpanel = new AktoMixpanel();
            aktoMixpanel.sendEvent(distinct_id, eventName, props);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in sending usage event to mixpanel");
        }
    }

    public static void sendToUsageSlack(String message) {
        String slackUsageWebhookUrl = null;
        try {
            Config.SlackAlertUsageConfig slackUsageWebhook = com.akto.onprem.Constants.getSlackAlertUsageConfig();
            if (slackUsageWebhook != null && slackUsageWebhook.getSlackWebhookUrl() != null
                    && !slackUsageWebhook.getSlackWebhookUrl().isEmpty()) {
                slackUsageWebhookUrl = slackUsageWebhook.getSlackWebhookUrl();
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Unable to find slack webhook URL");
        }

        LoggerMaker.sendToSlack(slackUsageWebhookUrl, message);
    }
}
