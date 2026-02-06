package com.akto.account_job_executor.executor.executors;

import com.akto.account_job_executor.bigquery.BigQueryConfig;
import com.akto.account_job_executor.bigquery.BigQueryConnector;
import com.akto.account_job_executor.bigquery.BigQueryIngestionClient;
import com.akto.account_job_executor.executor.AccountJobExecutor;
import com.akto.dto.jobs.AccountJob;
import com.akto.jobs.exception.RetryableJobException;
import com.akto.log.LoggerMaker;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class BigQueryExecutor extends AccountJobExecutor {

    public static final BigQueryExecutor INSTANCE = new BigQueryExecutor();

    private static final LoggerMaker logger = new LoggerMaker(BigQueryExecutor.class);
    private static final String SOURCE_TAG = "vertex-ai-custom-deployed-models";

    private BigQueryExecutor() {
    }

    @Override
    protected void runJob(AccountJob job) throws Exception {
        logger.info("Executing BigQuery job: jobId={}, subType={}", job.getId(), job.getSubType());

        Map<String, Object> config = job.getConfig();
        if (config == null || config.isEmpty()) {
            throw new IllegalArgumentException("Job config is null or empty for job: " + job.getId());
        }
        BigQueryConfig bqConfig = BigQueryConfig.fromJobConfig(config);
        logger.info("BigQuery config: {}", bqConfig);

        int accountId = job.getAccountId();
        if (accountId <= 0) {
            throw new IllegalArgumentException("Invalid accountId: " + accountId + " for job: " + job.getId());
        }

        BigQueryConnector connector = null;
        BigQueryIngestionClient ingestionClient = null;
        AtomicInteger batchNumber = new AtomicInteger(0);
        AtomicInteger ingested = new AtomicInteger(0);

        try {
            logger.info("Connecting to BigQuery project: {}", bqConfig.getProjectId());
            connector = new BigQueryConnector(bqConfig);

            logger.info("Sending data to ingestion service: {}", bqConfig.getIngestionServiceUrl());
            ingestionClient = new BigQueryIngestionClient(
                    bqConfig.getIngestionServiceUrl(),
                    bqConfig.getAuthToken(),
                    bqConfig.getIngestionBatchSize(),
                    bqConfig.getConnectTimeoutMs(),
                    bqConfig.getSocketTimeoutMs());

            updateJobHeartbeat(job);

            logger.info("Executing BigQuery query (streaming)...");
            final BigQueryIngestionClient finalIngestionClient = ingestionClient;
            final int finalAccountId = accountId;

            connector.streamQueryResults(
                    bqConfig.getIngestionBatchSize(),
                    () -> updateJobHeartbeat(job),
                    batch -> {
                        int batchNum = batchNumber.incrementAndGet();
                        int sent = finalIngestionClient.sendBatchToIngestionService(
                                batch,
                                SOURCE_TAG,
                                finalAccountId,
                                batchNum,
                                () -> updateJobHeartbeat(job));
                        ingested.addAndGet(sent);
                    });

            logger.info("Successfully ingested {} records in {} batches", ingested.get(), batchNumber.get());
        } catch (Exception e) {
            if (isTransientFailure(e)) {
                throw new RetryableJobException(e.getMessage() != null ? e.getMessage() : "Transient failure", e);
            }
            throw e;
        } finally {
            if (connector != null) {
                connector.close();
            }
            if (ingestionClient != null) {
                ingestionClient.close();
            }
        }

        logger.info("BigQuery job completed successfully: jobId={}", job.getId());
    }

    private static boolean isTransientFailure(Throwable e) {
        if (e == null) {
            return false;
        }
        if (e instanceof BigQueryIngestionClient.IngestionHttpException) {
            int status = ((BigQueryIngestionClient.IngestionHttpException) e).getStatusCode();
            return status == 408 || status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
        }
        if (e instanceof IOException) {
            String msg = e.getMessage();
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
