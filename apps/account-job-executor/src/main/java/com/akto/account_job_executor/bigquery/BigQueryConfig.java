package com.akto.account_job_executor.bigquery;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;

public class BigQueryConfig {

    private String projectId;
    private String dataset;
    private String table;

    private Instant fromDate;
    private Instant toDate;

    private String ingestionServiceUrl;
    private String authToken;

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

        config.fromDate = parseDate(jobConfig.get("fromDate"), Instant.now().minus(24, ChronoUnit.HOURS));
        config.toDate = parseDate(jobConfig.get("toDate"), Instant.now());

        config.ingestionServiceUrl = getString(jobConfig, "ingestionServiceUrl",
                System.getenv("DATA_INGESTION_SERVICE_URL"));

        config.authToken = System.getenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN");

        return config;
    }

    public String getQuery() {
        return String.format(DEFAULT_QUERY_TEMPLATE, projectId, dataset, table);
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
                return Instant.ofEpochSecond(epoch);
            } catch (NumberFormatException nfe) {
                return defaultValue;
            }
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
                '}';
    }
}
