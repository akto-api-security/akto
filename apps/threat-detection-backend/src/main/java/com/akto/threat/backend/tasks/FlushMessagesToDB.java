package com.akto.threat.backend.tasks;

import com.akto.dao.context.Context;
import com.akto.dto.threat_detection_backend.MaliciousEventDto;
import com.akto.kafka.KafkaConfig;
import com.akto.log.LoggerMaker;
import com.akto.threat.backend.cache.IgnoredEventCache;
import com.akto.threat.backend.constants.KafkaTopic;
import com.akto.threat.backend.constants.MongoDBCollection;
import com.akto.threat.backend.dao.MaliciousEventDao;
import com.akto.threat.backend.dao.ActorInfoDao;
import com.akto.threat.backend.db.AggregateSampleMaliciousEventModel;
import com.akto.threat.backend.db.SplunkIntegrationModel;
import com.akto.threat.backend.service.MaliciousEventService;
import com.akto.threat.backend.utils.SplunkEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.UpdateOptions;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.bson.Document;
import org.bson.conversions.Bson;

public class FlushMessagesToDB {

  private final KafkaConsumer<String, String> kafkaConsumer;
  private final KafkaConfig kafkaConfig;
  private final MongoClient mClient;

  private static final ObjectMapper mapper = new ObjectMapper();
  private static final Gson gson = new Gson();
  ExecutorService executorService = Executors.newFixedThreadPool(3);
  private static int lastSplunkConfigFetched = 0;
  SplunkIntegrationModel splunkConfig = null;
  private static final LoggerMaker logger = new LoggerMaker(MaliciousEventService.class);

  private static final boolean USE_ACTOR_INFO_TABLE = Boolean.parseBoolean(
      System.getenv().getOrDefault("USE_ACTOR_INFO_TABLE", "false")
  );

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

    // Set MongoClient for IgnoredEventCache
    IgnoredEventCache.setMongoClient(mongoClient);
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

    logger.debug("inserting malicious event in db for accountId " + accountId + " eventType " + eventType);

    switch (eventType) {
      case MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS:
        MaliciousEventDto event =
            mapper.readValue(payload, new TypeReference<MaliciousEventDto>() {});
        
        if (event == null){
          break;
        }
        
        // Check cache for ignored URL+filter combination (cache will auto-refresh from DB if needed)
        if (IgnoredEventCache.isIgnoredInCache(accountId, event.getLatestApiEndpoint(), event.getFilterId())) {
            logger.debug("Skipping insertion of malicious event due to ignored status for: " +
                       event.getLatestApiEndpoint() + " + " + event.getFilterId());
        } else {
            // No ignored event exists, safe to insert
            MaliciousEventDao.instance.insertOne(accountId, event);

            // Upsert actor_info table for optimized listThreatActors queries
            upsertActorInfo(accountId, event);
        }
        break;
      default:
        throw new IllegalArgumentException("Invalid event type");
    }
  }

  private void sendEventToSplunk(AggregateSampleMaliciousEventModel event, String accountId) {

    if (Context.now() - lastSplunkConfigFetched > 30 * 60) {
      lastSplunkConfigFetched = Context.now();
      MongoCollection<Document> coll =
            this.mClient
                .getDatabase(accountId)
                .getCollection(MongoDBCollection.ThreatDetection.SPLUNK_INTEGRATION_CONFIG, Document.class);

        int accId = Integer.parseInt(accountId);
        Bson filters = Filters.eq("accountId", accId);
        Document doc = coll.find(filters).cursor().next();
        splunkConfig = SplunkIntegrationModel.newBuilder().setAccountId(accId).setSplunkToken(doc.getString("splunkToken")).setSplunkUrl(doc.getString("splunkUrl")).build();
    }

    if (splunkConfig == null) {
      return;
    }
    SplunkEvent.sendEvent(event, splunkConfig);

  }

  private void upsertActorInfo(String accountId, MaliciousEventDto event) {
    if (!USE_ACTOR_INFO_TABLE) {
      return;
    }

    try {
      String actor = event.getActor();
      if (actor == null || actor.isEmpty()) {
        return;
      }

      // Get contextSource, default to "API" if null or empty
      String contextSource = event.getContextSource();
      if (contextSource == null || contextSource.isEmpty()) {
        contextSource = "API";
      }

      // Use compound filter (actorId, contextSource) for upsert
      // This allows same actor to have separate entries per context
      Bson filter = Filters.and(
          Filters.eq("actorId", actor),
          Filters.eq("contextSource", contextSource)
      );

      // Check if this event is critical (HIGH or CRITICAL severity)
      String severity = event.getSeverity();
      boolean isCriticalEvent = "CRITICAL".equalsIgnoreCase(severity) || "HIGH".equalsIgnoreCase(severity);

      // Build update document - update latest attack details
      // Use $max for lastAttackTs to only update if new event is newer
      // Use $min for discoveredAt to keep the earliest timestamp
      java.util.List<Bson> updatesList = new java.util.ArrayList<>();
      updatesList.add(Updates.set("filterId", event.getFilterId()));
      updatesList.add(Updates.set("category", event.getCategory()));
      updatesList.add(Updates.set("apiCollectionId", event.getLatestApiCollectionId()));
      updatesList.add(Updates.set("url", event.getLatestApiEndpoint()));
      updatesList.add(Updates.set("method", event.getLatestApiMethod() != null ? event.getLatestApiMethod().name() : ""));
      updatesList.add(Updates.set("country", event.getCountry() != null ? event.getCountry() : ""));
      updatesList.add(Updates.set("severity", severity != null ? severity : ""));
      updatesList.add(Updates.set("host", event.getHost() != null ? event.getHost() : ""));
      updatesList.add(Updates.set("latestMetadata", event.getMetadata() != null ? event.getMetadata() : ""));
      updatesList.add(Updates.max("lastAttackTs", event.getDetectedAt()));
      updatesList.add(Updates.min("discoveredAt", event.getDetectedAt()));
      updatesList.add(Updates.set("updatedAt", Context.now()));
      updatesList.add(Updates.setOnInsert("actorId", actor));
      updatesList.add(Updates.setOnInsert("contextSource", contextSource));  // Set on insert
      updatesList.add(Updates.setOnInsert("status", "ACTIVE"));
      updatesList.add(Updates.inc("totalAttacks", 1));

      // Only set isCritical to true if this event is HIGH/CRITICAL
      // Never set to false - missing/null field is treated as false when reading
      // Once set to true, stays true forever
      if (isCriticalEvent) {
        updatesList.add(Updates.set("isCritical", true));
      }

      Bson updates = Updates.combine(updatesList);

      UpdateOptions options = new UpdateOptions().upsert(true);

      ActorInfoDao.instance.getCollection(accountId).updateOne(filter, updates, options);

      logger.debug("Upserted actor_info for actor: " + actor);
    } catch (Exception e) {
      logger.error("Error upserting actor_info: " + e.getMessage(), e);
      // Don't throw - actor_info is supplementary, shouldn't block malicious event insertion
    }
  }
}
