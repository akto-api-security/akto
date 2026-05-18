package com.akto.dto.nhi_governance;

import java.util.List;
import java.util.Map;
import org.bson.types.ObjectId;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class NhiIdentity {
    public static final String COLLECTION_NAME = "nhi_identities";

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

    public NhiIdentity(String identityName, String identityType, String contextSource, String agentName) {
        this.identityName = identityName;
        this.identityType = identityType;
        this.contextSource = contextSource;
        this.agentName = agentName;
    }

    public String getHexId() {
        return this.id != null ? this.id.toHexString() : null;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Owner {
        private String email;
        private String name;

        public Owner(String email, String name) {
            this.email = email;
            this.name = name;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class TargetResource {
        private String resourceName;
        private String resourceType;
        private String accessLevel;

        public TargetResource(String resourceName, String resourceType, String accessLevel) {
            this.resourceName = resourceName;
            this.resourceType = resourceType;
            this.accessLevel = accessLevel;
        }
    }
}
