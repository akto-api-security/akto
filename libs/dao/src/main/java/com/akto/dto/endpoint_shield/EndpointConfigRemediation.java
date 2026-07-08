package com.akto.dto.endpoint_shield;

import lombok.Getter;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import java.util.List;

/**
 * A config-file fix (fetch current content, or save edited content) queued for
 * one endpoint device. The agent polls, applies it, and reports back via
 * EndpointConfigRemediationExecution.
 */
@Getter
@Setter
public class EndpointConfigRemediation {

    public static final String ACCOUNT_ID    = "accountId";
    public static final String MODE          = "mode";
    public static final String TOOL_NAME     = "toolName";
    public static final String CONFIG_PATH   = "configPath";
    public static final String FORMAT        = "format";
    public static final String NEW_CONTENT   = "newContent";
    public static final String EXPECTED_CHECKSUM_BEFORE_APPLY = "expectedChecksumBeforeApply";
    public static final String FINDING_REF_ID = "findingRefId";
    public static final String EXPIRY_SECONDS = "expirySeconds";
    public static final String TARGET_TYPE   = "targetType";
    public static final String TARGET_DEVICE_IDS = "targetDeviceIds";
    public static final String STATUS        = "status";
    public static final String CREATED_BY    = "createdBy";
    public static final String CREATED_AT    = "createdAt";
    public static final String EXPIRES_AT    = "expiresAt";

    public enum Mode       { PREVIEW, APPLY }
    public enum Format     { JSON, TOML }
    public enum TargetType { ALL, SELECTED }
    public enum Status     { ACTIVE, CANCELLED, EXPIRED }

    @BsonId
    private ObjectId id;

    private int     accountId;
    private Mode    mode;
    private String  toolName;
    private String  configPath;
    private Format  format;
    private String  newContent;                    // full replacement file text; APPLY only
    private String  expectedChecksumBeforeApply;    // sha256 hex; required for APPLY
    private String  findingRefId;                   // links back to the originating threat event
    private int     expirySeconds;
    private TargetType targetType;
    private List<String> targetDeviceIds;
    private Status  status;
    private String  createdBy;
    private long    createdAt;   // epoch ms
    private long    expiresAt;   // epoch ms

    /** Hex string of the ObjectId — returned to the frontend as remediationId. */
    @BsonIgnore
    public String getRemediationId() {
        return id != null ? id.toHexString() : null;
    }

    /** Computed at query time; never stored in MongoDB. */
    @BsonIgnore
    private ExecutionSummary executionSummary;

    @Getter
    @Setter
    public static class ExecutionSummary {
        private int total;
        private int pending;
        private int running;
        private int completed;
        private int failed;
    }
}
