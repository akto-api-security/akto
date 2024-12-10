package com.akto.threat.protection.utils;

import java.time.Duration;
import java.util.ArrayList;
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

import com.akto.dao.context.Context;
import com.akto.kafka.Kafka;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.threat.protection.db.AggregateSampleMaliciousEventModel;
import com.akto.threat.protection.db.MaliciousEventModel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.WriteModel;

public class KafkaUtils {

  private static Kafka kafkaProducer;
  private static Consumer<String, String> consumer;
  private static final Logger logger = LoggerFactory.getLogger(KafkaUtils.class);
  private static final LoggerMaker loggerMaker = new LoggerMaker(KafkaUtils.class);
  private static final Gson gson = new Gson();
  private static MongoClient mClient;
  private static final ObjectMapper mapper = new ObjectMapper();
  private static long lastSyncOffset = 0;

  public static void initKafkaProducer() {
    String kafkaBrokerUrl = System.getenv("THREAT_EVENTS_KAFKA_BROKER_URL");
    int batchSize =
        Integer.parseInt(System.getenv().getOrDefault("AKTO_KAFKA_PRODUCER_BATCH_SIZE", "100"));
    int kafkaLingerMS =
        Integer.parseInt(System.getenv().getOrDefault("AKTO_KAFKA_PRODUCER_LINGER_MS", "1000"));
    kafkaProducer = new Kafka(kafkaBrokerUrl, kafkaLingerMS, batchSize);
    logger.info("Kafka Producer Init " + Context.now());
  }

  public static void insertData(Object writes, String eventType, int accountId) {
    String topicName =
        System.getenv()
            .getOrDefault("THREAT_EVENTS_KAFKA_TOPIC", "akto.threat_protection.internal_events");
    BasicDBObject obj = new BasicDBObject();
    obj.put("eventType", eventType);
    String payloadStr = gson.toJson(writes);
    obj.put("payload", payloadStr);
    obj.put("accountId", accountId);
    kafkaProducer.send(obj.toString(), topicName);
  }

  public static void initMongoClient(MongoClient mongoClient) {
    mClient = mongoClient;
  }

  public static void initKafkaConsumer() {
    System.out.println("Kafka Init consumer called");
    String topicName =
        System.getenv()
            .getOrDefault("THREAT_EVENTS_KAFKA_TOPIC", "akto.threat_protection.internal_events");
    String kafkaBrokerUrl = System.getenv("THREAT_EVENTS_KAFKA_BROKER_URL"); // kafka1:19092
    String isKubernetes = System.getenv().getOrDefault("IS_KUBERNETES", "false");
    if (isKubernetes != null && isKubernetes.equalsIgnoreCase("true")) {
      kafkaBrokerUrl = "127.0.0.1:29092";
    }
    String groupIdConfig = System.getenv("THREAT_EVENTS_KAFKA_GROUP_ID_CONFIG");
    int maxPollRecordsConfig =
        Integer.parseInt(
            System.getenv().getOrDefault("THREAT_EVENTS_KAFKA_MAX_POLL_RECORDS_CONFIG", "100"));

    Properties properties = configProperties(kafkaBrokerUrl, groupIdConfig, maxPollRecordsConfig);
    consumer = new KafkaConsumer<>(properties);
    final Thread mainThread = Thread.currentThread();
    final AtomicBoolean exceptionOnCommitSync = new AtomicBoolean(false);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread() {
              public void run() {
                consumer.wakeup();
                try {
                  if (!exceptionOnCommitSync.get()) {
                    mainThread.join();
                  }
                } catch (InterruptedException e) {
                  e.printStackTrace();
                } catch (Error e) {
                  loggerMaker.errorAndAddToDb(
                      "Error in add shut down hook: " + e.getMessage(), LogDb.DASHBOARD);
                }
              }
            });

    try {
      consumer.subscribe(Arrays.asList(topicName));
      loggerMaker.infoAndAddToDb("Kafka Consumer subscribed", LogDb.DASHBOARD);
      while (true) {
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(10000));
        try {
          consumer.commitSync();
        } catch (Exception e) {
          throw e;
        }

        for (ConsumerRecord<String, String> r : records) {
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
      loggerMaker.errorAndAddToDb(
          "Exception in init kafka consumer  " + e.getMessage(), LogDb.DASHBOARD);
      e.printStackTrace();
      System.exit(0);
    } finally {
      consumer.close();
    }
  }

  private static void parseAndTriggerWrites(String message) throws Exception {
    Map<String, Object> json = gson.fromJson(message, Map.class);
    String eventType = (String) json.get("eventType");
    String payload = (String) json.get("payload");
    Double accIdDouble = (Double) json.get("accountId");
    int accountId = accIdDouble.intValue();
    Context.accountId.set(accountId);

    switch (eventType) {
      case "maliciousEvents":
        List<WriteModel<AggregateSampleMaliciousEventModel>> bulkUpdates = new ArrayList<>();
        List<AggregateSampleMaliciousEventModel> events =
            mapper.readValue(
                payload, new TypeReference<List<AggregateSampleMaliciousEventModel>>() {});
        events.forEach(
            event -> {
              bulkUpdates.add(new InsertOneModel<>(event));
            });

        mClient
            .getDatabase(accountId + "")
            .getCollection("malicious_events", AggregateSampleMaliciousEventModel.class)
            .bulkWrite(bulkUpdates, new BulkWriteOptions().ordered(false));
        break;

      case "smartEvent":
        MaliciousEventModel event =
            mapper.readValue(payload, new TypeReference<MaliciousEventModel>() {});
        mClient
            .getDatabase(accountId + "")
            .getCollection("smart_events", MaliciousEventModel.class)
            .insertOne(event);
        break;
      default:
        break;
    }
  }

  public static Properties configProperties(
      String kafkaBrokerUrl, String groupIdConfig, int maxPollRecordsConfig) {
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
}
