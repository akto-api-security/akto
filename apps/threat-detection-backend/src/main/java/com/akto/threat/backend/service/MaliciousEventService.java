package com.akto.threat.backend.service;

import com.akto.dto.type.URLMethods;
import com.akto.kafka.Kafka;
import com.akto.kafka.KafkaConfig;
import com.akto.log.LoggerMaker;
import com.akto.proto.generated.threat_detection.message.malicious_event.event_type.v1.EventType;
import com.akto.proto.generated.threat_detection.message.malicious_event.v1.MaliciousEventMessage;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchAlertFiltersRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchAlertFiltersResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatActorFilterRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatActorFilterResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.TimeRangeFilter;
import com.akto.proto.generated.threat_detection.service.malicious_alert_service.v1.RecordMaliciousEventRequest;
import com.akto.threat.backend.constants.KafkaTopic;
import com.akto.threat.backend.constants.MongoDBCollection;
import com.akto.threat.backend.db.MaliciousEventModel;
import com.akto.threat.backend.utils.KafkaUtils;
import com.akto.threat.backend.utils.ThreatUtils;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;

import java.util.*;
import java.util.List;

import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;

public class MaliciousEventService {

  private final Kafka kafka;
  private final MongoClient mongoClient;
  private static final LoggerMaker logger = new LoggerMaker(MaliciousEventService.class);

  private static final HashMap<String, Boolean> shouldNotCreateIndexes = new HashMap<>();

  public MaliciousEventService(
      KafkaConfig kafkaConfig, MongoClient mongoClient) {
    this.kafka = new Kafka(kafkaConfig);
    this.mongoClient = mongoClient;
  }

  public void recordMaliciousEvent(String accountId, RecordMaliciousEventRequest request) {
    MaliciousEventMessage evt = request.getMaliciousEvent();
    String actor = evt.getActor();
    String filterId = evt.getFilterId();

    String refId = UUID.randomUUID().toString();
    logger.debug("received malicious event " + evt.getLatestApiEndpoint() + " filterId " + evt.getFilterId() + " eventType " + evt.getEventType().toString());

    EventType eventType = evt.getEventType();

    MaliciousEventModel.EventType maliciousEventType =
        EventType.EVENT_TYPE_AGGREGATED.equals(eventType)
            ? MaliciousEventModel.EventType.AGGREGATED
            : MaliciousEventModel.EventType.SINGLE;

    MaliciousEventModel maliciousEventModel =
        MaliciousEventModel.newBuilder()
            .setDetectedAt(evt.getDetectedAt())
            .setActor(actor)
            .setFilterId(filterId)
            .setLatestApiEndpoint(evt.getLatestApiEndpoint())
            .setLatestApiMethod(URLMethods.Method.fromString(evt.getLatestApiMethod()))
            .setLatestApiOrig(evt.getLatestApiPayload())
            .setLatestApiCollectionId(evt.getLatestApiCollectionId())
            .setEventType(maliciousEventType)
            .setLatestApiIp(evt.getLatestApiIp())
            .setCountry(evt.getMetadata().getCountryCode())
            .setCategory(evt.getCategory())
            .setSubCategory(evt.getSubCategory())
            .setRefId(refId)
            .setSeverity(evt.getSeverity())
            .setType(evt.getType())
            .setMetadata(evt.getMetadata().toString())
            .build();

    this.kafka.send(
        KafkaUtils.generateMsg(
            maliciousEventModel, MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS, accountId),
        KafkaTopic.ThreatDetection.INTERNAL_DB_MESSAGES);
  }

  private static <T> Set<T> findDistinctFields(
      MongoCollection<MaliciousEventModel> coll, String fieldName, Class<T> tClass, Bson filters) {
    DistinctIterable<T> r = coll.distinct(fieldName, filters, tClass);
    Set<T> result = new HashSet<>();
    MongoCursor<T> cursor = r.cursor();
    while (cursor.hasNext()) {
      result.add(cursor.next());
    }
    return result;
  }

  public  ThreatActorFilterResponse fetchThreatActorFilters(
      String accountId, ThreatActorFilterRequest request) {
    MongoCollection<MaliciousEventModel> coll =
        this.mongoClient
            .getDatabase(accountId)
            .getCollection("malicious_events", MaliciousEventModel.class);

    Set<String> latestAttack =
        MaliciousEventService.<String>findDistinctFields(
            coll, "filterId", String.class, Filters.empty());

    Set<String> countries =
        MaliciousEventService.<String>findDistinctFields(
            coll, "country", String.class, Filters.empty());

    Set<String> actorIds =
        MaliciousEventService.<String>findDistinctFields(
            coll, "actor", String.class, Filters.empty());

    return ThreatActorFilterResponse.newBuilder().addAllSubCategories(latestAttack).addAllCountries(countries).addAllActorId(actorIds).build();
  }

  public FetchAlertFiltersResponse fetchAlertFilters(
      String accountId, FetchAlertFiltersRequest request) {
    MongoCollection<MaliciousEventModel> coll =
        this.mongoClient
            .getDatabase(accountId)
            .getCollection("malicious_events", MaliciousEventModel.class);

    Set<String> actors =
        MaliciousEventService.<String>findDistinctFields(
            coll, "actor", String.class, Filters.empty());
    Set<String> urls =
        MaliciousEventService.<String>findDistinctFields(
            coll, "latestApiEndpoint", String.class, Filters.empty());
    Set<String> subCategories =
        MaliciousEventService.<String>findDistinctFields(
            coll, "filterId", String.class, Filters.empty());

    return FetchAlertFiltersResponse.newBuilder().addAllActors(actors).addAllUrls(urls).addAllSubCategory(subCategories).build();
  }

  public ListMaliciousRequestsResponse listMaliciousRequests(
      String accountId, ListMaliciousRequestsRequest request) {

    if(!shouldNotCreateIndexes.getOrDefault(accountId, false)) {
      createIndexIfAbsent(accountId);
    }

    int limit = request.getLimit();
    int skip = request.hasSkip() ? request.getSkip() : 0;
    Map<String, Integer> sort = request.getSortMap();
    ListMaliciousRequestsRequest.Filter filter = request.getFilter();

    if(filter.getLatestAttackList() == null || filter.getLatestAttackList().isEmpty()) {
      return ListMaliciousRequestsResponse.newBuilder().build();
    }

    MongoCollection<MaliciousEventModel> coll =
        this.mongoClient
            .getDatabase(accountId)
            .getCollection(
                MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS, MaliciousEventModel.class);

    Document query = new Document();
    if (!filter.getActorsList().isEmpty()) {
      query.append("actor", new Document("$in", filter.getActorsList()));
    }

    if (!filter.getUrlsList().isEmpty()) {
      query.append("latestApiEndpoint", new Document("$in", filter.getUrlsList()));
    }

    if (!filter.getIpsList().isEmpty()) {
      query.append("latestApiIp", new Document("$in", filter.getIpsList()));
    }

    if (!filter.getTypesList().isEmpty()) {
      query.append("type", new Document("$in", filter.getTypesList()));
    }

    if (!filter.getSubCategoryList().isEmpty()) {
      query.append("subCategory", new Document("$in", filter.getSubCategoryList()));
    }

    if (!filter.getLatestAttackList().isEmpty()) {
      query.append("filterId", new Document("$in", filter.getLatestAttackList()));
    }

    // Handle status filter
    if (filter.hasStatusFilter()) {
      String statusFilter = filter.getStatusFilter();
      if ("UNDER_REVIEW".equals(statusFilter)) {
        query.append("status", "UNDER_REVIEW");
      } else if ("IGNORED".equals(statusFilter)) {
        query.append("status", "IGNORED");
      } else if ("ACTIVE".equals(statusFilter)) {
        // For Events tab: show null, empty, or ACTIVE status
        List<Document> orConditions = Arrays.asList(
          new Document("status", new Document("$exists", false)),
          new Document("status", null),
          new Document("status", ""),
          new Document("status", "ACTIVE")
        );
        query.append("$or", orConditions);
      }
    }

    if (filter.hasDetectedAtTimeRange()) {
      TimeRangeFilter timeRange = filter.getDetectedAtTimeRange();
      long start = timeRange.hasStart() ? timeRange.getStart() : 0;
      long end = timeRange.hasEnd() ? timeRange.getEnd() : Long.MAX_VALUE;

      query.append("detectedAt", new Document("$gte", start).append("$lte", end));
    }

    long total = coll.countDocuments(query);
    try (MongoCursor<MaliciousEventModel> cursor =
        coll.find(query)
            .sort(new Document("detectedAt", sort.getOrDefault("detectedAt", -1)))
            .skip(skip)
            .limit(limit)
            .cursor()) {
      List<ListMaliciousRequestsResponse.MaliciousEvent> maliciousEvents = new ArrayList<>();
      while (cursor.hasNext()) {
        MaliciousEventModel evt = cursor.next();
        String metadata = evt.getMetadata() != null ? evt.getMetadata() : "";

        maliciousEvents.add(
            ListMaliciousRequestsResponse.MaliciousEvent.newBuilder()
                .setActor(evt.getActor())
                .setFilterId(evt.getFilterId())
                .setFilterId(evt.getFilterId())
                .setId(evt.getId())
                .setIp(evt.getLatestApiIp())
                .setCountry(evt.getCountry())
                .setPayload(evt.getLatestApiOrig())
                .setEndpoint(evt.getLatestApiEndpoint())
                .setMethod(evt.getLatestApiMethod().name())
                .setDetectedAt(evt.getDetectedAt())
                .setCategory(evt.getCategory())
                .setSubCategory(evt.getSubCategory())
                .setApiCollectionId(evt.getLatestApiCollectionId())
                .setType(evt.getType())
                .setRefId(evt.getRefId())
                .setEventTypeVal(evt.getEventType().toString())
                .setMetadata(metadata)
                .setStatus(evt.getStatus() != null ? evt.getStatus().toString() : "ACTIVE")
                .build());
      }
      return ListMaliciousRequestsResponse.newBuilder()
          .setTotal(total)
          .addAllMaliciousEvents(maliciousEvents)
          .build();
    }
  }

  public void createIndexIfAbsent(String accountId) {
    ThreatUtils.createIndexIfAbsent(accountId, mongoClient);
    shouldNotCreateIndexes.put(accountId, true);
  }

  public boolean updateMaliciousEventStatus(String accountId, String eventId, String status) {
    try {
      MongoCollection<MaliciousEventModel> coll =
          this.mongoClient
              .getDatabase(accountId)
              .getCollection(
                  MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS, MaliciousEventModel.class);
      
      MaliciousEventModel event = coll.find(Filters.eq("_id", eventId)).first();
      if (event == null) {
        logger.warn("Event not found for id: " + eventId);
        return false;
      }
      
      MaliciousEventModel.Status eventStatus = MaliciousEventModel.Status.valueOf(status.toUpperCase());
      
      Bson filter = Filters.eq("_id", eventId);
      Bson update = Updates.set("status", eventStatus.toString());
      
      boolean updateSuccess = coll.updateOne(filter, update).getModifiedCount() > 0;      
      return updateSuccess;
    } catch (Exception e) {
      logger.error("Error updating malicious event status", e);
      return false;
    }
  }

  public int bulkUpdateMaliciousEventStatus(String accountId, List<String> eventIds, String status) {
    int updatedCount = 0;
    try {
      logger.info("Bulk updating events for account " + accountId + " with IDs: " + eventIds + " to status: " + status);
      
      MongoCollection<MaliciousEventModel> coll =
          this.mongoClient
              .getDatabase(accountId)
              .getCollection(
                  MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS, MaliciousEventModel.class);
      
      MaliciousEventModel.Status eventStatus = MaliciousEventModel.Status.valueOf(status.toUpperCase());
      
      for (String eventId : eventIds) {
        MaliciousEventModel event = coll.find(Filters.eq("_id", eventId)).first();
        if (event == null) {
          logger.warn("Event not found for id: " + eventId);
          continue;
        }
        
        Bson filter = Filters.eq("_id", eventId);
        Bson update = Updates.set("status", eventStatus.toString());
        
        boolean updateSuccess = coll.updateOne(filter, update).getModifiedCount() > 0;
        logger.debug("Update result for ID " + eventId + ": " + updateSuccess);
        
        if (updateSuccess) {
          updatedCount++;
        }
      }
      
      return updatedCount;
    } catch (Exception e) {
      logger.error("Error bulk updating malicious event status", e);
      return updatedCount;
    }
  }

  public int bulkUpdateFilteredEvents(String accountId, Map<String, Object> filter, String status) {
    try {
      MongoCollection<MaliciousEventModel> coll =
          this.mongoClient
              .getDatabase(accountId)
              .getCollection(
                  MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS, MaliciousEventModel.class);
      
      // Build query from filter
      Document query = new Document();
      
      // Handle ips/actors filter
      List<String> ips = (List<String>) filter.get("ips");
      if (ips != null && !ips.isEmpty()) {
        query.append("actor", new Document("$in", ips));
      }
      
      // Handle urls filter
      List<String> urls = (List<String>) filter.get("urls");
      if (urls != null && !urls.isEmpty()) {
        query.append("latestApiEndpoint", new Document("$in", urls));
      }
      
      // Handle types filter
      List<String> types = (List<String>) filter.get("types");
      if (types != null && !types.isEmpty()) {
        query.append("type", new Document("$in", types));
      }
      
      // Handle latestAttack filter
      List<String> latestAttack = (List<String>) filter.get("latestAttack");
      if (latestAttack != null && !latestAttack.isEmpty()) {
        query.append("filterId", new Document("$in", latestAttack));
      }
      
      // Handle time range
      Map<String, Integer> timeRange = (Map<String, Integer>) filter.get("detected_at_time_range");
      if (timeRange != null) {
        Integer start = timeRange.get("start");
        Integer end = timeRange.get("end");
        Document timeQuery = new Document();
        if (start != null) {
          timeQuery.append("$gte", start);
        }
        if (end != null) {
          timeQuery.append("$lte", end);
        }
        if (!timeQuery.isEmpty()) {
          query.append("detectedAt", timeQuery);
        }
      }
      
      // Handle status filter (for current tab)
      String statusFilter = (String) filter.get("statusFilter");
      if ("UNDER_REVIEW".equals(statusFilter)) {
        query.append("status", "UNDER_REVIEW");
      } else if ("IGNORED".equals(statusFilter)) {
        query.append("status", "IGNORED");
      } else if ("ACTIVE".equals(statusFilter) || "EVENTS".equals(statusFilter)) {
        // For Events tab: show null, empty, or ACTIVE status
        List<Document> orConditions = Arrays.asList(
          new Document("status", new Document("$exists", false)),
          new Document("status", null),
          new Document("status", ""),
          new Document("status", "ACTIVE")
        );
        query.append("$or", orConditions);
      }
      
      logger.info("Bulk updating events with filter: " + query.toJson() + " to status: " + status);
      
      // Get all matching events for cache management
      List<MaliciousEventModel> eventsToUpdate = new ArrayList<>();
      try (MongoCursor<MaliciousEventModel> cursor = coll.find(query).cursor()) {
        while (cursor.hasNext()) {
          eventsToUpdate.add(cursor.next());
        }
      }
      
      // Perform bulk update
      MaliciousEventModel.Status eventStatus = MaliciousEventModel.Status.valueOf(status.toUpperCase());
      Bson update = Updates.set("status", eventStatus.toString());
      
      long modifiedCount = coll.updateMany(query, update).getModifiedCount();
      
      // Cache will be updated automatically during next DB insertion checks
      
      return (int) modifiedCount;
    } catch (Exception e) {
      logger.error("Error bulk updating filtered events", e);
      return 0;
    }
  }

  public int bulkDeleteMaliciousEvents(String accountId, List<String> eventIds) {
    try {
      MongoCollection<MaliciousEventModel> coll =
          this.mongoClient
              .getDatabase(accountId)
              .getCollection(
                  MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS, MaliciousEventModel.class);

      Document query = new Document("_id", new Document("$in", eventIds));
      long deletedCount = coll.deleteMany(query).getDeletedCount();

      logger.info("Deleted " + deletedCount + " malicious events");
      return (int) deletedCount;
    } catch (Exception e) {
      logger.error("Error bulk deleting malicious events", e);
      return 0;
    }
  }

  public int bulkDeleteFilteredEvents(String accountId, Map<String, Object> filter) {
    try {
      MongoCollection<MaliciousEventModel> coll =
          this.mongoClient
              .getDatabase(accountId)
              .getCollection(
                  MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS, MaliciousEventModel.class);

      // Build query from filter (same as bulkUpdateFilteredEvents)
      Document query = new Document();

      // Handle ips/actors filter
      List<String> ips = (List<String>) filter.get("ips");
      if (ips != null && !ips.isEmpty()) {
        query.append("actor", new Document("$in", ips));
      }

      // Handle urls filter
      List<String> urls = (List<String>) filter.get("urls");
      if (urls != null && !urls.isEmpty()) {
        query.append("latestApiEndpoint", new Document("$in", urls));
      }

      // Handle types filter
      List<String> types = (List<String>) filter.get("types");
      if (types != null && !types.isEmpty()) {
        query.append("type", new Document("$in", types));
      }

      // Handle latestAttack filter
      List<String> latestAttack = (List<String>) filter.get("latestAttack");
      if (latestAttack != null && !latestAttack.isEmpty()) {
        query.append("filterId", new Document("$in", latestAttack));
      }

      // Handle time range
      Map<String, Integer> timeRange = (Map<String, Integer>) filter.get("detected_at_time_range");
      if (timeRange != null) {
        Integer start = timeRange.get("start");
        Integer end = timeRange.get("end");
        Document timeQuery = new Document();
        if (start != null) {
          timeQuery.append("$gte", start);
        }
        if (end != null) {
          timeQuery.append("$lte", end);
        }
        if (!timeQuery.isEmpty()) {
          query.append("detectedAt", timeQuery);
        }
      }

      // Handle status filter
      String statusFilter = (String) filter.get("statusFilter");
      if ("UNDER_REVIEW".equals(statusFilter)) {
        query.append("status", "UNDER_REVIEW");
      } else if ("IGNORED".equals(statusFilter)) {
        query.append("status", "IGNORED");
      } else if ("ACTIVE".equals(statusFilter) || "EVENTS".equals(statusFilter)) {
        // For Events tab: show null, empty, or ACTIVE status
        List<Document> orConditions = Arrays.asList(
          new Document("status", new Document("$exists", false)),
          new Document("status", null),
          new Document("status", ""),
          new Document("status", "ACTIVE")
        );
        query.append("$or", orConditions);
      }

      logger.info("Bulk deleting events with filter: " + query.toJson());

      long deletedCount = coll.deleteMany(query).getDeletedCount();

      logger.info("Deleted " + deletedCount + " filtered malicious events");
      return (int) deletedCount;
    } catch (Exception e) {
      logger.error("Error bulk deleting filtered events", e);
      return 0;
    }
  }
}
