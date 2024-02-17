package com.akto.utils.billing;

import java.io.IOException;
import java.util.*;

import com.akto.dao.RBACDao;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dto.RBAC;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.Organization;
import com.akto.log.LoggerMaker;
import com.akto.util.UsageUtils;
import com.google.gson.Gson;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import okhttp3.*;
import org.apache.commons.lang3.StringUtils;

public class OrganizationUtils {

    private static final LoggerMaker loggerMaker = new LoggerMaker(OrganizationUtils.class);
    public static boolean isOverage(HashMap<String, FeatureAccess> featureWiseAllowed) {

        if (featureWiseAllowed == null) {
            return false;
        }

        return featureWiseAllowed.entrySet().stream()
                .anyMatch(e -> e.getValue().getIsGranted()
                        && e.getValue().getOverageFirstDetected() != -1);
    }

    public static HashMap<String, FeatureAccess> getFeatureWiseAllowed(BasicDBList l) {
        if (l == null || l.size() == 0) return new HashMap<>();

        HashMap<String, FeatureAccess> result = new HashMap<>();

        int now = Context.now();

        for(Object o: l) {
            try {
                BasicDBObject bO = (BasicDBObject) o;

                boolean isGranted = bO.getBoolean("isGranted", false);

                BasicDBObject featureObject = (BasicDBObject) bO.get("feature");

                String featureLabel = "";
                if (featureObject != null) {
                    BasicDBObject metaData = (BasicDBObject) featureObject.get("additionalMetaData");
                    if (metaData != null) {
                        featureLabel = metaData.getString("key", "");
                    }
                    result.put(featureLabel, new FeatureAccess(isGranted));
                } else {
                    loggerMaker.errorAndAddToDb("unable to find feature object for this entitlement " + bO.toString(), LoggerMaker.LogDb.DASHBOARD);
                }

                if(featureLabel.isEmpty() && featureObject != null){
                    loggerMaker.errorAndAddToDb("unable to find feature label for this feature " + featureObject.toString(), LoggerMaker.LogDb.DASHBOARD);
                }

                Object usageLimitObj = bO.get("usageLimit");

                if (usageLimitObj == null) {
                    continue;
                }

                if (StringUtils.isNumeric(usageLimitObj.toString())) {
                    int usageLimit = Integer.parseInt(usageLimitObj.toString());
                    int usage = Integer.parseInt(bO.getOrDefault("currentUsage", "0").toString());
                    int overageFirstDetected = (usage >= usageLimit) ? now : -1;
                    result.put(featureLabel, new FeatureAccess(isGranted, overageFirstDetected, usageLimit, usage));
                }
            } catch (Exception e) {
                loggerMaker.infoAndAddToDb("unable to parse usage: " + o.toString(), LoggerMaker.LogDb.DASHBOARD);
                continue;
            }
        }

        return result;
    }

    public static Set<Integer> findAccountsBelongingToOrganization(int adminUserId) {
        Set<Integer> accounts = new HashSet<Integer>();

        try {
            List<RBAC> adminAccountsRbac = RBACDao.instance.findAll(
                Filters.eq(RBAC.USER_ID, adminUserId)
            );

            for (RBAC accountRbac : adminAccountsRbac) {
                accounts.add(accountRbac.getAccountId());
            }
        } catch (Exception e) {
            System.out.println("Failed to find accounts belonging to organization. Error - " + e.getMessage());
        }
        
        return accounts;
    }

    private static BasicDBObject fetchFromBillingService(String apiName, BasicDBObject reqBody) {
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
            System.out.println("Failed to sync organization with Akto. Error - " +  e.getMessage());
            return null;
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    private static BasicDBObject fetchFromInternalService(String apiName, BasicDBObject reqBody) {
        String json = reqBody.toJson();

        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
                .url(UsageUtils.getInternalServiceUrl() + "/api/"+apiName)
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
            System.out.println("Failed to sync organization with Akto. Error - " +  e.getMessage());
            return null;
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    public static void flushUsagePipelineForOrg(String organizationId) {
        fetchFromBillingService("flushUsageDataForOrg", new BasicDBObject("organizationId", organizationId));
    }

    public static BasicDBObject fetchOrgDetails(String orgId) {
        String orgIdUUID = UUID.fromString(orgId).toString();
        return fetchFromBillingService("fetchOrgDetails", new BasicDBObject("orgId", orgIdUUID));
    }
    public static BasicDBObject provisionSubscription(String customerId, String planId, String billingPeriod, String successUrl, String cancelUrl) {
        String orgIdUUID = UUID.fromString(customerId).toString();
        BasicDBObject req =
            new BasicDBObject("orgId", orgIdUUID)
            .append("planId", planId)
            .append("billingPeriod", billingPeriod)
            .append("successUrl", successUrl)
            .append("cancelUrl", cancelUrl);
        return fetchFromInternalService("provisionSubscription", req);
    }

    public static String fetchClientKey(String orgId, String adminEmail) {
        String orgIdUUID = UUID.fromString(orgId).toString();
        BasicDBObject reqBody = new BasicDBObject("orgId", orgIdUUID).append("adminEmail", adminEmail);
        BasicDBObject respBody = fetchFromBillingService("fetchClientKey", reqBody);
        if (respBody == null) return "";

        return respBody.getOrDefault("clientKey", "").toString();

    }

    public static String fetchSignature(String orgId, String adminEmail) {
        String orgIdUUID = UUID.fromString(orgId).toString();
        BasicDBObject reqBody = new BasicDBObject("orgId", orgIdUUID).append("adminEmail", adminEmail);
        BasicDBObject respBody = fetchFromBillingService("fetchSignature", reqBody);

        if (respBody == null) return "";

        return respBody.getOrDefault("signature", "").toString();
    }

    public static Boolean syncOrganizationWithAkto(Organization organization) {

        Gson gson = new Gson();
        Map<String, Organization> wrapper = new HashMap<>();
        wrapper.put("organization", organization);
        String json = gson.toJson(wrapper);

        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
                .url(UsageUtils.getUsageServiceUrl() + "/api/createOrganization")
                .post(body)
                .build();

        OkHttpClient client = new OkHttpClient();
        Response response = null;
                
        try {
            response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }

            OrganizationsDao.instance.updateOne(
                Filters.eq(Organization.ID, organization.getId()),
                Updates.set(Organization.SYNCED_WITH_AKTO, true)
            );
        } catch (IOException e) {
            System.out.println("Failed to sync organization with Akto. Error - " +  e.getMessage());
            return false;
        } finally {
            if (response != null) {
                response.close();
            }
        }

        return true;
    }

    public static String determineEmailDomain(String email) {
        if (!email.contains("@")) {
            return email;
        }

        String[] parts = email.split("@");
        if (parts.length != 2) {
            return email;
        }

        String domain = parts[1];
        String[] domainParts = domain.split("\\.");
        if (domainParts.length != 2) {
            return domain;
        }

        return domainParts[0];
    }

    public static BasicDBList fetchEntitlements(String orgId, String adminEmail) {
        String orgIdUUID = UUID.fromString(orgId).toString();
        BasicDBObject reqBody = new BasicDBObject("orgId", orgIdUUID).append("adminEmail", adminEmail);
        BasicDBObject ret = fetchFromBillingService("fetchEntitlements", reqBody);

        if (ret == null) {
            return null;
        }
        return (BasicDBList) (ret.get("entitlements"));
    }

    public static int fetchOrgGracePeriod(String orgId, String adminEmail) {
        String orgIdUUID = UUID.fromString(orgId).toString();
        BasicDBObject reqBody = new BasicDBObject("orgId", orgIdUUID).append("adminEmail", adminEmail);
        BasicDBObject ret = fetchFromBillingService("fetchOrgMetaData", reqBody);

        if (ret == null) {
            return 0;
        }

        BasicDBObject additionalMetaData = (BasicDBObject) ret.getOrDefault("additionalMetaData", new BasicDBObject());
        String gracePeriodStr = (String) additionalMetaData.getOrDefault("GRACE_PERIOD_END_EPOCH", "");

        int gracePeriod = 0;

        if(gracePeriodStr.isEmpty()) {
            return 0;
        }

        try {
            gracePeriod = Integer.parseInt(gracePeriodStr);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Failed to parse grace period for orgId: " + orgId + " adminEmail: " + adminEmail + " gracePeriodStr: " + gracePeriodStr, LoggerMaker.LogDb.DASHBOARD);   
        }

        if(gracePeriod <= 0) {
            return 0;
        }

        return gracePeriod;
    }
}
