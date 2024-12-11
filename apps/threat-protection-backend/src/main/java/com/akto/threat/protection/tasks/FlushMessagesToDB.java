package com.akto.threat.protection.tasks;

import com.akto.kafka.KafkaConfig;
import com.akto.runtime.utils.Utils;
import com.akto.threat.protection.db.AggregateSampleMaliciousEventModel;
import com.akto.threat.protection.db.MaliciousEventModel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.WriteModel;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FlushMessagesToDB {

  private final KafkaConsumer<String, String> kafkaConsumer;
  private final String kafkaTopic;
  private final KafkaConfig kafkaConfig;
  private final MongoClient mClient;

  private static final ObjectMapper mapper = new ObjectMapper();
  private static final Gson gson = new Gson();

  public FlushMessagesToDB(KafkaConfig kafkaConfig, String kafkaTopic, MongoClient mongoClient) {
    String kafkaBrokerUrl = kafkaConfig.getBootstrapServers();
    String groupId = kafkaConfig.getGroupId();

    Properties properties =
        Utils.configProperties(
            kafkaBrokerUrl, groupId, kafkaConfig.getConsumerConfig().getMaxPollRecords());
    this.kafkaConsumer = new KafkaConsumer<>(properties);
    this.kafkaConfig = kafkaConfig;

    this.kafkaTopic = kafkaTopic;

    this.mClient = mongoClient;
  }

  private static ExecutorService getPollingExecutor() {
    return Executors.newSingleThreadExecutor();
  }

  public void run() {
    this.kafkaConsumer.subscribe(Collections.singletonList(this.kafkaTopic));

    getPollingExecutor()
        .execute(
            () -> {
              // Poll data from Kafka topic
              while (true) {
                ConsumerRecords<String, String> records =
                    kafkaConsumer.poll(
                        Duration.ofMillis(
                            this.kafkaConfig.getConsumerConfig().getPollDurationMilli()));
                if (records.isEmpty()) {
                  continue;
                }

                processRecords(records);
              }
            });
  }

  private void processRecords(ConsumerRecords<String, String> records) {
    records.forEach(
        r -> {
          String message = r.value();
          try {
            writeMessage(message);
          } catch (JsonProcessingException e) {
            System.out.println("Error while parsing message" + e);
          } catch (Exception ex) {
            ex.printStackTrace();
          }
        });
  }

  private void writeMessage(String message) throws JsonProcessingException {
    Map<String, Object> json = gson.fromJson(message, Map.class);
    String eventType = (String) json.get("eventType");
    String payload = (String) json.get("payload");
    Double accIdDouble = (Double) json.get("accountId");
    int accountId = accIdDouble.intValue();

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

        this.mClient
            .getDatabase(accountId + "")
            .getCollection("malicious_events", AggregateSampleMaliciousEventModel.class)
            .bulkWrite(bulkUpdates, new BulkWriteOptions().ordered(false));
        break;

      case "smartEvent":
        MaliciousEventModel event =
            mapper.readValue(payload, new TypeReference<MaliciousEventModel>() {});
        this.mClient
            .getDatabase(accountId + "")
            .getCollection("smart_events", MaliciousEventModel.class)
            .insertOne(event);
        break;
      default:
        throw new IllegalArgumentException("Invalid event type");
    }
  }
}
