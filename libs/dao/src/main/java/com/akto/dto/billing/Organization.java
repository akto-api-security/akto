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


    public static final String ON_PREM = "onPrem";
    private boolean onPrem;
    public Organization() { }

    public Organization(String id, String name, String adminEmail, Set<Integer> accounts, boolean onPrem) {
        this.id = id;
        this.name = name;
        this.adminEmail = adminEmail;
        this.accounts = accounts;
        this.syncedWithAkto = false;
        this.onPrem = onPrem;
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

    public boolean isOnPrem() {
        return onPrem;
    }

    public void setOnPrem(boolean onPrem) {
        this.onPrem = onPrem;
    }
}
