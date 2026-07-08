package com.akto.action;

import com.akto.data_actor.DbLayer;
import com.akto.dto.EndpointConfigRemediation;
import com.akto.dto.EndpointConfigRemediationExecution;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.opensymphony.xwork2.ActionSupport;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EndpointConfigRemediationAction extends ActionSupport {

    private static final LoggerMaker loggerMaker = new LoggerMaker(EndpointConfigRemediationAction.class, LogDb.DB_ABS);

    // Shared request fields
    private String agentId;
    private String deviceId;
    private String executionId;
    private String remediationId;
    private String status;

    // Response lists
    private List<EndpointConfigRemediationExecution> executions;
    private List<EndpointConfigRemediation> remediations;

    // -------------------------------------------------------------------------
    // Agent: fetchEndpointConfigRemediations
    // Lazy fan-out — returns at most 10 PENDING executions for this device.
    // struts.xml includeProperties limits response to: id, mode, configPath, format, newContent, expectedChecksumBeforeApply
    // -------------------------------------------------------------------------

    public String fetchEndpointConfigRemediations() {
        try {
            if (agentId == null || agentId.isEmpty()) {
                addActionError("agentId is required");
                return ERROR.toUpperCase();
            }
            if (deviceId == null || deviceId.isEmpty()) {
                addActionError("deviceId is required");
                return ERROR.toUpperCase();
            }
            this.executions = DbLayer.fetchPendingEndpointConfigRemediationExecutions(agentId, deviceId);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in fetchEndpointConfigRemediations: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }

    // -------------------------------------------------------------------------
    // Agent: updateEndpointConfigRemediationStatus (CAS claim)
    // Returns 409 CONFLICT if another agent already claimed this execution.
    // -------------------------------------------------------------------------

    public String updateEndpointConfigRemediationStatus() {
        try {
            if (executionId == null || executionId.isEmpty()
                    || agentId == null || agentId.isEmpty()
                    || deviceId == null || deviceId.isEmpty()
                    || status == null) {
                addActionError("executionId, agentId, deviceId, and status are required");
                return ERROR.toUpperCase();
            }
            EndpointConfigRemediationExecution.Status newStatus;
            try {
                newStatus = EndpointConfigRemediationExecution.Status.valueOf(status);
            } catch (IllegalArgumentException e) {
                addActionError("Invalid status: " + status);
                return ERROR.toUpperCase();
            }
            if (newStatus != EndpointConfigRemediationExecution.Status.RUNNING
                    && newStatus != EndpointConfigRemediationExecution.Status.FAILED) {
                addActionError("Allowed transitions: PENDING → RUNNING or PENDING → FAILED");
                return ERROR.toUpperCase();
            }
            int r = DbLayer.claimEndpointConfigRemediationExecution(executionId, agentId, deviceId, newStatus);
            if (r == 1) return "CONFLICT";
            if (r < 0) {
                addActionError("Execution not found");
                return ERROR.toUpperCase();
            }
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in updateEndpointConfigRemediationStatus: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }

    // -------------------------------------------------------------------------
    // Agent: updateEndpointConfigRemediationResult
    // -------------------------------------------------------------------------

    private ExecutionResult result;

    @Getter
    @Setter
    public static class ExecutionResult {
        private String executionId;
        private String agentId;
        private String deviceId;
        private String status;
        private String resultContent;
        private String resultChecksum;
        private long durationMs;
        private long executedAt;
        private String errorReason = "";
    }

    public String updateEndpointConfigRemediationResult() {
        try {
            if (result == null) {
                addActionError("result is required");
                return ERROR.toUpperCase();
            }
            if (result.getExecutionId() == null || result.getDeviceId() == null || result.getStatus() == null) {
                addActionError("result.executionId, result.deviceId, and result.status are required");
                return ERROR.toUpperCase();
            }
            EndpointConfigRemediationExecution.Status newStatus;
            try {
                newStatus = EndpointConfigRemediationExecution.Status.valueOf(result.getStatus());
            } catch (IllegalArgumentException e) {
                addActionError("Invalid status: " + result.getStatus());
                return ERROR.toUpperCase();
            }
            if (newStatus != EndpointConfigRemediationExecution.Status.COMPLETED
                    && newStatus != EndpointConfigRemediationExecution.Status.FAILED) {
                addActionError("Result status must be COMPLETED or FAILED");
                return ERROR.toUpperCase();
            }
            boolean updated = DbLayer.updateEndpointConfigRemediationResult(
                    result.getExecutionId(), result.getDeviceId(), newStatus,
                    result.getResultContent(), result.getResultChecksum(),
                    result.getDurationMs(), result.getExecutedAt(), result.getErrorReason());
            if (!updated) {
                addActionError("Execution not found or invalid transition");
                return ERROR.toUpperCase();
            }
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in updateEndpointConfigRemediationResult: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }

    // -------------------------------------------------------------------------
    // Dashboard: queueEndpointConfigRemediation
    // -------------------------------------------------------------------------

    private String mode;
    private String toolName;
    private String configPath;
    private String format;
    private String newContent;
    private String expectedChecksumBeforeApply;
    private String findingRefId;
    private int expirySeconds;
    private String targetType;
    private List<String> targetDeviceIds;
    private String createdBy;
    private String queuedRemediationId; // response field

    public String queueEndpointConfigRemediation() {
        try {
            if (mode == null || mode.isEmpty()) {
                addActionError("mode is required");
                return ERROR.toUpperCase();
            }
            EndpointConfigRemediation.Mode jobMode;
            try {
                jobMode = EndpointConfigRemediation.Mode.valueOf(mode);
            } catch (IllegalArgumentException e) {
                addActionError("mode must be PREVIEW or APPLY");
                return ERROR.toUpperCase();
            }
            if (configPath == null || configPath.isEmpty()) {
                addActionError("configPath is required");
                return ERROR.toUpperCase();
            }
            if (toolName == null || toolName.isEmpty()) {
                addActionError("toolName is required");
                return ERROR.toUpperCase();
            }
            EndpointConfigRemediation.Format jobFormat;
            try {
                jobFormat = EndpointConfigRemediation.Format.valueOf(format);
            } catch (IllegalArgumentException e) {
                addActionError("format must be JSON or TOML");
                return ERROR.toUpperCase();
            }
            if (jobMode == EndpointConfigRemediation.Mode.APPLY) {
                if (newContent == null || newContent.isEmpty()) {
                    addActionError("newContent is required for APPLY jobs");
                    return ERROR.toUpperCase();
                }
                if (expectedChecksumBeforeApply == null || expectedChecksumBeforeApply.isEmpty()) {
                    addActionError("expectedChecksumBeforeApply is required for APPLY jobs");
                    return ERROR.toUpperCase();
                }
                if (newContent.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 10 * 1024 * 1024) {
                    addActionError("newContent must be at most 10MB");
                    return ERROR.toUpperCase();
                }
            }

            // PREVIEW jobs are short-lived (user is watching synchronously); APPLY jobs get a
            // generous window since a laptop may be offline when the fix is queued.
            int defaultExpiry = jobMode == EndpointConfigRemediation.Mode.PREVIEW ? 300 : 86400;
            if (expirySeconds < 60) expirySeconds = expirySeconds <= 0 ? defaultExpiry : 60;
            if (expirySeconds > 86400) expirySeconds = 86400;

            EndpointConfigRemediation.TargetType tType;
            try {
                tType = EndpointConfigRemediation.TargetType.valueOf(
                        targetType != null ? targetType : "SELECTED");
            } catch (IllegalArgumentException e) {
                addActionError("targetType must be ALL or SELECTED");
                return ERROR.toUpperCase();
            }
            if (tType == EndpointConfigRemediation.TargetType.SELECTED
                    && (targetDeviceIds == null || targetDeviceIds.isEmpty())) {
                addActionError("targetDeviceIds must be non-empty when targetType is SELECTED");
                return ERROR.toUpperCase();
            }

            String id = DbLayer.queueEndpointConfigRemediation(
                    jobMode, toolName, configPath, jobFormat, newContent,
                    expectedChecksumBeforeApply, findingRefId, expirySeconds,
                    tType,
                    tType == EndpointConfigRemediation.TargetType.ALL
                            ? Collections.emptyList() : targetDeviceIds,
                    createdBy);
            if (id == null) {
                addActionError("Failed to queue remediation");
                return ERROR.toUpperCase();
            }
            this.queuedRemediationId = id;
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in queueEndpointConfigRemediation: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }

    // -------------------------------------------------------------------------
    // Dashboard: fetchEndpointConfigRemediationList (with execution summary)
    // -------------------------------------------------------------------------

    private long createdAfter;
    private int limit;

    public String fetchEndpointConfigRemediationList() {
        try {
            this.remediations = DbLayer.fetchEndpointConfigRemediationList(createdAfter, limit);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in fetchEndpointConfigRemediationList: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }

    // -------------------------------------------------------------------------
    // Dashboard: fetchEndpointConfigRemediationExecutions (per-device drill-down)
    // -------------------------------------------------------------------------

    public String fetchEndpointConfigRemediationExecutions() {
        try {
            if (remediationId == null || remediationId.isEmpty()) {
                addActionError("remediationId is required");
                return ERROR.toUpperCase();
            }
            this.executions = DbLayer.fetchEndpointConfigRemediationExecutions(remediationId, limit);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in fetchEndpointConfigRemediationExecutions: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }

    // -------------------------------------------------------------------------
    // Dashboard: cancelEndpointConfigRemediation
    // -------------------------------------------------------------------------

    public String cancelEndpointConfigRemediation() {
        try {
            if (remediationId == null || remediationId.isEmpty()) {
                addActionError("remediationId is required");
                return ERROR.toUpperCase();
            }
            boolean cancelled = DbLayer.cancelEndpointConfigRemediation(remediationId);
            if (!cancelled) {
                addActionError("Remediation not found or already cancelled");
                return ERROR.toUpperCase();
            }
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in cancelEndpointConfigRemediation: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }
}
