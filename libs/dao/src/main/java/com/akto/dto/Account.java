package com.akto.dto;

public class Account {
    private int id;
    private String name;
    private boolean isDefault = false;
    private String timezone = "US/Pacific";
    public static final String INACTIVE_STR = "inactive";

    public static final String HYBRID_SAAS_ACCOUNT = "hybridSaasAccount";
    private boolean inactive = false;
    private int statusChangeTimestamp = 0;

    private boolean hybridSaasAccount;
    private boolean mergingRunning = false;

    private int mergingInitiateTs = 0;

    public static final String HYBRID_TESTING_ENABLED = "hybridTestingEnabled";
    private boolean hybridTestingEnabled;

    public Account() {}

    public Account(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public boolean isInactive() {
        return inactive;
    }

    public void setInactive(boolean inactive) {
        this.inactive = inactive;
    }

    public int getStatusChangeTimestamp() {
        return statusChangeTimestamp;
    }

    public void setStatusChangeTimestamp(int statusChangeTimestamp) {
        this.statusChangeTimestamp = statusChangeTimestamp;
    }

    public boolean getMergingRunning() {
        return mergingRunning;
    }

    public void setMergingRunning(boolean mergingRunning) {
        this.mergingRunning = mergingRunning;
    }

    public int getMergingInitiateTs() {
        return mergingInitiateTs;
    }

    public void setMergingInitiateTs(int mergingInitiateTs) {
        this.mergingInitiateTs = mergingInitiateTs;
    }

    public boolean getHybridSaasAccount() {
        return hybridSaasAccount;
    }

    public void setHybridSaasAccount(boolean hybridSaasAccount) {
        this.hybridSaasAccount = hybridSaasAccount;
    }

    public boolean getHybridTestingEnabled() {
        return hybridTestingEnabled;
    }

    public void setHybridTestingEnabled(boolean hybridTestingEnabled) {
        this.hybridTestingEnabled = hybridTestingEnabled;
    }
}
