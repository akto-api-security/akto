package com.akto.action.endpoint_shield;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dao.endpoint_shield.EndpointRemoteCommandDao;
import com.akto.dao.endpoint_shield.EndpointRemoteCommandExecutionDao;
import com.akto.dto.endpoint_shield.EndpointRemoteCommand;
import com.akto.dto.endpoint_shield.EndpointRemoteCommand.ExecutionSummary;
import com.akto.dto.endpoint_shield.EndpointRemoteCommandExecution;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

public class EndpointRemoteCommandAction extends UserAction {

    private static final LoggerMaker logger =
            new LoggerMaker(EndpointRemoteCommandAction.class, LogDb.DASHBOARD);

    private static final int MAX_LIMIT       = 200;
    private static final int DEFAULT_LIMIT   = 50;
    private static final int MAX_ARGS        = 50;
    private static final int MAX_ARG_LEN     = 1024;
    private static final int MAX_TIMEOUT_SEC = 300;
    private static final int MIN_TIMEOUT_SEC = 1;
    private static final int MAX_EXPIRY_SEC  = 86400;
    private static final int MIN_EXPIRY_SEC  = 60;

    // ── request fields ──────────────────────────────────────────────────────
    @Setter private String command;
    @Setter private List<String> args;
    @Setter private int timeoutSec  = 60;
    @Setter private int expirySeconds = 3600;
    @Setter private String targetType;
    @Setter private List<String> targetDeviceIds;
    @Getter @Setter private String commandId;  // input for cancel/fetch; output for queue
    @Setter private String deviceId;
    @Setter private int limit = DEFAULT_LIMIT;
    @Setter private long createdAfter = 0;

    // ── response fields ──────────────────────────────────────────────────────
    @Getter private List<EndpointRemoteCommand>          commands;
    @Getter private List<EndpointRemoteCommandExecution> executions;

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Queue a new command
    // ─────────────────────────────────────────────────────────────────────────
    public String queueEndpointRemoteCommand() {
        try {
            // Validate command
            if (StringUtils.isBlank(command)) {
                addActionError("command is required");
                return Action.ERROR.toUpperCase();
            }
            String trimmedCmd = command.trim();
            if (trimmedCmd.contains("/") || trimmedCmd.contains("\\")) {
                addActionError("command cannot contain path separators");
                return Action.ERROR.toUpperCase();
            }

            // Validate args
            if (args == null) args = new ArrayList<>();
            if (args.size() > MAX_ARGS) {
                addActionError("args may not exceed " + MAX_ARGS + " entries");
                return Action.ERROR.toUpperCase();
            }
            for (String arg : args) {
                if (arg != null && arg.length() > MAX_ARG_LEN) {
                    addActionError("each arg must be at most " + MAX_ARG_LEN + " characters");
                    return Action.ERROR.toUpperCase();
                }
            }

            // Validate timeout
            if (timeoutSec < MIN_TIMEOUT_SEC || timeoutSec > MAX_TIMEOUT_SEC) {
                addActionError("timeoutSec must be between " + MIN_TIMEOUT_SEC + " and " + MAX_TIMEOUT_SEC);
                return Action.ERROR.toUpperCase();
            }

            // Validate expiry
            if (expirySeconds < MIN_EXPIRY_SEC || expirySeconds > MAX_EXPIRY_SEC) {
                addActionError("expirySeconds must be between " + MIN_EXPIRY_SEC + " and " + MAX_EXPIRY_SEC);
                return Action.ERROR.toUpperCase();
            }

            // Validate targetType
            EndpointRemoteCommand.TargetType target;
            try {
                target = EndpointRemoteCommand.TargetType.valueOf(
                        StringUtils.isBlank(targetType) ? "" : targetType.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                addActionError("targetType must be ALL or SELECTED");
                return Action.ERROR.toUpperCase();
            }

            if (target == EndpointRemoteCommand.TargetType.SELECTED) {
                if (targetDeviceIds == null || targetDeviceIds.isEmpty()) {
                    addActionError("targetDeviceIds is required when targetType is SELECTED");
                    return Action.ERROR.toUpperCase();
                }
            } else {
                targetDeviceIds = new ArrayList<>();
            }

            long nowMs = System.currentTimeMillis();
            String createdBy = getSUser() != null ? getSUser().getLogin() : null;

            EndpointRemoteCommand cmd = new EndpointRemoteCommand();
            cmd.setId(new ObjectId());
            cmd.setAccountId(Context.accountId.get());
            cmd.setCommand(trimmedCmd);
            cmd.setArgs(args);
            cmd.setTimeoutSec(timeoutSec);
            cmd.setExpirySeconds(expirySeconds);
            cmd.setTargetType(target);
            cmd.setTargetDeviceIds(targetDeviceIds);
            cmd.setStatus(EndpointRemoteCommand.Status.ACTIVE);
            cmd.setCreatedBy(createdBy);
            cmd.setCreatedAt(nowMs);
            cmd.setExpiresAt(nowMs + (long) expirySeconds * 1000L);

            EndpointRemoteCommandDao.instance.createIndicesIfAbsent();
            EndpointRemoteCommandDao.instance.insertOne(cmd);

            this.commandId = cmd.getCommandId();
            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            logger.errorAndAddToDb(e, "queueEndpointRemoteCommand failed: " + e.getMessage());
            addActionError("Failed to queue command");
            return Action.ERROR.toUpperCase();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. List commands with execution summary
    // ─────────────────────────────────────────────────────────────────────────
    public String fetchEndpointRemoteCommandList() {
        try {
            int effectiveLimit = (limit <= 0 || limit > MAX_LIMIT) ? DEFAULT_LIMIT : limit;

            EndpointRemoteCommandDao.instance.createIndicesIfAbsent();

            org.bson.conversions.Bson filter = createdAfter > 0
                    ? Filters.gt(EndpointRemoteCommand.CREATED_AT, createdAfter)
                    : Filters.empty();

            this.commands = EndpointRemoteCommandDao.instance.findAll(
                    filter, 0, effectiveLimit,
                    Sorts.descending(EndpointRemoteCommand.CREATED_AT));

            if (this.commands == null) this.commands = new ArrayList<>();

            // Compute execution summary for each command
            EndpointRemoteCommandExecutionDao.instance.createIndicesIfAbsent();
            for (EndpointRemoteCommand cmd : this.commands) {
                String hexId = cmd.getCommandId();
                if (hexId == null) continue;

                List<EndpointRemoteCommandExecution> execs =
                        EndpointRemoteCommandExecutionDao.instance.findAll(
                                Filters.eq(EndpointRemoteCommandExecution.COMMAND_ID, hexId),
                                0, 10000, null);

                ExecutionSummary summary = buildSummary(execs);
                cmd.setExecutionSummary(summary);
            }

            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            logger.errorAndAddToDb(e, "fetchEndpointRemoteCommandList failed: " + e.getMessage());
            addActionError("Failed to fetch commands");
            return Action.ERROR.toUpperCase();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. List executions for a command or device
    // ─────────────────────────────────────────────────────────────────────────
    public String fetchEndpointRemoteCommandExecutions() {
        try {
            int effectiveLimit = (limit <= 0 || limit > MAX_LIMIT) ? DEFAULT_LIMIT : limit;

            EndpointRemoteCommandExecutionDao.instance.createIndicesIfAbsent();

            org.bson.conversions.Bson filter;
            if (!StringUtils.isBlank(commandId)) {
                filter = Filters.eq(EndpointRemoteCommandExecution.COMMAND_ID, commandId.trim());
            } else if (!StringUtils.isBlank(deviceId)) {
                filter = Filters.eq(EndpointRemoteCommandExecution.DEVICE_ID, deviceId.trim());
            } else {
                addActionError("commandId or deviceId is required");
                return Action.ERROR.toUpperCase();
            }

            this.executions = EndpointRemoteCommandExecutionDao.instance.findAll(
                    filter, 0, effectiveLimit,
                    Sorts.descending(EndpointRemoteCommandExecution.EXECUTED_AT));

            if (this.executions == null) this.executions = new ArrayList<>();
            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            logger.errorAndAddToDb(e, "fetchEndpointRemoteCommandExecutions failed: " + e.getMessage());
            addActionError("Failed to fetch executions");
            return Action.ERROR.toUpperCase();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Cancel a command
    // ─────────────────────────────────────────────────────────────────────────
    public String cancelEndpointRemoteCommand() {
        try {
            if (StringUtils.isBlank(commandId)) {
                addActionError("commandId is required");
                return Action.ERROR.toUpperCase();
            }

            ObjectId oid;
            try {
                oid = new ObjectId(commandId.trim());
            } catch (IllegalArgumentException e) {
                addActionError("invalid commandId");
                return Action.ERROR.toUpperCase();
            }

            EndpointRemoteCommandDao.instance.updateOneNoUpsert(
                    Filters.and(
                            Filters.eq("_id", oid),
                            Filters.eq(EndpointRemoteCommand.ACCOUNT_ID, Context.accountId.get())
                    ),
                    Updates.set(EndpointRemoteCommand.STATUS,
                            EndpointRemoteCommand.Status.CANCELLED.name())
            );

            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            logger.errorAndAddToDb(e, "cancelEndpointRemoteCommand failed: " + e.getMessage());
            addActionError("Failed to cancel command");
            return Action.ERROR.toUpperCase();
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static ExecutionSummary buildSummary(List<EndpointRemoteCommandExecution> execs) {
        ExecutionSummary s = new ExecutionSummary();
        if (execs == null) return s;
        s.setTotal(execs.size());
        for (EndpointRemoteCommandExecution e : execs) {
            if (e.getStatus() == null) continue;
            switch (e.getStatus()) {
                case PENDING:   s.setPending(s.getPending() + 1);     break;
                case RUNNING:   s.setRunning(s.getRunning() + 1);     break;
                case COMPLETED: s.setCompleted(s.getCompleted() + 1); break;
                case FAILED:    s.setFailed(s.getFailed() + 1);       break;
            }
        }
        return s;
    }
}
