package com.akto.kafka;

import com.akto.dto.metrics.MetricData;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.google.gson.Gson;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MetricsKafkaProducer {

    private static final LoggerMaker loggerMaker = new LoggerMaker(MetricsKafkaProducer.class, LogDb.DB_ABS);
    private static final Gson gson = new Gson();

    private static final Map<String, String> envCache = new ConcurrentHashMap<>();

    private static String getCachedEnv(String key) {
        return envCache.computeIfAbsent(key, k -> {
            String v = System.getenv(k);
            return v == null ? "" : v;
        });
    }

    private static String getEnvOrDefault(String key, String defaultValue) {
        String v = getCachedEnv(key);
        return (v == null || v.isEmpty()) ? defaultValue : v;
    }

    private static volatile Kafka producer;
    private static volatile boolean initAttempted = false;

    public static boolean isMetricsKafkaEnabled() {
        return "true".equalsIgnoreCase(getCachedEnv("METRICS_KAFKA_ENABLED"));
    }

    public static boolean isMetricsKafkaConsumerEnabled() {
        return "true".equalsIgnoreCase(getCachedEnv("METRICS_KAFKA_CONSUMER_ENABLED"));
    }


    public static String getMetricsBrokerUrl() {
        String metricsBroker = getCachedEnv("AKTO_METRICS_KAFKA_BROKER_URL");
        if (metricsBroker != null && !metricsBroker.isEmpty()) {
            return metricsBroker;
        }
        String fallback = getCachedEnv("AKTO_KAFKA_BROKER_URL");
        return (fallback == null || fallback.isEmpty()) ? "localhost:29092" : fallback;
    }

    public static String getMetricsTopicName() {
        return getEnvOrDefault("METRICS_KAFKA_TOPIC_NAME", "akto.metrics");
    }

    public static String getMetricsConsumerGroupId() {
        return getEnvOrDefault("METRICS_KAFKA_CONSUMER_GROUP_ID", "metrics-consumer");
    }

    public static int getMetricsMaxPollRecords() {
        try {
            return Integer.parseInt(getEnvOrDefault("METRICS_KAFKA_MAX_POLL_RECORDS_CONFIG", "2000"));
        } catch (NumberFormatException e) {
            return 500;
        }
    }

    public static synchronized void init() {
        if (producer != null || initAttempted) {
            return;
        }
        initAttempted = true;
        try {
            int batchSize = Integer.parseInt(getEnvOrDefault("METRICS_KAFKA_PRODUCER_BATCH_SIZE", "65536"));
            int lingerMs = Integer.parseInt(getEnvOrDefault("METRICS_KAFKA_PRODUCER_LINGER_MS", "100"));
            // Metrics are loss-tolerant: acks=1 + lz4 trade a little durability for much
            // higher throughput than the traffic producer, which leaves acks at cluster
            // default and no compression.
            producer = new Kafka(getMetricsBrokerUrl(), lingerMs, batchSize, "1", "lz4");
            loggerMaker.infoAndAddToDb("Metrics Kafka producer initialized, ready=" + producer.producerReady, LogDb.DB_ABS);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error initializing metrics kafka producer: " + e.getMessage(), LogDb.DB_ABS);
        }
    }

    private static boolean sendEnvelope(String triggerMethod, Object payload, int accountId, String moduleType) {
        if (!isMetricsKafkaEnabled()) {
            return false;
        }
        try {
            if (producer == null) {
                init();
            }
            if (producer == null || !producer.producerReady) {
                return false;
            }

            BasicDBObject envelope = new BasicDBObject();
            envelope.put("triggerMethod", triggerMethod);
            envelope.put("payload", gson.toJson(payload));
            envelope.put("accountId", accountId);
            envelope.put("moduleType", moduleType == null ? "" : moduleType);


            String partitionKey = accountId + ":" + (moduleType == null ? "" : moduleType);
            producer.send(partitionKey, envelope.toString(), getMetricsTopicName());
            return true;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error producing " + triggerMethod + " to metrics kafka topic: " + e.getMessage(), LogDb.DB_ABS);
            return false;
        }
    }


    public static boolean produceIngestMetricsData(List<MetricData> metricData, int accountId) {
        if (metricData == null || metricData.isEmpty()) {
            return true; // nothing to send; don't force a pointless sync fallback
        }
        String moduleType = null;
        for (MetricData m : metricData) {
            if (m != null && m.getModuleType() != null) {
                moduleType = m.getModuleType();
                break;
            }
        }
        return sendEnvelope("ingestMetricsData", metricData, accountId, moduleType);
    }


    public static boolean produceRuntimeMetricsData(BasicDBList metricsData, int accountId) {
        if (metricsData == null || metricsData.isEmpty()) {
            return true;
        }
        return sendEnvelope("insertRuntimeMetricsData", metricsData, accountId, "runtime");
    }
}
