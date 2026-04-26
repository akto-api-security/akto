package com.akto.dto.integration;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;

import java.util.Objects;

/**
 * NewRelic Integration Configuration
 * Stores NewRelic account credentials and configuration per organization
 */
public class NewRelicIntegration {

    // Collection name
    public static final String COLLECTION_NAME = "new_relic_integrations";

    // Field name constants
    public static final String ID = "_id";
    public static final String ORG_ID = "orgId";
    public static final String API_KEY = "apiKey";
    public static final String ACCOUNT_ID = "accountId";
    public static final String REGION = "region";
    public static final String ENABLED = "enabled";
    public static final String LAST_SYNC_TIME = "lastSyncTime";
    public static final String CREATED_AT = "createdAt";
    public static final String UPDATED_AT = "updatedAt";
    public static final String CREATED_BY = "createdBy";

    // Fields
    @BsonId
    private ObjectId id;

    private Integer orgId;
    private String apiKey;          // Encrypted at rest
    private String accountId;       // NewRelic account ID
    private String region;          // "US" or "EU"
    private Boolean enabled;
    private Long lastSyncTime;      // Unix timestamp (ms)
    private Long createdAt;
    private Long updatedAt;
    private String createdBy;

    // ========== Constructors ==========

    public NewRelicIntegration() {}

    public NewRelicIntegration(Integer orgId, String accountId, String region) {
        this.orgId = orgId;
        this.accountId = accountId;
        this.region = region;
        this.enabled = true;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public NewRelicIntegration(ObjectId id, Integer orgId, String apiKey, String accountId,
                              String region, Boolean enabled, Long lastSyncTime,
                              Long createdAt, Long updatedAt, String createdBy) {
        this.id = id;
        this.orgId = orgId;
        this.apiKey = apiKey;
        this.accountId = accountId;
        this.region = region;
        this.enabled = enabled;
        this.lastSyncTime = lastSyncTime;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.createdBy = createdBy;
    }

    // ========== Getters & Setters ==========

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public Integer getOrgId() {
        return orgId;
    }

    public void setOrgId(Integer orgId) {
        this.orgId = orgId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Long getLastSyncTime() {
        return lastSyncTime;
    }

    public void setLastSyncTime(Long lastSyncTime) {
        this.lastSyncTime = lastSyncTime;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    // ========== Utility Methods ==========

    @Override
    public String toString() {
        return "NewRelicIntegration{" +
                "id=" + id +
                ", orgId=" + orgId +
                ", accountId='" + accountId + '\'' +
                ", region='" + region + '\'' +
                ", enabled=" + enabled +
                ", lastSyncTime=" + lastSyncTime +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", createdBy='" + createdBy + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NewRelicIntegration that = (NewRelicIntegration) o;
        return Objects.equals(id, that.id) &&
               Objects.equals(orgId, that.orgId) &&
               Objects.equals(accountId, that.accountId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, orgId, accountId);
    }
}
