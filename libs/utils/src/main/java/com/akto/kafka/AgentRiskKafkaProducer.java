package com.akto.kafka;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feature-flagged producer for the agent risk-score topic.
 * Created only when AGENT_RISK_SCORE_ENABLED is true. Produce failures never
 * throw: callers must keep writing traces regardless.
 */
public class AgentRiskKafkaProducer {

    private static final LoggerMaker loggerMaker = new LoggerMaker(AgentRiskKafkaProducer.class, LogDb.DB_ABS);
    private static final Gson gson = new Gson();
    private static final Map<String, String> envCache = new ConcurrentHashMap<>();

    private static volatile Kafka producer;
    private static volatile boolean initAttempted = false;

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

    public static boolean isRiskScoreEnabled() {
        return "true".equalsIgnoreCase(getCachedEnv("AGENT_RISK_SCORE_ENABLED"));
    }

    public static boolean isRiskConsumerEnabled() {
        return "true".equalsIgnoreCase(getCachedEnv("AGENT_RISK_KAFKA_CONSUMER_ENABLED"));
    }

    public static String getEmbedServiceUrl() {
        String v = getCachedEnv("AGENT_EMBED_SERVICE_URL");
        return (v == null || v.isEmpty()) ? null : v.replaceAll("/$", "");
    }

    public static String getRiskBrokerUrl() {
        String dedicated = getCachedEnv("AKTO_AGENT_RISK_KAFKA_BROKER_URL");
        if (dedicated != null && !dedicated.isEmpty()) {
            return dedicated;
        }
        String fallback = getCachedEnv("AKTO_KAFKA_BROKER_URL");
        return (fallback == null || fallback.isEmpty()) ? "localhost:9092" : fallback;
    }

    public static String getRiskTopicName() {
        return getEnvOrDefault("AGENT_RISK_KAFKA_TOPIC_NAME", "akto.agent.risk");
    }

    public static String getRiskConsumerGroupId() {
        return getEnvOrDefault("AGENT_RISK_KAFKA_CONSUMER_GROUP_ID", "agent-risk-consumer");
    }

    public static int getRiskMaxPollRecords() {
        try {
            return Integer.parseInt(getEnvOrDefault("AGENT_RISK_KAFKA_MAX_POLL_RECORDS_CONFIG", "200"));
        } catch (NumberFormatException e) {
            return 200;
        }
    }

    public static int getApiInfoDecaySecs() {
        try {
            return Integer.parseInt(getEnvOrDefault("AGENT_RISK_APIINFO_DECAY_SECS", "86400"));
        } catch (NumberFormatException e) {
            return 86400;
        }
    }

    public static double getKnnDistanceThreshold() {
        try {
            return Double.parseDouble(getEnvOrDefault("AGENT_RISK_KNN_DISTANCE_THRESHOLD", "0.15"));
        } catch (NumberFormatException e) {
            return 0.15d;
        }
    }

    public static int getFuzzyMaxChars() {
        try {
            return Integer.parseInt(getEnvOrDefault("AGENT_RISK_FUZZY_MAX_CHARS", "1000"));
        } catch (NumberFormatException e) {
            return 1000;
        }
    }

    public static int getHighRiskComposite() {
        try {
            return Integer.parseInt(getEnvOrDefault("AGENT_RISK_HIGH_RISK_COMPOSITE", "70"));
        } catch (NumberFormatException e) {
            return 70;
        }
    }

    public static synchronized void init() {
        if (producer != null || initAttempted) {
            return;
        }
        initAttempted = true;
        try {
            int batchSize = Integer.parseInt(getEnvOrDefault("AGENT_RISK_KAFKA_PRODUCER_BATCH_SIZE", "65536"));
            int lingerMs = Integer.parseInt(getEnvOrDefault("AGENT_RISK_KAFKA_PRODUCER_LINGER_MS", "50"));
            producer = new Kafka(getRiskBrokerUrl(), lingerMs, batchSize);
            loggerMaker.infoAndAddToDb("Agent risk Kafka producer initialized, ready=" + producer.producerReady, LogDb.DB_ABS);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error initializing agent risk kafka producer: " + e.getMessage(), LogDb.DB_ABS);
        }
    }

    /**
     * @return true if handed to Kafka; false if flag off / producer not ready / send failed.
     */
    public static boolean produce(List<?> records, int accountId) {
        if (!isRiskScoreEnabled()) {
            return false;
        }
        if (records == null || records.isEmpty()) {
            return true;
        }
        try {
            if (producer == null) {
                init();
            }
            if (producer == null || !producer.producerReady) {
                return false;
            }
            BasicDBObject envelope = new BasicDBObject();
            envelope.put("triggerMethod", "scoreAgentQueryRecords");
            envelope.put("payload", gson.toJson(records));
            envelope.put("accountId", accountId);
            producer.send(String.valueOf(accountId), envelope.toString(), getRiskTopicName());
            return true;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error producing agent risk jobs: " + e.getMessage(), LogDb.DB_ABS);
            return false;
        }
    }
}
