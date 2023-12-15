package com.akto.dto.billing;

import java.util.HashMap;
import java.util.Set;
import org.bson.codecs.pojo.annotations.BsonId;

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
    // feature label -> FeatureAccess
    HashMap<String, FeatureAccess> featureWiseAllowed;
    public static final String FEATURE_WISE_ALLOWED = "featureWiseAllowed";

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

    public HashMap<String, FeatureAccess> getFeatureWiseAllowed() {
        return featureWiseAllowed;
    }

    public void setFeatureWiseAllowed(HashMap<String, FeatureAccess> featureWiseAllowed) {
        this.featureWiseAllowed = featureWiseAllowed;
    }
}
