package com.akto.utils;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.action.DbAction;
import com.akto.dao.context.Context;
import com.akto.dto.bulk_updates.BulkUpdates;
import com.akto.kafka.Kafka;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;

import java.util.concurrent.ConcurrentHashMap;

public class KafkaUtils {
    // Local cache for environment variables
    private static final Map<String, String> envCache = new ConcurrentHashMap<>();
    private static String getCachedEnv(String key) {
        return envCache.computeIfAbsent(key, System::getenv);
    }
    // Cached set of account IDs for topic routing
    final private static Set<String> accountIdSet = getAccountIdSetFromEnv();

    private static Set<String> getAccountIdSetFromEnv() {
        String accountIdsEnv = getCachedEnv("AKTO_KAFKA_ACCOUNT_ID");
        if (accountIdsEnv != null && !accountIdsEnv.isEmpty()) {
            String[] parts = accountIdsEnv.split(",");
            Set<String> accountIdSet = new HashSet<>();
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    accountIdSet.add(trimmed);
                }
            }
            return accountIdSet;
        }
        return Collections.emptySet();
    }
    
    private final static ObjectMapper mapper = new ObjectMapper();
    private static final Gson gson = new Gson();
    private static final LoggerMaker loggerMaker = new LoggerMaker(KafkaUtils.class, LogDb.DB_ABS);
    private static final Logger logger = LoggerFactory.getLogger(KafkaUtils.class);
    private Consumer<String, String> consumer;
    private static Kafka kafkaProducer;
    long lastSyncOffset = 0;

    public static void main(String[] args) throws Exception {
        try {
            KafkaUtils kafkaUtils = new KafkaUtils();

            // Start Kafka producer if write enabled
            if (kafkaUtils.isWriteEnabled()) {
                kafkaUtils.initKafkaProducer();
            }

            // Start fast-discovery consumer if enabled
            if (kafkaUtils.isFastDiscoveryEnabled()) {
                String brokerUrl = getBrokerUrl();
                String topicName = getFastDiscoveryTopicName();
                String groupId = "fast-discovery-consumer";
                int maxPollRecords = Integer.parseInt(getCachedEnv("AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG"));

                com.akto.FastDiscoveryKafkaConsumer fastDiscoveryConsumer =
                    new com.akto.FastDiscoveryKafkaConsumer(
                        brokerUrl,
                        topicName,
                        groupId,
                        maxPollRecords
                    );
                fastDiscoveryConsumer.start();
                loggerMaker.infoAndAddToDb("Fast-discovery consumer started on topic: " + topicName, LogDb.DB_ABS);
            }

            // Start main consumer if read enabled (this blocks)
            if (kafkaUtils.isReadEnabled()) {
                kafkaUtils.initKafkaConsumer();
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in main: " + e.toString(), LogDb.DB_ABS);
        }

    }

    public static String getReadTopicName() {
        try {
            String accountIdStr = getCachedEnv("AKTO_KAFKA_ACCOUNT_ID");
            if (accountIdStr != null && !accountIdStr.isEmpty()) {
                int accountId = Integer.parseInt(accountIdStr);
                return getTopicNameForAccount("AKTO_KAFKA_TOPIC_NAME", accountId);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in getReadTopicName: "+ e.toString());
        }
        return getCachedEnv("AKTO_KAFKA_TOPIC_NAME");
    }

    public void initKafkaConsumer() {
        System.out.println("kafka init consumer called");
        String topicName = getReadTopicName();
        String kafkaBrokerUrl = getCachedEnv("AKTO_KAFKA_BROKER_URL"); // kafka1:19092
        String isKubernetes = getCachedEnv("IS_KUBERNETES");
        if (isKubernetes != null && isKubernetes.equalsIgnoreCase("true")) {
            kafkaBrokerUrl = "127.0.0.1:29092";
        }
        String groupIdConfig =  getCachedEnv("AKTO_KAFKA_GROUP_ID_CONFIG");
        int maxPollRecordsConfig = Integer.parseInt(getCachedEnv("AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG"));

        Properties properties = configProperties(kafkaBrokerUrl, groupIdConfig, maxPollRecordsConfig);
        this.consumer = new KafkaConsumer<>(properties);
        final Thread mainThread = Thread.currentThread();
        final AtomicBoolean exceptionOnCommitSync = new AtomicBoolean(false);


        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                consumer.wakeup();
                try {
                    if (!exceptionOnCommitSync.get()) {
                        mainThread.join();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (Error e){
                    loggerMaker.errorAndAddToDb("Error in add shut down hook: "+ e.toString());
                }
            }
        });


        try {
            this.consumer.subscribe(Arrays.asList(topicName));
            loggerMaker.infoAndAddToDb("Kafka Consumer subscribed");
            while (true) {
                ConsumerRecords<String, String> records = this.consumer.poll(Duration.ofMillis(10000));
                try {
                    this.consumer.commitSync();
                } catch (Exception e) {
                    throw e;
                }
                
                for (ConsumerRecord<String,String> r: records) {
                    try {                         
                        lastSyncOffset++;
                        if (lastSyncOffset % 100 == 0) {
                            logger.info("Committing offset at position: " + lastSyncOffset);
                        }

                        parseAndTriggerWrites(r.value());
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e, "Error in parseAndTriggerWrites " + e);
                        continue;
                    }
                }
            }
        } catch (WakeupException ignored) {
          // nothing to catch. This exception is called from the shutdown hook.
        } catch (Exception e) {
            exceptionOnCommitSync.set(true);
            loggerMaker.errorAndAddToDb("Exception in init kafka consumer  " + e.toString());
            e.printStackTrace();
            System.exit(0);
        } finally {
            this.consumer.close();
        }
    }

    public void initKafkaProducer() {
        String kafkaBrokerUrl = getCachedEnv("AKTO_KAFKA_BROKER_URL");
        int batchSize = Integer.parseInt(getCachedEnv("AKTO_KAFKA_PRODUCER_BATCH_SIZE"));
        int kafkaLingerMS = Integer.parseInt(getCachedEnv("AKTO_KAFKA_PRODUCER_LINGER_MS"));
        kafkaProducer = new Kafka(kafkaBrokerUrl, kafkaLingerMS, batchSize);
        logger.info("Kafka Producer Init " + Context.now());
    }


    private static void parseAndTriggerWrites(String message) throws Exception {
        DbAction dbAction = new DbAction();
        Map<String, Object> json = gson.fromJson(message, Map.class);
        String triggerMethod = (String) json.get("triggerMethod");
        String payload = (String) json.get("payload");
        Double accIdDouble = (Double) json.get("accountId");
        int accountId = accIdDouble.intValue();
        Context.accountId.set(accountId);
        List<BulkUpdates> bulkWrites = mapper.readValue(payload, new TypeReference<List<BulkUpdates>>(){});

        // logger.info("Account id: " + accountId + " trigger method: " + triggerMethod);
        // logger.info(" bulkWrites: \n");
        // for(BulkUpdates update: bulkWrites){
        //     logger.info(update.getFilters().toString() + " " + update.getUpdates().toString());
        // }
        // logger.info("\n");

        switch (triggerMethod) {
            case "bulkWriteSti":
                dbAction.setWritesForSti(bulkWrites);
                dbAction.bulkWriteSti();
                break;
            
            case "bulkWriteSampleData":
                dbAction.setWritesForSampleData(bulkWrites);
                dbAction.bulkWriteSampleData();
                break;

            case "bulkWriteSensitiveSampleData":
                dbAction.setWritesForSensitiveSampleData(bulkWrites);
                dbAction.bulkWriteSensitiveSampleData();
                break;
                
            case "bulkWriteSensitiveParamInfo":
                dbAction.setWritesForSensitiveParamInfo(bulkWrites);
                dbAction.bulkWriteSensitiveParamInfo();
                break;

            case "bulkWriteTrafficInfo":
                dbAction.setWritesForTrafficInfo(bulkWrites);
                dbAction.bulkWriteTrafficInfo();
                break;
            
            case "bulkWriteTrafficMetrics":
                dbAction.setWritesForTrafficMetrics(bulkWrites);
                dbAction.bulkWriteTrafficMetrics();
                break;
            case "bulkWriteTestingRunIssues":
                dbAction.setWritesForTestingRunIssues(bulkWrites);
                dbAction.bulkWriteTestingRunIssues();
                break;

            case "bulkWriteSuspectSampleData":
                dbAction.setWritesForSuspectSampleData(bulkWrites);
                dbAction.bulkWriteSuspectSampleData();
                break;

            default:
                break;
        }
    }

    public static Properties configProperties(String kafkaBrokerUrl, String groupIdConfig, int maxPollRecordsConfig) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBrokerUrl);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecordsConfig);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupIdConfig);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return properties;
    }

    public boolean isReadEnabled() {
        String readEnabled = getCachedEnv("KAFKA_READ_ENABLED");
        if (readEnabled == null) {
            return false;
        }
        return readEnabled.equalsIgnoreCase("true");
    }

    public boolean isWriteEnabled() {
        String writeEnabled = getCachedEnv("KAFKA_WRITE_ENABLED");
        if (writeEnabled == null) {
            return false;
        }
        return writeEnabled.equalsIgnoreCase("true");
    }

    /**
     * Check if fast-discovery Kafka topic is enabled.
     * Used by FastDiscoveryKafkaConsumer to determine if it should start.
     */
    public boolean isFastDiscoveryEnabled() {
        String fastDiscoveryEnabled = getCachedEnv("FAST_DISCOVERY_KAFKA_ENABLED");
        if (fastDiscoveryEnabled == null) {
            return false;  // Disabled by default
        }
        return fastDiscoveryEnabled.equalsIgnoreCase("true");
    }

    /**
     * Get the fast-discovery Kafka topic name.
     * Default: akto.fast-discovery.writes
     */
    public static String getFastDiscoveryTopicName() {
        String topicName = getCachedEnv("FAST_DISCOVERY_KAFKA_TOPIC_NAME");
        if (topicName == null || topicName.isEmpty()) {
            return "akto.fast-discovery.writes";  // Default topic name
        }
        return topicName;
    }

    /**
     * Get broker URL for Kafka connections.
     */
    public static String getBrokerUrl() {
        String kafkaBrokerUrl = getCachedEnv("AKTO_KAFKA_BROKER_URL");
        String isKubernetes = getCachedEnv("IS_KUBERNETES");
        if (isKubernetes != null && isKubernetes.equalsIgnoreCase("true")) {
            return "127.0.0.1:29092";
        }
        return kafkaBrokerUrl != null ? kafkaBrokerUrl : "localhost:9092";
    }

    /**
     * Insert data to fast-discovery Kafka topic.
     * Used by fastDiscoveryBulkWriteSti and fastDiscoveryBulkWriteApiInfo endpoints.
     */
    public void insertFastDiscoveryData(Object data, String triggerMethod, int accountId) {
        try {
            String topicName = getFastDiscoveryTopicName();
            String payloadStr = gson.toJson(data);
            BasicDBObject obj = new BasicDBObject();
            obj.put("triggerMethod", triggerMethod);
            obj.put("payload", payloadStr);
            obj.put("accountId", accountId);
            obj.put("source", "fast-discovery");  // Mark source for identification

            kafkaProducer.send(obj.toString(), topicName);
            loggerMaker.infoAndAddToDb("Sent fast-discovery data to topic: " + topicName, LogDb.DB_ABS);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in insertFastDiscoveryData: " + e.toString(), LogDb.DB_ABS);
        }
    }


    public static String getTopicNameForAccount(String defaultTopicEnvVar, int accountId) {
        String defaultTopic = getCachedEnv(defaultTopicEnvVar);
        return defaultTopic + "_" + accountId;
    }

    public void insertData(List<BulkUpdates> writes, String triggerMethod, int accountId) {
        try {
            if (accountIdSet != null && accountIdSet.contains(String.valueOf(accountId))) {
                String topicName = getTopicNameForAccount("AKTO_KAFKA_TOPIC_NAME", accountId);
                insertDataCore(writes, triggerMethod, accountId, "", topicName, "kafka insertData (custom topic)");
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in insertData: "+ e.toString());
        }

        insertDataCore(writes, triggerMethod, accountId, "AKTO_KAFKA_TOPIC_NAME", null, "kafka insertData");
    }

    public void insertDataSecondary(Object writes, String triggerMethod, int accountId) {
        String skipSecondaryData = getCachedEnv("SKIP_SECONDARY_DATA");
        if (skipSecondaryData != null && skipSecondaryData.equalsIgnoreCase("true")) {
            return;
        }
        insertDataCore(writes, triggerMethod, accountId, "AKTO_KAFKA_TOPIC_NAME_SECONDARY", "akto.secondary.trafficdata", "kafka insertDataSecondary");
    }

    /*
     * By default, traffic Metrics and traffic Info are sent to the same kafka topic.
     * In case of high load, we send them to a different topic
     * and add a separate consumer for the topic.
     */
    public void insertDataTraffic(List<BulkUpdates> writes, String triggerMethod, int accountId) {
        return;
        // insertDataCore(writes, triggerMethod, accountId, "AKTO_KAFKA_TOPIC_NAME_TRAFFIC", "akto.trafficdata", "kafka insertDataTraffic");
    }

    public void insertDataCore(Object writes, String triggerMethod, int accountId, String topicEnvVar, String defaultTopic, String errorContext) {
        try {
            // Retrieve topic name from local cache or use default if specified
            String topicName = getCachedEnv(topicEnvVar);
            if (topicName == null) {
                if (defaultTopic != null) {
                    topicName = defaultTopic;
                } else {
                    throw new Exception(topicEnvVar + " is null and no default topic provided");
                }
            }
            String payloadStr = gson.toJson(writes);
            BasicDBObject obj = new BasicDBObject();
            obj.put("triggerMethod", triggerMethod);
            obj.put("payload", payloadStr);
            obj.put("accountId", accountId);
            kafkaProducer.send(obj.toString(), topicName);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in " + errorContext + " " + e.toString());
        }
    }
}