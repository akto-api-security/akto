package com.akto.account_job_executor.executor.executors;

import com.akto.account_job_executor.bigquery.BigQueryConfig;
import com.akto.account_job_executor.bigquery.BigQueryConnector;
import com.akto.account_job_executor.bigquery.BigQueryIngestionClient;
import com.akto.account_job_executor.client.CyborgApiClient;
import com.akto.account_job_executor.executor.AccountJobExecutor;
import com.akto.dto.jobs.AccountJob;
import com.akto.jobs.exception.RetryableJobException;
import com.akto.log.LoggerMaker;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class BigQueryExecutor extends AccountJobExecutor {

    public static final BigQueryExecutor INSTANCE = new BigQueryExecutor();

    private static final LoggerMaker logger = new LoggerMaker(BigQueryExecutor.class);
    private static final String INGESTION_SOURCE_TAG = "MIRRORING";
    private static final String CURSOR_KEY = "lastQueriedUntilEpochMs";

    private BigQueryExecutor() {
    }

    @Override
    protected void runJob(AccountJob job) throws Exception {
        logger.info("Executing BigQuery job: jobId={}, subType={}", job.getId(), job.getSubType());

        Map<String, Object> jobConfig = job.getConfig();
        if (jobConfig == null || jobConfig.isEmpty()) {
            throw new IllegalArgumentException("Job config is null or empty for job: " + job.getId());
        }

        Instant now = Instant.now();
        int recurringIntervalSeconds = job.getRecurringIntervalSeconds();

        Object cursorVal = jobConfig.get(CURSOR_KEY);
        Long lastCursorMs = cursorVal instanceof Number ? ((Number) cursorVal).longValue() : null;
        
        Instant rangeStart;
        Instant rangeEnd = now;
        
        if (lastCursorMs != null && lastCursorMs > 0) {
            rangeStart = Instant.ofEpochMilli(lastCursorMs);
            if (recurringIntervalSeconds > 0) {
                rangeEnd = rangeStart.plusSeconds(recurringIntervalSeconds);
                if (rangeEnd.isAfter(now)) {
                    rangeEnd = now;
                }
            }
        } else {
            int lookbackSeconds = recurringIntervalSeconds > 0 ? recurringIntervalSeconds : 300;
            rangeStart = now.minusSeconds(lookbackSeconds);
        }
        
        if (!rangeStart.isBefore(rangeEnd)) {
            logger.info("No new time range to query: rangeStart={} >= rangeEnd={}. Skipping job.", rangeStart, rangeEnd);
            return;
        }

        BigQueryConfig bigQueryConfig = BigQueryConfig.fromJobConfig(jobConfig, rangeStart, rangeEnd);
        logger.info("BigQuery config: {}", bigQueryConfig);

        int accountId = job.getAccountId();
        if (accountId <= 0) {
            throw new IllegalArgumentException("Invalid accountId: " + accountId + " for job: " + job.getId());
        }

        String jobIdString = String.valueOf(job.getId());
        int[] batchCounter = { 0 };
        int[] totalRecordsIngested = { 0 };

        try (BigQueryConnector bigQueryConnector = new BigQueryConnector(bigQueryConfig, jobIdString, accountId);
                BigQueryIngestionClient bigQueryIngestionClient = new BigQueryIngestionClient(
                        bigQueryConfig.getIngestionServiceUrl(),
                        bigQueryConfig.getAuthToken(),
                        bigQueryConfig.getConnectTimeoutMs(),
                        bigQueryConfig.getSocketTimeoutMs(),
                        jobIdString,
                        accountId,
                        job.getJobType(),
                        job.getSubType())) {

            logger.info("Connected to BigQuery project: {}", bigQueryConfig.getProjectId());
            logger.info("Sending data to ingestion service: {}", bigQueryConfig.getIngestionServiceUrl());

            updateJobHeartbeat(job);

            logger.info("Executing BigQuery query (streaming)...");

            bigQueryConnector.streamQueryResultsInBatches(
                    bigQueryConfig.getIngestionBatchSize(),
                    () -> updateJobHeartbeat(job),
                    batch -> {
                        int batchNumber = ++batchCounter[0];
                        int sent = bigQueryIngestionClient.ingestRecordsBatch(
                                batch,
                                INGESTION_SOURCE_TAG,
                                batchNumber,
                                () -> updateJobHeartbeat(job));
                        totalRecordsIngested[0] += sent;
                    });

            logger.info("Successfully ingested {} records in {} batches", totalRecordsIngested[0], batchCounter[0]);
        } catch (Exception e) {
            if (isRetryableFailure(e)) {
                logger.warn("Retryable failure during BigQuery job: jobId={}, error={}", job.getId(), e.getMessage());
                throw new RetryableJobException(e.getMessage() != null ? e.getMessage() : "Transient failure", e);
            }
            throw e;
        }

        long newCursorMs = rangeEnd.toEpochMilli();
        Map<String, Object> updates = new HashMap<>();
        updates.put("config." + CURSOR_KEY, newCursorMs);
        CyborgApiClient.updateJob(job.getId(), updates);
        logger.info("Updated cursor to {} (rangeEnd={})", newCursorMs, rangeEnd);

        logger.info("BigQuery job completed successfully: jobId={}", job.getId());
    }

    private static boolean isRetryableFailure(Throwable error) {
        Set<Throwable> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = error; current != null && seen.add(current); current = current.getCause()) {
            if (isRetryableException(current)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRetryableException(Throwable error) {
        if (error instanceof BigQueryIngestionClient.IngestionHttpException) {
            return ((BigQueryIngestionClient.IngestionHttpException) error).isRetryable();
        }
        if (error instanceof IOException) {
            String msg = error.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("interrupted")) {
                    return false;
                }
                if (lower.contains("timed out") || lower.contains("timeout") || lower.contains("connection reset")) {
                    return true;
                }
            }
        }
        return false;
    }

}
