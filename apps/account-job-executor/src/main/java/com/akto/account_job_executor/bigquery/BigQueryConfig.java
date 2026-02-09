package com.akto.account_job_executor.bigquery;

import java.time.Instant;
import java.util.Map;
import java.util.regex.Pattern;

import static com.akto.jobs.executors.AIAgentConnectorConstants.*;

public class BigQueryConfig {
    private static final Pattern PROJECT_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");
    private static final Pattern DATASET_TABLE_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    private final String projectId;
    private final String dataset;
    private final String table;

    private final Instant queryStartTime;
    private final Instant queryEndTime;

    private final String ingestionServiceUrl;
    private final String authToken;

    private static final int INGESTION_BATCH_SIZE = 100;
    private static final int QUERY_PAGE_SIZE = 1000;
    private static final int QUERY_TIMEOUT_SECONDS = 600;
    private static final int MAX_QUERY_ROWS = 10_000;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int SOCKET_TIMEOUT_MS = 30_000;

    private static final String QUERY_TEMPLATE = "SELECT endpoint, deployed_model_id, logging_time, request_id, request_payload, response_payload "
            +
            "FROM `%s.%s.%s` " +
            "WHERE logging_time BETWEEN @fromDate AND @toDate " +
            "ORDER BY logging_time DESC";

    private BigQueryConfig(String projectId, String dataset, String table,
                           Instant queryStartTime, Instant queryEndTime,
                           String ingestionServiceUrl, String authToken) {
        this.projectId = projectId;
        this.dataset = dataset;
        this.table = table;
        this.queryStartTime = queryStartTime;
        this.queryEndTime = queryEndTime;
        this.ingestionServiceUrl = ingestionServiceUrl;
        this.authToken = authToken;
    }

    /**
     * Create BigQueryConfig from job configuration.
     *
     * @param jobConfig Map containing projectId, dataset, table
     * @param fromDate  Start of time range to query
     * @param toDate    End of time range to query
     */
    public static BigQueryConfig fromJobConfig(Map<String, Object> jobConfig, Instant fromDate, Instant toDate) {
        String projectId = requireProjectId(jobConfig, CONFIG_VERTEX_AI_PROJECT_ID);
        String dataset = requireDatasetOrTable(jobConfig, CONFIG_VERTEX_AI_BIGQUERY_DATASET);
        String table = requireDatasetOrTable(jobConfig, CONFIG_VERTEX_AI_BIGQUERY_TABLE);

        String ingestionServiceUrl = getOptionalString(jobConfig, CONFIG_DATA_INGESTION_SERVICE_URL, null);
        if (ingestionServiceUrl == null || ingestionServiceUrl.isEmpty()) {
            throw new IllegalArgumentException(
                    "Ingestion service URL not set. Provide '" + CONFIG_DATA_INGESTION_SERVICE_URL + "' in job config");
        }

        String authToken = System.getenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN");
        if (authToken == null || authToken.isEmpty()) {
            throw new IllegalArgumentException(
                    "DATABASE_ABSTRACTOR_SERVICE_TOKEN environment variable not set. Required for DIS authentication.");
        }

        return new BigQueryConfig(projectId, dataset, table, fromDate, toDate, ingestionServiceUrl, authToken);
    }

    public String buildQuery() {
        return String.format(QUERY_TEMPLATE, projectId, dataset, table) + " LIMIT " + MAX_QUERY_ROWS;
    }

    private static String requireProjectId(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value == null || value.toString().isEmpty()) {
            throw new IllegalArgumentException("Required config parameter missing: " + key);
        }
        String stringValue = value.toString();
        if (!PROJECT_ID_PATTERN.matcher(stringValue).matches()) {
            throw new IllegalArgumentException(
                    "Invalid project ID '" + key + "'. " +
                    "Only alphanumeric characters, dots, underscores, and hyphens are allowed. Got: " + stringValue);
        }
        return stringValue;
    }

    private static String requireDatasetOrTable(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value == null || value.toString().isEmpty()) {
            throw new IllegalArgumentException("Required config parameter missing: " + key);
        }
        String stringValue = value.toString();
        if (!DATASET_TABLE_PATTERN.matcher(stringValue).matches()) {
            throw new IllegalArgumentException(
                    "Invalid dataset/table name '" + key + "'. " +
                    "Only alphanumeric characters and underscores are allowed (no dots or hyphens). Got: " + stringValue);
        }
        return stringValue;
    }

    private static String getOptionalString(Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    // Getters
    public String getProjectId() {
        return projectId;
    }

    public String getDataset() {
        return dataset;
    }

    public String getTable() {
        return table;
    }

    public Instant getQueryStartTime() {
        return queryStartTime;
    }

    public Instant getQueryEndTime() {
        return queryEndTime;
    }

    public String getIngestionServiceUrl() {
        return ingestionServiceUrl;
    }

    public String getAuthToken() {
        return authToken;
    }

    public int getIngestionBatchSize() {
        return INGESTION_BATCH_SIZE;
    }

    public int getQueryPageSize() {
        return QUERY_PAGE_SIZE;
    }

    public int getQueryTimeoutSeconds() {
        return QUERY_TIMEOUT_SECONDS;
    }

    public int getConnectTimeoutMs() {
        return CONNECT_TIMEOUT_MS;
    }

    public int getSocketTimeoutMs() {
        return SOCKET_TIMEOUT_MS;
    }

    public int getMaxQueryRows() {
        return MAX_QUERY_ROWS;
    }

    @Override
    public String toString() {
        return "BigQueryConfig{" +
                "projectId='" + projectId + '\'' +
                ", dataset='" + dataset + '\'' +
                ", table='" + table + '\'' +
                ", queryStartTime=" + queryStartTime +
                ", queryEndTime=" + queryEndTime +
                '}';
    }
}
