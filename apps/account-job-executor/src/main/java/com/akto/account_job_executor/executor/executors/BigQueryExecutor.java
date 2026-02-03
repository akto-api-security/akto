package com.akto.account_job_executor.executor.executors;

import com.akto.account_job_executor.bigquery.BigQueryConfig;
import com.akto.account_job_executor.bigquery.BigQueryConnector;
import com.akto.account_job_executor.bigquery.BigQueryIngestionClient;
import com.akto.account_job_executor.executor.AccountJobExecutor;
import com.akto.dto.jobs.AccountJob;
import com.akto.log.LoggerMaker;

import java.util.List;
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

        if (bqConfig.getAuthToken() == null || bqConfig.getAuthToken().isEmpty()) {
            throw new IllegalArgumentException("DATABASE_ABSTRACTOR_SERVICE_TOKEN not set");
        }

        BigQueryConnector connector = null;
        BigQueryIngestionClient ingestionClient = null;

        try {
            logger.info("Connecting to BigQuery project: {}", bqConfig.getProjectId());
            connector = new BigQueryConnector(bqConfig);

            updateJobHeartbeat(job);

            logger.info("Executing BigQuery query...");
            List<Map<String, Object>> results = connector.executeQuery();
            logger.info("Query returned {} rows", results.size());

            if (results.isEmpty()) {
                logger.info("No data returned from BigQuery, nothing to ingest");
                return;
            }

            updateJobHeartbeat(job);

            logger.info("Sending data to ingestion service: {}", bqConfig.getIngestionServiceUrl());
            ingestionClient = new BigQueryIngestionClient(
                    bqConfig.getIngestionServiceUrl(),
                    bqConfig.getAuthToken());

            int ingested = ingestionClient.sendToIngestionService(results, SOURCE_TAG);
            logger.info("Successfully ingested {} records", ingested);

            updateJobHeartbeat(job);

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
}
