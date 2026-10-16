package com.akto.action.endpoint_shield;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dao.endpoint_shield.EndpointRemoteCommandDao;
import com.akto.dao.endpoint_shield.EndpointRemoteCommandExecutionDao;
import com.akto.dao.endpoint_shield.EndpointShieldDiagnosticDao;
import com.akto.dto.endpoint_shield.EndpointRemoteCommand;
import com.akto.dto.endpoint_shield.EndpointRemoteCommandExecution;
import com.akto.dto.endpoint_shield.EndpointShieldDiagnostic;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;

/**
 * Device panel → Diagnostics tab: a device's diagnostic history (installer,
 * agent post-provision, on-demand reports), a single report, and on-demand runs.
 *
 * An on-demand run is a remote command named akto-diagnose targeted at one
 * device. The agent handles that name itself (it is never looked up on PATH):
 * it runs the bundled diagnose script, stores the report through the
 * db-abstractor with the execution id, and reports "diagnosticId: <id>" as
 * the command's output.
 */
@Getter
@Setter
public class EndpointShieldDiagnosticsAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(EndpointShieldDiagnosticsAction.class, LogDb.DASHBOARD);
    private static final int MAX_LIST = 20; // the db-abstractor keeps the newest 20 per device

    public static final String DIAGNOSE_COMMAND = "akto-diagnose";
    private static final List<String> PROFILES = Arrays.asList("full", "quick");
    private static final int RUN_TIMEOUT_SEC = 240;     // the agent's own cap for akto-diagnose
    private static final int RUN_EXPIRY_SEC = 3600;     // a device that does not poll within an hour → Expired
    private static final long RUNS_WINDOW_MS = 24L * 3600 * 1000;
    private static final int MAX_RUNS = 5;
    private static final int MAX_RUN_ERROR_CHARS = 1000;
    private static final Pattern DIAGNOSTIC_ID = Pattern.compile("diagnosticId:\\s*([0-9a-f]{24})");

    // Request
    private String deviceId;
    private int limit;
    private String id;
    private String profile;
    private String commandId;

    // Response
    private List<Map<String, Object>> diagnostics;
    private Map<String, Object> diagnostic;
    private List<Map<String, Object>> runs;

    /** Summaries (no report text), newest first. */
    public String fetchEndpointShieldDiagnostics() {
        if (StringUtils.isBlank(deviceId)) {
            addActionError("deviceId is required");
            return Action.ERROR.toUpperCase();
        }
        try {
            int lim = (limit <= 0 || limit > MAX_LIST) ? MAX_LIST : limit;
            List<EndpointShieldDiagnostic> rows = EndpointShieldDiagnosticDao.instance.findAll(
                    Filters.eq(EndpointShieldDiagnostic.DEVICE_ID, deviceId), 0, lim,
                    Sorts.descending(EndpointShieldDiagnostic.CREATED_AT),
                    Projections.exclude(EndpointShieldDiagnostic.REPORT));
            diagnostics = new ArrayList<>(rows.size());
            for (EndpointShieldDiagnostic d : rows) {
                diagnostics.add(toView(d, false));
            }
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "fetchEndpointShieldDiagnostics failed: " + e.getMessage());
            addActionError("Could not load diagnostics");
            return Action.ERROR.toUpperCase();
        }
    }

    /** One report, including its full text. */
    public String fetchEndpointShieldDiagnostic() {
        if (StringUtils.isBlank(id) || !ObjectId.isValid(id)) {
            addActionError("valid id is required");
            return Action.ERROR.toUpperCase();
        }
        try {
            EndpointShieldDiagnostic d = EndpointShieldDiagnosticDao.instance.findOne(Filters.eq("_id", new ObjectId(id)));
            if (d == null) {
                addActionError("diagnostic not found");
                return Action.ERROR.toUpperCase();
            }
            diagnostic = toView(d, true);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "fetchEndpointShieldDiagnostic failed: " + e.getMessage());
            addActionError("Could not load the diagnostic");
            return Action.ERROR.toUpperCase();
        }
    }

    /**
     * Queue an on-demand run for one device. If a run for this device is still
     * waiting or running, returns that one instead of queueing a second.
     */
    public String runEndpointShieldDiagnostic() {
        if (StringUtils.isBlank(deviceId)) {
            addActionError("deviceId is required");
            return Action.ERROR.toUpperCase();
        }
        String p = StringUtils.isBlank(profile) ? "full" : profile.trim();
        if (!PROFILES.contains(p)) {
            addActionError("profile must be one of " + PROFILES);
            return Action.ERROR.toUpperCase();
        }
        try {
            long nowMs = System.currentTimeMillis();
            for (EndpointRemoteCommand c : recentRunCommands(deviceId, 1)) {
                String state = (String) runView(c, deviceId, nowMs).get("state");
                if ("waiting".equals(state) || "running".equals(state)) {
                    commandId = c.getCommandId();
                    return Action.SUCCESS.toUpperCase();
                }
            }

            EndpointRemoteCommand cmd = new EndpointRemoteCommand();
            cmd.setId(new ObjectId());
            cmd.setAccountId(Context.accountId.get());
            cmd.setCommand(DIAGNOSE_COMMAND);
            cmd.setArgs(Arrays.asList("--profile", p));
            cmd.setTimeoutSec(RUN_TIMEOUT_SEC);
            cmd.setExpirySeconds(RUN_EXPIRY_SEC);
            cmd.setTargetType(EndpointRemoteCommand.TargetType.SELECTED);
            cmd.setTargetDeviceIds(Collections.singletonList(deviceId.trim()));
            cmd.setStatus(EndpointRemoteCommand.Status.ACTIVE);
            cmd.setCreatedBy(getSUser() != null ? getSUser().getLogin() : null);
            cmd.setCreatedAt(nowMs);
            cmd.setExpiresAt(nowMs + RUN_EXPIRY_SEC * 1000L);
            EndpointRemoteCommandDao.instance.createIndicesIfAbsent();
            EndpointRemoteCommandDao.instance.insertOne(cmd);
            commandId = cmd.getCommandId();
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "runEndpointShieldDiagnostic failed: " + e.getMessage());
            addActionError("Could not start diagnostics");
            return Action.ERROR.toUpperCase();
        }
    }

    /** This device's on-demand runs from the last 24 hours, newest first, with their state. */
    public String fetchEndpointShieldDiagnosticRuns() {
        if (StringUtils.isBlank(deviceId)) {
            addActionError("deviceId is required");
            return Action.ERROR.toUpperCase();
        }
        try {
            long nowMs = System.currentTimeMillis();
            runs = new ArrayList<>();
            for (EndpointRemoteCommand c : recentRunCommands(deviceId, MAX_RUNS)) {
                runs.add(runView(c, deviceId, nowMs));
            }
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "fetchEndpointShieldDiagnosticRuns failed: " + e.getMessage());
            addActionError("Could not load diagnostic runs");
            return Action.ERROR.toUpperCase();
        }
    }

    /** Cancel a run the device has not finished; only akto-diagnose commands. */
    public String cancelEndpointShieldDiagnosticRun() {
        if (StringUtils.isBlank(commandId) || !ObjectId.isValid(commandId.trim())) {
            addActionError("valid commandId is required");
            return Action.ERROR.toUpperCase();
        }
        try {
            EndpointRemoteCommandDao.instance.updateOneNoUpsert(
                    Filters.and(
                            Filters.eq("_id", new ObjectId(commandId.trim())),
                            Filters.eq(EndpointRemoteCommand.COMMAND, DIAGNOSE_COMMAND),
                            Filters.eq(EndpointRemoteCommand.STATUS, EndpointRemoteCommand.Status.ACTIVE.name())),
                    Updates.set(EndpointRemoteCommand.STATUS, EndpointRemoteCommand.Status.CANCELLED.name()));
            EndpointRemoteCommandAction.failPendingExecutions(commandId.trim());
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "cancelEndpointShieldDiagnosticRun failed: " + e.getMessage());
            addActionError("Could not cancel the run");
            return Action.ERROR.toUpperCase();
        }
    }

    private static List<EndpointRemoteCommand> recentRunCommands(String deviceId, int max) {
        List<EndpointRemoteCommand> l = EndpointRemoteCommandDao.instance.findAll(
                Filters.and(
                        Filters.eq(EndpointRemoteCommand.COMMAND, DIAGNOSE_COMMAND),
                        Filters.eq(EndpointRemoteCommand.TARGET_DEVICE_IDS, deviceId.trim()),
                        Filters.gt(EndpointRemoteCommand.CREATED_AT, System.currentTimeMillis() - RUNS_WINDOW_MS)),
                0, max, Sorts.descending(EndpointRemoteCommand.CREATED_AT));
        return l == null ? Collections.emptyList() : l;
    }

    /**
     * state: waiting (the device has not picked it up) · running · completed ·
     * failed · expired (the device never picked it up) · cancelled.
     */
    private static Map<String, Object> runView(EndpointRemoteCommand c, String deviceId, long nowMs) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("commandId", c.getCommandId());
        List<String> args = c.getArgs();
        m.put("profile", args != null && args.size() >= 2 ? args.get(1) : "full");
        m.put("requestedBy", c.getCreatedBy());
        m.put("createdAt", c.getCreatedAt());
        m.put("expiresAt", c.getExpiresAt());

        EndpointRemoteCommandExecution ex = EndpointRemoteCommandExecutionDao.instance.findOne(Filters.and(
                Filters.eq(EndpointRemoteCommandExecution.COMMAND_ID, c.getCommandId()),
                Filters.eq(EndpointRemoteCommandExecution.DEVICE_ID, deviceId.trim())));
        String state;
        if (ex != null && ex.getStatus() == EndpointRemoteCommandExecution.Status.COMPLETED) {
            state = "completed";
        } else if (ex != null && ex.getStatus() == EndpointRemoteCommandExecution.Status.FAILED) {
            state = EndpointRemoteCommandExecution.ERR_CANCELLED.equals(ex.getErrorReason()) ? "cancelled"
                    : EndpointRemoteCommandExecution.ERR_EXPIRED.equals(ex.getErrorReason()) ? "expired" : "failed";
        } else if (ex != null && ex.getStatus() == EndpointRemoteCommandExecution.Status.RUNNING) {
            state = "running";
        } else if (c.getStatus() == EndpointRemoteCommand.Status.CANCELLED) {
            state = "cancelled";
        } else if (c.getStatus() == EndpointRemoteCommand.Status.EXPIRED || c.getExpiresAt() < nowMs) {
            state = "expired";
        } else {
            // No execution yet, or PENDING: the agent fetched it but has not claimed it.
            state = ex == null ? "waiting" : "running";
        }
        m.put("state", state);
        if (ex != null) {
            m.put("errorReason", ex.getErrorReason());
            m.put("durationMs", ex.getDurationMs());
            m.put("executedAt", ex.getExecutedAt());
            if ("failed".equals(state)) {
                m.put("error", StringUtils.abbreviate(StringUtils.defaultIfBlank(ex.getStderr(), ex.getStdout()), MAX_RUN_ERROR_CHARS));
            }
            if (ex.getStdout() != null) {
                Matcher mt = DIAGNOSTIC_ID.matcher(ex.getStdout());
                if (mt.find()) {
                    m.put("diagnosticId", mt.group(1));
                }
            }
        }
        return m;
    }

    // A flat view with a string id: the ObjectId itself does not serialize usefully to JSON.
    private static Map<String, Object> toView(EndpointShieldDiagnostic d, boolean withReport) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getHexId());
        m.put("deviceId", d.getDeviceId());
        m.put("agentId", d.getAgentId());
        m.put("source", d.getSource());
        m.put("installId", d.getInstallId());
        m.put("executionId", d.getExecutionId());
        m.put("requestedBy", d.getRequestedBy());
        m.put("profile", d.getProfile());
        m.put("outcome", d.getOutcome());
        m.put("agentVersion", d.getAgentVersion());
        m.put("os", d.getOs());
        m.put("verdict", d.getVerdict());
        m.put("healthy", d.isHealthy());
        m.put("findings", d.getFindings());
        m.put("warningCount", d.getWarningCount());
        m.put("errorCount", d.getErrorCount());
        m.put("createdAt", d.getCreatedAt());
        if (withReport) {
            m.put("report", d.getReport());
        }
        return m;
    }
}
