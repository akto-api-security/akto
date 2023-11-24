package com.akto.dto.billing;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;

import com.akto.dao.RBACDao;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dao.usage.UsageMetricInfoDao;
import com.akto.dao.usage.UsageMetricsDao;
import com.akto.dto.RBAC;
import com.akto.dto.usage.UsageMetric;
import com.akto.dto.usage.UsageMetricInfo;
import com.akto.util.UsageUtils;
import com.google.gson.Gson;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Organization {
    
    // Change this to use a UUID
    @BsonId
    private String id;
    public static final String ID = "_id";
    private String name;
    private String adminEmail;
    public static final String ADMIN_EMAIL = "adminEmail";
    private Boolean syncedWithAkto;
    public static final String SYNCED_WITH_AKTO = "syncedWithAkto";
    public Set<Integer> accounts;
    public static final String ACCOUNTS = "accounts";

    public Organization() { }

    public Organization(String id, String name, String adminEmail, Set<Integer> accounts) {
        this.id = id;
        this.name = name;
        this.adminEmail = adminEmail;
        this.accounts = accounts;
        this.syncedWithAkto = false;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAdminEmail() {
        return adminEmail;
    }

    public void setAdminEmail(String email) {
        this.adminEmail = email;
    }

    public Boolean getSyncedWithAkto() {
        return syncedWithAkto;
    }

    public void setSyncedWithAkto(Boolean syncedWithAkto) {
        this.syncedWithAkto = syncedWithAkto;
    }

    public Set<Integer> getAccounts() {
        return accounts;
    }

    public void setAccounts(Set<Integer> accounts) {
        this.accounts = accounts;
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

    public static Boolean syncWithAkto(Organization organization) {

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
}
