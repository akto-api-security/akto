package com.akto.account_job_executor.bigquery;

import com.akto.log.LoggerMaker;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.bigquery.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class BigQueryConnector {

    private static final LoggerMaker logger = new LoggerMaker(BigQueryConnector.class);

    private final BigQuery bigQueryClient;
    private final BigQueryConfig config;

    public BigQueryConnector(BigQueryConfig config) throws IOException {
        this.config = config;
        this.bigQueryClient = createBigQueryClient(config.getProjectId());
        logger.info("BigQuery connector initialized for project: {}", config.getProjectId());
    }

    public List<Map<String, Object>> executeQuery() throws InterruptedException {
        logger.info("Executing BigQuery query: fromDate={}, toDate={}",
                config.getFromDateFormatted(), config.getToDateFormatted());

        String query = config.getQuery();
        logger.debug("Query: {}", query);

        QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query)
                .addNamedParameter("fromDate", QueryParameterValue.timestamp(
                        config.getFromDate().toEpochMilli() * 1000))
                .addNamedParameter("toDate", QueryParameterValue.timestamp(
                        config.getToDate().toEpochMilli() * 1000))
                .setUseLegacySql(false)
                .build();

        TableResult result = bigQueryClient.query(queryConfig);

        List<Map<String, Object>> rows = new ArrayList<>();
        Schema schema = result.getSchema();

        for (FieldValueList row : result.iterateAll()) {
            Map<String, Object> rowMap = new LinkedHashMap<>();
            List<Field> fields = schema.getFields();

            for (int i = 0; i < fields.size(); i++) {
                Field field = fields.get(i);
                FieldValue value = row.get(i);
                rowMap.put(field.getName(), extractValue(value));
            }
            rows.add(rowMap);
        }

        logger.info("Query returned {} rows", rows.size());
        return rows;
    }

    private Object extractValue(FieldValue fieldValue) {
        if (fieldValue.isNull()) {
            return null;
        }

        switch (fieldValue.getAttribute()) {
            case PRIMITIVE:
                return fieldValue.getStringValue();
            case REPEATED:
                List<Object> list = new ArrayList<>();
                for (FieldValue item : fieldValue.getRepeatedValue()) {
                    list.add(extractValue(item));
                }
                return list;
            case RECORD:
                Map<String, Object> record = new LinkedHashMap<>();
                for (FieldValue item : fieldValue.getRecordValue()) {
                    record.put(item.toString(), extractValue(item));
                }
                return record;
            default:
                return fieldValue.getStringValue();
        }
    }

    private BigQuery createBigQueryClient(String projectId) throws IOException {
        GoogleCredentials credentials = loadCredentials();

        return BigQueryOptions.newBuilder()
                .setProjectId(projectId)
                .setCredentials(credentials)
                .build()
                .getService();
    }

    private GoogleCredentials loadCredentials() throws IOException {
        String credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (credentialsPath == null || credentialsPath.isEmpty()) {
            throw new IOException("GOOGLE_APPLICATION_CREDENTIALS environment variable is not set");
        }
        logger.info("Loading credentials from file: {}", credentialsPath);
        return ServiceAccountCredentials.fromStream(new FileInputStream(credentialsPath));
    }

    public void close() {
        logger.debug("BigQuery connector closed");
    }
}
