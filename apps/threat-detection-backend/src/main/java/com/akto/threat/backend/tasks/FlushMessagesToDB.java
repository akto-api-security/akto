package com.akto.threat.backend.tasks;

import com.akto.kafka.KafkaConfig;
import com.akto.runtime.utils.Utils;
import com.akto.threat.backend.constants.KafkaTopic;
import com.akto.threat.backend.constants.MongoDBCollection;
import com.akto.threat.backend.db.AggregateSampleMaliciousEventModel;
import com.akto.threat.backend.db.MaliciousEventModel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.WriteModel;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

public class FlushMessagesToDB {

  private final KafkaConsumer<String, String> kafkaConsumer;
  private final KafkaConfig kafkaConfig;
  private final MongoClient mClient;

  private static final ObjectMapper mapper = new ObjectMapper();
  private static final Gson gson = new Gson();

  public FlushMessagesToDB(KafkaConfig kafkaConfig, MongoClient mongoClient) {
    String kafkaBrokerUrl = kafkaConfig.getBootstrapServers();

    Properties properties = new Properties();
    properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBrokerUrl);
    properties.put(
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
        kafkaConfig.getKeySerializer().getDeserializer());
    properties.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
        kafkaConfig.getValueSerializer().getDeserializer());
    properties.put(
        ConsumerConfig.MAX_POLL_RECORDS_CONFIG,
        kafkaConfig.getConsumerConfig().getMaxPollRecords());
    properties.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaConfig.getGroupId());
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

    this.kafkaConsumer = new KafkaConsumer<>(properties);
    this.kafkaConfig = kafkaConfig;

    this.mClient = mongoClient;
  }

  public void run() {
    ExecutorService pollingExecutor = Executors.newSingleThreadExecutor();
    this.kafkaConsumer.subscribe(
        Collections.singletonList(KafkaTopic.ThreatDetection.INTERNAL_DB_MESSAGES));

    pollingExecutor.execute(
        () -> {
          // Poll data from Kafka topic
          while (true) {
            ConsumerRecords<String, String> records =
                kafkaConsumer.poll(
                    Duration.ofMillis(this.kafkaConfig.getConsumerConfig().getPollDurationMilli()));
            if (records.isEmpty()) {
              continue;
            }

            processRecords(records);

            if (!records.isEmpty()) {
              kafkaConsumer.commitSync();
            }
          }
        });
  }

  private void processRecords(ConsumerRecords<String, String> records) {
    records.forEach(
        r -> {
          try {
            String message = r.value();
            writeMessage(message);
          } catch (JsonProcessingException ex) {
            ex.printStackTrace();
          } catch (Exception ex) {
            ex.printStackTrace();
          }
        });
  }

  private void writeMessage(String message) throws JsonProcessingException {
    Map<String, Object> json = gson.fromJson(message, Map.class);
    String eventType = (String) json.get("eventType");
    String payload = (String) json.get("payload");
    String accountId = (String) json.get("accountId");

    switch (eventType) {
      case MongoDBCollection.ThreatDetection.AGGREGATE_SAMPLE_MALICIOUS_REQUESTS:
        List<WriteModel<AggregateSampleMaliciousEventModel>> bulkUpdates = new ArrayList<>();
        List<AggregateSampleMaliciousEventModel> events =
            mapper.readValue(
                payload, new TypeReference<List<AggregateSampleMaliciousEventModel>>() {});
        events.forEach(
            event -> {
              bulkUpdates.add(new InsertOneModel<>(event));
            });

        this.mClient
            .getDatabase(accountId + "")
            .getCollection(eventType, AggregateSampleMaliciousEventModel.class)
            .bulkWrite(bulkUpdates, new BulkWriteOptions().ordered(false));
        break;

      case MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS:
        MaliciousEventModel event =
            mapper.readValue(payload, new TypeReference<MaliciousEventModel>() {});
        this.mClient
            .getDatabase(accountId + "")
            .getCollection(eventType, MaliciousEventModel.class)
            .insertOne(event);
        break;
      default:
        throw new IllegalArgumentException("Invalid event type");
    }
  }
}
