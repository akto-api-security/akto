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
import java.util.HashMap;

public class StiggReporterClient {

    private static final LoggerMaker loggerMaker = new LoggerMaker(StiggReporterClient.class);
    public static final StiggReporterClient instance = new StiggReporterClient();
    private static final OkHttpClient client = CoreHTTPClient.client.newBuilder().build();

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
                throw new IOException("Unexpected response code: " + response);
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

    public Config.StiggConfig getStiggConfig() {
        return this.stiggConfig;
    }
}
