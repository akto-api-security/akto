package com.akto.action;

import com.akto.dao.context.Context;
import com.akto.dao.endpoint_shield.FileInspectionResultDao;
import com.akto.dto.endpoint_shield.FileInspectionResult;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

public class FileInspectionResultAction extends ActionSupport {

    private static final LoggerMaker logger =
            new LoggerMaker(FileInspectionResultAction.class, LogDb.DASHBOARD);

    @Setter
    private FileInspectionResult fileInspectionResult;

    @Getter
    private boolean success = false;

    public String saveFileInspectionResult() {
        if (fileInspectionResult == null) {
            addActionError("fileInspectionResult is required");
            return Action.ERROR.toUpperCase();
        }
        if (StringUtils.isBlank(fileInspectionResult.getRuleId())) {
            addActionError("ruleId is required");
            return Action.ERROR.toUpperCase();
        }
        if (StringUtils.isBlank(fileInspectionResult.getDeviceId())) {
            addActionError("deviceId is required");
            return Action.ERROR.toUpperCase();
        }
        try {
            FileInspectionResultDao.instance.createIndicesIfAbsent();

            if (fileInspectionResult.getExecutedAt() <= 0) {
                fileInspectionResult.setExecutedAt(Context.now());
            }
            fileInspectionResult.setAccountId(Context.accountId.get());

            Bson filter = Filters.and(
                    Filters.eq(FileInspectionResult.RULE_ID, fileInspectionResult.getRuleId()),
                    Filters.eq(FileInspectionResult.DEVICE_ID, fileInspectionResult.getDeviceId())
            );

            // Build a $set update from all incoming fields so we never change _id.
            // Works on both capped and regular collections.
            Bson update = Updates.combine(
                    Updates.set(FileInspectionResult.EXECUTED_AT,   fileInspectionResult.getExecutedAt()),
                    Updates.set(FileInspectionResult.ACCOUNT_ID,    fileInspectionResult.getAccountId()),
                    Updates.set(FileInspectionResult.AGENT_ID,      fileInspectionResult.getAgentId()),
                    Updates.set(FileInspectionResult.DEVICE_LABEL,  fileInspectionResult.getDeviceLabel()),
                    Updates.set(FileInspectionResult.STATUS,        fileInspectionResult.getStatus()),
                    Updates.set(FileInspectionResult.MATCHES,       fileInspectionResult.getMatches()),
                    Updates.set(FileInspectionResult.ERROR_MESSAGE, fileInspectionResult.getErrorMessage())
            );

            long matched = FileInspectionResultDao.instance.getMCollection()
                    .updateOne(filter, update)
                    .getMatchedCount();

            if (matched == 0) {
                if (fileInspectionResult.getId() == null) {
                    fileInspectionResult.setId(new ObjectId());
                }
                FileInspectionResultDao.instance.insertOne(fileInspectionResult);
            }

            success = true;
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "saveFileInspectionResult failed: " + e.getMessage());
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }
}
