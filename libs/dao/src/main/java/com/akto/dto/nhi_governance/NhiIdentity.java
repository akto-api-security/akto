package com.akto.dto.nhi_governance;

import java.util.List;
import java.util.Map;
import org.bson.types.ObjectId;

public class NhiIdentity {
    public static final String COLLECTION_NAME = "nhi_identities";

    // Field names for MongoDB
    public static final String ID = "_id";
    public static final String IDENTITY_NAME = "identityName";
    public static final String IDENTITY_TYPE = "identityType";
    public static final String CONTEXT_SOURCE = "contextSource";
    public static final String AGENT_NAME = "agentName";
    public static final String AGENT_TYPE = "agentType";
    public static final String OWNER = "owner";
    public static final String TARGET_RESOURCE = "targetResource";
    public static final String ACCESS_LEVEL = "accessLevel";
    public static final String EXPIRY_DATE = "expiryDate";
    public static final String CREATED_AT = "createdAt";
    public static final String CREATED_BY = "createdBy";
    public static final String UPDATED_AT = "updatedAt";
    public static final String UPDATED_BY = "updatedBy";
    public static final String LAST_USED_AT = "lastUsedAt";
    public static final String LAST_ROTATED_AT = "lastRotatedAt";
    public static final String STATUS = "status";
    public static final String RISK_LEVEL = "riskLevel";
    public static final String METADATA = "metadata";
    public static final String RELATED_VIOLATION_IDS = "relatedViolationIds";

    private ObjectId id;
    private String identityName;
    private String identityType;
    private String contextSource;
    private String agentName;
    private String agentType;
    private Owner owner;
    private List<TargetResource> targetResource;
    private String accessLevel;
    private int expiryDate;
    private int createdAt;
    private String createdBy;
    private int updatedAt;
    private String updatedBy;
    private int lastUsedAt;
    private int lastRotatedAt;
    private String status;
    private String riskLevel;
    private Map<String, Object> metadata;
    private List<String> relatedViolationIds;

    public NhiIdentity() {}

    public NhiIdentity(String identityName, String identityType, String contextSource, String agentName) {
        this.identityName = identityName;
        this.identityType = identityType;
        this.contextSource = contextSource;
        this.agentName = agentName;
    }

    // Getters and Setters
    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getHexId() {
        return this.id != null ? this.id.toHexString() : null;
    }

    public String getIdentityName() {
        return identityName;
    }

    public void setIdentityName(String identityName) {
        this.identityName = identityName;
    }

    public String getIdentityType() {
        return identityType;
    }

    public void setIdentityType(String identityType) {
        this.identityType = identityType;
    }

    public String getContextSource() {
        return contextSource;
    }

    public void setContextSource(String contextSource) {
        this.contextSource = contextSource;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getAgentType() {
        return agentType;
    }

    public void setAgentType(String agentType) {
        this.agentType = agentType;
    }

    public Owner getOwner() {
        return owner;
    }

    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    public List<TargetResource> getTargetResource() {
        return targetResource;
    }

    public void setTargetResource(List<TargetResource> targetResource) {
        this.targetResource = targetResource;
    }

    public String getAccessLevel() {
        return accessLevel;
    }

    public void setAccessLevel(String accessLevel) {
        this.accessLevel = accessLevel;
    }

    public int getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(int expiryDate) {
        this.expiryDate = expiryDate;
    }

    public int getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(int createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public int getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(int updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public int getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(int lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public int getLastRotatedAt() {
        return lastRotatedAt;
    }

    public void setLastRotatedAt(int lastRotatedAt) {
        this.lastRotatedAt = lastRotatedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public List<String> getRelatedViolationIds() {
        return relatedViolationIds;
    }

    public void setRelatedViolationIds(List<String> relatedViolationIds) {
        this.relatedViolationIds = relatedViolationIds;
    }

    // Inner class for Owner
    public static class Owner {
        private String email;
        private String name;

        public Owner() {}

        public Owner(String email, String name) {
            this.email = email;
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    // Inner class for TargetResource
    public static class TargetResource {
        private String resourceName;
        private String resourceType;
        private String accessLevel;

        public TargetResource() {}

        public TargetResource(String resourceName, String resourceType, String accessLevel) {
            this.resourceName = resourceName;
            this.resourceType = resourceType;
            this.accessLevel = accessLevel;
        }

        public String getResourceName() {
            return resourceName;
        }

        public void setResourceName(String resourceName) {
            this.resourceName = resourceName;
        }

        public String getResourceType() {
            return resourceType;
        }

        public void setResourceType(String resourceType) {
            this.resourceType = resourceType;
        }

        public String getAccessLevel() {
            return accessLevel;
        }

        public void setAccessLevel(String accessLevel) {
            this.accessLevel = accessLevel;
        }
    }
}
