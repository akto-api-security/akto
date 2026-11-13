package com.akto.action;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.akto.dao.S3ExportIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dao.jobs.JobsDao;
import com.akto.dto.jobs.Job;
import com.akto.dto.jobs.JobExecutorType;
import com.akto.dto.jobs.S3ExportJobParams;
import com.akto.dto.s3_export.S3ExportIntegration;
import com.akto.jobs.JobScheduler;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.s3_export.S3ExportDataset;
import com.akto.s3_export.S3ExportUtils;
import com.akto.s3_export.S3ExportValidator;
import com.akto.s3_export.S3ExportWriter;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;

import lombok.Getter;
import lombok.Setter;

public class S3ExportIntegrationAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(S3ExportIntegrationAction.class, LogDb.DASHBOARD);

    // role of the dashboard pods, the principal the customer's trust policy must allow
    private static final String AKTO_ROLE_ARN_ENV = "AKTO_S3_EXPORT_ROLE_ARN";
    private static final int SECONDS_PER_HOUR = 3600;

    // request params
    @Getter @Setter private String bucket;
    @Getter @Setter private String region;
    @Getter @Setter private String prefix;
    @Getter @Setter private String roleArn;
    @Getter @Setter private String kmsKeyArn;
    @Getter @Setter private List<String> datasets;
    @Getter @Setter private Boolean includePayloads;
    @Getter @Setter private Integer intervalHours;
    @Getter @Setter private Integer initialBackfillHours;

    // response properties (no setters, so a client cannot set them)
    @Getter private S3ExportIntegration s3ExportIntegration;
    @Getter private String aktoRoleArn;
    @Getter private List<String> availableDatasets;
    @Getter private BasicDBObject jobStatus;

    public String fetchS3ExportIntegration() {
        try {
            s3ExportIntegration = getOrCreateIntegration();
            aktoRoleArn = System.getenv().getOrDefault(AKTO_ROLE_ARN_ENV, "");
            availableDatasets = S3ExportDataset.names();
            jobStatus = buildJobStatus(findJobParams(s3ExportIntegration.getJobId()));
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error fetching S3 export integration for accountId: " + Context.accountId.get());
            addActionError("Failed to fetch S3 export integration. Please try again.");
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String testS3ExportIntegration() {
        try {
            String validBucket = S3ExportValidator.validateBucket(bucket);
            String validRegion = S3ExportValidator.validateRegion(region);
            String validPrefix = S3ExportValidator.validatePrefix(prefix);
            String validRoleArn = S3ExportValidator.validateRoleArn(roleArn);
            String validKmsKeyArn = S3ExportValidator.validateKmsKeyArn(kmsKeyArn);
            String externalId = getOrCreateIntegration().getExternalId();
            return runConnectionTest(externalId, validBucket, validRegion, validPrefix, validRoleArn, validKmsKeyArn);
        } catch (IllegalArgumentException e) {
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public String saveS3ExportIntegration() {
        String validBucket;
        String validRegion;
        String validPrefix;
        String validRoleArn;
        String validKmsKeyArn;
        List<String> validDatasets;
        int validInterval;
        int validBackfill;
        try {
            validBucket = S3ExportValidator.validateBucket(bucket);
            validRegion = S3ExportValidator.validateRegion(region);
            validPrefix = S3ExportValidator.validatePrefix(prefix);
            validRoleArn = S3ExportValidator.validateRoleArn(roleArn);
            validKmsKeyArn = S3ExportValidator.validateKmsKeyArn(kmsKeyArn);
            validDatasets = S3ExportValidator.validateDatasets(datasets);
            validInterval = S3ExportValidator.validateIntervalHours(
                intervalHours != null ? intervalHours : S3ExportIntegration.DEFAULT_INTERVAL_HOURS);
            validBackfill = S3ExportValidator.validateInitialBackfillHours(
                initialBackfillHours != null ? initialBackfillHours : S3ExportUtils.FIRST_RUN_BACKFILL_HOURS);
        } catch (IllegalArgumentException e) {
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }

        int accountId = Context.accountId.get();
        try {
            S3ExportIntegration existing = getOrCreateIntegration();
            String testResult = runConnectionTest(existing.getExternalId(), validBucket, validRegion, validPrefix, validRoleArn, validKmsKeyArn);
            if (!Action.SUCCESS.toUpperCase().equals(testResult)) return testResult;

            S3ExportJobParams oldParams = findJobParams(existing.getJobId());
            Job job = JobScheduler.scheduleRecurringJob(accountId, buildJobParams(oldParams, validDatasets, validBackfill),
                JobExecutorType.DASHBOARD, validInterval * SECONDS_PER_HOUR, Context.now());
            if (job == null) {
                addActionError("Failed to schedule the S3 export. Please try again.");
                return Action.ERROR.toUpperCase();
            }

            // externalId and jobId are only ever set by the server
            Bson updates = Updates.combine(
                Updates.set(S3ExportIntegration.BUCKET, validBucket),
                Updates.set(S3ExportIntegration.REGION, validRegion),
                Updates.set(S3ExportIntegration.PREFIX, validPrefix),
                Updates.set(S3ExportIntegration.ROLE_ARN, validRoleArn),
                Updates.set(S3ExportIntegration.KMS_KEY_ARN, validKmsKeyArn),
                Updates.set(S3ExportIntegration.DATASETS, validDatasets),
                Updates.set(S3ExportIntegration.INCLUDE_PAYLOADS, includePayloads == null || includePayloads),
                Updates.set(S3ExportIntegration.INTERVAL_HOURS, validInterval),
                Updates.set(S3ExportIntegration.ENABLED, true),
                Updates.set(S3ExportIntegration.JOB_ID, job.getId()),
                Updates.set(S3ExportIntegration.UPDATED_AT, Context.now()),
                Updates.set(S3ExportIntegration.UPDATED_BY, getSUser() != null ? getSUser().getLogin() : null)
            );
            S3ExportIntegrationDao.instance.getMCollection().updateOne(new BasicDBObject(), updates);

            if (existing.getJobId() != null) JobScheduler.deleteJob(existing.getJobId());
            logger.infoAndAddToDb("Saved S3 export integration for accountId: " + accountId);
        } catch (Exception e) {
            logger.errorAndAddToDb("Error saving S3 export integration for accountId: " + accountId + ", error: " + S3ExportUtils.describeError(e));
            addActionError("Failed to save S3 export integration. Please try again.");
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String removeS3ExportIntegration() {
        try {
            S3ExportIntegration existing = S3ExportIntegrationDao.instance.findOne(new BasicDBObject());
            if (existing != null && existing.getJobId() != null) {
                JobScheduler.deleteJob(existing.getJobId());
            }
            S3ExportIntegrationDao.instance.deleteAll(new BasicDBObject());
            logger.infoAndAddToDb("Removed S3 export integration for accountId: " + Context.accountId.get());
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error while removing S3 export integration for accountId: " + Context.accountId.get());
            addActionError("Failed to remove S3 export integration. Please try again.");
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    /** The account's integration; created with just a generated external id so the customer can set up IAM before saving. */
    private S3ExportIntegration getOrCreateIntegration() {
        return S3ExportIntegrationDao.instance.getMCollection().findOneAndUpdate(
            new BasicDBObject(),
            Updates.combine(
                Updates.setOnInsert(S3ExportIntegration.EXTERNAL_ID, UUID.randomUUID().toString()),
                Updates.setOnInsert(S3ExportIntegration.DATASETS, new ArrayList<String>()),
                Updates.setOnInsert(S3ExportIntegration.ENABLED, false),
                Updates.setOnInsert(S3ExportIntegration.INCLUDE_PAYLOADS, true),
                Updates.setOnInsert(S3ExportIntegration.INTERVAL_HOURS, S3ExportIntegration.DEFAULT_INTERVAL_HOURS),
                Updates.setOnInsert(S3ExportIntegration.CREATED_AT, Context.now())
            ),
            new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
    }

    private String runConnectionTest(String externalId, String validBucket, String validRegion, String validPrefix,
                                     String validRoleArn, String validKmsKeyArn) {
        int accountId = Context.accountId.get();
        try {
            S3ExportWriter.testConnection(accountId, validBucket, validRegion, validPrefix, validRoleArn,
                externalId, validKmsKeyArn);
        } catch (Exception e) {
            logger.errorAndAddToDb("S3 export connection test failed for accountId: " + accountId + ", error: " + S3ExportUtils.describeError(e));
            addActionError(S3ExportUtils.connectionErrorMessage(e, validRoleArn));
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    /** Keeps the checkpoints of datasets that stay enabled; newly enabled ones start initialBackfillHours back. */
    private static S3ExportJobParams buildJobParams(S3ExportJobParams oldParams, List<String> enabledDatasets, int backfillHours) {
        long newStartMs = S3ExportUtils.hourFloor(System.currentTimeMillis()) - backfillHours * S3ExportUtils.HOUR_MS;
        Map<String, Long> checkpoints = new HashMap<>();
        for (String dataset : enabledDatasets) {
            Long existing = oldParams != null && oldParams.getExportedUntilMs() != null
                ? oldParams.getExportedUntilMs().get(dataset) : null;
            checkpoints.put(dataset, existing != null ? existing : newStartMs);
        }
        S3ExportJobParams params = new S3ExportJobParams(checkpoints);
        if (oldParams != null) {
            params.setLastRunAt(oldParams.getLastRunAt());
            params.setLastSuccessAt(oldParams.getLastSuccessAt());
            params.setLastRecordCounts(oldParams.getLastRecordCounts());
        }
        return params;
    }

    private static S3ExportJobParams findJobParams(ObjectId jobId) {
        if (jobId == null) return null;
        Job job = JobsDao.instance.findOne(new BasicDBObject(Job.ID, jobId));
        return job != null && job.getJobParams() instanceof S3ExportJobParams ? (S3ExportJobParams) job.getJobParams() : null;
    }

    private static BasicDBObject buildJobStatus(S3ExportJobParams params) {
        BasicDBObject status = new BasicDBObject();
        if (params == null) return status;
        status.put("lastRunAt", params.getLastRunAt());
        status.put("lastSuccessAt", params.getLastSuccessAt());
        status.put("lastError", params.getLastError());
        status.put("lastRecordCounts", params.getLastRecordCounts());
        status.put("exportedUntilMs", params.getExportedUntilMs());
        return status;
    }
}
