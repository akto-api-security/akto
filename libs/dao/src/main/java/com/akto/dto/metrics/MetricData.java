package com.akto.dto.metrics;

import com.akto.dao.context.Context;
import com.akto.dto.data_types.Conditions;
import com.akto.dto.usage.MetricTypes;
import org.bson.types.ObjectId;

public class MetricData {
    private ObjectId id;
    private String metricId;
    private float value;
    private String orgId;
    private String instanceId;
    private int timestamp;

    public MetricType getMetricType() {
        return metricType;
    }

    public void setMetricType(MetricType metricType) {
        this.metricType = metricType;
    }

    public enum MetricType {
        LATENCY, SUM
    }

    private MetricType metricType;
    public enum Name {
        // Runtime metrics
        RT_KAFKA_RECORD_COUNT("Kafka Records Count", "Number of records processed by runtime module"),
        RT_KAFKA_RECORD_SIZE("Kafka Records Size", "Total size of records processed by runtime module"),
        RT_KAFKA_LATENCY("Runtime Processing Latency", "Time taken to process records in runtime module"),
        KAFKA_RECORDS_LAG_MAX("Kafka Records Lag", "Maximum lag in processing Kafka records"),
        KAFKA_RECORDS_CONSUMED_RATE("Kafka Consumption Rate", "Rate at which Kafka records are being consumed"),
        KAFKA_FETCH_AVG_LATENCY("Kafka Fetch Latency", "Average time taken to fetch records from Kafka"),
        KAFKA_BYTES_CONSUMED_RATE("Kafka Bytes Consumption Rate", "Rate at which bytes are being consumed from Kafka"),
        CYBORG_NEW_API_COUNT("New APIs Detected", "Count of newly discovered APIs by Cyborg"),
        CYBORG_TOTAL_API_COUNT("Total APIs", "Total number of APIs tracked by Cyborg"),
        DELTA_CATALOG_TOTAL_COUNT("Total Catalog Items", "Total number of items in the delta catalog"),
        DELTA_CATALOG_NEW_COUNT("New Catalog Items", "Number of new items added to the delta catalog"),
        CYBORG_API_PAYLOAD_SIZE("API Payload Size", "Total size of API payloads processed by Cyborg"),

        // Postgres metrics
        PG_SAMPLE_DATA_INSERT_COUNT("Sample Data Insert Count", "Number of sample data records inserted into Postgres"),
        PG_SAMPLE_DATA_INSERT_LATENCY("Sample Data Insert Latency", "Time taken to insert sample data into Postgres"),
        MERGING_JOB_LATENCY("Merging Job Latency", "Time taken to complete the merging job"),
        MERGING_JOB_URLS_UPDATED_COUNT("Updated URLs Count", "Number of URLs updated during merging"),
        STALE_SAMPLE_DATA_CLEANUP_JOB_LATENCY("Cleanup Job Latency", "Time taken to clean up stale sample data"),
        STALE_SAMPLE_DATA_DELETED_COUNT("Deleted Stale Data Count", "Number of stale sample data records deleted"),
        MERGING_JOB_URL_UPDATE_LATENCY("URL Update Latency", "Time taken to update URLs during merging"),
        TOTAL_SAMPLE_DATA_COUNT("Total Sample Data", "Total number of sample data records in Postgres"),
        PG_DATA_SIZE_IN_MB("Postgres Data Size", "Total size of data in Postgres in megabytes"),

        // Testing metrics
        TESTING_RUN_COUNT("Testing Run Count", "Number of test runs executed"),
        TESTING_RUN_LATENCY("Testing Run Latency", "Time taken to complete test runs"),
        SAMPLE_DATA_FETCH_LATENCY("Sample Data Fetch Latency", "Time taken to fetch sample data"),
        MULTIPLE_SAMPLE_DATA_FETCH_LATENCY("Multiple Sample Data Fetch Latency", "Time taken to fetch multiple sample data records"),

        // Cyborg metrics
        CYBORG_CALL_LATENCY("Cyborg Call Latency", "Time taken for Cyborg API calls"),
        CYBORG_CALL_COUNT("Cyborg Call Count", "Number of calls made to Cyborg"),
        CYBORG_DATA_SIZE("Cyborg Data Size", "Total size of data processed by Cyborg");

        private final String descriptionName;
        private final String description;
        private static final Name[] names = values();

        Name(String descriptionName, String description) {
            this.descriptionName = descriptionName;
            this.description = description;
        }

        public static Name[] getNames() {
            return names;
        }

        public String getDescription() {
            return this.description;
        }

        public String getDescriptionName() {
            return this.descriptionName;
        }
    }

    public MetricData() {
    }

    public MetricData(String metricId, float value, String orgId, String instanceId, MetricType metricType) {
        this.metricId = metricId;
        this.value = value;
        this.orgId = orgId;
        this.instanceId = instanceId;
        this.timestamp = Context.now();
        this.metricType = metricType;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getMetricId() {
        return metricId;
    }

    public void setMetricId(String metricId) {
        this.metricId = metricId;
    }

    public float getValue() {
        return value;
    }

    public void setValue(float value) {
        this.value = value;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }
} 