package com.akto.account_job_executor.executor.executors;

import com.akto.account_job_executor.bigquery.BigQueryConfig;
import com.akto.account_job_executor.bigquery.BigQueryConnector;
import com.akto.account_job_executor.bigquery.BigQueryIngestionClient;
import com.akto.account_job_executor.executor.AccountJobExecutor;
import com.akto.dto.jobs.AccountJob;
import com.akto.jobs.exception.RetryableJobException;
import com.akto.log.LoggerMaker;

import java.util.Map;

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

            final int[] batchNumber = new int[] { 0 };
            final int[] ingested = new int[] { 0 };

            connector.streamQueryResults(
                    bqConfig.getIngestionBatchSize(),
                    () -> updateJobHeartbeat(job),
                    batch -> {
                        batchNumber[0]++;
                        int sent = finalIngestionClient.sendBatchToIngestionService(
                                batch,
                                SOURCE_TAG,
                                finalAccountId,
                                batchNumber[0],
                                () -> updateJobHeartbeat(job));
                        ingested[0] += sent;
                    });

            logger.info("Successfully ingested {} records in {} batches", ingested[0], batchNumber[0]);

        } catch (Exception e) {
            if (isRetryable(e)) {
                throw new RetryableJobException("Transient failure in BigQueryExecutor", e);
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

    private boolean isRetryable(Throwable t) {
        if (t == null)
            return false;
        if (t instanceof java.net.SocketTimeoutException)
            return true;
        if (t instanceof java.net.ConnectException)
            return true;
        if (t instanceof java.net.UnknownHostException)
            return true;

        if (t instanceof com.google.cloud.bigquery.BigQueryException) {
            int code = ((com.google.cloud.bigquery.BigQueryException) t).getCode();
            return code == 408 || code == 429 || code == 500 || code == 502 || code == 503 || code == 504;
        }

        String msg = t.getMessage();
        if (msg != null) {
            String m = msg.toLowerCase();
            if (m.contains("timed out") || m.contains("timeout"))
                return true;
            if (m.contains("connection reset") || m.contains("refused"))
                return true;
            if (m.contains("ingestion service returned 429")
                    || m.contains("ingestion service returned 502")
                    || m.contains("ingestion service returned 503")
                    || m.contains("ingestion service returned 504")) {
                return true;
            }
        }

        return isRetryable(t.getCause());
    }

}
