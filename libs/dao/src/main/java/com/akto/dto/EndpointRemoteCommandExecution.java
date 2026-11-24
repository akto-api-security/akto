package com.akto.dto;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;

@Getter
@Setter
@NoArgsConstructor
public class EndpointRemoteCommandExecution {

    public static final String ID = "id";
    // @BsonProperty breaks the id→_id convention; MongoDB manages its own ObjectId _id.
    @BsonProperty("id")
    private String id;

    public static final String COMMAND_ID = "commandId";
    private String commandId;

    public static final String DEVICE_ID = "deviceId";
    private String deviceId;

    public static final String AGENT_ID = "agentId";
    private String agentId;

    public static final String STATUS = "status";
    private Status status;

    public static final String STDOUT = "stdout";
    private String stdout;

    public static final String STDERR = "stderr";
    private String stderr;

    public static final String EXIT_CODE = "exitCode";
    private int exitCode = -1;

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
     * response can include command details without exposing internal IDs.
     */
    @BsonIgnore
    private String command;
    @BsonIgnore
    private List<String> args;
    @BsonIgnore
    private int timeoutSec;

    public enum Status {
        PENDING, RUNNING, COMPLETED, FAILED
    }
}
