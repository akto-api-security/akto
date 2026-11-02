package com.akto.action.endpoint_shield;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dao.endpoint_shield.FileInspectionResultDao;
import com.akto.dao.endpoint_shield.FileInspectionRuleDao;
import com.akto.dto.endpoint_shield.FileInspectionResult;
import com.akto.dto.endpoint_shield.FileInspectionRule;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.utils.blob.AzureBlobClient;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.opensymphony.xwork2.Action;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.util.List;

public class FileInspectionRuleAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(FileInspectionRuleAction.class, LogDb.DASHBOARD);
    private static final int MAX_PATH_LENGTH = 1024;
    private static final int MAX_AGENT_LENGTH = 100;
    private static final Set<String> VALID_OS = new HashSet<>(Arrays.asList("darwin", "linux", "windows"));
    private static final Set<String> VALID_ROLES = new HashSet<>(Arrays.asList("login", "credentials", "mcp_config"));

    @Setter private String path;
    @Setter private boolean existenceOnly;
    @Setter private int maxDepth;        // 0 = immediate children, -1 = unlimited
    @Setter private String agent;
    @Setter private String os;
    @Setter private String role;
    @Setter private String ruleId;       // used by update + result-fetch
    @Setter private String sha256;       // used by getFileContent
    @Setter private int sinceExecutedAt; // cursor for result-fetch pagination
    @Setter private int limit;           // page size for result-fetch

    @Getter private FileInspectionRule rule;
    @Getter private List<FileInspectionRule> rules;
    @Getter private List<FileInspectionResult> recentResults;
    @Getter private String blobContent;

    /**
     * Adds a new path-rule, or updates the existenceOnly flag if the same path
     * already exists for this account. Returns the rule that's now in the DB.
     */
    public String addFileInspectionRule() {
        try {
            FileInspectionRuleDao.instance.createIndicesIfAbsent();

            String validation = validatePath(path);
            if (validation == null) validation = validateMetadata();
            if (validation != null) {
                addActionError(validation);
                return Action.ERROR.toUpperCase();
            }
            String trimmed = path.trim();
            int now = Context.now();
            String addedBy = getSUser() == null ? null : getSUser().getLogin();

            Bson filter = Filters.eq(FileInspectionRule.PATH, trimmed);
            FileInspectionRule existing = FileInspectionRuleDao.instance.findOne(filter);
            if (existing != null) {
                existing.setExistenceOnly(existenceOnly);
                existing.setMaxDepth(maxDepth);
                if (StringUtils.isNotBlank(agent)) existing.setAgent(agent.trim());
                if (StringUtils.isNotBlank(os)) existing.setOs(normalize(os));
                if (StringUtils.isNotBlank(role)) existing.setRole(normalize(role));
                existing.setUpdatedTs(now);
                FileInspectionRuleDao.instance.replaceOne(filter, existing);
                this.rule = existing;
            } else {
                FileInspectionRule r = new FileInspectionRule();
                r.setId(new ObjectId());
                r.setAccountId(Context.accountId.get());
                r.setPath(trimmed);
                r.setExistenceOnly(existenceOnly);
                r.setMaxDepth(maxDepth);
                r.setAddedBy(addedBy);
                r.setAgent(StringUtils.trimToNull(agent));
                r.setOs(normalize(os));
                r.setRole(normalize(role));
                r.setCreatedTs(now);
                r.setUpdatedTs(now);
                FileInspectionRuleDao.instance.insertOne(r);
                this.rule = r;
            }
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "addFileInspectionRule failed: " + e.getMessage());
            addActionError("Failed to save rule");
            return Action.ERROR.toUpperCase();
        }
    }

    public String updateFileInspectionRule() {
        try {
            if (StringUtils.isBlank(ruleId) || !ObjectId.isValid(ruleId.trim())) {
                addActionError("Valid ruleId is required");
                return Action.ERROR.toUpperCase();
            }
            String validation = validatePath(path);
            if (validation == null) validation = validateMetadata();
            if (validation != null) {
                addActionError(validation);
                return Action.ERROR.toUpperCase();
            }
            ObjectId id = new ObjectId(ruleId.trim());
            Bson filter = Filters.eq(FileInspectionRule.ID, id);
            FileInspectionRule existing = FileInspectionRuleDao.instance.findOne(filter);
            if (existing == null) {
                addActionError("Rule not found");
                return Action.ERROR.toUpperCase();
            }
            String trimmed = path.trim();
            FileInspectionRule samePath = FileInspectionRuleDao.instance.findOne(Filters.eq(FileInspectionRule.PATH, trimmed));
            if (samePath != null && !id.equals(samePath.getId())) {
                addActionError("A rule for this path already exists");
                return Action.ERROR.toUpperCase();
            }
            existing.setPath(trimmed);
            existing.setExistenceOnly(existenceOnly);
            existing.setMaxDepth(maxDepth);
            existing.setAgent(StringUtils.trimToNull(agent));
            existing.setOs(normalize(os));
            existing.setRole(normalize(role));
            existing.setUpdatedTs(Context.now());
            FileInspectionRuleDao.instance.replaceOne(filter, existing);
            this.rule = existing;
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "updateFileInspectionRule failed: " + e.getMessage());
            addActionError("Failed to update rule");
            return Action.ERROR.toUpperCase();
        }
    }

    public String fetchFileInspectionRules() {
        try {
            FileInspectionRuleDao.instance.createIndicesIfAbsent();
            this.rules = FileInspectionRuleDao.instance.findAll(
                Filters.empty(),
                0, 500,
                Sorts.descending(FileInspectionRule.UPDATED_TS)
            );
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "fetchFileInspectionRules failed: " + e.getMessage());
            addActionError("Failed to fetch rules");
            return Action.ERROR.toUpperCase();
        }
    }

    /**
     * Delete by path (matches PatternSettingsPage's onDelete contract which
     * passes the row's display value).
     */
    public String deleteFileInspectionRule() {
        try {
            if (StringUtils.isBlank(path)) {
                addActionError("path is required");
                return Action.ERROR.toUpperCase();
            }
            FileInspectionRuleDao.instance.deleteAll(Filters.eq(FileInspectionRule.PATH, path.trim()));
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "deleteFileInspectionRule failed: " + e.getMessage());
            addActionError("Failed to delete rule");
            return Action.ERROR.toUpperCase();
        }
    }

    public String fetchFileInspectionResults() {
        try {
            FileInspectionResultDao.instance.createIndicesIfAbsent();

            List<Bson> clauses = new ArrayList<>();
            if (StringUtils.isNotBlank(ruleId)) {
                clauses.add(Filters.eq(FileInspectionResult.RULE_ID, ruleId));
            }
            if (sinceExecutedAt > 0) {
                clauses.add(Filters.gt(FileInspectionResult.EXECUTED_AT, sinceExecutedAt));
            }
            Bson filter = clauses.isEmpty() ? Filters.empty() : Filters.and(clauses);

            int effLimit = (limit > 0 && limit <= 1000) ? limit : 100;
            // Cursor-mode (sinceExecutedAt > 0) needs ascending order so the
            // caller can advance its cursor monotonically. UI mode (no cursor)
            // keeps the existing descending "most recent first" behaviour.
            Bson sort = sinceExecutedAt > 0
                ? Sorts.ascending(FileInspectionResult.EXECUTED_AT)
                : Sorts.descending(FileInspectionResult.EXECUTED_AT);

            this.recentResults = FileInspectionResultDao.instance.findAll(
                filter, 0, effLimit, sort
            );
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "fetchFileInspectionResults failed: " + e.getMessage());
            addActionError("Failed to fetch results");
            return Action.ERROR.toUpperCase();
        }
    }

    public String getFileContent() {
        try {
            if (StringUtils.isBlank(sha256)) {
                addActionError("sha256 is required");
                return Action.ERROR.toUpperCase();
            }
            String blobName = AzureBlobClient.buildBlobName(Context.accountId.get(), sha256.trim().toLowerCase());
            byte[] data = AzureBlobClient.getInstance().download(blobName);
            if (data == null) {
                addActionError("Content not found");
                return Action.ERROR.toUpperCase();
            }
            this.blobContent = new String(data, java.nio.charset.StandardCharsets.UTF_8);
            return Action.SUCCESS.toUpperCase();
        } catch (IllegalStateException e) {
            logger.errorAndAddToDb(e, "getFileContent: blob storage not configured");
            addActionError("Blob storage not configured: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "getFileContent failed: " + e.getMessage());
            addActionError("Failed to fetch content");
            return Action.ERROR.toUpperCase();
        }
    }

    private String validateMetadata() {
        if (agent != null && agent.trim().length() > MAX_AGENT_LENGTH) return "Agent name too long (max " + MAX_AGENT_LENGTH + ")";
        String o = normalize(os);
        if (o != null && !VALID_OS.contains(o)) return "Invalid os: " + os;
        String r = normalize(role);
        if (r != null && !VALID_ROLES.contains(r)) return "Invalid role: " + role;
        return null;
    }

    private static String normalize(String v) {
        String t = StringUtils.trimToNull(v);
        return t == null ? null : t.toLowerCase();
    }

    private static String validatePath(String p) {
        if (StringUtils.isBlank(p)) return "Path is required";
        String t = p.trim();
        if (t.length() > MAX_PATH_LENGTH) return "Path too long (max " + MAX_PATH_LENGTH + ")";
        if ("/".equals(t)) return "Path cannot be the root '/'";
        return null;
    }
}
