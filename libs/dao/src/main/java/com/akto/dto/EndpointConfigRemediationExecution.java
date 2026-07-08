package com.akto.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;

@Getter
@Setter
@NoArgsConstructor
public class EndpointConfigRemediationExecution {

    public static final String ID = "id";
    // @BsonProperty breaks the id→_id convention; MongoDB manages its own ObjectId _id.
    @BsonProperty("id")
    private String id;

    public static final String REMEDIATION_ID = "remediationId";
    private String remediationId;

    public static final String DEVICE_ID = "deviceId";
    private String deviceId;

    public static final String AGENT_ID = "agentId";
    private String agentId;

    public static final String STATUS = "status";
    private Status status;

    /** Full file text on success: current content (PREVIEW) or newly-written content (APPLY). */
    public static final String RESULT_CONTENT = "resultContent";
    private String resultContent;

    public static final String RESULT_CHECKSUM = "resultChecksum";
    private String resultChecksum;

    public static final String DURATION_MS = "durationMs";
    private long durationMs;

    public static final String EXECUTED_AT = "executedAt";
    private long executedAt;

    public static final String ERROR_REASON = "errorReason";
    private String errorReason = "";

    public static final String CREATED_AT = "createdAt";
    private long createdAt;

    public static final String UPDATED_AT = "updatedAt";
    private long updatedAt;

    /**
     * Transient fields populated by the lazy fan-out query so the agent poll
     * response can include job details without exposing internal IDs.
     */
    @BsonIgnore
    private String mode;
    @BsonIgnore
    private String toolName;
    @BsonIgnore
    private String configPath;
    @BsonIgnore
    private String format;
    @BsonIgnore
    private String newContent;
    @BsonIgnore
    private String expectedChecksumBeforeApply;

    public enum Status {
        PENDING, RUNNING, COMPLETED, FAILED
    }
}
