package com.akto.dto.billing;

import java.util.HashMap;
import java.util.Set;
import org.bson.codecs.pojo.annotations.BsonId;

import com.akto.dao.context.Context;

public class Organization {
    
    // Change this to use a UUID
    @BsonId
    private String id;
    public static final String ID = "_id";
    public static final String NAME = "name";
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

    int lastFeatureMapUpdate;
    public static final String LAST_FEATURE_MAP_UPDATE = "lastFeatureMapUpdate";

    public static final String ON_PREM = "onPrem";
    private boolean onPrem;

    public static final String GRACE_PERIOD = "gracePeriod";

    public static final String HOTJAR_SITE_ID = "hotjarSiteId";

    public String hotjarSiteId = "hotjarSiteId";

    public static final String PLAN_TYPE = "planType";
    public String planType = "planType";

    public static final String TRIAL_MSG = "trialMsg";
    public String trialMsg = "trialMsg";

    public static final String PROTECTIONTRIAL_MSG = "protectionTrialMsg";
    public String protectionTrialMsg = "protectionTrialMsg";

    public static final String AGENTTRIAL_MSG = "agentTrialMsg";
    public String agentTrialMsg = "agentTrialMsg";

    public static final String TEST_TELEMETRY_ENABLED = "testTelemetryEnabled";
    private boolean testTelemetryEnabled;
    private int gracePeriod;

    public static final String _EXPIRED = "expired";
    private boolean expired;

    public static final int NO_SYNC_PERIOD = 60 * 60 * 24 * 3; // 3 days.

    public static final String NAME_LAST_UPDATE = "nameLastUpdate";
    private int nameLastUpdate;

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

    public HashMap<String, FeatureAccess> getFeatureWiseAllowed() {
        return featureWiseAllowed;
    }

    public void setFeatureWiseAllowed(HashMap<String, FeatureAccess> featureWiseAllowed) {
        this.featureWiseAllowed = featureWiseAllowed;
    }

    public boolean isOnPrem() {
        return onPrem;
    }

    public void setOnPrem(boolean onPrem) {
        this.onPrem = onPrem;
    }

    public int getGracePeriod() {
        return gracePeriod;
    }

    public void setGracePeriod(int gracePeriod) {
        this.gracePeriod = gracePeriod;
    }

    public  String getHotjarSiteId() {
        return hotjarSiteId;
    }

    public  void setHotjarSiteId(String hotjarSiteId) {
        this.hotjarSiteId = hotjarSiteId;
    }

    public  String getplanType() {
        return planType;
    }

    public  void setplanType(String planType) {
        this.planType = planType;
    }

    public  String gettrialMsg() {
        return trialMsg;
    }

    public  void settrialMsg(String trialMsg) {
        this.trialMsg = trialMsg;
    }

    public  String getprotectionTrialMsg() {
        return protectionTrialMsg;
    }

    public  void setprotectionTrialMsg(String protectionTrialMsg) {
        this.protectionTrialMsg = protectionTrialMsg;
    }

    public  String getagentTrialMsg() {
        return agentTrialMsg;
    }

    public  void setagentTrialMsg(String agentTrialMsg) {
        this.agentTrialMsg = agentTrialMsg;
    }

    public int getLastFeatureMapUpdate() {
        return lastFeatureMapUpdate;
    }

    public void setLastFeatureMapUpdate(int lastFeatureMapUpdate) {
        this.lastFeatureMapUpdate = lastFeatureMapUpdate;
    }

    public boolean isExpired() {
        return expired;
    }

    public void setExpired(boolean expired) {
        this.expired = expired;
    }

    public boolean checkExpirationWithAktoSync() {

        boolean noConnectivity = (((lastFeatureMapUpdate > 0) &&
                (lastFeatureMapUpdate + Organization.NO_SYNC_PERIOD < Context.now())) &&
                (featureWiseAllowed == null || featureWiseAllowed.isEmpty()));

        /*
         * TODO: no connectivity check to be added later.
         */
        if (expired) {
            return true;
        }

        return false;
    }
    public boolean getTestTelemetryEnabled() {
        return testTelemetryEnabled;
    }

    public void setTestTelemetryEnabled(boolean testTelemetryEnabled) {
        this.testTelemetryEnabled = testTelemetryEnabled;
    }

    public int getNameLastUpdate() {
        return nameLastUpdate;
    }

    public void setNameLastUpdate(int nameLastUpdate) {
        this.nameLastUpdate = nameLastUpdate;
    }
}
