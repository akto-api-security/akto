package com.akto.action;

import com.akto.data_actor.DbLayer;
import com.akto.dto.EndpointRemoteCommand;
import com.akto.dto.EndpointRemoteCommandExecution;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.opensymphony.xwork2.ActionSupport;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EndpointRemoteCommandAction extends ActionSupport {

    private static final LoggerMaker loggerMaker = new LoggerMaker(EndpointRemoteCommandAction.class, LogDb.DB_ABS);

    // Shared request fields
    private String agentId;
    private String deviceId;
    private String executionId;
    private String commandId;
    private String status;

    // Response lists
    private List<EndpointRemoteCommandExecution> executions;
    private List<EndpointRemoteCommand> commands;

    // -------------------------------------------------------------------------
    // 3a. Agent: fetchEndpointRemoteCommands
    // Lazy fan-out — returns at most 10 PENDING executions for this device.
    // struts.xml includeProperties limits response to: id, command, args, timeoutSec
    // -------------------------------------------------------------------------

    public String fetchEndpointRemoteCommands() {
        try {
            if (agentId == null || agentId.isEmpty()) {
                addActionError("agentId is required");
                return ERROR.toUpperCase();
            }
            if (deviceId == null || deviceId.isEmpty()) {
                addActionError("deviceId is required");
                return ERROR.toUpperCase();
            }
            this.executions = DbLayer.fetchPendingEndpointRemoteCommandExecutions(agentId, deviceId);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in fetchEndpointRemoteCommands: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }

    // -------------------------------------------------------------------------
    // 3b. Agent: updateEndpointRemoteCommandStatus (CAS claim)
    // Returns 409 CONFLICT if another agent already claimed this execution.
    // -------------------------------------------------------------------------

    public String updateEndpointRemoteCommandStatus() {
        try {
            if (executionId == null || executionId.isEmpty()
                    || agentId == null || agentId.isEmpty()
                    || deviceId == null || deviceId.isEmpty()
                    || status == null) {
                addActionError("executionId, agentId, deviceId, and status are required");
                return ERROR.toUpperCase();
            }
            EndpointRemoteCommandExecution.Status newStatus;
            try {
                newStatus = EndpointRemoteCommandExecution.Status.valueOf(status);
            } catch (IllegalArgumentException e) {
                addActionError("Invalid status: " + status);
                return ERROR.toUpperCase();
            }
            if (newStatus != EndpointRemoteCommandExecution.Status.RUNNING
                    && newStatus != EndpointRemoteCommandExecution.Status.FAILED) {
                addActionError("Allowed transitions: PENDING → RUNNING or PENDING → FAILED");
                return ERROR.toUpperCase();
            }
            int r = DbLayer.claimEndpointRemoteCommandExecution(executionId, agentId, deviceId, newStatus);
            if (r == 1) return "CONFLICT";
            if (r < 0) {
                addActionError("Execution not found");
                return ERROR.toUpperCase();
            }
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in updateEndpointRemoteCommandStatus: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }

    // -------------------------------------------------------------------------
    // 3c. Agent: updateEndpointRemoteCommandResult
    // -------------------------------------------------------------------------

    private ExecutionResult result;

    @Getter
    @Setter
    public static class ExecutionResult {
        private String executionId;
        private String agentId;
        private String deviceId;
        private String status;
        private String stdout;
        private String stderr;
        private int exitCode = -1;
        private long durationMs;
        private long executedAt;
        private String errorReason = "";
    }

    public String updateEndpointRemoteCommandResult() {
        try {
            if (result == null) {
                addActionError("result is required");
                return ERROR.toUpperCase();
            }
            if (result.getExecutionId() == null || result.getDeviceId() == null || result.getStatus() == null) {
                addActionError("result.executionId, result.deviceId, and result.status are required");
                return ERROR.toUpperCase();
            }
            EndpointRemoteCommandExecution.Status newStatus;
            try {
                newStatus = EndpointRemoteCommandExecution.Status.valueOf(result.getStatus());
            } catch (IllegalArgumentException e) {
                addActionError("Invalid status: " + result.getStatus());
                return ERROR.toUpperCase();
            }
            if (newStatus != EndpointRemoteCommandExecution.Status.COMPLETED
                    && newStatus != EndpointRemoteCommandExecution.Status.FAILED) {
                addActionError("Result status must be COMPLETED or FAILED");
                return ERROR.toUpperCase();
            }
            boolean updated = DbLayer.updateEndpointRemoteCommandResult(
                    result.getExecutionId(), result.getDeviceId(), newStatus,
                    result.getStdout(), result.getStderr(),
                    result.getExitCode(), result.getDurationMs(),
                    result.getExecutedAt(), result.getErrorReason());
            if (!updated) {
                addActionError("Execution not found or invalid transition");
                return ERROR.toUpperCase();
            }
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in updateEndpointRemoteCommandResult: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }

    // -------------------------------------------------------------------------
    // 4a. Dashboard: queueEndpointRemoteCommand
    // -------------------------------------------------------------------------

    private String command;
    private List<String> args;
    private int timeoutSec;
    private int expirySeconds;
    private String targetType;
    private List<String> targetDeviceIds;
    private String createdBy;
    private String queuedCommandId; // response field

    public String queueEndpointRemoteCommand() {
        try {
            if (command == null || command.isEmpty()) {
                addActionError("command is required");
                return ERROR.toUpperCase();
            }
            if (command.contains("/") || command.contains("\\")) {
                addActionError("command must not contain path separators");
                return ERROR.toUpperCase();
            }
            if (args != null) {
                if (args.size() > 50) {
                    addActionError("args must have at most 50 elements");
                    return ERROR.toUpperCase();
                }
                for (String arg : args) {
                    if (arg != null && arg.length() > 1024) {
                        addActionError("each arg must be at most 1024 characters");
                        return ERROR.toUpperCase();
                    }
                }
            }
            if (timeoutSec < 0) timeoutSec = 0;
            if (timeoutSec > 300) timeoutSec = 300;
            if (expirySeconds < 60) expirySeconds = expirySeconds <= 0 ? 3600 : 60;
            if (expirySeconds > 86400) expirySeconds = 86400;

            EndpointRemoteCommand.TargetType tType;
            try {
                tType = EndpointRemoteCommand.TargetType.valueOf(
                        targetType != null ? targetType : "ALL");
            } catch (IllegalArgumentException e) {
                addActionError("targetType must be ALL or SELECTED");
                return ERROR.toUpperCase();
            }
            if (tType == EndpointRemoteCommand.TargetType.SELECTED
                    && (targetDeviceIds == null || targetDeviceIds.isEmpty())) {
                addActionError("targetDeviceIds must be non-empty when targetType is SELECTED");
                return ERROR.toUpperCase();
            }

            String id = DbLayer.queueEndpointRemoteCommand(
                    command,
                    args != null ? args : Collections.emptyList(),
                    timeoutSec, expirySeconds, tType,
                    tType == EndpointRemoteCommand.TargetType.ALL
                            ? Collections.emptyList() : targetDeviceIds,
                    createdBy);
            if (id == null) {
                addActionError("Failed to queue command");
                return ERROR.toUpperCase();
            }
            this.queuedCommandId = id;
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in queueEndpointRemoteCommand: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }

    // -------------------------------------------------------------------------
    // 4b. Dashboard: fetchEndpointRemoteCommandList (with execution summary)
    // -------------------------------------------------------------------------

    private long createdAfter;
    private int limit;

    public String fetchEndpointRemoteCommandList() {
        try {
            this.commands = DbLayer.fetchEndpointRemoteCommandList(createdAfter, limit);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in fetchEndpointRemoteCommandList: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }

    // -------------------------------------------------------------------------
    // 4c. Dashboard: fetchEndpointRemoteCommandExecutions (per-device drill-down)
    // -------------------------------------------------------------------------

    public String fetchEndpointRemoteCommandExecutions() {
        try {
            if (commandId == null || commandId.isEmpty()) {
                addActionError("commandId is required");
                return ERROR.toUpperCase();
            }
            this.executions = DbLayer.fetchEndpointRemoteCommandExecutions(commandId, limit);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in fetchEndpointRemoteCommandExecutions: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }

    // -------------------------------------------------------------------------
    // 4d. Dashboard: cancelEndpointRemoteCommand
    // -------------------------------------------------------------------------

    public String cancelEndpointRemoteCommand() {
        try {
            if (commandId == null || commandId.isEmpty()) {
                addActionError("commandId is required");
                return ERROR.toUpperCase();
            }
            boolean cancelled = DbLayer.cancelEndpointRemoteCommand(commandId);
            if (!cancelled) {
                addActionError("Command not found or already cancelled");
                return ERROR.toUpperCase();
            }
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in cancelEndpointRemoteCommand: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }
}
