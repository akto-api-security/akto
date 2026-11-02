package com.akto.dto.nhi_governance;

import java.util.List;
import java.util.Map;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One Non-Human Identity (API key, service-account token, MCP server auth token, etc.).
 *
 * This DTO is a strict SUPERSET of the akto-dashboard's
 * com.akto.dto.nhi_governance.NhiIdentity, sharing the {@link #COLLECTION_NAME}
 * "nhi_identities" — so docs written by either side round-trip cleanly:
 *   - dashboard-managed fields (owner, targetResource, accessLevel, expiryDate,
 *     lastRotatedAt, relatedViolationIds, createdBy, updatedBy) keep their
 *     original names and shapes
 *   - endpoint-shield-only fields (prefix, suffix, hash, source, deviceId, ...)
 *     are additive — the dashboard's POJO codec ignores them on read
 *
 * The raw secret value is never stored. Only:
 *   - prefix:  first 4 chars of the secret (display)
 *   - suffix:  last 4 chars of the secret  (display)
 *   - hash:    sha256 of the full secret   (change detection across scans)
 *
 * Natural identity key for cyborg upserts: (deviceId, source, identityName).
 * One row per (device, file, secret-name) — rotating the secret in one file does
 * not affect rows for other files or other devices.
 */
@Getter
@Setter
@NoArgsConstructor
public class NhiIdentity {

    public static final String COLLECTION_NAME = "nhi_identities";

    // --- shared with akto-dashboard's NhiIdentity ---
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

    // --- endpoint-shield-only fields (additive; dashboard ignores) ---
    public static final String PREFIX = "prefix";
    public static final String SUFFIX = "suffix";
    public static final String HASH = "hash";
    public static final String PREVIOUS_HASH = "previousHash";
    public static final String SOURCE = "source";
    public static final String SOURCE_TYPE = "sourceType";
    public static final String DEVICE_ID = "deviceId";
    public static final String DEVICE_LABEL = "deviceLabel";
    public static final String FIRST_SEEN_AT = "firstSeenAt";
    public static final String LAST_SEEN_AT = "lastSeenAt";

    @BsonId
    private ObjectId id;

    // shared with dashboard
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

    // endpoint-shield-only
    private String prefix;
    private String suffix;
    private String hash;
    private String previousHash;
    private String source;
    private String sourceType;
    private String deviceId;
    private String deviceLabel;
    private int firstSeenAt;
    private int lastSeenAt;

    public NhiIdentity(String identityName, String identityType, String contextSource, String agentName) {
        this.identityName = identityName;
        this.identityType = identityType;
        this.contextSource = contextSource;
        this.agentName = agentName;
    }

    @BsonIgnore
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
