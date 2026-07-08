package com.akto.action.endpoint_shield;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dao.endpoint_shield.EndpointConfigRemediationDao;
import com.akto.dao.endpoint_shield.EndpointConfigRemediationExecutionDao;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dto.endpoint_shield.EndpointConfigRemediation;
import com.akto.dto.endpoint_shield.EndpointConfigRemediation.ExecutionSummary;
import com.akto.dto.endpoint_shield.EndpointConfigRemediationExecution;
import com.akto.dto.monitoring.ModuleInfo;
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

public class EndpointConfigRemediationAction extends UserAction {

    private static final LoggerMaker logger =
            new LoggerMaker(EndpointConfigRemediationAction.class, LogDb.DASHBOARD);

    private static final int MAX_LIMIT              = 200;
    private static final int DEFAULT_LIMIT          = 50;
    private static final int MAX_NEW_CONTENT_BYTES  = 10 * 1024 * 1024; // matches gateway's MaxConfigFileSize
    private static final int MAX_EXPIRY_SEC         = 86400;
    private static final int MIN_EXPIRY_SEC         = 60;
    private static final int DEFAULT_PREVIEW_EXPIRY_SEC = 300;   // user is watching synchronously
    private static final int DEFAULT_APPLY_EXPIRY_SEC   = 86400; // device may be offline right now

    // ── request fields ──────────────────────────────────────────────────────
    @Setter private String mode;
    @Setter private String toolName;
    @Setter private String configPath;
    @Setter private String format;
    @Setter private String newContent;
    @Setter private String expectedChecksumBeforeApply;
    @Setter private String findingRefId;
    @Setter private int expirySeconds;
    @Setter private String targetType;
    @Setter private List<String> targetDeviceIds;
    @Getter @Setter private String remediationId; // input for cancel/fetch; output for queue
    @Setter private String deviceId;
    @Setter private String host; // deviceLabel.ai-agent.<toolName> — resolved to deviceId if targetDeviceIds is empty
    @Setter private int limit = DEFAULT_LIMIT;
    @Setter private long createdAfter = 0;

    // ── response fields ──────────────────────────────────────────────────────
    @Getter private List<EndpointConfigRemediation>          remediations;
    @Getter private List<EndpointConfigRemediationExecution> executions;

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Queue a new remediation job (PREVIEW or APPLY)
    // ─────────────────────────────────────────────────────────────────────────
    public String queueEndpointConfigRemediation() {
        try {
            EndpointConfigRemediation.Mode jobMode;
            try {
                jobMode = EndpointConfigRemediation.Mode.valueOf(
                        StringUtils.isBlank(mode) ? "" : mode.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                addActionError("mode must be PREVIEW or APPLY");
                return Action.ERROR.toUpperCase();
            }

            if (StringUtils.isBlank(configPath)) {
                addActionError("configPath is required");
                return Action.ERROR.toUpperCase();
            }
            if (StringUtils.isBlank(toolName)) {
                addActionError("toolName is required");
                return Action.ERROR.toUpperCase();
            }

            EndpointConfigRemediation.Format jobFormat;
            try {
                jobFormat = EndpointConfigRemediation.Format.valueOf(
                        StringUtils.isBlank(format) ? "" : format.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                addActionError("format must be JSON or TOML");
                return Action.ERROR.toUpperCase();
            }

            if (jobMode == EndpointConfigRemediation.Mode.APPLY) {
                if (StringUtils.isBlank(newContent)) {
                    addActionError("newContent is required for APPLY jobs");
                    return Action.ERROR.toUpperCase();
                }
                if (newContent.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_NEW_CONTENT_BYTES) {
                    addActionError("newContent must be at most " + MAX_NEW_CONTENT_BYTES + " bytes");
                    return Action.ERROR.toUpperCase();
                }
                if (StringUtils.isBlank(expectedChecksumBeforeApply)) {
                    addActionError("expectedChecksumBeforeApply is required for APPLY jobs");
                    return Action.ERROR.toUpperCase();
                }
            }

            // Resolve the target device: prefer explicit targetDeviceIds, otherwise
            // derive it from the threat event's host string (deviceLabel.ai-agent.<toolName>).
            if (targetDeviceIds == null || targetDeviceIds.isEmpty()) {
                String resolvedDeviceId = StringUtils.isNotBlank(deviceId) ? deviceId : resolveDeviceIdFromHost(host);
                if (StringUtils.isBlank(resolvedDeviceId)) {
                    addActionError("targetDeviceIds, deviceId, or a resolvable host is required");
                    return Action.ERROR.toUpperCase();
                }
                targetDeviceIds = new ArrayList<>();
                targetDeviceIds.add(resolvedDeviceId);
            }

            int defaultExpiry = jobMode == EndpointConfigRemediation.Mode.PREVIEW
                    ? DEFAULT_PREVIEW_EXPIRY_SEC : DEFAULT_APPLY_EXPIRY_SEC;
            if (expirySeconds <= 0) expirySeconds = defaultExpiry;
            if (expirySeconds < MIN_EXPIRY_SEC) expirySeconds = MIN_EXPIRY_SEC;
            if (expirySeconds > MAX_EXPIRY_SEC) expirySeconds = MAX_EXPIRY_SEC;

            long nowMs = System.currentTimeMillis();
            String createdByUser = getSUser() != null ? getSUser().getLogin() : null;

            EndpointConfigRemediation job = new EndpointConfigRemediation();
            job.setId(new ObjectId());
            job.setAccountId(Context.accountId.get());
            job.setMode(jobMode);
            job.setToolName(toolName.trim());
            job.setConfigPath(configPath.trim());
            job.setFormat(jobFormat);
            job.setNewContent(newContent);
            job.setExpectedChecksumBeforeApply(expectedChecksumBeforeApply);
            job.setFindingRefId(findingRefId);
            job.setExpirySeconds(expirySeconds);
            job.setTargetType(EndpointConfigRemediation.TargetType.SELECTED);
            job.setTargetDeviceIds(targetDeviceIds);
            job.setStatus(EndpointConfigRemediation.Status.ACTIVE);
            job.setCreatedBy(createdByUser);
            job.setCreatedAt(nowMs);
            job.setExpiresAt(nowMs + (long) expirySeconds * 1000L);

            EndpointConfigRemediationDao.instance.createIndicesIfAbsent();
            EndpointConfigRemediationDao.instance.insertOne(job);

            this.remediationId = job.getRemediationId();
            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            logger.errorAndAddToDb(e, "queueEndpointConfigRemediation failed: " + e.getMessage());
            addActionError("Failed to queue config remediation");
            return Action.ERROR.toUpperCase();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. List remediation jobs with execution summary
    // ─────────────────────────────────────────────────────────────────────────
    public String fetchEndpointConfigRemediationList() {
        try {
            int effectiveLimit = (limit <= 0 || limit > MAX_LIMIT) ? DEFAULT_LIMIT : limit;

            EndpointConfigRemediationDao.instance.createIndicesIfAbsent();

            org.bson.conversions.Bson filter = createdAfter > 0
                    ? Filters.gt(EndpointConfigRemediation.CREATED_AT, createdAfter)
                    : Filters.empty();

            this.remediations = EndpointConfigRemediationDao.instance.findAll(
                    filter, 0, effectiveLimit,
                    Sorts.descending(EndpointConfigRemediation.CREATED_AT));

            if (this.remediations == null) this.remediations = new ArrayList<>();

            EndpointConfigRemediationExecutionDao.instance.createIndicesIfAbsent();
            for (EndpointConfigRemediation job : this.remediations) {
                String hexId = job.getRemediationId();
                if (hexId == null) continue;

                List<EndpointConfigRemediationExecution> execs =
                        EndpointConfigRemediationExecutionDao.instance.findAll(
                                Filters.eq(EndpointConfigRemediationExecution.REMEDIATION_ID, hexId),
                                0, 10000, null);

                job.setExecutionSummary(buildSummary(execs));
            }

            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            logger.errorAndAddToDb(e, "fetchEndpointConfigRemediationList failed: " + e.getMessage());
            addActionError("Failed to fetch config remediations");
            return Action.ERROR.toUpperCase();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. List executions for a remediation job or device
    // ─────────────────────────────────────────────────────────────────────────
    public String fetchEndpointConfigRemediationExecutions() {
        try {
            int effectiveLimit = (limit <= 0 || limit > MAX_LIMIT) ? DEFAULT_LIMIT : limit;

            EndpointConfigRemediationExecutionDao.instance.createIndicesIfAbsent();

            org.bson.conversions.Bson filter;
            if (!StringUtils.isBlank(remediationId)) {
                filter = Filters.eq(EndpointConfigRemediationExecution.REMEDIATION_ID, remediationId.trim());
            } else if (!StringUtils.isBlank(deviceId)) {
                filter = Filters.eq(EndpointConfigRemediationExecution.DEVICE_ID, deviceId.trim());
            } else {
                addActionError("remediationId or deviceId is required");
                return Action.ERROR.toUpperCase();
            }

            this.executions = EndpointConfigRemediationExecutionDao.instance.findAll(
                    filter, 0, effectiveLimit,
                    Sorts.descending(EndpointConfigRemediationExecution.EXECUTED_AT));

            if (this.executions == null) this.executions = new ArrayList<>();
            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            logger.errorAndAddToDb(e, "fetchEndpointConfigRemediationExecutions failed: " + e.getMessage());
            addActionError("Failed to fetch config remediation executions");
            return Action.ERROR.toUpperCase();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Cancel a remediation job
    // ─────────────────────────────────────────────────────────────────────────
    public String cancelEndpointConfigRemediation() {
        try {
            if (StringUtils.isBlank(remediationId)) {
                addActionError("remediationId is required");
                return Action.ERROR.toUpperCase();
            }

            ObjectId oid;
            try {
                oid = new ObjectId(remediationId.trim());
            } catch (IllegalArgumentException e) {
                addActionError("invalid remediationId");
                return Action.ERROR.toUpperCase();
            }

            EndpointConfigRemediationDao.instance.updateOneNoUpsert(
                    Filters.and(
                            Filters.eq("_id", oid),
                            Filters.eq(EndpointConfigRemediation.ACCOUNT_ID, Context.accountId.get())
                    ),
                    Updates.set(EndpointConfigRemediation.STATUS,
                            EndpointConfigRemediation.Status.CANCELLED.name())
            );

            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            logger.errorAndAddToDb(e, "cancelEndpointConfigRemediation failed: " + e.getMessage());
            addActionError("Failed to cancel config remediation");
            return Action.ERROR.toUpperCase();
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * A threat event's "host" field is shaped like deviceLabel.ai-agent.<toolName>
     * (see akto-gateway utils.GetAgentCollectionName). deviceLabel is the same
     * value stored in module_info's "name" field for that device's agent
     * (see EndpointShieldAgentAction.getMcpServersByAgent), so splitting on the
     * first "." and looking that up avoids needing a dedicated deviceId field
     * on the malicious event schema.
     */
    private static String resolveDeviceIdFromHost(String host) {
        if (StringUtils.isBlank(host)) return null;
        String deviceLabel = host.contains(".") ? host.substring(0, host.indexOf('.')) : host;
        if (StringUtils.isBlank(deviceLabel)) return null;

        List<ModuleInfo> matches = ModuleInfoDao.instance.findAll(Filters.and(
                Filters.eq(ModuleInfo.NAME, deviceLabel),
                Filters.eq(ModuleInfo.MODULE_TYPE, ModuleInfo.ModuleType.MCP_ENDPOINT_SHIELD)
        ));
        if (matches.isEmpty()) return null;
        return matches.get(0).getName();
    }

    private static ExecutionSummary buildSummary(List<EndpointConfigRemediationExecution> execs) {
        ExecutionSummary s = new ExecutionSummary();
        if (execs == null) return s;
        s.setTotal(execs.size());
        for (EndpointConfigRemediationExecution e : execs) {
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
