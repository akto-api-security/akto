package com.akto.dto.nhi_governance;

import java.util.List;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class NhiPolicy {
    public static final String COLLECTION_NAME = "nhi_policies";

    public static final String ID = "_id";
    public static final String POLICY_NAME = "policyName";
    public static final String DESCRIPTION = "description";
    public static final String STATUS = "status";
    public static final String SCOPE = "scope";
    public static final String TOKEN_SEGREGATION = "tokenSegregation";
    public static final String EXPIRATION_TRACKING = "expirationTracking";
    public static final String ROTATION_ENFORCEMENT = "rotationEnforcement";
    public static final String CREATED_AT = "createdAt";
    public static final String CREATED_BY = "createdBy";
    public static final String UPDATED_AT = "updatedAt";
    public static final String UPDATED_BY = "updatedBy";
    public static final String LAST_TRIGGERED_AT = "lastTriggeredAt";
    public static final String VIOLATION_IDS = "violationIds";

    private ObjectId id;
    private String policyName;
    private String description;
    private String status;
    private Scope scope;
    private TokenSegregation tokenSegregation;
    private ExpirationTracking expirationTracking;
    private RotationEnforcement rotationEnforcement;
    private int createdAt;
    private String createdBy;
    private int updatedAt;
    private String updatedBy;
    private Integer lastTriggeredAt;
    private List<String> violationIds;

    public NhiPolicy(String policyName, String description, String status) {
        this.policyName = policyName;
        this.description = description;
        this.status = status;
    }

    @BsonIgnore
    public String getHexId() {
        return id != null ? id.toHexString() : null;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Scope {
        private List<String> agents;
        private List<String> nhiIds;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class TokenSegregation {
        private boolean enabled;

        public TokenSegregation(boolean enabled) {
            this.enabled = enabled;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ExpirationTracking {
        private boolean enabled;
        private int warningThresholdMonths;
        private boolean flagExpiredTokens;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class RotationEnforcement {
        private boolean enabled;
        private int maxAgeDays = 30;   // violation fires when credential age exceeds this
        private int warningDays = 7;   // Medium-severity warning fires this many days before deadline
    }
}
