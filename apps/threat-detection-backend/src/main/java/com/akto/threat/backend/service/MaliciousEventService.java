package com.akto.threat.backend.service;

import com.akto.dto.threat_detection_backend.MaliciousEventDto;
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
import com.akto.threat.backend.dao.MaliciousEventDao;
import com.akto.threat.backend.utils.KafkaUtils;
import com.akto.threat.backend.utils.ThreatUtils;
import com.akto.util.ThreatDetectionConstants;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.DistinctIterable;
import com.mongodb.client.MongoCursor;
import org.bson.conversions.Bson;

import java.util.*;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;

public class MaliciousEventService {

  private final Kafka kafka;
  private final MaliciousEventDao maliciousEventDao;
  private static final LoggerMaker logger = new LoggerMaker(MaliciousEventService.class);

  private static final HashMap<String, Boolean> shouldNotCreateIndexes = new HashMap<>();

  public MaliciousEventService(
      KafkaConfig kafkaConfig, MaliciousEventDao maliciousEventDao) {
    this.kafka = new Kafka(kafkaConfig);
    this.maliciousEventDao = maliciousEventDao;
  }

  // Convert string label to model Label enum
  private static MaliciousEventDto.Label convertStringLabelToModelLabel(String labelString) {
    if (labelString == null || labelString.isEmpty()) {
      return MaliciousEventDto.Label.THREAT; // Default for backward compatibility
    }

    String normalized = labelString.toUpperCase().trim();
    switch (normalized) {
      case "THREAT":
        return MaliciousEventDto.Label.THREAT;
      case "GUARDRAIL":
        return MaliciousEventDto.Label.GUARDRAIL;
      default:
        logger.debug("Unknown label string: " + labelString + ", defaulting to THREAT");
        return MaliciousEventDto.Label.THREAT;
    }
  }

  // Convert model Label enum to string
  private static String convertModelLabelToString(MaliciousEventDto.Label modelLabel) {
    if (modelLabel == null) {
      return "threat";
    }

    switch (modelLabel) {
      case THREAT:
        return "threat";
      case GUARDRAIL:
        return "guardrail";
      default:
        return "threat";
    }
  }

  // Helper method to apply label filter with backward compatibility
  private static void applyLabelFilter(Document query, MaliciousEventDto.Label labelEnum) {
    List<Document> orConditions = new ArrayList<>();
    orConditions.add(new Document("label", labelEnum.name()));

    if (labelEnum == MaliciousEventDto.Label.THREAT) {
      // For backward compatibility: treat null/missing label as "threat"
      orConditions.add(new Document("label", new Document("$exists", false)));
      orConditions.add(new Document("label", null));
    }

    if (orConditions.size() > 1) {
      List<Document> andConditions = new ArrayList<>();
      if (!query.isEmpty()) {
        andConditions.add(new Document(query));
      }
      andConditions.add(new Document("$or", orConditions));
      query.clear();
      query.append("$and", andConditions);
    } else {
      query.append("label", labelEnum.name());
    }
  }

  public void recordMaliciousEvent(String accountId, RecordMaliciousEventRequest request) {
    MaliciousEventMessage evt = request.getMaliciousEvent();
    String actor = evt.getActor();
    String filterId = evt.getFilterId();

    String refId = UUID.randomUUID().toString();
    logger.debug("received malicious event " + evt.getLatestApiEndpoint() + " filterId " + evt.getFilterId() + " eventType " + evt.getEventType().toString());

    EventType eventType = evt.getEventType();

    MaliciousEventDto.EventType maliciousEventType =
        EventType.EVENT_TYPE_AGGREGATED.equals(eventType)
            ? MaliciousEventDto.EventType.AGGREGATED
            : MaliciousEventDto.EventType.SINGLE;

    // Convert string label to model enum
    MaliciousEventDto.Label label = convertStringLabelToModelLabel(evt.getLabel());

    String status = (evt.getStatus() != null && !evt.getStatus().isEmpty()) ? evt.getStatus() : ThreatDetectionConstants.ACTIVE;

    MaliciousEventDto maliciousEventModel =
        MaliciousEventDto.newBuilder()
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
            .setSuccessfulExploit(evt.getSuccessfulExploit())
            .setStatus(MaliciousEventDto.Status.valueOf(status.toUpperCase()))
            .setLabel(label)
            .setHost(evt.getHost() != null ? evt.getHost() : "")
            .build();

    this.kafka.send(
        KafkaUtils.generateMsg(
            maliciousEventModel, MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS, accountId),
        KafkaTopic.ThreatDetection.INTERNAL_DB_MESSAGES);
  }

  private <T> Set<T> findDistinctFields(
      String accountId, String fieldName, Class<T> tClass, Bson filters) {
    DistinctIterable<T> r = maliciousEventDao.getCollection(accountId).distinct(fieldName, filters, tClass);
    Set<T> result = new HashSet<>();
    MongoCursor<T> cursor = r.cursor();
    while (cursor.hasNext()) {
      result.add(cursor.next());
    }
    return result;
  }

  public  ThreatActorFilterResponse fetchThreatActorFilters(
      String accountId, ThreatActorFilterRequest request) {

    Set<String> latestAttack =
        this.findDistinctFields(accountId, "filterId", String.class, Filters.empty());

    Set<String> countries =
        this.findDistinctFields(accountId, "country", String.class, Filters.empty());

    Set<String> actorIds =
        this.findDistinctFields(accountId, "actor", String.class, Filters.empty());

    Set<String> hosts =
        this.findDistinctFields(accountId, "host", String.class, Filters.empty());

    return ThreatActorFilterResponse.newBuilder().addAllSubCategories(latestAttack).addAllCountries(countries).addAllActorId(actorIds).addAllHost(hosts).build();
  }

  public FetchAlertFiltersResponse fetchAlertFilters(
      String accountId, FetchAlertFiltersRequest request) {

    Set<String> actors =
        this.findDistinctFields(accountId, "actor", String.class, Filters.empty());
    Set<String> urls =
        this.findDistinctFields(accountId, "latestApiEndpoint", String.class, Filters.empty());
    Set<String> subCategories =
        this.findDistinctFields(accountId, "filterId", String.class, Filters.empty());
    Set<String> hosts =
        this.findDistinctFields(accountId, "host", String.class, Filters.empty());

    return FetchAlertFiltersResponse.newBuilder().addAllActors(actors).addAllUrls(urls).addAllSubCategory(subCategories).addAllHosts(hosts).build();
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

    if (!filter.getHostsList().isEmpty()) {
      query.append("host", new Document("$in", filter.getHostsList()));
    }

    if (filter.hasLatestApiOrigRegex() && !filter.getLatestApiOrigRegex().isEmpty()) {
      query.append("latestApiOrig", new Document("$regex", filter.getLatestApiOrigRegex()));
    }


    if (!filter.getLatestAttackList().isEmpty()) {
      query.append("filterId", new Document("$in", filter.getLatestAttackList()));
    }

    // Handle status filter
    if (filter.hasStatusFilter()) {
      applyStatusFilter(query, filter.getStatusFilter());
    }

    if (filter.hasDetectedAtTimeRange()) {
      TimeRangeFilter timeRange = filter.getDetectedAtTimeRange();
      long start = timeRange.hasStart() ? timeRange.getStart() : 0;
      long end = timeRange.hasEnd() ? timeRange.getEnd() : Long.MAX_VALUE;

      query.append("detectedAt", new Document("$gte", start).append("$lte", end));
    }

    if (filter.hasSuccessfulExploit()) {
      boolean val = filter.getSuccessfulExploit();
      if (val) {
        query.append("successfulExploit", true);
      } else {
        List<Document> andConditions = new ArrayList<>();
        andConditions.add(new Document(query));
        andConditions.add(new Document("$or", Arrays.asList(
            new Document("successfulExploit", false),
            new Document("successfulExploit", new Document("$exists", false))
        )));
        query.clear();
        query.append("$and", andConditions);
      }
    }

    if (filter.hasLabel()) {
      String labelString = filter.getLabel();
      MaliciousEventDto.Label labelEnum = convertStringLabelToModelLabel(labelString);
      applyLabelFilter(query, labelEnum);
    }

    long total = maliciousEventDao.countDocuments(accountId, query);
    try (MongoCursor<MaliciousEventDto> cursor =
        maliciousEventDao.getCollection(accountId)
            .find(query)
            .sort(new Document("detectedAt", sort.getOrDefault("detectedAt", -1)))
            .skip(skip)
            .limit(limit)
            .cursor()) {
      List<ListMaliciousRequestsResponse.MaliciousEvent> maliciousEvents = new ArrayList<>();
      while (cursor.hasNext()) {
        MaliciousEventDto evt = cursor.next();
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
                .setStatus(evt.getStatus() != null ? evt.getStatus().toString() : ThreatDetectionConstants.ACTIVE)
                .setSuccessfulExploit(evt.getSuccessfulExploit() != null ? evt.getSuccessfulExploit() : false)
                .setLabel(convertModelLabelToString(evt.getLabel()))
                .setHost(evt.getHost() != null ? evt.getHost() : "")
                .setJiraTicketUrl(evt.getJiraTicketUrl() != null ? evt.getJiraTicketUrl() : "")
                .build());
      }
      return ListMaliciousRequestsResponse.newBuilder()
          .setTotal(total)
          .addAllMaliciousEvents(maliciousEvents)
          .build();
    }
  }

  public void createIndexIfAbsent(String accountId) {
    ThreatUtils.createIndexIfAbsent(accountId, maliciousEventDao);
    shouldNotCreateIndexes.put(accountId, true);
  }

  public int updateMaliciousEventStatus(String accountId, List<String> eventIds, Map<String, Object> filterMap, String status, String jiraTicketUrl) {
    try {
      Bson update = null;

      if(status != null && !status.isEmpty()) {
        MaliciousEventDto.Status eventStatus = MaliciousEventDto.Status.valueOf(status.toUpperCase());
        update = Updates.set("status", eventStatus.toString());
      }
      if (jiraTicketUrl != null && !jiraTicketUrl.isEmpty()) {
        update = Updates.set("jiraTicketUrl", jiraTicketUrl);
      }

      Document query = buildQuery(eventIds, filterMap, "update");
      if (query == null) {
        return 0;
      }

      String logMessage = String.format("Updating events %s to status: %s and jiraTicketUrl: %s",
          getQueryDescription(eventIds, filterMap), status, jiraTicketUrl != null && !jiraTicketUrl.isEmpty() ? jiraTicketUrl : "null");
      logger.info(logMessage);

      long modifiedCount = maliciousEventDao.getCollection(accountId).updateMany(query, update).getModifiedCount();
      return (int) modifiedCount;
    } catch (Exception e) {
      logger.error("Error updating malicious event status", e);
      return 0;
    }
  }

  public int deleteMaliciousEvents(String accountId, List<String> eventIds, Map<String, Object> filterMap) {
    try {
      Document query = buildQuery(eventIds, filterMap, "delete");
      if (query == null) {
        return 0;
      }

      String logMessage = "Deleting events " + getQueryDescription(eventIds, filterMap);
      logger.info(logMessage);

      long deletedCount = maliciousEventDao.getCollection(accountId).deleteMany(query).getDeletedCount();
      logger.info("Deleted " + deletedCount + " malicious events");

      return (int) deletedCount;
    } catch (Exception e) {
      logger.error("Error deleting malicious events", e);
      return 0;
    }
  }

  private Document buildQuery(List<String> eventIds, Map<String, Object> filterMap, String operation) {
    if (eventIds != null && !eventIds.isEmpty()) {
      // Query by event IDs
      return new Document("_id", new Document("$in", eventIds));
    } else if (filterMap != null && !filterMap.isEmpty()) {
      // Query by filter criteria
      return buildQueryFromFilter(filterMap);
    } else {
      logger.warn("Neither eventIds nor filterMap provided for " + operation);
      return null;
    }
  }

  private String getQueryDescription(List<String> eventIds, Map<String, Object> filterMap) {
    if (eventIds != null && !eventIds.isEmpty()) {
      return "by IDs: " + eventIds;
    } else if (filterMap != null && !filterMap.isEmpty()) {
      Document query = buildQueryFromFilter(filterMap);
      return "by filter: " + query.toJson();
    }
    return "";
  }


  private void applyStatusFilter(Document query, String statusFilter) {
    if (statusFilter == null) {
      return;
    }

    if (ThreatDetectionConstants.UNDER_REVIEW.equals(statusFilter)) {
      query.append("status", ThreatDetectionConstants.UNDER_REVIEW);
    } else if (ThreatDetectionConstants.IGNORED.equals(statusFilter)) {
      query.append("status", ThreatDetectionConstants.IGNORED);
    } else if (ThreatDetectionConstants.ACTIVE.equals(statusFilter) || ThreatDetectionConstants.EVENTS_FILTER.equals(statusFilter)) {
      // For Events tab: show null, empty, or ACTIVE status
      List<Document> orConditions = Arrays.asList(
        new Document("status", new Document("$exists", false)),
        new Document("status", null),
        new Document("status", ""),
        new Document("status", ThreatDetectionConstants.ACTIVE)
      );
      query.append("$or", orConditions);
    }
  }

  private Document buildQueryFromFilter(Map<String, Object> filter) {
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
    applyStatusFilter(query, statusFilter);

    // Handle label filter with backward compatibility
    String label = (String) filter.get("label");
    if (label != null && !label.isEmpty()) {
      MaliciousEventDto.Label labelEnum = convertStringLabelToModelLabel(label);
      applyLabelFilter(query, labelEnum);
    }

    String latestApiOrigRegex = (String) filter.get("latestApiOrigRegex");
    if (latestApiOrigRegex != null && !latestApiOrigRegex.isEmpty()) {
      query.append("latestApiOrig", new Document("$regex", latestApiOrigRegex));
    }

    return query;
  }
}
