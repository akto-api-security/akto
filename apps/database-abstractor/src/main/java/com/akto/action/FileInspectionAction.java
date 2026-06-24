package com.akto.action;

import com.akto.dao.context.Context;
import com.akto.dao.endpoint_shield.FileInspectionResultDao;
import com.akto.dao.endpoint_shield.FileInspectionRuleDao;
import com.akto.dto.endpoint_shield.FileInspectionResult;
import com.akto.dto.endpoint_shield.FileInspectionRule;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.utils.blob.AzureBlobClient;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.opensymphony.xwork2.ActionSupport;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Endpoints called by the akto-endpoint-shield agent. Account context is set
 * by AuthFilter from the JWT, so each agent only sees its own account's data.
 *
 * Also hosts the small control-plane endpoints used by sibling services
 * (e.g. guardrails-service) to:
 *   - seed file-inspection rules     ({@link #addFileInspectionRule()})
 *   - pull recent results            ({@link #fetchFileInspectionResults()})
 *   - re-read a previously uploaded  ({@link #getFileContent()})
 *     file's content from blob storage
 */
public class FileInspectionAction extends ActionSupport {

    private static final LoggerMaker loggerMaker = new LoggerMaker(FileInspectionAction.class, LogDb.DB_ABS);
    private static final Pattern SHA256_RE = Pattern.compile("^[a-f0-9]{64}$");
    private static final int MAX_PATH_LENGTH = 1024;
    private static final int RESULTS_PAGE_MAX = 500;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Setter private long updatedAfter;
    @Setter private BasicDBObject result;

    @Setter private String path;
    @Setter private boolean existenceOnly;
    @Setter private int maxDepth;
    @Setter private String addedBy;

    @Setter private long sinceExecutedAt;
    @Setter private int limit;

    @Setter private String sha256;

    @Getter private List<FileInspectionRule> rules;
    @Getter private FileInspectionRule rule;
    @Getter private List<FileInspectionResult> results;
    @Getter private String blobContent;

    public String fetchFileInspectionRules() {
        try {
            FileInspectionRuleDao.getInstance().createIndicesIfAbsent();
            Bson filter = Filters.gt(FileInspectionRule.UPDATED_TS, (int) updatedAfter);
            this.rules = FileInspectionRuleDao.getInstance().findAll(
                filter, 0, 500,
                Sorts.descending(FileInspectionRule.UPDATED_TS)
            );
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("fetchFileInspectionRules failed: " + e.getMessage(), LogDb.DB_ABS);
            addActionError("Failed to fetch rules");
            return ERROR.toUpperCase();
        }
    }

    public String uploadFileInspectionResult() {
        try {
            if (result == null) {
                addActionError("result is required");
                return ERROR.toUpperCase();
            }
            FileInspectionResult parsed = MAPPER.readValue(result.toJson(), FileInspectionResult.class);
            if (StringUtils.isBlank(parsed.getRuleId())) {
                addActionError("ruleId is required");
                return ERROR.toUpperCase();
            }
            if (StringUtils.isBlank(parsed.getDeviceId())) {
                addActionError("deviceId is required");
                return ERROR.toUpperCase();
            }
            FileInspectionResultDao.instance.createIndicesIfAbsent();
            parsed.setAccountId(Context.accountId.get());
            if (parsed.getExecutedAt() == 0) {
                parsed.setExecutedAt(Context.now());
            }
            uploadMatchBlobs(parsed);

            Bson filter = Filters.and(
                    Filters.eq(FileInspectionResult.RULE_ID, parsed.getRuleId()),
                    Filters.eq(FileInspectionResult.DEVICE_ID, parsed.getDeviceId())
            );
            FileInspectionResult existing = FileInspectionResultDao.instance.findOne(filter);
            if (existing != null) {
                parsed.setId(existing.getId());
            } else {
                parsed.setId(new ObjectId());
            }
            FileInspectionResultDao.instance.replaceOne(filter, parsed);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("uploadFileInspectionResult failed: " + e.getMessage(), LogDb.DB_ABS);
            addActionError("Failed to upload result");
            return ERROR.toUpperCase();
        }
    }

    /**
     * Idempotent rule create/update keyed by path. Mirrors
     * dashboard's FileInspectionRuleAction#addFileInspectionRule so other
     * akto services (guardrails-service) can seed rules without going
     * through the dashboard.
     */
    public String addFileInspectionRule() {
        try {
            FileInspectionRuleDao.getInstance().createIndicesIfAbsent();

            String validation = validatePath(path);
            if (validation != null) {
                addActionError(validation);
                return ERROR.toUpperCase();
            }
            String trimmed = path.trim();
            int now = Context.now();

            Bson filter = Filters.eq(FileInspectionRule.PATH, trimmed);
            FileInspectionRule existing = FileInspectionRuleDao.getInstance().findOne(filter);
            if (existing != null) {
                existing.setExistenceOnly(existenceOnly);
                existing.setMaxDepth(maxDepth);
                existing.setUpdatedTs(now);
                FileInspectionRuleDao.getInstance().replaceOne(filter, existing);
                this.rule = existing;
            } else {
                FileInspectionRule r = new FileInspectionRule();
                r.setId(new ObjectId());
                r.setAccountId(Context.accountId.get());
                r.setPath(trimmed);
                r.setExistenceOnly(existenceOnly);
                r.setMaxDepth(maxDepth);
                r.setAddedBy(StringUtils.isBlank(addedBy) ? "system" : addedBy);
                r.setCreatedTs(now);
                r.setUpdatedTs(now);
                FileInspectionRuleDao.getInstance().insertOne(r);
                this.rule = r;
            }
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("addFileInspectionRule failed: " + e.getMessage(), LogDb.DB_ABS);
            addActionError("Failed to save rule");
            return ERROR.toUpperCase();
        }
    }

    /**
     * Return recent inspection results with executedAt > sinceExecutedAt.
     * Sorted ascending by executedAt so the caller can advance a cursor.
     */
    public String fetchFileInspectionResults() {
        try {
            FileInspectionResultDao.instance.createIndicesIfAbsent();
            int pageSize = limit > 0 && limit <= RESULTS_PAGE_MAX ? limit : RESULTS_PAGE_MAX;
            Bson filter = sinceExecutedAt > 0
                ? Filters.gt(FileInspectionResult.EXECUTED_AT, (int) sinceExecutedAt)
                : Filters.empty();
            this.results = FileInspectionResultDao.instance.findAll(
                filter, 0, pageSize,
                Sorts.ascending(FileInspectionResult.EXECUTED_AT)
            );
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("fetchFileInspectionResults failed: " + e.getMessage(), LogDb.DB_ABS);
            addActionError("Failed to fetch results");
            return ERROR.toUpperCase();
        }
    }

    /**
     * Download a previously-uploaded file's content from Azure blob storage
     * and return it as a UTF-8 string. Callers identify the blob by the
     * sha256 the agent reported; blob name is rebuilt from (accountId, sha256).
     */
    public String getFileContent() {
        try {
            if (StringUtils.isBlank(sha256) || !SHA256_RE.matcher(sha256.trim().toLowerCase()).matches()) {
                addActionError("valid sha256 is required");
                return ERROR.toUpperCase();
            }
            String blobName = AzureBlobClient.buildBlobName(Context.accountId.get(), sha256.trim().toLowerCase());
            byte[] data = AzureBlobClient.getInstance().download(blobName);
            if (data == null) {
                addActionError("Content not found");
                return ERROR.toUpperCase();
            }
            this.blobContent = new String(data, StandardCharsets.UTF_8);
            return SUCCESS.toUpperCase();
        } catch (IllegalStateException e) {
            loggerMaker.errorAndAddToDb("getFileContent: blob storage not configured: " + e.getMessage(), LogDb.DB_ABS);
            addActionError("Blob storage not configured");
            return ERROR.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("getFileContent failed: " + e.getMessage(), LogDb.DB_ABS);
            addActionError("Failed to fetch content");
            return ERROR.toUpperCase();
        }
    }

    private void uploadMatchBlobs(FileInspectionResult r) {
        if (r.getMatches() == null) return;
        AzureBlobClient client;
        try {
            client = AzureBlobClient.getInstance();
        } catch (Exception e) {
            loggerMaker.infoAndAddToDb("Azure unavailable, skipping blob uploads: " + e.getMessage(), LogDb.DB_ABS);
            return;
        }
        for (FileInspectionResult.Match m : r.getMatches()) {
            String raw = m.getContentRaw();
            if (raw == null || raw.isEmpty()) {
                loggerMaker.infoAndAddToDb("Discarding match " + m.getPath() + ": no content", LogDb.DB_ABS);
                continue;
            }
            try {
                byte[] data = java.util.Base64.getDecoder().decode(raw);
                String sha = m.getSha256();
                if (sha == null || !SHA256_RE.matcher(sha).matches()) {
                    loggerMaker.infoAndAddToDb("Discarding match " + m.getPath() + ": invalid sha256", LogDb.DB_ABS);
                    continue;
                }
                String blobName = AzureBlobClient.buildBlobName(r.getAccountId(), sha);
                if (!client.exists(blobName)) {
                    client.upload(blobName, data);
                    loggerMaker.infoAndAddToDb("Uploaded blob " + blobName + " for " + m.getPath(), LogDb.DB_ABS);
                }
                m.setContentBlobName(blobName);
            } catch (Exception e) {
                loggerMaker.infoAndAddToDb("blob upload skipped for " + m.getPath() + ": " + e.getMessage(), LogDb.DB_ABS);
                m.setReadError("blob upload failed: " + e.getMessage());
            } finally {
                m.setContentRaw(null);
                loggerMaker.infoAndAddToDb("Cleared raw content for " + m.getPath(), LogDb.DB_ABS);
            }
        }
    }

    private static String validatePath(String p) {
        if (StringUtils.isBlank(p)) return "Path is required";
        String t = p.trim();
        if (t.length() > MAX_PATH_LENGTH) return "Path too long (max " + MAX_PATH_LENGTH + ")";
        if ("/".equals(t)) return "Path cannot be the root '/'";
        return null;
    }
}
