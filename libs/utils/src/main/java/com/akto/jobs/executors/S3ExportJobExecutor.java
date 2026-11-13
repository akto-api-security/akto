package com.akto.jobs.executors;

import java.util.HashMap;
import java.util.Map;

import com.akto.billing.UsageMetricUtils;
import com.akto.dao.S3ExportIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dao.jobs.JobsDao;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.jobs.Job;
import com.akto.dto.jobs.JobStatus;
import com.akto.dto.jobs.S3ExportJobParams;
import com.akto.dto.s3_export.S3ExportIntegration;
import com.akto.jobs.JobExecutor;
import com.akto.jobs.exception.RetryableJobException;
import com.akto.log.LoggerMaker;
import com.akto.s3_export.S3ExportDataset;
import com.akto.s3_export.S3ExportPartSink;
import com.akto.s3_export.S3ExportUtils;
import com.akto.s3_export.S3ExportWriter;
import com.akto.util.DashboardMode;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

/**
 * Exports the account's enabled datasets to the customer's S3 bucket, one closed UTC hour at a time.
 * Each hour is written under a fixed key, so a retried hour overwrites itself; the dataset's
 * checkpoint moves past an hour only after its _SUCCESS marker is written.
 */
public class S3ExportJobExecutor extends JobExecutor<S3ExportJobParams> {

    private static final LoggerMaker logger = new LoggerMaker(S3ExportJobExecutor.class, LoggerMaker.LogDb.DASHBOARD);
    public static final S3ExportJobExecutor INSTANCE = new S3ExportJobExecutor();

    public static final String FEATURE_LABEL = "S3_EXPORT";

    public S3ExportJobExecutor() {
        super(S3ExportJobParams.class);
    }

    @Override
    protected void runJob(Job job) throws Exception {
        int accountId = job.getAccountId();
        S3ExportJobParams params = paramClass.cast(job.getJobParams());
        if (params.getExportedUntilMs() == null) params.setExportedUntilMs(new HashMap<>());

        S3ExportIntegration integration = S3ExportIntegrationDao.instance.findOne(new BasicDBObject());
        if (integration == null || !integration.isEnabled()) {
            logger.infoAndAddToDb("S3 export skipped, not enabled: accountId={}", accountId);
            return;
        }
        if (DashboardMode.isMetered()) {
            FeatureAccess access = UsageMetricUtils.getFeatureAccessSaas(accountId, FEATURE_LABEL);
            if (access == null || !access.getIsGranted()) {
                logger.infoAndAddToDb("S3 export skipped, feature not granted: accountId={}", accountId);
                return;
            }
        }

        params.setLastRunAt(Context.now());
        Map<String, Long> recordCounts = new HashMap<>();
        Exception firstFailure = null;
        try (S3ExportWriter writer = new S3ExportWriter(accountId, integration.getBucket(), integration.getRegion(),
                integration.getRoleArn(), integration.getExternalId(), integration.getKmsKeyArn())) {
            for (String name : integration.getDatasets()) {
                S3ExportDataset dataset = S3ExportDataset.fromName(name);
                if (dataset == null) continue;
                // one failing dataset must not block the others
                try {
                    recordCounts.put(name, exportDataset(job, params, integration, dataset, writer));
                } catch (Exception e) {
                    if (firstFailure == null) firstFailure = e;
                }
            }
        } catch (Exception e) {
            if (firstFailure == null) firstFailure = e;
        }

        params.setLastRecordCounts(recordCounts);
        if (firstFailure == null) {
            params.setLastSuccessAt(Context.now());
            params.setLastError(null);
            updateJobParams(job, params);
            return;
        }
        String description = S3ExportUtils.describeError(firstFailure);
        params.setLastError(description);
        updateJobParams(job, params);
        if (S3ExportUtils.isRetryable(firstFailure)) throw new RetryableJobException(description, firstFailure);
        throw firstFailure;
    }

    /** Exports the closed hours past the dataset's checkpoint (at most one run's worth); returns the rows written. */
    private long exportDataset(Job job, S3ExportJobParams params, S3ExportIntegration integration,
                               S3ExportDataset dataset, S3ExportWriter writer) throws Exception {
        long nowMs = System.currentTimeMillis();
        long cursor = S3ExportUtils.startCursor(params.getExportedUntilMs().get(dataset.name()), nowMs);
        long records = 0;
        for (long windowStart : S3ExportUtils.windowStarts(cursor, nowMs)) {
            if (!isStillRunning(job)) {
                logger.infoAndAddToDb("S3 export stopped: accountId={} dataset={}", job.getAccountId(), dataset.name());
                break;
            }
            records += exportHour(job, integration, dataset, windowStart, writer);
            // resume point if a later hour fails or the run dies
            params.getExportedUntilMs().put(dataset.name(), windowStart + S3ExportUtils.HOUR_MS);
            updateJobParams(job, params);
        }
        return records;
    }

    private long exportHour(Job job, S3ExportIntegration integration, S3ExportDataset dataset, long windowStartMs,
                            S3ExportWriter writer) throws Exception {
        int accountId = job.getAccountId();
        long windowEndMs = windowStartMs + S3ExportUtils.HOUR_MS;
        try {
            String dirKey = S3ExportUtils.hourDirKey(integration.getPrefix(), dataset, windowStartMs);
            S3ExportPartSink sink = new S3ExportPartSink(dirKey, writer, () -> updateJobHeartbeat(job));
            dataset.getSource().export(accountId, windowStartMs, windowEndMs, integration.isIncludePayloads(), sink::accept);
            sink.finish();
            writer.uploadJson(S3ExportUtils.successKey(dirKey), S3ExportUtils.successMarker(
                dataset, windowStartMs, windowEndMs, sink.getRecordCount(), sink.getPartCount(), System.currentTimeMillis()));
            logger.infoAndAddToDb("S3 export hour done: accountId={} dataset={} window=[{}, {}) records={} parts={}",
                accountId, dataset.name(), windowStartMs, windowEndMs, sink.getRecordCount(), sink.getPartCount());
            return sink.getRecordCount();
        } catch (Exception e) {
            logger.errorAndAddToDb("S3 export hour failed: accountId={} dataset={} window=[{}, {}) error={}",
                accountId, dataset.name(), windowStartMs, windowEndMs, S3ExportUtils.describeError(e));
            throw e;
        }
    }

    // the job is stopped when the integration is removed or re-saved; also keeps the heartbeat alive
    private boolean isStillRunning(Job job) {
        updateJobHeartbeat(job);
        Job latest = JobsDao.instance.findOne(Filters.eq(Job.ID, job.getId()));
        return latest != null && latest.getJobStatus() == JobStatus.RUNNING;
    }
}
