package com.akto.account_job_executor.bigquery;

import com.akto.log.LoggerMaker;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.bigquery.*;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

public class BigQueryConnector implements AutoCloseable {

    private static final LoggerMaker logger = new LoggerMaker(BigQueryConnector.class);

    private final BigQuery bigQueryClient;
    private final BigQueryConfig config;

    public BigQueryConnector(BigQueryConfig config) throws IOException {
        this.config = config;
        this.bigQueryClient = createBigQueryClient(config);
        logger.info("BigQuery connector initialized for project: {}", config.getProjectId());
    }

    @FunctionalInterface
    public interface BatchHandler {
        void handle(List<Map<String, Object>> batch) throws Exception;
    }

    public void streamQueryResults(int batchSize, Runnable heartbeat, BatchHandler batchHandler) throws Exception {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be > 0");
        }
        if (batchHandler == null) {
            throw new IllegalArgumentException("batchHandler cannot be null");
        }

        TableResult result = executeQueryWithPolling(heartbeat);
        Schema schema = result.getSchema();

        List<Map<String, Object>> batch = new ArrayList<>(batchSize);
        long rowCount = 0;

        for (FieldValueList row : result.iterateAll()) {
            Map<String, Object> rowMap = new LinkedHashMap<>();
            List<Field> fields = schema.getFields();

            for (int i = 0; i < fields.size(); i++) {
                Field field = fields.get(i);
                FieldValue value = row.get(i);
                rowMap.put(field.getName(), extractValue(value, field));
            }

            batch.add(rowMap);
            rowCount++;

            if (batch.size() >= batchSize) {
                batchHandler.handle(batch);
                batch = new ArrayList<>(batchSize);
                if (heartbeat != null)
                    heartbeat.run();
            }
        }

        if (!batch.isEmpty()) {
            batchHandler.handle(batch);
        }

        logger.info("Streamed {} rows from BigQuery", rowCount);
    }

    private TableResult executeQueryWithPolling(Runnable heartbeat) throws Exception {
        logger.info("Executing BigQuery query: fromDate={}, toDate={}",
                config.getFromDateFormatted(), config.getToDateFormatted());

        String query = config.getQuery();
        logger.debug("Query: {}", query);

        QueryJobConfiguration.Builder queryConfigBuilder = QueryJobConfiguration.newBuilder(query)
                .addNamedParameter("fromDate", QueryParameterValue.timestamp(
                        config.getFromDate().toEpochMilli() * 1000))
                .addNamedParameter("toDate", QueryParameterValue.timestamp(
                        config.getToDate().toEpochMilli() * 1000))
                .setUseLegacySql(false);

        if (config.getMaximumBytesBilled() != null && config.getMaximumBytesBilled() > 0) {
            queryConfigBuilder.setMaximumBytesBilled(config.getMaximumBytesBilled());
        }

        QueryJobConfiguration queryConfig = queryConfigBuilder.build();

        JobId jobId = JobId.newBuilder()
                .setProject(config.getProjectId())
                .setJob(UUID.randomUUID().toString())
                .build();
        JobInfo jobInfo = JobInfo.newBuilder(queryConfig).setJobId(jobId).build();
        Job queryJob = bigQueryClient.create(jobInfo);

        if (queryJob == null) {
            throw new IOException("Failed to create BigQuery job");
        }

        JobId actualJobId = queryJob.getJobId();
        logger.info("Created BigQuery job: project={}, job={}", actualJobId.getProject(), actualJobId.getJob());

        long startMs = System.currentTimeMillis();
        long timeoutMs = Duration.ofSeconds(config.getQueryTimeoutSeconds()).toMillis();

        while (true) {
            queryJob = bigQueryClient.getJob(actualJobId);
            if (queryJob == null) {
                throw new IOException(
                        "BigQuery job not found: " + actualJobId.getJob() + " in project " + actualJobId.getProject());
            }

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
                throw new IOException("BigQuery query timed out after " + config.getQueryTimeoutSeconds() + " seconds");
            }

            try {
                Thread.sleep(5000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IOException("BigQuery query polling interrupted", ie);
            }
        }

        if (heartbeat != null)
            heartbeat.run();

        return queryJob.getQueryResults(BigQuery.QueryResultsOption.pageSize(config.getQueryPageSize()));
    }

    private Object extractValue(FieldValue fieldValue, Field field) {
        if (fieldValue.isNull()) {
            return null;
        }

        switch (fieldValue.getAttribute()) {
            case PRIMITIVE:
                return extractPrimitive(fieldValue, field);
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

    private Object extractPrimitive(FieldValue fieldValue, Field field) {
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
        if (config.getCredentialsJson() != null && !config.getCredentialsJson().isEmpty()) {
            logger.info("Loading credentials from JSON content");
            credentials = ServiceAccountCredentials.fromStream(
                    new ByteArrayInputStream(config.getCredentialsJson().getBytes(StandardCharsets.UTF_8)));
        } else if (config.getCredentialsJsonBase64() != null && !config.getCredentialsJsonBase64().isEmpty()) {
            logger.info("Loading credentials from base64 JSON content");
            byte[] decoded = Base64.getDecoder().decode(config.getCredentialsJsonBase64());
            credentials = ServiceAccountCredentials.fromStream(new ByteArrayInputStream(decoded));
        } else if (config.getCredentialsPath() != null && !config.getCredentialsPath().isEmpty()) {
            logger.info("Loading credentials from file-based service account key");
            try (FileInputStream fis = new FileInputStream(config.getCredentialsPath())) {
                credentials = ServiceAccountCredentials.fromStream(fis);
            }
        } else {
            logger.info("Using Application Default Credentials (ADC) - running on GCP infrastructure");
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
        logger.debug("BigQuery connector closed");
    }
}