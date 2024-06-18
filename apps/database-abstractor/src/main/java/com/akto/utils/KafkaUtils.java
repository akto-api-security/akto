package com.akto.utils;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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

public class KafkaUtils {
    
    private final static ObjectMapper mapper = new ObjectMapper();
    private static final Gson gson = new Gson();
    private static final LoggerMaker loggerMaker = new LoggerMaker(KafkaUtils.class);
    private static final Logger logger = LoggerFactory.getLogger(KafkaUtils.class);
    private Consumer<String, String> consumer;
    private static Kafka kafkaProducer;
    long lastSyncOffset = 0;

    public static void main(String[] args) throws Exception {
        try {
            KafkaUtils kafkaUtils = new KafkaUtils();
            if (kafkaUtils.isReadEnabled()) {
                kafkaUtils.initKafkaConsumer();
            }

            if (kafkaUtils.isWriteEnabled()) {
                kafkaUtils.initKafkaProducer();
            }
        } catch (Exception e) {
            // TODO: handle exception
        }
        
    }

    public void initKafkaConsumer() {
        System.out.println("kafka init consumer called");
        String topicName = System.getenv("AKTO_KAFKA_TOPIC_NAME");
        String kafkaBrokerUrl = System.getenv("AKTO_KAFKA_BROKER_URL"); // kafka1:19092
        String isKubernetes = System.getenv("IS_KUBERNETES");
        if (isKubernetes != null && isKubernetes.equalsIgnoreCase("true")) {
            kafkaBrokerUrl = "127.0.0.1:29092";
        }
        String groupIdConfig =  System.getenv("AKTO_KAFKA_GROUP_ID_CONFIG");
        int maxPollRecordsConfig = Integer.parseInt(System.getenv("AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG"));

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
                    loggerMaker.errorAndAddToDb("Error in add shut down hook: "+ e.getMessage(), LogDb.DASHBOARD);
                }
            }
        });


        try {
            this.consumer.subscribe(Arrays.asList(topicName));
            loggerMaker.infoAndAddToDb("Kafka Consumer subscribed", LogDb.DASHBOARD);
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
                        loggerMaker.errorAndAddToDb(e, "Error in parseAndTriggerWrites " + e, LogDb.DASHBOARD);
                        continue;
                    }
                }
            }
        } catch (WakeupException ignored) {
          // nothing to catch. This exception is called from the shutdown hook.
        } catch (Exception e) {
            exceptionOnCommitSync.set(true);
            loggerMaker.errorAndAddToDb("Exception in init kafka consumer  " + e.getMessage(),LogDb.DASHBOARD);
            e.printStackTrace();
            System.exit(0);
        } finally {
            this.consumer.close();
        }

    }

    public void initKafkaProducer() {
        String kafkaBrokerUrl = System.getenv("AKTO_KAFKA_BROKER_URL");
        int batchSize = Integer.parseInt(System.getenv("AKTO_KAFKA_PRODUCER_BATCH_SIZE"));
        int kafkaLingerMS = Integer.parseInt(System.getenv("AKTO_KAFKA_PRODUCER_LINGER_MS"));
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
        if (System.getenv("KAFKA_READ_ENABLED") == null) {
            return false;
        }
        return System.getenv("KAFKA_READ_ENABLED").equalsIgnoreCase("true");
    }

    public boolean isWriteEnabled() {
        if (System.getenv("KAFKA_WRITE_ENABLED") == null) {
            return false;
        }
        return System.getenv("KAFKA_WRITE_ENABLED").equalsIgnoreCase("true");
    }

    public void insertData(List<BulkUpdates> writes, String triggerMethod, int accountId) {
        String topicName = System.getenv("AKTO_KAFKA_TOPIC_NAME");
        BasicDBObject obj = new BasicDBObject();
        obj.put("triggerMethod", triggerMethod);
        String payloadStr = gson.toJson(writes);
        obj.put("payload", payloadStr);
        obj.put("accountId", accountId);
        kafkaProducer.send(obj.toString(), topicName);
    }

}
