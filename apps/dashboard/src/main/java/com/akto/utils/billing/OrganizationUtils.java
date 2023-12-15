package com.akto.utils.billing;

import java.io.IOException;
import java.util.*;

import com.akto.dao.RBACDao;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dto.RBAC;
import com.akto.dto.billing.Organization;
import com.akto.util.UsageUtils;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import okhttp3.*;

public class OrganizationUtils {
    
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

    public static BasicDBObject fetchOrgDetails(String orgId) {

        String orgIdUUID = UUID.fromString(orgId).toString();

        Request request = new Request.Builder()
                .url(UsageUtils.getUsageServiceUrl() + "/api/fetchOrgDetails?orgId="+orgIdUUID)
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
}
