package com.akto.stigg;

import com.akto.DaoInit;
import com.akto.dao.ConfigsDao;
import com.akto.dto.Config;
import com.akto.dto.billing.Organization;
import com.akto.log.LoggerMaker;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;

public class StiggReporterClient {

    private static final LoggerMaker loggerMaker = new LoggerMaker(StiggReporterClient.class);
    public static final StiggReporterClient instance = new StiggReporterClient();

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

                    if (stiggConfig != null && stiggConfig.getFreePlanId() == null) {
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
        OkHttpClient client = new OkHttpClient();
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
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected response code: " + response);
            }

            String responseBodyStr = response.body().string();
            BasicDBObject responseBodyObj = BasicDBObject.parse(responseBodyStr);
            return responseBodyObj.toJson();
        } catch (Exception e) {
            return new BasicDBObject("err", e.getMessage()).toJson();
        }

    }

    public boolean isOverage(String customerId) {
        BasicDBList l = fetchEntitlements(customerId);
        if (l.size() == 0) return false;

        boolean result = false;

        for(Object o: l) {
            try {
                BasicDBObject bO = (BasicDBObject) o;
                Object usageLimitObj = bO.get("usageLimit");

                if (usageLimitObj == null) {
                    continue;
                }

                if (StringUtils.isNumeric(usageLimitObj.toString())) {
                    int usageLimit = Integer.parseInt(usageLimitObj.toString());
                    int usage = Integer.parseInt(bO.getOrDefault("currentUsage", "0").toString());
                    if (usage > usageLimit) {
                        return true;
                    }

                }
            } catch (Exception e) {
                loggerMaker.infoAndAddToDb("unable to parse usage: " + o.toString(), LoggerMaker.LogDb.DASHBOARD);
                continue;
            }
        }

        return result;
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
            "}}";

        BasicDBObject obj = BasicDBObject.parse(executeGraphQL(queryQ, inputVariables));

        BasicDBObject data = (BasicDBObject) obj.getOrDefault("data", new BasicDBObject());
        return (BasicDBList) data.getOrDefault("entitlements", new BasicDBList());
    }

    public String reportUsage(int value, String customerId, String featureId) throws IOException {

//        TimeZone utc = TimeZone.getTimeZone("UTC");
//        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
//        format.setTimeZone(utc);

        BasicDBObject varsObj = new BasicDBObject("input",
                new BasicDBObject("customerId", customerId)
                .append("featureId", featureId)
                .append("value", value)
                .append("updateBehavior", "SET")
        );

        String inputVariables = varsObj.toString();

        String mutationQ = "mutation CreateUsageMeasurement($input: UsageMeasurementCreateInput!) {  createUsageMeasurement(usageMeasurement: $input) {    id}}";

        return executeGraphQL(mutationQ, inputVariables);

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

        return executeGraphQL(mutationQ, inputVariables);
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
            .append("subscriptionParams", new BasicDBObject("planId", stiggConfig.getFreePlanId()))
        ).toString();

        executeGraphQL(mutationQ, inputVariables);

        return provisionSubscription(organization.getId(), stiggConfig.getFreePlanId(), "ANNUALLY", "https://some.checkout.url", "https://some.checkout.url");
    }

    public Config.StiggConfig getStiggConfig() {
        return this.stiggConfig;
    }
}
