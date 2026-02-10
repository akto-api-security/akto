package com.akto.account_job_executor.bigquery;

import com.akto.log.LoggerMaker;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.bigquery.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.*;

public class BigQueryConnector implements AutoCloseable {

    private static final LoggerMaker logger = new LoggerMaker(BigQueryConnector.class);
    private static final long POLL_INTERVAL_MS = 5000;

    private final BigQuery bigQueryClient;
    private final BigQueryConfig bigQueryConfig;
    private final String accountJobId;
    private final int accountId;

    private Job activeQueryJob;

    public BigQueryConnector(BigQueryConfig config, String jobId, int accountId) throws IOException {
        this.bigQueryConfig = config;
        this.accountJobId = jobId;
        this.accountId = accountId;
        this.bigQueryClient = createBigQueryClient(config);
        logger.info("BigQuery connector initialized: jobId={}, accountId={}, project={}", jobId, accountId,
                config.getProjectId());
    }

    @FunctionalInterface
    public interface BatchHandler {
        void handleBatch(List<Map<String, Object>> recordsBatch) throws Exception;
    }

    public void streamQueryResultsInBatches(int batchSize, Runnable heartbeat, BatchHandler batchHandler) throws Exception {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be > 0");
        }
        if (batchHandler == null) {
            throw new IllegalArgumentException("batchHandler cannot be null");
        }

        TableResult result = executeQueryWithPolling(heartbeat);
        Schema schema = result.getSchema();
        if (schema == null) {
            throw new IOException("BigQuery query returned null schema");
        }

        List<Field> schemaFields = schema.getFields();
        List<Map<String, Object>> recordsBatch = new ArrayList<>(batchSize);
        long totalRowsStreamed = 0;

        for (FieldValueList row : result.iterateAll()) {
            Map<String, Object> rowData = new LinkedHashMap<>();

            for (int i = 0; i < schemaFields.size(); i++) {
                Field field = schemaFields.get(i);
                FieldValue fieldValue = row.get(i);
                rowData.put(field.getName(), extractValue(fieldValue, field));
            }

            recordsBatch.add(rowData);
            totalRowsStreamed++;

            if (recordsBatch.size() >= batchSize) {
                batchHandler.handleBatch(recordsBatch);
                recordsBatch = new ArrayList<>(batchSize);
                if (heartbeat != null)
                    heartbeat.run();
            }
        }

        if (!recordsBatch.isEmpty()) {
            batchHandler.handleBatch(recordsBatch);
        }

        logger.info("Streamed {} rows from BigQuery: jobId={}, accountId={}", totalRowsStreamed, accountJobId, accountId);
        
        if (totalRowsStreamed >= bigQueryConfig.getMaxQueryRows()) {
            logger.warn("Query result count ({}) reached MAX_QUERY_ROWS limit. Data may have been truncated. " +
                    "Consider reducing the query time window. jobId={}, accountId={}", 
                    totalRowsStreamed, accountJobId, accountId);
        }
    }

    private TableResult executeQueryWithPolling(Runnable heartbeat) throws Exception {
        logger.info("Executing BigQuery query: jobId={}, accountId={}, fromDate={}, toDate={}",
                accountJobId, accountId, bigQueryConfig.getQueryStartTime(), bigQueryConfig.getQueryEndTime());

        String query = bigQueryConfig.buildQuery();
        logger.debug("Query: {}", query);

        QueryJobConfiguration.Builder queryConfigBuilder = QueryJobConfiguration.newBuilder(query)
                .addNamedParameter("fromDate", QueryParameterValue.timestamp(
                        bigQueryConfig.getQueryStartTime().toEpochMilli() * 1000))
                .addNamedParameter("toDate", QueryParameterValue.timestamp(
                        bigQueryConfig.getQueryEndTime().toEpochMilli() * 1000))
                .setUseLegacySql(false);

        QueryJobConfiguration queryConfig = queryConfigBuilder.build();

        JobId bigQueryJobId = JobId.newBuilder()
                .setProject(bigQueryConfig.getProjectId())
                .setJob(UUID.randomUUID().toString())
                .build();
        JobInfo jobInfo = JobInfo.newBuilder(queryConfig).setJobId(bigQueryJobId).build();
        Job queryJob = bigQueryClient.create(jobInfo);

        if (queryJob == null) {
            throw new IOException("Failed to create BigQuery job");
        }

        activeQueryJob = queryJob;

        JobId createdJobId = queryJob.getJobId();
        logger.info("Created BigQuery job: jobId={}, accountId={}, bqProject={}, bqJob={}",
                this.accountJobId, accountId, createdJobId.getProject(), createdJobId.getJob());

        long startMs = System.currentTimeMillis();
        long timeoutMs = Duration.ofSeconds(bigQueryConfig.getQueryTimeoutSeconds()).toMillis();

        while (true) {
            queryJob = bigQueryClient.getJob(createdJobId);
            if (queryJob == null) {
                throw new IOException(
                        "BigQuery job not found: " + createdJobId.getJob() + " in project " + createdJobId.getProject());
            }

            activeQueryJob = queryJob;

            JobStatus status = queryJob.getStatus();
            if (status != null && status.getError() != null) {
                BigQueryError error = status.getError();
                logger.error("BigQuery job failed: reason={}, location={}, message={}",
                        error.getReason(), error.getLocation(), error.getMessage());
                throw new IOException("BigQuery job failed: " + error.getMessage());
            }

            JobStatus.State state = status != null ? status.getState() : null;
            if (state == JobStatus.State.DONE) {
                break;
            }

            if (heartbeat != null)
                heartbeat.run();
            if (System.currentTimeMillis() - startMs > timeoutMs) {
                cancelActiveQueryJob();
                throw new IOException("BigQuery query timed out after " + bigQueryConfig.getQueryTimeoutSeconds() + " seconds");
            }

            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                cancelActiveQueryJob();
                throw new IOException("BigQuery query polling interrupted", ie);
            }
        }

        if (heartbeat != null)
            heartbeat.run();

        return queryJob.getQueryResults(BigQuery.QueryResultsOption.pageSize(bigQueryConfig.getQueryPageSize()));
    }

    private void cancelActiveQueryJob() {
        try {
            Job job = activeQueryJob;
            if (job != null) {
                logger.info("Cancelling BigQuery job: bqJob={}", job.getJobId().getJob());
                job.cancel();
            }
        } catch (Exception e) {
            logger.warn("Failed to cancel BigQuery job: {}", e.getMessage());
        }
    }

    private Object extractValue(FieldValue fieldValue, Field field) {
        if (fieldValue.isNull()) {
            return null;
        }

        switch (fieldValue.getAttribute()) {
            case PRIMITIVE:
                return extractPrimitiveValue(fieldValue, field);
            case REPEATED:
                List<Object> list = new ArrayList<>();
                FieldList subFields = field != null ? field.getSubFields() : null;
                Field elementField = (subFields != null && !subFields.isEmpty()) ? subFields.get(0) : null;
                for (FieldValue item : fieldValue.getRepeatedValue()) {
                    list.add(extractValue(item, elementField));
                }
                return list;
            case RECORD:
                Map<String, Object> record = new LinkedHashMap<>();
                FieldList recordFields = field != null ? field.getSubFields() : null;
                List<FieldValue> recordValues = fieldValue.getRecordValue();
                if (recordFields != null && recordFields.size() == recordValues.size()) {
                    for (int i = 0; i < recordFields.size(); i++) {
                        Field subField = recordFields.get(i);
                        FieldValue subValue = recordValues.get(i);
                        record.put(subField.getName(), extractValue(subValue, subField));
                    }
                } else {
                    // Fallback: use index-based keys if schema not available
                    for (int i = 0; i < recordValues.size(); i++) {
                        record.put("field_" + i, extractValue(recordValues.get(i), null));
                    }
                }
                return record;
            default:
                return fieldValue.getStringValue();
        }
    }

    private Object extractPrimitiveValue(FieldValue fieldValue, Field field) {
        if (field == null || field.getType() == null) {
            return fieldValue.getStringValue();
        }

        StandardSQLTypeName typeName = field.getType().getStandardType();
        if (typeName == null) {
            return fieldValue.getStringValue();
        }

        switch (typeName) {
            case BOOL:
                return fieldValue.getBooleanValue();
            case INT64:
                return fieldValue.getLongValue();
            case FLOAT64:
                return fieldValue.getDoubleValue();
            case TIMESTAMP:
                // BigQuery returns microseconds since epoch. Convert to epoch millis for ingestion
                return fieldValue.getTimestampValue() / 1000L;
            default:
                return fieldValue.getStringValue();
        }
    }

    private BigQuery createBigQueryClient(BigQueryConfig config) throws IOException {
        GoogleCredentials credentials = loadCredentials(config);

        return BigQueryOptions.newBuilder()
                .setProjectId(config.getProjectId())
                .setCredentials(credentials)
                .build()
                .getService();
    }

    private GoogleCredentials loadCredentials(BigQueryConfig config) throws IOException {
        GoogleCredentials credentials;
        String jsonAuthFilePath = config.getJsonAuthFilePath();

        if (jsonAuthFilePath != null && !jsonAuthFilePath.isEmpty()) {
            logger.info("Loading credentials from JSON auth file: {}", jsonAuthFilePath);
            try (FileInputStream fis = new FileInputStream(jsonAuthFilePath)) {
                credentials = ServiceAccountCredentials.fromStream(fis);
            }
        } else {
            logger.info("Using Application Default Credentials (ADC)");
            credentials = GoogleCredentials.getApplicationDefault();
        }

        // Scope credentials for BigQuery access
        if (credentials.createScopedRequired()) {
            credentials = credentials.createScoped("https://www.googleapis.com/auth/bigquery");
        }
        return credentials;
    }

    @Override
    public void close() {
        cancelActiveQueryJob();
        logger.debug("BigQuery connector closed");
    }
}
