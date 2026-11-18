package com.akto.action;

import com.akto.billing.UsageMetricUtils;
import com.akto.dao.AtlasS3ExportIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dao.jobs.JobsDao;
import com.akto.dto.AtlasS3ExportIntegration;
import com.akto.dto.jobs.AtlasS3ExportJobParams;
import com.akto.dto.jobs.Job;
import com.akto.dto.jobs.JobExecutorType;
import com.akto.dto.jobs.JobStatus;
import com.akto.jobs.JobScheduler;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.DashboardMode;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Getter
@Setter
public class AtlasS3ExportIntegrationAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(AtlasS3ExportIntegrationAction.class, LogDb.DASHBOARD);

    private static final Pattern BUCKET_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$");
    private static final Pattern REGION_PATTERN = Pattern.compile("^[a-z]{2}(-[a-z]+)+-\\d+$");
    private static final Pattern ROLE_ARN_PATTERN = Pattern.compile("^arn:aws[a-z-]*:iam::\\d{12}:role/\\S+$");
    private static final Pattern ACCESS_KEY_ID_PATTERN = Pattern.compile("^AKIA[A-Z0-9]{12,124}$");
    private static final int EXPORT_INTERVAL_SECONDS = 300;

    private String bucketName;
    private String region;
    private String prefix;
    private String roleArn;
    private String authType;
    private String accessKeyId;
    // Write-only: never serialized back in a response.
    @Getter(AccessLevel.NONE)
    private String secretAccessKey;

    private AtlasS3ExportIntegration atlasS3ExportIntegration;

    public String fetchAtlasS3ExportIntegration() {
        atlasS3ExportIntegration = AtlasS3ExportIntegrationDao.instance.findOne(new BasicDBObject(),
            Projections.exclude(AtlasS3ExportIntegration.SECRET_ACCESS_KEY));
        return Action.SUCCESS.toUpperCase();
    }

    public String addAtlasS3ExportIntegration() {
        if (DashboardMode.isOnPremDeployment()
            || !UsageMetricUtils.getFeatureAccessSaas(Context.accountId.get(), AtlasS3ExportIntegration.FEATURE_LABEL).getIsGranted()) {
            addActionError("Atlas log export to S3 is not enabled for this account.");
            return Action.ERROR.toUpperCase();
        }

        bucketName = StringUtils.trimToEmpty(bucketName);
        region = StringUtils.trimToEmpty(region);
        prefix = StringUtils.trimToEmpty(prefix).replaceAll("^/+|/+$", "");
        roleArn = StringUtils.trimToEmpty(roleArn);
        accessKeyId = StringUtils.trimToEmpty(accessKeyId);
        secretAccessKey = StringUtils.trimToEmpty(secretAccessKey);
        boolean useAccessKeys = AtlasS3ExportIntegration.AUTH_TYPE_ACCESS_KEYS.equals(authType);

        if (!BUCKET_PATTERN.matcher(bucketName).matches()) {
            addActionError("Please enter a valid S3 bucket name.");
            return Action.ERROR.toUpperCase();
        }
        if (!REGION_PATTERN.matcher(region).matches()) {
            addActionError("Please enter a valid AWS region, e.g. us-east-1.");
            return Action.ERROR.toUpperCase();
        }
        AtlasS3ExportIntegration existing = AtlasS3ExportIntegrationDao.instance.findOne(new BasicDBObject());
        if (useAccessKeys) {
            if (!ACCESS_KEY_ID_PATTERN.matcher(accessKeyId).matches()) {
                addActionError("Please enter a valid AWS access key ID (starts with AKIA).");
                return Action.ERROR.toUpperCase();
            }
            // On edit the secret may be left blank to keep the saved one, but only for the same key.
            if (secretAccessKey.isEmpty()
                    && (existing == null || !existing.usesAccessKeys() || !accessKeyId.equals(existing.getAccessKeyId()))) {
                addActionError("Please enter the AWS secret access key.");
                return Action.ERROR.toUpperCase();
            }
        } else if (!ROLE_ARN_PATTERN.matcher(roleArn).matches()) {
            addActionError("Please enter a valid IAM role ARN, e.g. arn:aws:iam::123456789012:role/akto-atlas-export.");
            return Action.ERROR.toUpperCase();
        }

        // The job reads the config on every run, so an edit keeps the running job (and its checkpoint).
        ObjectId jobId = existing != null ? existing.getExportJobId() : null;
        if (jobId == null || JobsDao.instance.findOne(Filters.and(Filters.eq(Job.ID, jobId),
                Filters.ne(Job.JOB_STATUS, JobStatus.STOPPED.name()))) == null) {
            // Checkpoint 0: the job starts from the oldest Atlas trace and sends all history, a batch per run.
            Job job = JobScheduler.scheduleRecurringJob(Context.accountId.get(),
                new AtlasS3ExportJobParams(0), JobExecutorType.DASHBOARD, EXPORT_INTERVAL_SECONDS);
            if (job == null) {
                addActionError("Failed to schedule Atlas S3 export. Please try again.");
                return Action.ERROR.toUpperCase();
            }
            jobId = job.getId();
        }

        int now = Context.now();
        List<Bson> updates = new ArrayList<>(Arrays.asList(
            Updates.set(AtlasS3ExportIntegration.EXPORT_JOB_ID, jobId),
            Updates.set(AtlasS3ExportIntegration.BUCKET_NAME, bucketName),
            Updates.set(AtlasS3ExportIntegration.REGION, region),
            Updates.set(AtlasS3ExportIntegration.PREFIX, prefix),
            Updates.set(AtlasS3ExportIntegration.AUTH_TYPE, useAccessKeys
                ? AtlasS3ExportIntegration.AUTH_TYPE_ACCESS_KEYS : AtlasS3ExportIntegration.AUTH_TYPE_ROLE),
            Updates.setOnInsert(AtlasS3ExportIntegration.CREATED_TS, now),
            Updates.set(AtlasS3ExportIntegration.UPDATED_TS, now)
        ));
        if (useAccessKeys) {
            updates.add(Updates.set(AtlasS3ExportIntegration.ACCESS_KEY_ID, accessKeyId));
            if (!secretAccessKey.isEmpty()) {
                updates.add(Updates.set(AtlasS3ExportIntegration.SECRET_ACCESS_KEY, secretAccessKey));
            }
            updates.add(Updates.unset(AtlasS3ExportIntegration.ROLE_ARN));
        } else {
            updates.add(Updates.set(AtlasS3ExportIntegration.ROLE_ARN, roleArn));
            updates.add(Updates.unset(AtlasS3ExportIntegration.ACCESS_KEY_ID));
            updates.add(Updates.unset(AtlasS3ExportIntegration.SECRET_ACCESS_KEY));
        }
        AtlasS3ExportIntegrationDao.instance.updateOne(new BasicDBObject(), Updates.combine(updates));
        loggerMaker.infoAndAddToDb("Saved Atlas S3 export integration for bucket " + bucketName);
        return Action.SUCCESS.toUpperCase();
    }

    public String removeAtlasS3ExportIntegration() {
        AtlasS3ExportIntegration existing = AtlasS3ExportIntegrationDao.instance.findOne(new BasicDBObject());
        if (existing != null && existing.getExportJobId() != null) {
            JobScheduler.deleteJob(existing.getExportJobId());
        }
        AtlasS3ExportIntegrationDao.instance.deleteAll(new BasicDBObject());
        loggerMaker.infoAndAddToDb("Removed Atlas S3 export integration");
        return Action.SUCCESS.toUpperCase();
    }
}
