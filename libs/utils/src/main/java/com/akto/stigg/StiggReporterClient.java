package com.akto.stigg;

import com.akto.dao.ConfigsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.Organization;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.http_util.CoreHTTPClient;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class StiggReporterClient {

    private static final LoggerMaker loggerMaker = new LoggerMaker(StiggReporterClient.class, LogDb.BILLING);
    public static final StiggReporterClient instance = new StiggReporterClient();
    private static final OkHttpClient client = CoreHTTPClient.client.newBuilder().build();

    private static final int FEATURE_LABEL_CACHE_TTL = 7 * 24 * 60 * 60;
    private static final int FEATURE_LABEL_CACHE_MAX_PAGES = 20;
    private final Object featureLabelCacheLock = new Object();
    private volatile Map<String, String> featureLabelToId = Collections.emptyMap();
    private volatile Map<String, String> featureIdToType = Collections.emptyMap();
    private volatile int featureLabelCacheEpoch = 0;

    private Config.StiggConfig stiggConfig = null;
    private StiggReporterClient() {
        if (stiggConfig == null) {
            synchronized (StiggReporterClient.class) {
                if (stiggConfig == null) {
                    try {
                        Config config = ConfigsDao.instance.findOne("_id", "STIGG-ankush");
                        if (config == null) {
                            loggerMaker.errorAndAddToDb("No stigg config found", LoggerMaker.LogDb.BILLING);
                        } else {
                            stiggConfig = (Config.StiggConfig) config;
                        }
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb("Error while fetching stigg config: " + e.getMessage(), LoggerMaker.LogDb.BILLING);
                    }

                    if (stiggConfig != null && stiggConfig.getSaasFreePlanId() == null  && stiggConfig.getOnPremFreePlanId() == null) {
                        loggerMaker.errorAndAddToDb("No free planId found in stigg config", LoggerMaker.LogDb.BILLING);
                    }
                }
            }
        }
    }

    private String executeGraphQL(String query, String vars) throws IllegalStateException {
        if (stiggConfig == null) {
            throw new IllegalStateException("Stigg config is not initialised");
        }
        int timeNow = Context.now();
        String requestBody = String.format("{\"query\":\"%s\",\"variables\":%s}", query, vars);

        // Set the GraphQL endpoint URL
        String graphqlEndpoint = "https://api.stigg.io/graphql";

        // Create a JSON request body with the GraphQL query
        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        RequestBody requestBodyObject = RequestBody.create(requestBody, JSON);

        Request request = new Request.Builder()
                .url(graphqlEndpoint)
                .header("X-Api-Key", stiggConfig.getServerKey())
                .post(requestBodyObject)
                .build();

        // Execute the request and get the response
        try (Response response = client.newCall(request).execute()) {
            String queryString = "";
            try {
                String[] queryTypes = query.split("\\(");
                queryString = queryTypes[0];
            } catch (Exception e) {
                loggerMaker.logger.info("Error in splitting regex");
            }
            

            if (!response.isSuccessful()) {
                String errorBody = "";
                if (response.body() != null) {
                    errorBody = response.body().string();
                }
                throw new IOException("Unexpected response code: " + response.code() + " " + errorBody);
            }
            String responseBodyStr = response.body().string();
            loggerMaker.infoAndAddToDb("Time taken by stigg call for query: "+ queryString + " is: " + (Context.now() - timeNow));

            timeNow = Context.now();
            BasicDBObject responseBodyObj = BasicDBObject.parse(responseBodyStr);
            loggerMaker.infoAndAddToDb("Time taken by parsing response for query: "+ queryString + " is: " + (Context.now() - timeNow));
            return responseBodyObj.toJson();
        } catch (Exception e) {
            return new BasicDBObject("err", e.getMessage()).toJson();
        }

    }



    public BasicDBList fetchEntitlements(String customerId) {
        BasicDBObject varsObj = new BasicDBObject("input", new BasicDBObject("customerId", customerId));

        String inputVariables = varsObj.toString();

        String queryQ =
            "query Entitlements($input: FetchEntitlementsQuery!) {entitlements(query: $input) {" +
                "    currentUsage\\n" +
                "    customerId\\n" +
                "    entitlementUpdatedAt\\n" +
                "    usageLimit\\n" +
                "    isGranted\\n" +
                "    feature { "    +
                "    id " +
                "    refId " +
                "    additionalMetaData " +
                "  }" +
            "}}";

        BasicDBObject obj = BasicDBObject.parse(executeGraphQL(queryQ, inputVariables));

        loggerMaker.infoAndAddToDb("Entitlements for customerId: " + customerId + " " + obj.toJson(), LoggerMaker.LogDb.BILLING);

        BasicDBObject data = (BasicDBObject) obj.getOrDefault("data", new BasicDBObject());
        return (BasicDBList) data.getOrDefault("entitlements", new BasicDBList());
    }

    public BasicDBObject fetchOrgMetaData(String customerId) {
        BasicDBObject varsObj = new BasicDBObject("input", new BasicDBObject("customerId", customerId));

        String inputVariables = varsObj.toString();

        String queryQ =
            "query GetCustomerByRefId($input: GetCustomerByRefIdInput!) { getCustomerByRefId(input: $input) { " +
            "  additionalMetaData\\n " + 
            "}}";

        BasicDBObject obj = BasicDBObject.parse(executeGraphQL(queryQ, inputVariables));

        loggerMaker.infoAndAddToDb("OrgInfo for customerId: " + customerId + " " + obj.toJson(), LoggerMaker.LogDb.BILLING);

        BasicDBObject data = (BasicDBObject) obj.getOrDefault("data", new BasicDBObject());
        BasicDBObject customer = (BasicDBObject) data.getOrDefault("getCustomerByRefId", new BasicDBObject());
        BasicDBObject additionalMetaData = (BasicDBObject) customer.getOrDefault("additionalMetaData",
                new BasicDBObject());
        if (additionalMetaData == null) {
            additionalMetaData = new BasicDBObject();
        }
        return additionalMetaData;    
    }

    public BasicDBObject getUpdateObject(int value, String customerId, String featureId) {
        return new BasicDBObject("customerId", customerId)
                .append("featureId", featureId)
                .append("value", value)
                .append("updateBehavior", "SET");
    }

    public String reportUsageBulk(String customerId, BasicDBList updateList) throws IOException {

        BasicDBObject varsObj = new BasicDBObject("input",
                new BasicDBObject("usages", updateList));
        String inputVariables = varsObj.toString();
        String mutationQ = "mutation ReportUsageBulk($input: ReportUsageBulkInput!) { reportUsageBulk(input: $input) { id } }";
        String ret = executeGraphQL(mutationQ, inputVariables);

        loggerMaker.infoAndAddToDb("Reporting usage for customerId: " + customerId + " data: " + inputVariables + " ret: " + ret, LoggerMaker.LogDb.BILLING);
        return ret;
    }

    public String provisionSubscription(String customerId, String planId, String billingPeriod, String successUrl, String cancelUrl) {
        String mutationQ = "mutation ProvisionSubscription($input: ProvisionSubscription!) {\\n" +
                "  provisionSubscription(input: $input) {\\n" +
                "    status\\n" +
                "    checkoutUrl\\n" +
                "    subscription {\\n" +
                "      plan {\\n" +
                "        id\\n" +
                "      }}}}";

        String inputVariables = new BasicDBObject("input",
            new BasicDBObject("customerId", customerId)
            .append("planId", planId)
            .append("billingPeriod", billingPeriod)
            .append("checkoutOptions",
                    new BasicDBObject("successUrl", successUrl)
                    .append("cancelUrl", cancelUrl)
                    .append("allowPromoCodes", true)
                    .append("collectBillingAddress", true)
            )
        ).toString();

        String ret = executeGraphQL(mutationQ, inputVariables);

        loggerMaker.infoAndAddToDb("Provisioning subscription customerId: " + customerId + " planId: " + planId + " " + ret, LoggerMaker.LogDb.BILLING);


        return ret;
    }

    public String provisionCustomer(Organization organization) {
        String mutationQ = "mutation ProvisionCustomer($input: ProvisionCustomerInput!) {\\n" +
                "  provisionCustomer(input: $input) {\\n" +
                "    customer {\\n" +
                "      customerId\\n" +
                "    }\\n" +
                "  }\\n" +
                "}";

        String inputVariables = new BasicDBObject("input",
            new BasicDBObject("customerId", organization.getId())
            .append("name", organization.getName())
            .append("email", organization.getAdminEmail())
            .append("subscriptionParams", new BasicDBObject("planId", organization.isOnPrem() ? stiggConfig.getOnPremFreePlanId(): stiggConfig.getSaasFreePlanId()))
        ).toString();

        String out = executeGraphQL(mutationQ, inputVariables);

        loggerMaker.infoAndAddToDb("Provisioning customer organization: " + organization.getId() + " " + out, LoggerMaker.LogDb.BILLING);

        return provisionSubscription(organization.getId(), organization.isOnPrem() ? stiggConfig.getOnPremFreePlanId() : stiggConfig.getSaasFreePlanId(), "ANNUALLY", "https://some.checkout.url", "https://some.checkout.url");
    }

    public BasicDBObject fetchCustomer(String customerId) {
        BasicDBObject varsObj = new BasicDBObject("input", new BasicDBObject("customerId", customerId));
        String queryQ =
            "query GetCustomerByRefId($input: GetCustomerByRefIdInput!) { getCustomerByRefId(input: $input) { " +
            "  customerId\\n name\\n email\\n " +
            "  promotionalEntitlements { id status period hasUnlimitedUsage endDate feature { refId displayName } } " +
            "}}";

        BasicDBObject data = requireGraphQLData(executeGraphQL(queryQ, varsObj.toString()), "getCustomerByRefId");
        BasicDBObject customer = (BasicDBObject) data.get("getCustomerByRefId");
        if (customer == null || customer.isEmpty()) {
            throw new IllegalStateException("No Stigg customer found for id: " + customerId);
        }
        return customer;
    }

    public String findFeatureIdByLabel(String featureLabel) {
        synchronized (featureLabelCacheLock) {
            if (!isFeatureLabelCacheFresh()) {
                refreshFeatureLabelCache();
            }
            String featureId = featureLabelToId.get(featureLabel);
            if (StringUtils.isEmpty(featureId)) {
                throw new IllegalStateException("No Stigg feature found for feature label: " + featureLabel);
            }
            return featureId;
        }
    }

    private boolean isFeatureLabelCacheFresh() {
        return featureLabelCacheEpoch > 0 && Context.now() - featureLabelCacheEpoch < FEATURE_LABEL_CACHE_TTL;
    }

    private void refreshFeatureLabelCache() {
        Map<String, String> nextLabels = new HashMap<>();
        Map<String, String> nextTypes = new HashMap<>();
        String cursor = null;
        boolean hasNextPage = true;
        int pages = 0;
        String queryQ =
            "query FetchFeatures($paging: CursorPaging) { features(paging: $paging) {" +
            "    pageInfo { endCursor hasNextPage }\\n" +
            "    edges { node { refId featureType additionalMetaData } }" +
            "}}";

        while (hasNextPage && pages < FEATURE_LABEL_CACHE_MAX_PAGES) {
            pages++;
            BasicDBObject paging = new BasicDBObject("first", 100);
            if (cursor != null) {
                paging.append("after", cursor);
            }

            BasicDBObject data = requireGraphQLData(
                    executeGraphQL(queryQ, new BasicDBObject("paging", paging).toString()),
                    "features");
            BasicDBObject features = (BasicDBObject) data.get("features");
            if (features == null) {
                break;
            }

            BasicDBList edges = (BasicDBList) features.get("edges");
            if (edges != null) {
                for (Object edgeObj : edges) {
                    if (!(edgeObj instanceof BasicDBObject)) {
                        continue;
                    }
                    BasicDBObject node = (BasicDBObject) ((BasicDBObject) edgeObj).get("node");
                    String label = extractFeatureLabel(node);
                    String refId = node == null ? null : node.getString("refId");
                    if (!label.isEmpty() && !nextLabels.containsKey(label) && !StringUtils.isEmpty(refId)) {
                        nextLabels.put(label, refId);
                        nextTypes.put(refId, StringUtils.defaultString(node.getString("featureType")));
                    }
                }
            }

            BasicDBObject pageInfo = (BasicDBObject) features.get("pageInfo");
            hasNextPage = pageInfo != null && pageInfo.getBoolean("hasNextPage", false);
            cursor = pageInfo == null ? null : pageInfo.getString("endCursor");
        }

        this.featureLabelToId = Collections.unmodifiableMap(nextLabels);
        this.featureIdToType = Collections.unmodifiableMap(nextTypes);
        this.featureLabelCacheEpoch = Context.now();
        loggerMaker.infoAndAddToDb("Cached " + nextLabels.size() + " Stigg feature labels", LoggerMaker.LogDb.BILLING);
    }

    public static String extractFeatureLabel(BasicDBObject feature) {
        if (feature == null) {
            return "";
        }
        Object metaObj = feature.get("additionalMetaData");
        if (!(metaObj instanceof BasicDBObject)) {
            return "";
        }
        return StringUtils.trimToEmpty(((BasicDBObject) metaObj).getString("key", ""));
    }

    public BasicDBList grantLifetimePromotionalEntitlement(String customerId, String featureId) {
        return grantLifetimePromotionalEntitlement(customerId, featureId, null);
    }

    public BasicDBList grantLifetimePromotionalEntitlement(String customerId, String featureId, BasicDBObject customer) {
        BasicDBObject existing = findExistingPromo(customer, featureId);
        PromoGrantPlan plan = planGrant(existing);
        if (plan == PromoGrantPlan.ALREADY_GRANTED) {
            BasicDBList alreadyGranted = new BasicDBList();
            alreadyGranted.add(existing);
            loggerMaker.infoAndAddToDb("Lifetime promotional entitlement already present customerId: " +
                    customerId + " featureId: " + featureId, LoggerMaker.LogDb.BILLING);
            return alreadyGranted;
        }
        if (plan == PromoGrantPlan.REVOKE_THEN_GRANT) {
            revokePromotionalEntitlement(customerId, featureId);
        }

        try {
            return createLifetimePromotionalEntitlement(customerId, featureId);
        } catch (IllegalStateException e) {
            if (!isDuplicatePromoError(e.getMessage())) {
                throw e;
            }
            revokePromotionalEntitlement(customerId, featureId);
            return createLifetimePromotionalEntitlement(customerId, featureId);
        }
    }

    enum PromoGrantPlan {
        ALREADY_GRANTED,
        REVOKE_THEN_GRANT,
        GRANT
    }

    static PromoGrantPlan planGrant(BasicDBObject existing) {
        if (existing == null) {
            return PromoGrantPlan.GRANT;
        }
        String status = StringUtils.defaultString(existing.getString("status"));
        String period = StringUtils.defaultString(existing.getString("period"));
        if (isActivePromo(status) && "LIFETIME".equalsIgnoreCase(period)) {
            return PromoGrantPlan.ALREADY_GRANTED;
        }
        if (isActivePromo(status) || isPausedPromo(status)) {
            return PromoGrantPlan.REVOKE_THEN_GRANT;
        }
        return PromoGrantPlan.GRANT;
    }

    static BasicDBObject findExistingPromo(BasicDBObject customer, String featureId) {
        if (customer == null) {
            return null;
        }
        Object promosObj = customer.get("promotionalEntitlements");
        if (!(promosObj instanceof BasicDBList)) {
            return null;
        }
        for (Object promoObj : (BasicDBList) promosObj) {
            if (!(promoObj instanceof BasicDBObject)) {
                continue;
            }
            BasicDBObject promo = (BasicDBObject) promoObj;
            BasicDBObject feature = (BasicDBObject) promo.get("feature");
            String refId = feature == null ? null : feature.getString("refId");
            if (featureId.equals(refId)) {
                return promo;
            }
        }
        return null;
    }

    static boolean isActivePromo(String status) {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    static boolean isPausedPromo(String status) {
        return "PAUSED".equalsIgnoreCase(status);
    }

    static boolean isDuplicatePromoError(String message) {
        return message != null && (message.contains("DuplicatedEntityNotAllowed")
                || message.toLowerCase().contains("already exists")
                || message.toLowerCase().contains("already granted"));
    }

    private void revokePromotionalEntitlement(String customerId, String featureId) {
        BasicDBObject varsObj = new BasicDBObject("input",
                new BasicDBObject("customerId", customerId).append("featureId", featureId));
        String mutationQ = "mutation RevokePromotionalEntitlement($input: RevokePromotionalEntitlementInput!) {\\n" +
                "  revokePromotionalEntitlement(input: $input) { id }\\n" +
                "}";
        requireGraphQLData(executeGraphQL(mutationQ, varsObj.toString()), "revokePromotionalEntitlement");
        loggerMaker.infoAndAddToDb("Revoked promotional entitlement customerId: " + customerId +
                " featureId: " + featureId, LoggerMaker.LogDb.BILLING);
    }

    private BasicDBList createLifetimePromotionalEntitlement(String customerId, String featureId) {
        BasicDBObject entitlement = new BasicDBObject("featureId", featureId)
                .append("period", "LIFETIME")
                .append("isVisible", true);
        if ("NUMBER".equalsIgnoreCase(featureIdToType.get(featureId))) {
            entitlement.append("hasUnlimitedUsage", true);
        }

        BasicDBList promotionalEntitlements = new BasicDBList();
        promotionalEntitlements.add(entitlement);

        BasicDBObject varsObj = new BasicDBObject("input",
                new BasicDBObject("customerId", customerId)
                        .append("promotionalEntitlements", promotionalEntitlements));

        String mutationQ = "mutation GrantPromotionalEntitlements($input: GrantPromotionalEntitlementsInput!) {\\n" +
                "  grantPromotionalEntitlements(input: $input) {\\n" +
                "    id\\n" +
                "    status\\n" +
                "    hasUnlimitedUsage\\n" +
                "    usageLimit\\n" +
                "    endDate\\n" +
                "    feature {\\n" +
                "      refId\\n" +
                "      displayName\\n" +
                "    }\\n" +
                "  }\\n" +
                "}";

        BasicDBObject data = requireGraphQLData(executeGraphQL(mutationQ, varsObj.toString()), "grantPromotionalEntitlements");
        BasicDBList granted = (BasicDBList) data.get("grantPromotionalEntitlements");
        if (granted == null || granted.isEmpty()) {
            throw new IllegalStateException("Stigg did not grant a promotional entitlement for feature: " + featureId);
        }

        loggerMaker.infoAndAddToDb("Granted lifetime promotional entitlement customerId: " + customerId +
                " featureId: " + featureId + " " + granted.toString(), LoggerMaker.LogDb.BILLING);
        return granted;
    }

    private BasicDBObject requireGraphQLData(String responseJson, String operationName) {
        BasicDBObject obj = BasicDBObject.parse(responseJson);
        if (obj.containsField("err")) {
            throw new IllegalStateException(String.valueOf(obj.get("err")));
        }
        if (obj.containsField("errors")) {
            throw new IllegalStateException(extractGraphQLError(obj.get("errors")));
        }
        BasicDBObject data = (BasicDBObject) obj.get("data");
        if (data == null) {
            throw new IllegalStateException("Empty Stigg data for " + operationName);
        }
        return data;
    }

    private String extractGraphQLError(Object errors) {
        if (errors instanceof BasicDBList) {
            BasicDBList errorList = (BasicDBList) errors;
            if (!errorList.isEmpty() && errorList.get(0) instanceof BasicDBObject) {
                BasicDBObject first = (BasicDBObject) errorList.get(0);
                String message = first.getString("message");
                if (!StringUtils.isEmpty(message)) {
                    return message;
                }
            }
        }
        return String.valueOf(errors);
    }

    public Config.StiggConfig getStiggConfig() {
        return this.stiggConfig;
    }
}
