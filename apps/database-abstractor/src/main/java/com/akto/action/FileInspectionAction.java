package com.akto.action;

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
import com.opensymphony.xwork2.ActionSupport;
import lombok.Getter;
import lombok.Setter;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Endpoints called by the akto-endpoint-shield agent. Account context is set
 * by AuthFilter from the JWT, so each agent only sees its own account's data.
 */
public class FileInspectionAction extends ActionSupport {

    private static final LoggerMaker loggerMaker = new LoggerMaker(FileInspectionAction.class, LogDb.DB_ABS);
    private static final Pattern SHA256_RE = Pattern.compile("^[a-f0-9]{64}$");
    private static final long MAX_BLOB_BYTES = 50L * 1024 * 1024;

    @Setter private long updatedAfter;
    @Setter private FileInspectionResult result;

    @Getter private List<FileInspectionRule> rules;

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
            FileInspectionResultDao.instance.createIndicesIfAbsent();
            result.setId(new ObjectId());
            result.setAccountId(Context.accountId.get());
            if (result.getExecutedAt() == 0) {
                result.setExecutedAt(Context.now());
            }
            uploadMatchBlobs(result);
            FileInspectionResultDao.instance.insertOne(result);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("uploadFileInspectionResult failed: " + e.getMessage(), LogDb.DB_ABS);
            addActionError("Failed to upload result");
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
}
