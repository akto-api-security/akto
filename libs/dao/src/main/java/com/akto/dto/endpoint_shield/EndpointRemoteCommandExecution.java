package com.akto.dto.endpoint_shield;

import lombok.Getter;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

/**
 * Records one device's execution of an {@link EndpointRemoteCommand}.
 * Created (PENDING) when the command is queued; updated by the agent
 * as it transitions through RUNNING → COMPLETED / FAILED.
 */
@Getter
@Setter
public class EndpointRemoteCommandExecution {

    public static final String COMMAND_ID   = "commandId";
    public static final String ACCOUNT_ID   = "accountId";
    public static final String DEVICE_ID    = "deviceId";
    public static final String STATUS       = "status";
    public static final String STDOUT       = "stdout";
    public static final String STDERR       = "stderr";
    public static final String EXIT_CODE    = "exitCode";
    public static final String DURATION_MS  = "durationMs";
    public static final String EXECUTED_AT  = "executedAt";
    public static final String ERROR_REASON = "errorReason";

    public enum Status { PENDING, RUNNING, COMPLETED, FAILED }

    // ── Error reasons (stored as string so agents can add new ones freely) ──
    public static final String ERR_TIMEOUT             = "timeout";
    public static final String ERR_EXEC_ERROR          = "exec_error";
    public static final String ERR_PRESUMED_DEAD_AGENT = "presumed_dead_agent";
    public static final String ERR_EXPIRED             = "expired";
    public static final String ERR_CANCELLED           = "cancelled";

    @BsonId
    private ObjectId id;

    private int    accountId;
    private String commandId;   // hex ObjectId of the parent EndpointRemoteCommand
    private String deviceId;
    private Status status;
    private String stdout;
    private String stderr;
    private int    exitCode;
    private long   durationMs;
    private long   executedAt;  // epoch ms when the agent finished
    private String errorReason; // one of ERR_* constants, or null

    /** Hex string of the ObjectId — used as a stable key in the frontend. */
    @BsonIgnore
    public String getExecId() {
        return id != null ? id.toHexString() : null;
    }
}
