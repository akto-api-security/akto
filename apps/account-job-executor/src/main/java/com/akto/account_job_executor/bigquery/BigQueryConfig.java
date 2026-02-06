package com.akto.account_job_executor.bigquery;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.regex.Pattern;

public class BigQueryConfig {

    private String projectId;
    private String dataset;
    private String table;

    private Instant fromDate;
    private Instant toDate;

    private String ingestionServiceUrl;
    private String authToken;
    private String credentialsPath;
    private String credentialsJson;
    private String credentialsJsonBase64;

    private int ingestionBatchSize;
    private int queryPageSize;
    private int queryTimeoutSeconds;
    private Integer maxRows;
    private Long maximumBytesBilled;
    private int connectTimeoutMs;
    private int socketTimeoutMs;

    private static final int DEFAULT_INGESTION_BATCH_SIZE = 100;
    private static final int DEFAULT_QUERY_PAGE_SIZE = 1000;
    private static final int DEFAULT_QUERY_TIMEOUT_SECONDS = 600;
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
    private static final int DEFAULT_SOCKET_TIMEOUT_MS = 30_000;

    private static final Pattern BQ_PROJECT_PATTERN = Pattern.compile("^[A-Za-z0-9_\\-]+$");
    private static final Pattern BQ_DATASET_TABLE_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");

    private static final String DEFAULT_QUERY_TEMPLATE = "SELECT endpoint, deployed_model_id, logging_time, request_id, request_payload, response_payload "
            +
            "FROM `%s.%s.%s` " +
            "WHERE logging_time BETWEEN @fromDate AND @toDate " +
            "ORDER BY logging_time DESC";

    private BigQueryConfig() {
    }

    public static BigQueryConfig fromJobConfig(Map<String, Object> jobConfig) {
        BigQueryConfig config = new BigQueryConfig();

        config.projectId = getRequiredString(jobConfig, "projectId");
        config.dataset = getRequiredString(jobConfig, "dataset");
        config.table = getRequiredString(jobConfig, "table");

        validateProjectId("projectId", config.projectId);
        validateDatasetOrTable("dataset", config.dataset);
        validateDatasetOrTable("table", config.table);

        config.fromDate = parseDate(jobConfig.get("fromDate"), Instant.now().minus(24, ChronoUnit.HOURS));
        config.toDate = parseDate(jobConfig.get("toDate"), Instant.now());

        if (config.fromDate.isAfter(config.toDate)) {
            throw new IllegalArgumentException("Invalid time range: fromDate is after toDate");
        }

        config.ingestionServiceUrl = getString(jobConfig, "ingestionServiceUrl",
                System.getenv("DATA_INGESTION_SERVICE_URL"));

        config.authToken = getString(jobConfig, "authToken",
                System.getenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN"));

        config.credentialsPath = getString(jobConfig, "credentialsPath",
                System.getenv("GOOGLE_APPLICATION_CREDENTIALS"));

        config.credentialsJson = getString(jobConfig, "credentialsJson",
                System.getenv("GOOGLE_APPLICATION_CREDENTIALS_JSON"));

        config.credentialsJsonBase64 = getString(jobConfig, "credentialsJsonBase64",
                System.getenv("GOOGLE_APPLICATION_CREDENTIALS_BASE64"));

        config.ingestionBatchSize = getInt(jobConfig, "ingestionBatchSize", DEFAULT_INGESTION_BATCH_SIZE);
        config.queryPageSize = getInt(jobConfig, "queryPageSize", DEFAULT_QUERY_PAGE_SIZE);
        config.queryTimeoutSeconds = getInt(jobConfig, "queryTimeoutSeconds", DEFAULT_QUERY_TIMEOUT_SECONDS);
        config.maxRows = getOptionalInt(jobConfig, "maxRows");
        config.maximumBytesBilled = getOptionalLong(jobConfig, "maximumBytesBilled");
        config.connectTimeoutMs = getInt(jobConfig, "connectTimeoutMs", DEFAULT_CONNECT_TIMEOUT_MS);
        config.socketTimeoutMs = getInt(jobConfig, "socketTimeoutMs", DEFAULT_SOCKET_TIMEOUT_MS);

        validateNumericConfig(config);

        // Validate ingestion service URL early to fail fast
        if (config.ingestionServiceUrl == null || config.ingestionServiceUrl.isEmpty()) {
            throw new IllegalArgumentException(
                    "ingestionServiceUrl not set. Provide in job config or set DATA_INGESTION_SERVICE_URL env var");
        }

        if (config.authToken == null || config.authToken.isEmpty()) {
            throw new IllegalArgumentException(
                    "authToken not set. Provide in job config or set DATABASE_ABSTRACTOR_SERVICE_TOKEN env var");
        }

        return config;
    }

    public String getQuery() {
        String baseQuery = String.format(DEFAULT_QUERY_TEMPLATE, projectId, dataset, table);
        if (maxRows != null && maxRows > 0) {
            return baseQuery + " LIMIT " + maxRows;
        }
        return baseQuery;
    }

    private static String getRequiredString(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value == null || value.toString().isEmpty()) {
            throw new IllegalArgumentException("Required config parameter missing: " + key);
        }
        return value.toString();
    }

    private static String getString(Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private static Instant parseDate(Object value, Instant defaultValue) {
        if (value == null)
            return defaultValue;
        try {
            return Instant.parse(value.toString());
        } catch (Exception e) {
            try {
                long epoch = Long.parseLong(value.toString());
                // Support both epoch seconds and epoch millis
                // epoch millis is typically >= 10^12 (2001-09-09 and later)
                if (epoch >= 1_000_000_000_000L) {
                    return Instant.ofEpochMilli(epoch);
                } else {
                    return Instant.ofEpochSecond(epoch);
                }
            } catch (NumberFormatException nfe) {
                return defaultValue;
            }
        }
    }

    private static void validateProjectId(String key, String value) {
        if (value == null || value.isEmpty() || !BQ_PROJECT_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid BigQuery project identifier for " + key
                    + ": only alphanumeric, underscores, and hyphens allowed");
        }
    }

    private static void validateDatasetOrTable(String key, String value) {
        if (value == null || value.isEmpty() || !BQ_DATASET_TABLE_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid BigQuery identifier for " + key
                    + ": only alphanumeric and underscores allowed (no hyphens)");
        }
    }

    private static final int MAX_INGESTION_BATCH_SIZE = 10_000;
    private static final int MAX_QUERY_PAGE_SIZE = 10_000;
    private static final int MAX_QUERY_TIMEOUT_SECONDS = 3600;
    private static final int MAX_TIMEOUT_MS = 300_000;

    private static void validateNumericConfig(BigQueryConfig config) {
        if (config.ingestionBatchSize < 1 || config.ingestionBatchSize > MAX_INGESTION_BATCH_SIZE) {
            throw new IllegalArgumentException("ingestionBatchSize must be between 1 and " + MAX_INGESTION_BATCH_SIZE
                    + ", got: " + config.ingestionBatchSize);
        }
        if (config.queryPageSize < 1 || config.queryPageSize > MAX_QUERY_PAGE_SIZE) {
            throw new IllegalArgumentException("queryPageSize must be between 1 and " + MAX_QUERY_PAGE_SIZE
                    + ", got: " + config.queryPageSize);
        }
        if (config.queryTimeoutSeconds < 1 || config.queryTimeoutSeconds > MAX_QUERY_TIMEOUT_SECONDS) {
            throw new IllegalArgumentException("queryTimeoutSeconds must be between 1 and " + MAX_QUERY_TIMEOUT_SECONDS
                    + ", got: " + config.queryTimeoutSeconds);
        }
        if (config.connectTimeoutMs < 1 || config.connectTimeoutMs > MAX_TIMEOUT_MS) {
            throw new IllegalArgumentException("connectTimeoutMs must be between 1 and " + MAX_TIMEOUT_MS
                    + ", got: " + config.connectTimeoutMs);
        }
        if (config.socketTimeoutMs < 1 || config.socketTimeoutMs > MAX_TIMEOUT_MS) {
            throw new IllegalArgumentException("socketTimeoutMs must be between 1 and " + MAX_TIMEOUT_MS
                    + ", got: " + config.socketTimeoutMs);
        }
        if (config.maxRows != null && config.maxRows < 1) {
            throw new IllegalArgumentException("maxRows must be positive when set, got: " + config.maxRows);
        }
        if (config.maximumBytesBilled != null && config.maximumBytesBilled < 1) {
            throw new IllegalArgumentException("maximumBytesBilled must be positive when set, got: "
                    + config.maximumBytesBilled);
        }
    }

    private static int getInt(Map<String, Object> config, String key, int defaultValue) {
        Object value = config.get(key);
        if (value == null)
            return defaultValue;
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static Integer getOptionalInt(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value == null)
            return null;
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static Long getOptionalLong(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value == null)
            return null;
        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return null;
        }
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

    public Instant getFromDate() {
        return fromDate;
    }

    public Instant getToDate() {
        return toDate;
    }

    public String getIngestionServiceUrl() {
        return ingestionServiceUrl;
    }

    public String getAuthToken() {
        return authToken;
    }

    public String getCredentialsPath() {
        return credentialsPath;
    }

    public String getCredentialsJson() {
        return credentialsJson;
    }

    public String getCredentialsJsonBase64() {
        return credentialsJsonBase64;
    }

    public int getIngestionBatchSize() {
        return ingestionBatchSize;
    }

    public int getQueryPageSize() {
        return queryPageSize;
    }

    public int getQueryTimeoutSeconds() {
        return queryTimeoutSeconds;
    }

    public Integer getMaxRows() {
        return maxRows;
    }

    public Long getMaximumBytesBilled() {
        return maximumBytesBilled;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int getSocketTimeoutMs() {
        return socketTimeoutMs;
    }

    public String getFromDateFormatted() {
        return DateTimeFormatter.ISO_INSTANT.format(fromDate);
    }

    public String getToDateFormatted() {
        return DateTimeFormatter.ISO_INSTANT.format(toDate);
    }

    @Override
    public String toString() {
        return "BigQueryConfig{" +
                "projectId='" + projectId + '\'' +
                ", dataset='" + dataset + '\'' +
                ", table='" + table + '\'' +
                ", fromDate=" + fromDate +
                ", toDate=" + toDate +
                ", ingestionBatchSize=" + ingestionBatchSize +
                ", queryPageSize=" + queryPageSize +
                ", queryTimeoutSeconds=" + queryTimeoutSeconds +
                ", maxRows=" + maxRows +
                ", maximumBytesBilled=" + maximumBytesBilled +
                ", connectTimeoutMs=" + connectTimeoutMs +
                ", socketTimeoutMs=" + socketTimeoutMs +
                '}';
    }
}
