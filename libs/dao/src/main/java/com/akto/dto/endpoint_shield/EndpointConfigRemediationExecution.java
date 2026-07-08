package com.akto.dto.endpoint_shield;

import lombok.Getter;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

/**
 * Records one device's execution of an {@link EndpointConfigRemediation}.
 * Created (PENDING) when the job is queued; updated by the agent
 * as it transitions through RUNNING → COMPLETED / FAILED.
 */
@Getter
@Setter
public class EndpointConfigRemediationExecution {

    public static final String REMEDIATION_ID = "remediationId";
    public static final String ACCOUNT_ID     = "accountId";
    public static final String DEVICE_ID      = "deviceId";
    public static final String STATUS         = "status";
    public static final String RESULT_CONTENT  = "resultContent";
    public static final String RESULT_CHECKSUM = "resultChecksum";
    public static final String DURATION_MS    = "durationMs";
    public static final String EXECUTED_AT    = "executedAt";
    public static final String ERROR_REASON   = "errorReason";

    public enum Status { PENDING, RUNNING, COMPLETED, FAILED }

    // ── Error reasons (stored as string so agents can add new ones freely) ──
    public static final String ERR_PATH_NOT_ALLOWED         = "path_not_allowed";
    public static final String ERR_FILE_CHANGED_SINCE_REVIEW = "file_changed_since_review";
    public static final String ERR_PARSE_FAILED             = "parse_failed";
    public static final String ERR_IO_ERROR                 = "io_error";
    public static final String ERR_TIMEOUT                  = "timeout";
    public static final String ERR_PRESUMED_DEAD_AGENT       = "presumed_dead_agent";
    public static final String ERR_EXPIRED                  = "expired";
    public static final String ERR_CANCELLED                = "cancelled";

    @BsonId
    private ObjectId id;

    private int    accountId;
    private String remediationId;  // hex ObjectId of the parent EndpointConfigRemediation
    private String deviceId;
    private Status status;
    private String resultContent;  // full file text on success (current for PREVIEW, new for APPLY)
    private String resultChecksum; // sha256 hex of resultContent
    private long   durationMs;
    private long   executedAt;     // epoch ms when the agent finished
    private String errorReason;    // one of ERR_* constants, or null

    /** Hex string of the ObjectId — used as a stable key in the frontend. */
    @BsonIgnore
    public String getExecId() {
        return id != null ? id.toHexString() : null;
    }
}
