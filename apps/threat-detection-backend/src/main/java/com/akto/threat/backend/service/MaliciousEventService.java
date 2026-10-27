package com.akto.threat.backend.service;

import com.akto.dao.AgenticSessionContextDao;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.agentic_sessions.SessionDocument;
import com.akto.dto.threat_detection_backend.MaliciousEventDto;
import com.akto.threat.backend.utils.ThreatUtils;
import com.akto.dto.type.URLMethods;
import com.akto.kafka.Kafka;
import com.akto.kafka.KafkaConfig;
import com.akto.log.LoggerMaker;
import com.akto.proto.generated.threat_detection.message.malicious_event.event_type.v1.EventType;
import com.akto.proto.generated.threat_detection.message.malicious_event.v1.MaliciousEventMessage;
import com.akto.proto.generated.threat_detection.message.malicious_event.v1.OwaspCategory;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchAlertFiltersRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchAlertFiltersResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListGuardrailViolationPayloadsRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListGuardrailViolationPayloadsResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatActorFilterRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatActorFilterResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.TimeRangeFilter;
import com.akto.proto.generated.threat_detection.service.malicious_alert_service.v1.RecordMaliciousEventRequest;
import com.akto.proto.generated.threat_detection.service.agentic_session_service.v1.BulkUpdateAgenticSessionContextRequest;
import com.akto.proto.generated.threat_detection.message.agentic_session.v1.SessionDocumentMessage;
import com.akto.proto.generated.threat_detection.message.agentic_session.v1.ConversationEntry;
import com.akto.threat.backend.constants.KafkaTopic;
import com.akto.threat.backend.constants.MongoDBCollection;
import com.akto.threat.backend.dao.ActorInfoDao;
import com.akto.threat.backend.dao.MaliciousEventDao;
import com.akto.threat.backend.cache.DashboardFilterCache;
import com.akto.threat.backend.utils.KafkaUtils;
import com.akto.threat.backend.utils.ParallelQueryExecutor;
import com.akto.util.ThreatDetectionConstants;
import com.mongodb.client.model.CountOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.DistinctIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.mongodb.client.model.Updates;

public class MaliciousEventService {

  private final Kafka kafka;
  private final MaliciousEventDao maliciousEventDao;
  private static final LoggerMaker logger = new LoggerMaker(MaliciousEventService.class);

  private static final HashMap<String, Boolean> shouldNotCreateIndexes = new HashMap<>();
  private static final List<String> IGNORED_POLICIES_FOR_ACCOUNT = Arrays.asList("SQLInjection", "SSRF");

  private static final boolean USE_ACTOR_INFO_TABLE = Boolean.parseBoolean(
      System.getenv().getOrDefault("USE_ACTOR_INFO_TABLE", "false")
  );

  private static final DashboardFilterCache<FetchAlertFiltersResponse> alertFiltersCache = new DashboardFilterCache<>();
  private static final DashboardFilterCache<Long> countCache = new DashboardFilterCache<>();

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

  // // Helper method to apply label filter with backward compatibility
  // private static void applyLabelFilter(Document query, MaliciousEventDto.Label labelEnum) {
  //   List<Document> orConditions = new ArrayList<>();
  //   orConditions.add(new Document("label", labelEnum.name()));

  //   if (labelEnum == MaliciousEventDto.Label.THREAT) {
  //     // For backward compatibility: treat null/missing label as "threat"
  //     orConditions.add(new Document("label", new Document("$exists", false)));
  //     orConditions.add(new Document("label", null));
  //   }

  //   if (orConditions.size() > 1) {
  //     List<Document> andConditions = new ArrayList<>();
  //     if (!query.isEmpty()) {
  //       andConditions.add(new Document(query));
  //     }
  //     andConditions.add(new Document("$or", orConditions));
  //     query.clear();
  //     query.append("$and", andConditions);
  //   } else {
  //     query.append("label", labelEnum.name());
  //   }
  // }

  // Events for these filterIds are dropped at ingestion.
  private static final Set<String> SKIPPED_FILTER_IDS = Collections.singleton("ApiLevelRateLimited");

  public void recordMaliciousEvent(String accountId, RecordMaliciousEventRequest request) {
    MaliciousEventMessage evt = request.getMaliciousEvent();
    String actor = evt.getActor();
    String filterId = evt.getFilterId();

    if (SKIPPED_FILTER_IDS.contains(filterId)) {
      logger.debug("Skipping malicious event for filterId " + filterId);
      return;
    }

    MaliciousEventDto.Builder builder = MaliciousEventDto.newBuilder();
    String severity = evt.getSeverity();
    // Skip recording for specific policies on specific account.
    // TODO: Remove once policy is fixed.

    String refId = (evt.hasRefId() && !evt.getRefId().isEmpty())
        ? evt.getRefId()
        : UUID.randomUUID().toString();
    logger.debug("received malicious event " + evt.getLatestApiEndpoint() + " filterId " + evt.getFilterId() + " eventType " + evt.getEventType().toString());

    EventType eventType = evt.getEventType();

    MaliciousEventDto.EventType maliciousEventType =
        EventType.EVENT_TYPE_AGGREGATED.equals(eventType)
            ? MaliciousEventDto.EventType.AGGREGATED
            : MaliciousEventDto.EventType.SINGLE;

    // Convert string label to model enum
    MaliciousEventDto.Label label = convertStringLabelToModelLabel(evt.getLabel());

    String status = (evt.getStatus() != null && !evt.getStatus().isEmpty()) ? evt.getStatus() : ThreatDetectionConstants.ACTIVE;

    // Extract contextSource from the event message
    String contextSource = null;
    if (evt.getContextSource() != null && !evt.getContextSource().isEmpty()) {
        contextSource = evt.getContextSource();
    }

    // Extract sessionId from the event message
    String sessionId = null;
    if (evt.getSessionId() != null && !evt.getSessionId().isEmpty()) {
        sessionId = evt.getSessionId();
    }

    builder.setDetectedAt(evt.getDetectedAt())
        .setActor(actor)
        .setFilterId(filterId)
        .setLatestApiEndpoint(evt.getLatestApiEndpoint())
        .setLatestApiMethod(URLMethods.Method.fromString(evt.getLatestApiMethod()))
        .setLatestApiOrig("settings-scanner".equals(actor) ? ThreatUtils.repairConfigScanEnvelope(evt.getLatestApiPayload()) : evt.getLatestApiPayload())
        .setLatestApiCollectionId(evt.getLatestApiCollectionId())
        .setEventType(maliciousEventType)
        .setLatestApiIp(evt.getLatestApiIp())
        .setSessionId(sessionId)
        .setCountry(evt.getMetadata().getCountryCode())
        .setDestCountry(evt.getMetadata() != null && evt.getMetadata().getDestCountryCode() != null ? evt.getMetadata().getDestCountryCode() : "")
        .setCategory(evt.getCategory())
        .setSubCategory(evt.getSubCategory())
        .setRefId(refId)
        .setSeverity(severity)
        .setType(evt.getType())
        .setMetadata(evt.getMetadata().toString())
        .setSuccessfulExploit(evt.getSuccessfulExploit())
        .setStatus(MaliciousEventDto.Status.valueOf(status.toUpperCase()))
        .setLabel(label)
        .setHost(evt.getHost() != null ? evt.getHost() : "")
        .setEvidenceLine(evt.getEvidenceLine() != null ? evt.getEvidenceLine() : "");

    // Set contextSource if available
    if (contextSource != null && !contextSource.isEmpty()) {
        builder.setContextSource(contextSource);
    }

    if (ThreatUtils.groupsActivities(accountId)) {
        builder.setGroupKey(ThreatUtils.activityGroupKey(evt.getLatestApiPayload()));
    }

    List<MaliciousEventDto.OwaspCategory> owaspCategories = evt.getOwaspCategoriesList()
        .stream()
        .map(o -> new MaliciousEventDto.OwaspCategory(
            o.getId(), o.getName(), o.getSeverity(), o.getConfidence()))
        .collect(Collectors.toList());
    if (!owaspCategories.isEmpty()) {
        builder.setOwaspCategories(owaspCategories);
    }

    if (ThreatDetectionConstants.HUMAN_APPROVAL.equalsIgnoreCase(status)) {
        String humanResponse = evt.getHumanResponse();
        builder.setHumanResponse(
            humanResponse != null && !humanResponse.isEmpty()
                ? humanResponse
                : MaliciousEventDto.HumanResponse.PENDING.name());
    }

    MaliciousEventDto maliciousEventModel = builder.build();

    this.kafka.send(
        KafkaUtils.generateMsg(
            maliciousEventModel, MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS, accountId),
        KafkaTopic.ThreatDetection.INTERNAL_DB_MESSAGES);
  }

  public int updateRemediation(String accountId, String refId, String remediation) {
    return updateEnrichment(accountId, refId, remediation, null);
  }

  /**
   * Patches LLM-generated enrichment onto an already-recorded event, keyed on refId.
   * Both fields are optional and independently gated on the gateway, so only the
   * non-empty ones are written - otherwise a remediation-only call would wipe an
   * evidence line already patched in by the same goroutine (and vice versa).
   */
  public int updateEnrichment(String accountId, String refId, String remediation, String evidenceLine) {
    try {
      if (refId == null || refId.isEmpty()) {
        logger.error("refId is required to update enrichment");
        return 0;
      }

      List<Bson> updates = new ArrayList<>();
      if (remediation != null && !remediation.isEmpty()) {
        updates.add(Updates.set("remediation", remediation));
      }
      if (evidenceLine != null && !evidenceLine.isEmpty()) {
        updates.add(Updates.set("evidenceLine", evidenceLine));
      }
      if (updates.isEmpty()) {
        return 0;
      }
      Bson update = Updates.combine(updates);

      Bson filters = Filters.eq("refId", refId);

      long modifiedCount = maliciousEventDao.getCollection(accountId)
          .updateOne(filters, update)
          .getModifiedCount();

      return (int) modifiedCount;
    } catch (Exception e) {
      logger.error("Error updating enrichment for refId: " + refId, e);
      return 0;
    }
  }

  /** Looks up an event's human-approval decision by refId. Null (not found) is normal — the async write may not have landed yet. */
  public MaliciousEventDto getApprovalStatus(String accountId, String refId) {
    if (refId == null || refId.isEmpty()) {
      logger.error("refId is required to fetch approval status");
      return null;
    }

    Bson filters = Filters.eq("refId", refId);
    return maliciousEventDao.getCollection(accountId).find(filters).first();
  }

  private <T> Set<T> findDistinctFields(
      String accountId, String fieldName, Class<T> tClass, Bson filters) {
    DistinctIterable<T> r = maliciousEventDao.getCollection(accountId).distinct(fieldName, filters, tClass);
    Set<T> result = new HashSet<>();
    MongoCursor<T> cursor = r.cursor();
    while (cursor.hasNext()) {
      T value = cursor.next();
      if (value != null) {
        result.add(value);
      }
    }
    return result;
  }

  public  ThreatActorFilterResponse fetchThreatActorFilters(
      String accountId, ThreatActorFilterRequest request) {

    // Use optimized actor_info table if feature flag is enabled
    if (USE_ACTOR_INFO_TABLE) {
      return fetchThreatActorFiltersFromActorInfo(accountId, request);
    } else {
      return fetchThreatActorFiltersFromMaliciousEvents(accountId, request);
    }
  }

  private ThreatActorFilterResponse fetchThreatActorFiltersFromActorInfo(
      String accountId, ThreatActorFilterRequest request) {

    ActorInfoDao actorInfoDao = ActorInfoDao.instance;

    Set<String> latestAttack = new HashSet<>();
    Set<String> countries = new HashSet<>();
    Set<String> actorIds = new HashSet<>();
    Set<String> hosts = new HashSet<>();

    // Use MongoDB distinct() for efficient field-level queries
    try {
      // Get distinct filterIds
      actorInfoDao.getCollection(accountId)
          .distinct("filterId", String.class)
          .forEach(value -> {
            if (value != null && !value.isEmpty()) {
              latestAttack.add(value);
            }
          });

      // Get distinct countries
      actorInfoDao.getCollection(accountId)
          .distinct("country", String.class)
          .forEach(value -> {
            if (value != null && !value.isEmpty()) {
              countries.add(value);
            }
          });

      // Get distinct actorIds
      actorInfoDao.getCollection(accountId)
          .distinct("actorId", String.class)
          .forEach(value -> {
            if (value != null && !value.isEmpty()) {
              actorIds.add(value);
            }
          });

      // Get distinct hosts
      actorInfoDao.getCollection(accountId)
          .distinct("host", String.class)
          .forEach(value -> {
            if (value != null && !value.isEmpty()) {
              hosts.add(value);
            }
          });
    } catch (Exception e) {
      // Log but return empty sets
      System.err.println("Error fetching filters from actor_info: " + e.getMessage());
    }

    return ThreatActorFilterResponse.newBuilder()
        .addAllSubCategories(latestAttack)
        .addAllCountries(countries)
        .addAllActorId(actorIds)
        .addAllHost(hosts)
        .build();
  }

  private ThreatActorFilterResponse fetchThreatActorFiltersFromMaliciousEvents(
      String accountId, ThreatActorFilterRequest request) {

    Set<String> latestAttack =
        this.findDistinctFields(accountId, "filterId", String.class, Filters.empty());

    Set<String> countries =
        this.findDistinctFields(accountId, "country", String.class, Filters.empty());

    Set<String> actorIds =
        this.findDistinctFields(accountId, "actor", String.class, Filters.empty());

    Set<String> hosts =
        this.findDistinctFields(accountId, "host", String.class, Filters.empty());

    return ThreatActorFilterResponse.newBuilder()
        .addAllSubCategories(latestAttack)
        .addAllCountries(countries)
        .addAllActorId(actorIds)
        .addAllHost(hosts)
        .build();
  }

  private Bson buildTimeRangeFilter(TimeRangeFilter timeRange, String dateField) {
    if (timeRange == null) {
      return Filters.empty();
    }
    long start = timeRange.hasStart() ? timeRange.getStart() : 0;
    long end = timeRange.hasEnd() ? timeRange.getEnd() : Long.MAX_VALUE;
    return Filters.and(
        Filters.gte(dateField, start),
        Filters.lte(dateField, end)
    );
  }

  private <T> Set<T> fetchDistinctFieldValues(
      String accountId, String fieldName, Bson filter, Class<T> tClass) {
    Set<T> result = new HashSet<>();
    try {
      maliciousEventDao.getCollection(accountId)
          .distinct(fieldName, filter, tClass)
          .forEach(value -> {
            if (value != null && isNotEmpty(value)) {
              result.add(value);
            }
          });
    } catch (Exception e) {
      logger.error("Error fetching distinct " + fieldName + ": " + e.getMessage(), e);
    }
    return result;
  }

  private boolean isNotEmpty(Object value) {
    if (value == null) return false;
    if (value instanceof String) return !((String) value).isEmpty();
    return true;
  }

  private <T> Set<T> fetchDistinctFieldValues(
      com.mongodb.client.MongoCollection<?> collection, String fieldName, Bson filter, Class<T> tClass, int limit) {
    Set<T> result = new HashSet<>();
    try {
      collection.distinct(fieldName, filter, tClass)
          .forEach(value -> {
            if (value != null && isNotEmpty(value) && result.size() < limit) {
              result.add(value);
            }
          });
    } catch (Exception e) {
      logger.error("Error fetching distinct " + fieldName + ": " + e.getMessage(), e);
    }
    return result;
  }

  public FetchAlertFiltersResponse fetchAlertFilters(
      String accountId, FetchAlertFiltersRequest request, String contextSource) {

    TimeRangeFilter timeRange = request.getDetectedAtTimeRange();
    long startTs = (timeRange != null && timeRange.hasStart()) ? timeRange.getStart() : 0;
    long endTs   = (timeRange != null && timeRange.hasEnd())   ? timeRange.getEnd()   : 0;

    String cacheKey = "alertFilters|fetch_filters|" + accountId + "|" + contextSource
        + "|" + DashboardFilterCache.bucketDay(startTs)
        + "|" + DashboardFilterCache.bucketDay(endTs);

    return alertFiltersCache.get(cacheKey, () -> computeAlertFilters(accountId, request, contextSource));
  }

  private FetchAlertFiltersResponse computeAlertFilters(
      String accountId, FetchAlertFiltersRequest request, String contextSource) {

    Document contextFilter = ThreatUtils.buildSimpleContextFilterNew(contextSource, accountId);
    Bson eventsFilter = Filters.and(
        buildTimeRangeFilter(request.getDetectedAtTimeRange(), "detectedAt"),
        contextFilter);

    // actor_info uses discoveredAt; all other queries use detectedAt via eventsFilter
    Bson actorFilter = USE_ACTOR_INFO_TABLE
        ? Filters.and(buildTimeRangeFilter(request.getDetectedAtTimeRange(), "discoveredAt"), contextFilter)
        : eventsFilter;

    Map<String, Callable<Set<String>>> tasks = new LinkedHashMap<>();
    tasks.put("actors",       USE_ACTOR_INFO_TABLE
        ? () -> fetchDistinctFieldValues(ActorInfoDao.instance.getCollection(accountId), "actorId", actorFilter, String.class, Integer.MAX_VALUE)
        : () -> fetchDistinctFieldValues(accountId, "actor", eventsFilter, String.class));
    tasks.put("subCategories", () -> fetchDistinctFieldValues(accountId, "filterId",          eventsFilter, String.class));
    tasks.put("hosts",         () -> fetchDistinctFieldValues(accountId, "host",              eventsFilter, String.class));
    tasks.put("urls",          () -> fetchDistinctFieldValues(maliciousEventDao.getCollection(accountId), "latestApiEndpoint", eventsFilter, String.class, 1000));

    Map<String, Set<String>> results = ParallelQueryExecutor.submitAndCollect(tasks, 30_000);

    Set<String> empty = Collections.emptySet();
    return FetchAlertFiltersResponse.newBuilder()
        .addAllActors(results.getOrDefault("actors", empty))
        .addAllSubCategory(results.getOrDefault("subCategories", empty))
        .addAllHosts(results.getOrDefault("hosts", empty))
        .addAllUrls(results.getOrDefault("urls", empty))
        .build();
  }

  public ListMaliciousRequestsResponse listMaliciousRequests(
      String accountId, ListMaliciousRequestsRequest request, String contextSource, String skillEvalMode, String configEvalMode) {
    return listMaliciousRequests(accountId, request, contextSource, skillEvalMode, configEvalMode, null);
  }

  public ListMaliciousRequestsResponse listMaliciousRequests(
      String accountId, ListMaliciousRequestsRequest request, String contextSource, String skillEvalMode, String configEvalMode, String humanResponseFilter) {

    if(!shouldNotCreateIndexes.getOrDefault(accountId, false)) {
      createIndexIfAbsent(accountId);
    }

    int limit = request.getLimit();
    int skip = request.hasSkip() ? request.getSkip() : 0;
    Map<String, Integer> sort = request.getSortMap();
    ListMaliciousRequestsRequest.Filter filter = request.getFilter();
    boolean minimalFields = request.hasMinimalFields() && request.getMinimalFields();
    List<String> excludedFields = new ArrayList<>();
    if (minimalFields) {
      excludedFields.addAll(Arrays.asList("metadata","latestApiOrig","payload","remediation", "evidenceLine", "humanResponse", "owaspCategories"));
    }

    Document query = new Document();
    if (!filter.getActorsList().isEmpty()) {
      query.append("actor", new Document("$in", filter.getActorsList()));
    }

    if (!filter.getUrlsList().isEmpty()) {
      query.append("latestApiEndpoint", new Document("$in", filter.getUrlsList()));
    }

    if (!filter.getApiCollectionIdList().isEmpty()) {
      query.append("latestApiCollectionId", new Document("$in", filter.getApiCollectionIdList()));
    }

    if (!filter.getMethodList().isEmpty()) {
      // Convert string methods to uppercase to match enum names stored in MongoDB
      List<String> methodStrings = filter.getMethodList().stream()
          .map(m -> m != null ? m.toUpperCase() : null)
          .filter(m -> m != null)
          .collect(Collectors.toList());
      if (!methodStrings.isEmpty()) {
        query.append("latestApiMethod", new Document("$in", methodStrings));
      }
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

    // Host attribution: exact match (hosts) OR loose device+service match (looseHostKeys, for
    // 2-vs-3-segment hostname format mismatches) OR claude-config scanner events (no collection of
    // their own, attributed by device id instead) — see the Filter.search_text/loose_host_keys/
    // claude_device_ids/match_claude_config proto doc comments for the client-side derivation this
    // mirrors (ViolationsTab.jsx's hostSet/looseHostSet/claudeDeviceIds).
    Document hostMatch = ThreatUtils.hostAttributionMatch(filter.getHostsList(), filter.getLooseHostKeysList(),
        filter.getClaudeDeviceIdsList(), filter.hasMatchClaudeConfig() && filter.getMatchClaudeConfig());
    List<Document> extraAndClauses = new ArrayList<>();
    if (hostMatch != null) {
      extraAndClauses.add(hostMatch);
    }
    if (filter.hasSearchText() && !filter.getSearchText().isEmpty()) {
      Pattern searchPattern = Pattern.compile(Pattern.quote(filter.getSearchText()), Pattern.CASE_INSENSITIVE);
      extraAndClauses.add(new Document("$or", Arrays.asList(
          new Document("filterId", searchPattern),
          new Document("host", searchPattern),
          new Document("actor", searchPattern)
      )));
    }
    if (!extraAndClauses.isEmpty()) {
      List<Document> allClauses = new ArrayList<>();
      allClauses.add(new Document(query));
      allClauses.addAll(extraAndClauses);
      query.clear();
      query.append("$and", allClauses);
    }

    if (filter.hasLatestApiOrigRegex() && !filter.getLatestApiOrigRegex().isEmpty()) {
      query.append("latestApiOrig", new Document("$regex", filter.getLatestApiOrigRegex()));
    }


    if (!filter.getLatestAttackList().isEmpty()) {
      query.append("filterId", new Document("$in", filter.getLatestAttackList()));
    }

    if (!filter.getSeverityList().isEmpty()) {
      query.append("severity", new Document("$in", filter.getSeverityList()));
    }

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

    applyRiskScoreFilter(query, filter);
    andDocument(query, agenticTypeCondition(filter.getAgenticTypesList()));
    andDocument(query, behaviourCondition(filter.getBehavioursList()));
    andDocument(query, caseOwnerCondition(filter.getCaseOwnersList()));
    andDocument(query, caseNotesCondition(filter.hasHasCaseNotes() ? filter.getHasCaseNotes() : null));

    // if (filter.hasLabel()) {
    //   String labelString = filter.getLabel();
    //   MaliciousEventDto.Label labelEnum = convertStringLabelToModelLabel(labelString);
    //   applyLabelFilter(query, labelEnum);
    // }

    // Apply simple context filter (only for ENDPOINT and AGENTIC)
    Document contextFilter = ThreatUtils.buildSimpleContextFilter(contextSource, accountId);
    if (!contextFilter.isEmpty()) {
      andDocument(query, contextFilter);
    }
    applyHumanResponseFilter(query, humanResponseFilter);

    // Skills Evaluations / Misconfigured Settings partitions (see ThreatUtils.evaluationModeConditions).
    // Done server-side so the total count and pagination stay correct.
    List<Document> evaluationModeConditions = ThreatUtils.evaluationModeConditions(contextSource, skillEvalMode, configEvalMode);
    if (!evaluationModeConditions.isEmpty()) {
      List<Document> andConditions = new ArrayList<>();
      andConditions.add(new Document(query));
      andConditions.addAll(evaluationModeConditions);
      query.clear();
      query.append("$and", andConditions);
    }

    // Check if sortBySeverity flag is set
    boolean sortBySeverity = filter.hasSortBySeverity() && filter.getSortBySeverity();
    boolean sortByRiskScore = sort.containsKey("riskScore");
    int riskScoreDir = sort.getOrDefault("riskScore", -1);

    long startTs = filter.hasDetectedAtTimeRange() && filter.getDetectedAtTimeRange().hasStart() ? filter.getDetectedAtTimeRange().getStart() : 0;
    long endTs   = filter.hasDetectedAtTimeRange() && filter.getDetectedAtTimeRange().hasEnd()   ? filter.getDetectedAtTimeRange().getEnd()   : 0;
    String statusFilter = filter.hasStatusFilter() ? filter.getStatusFilter() : ThreatDetectionConstants.ACTIVE;
    String countCacheKey = "maliciousCount|list_malicious_requests|" + accountId + "|" + contextSource + "|" + statusFilter
        + "|" + DashboardFilterCache.bucketDay(startTs)
        + "|" + DashboardFilterCache.bucketDay(endTs);

    // Misconfigured Settings tab: config-scanner findings get re-reported on every fsnotify/login
    // event for as long as the misconfiguration is unmitigated, producing many identical rows
    // (same host, same actor, same setting). Collapse to one row per {host, actor,
    // latestApiEndpoint} — latestApiEndpoint already identifies the specific misconfigured
    // setting (e.g. "/claude/config/skipDangerousModePermissionPrompt"), so it stands in for
    // "agentic component + rule violated" — keeping only the latest occurrence. A different actor
    // (username) on the same host/setting still gets its own row.
    boolean dedupeLatestPerHostActorEndpoint = "only".equalsIgnoreCase(configEvalMode)
        && "ENDPOINT".equalsIgnoreCase(contextSource);

    // Repeated Bedrock activities (same caller and prompt within the gap) are listed as one row.
    boolean groupActivities = ThreatUtils.groupsActivities(accountId) && "AGENTIC".equalsIgnoreCase(contextSource)
        && !dedupeLatestPerHostActorEndpoint && !(filter.hasUngroupActivities() && filter.getUngroupActivities());

    Document dedupeGroupKey = new Document("host", "$host")
        .append("actor", "$actor")
        .append("latestApiEndpoint", "$latestApiEndpoint");

    long total;
    MongoCursor<MaliciousEventDto> cursor;
    ActivityRows activityRows = groupActivities
        ? listActivityRows(accountId, query, sort, sortBySeverity, sortByRiskScore, riskScoreDir, excludedFields, skip, limit)
        : null;
    // $unset rejects an empty field list, so only add it when minimalFields populated excludedFields.
    List<Document> matchStages = new ArrayList<>();
    matchStages.add(new Document("$match", query));
    if (!excludedFields.isEmpty()) {
      matchStages.add(new Document("$unset", excludedFields));
    }
    if (dedupeLatestPerHostActorEndpoint) {
      MongoCursor<Document> countCursor = maliciousEventDao.aggregateRaw(accountId, Arrays.asList(
          new Document("$match", query),
          new Document("$group", new Document("_id", dedupeGroupKey)),
          new Document("$count", "total")
      )).cursor();
      total = countCursor.hasNext() ? ((Number) countCursor.next().get("total")).longValue() : 0;
      countCursor.close();

      List<Document> pipeline = new ArrayList<>(matchStages);
      pipeline.addAll(Arrays.asList(
          new Document("$sort", new Document("detectedAt", -1)),
          new Document("$group", new Document("_id", dedupeGroupKey).append("doc", new Document("$first", "$$ROOT"))),
          new Document("$replaceRoot", new Document("newRoot", "$doc"))
      ));
      if (sortByRiskScore) {
        pipeline.add(new Document("$addFields", riskScoreSortAddFields()));
        pipeline.add(new Document("$sort", new Document("riskScoreNum", riskScoreDir).append("detectedAt", -1)));
      } else {
        pipeline.add(new Document("$sort", new Document("detectedAt", sort.getOrDefault("detectedAt", -1))));
      }
      pipeline.add(new Document("$skip", skip));
      pipeline.add(new Document("$limit", limit));
      cursor = maliciousEventDao.getCollection(accountId).aggregate(pipeline).cursor();
    } else if (activityRows != null) {
      total = activityRows.total;
      cursor = activityRows.cursor;
    } else if (sortBySeverity) {
      total = maliciousEventDao.countDocuments(accountId, query);
      // Use aggregation pipeline for custom severity sorting
      List<Document> pipeline = new ArrayList<>(matchStages);
      pipeline.addAll(Arrays.asList(
              new Document("$addFields", severityRankAddFields()),
              new Document("$sort", new Document("severityRank", sort.getOrDefault("severity", 1))),
              new Document("$skip", skip),
              new Document("$limit", limit)
          ));
      cursor = maliciousEventDao.getCollection(accountId).aggregate(pipeline).cursor();
    } else if (sortByRiskScore) {
      total = maliciousEventDao.countDocuments(accountId, query);
      List<Document> pipeline = new ArrayList<>(matchStages);
      pipeline.addAll(Arrays.asList(
              new Document("$addFields", riskScoreSortAddFields()),
              new Document("$sort", new Document("riskScoreNum", riskScoreDir).append("detectedAt", -1)),
              new Document("$skip", skip),
              new Document("$limit", limit)
          ));
      cursor = maliciousEventDao.getCollection(accountId).aggregate(pipeline).cursor();
    } else {
      total = maliciousEventDao.countDocuments(accountId, query);
      cursor = maliciousEventDao.getCollection(accountId)
          .find(query)
          .projection(Projections.exclude(excludedFields))
          .sort(new Document("detectedAt", sort.getOrDefault("detectedAt", -1)))
          .skip(skip)
          .limit(limit)
          .cursor();
    }

    try {
      List<MaliciousEventDto> pageEvents = new ArrayList<>();
      while (cursor.hasNext()) {
        pageEvents.add(cursor.next());
      }

      // Session-id resolution is its own extra DB round trip (findExistingSessionIdentifiers) —
      // skipped entirely under minimal_fields, same reasoning as the metadata/owasp/etc. fields
      // below: a caller that only needs filterId/category/host has no use for it either.
      Set<String> validSessionIds = Collections.emptySet();
      if (!minimalFields) {
        Set<String> sessionIdsOnPage = pageEvents.stream()
            .map(MaliciousEventDto::getSessionId)
            .filter(id -> id != null && !id.isEmpty())
            .collect(Collectors.toSet());
        validSessionIds = AgenticSessionContextDao.instance.findExistingSessionIdentifiers(accountId, sessionIdsOnPage);
      }

      List<ListMaliciousRequestsResponse.MaliciousEvent> maliciousEvents = new ArrayList<>();
      for (MaliciousEventDto evt : pageEvents) {
        String metadata = minimalFields ? "" : ThreatUtils.fetchMetadataString(evt.getMetadata() != null ? evt.getMetadata() : "");
        String resolvedSessionId = (!minimalFields && evt.getSessionId() != null && validSessionIds.contains(evt.getSessionId()))
            ? evt.getSessionId() : "";

        ListMaliciousRequestsResponse.MaliciousEvent.Builder builder =
            ListMaliciousRequestsResponse.MaliciousEvent.newBuilder()
                .setActor(evt.getActor())
                .setFilterId(evt.getFilterId())
                .setId(evt.getId())
                .setIp(evt.getLatestApiIp())
                .setCountry(evt.getCountry())
                .setDestCountry(evt.getDestCountry() != null ? evt.getDestCountry() : "")
                .setPayload("")
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
                .setSeverity(evt.getSeverity() != null ? evt.getSeverity() : "HIGH")
                .setSessionId(resolvedSessionId);
        if (evt.getGroupKey() != null && evt.getGroupCount() != null) {
          builder.setGroupCount(evt.getGroupCount())
              .setGroupFirstDetectedAt(evt.getGroupFirstDetectedAt() != null ? evt.getGroupFirstDetectedAt() : evt.getDetectedAt());
          if (evt.getGroupEventIds() != null) {
            builder.addAllGroupEventIds(evt.getGroupEventIds());
          }
          if (evt.getGroupFilterIds() != null) {
            builder.addAllGroupFilterIds(evt.getGroupFilterIds());
          }
        }
        if (!minimalFields) {
          builder.setRemediation(evt.getRemediation() != null ? evt.getRemediation() : "")
              .setEvidenceLine(evt.getEvidenceLine() != null ? evt.getEvidenceLine() : "")
              .setHumanResponse(evt.getHumanResponse() != null ? evt.getHumanResponse() : "")
              .setCaseOwner(evt.getCaseOwner() != null ? evt.getCaseOwner() : "")
              .setCaseNotes(evt.getCaseNotes() != null ? evt.getCaseNotes() : "")
              .addAllCaseTimeline(ThreatUtils.caseActivities(evt))
              .addAllOwaspCategories(evt.getOwaspCategories() != null
                  ? evt.getOwaspCategories().stream()
                      .map(o -> OwaspCategory.newBuilder()
                          .setId(o.getId() != null ? o.getId() : "")
                          .setName(o.getName() != null ? o.getName() : "")
                          .setSeverity(o.getSeverity() != null ? o.getSeverity() : "")
                          .setConfidence(o.getConfidence() != null ? o.getConfidence() : "")
                          .build())
                      .collect(Collectors.toList())
                  : Collections.emptyList());
        }
        maliciousEvents.add(builder.build());
      }
      return ListMaliciousRequestsResponse.newBuilder()
          .setTotal(total)
          .addAllMaliciousEvents(maliciousEvents)
          .build();
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }
  }

  /** Fallback when the caller sends no limit (0/unset). */
  private static final int GUARDRAIL_VIOLATION_PAYLOADS_DEFAULT_LIMIT = 50;
  /** Hard cap regardless of what the caller asks for — an internal LLM scan job paging through
   *  this endpoint should never be able to pull an unbounded page (and, by extension, an
   *  unbounded amount of payload text) into memory in one call. */
  private static final int GUARDRAIL_VIOLATION_PAYLOADS_MAX_LIMIT = 500;

  // Batched, cursor-paginated fetch of the raw request/response payload (latestApiOrig) behind a
  // set of guardrail-policy violations. listMaliciousRequests cannot serve this: its response
  // hard-codes payload to "" for every row (see .setPayload("") below), and adding a payload field
  // to that request risks an unrecognized-field parse failure on an un-redeployed caller (see the
  // proto comment on ListGuardrailViolationPayloadsRequest). Sorted/paginated on the raw _id in
  // either direction (request.newestFirst) — filterId does not appear in an existing _id-ordered
  // index, but this endpoint is used by an internal LLM scan job, not an interactive list view, so
  // a collection scan bounded by contextSource + filterId + detectedAt (existing
  // contextSource_1_filterId_1_detectedAt_-1 index) followed by an in-memory _id sort over the
  // matched page is an acceptable cost; add an {contextSource, filterId, _id} index if this needs
  // to scale further.
  /** Cursor encoding for listGuardrailViolationPayloads: "<detectedAt>|<_id>". Not an ObjectId —
   *  see that method's own comment on why. "|" is safe as a separator: detectedAt is numeric and
   *  _id is a UUID string (neither can contain it). */
  private static final String GUARDRAIL_VIOLATION_PAYLOADS_CURSOR_SEPARATOR = "|";

  public ListGuardrailViolationPayloadsResponse listGuardrailViolationPayloads(
      String accountId, ListGuardrailViolationPayloadsRequest request, String contextSource) {

    if (!shouldNotCreateIndexes.getOrDefault(accountId, false)) {
      createIndexIfAbsent(accountId);
    }

    int requestedLimit = request.getLimit() > 0
        ? request.getLimit() : GUARDRAIL_VIOLATION_PAYLOADS_DEFAULT_LIMIT;
    int limit = Math.min(requestedLimit, GUARDRAIL_VIOLATION_PAYLOADS_MAX_LIMIT);
    List<String> filterIds = request.getFilterIdsList();

    Document query = ThreatUtils.buildSimpleContextFilterNew(contextSource, accountId);
    if (!filterIds.isEmpty()) {
      query.append("filterId", new Document("$in", filterIds));
    }
    // Only rows that actually carry a payload are useful to the LLM attribution step.
    query.append("latestApiOrig", new Document("$exists", true).append("$ne", ""));

    if (request.hasDetectedAtTimeRange()) {
      TimeRangeFilter timeRange = request.getDetectedAtTimeRange();
      long start = timeRange.hasStart() ? timeRange.getStart() : 0;
      long end = timeRange.hasEnd() ? timeRange.getEnd() : Long.MAX_VALUE;
      query.append("detectedAt", new Document("$gte", start).append("$lte", end));
    }

    // Oldest-first by default (what an exhaustive paging scan needs, so no row is skipped as new
    // ones arrive between pages) — flip to newest-first on request.
    //
    // Cursor is on (detectedAt, _id), NOT _id alone: MaliciousEventDto#id is a
    // UUID.randomUUID().toString() that the POJO codec auto-maps to _id (no @BsonId ObjectId
    // override), so _id in this collection is a random string with no chronological meaning —
    // sorting/range-filtering on it alone would produce an arbitrary, unstable page order, not the
    // "walk forward without skipping a row" guarantee this endpoint exists for. detectedAt gives
    // the real ordering; _id is only a tiebreaker for the (common) case of several events sharing a
    // timestamp, using the standard keyset-pagination "$gt this OR ($eq this AND $gt that)" shape.
    boolean newestFirst = request.hasNewestFirst() && request.getNewestFirst();
    String cmp = newestFirst ? "$lt" : "$gt";

    String cursorParam = request.hasCursor() ? request.getCursor() : null;
    if (cursorParam != null && !cursorParam.isEmpty()) {
      int sep = cursorParam.indexOf(GUARDRAIL_VIOLATION_PAYLOADS_CURSOR_SEPARATOR);
      if (sep < 0) {
        logger.error("Malformed cursor for listGuardrailViolationPayloads: " + cursorParam);
        return ListGuardrailViolationPayloadsResponse.newBuilder().build();
      }
      try {
        long cursorDetectedAt = Long.parseLong(cursorParam.substring(0, sep));
        String cursorId = cursorParam.substring(sep + 1);
        query.append("$or", Arrays.asList(
            new Document("detectedAt", new Document(cmp, cursorDetectedAt)),
            new Document("detectedAt", cursorDetectedAt)
                .append("_id", new Document(cmp, cursorId))
        ));
      } catch (NumberFormatException e) {
        logger.error("Malformed cursor for listGuardrailViolationPayloads: " + cursorParam);
        return ListGuardrailViolationPayloadsResponse.newBuilder().build();
      }
    }

    Bson sort = newestFirst
        ? Sorts.orderBy(Sorts.descending("detectedAt"), Sorts.descending("_id"))
        : Sorts.orderBy(Sorts.ascending("detectedAt"), Sorts.ascending("_id"));

    List<ListGuardrailViolationPayloadsResponse.ViolationPayload> payloads = new ArrayList<>();
    MongoCursor<Document> cursor = null;
    try {
      cursor = maliciousEventDao.getDocumentCollection(accountId)
          .find(query)
          .projection(Projections.include("_id", "refId", "filterId", "detectedAt", "latestApiOrig"))
          .sort(sort)
          .limit(limit)
          .cursor();

      while (cursor.hasNext()) {
        Document doc = cursor.next();
        String orig = HttpResponseParams.getSampleStringFromProtoString(doc.getString("latestApiOrig"));
        Object detectedAtRaw = doc.get("detectedAt");
        long detectedAt = detectedAtRaw instanceof Number ? ((Number) detectedAtRaw).longValue() : 0L;
        // _id may be legitimately typed as ObjectId or String depending on how a given row was
        // inserted — read it generically rather than assuming one BSON type (see this method's own
        // comment above on why it's a String in practice for this DTO).
        Object idRaw = doc.get("_id");
        String id = idRaw != null ? idRaw.toString() : "";
        payloads.add(ListGuardrailViolationPayloadsResponse.ViolationPayload.newBuilder()
            .setRefId(doc.getString("refId") != null ? doc.getString("refId") : "")
            .setFilterId(doc.getString("filterId") != null ? doc.getString("filterId") : "")
            .setDetectedAt(detectedAt)
            .setOrig(orig != null ? orig : "")
            .setCursor(detectedAt + GUARDRAIL_VIOLATION_PAYLOADS_CURSOR_SEPARATOR + id)
            .build());
      }
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }

    return ListGuardrailViolationPayloadsResponse.newBuilder()
        .addAllPayloads(payloads)
        .build();
  }

  // metadata is stored as proto-text (`risk_score: "0.95"`) or JSON (`"riskScore": "0.95"`).
  private static final String RISK_SCORE_EXTRACT_REGEX = "(?:risk_score|riskScore)\\s*[:=]\\s*\"([0-9]*\\.?[0-9]+)\"";

  private static final class ActivityRows {
    final long total;
    final MongoCursor<MaliciousEventDto> cursor;

    ActivityRows(long total, MongoCursor<MaliciousEventDto> cursor) {
      this.total = total;
      this.cursor = cursor;
    }
  }

  // Rows for an account that groups activities. Null if the grouped query fails, so the caller lists
  // events one per row as it always has instead of showing nothing.
  private ActivityRows listActivityRows(String accountId, Document query, Map<String, Integer> sort, boolean sortBySeverity,
      boolean sortByRiskScore, int riskScoreDir, List<String> excludedFields, int skip, int limit) {
    try {
      // Keyed events are folded into activity rows; the rest stay one row each and come straight off
      // the indexes. Each side is cut to the page window before the two are merged and paged.
      Document keyed = new Document("$and", Arrays.asList(query, new Document("groupKey", new Document("$type", "string"))));
      Document unkeyed = new Document("$and", Arrays.asList(query, new Document("groupKey", new Document("$not", new Document("$type", "string")))));
      List<Document> rowStages = ThreatUtils.activityGroupStages(keyed);
      // The planner prefers the contextSource indexes and then reads every event; keyed events are a
      // small slice of the collection, so their own partial index is far cheaper.
      Bson keyedIndex = ThreatUtils.ACTIVITY_GROUP_INDEX_KEYS;
      MongoCollection<MaliciousEventDto> events = maliciousEventDao.getCollection(accountId);

      List<Document> countPipeline = new ArrayList<>(rowStages);
      countPipeline.add(new Document("$count", "total"));
      MongoCursor<Document> countCursor = events
          .aggregate(countPipeline, Document.class).hint(keyedIndex).allowDiskUse(true).cursor();
      long keyedRows = countCursor.hasNext() ? ((Number) countCursor.next().get("total")).longValue() : 0;
      countCursor.close();
      // "No groupKey" can't use an index; all events minus keyed events can.
      long keyedEvents = events.countDocuments(keyed, new CountOptions().hint(keyedIndex));
      long total = keyedRows + maliciousEventDao.countDocuments(accountId, query) - keyedEvents;

      List<Document> sortStages = new ArrayList<>();
      if (sortBySeverity) {
        sortStages.add(new Document("$addFields", severityRankAddFields()));
        sortStages.add(new Document("$sort", new Document("severityRank", sort.getOrDefault("severity", 1)).append("detectedAt", -1)));
      } else if (sortByRiskScore) {
        sortStages.add(new Document("$addFields", riskScoreSortAddFields()));
        sortStages.add(new Document("$sort", new Document("riskScoreNum", riskScoreDir).append("detectedAt", -1)));
      } else {
        int dir = sort.getOrDefault("detectedAt", -1);
        // _id breaks ties so pages stay stable when several rows share a detectedAt.
        sortStages.add(new Document("$sort", new Document("detectedAt", dir).append("_id", dir)));
      }
      List<String> dropped = new ArrayList<>(excludedFields);
      if (!dropped.contains("latestApiOrig")) {
        dropped.add("latestApiOrig");
      }
      Document pageWindow = new Document("$limit", skip + limit);

      List<Document> unkeyedPage = new ArrayList<>();
      unkeyedPage.add(new Document("$match", unkeyed));
      unkeyedPage.addAll(sortStages);
      unkeyedPage.add(pageWindow);
      unkeyedPage.add(new Document("$unset", dropped));

      // Keyed rows are the outer pipeline so the index hint applies to them.
      List<Document> pipeline = new ArrayList<>(rowStages);
      pipeline.addAll(sortStages);
      pipeline.add(pageWindow);
      pipeline.add(new Document("$unset", dropped));
      pipeline.add(new Document("$unionWith", new Document("coll", events.getNamespace().getCollectionName())
          .append("pipeline", unkeyedPage)));
      pipeline.addAll(sortStages);
      pipeline.add(new Document("$skip", skip));
      pipeline.add(new Document("$limit", limit));
      MongoCursor<MaliciousEventDto> cursor = events.aggregate(pipeline).hint(keyedIndex).allowDiskUse(true).cursor();
      return new ActivityRows(total, cursor);
    } catch (Exception e) {
      logger.error("listActivityRows failed for accountId=" + accountId + ", listing ungrouped: " + e.getMessage());
      return null;
    }
  }

  // severityRank: CRITICAL=1 .. LOW=4, anything else 5, for the custom severity sort.
  private static Document severityRankAddFields() {
    return new Document("severityRank", new Document("$switch", new Document()
        .append("branches", Arrays.asList(
            new Document("case", new Document("$eq", Arrays.asList("$severity", "CRITICAL"))).append("then", 1),
            new Document("case", new Document("$eq", Arrays.asList("$severity", "HIGH"))).append("then", 2),
            new Document("case", new Document("$eq", Arrays.asList("$severity", "MEDIUM"))).append("then", 3),
            new Document("case", new Document("$eq", Arrays.asList("$severity", "LOW"))).append("then", 4)))
        .append("default", 5)));
  }

  private static Document riskScoreSortAddFields() {
    Document regexFind = new Document("$regexFind",
        new Document("input", new Document("$ifNull", Arrays.asList("$metadata", "")))
            .append("regex", RISK_SCORE_EXTRACT_REGEX));
    Document parsedScore = new Document("$cond", Arrays.asList(
        new Document("$eq", Arrays.asList("$$m", null)),
        -1,
        new Document("$convert", new Document("input",
            new Document("$arrayElemAt", Arrays.asList("$$m.captures", 0)))
            .append("to", "double")
            .append("onError", -1)
            .append("onNull", -1))
    ));
    Document letExpr = new Document("$let",
        new Document("vars", new Document("m", regexFind)).append("in", parsedScore));
    return new Document("riskScoreNum", letExpr);
  }

  // Order is precedence, mirroring deriveAgenticType in the dashboard's violationsData.js.
  private static final String[][] AGENTIC_TYPE_RULES = {
      {"Tool", "tool"}, {"Skill", "skill"}, {"Resource", "resource"}, {"Prompt", "prompt"},
      {"Config", "config|setting"}, {"Tool", "mcp|server"}, {"Prompt", "message|completion|chat"}
  };
  private Document agenticTypeCondition(List<String> types) {
    if (types == null || types.isEmpty()) {
      return null;
    }
    Set<String> wanted = new HashSet<>(types);
    List<Document> anyOf = new ArrayList<>();
    StringBuilder earlier = new StringBuilder();
    for (String[] rule : AGENTIC_TYPE_RULES) {
      if (wanted.contains(rule[0])) {
        List<Document> allOf = new ArrayList<>();
        allOf.add(new Document("latestApiEndpoint", Pattern.compile(rule[1], Pattern.CASE_INSENSITIVE)));
        if (earlier.length() > 0) {
          allOf.add(new Document("latestApiEndpoint",
              new Document("$not", Pattern.compile(earlier.toString(), Pattern.CASE_INSENSITIVE))));
        }
        anyOf.add(new Document("$and", allOf));
      }
      earlier.append(earlier.length() > 0 ? "|" : "").append("(?:").append(rule[1]).append(")");
    }
    if (wanted.contains("Prompt")) {
      anyOf.add(new Document("latestApiEndpoint",
          new Document("$not", Pattern.compile(earlier.toString(), Pattern.CASE_INSENSITIVE))));
    }
    return anyOf.isEmpty() ? null : new Document("$or", anyOf);
  }

  private Document behaviourCondition(List<String> behaviours) {
    if (behaviours == null || behaviours.isEmpty()) {
      return null;
    }
    List<Document> anyOf = new ArrayList<>();
    for (String b : behaviours) {
      if ("flag".equalsIgnoreCase(b)) {
        // The UI shows a warn, or an event with no behaviour, as Flagged.
        anyOf.add(new Document("metadata", Pattern.compile("behaviour\"?\\s*:\\s*\"(warn|flag)\"")));
        anyOf.add(new Document("metadata", new Document("$not", Pattern.compile("behaviour"))));
      } else {
        anyOf.add(new Document("metadata", Pattern.compile("behaviour\"?\\s*:\\s*\"" + Pattern.quote(b.toLowerCase()) + "\"")));
      }
    }
    return new Document("$or", anyOf);
  }

  // Owners to match; UNASSIGNED_OWNER stands for "no owner" (field missing, null or empty).
  private Document caseOwnerCondition(List<String> owners) {
    if (owners == null || owners.isEmpty()) {
      return null;
    }
    List<Object> match = new ArrayList<>();
    for (String owner : owners) {
      if (UNASSIGNED_OWNER.equals(owner)) {
        match.add(null);
        match.add("");
      } else if (owner != null && !owner.isEmpty()) {
        match.add(owner);
      }
    }
    return match.isEmpty() ? null : new Document(MaliciousEventDto.CASE_OWNER, new Document("$in", match));
  }

  // true: events with notes; false: events without (field missing, null or empty).
  private Document caseNotesCondition(Boolean hasNotes) {
    if (hasNotes == null) {
      return null;
    }
    List<Object> none = Arrays.asList(null, "");
    return new Document(MaliciousEventDto.CASE_NOTES, new Document(hasNotes ? "$nin" : "$in", none));
  }

  private void applyRiskScoreFilter(Document query, ListMaliciousRequestsRequest.Filter filter) {
    if (!filter.hasRiskScoreFilterType() || filter.getRiskScoreFilterType().isEmpty()) {
      return;
    }
    Document expr = buildRiskScoreExpr(filter);
    if (expr == null) {
      return;
    }
    List<Document> andConditions = new ArrayList<>();
    andConditions.add(new Document(query));
    andConditions.add(new Document("$expr", expr));
    query.clear();
    query.append("$and", andConditions);
  }

  private Document buildRiskScoreExpr(ListMaliciousRequestsRequest.Filter filter) {
    String type = filter.getRiskScoreFilterType();
    Document regexFind = new Document("$regexFind",
        new Document("input", new Document("$ifNull", Arrays.asList("$metadata", "")))
            .append("regex", RISK_SCORE_EXTRACT_REGEX));
    Document parsedScore = new Document("$cond", Arrays.asList(
        new Document("$eq", Arrays.asList("$$m", null)),
        null,
        new Document("$convert", new Document("input",
            new Document("$arrayElemAt", Arrays.asList("$$m.captures", 0)))
            .append("to", "double")
            .append("onError", null)
            .append("onNull", null))
    ));

    Document inExpr;
    switch (type) {
      case "blank":
        inExpr = new Document("$eq", Arrays.asList("$$m", null));
        break;
      case "notBlank":
        inExpr = new Document("$ne", Arrays.asList("$$m", null));
        break;
      case "equals":
        if (!filter.hasRiskScoreFilterValue()) return null;
        inExpr = new Document("$eq", Arrays.asList(parsedScore, filter.getRiskScoreFilterValue()));
        break;
      case "notEqual":
        if (!filter.hasRiskScoreFilterValue()) return null;
        inExpr = new Document("$and", Arrays.asList(
            new Document("$ne", Arrays.asList(parsedScore, null)),
            new Document("$ne", Arrays.asList(parsedScore, filter.getRiskScoreFilterValue()))
        ));
        break;
      case "greaterThan":
        if (!filter.hasRiskScoreFilterValue()) return null;
        inExpr = new Document("$gt", Arrays.asList(parsedScore, filter.getRiskScoreFilterValue()));
        break;
      case "greaterThanOrEqual":
        if (!filter.hasRiskScoreFilterValue()) return null;
        inExpr = new Document("$gte", Arrays.asList(parsedScore, filter.getRiskScoreFilterValue()));
        break;
      case "lessThan":
        if (!filter.hasRiskScoreFilterValue()) return null;
        inExpr = new Document("$lt", Arrays.asList(parsedScore, filter.getRiskScoreFilterValue()));
        break;
      case "lessThanOrEqual":
        if (!filter.hasRiskScoreFilterValue()) return null;
        inExpr = new Document("$lte", Arrays.asList(parsedScore, filter.getRiskScoreFilterValue()));
        break;
      default:
        return null;
    }

    return new Document("$let", new Document("vars", new Document("m", regexFind)).append("in", inExpr));
  }

  // Gives stored activities the current groupKey, so they group like new ones: events from before
  // grouping existed, and events keyed by an earlier version of the key. Only events whose key changes
  // are written, so running it again is safe; undo with $unset groupKey.
  public int backfillActivityGroupKeys(String accountId) {
    if (!ThreatUtils.groupsActivities(accountId)) {
      return 0;
    }
    createIndexIfAbsent(accountId);
    MongoCollection<Document> events = maliciousEventDao.getCollection(accountId).withDocumentClass(Document.class);
    Document bedrockEvents = new Document("latestApiOrig", new Document("$regex", "bedrock-identity-arn"));
    int updated = 0;
    List<WriteModel<Document>> batch = new ArrayList<>();
    try (MongoCursor<Document> cursor = events.find(bedrockEvents).projection(Projections.include("_id", "latestApiOrig", "groupKey")).batchSize(500).cursor()) {
      while (cursor.hasNext()) {
        Document event = cursor.next();
        String groupKey = ThreatUtils.activityGroupKey(event.getString("latestApiOrig"));
        if (groupKey == null || groupKey.equals(event.getString("groupKey"))) {
          continue;
        }
        batch.add(new UpdateOneModel<>(Filters.eq("_id", event.get("_id")), Updates.set("groupKey", groupKey)));
        if (batch.size() >= 500) {
          updated += events.bulkWrite(batch).getModifiedCount();
          batch.clear();
        }
      }
    }
    if (!batch.isEmpty()) {
      updated += events.bulkWrite(batch).getModifiedCount();
    }
    logger.info("backfillActivityGroupKeys accountId=" + accountId + " updated=" + updated);
    return updated;
  }

  public void createIndexIfAbsent(String accountId) {
    ThreatUtils.createIndexIfAbsent(accountId, maliciousEventDao);
    shouldNotCreateIndexes.put(accountId, true);
  }

  public int updateMaliciousEventStatus(String accountId, List<String> eventIds, Map<String, Object> filterMap, String status, String jiraTicketUrl, String contextSource) {
    return updateMaliciousEventStatus(accountId, eventIds, filterMap, status, jiraTicketUrl, contextSource, null);
  }

  public int updateMaliciousEventStatus(String accountId, List<String> eventIds, Map<String, Object> filterMap, String status, String jiraTicketUrl, String contextSource, String humanResponse) {
    return updateMaliciousEventStatus(accountId, eventIds, filterMap, status, jiraTicketUrl, contextSource, humanResponse, null, null, null);
  }

  /**
   * caseOwner / caseNotes: null = leave as is, "" = clear. A change to status, owner or notes that actually
   * takes effect on an event is added to its caseTimeline under updatedBy, so the history is complete whichever
   * screen made the change. Jira and human-response updates are not part of the case history.
   */
  public int updateMaliciousEventStatus(String accountId, List<String> eventIds, Map<String, Object> filterMap, String status, String jiraTicketUrl, String contextSource, String humanResponse, String caseOwner, String caseNotes, String updatedBy) {
    try {
      boolean statusGiven = status != null && !status.isEmpty();
      if (statusGiven || caseOwner != null || caseNotes != null) {
        return updateWithCaseHistory(accountId, eventIds, filterMap, status, jiraTicketUrl, contextSource, humanResponse, caseOwner, caseNotes, updatedBy);
      }

      List<Bson> updateOps = new ArrayList<>();

      if(status != null && !status.isEmpty()) {
        MaliciousEventDto.Status eventStatus = MaliciousEventDto.Status.valueOf(status.toUpperCase());
        updateOps.add(Updates.set("status", eventStatus.toString()));
      }
      if (jiraTicketUrl != null && !jiraTicketUrl.isEmpty()) {
        updateOps.add(Updates.set("jiraTicketUrl", jiraTicketUrl));
      }
      if (humanResponse != null && !humanResponse.isEmpty()) {
        MaliciousEventDto.HumanResponse parsed = MaliciousEventDto.HumanResponse.valueOf(humanResponse.toUpperCase());
        updateOps.add(Updates.set(MaliciousEventDto.HUMAN_RESPONSE, parsed.name()));
      }
      if (updateOps.isEmpty()) {
        return 0;
      }
      Bson update = updateOps.size() == 1 ? updateOps.get(0) : Updates.combine(updateOps);

      Document query = buildQuery(eventIds, filterMap, "update", contextSource, accountId);
      if (query == null) {
        return 0;
      }
      if (humanResponse != null && !humanResponse.isEmpty()) {
        query.append("status", ThreatDetectionConstants.HUMAN_APPROVAL);
        applyHumanResponseFilter(query, MaliciousEventDto.HumanResponse.PENDING.name());
      }

      String logMessage = String.format("Updating events %s to status: %s jiraTicketUrl: %s humanResponse: %s",
          getQueryDescription(eventIds, filterMap, accountId), status,
          jiraTicketUrl != null && !jiraTicketUrl.isEmpty() ? jiraTicketUrl : "null",
          humanResponse != null && !humanResponse.isEmpty() ? humanResponse : "null");
      logger.info(logMessage);

      long modifiedCount = maliciousEventDao.getCollection(accountId).updateMany(query, update).getModifiedCount();
      return (int) modifiedCount;
    } catch (Exception e) {
      logger.error("Error updating malicious event status", e);
      return 0;
    }
  }

  // The owner an event filter uses for "no owner".
  public static final String UNASSIGNED_OWNER = "__unassigned__";
  private static final int CASE_TIMELINE_MAX_ENTRIES = 10;
  private static final int CASE_NOTES_MAX_LENGTH = 5000;

  // Wraps a user-supplied value so a pipeline stage never reads it as an expression or field path ("$100 refund").
  private static Document literal(Object value) {
    return new Document("$literal", value);
  }

  // [entry] when `changed`, else [] — the term for one kind of change in the timeline concatenation.
  private static Document timelineEntryIf(Document changed, long timestamp, String updatedBy, String activityType, String status, String owner, String notes) {
    Document entry = new Document(MaliciousEventDto.CaseActivity.TIMESTAMP, timestamp)
        .append(MaliciousEventDto.CaseActivity.UPDATED_BY, updatedBy)
        .append(MaliciousEventDto.CaseActivity.ACTIVITY_TYPE, activityType)
        .append(MaliciousEventDto.CaseActivity.STATUS, status)
        .append(MaliciousEventDto.CaseActivity.OWNER, owner)
        .append(MaliciousEventDto.CaseActivity.NOTES, notes);
    return new Document("$cond", Arrays.asList(changed, Arrays.asList(literal(entry)), Collections.emptyList()));
  }

  private int updateWithCaseHistory(String accountId, List<String> eventIds, Map<String, Object> filterMap, String status, String jiraTicketUrl, String contextSource, String humanResponse, String caseOwner, String caseNotes, String updatedBy) {
    Document query = buildQuery(eventIds, filterMap, "update", contextSource, accountId);
    if (query == null) {
      return 0;
    }
    if (humanResponse != null && !humanResponse.isEmpty()) {
      query.append("status", ThreatDetectionConstants.HUMAN_APPROVAL);
      applyHumanResponseFilter(query, MaliciousEventDto.HumanResponse.PENDING.name());
    }

    long now = com.akto.dao.context.Context.now();
    String by = updatedBy == null ? "" : updatedBy.trim();   // who the entries are recorded under
    Document set = new Document();
    List<Object> changes = new ArrayList<>();

    if (status != null && !status.isEmpty()) {
      String newStatus = MaliciousEventDto.Status.valueOf(status.toUpperCase()).toString();
      set.append("status", literal(newStatus));
      Document changed = new Document("$ne", Arrays.asList(new Document("$ifNull", Arrays.asList("$status", ThreatDetectionConstants.ACTIVE)), literal(newStatus)));
      changes.add(timelineEntryIf(changed, now, by, MaliciousEventDto.CaseActivity.ACTIVITY_TYPE_STATUS, newStatus, "", ""));
    }
    if (caseOwner != null) {
      String newOwner = caseOwner.trim();
      set.append(MaliciousEventDto.CASE_OWNER, literal(newOwner));
      Document changed = new Document("$ne", Arrays.asList(new Document("$ifNull", Arrays.asList("$" + MaliciousEventDto.CASE_OWNER, "")), literal(newOwner)));
      changes.add(timelineEntryIf(changed, now, by, MaliciousEventDto.CaseActivity.ACTIVITY_TYPE_OWNER, "", newOwner, ""));
    }
    if (caseNotes != null) {
      String newNotes = caseNotes.length() > CASE_NOTES_MAX_LENGTH ? caseNotes.substring(0, CASE_NOTES_MAX_LENGTH) : caseNotes;
      set.append(MaliciousEventDto.CASE_NOTES, literal(newNotes));
      Document changed = new Document("$ne", Arrays.asList(new Document("$ifNull", Arrays.asList("$" + MaliciousEventDto.CASE_NOTES, "")), literal(newNotes)));
      changes.add(timelineEntryIf(changed, now, by, MaliciousEventDto.CaseActivity.ACTIVITY_TYPE_NOTES, "", "", newNotes));
    }
    if (jiraTicketUrl != null && !jiraTicketUrl.isEmpty()) {
      set.append("jiraTicketUrl", literal(jiraTicketUrl));
    }
    if (humanResponse != null && !humanResponse.isEmpty()) {
      set.append(MaliciousEventDto.HUMAN_RESPONSE, literal(MaliciousEventDto.HumanResponse.valueOf(humanResponse.toUpperCase()).name()));
    }

    // Stage 1 reads the old values to decide which changes are real and appends them (keeping the newest
    // entries); stage 2 writes the new values. The timeline is left alone, even absent, when nothing changed.
    Document newEntries = new Document("$concatArrays", changes);
    Document timeline = new Document("$cond", Arrays.asList(
        new Document("$eq", Arrays.asList(new Document("$size", newEntries), 0)),
        "$" + MaliciousEventDto.CASE_TIMELINE,
        new Document("$slice", Arrays.asList(
            new Document("$concatArrays", Arrays.asList(new Document("$ifNull", Arrays.asList("$" + MaliciousEventDto.CASE_TIMELINE, Collections.emptyList())), newEntries)),
            -CASE_TIMELINE_MAX_ENTRIES))));
    List<Document> pipeline = Arrays.asList(
        new Document("$set", new Document(MaliciousEventDto.CASE_TIMELINE, timeline)),
        new Document("$set", set));

    logger.info(String.format("Updating events %s to status: %s caseOwner: %s caseNotes: %s by: %s",
        getQueryDescription(eventIds, filterMap, accountId), status,
        caseOwner == null ? "unchanged" : "'" + caseOwner + "'", caseNotes == null ? "unchanged" : "(" + caseNotes.length() + " chars)", by));

    return (int) maliciousEventDao.getCollection(accountId).updateMany(query, pipeline).getModifiedCount();
  }

  public int deleteMaliciousEvents(String accountId, List<String> eventIds, Map<String, Object> filterMap, String contextSource) {
    try {
      Document query = buildQuery(eventIds, filterMap, "delete", contextSource, accountId);
      if (query == null) {
        return 0;
      }

      String logMessage = "Deleting events " + getQueryDescription(eventIds, filterMap, accountId);
      logger.info(logMessage);

      long deletedCount = maliciousEventDao.getCollection(accountId).deleteMany(query).getDeletedCount();
      logger.info("Deleted " + deletedCount + " malicious events");

      return (int) deletedCount;
    } catch (Exception e) {
      logger.error("Error deleting malicious events", e);
      return 0;
    }
  }

  private Document buildQuery(List<String> eventIds, Map<String, Object> filterMap, String operation, String contextSource, String accountId) {
    if (eventIds != null && !eventIds.isEmpty()) {
      // Query by event IDs
      return new Document("_id", new Document("$in", eventIds));
    } else if (filterMap != null && !filterMap.isEmpty()) {
      // Query by filter criteria
      return buildQueryFromFilter(filterMap, contextSource, accountId);
    } else {
      logger.warn("Neither eventIds nor filterMap provided for " + operation);
      return null;
    }
  }

  private String getQueryDescription(List<String> eventIds, Map<String, Object> filterMap, String accountId) {
    if (eventIds != null && !eventIds.isEmpty()) {
      return "by IDs: " + eventIds;
    } else if (filterMap != null && !filterMap.isEmpty()) {
      Document query = buildQueryFromFilter(filterMap, null, accountId);
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
    } else if (ThreatDetectionConstants.TRAINING.equals(statusFilter)) {
      query.append("status", ThreatDetectionConstants.TRAINING);
    } else if (ThreatDetectionConstants.HUMAN_APPROVAL.equals(statusFilter)) {
      query.append("status", ThreatDetectionConstants.HUMAN_APPROVAL);
    } else if (ThreatDetectionConstants.ACTIVE.equals(statusFilter) || ThreatDetectionConstants.EVENTS_FILTER.equals(statusFilter)) {
      query.append("status", ThreatDetectionConstants.ACTIVE);
    }
  }

  private void applyHumanResponseFilter(Document query, String humanResponseFilter) {
    if (humanResponseFilter == null || humanResponseFilter.isEmpty()) {
      return;
    }
    query.append("humanResponse", humanResponseFilter.toUpperCase());
  }

  // Combine with $and so a clause that uses $or (legacy context, PENDING humanResponse)
  // cannot overwrite another $or already on the query.
  private void andDocument(Document query, Document clause) {
    if (clause == null || clause.isEmpty()) {
      return;
    }
    if (query.isEmpty()) {
      query.putAll(clause);
      return;
    }
    List<Document> clauses = new ArrayList<>();
    clauses.add(new Document(query));
    clauses.add(clause);
    query.clear();
    query.append("$and", clauses);
  }

  private Document buildQueryFromFilter(Map<String, Object> filter, String contextSource, String accountId) {
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

    // Handle apiCollectionId filter
    List<Integer> apiCollectionIds = (List<Integer>) filter.get("apiCollectionId");
    if (apiCollectionIds != null && !apiCollectionIds.isEmpty()) {
      query.append("latestApiCollectionId", new Document("$in", apiCollectionIds));
    }

    // Handle method filter
    List<String> methods = (List<String>) filter.get("method");
    if (methods != null && !methods.isEmpty()) {
      // Convert string methods to uppercase to match enum names stored in MongoDB
      List<String> normalizedMethods = methods.stream()
          .map(m -> m != null ? m.toUpperCase() : null)
          .filter(m -> m != null)
          .collect(Collectors.toList());
      if (!normalizedMethods.isEmpty()) {
        query.append("latestApiMethod", new Document("$in", normalizedMethods));
      }
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

    String statusFilter = (String) filter.get("statusFilter");
    applyStatusFilter(query, statusFilter);

    // // Handle label filter with backward compatibility
    // String label = (String) filter.get("label");
    // if (label != null && !label.isEmpty()) {
    //   MaliciousEventDto.Label labelEnum = convertStringLabelToModelLabel(label);
    //   applyLabelFilter(query, labelEnum);
    // }

    String latestApiOrigRegex = (String) filter.get("latestApiOrigRegex");
    if (latestApiOrigRegex != null && !latestApiOrigRegex.isEmpty()) {
      query.append("latestApiOrig", new Document("$regex", latestApiOrigRegex));
    }

    // A bulk action on a filtered view must reach exactly the rows the view shows.
    andDocument(query, caseOwnerCondition((List<String>) filter.get("caseOwners")));
    andDocument(query, caseNotesCondition((Boolean) filter.get("hasCaseNotes")));

    // Apply simple context filter (only for ENDPOINT and AGENTIC)
    Document contextFilter = ThreatUtils.buildSimpleContextFilter(contextSource, accountId);
    if (!contextFilter.isEmpty()) {
      andDocument(query, contextFilter);
    }

    return query;
  }

  public SessionDocument fetchSessionContext(String accountId, String sessionId) {
    try {
      return AgenticSessionContextDao.instance.findBySessionIdentifier(accountId, sessionId);
    } catch (Exception e) {
      logger.error("Error fetching session context", e);
      return null;
    }
  }

  public void bulkUpdateAgenticSessionContext(String accountId, BulkUpdateAgenticSessionContextRequest req) {
    if (req == null || req.getSessionDocumentsCount() == 0) {
      return;
    }

    // Convert protobuf messages to SessionDocument objects
    List<SessionDocument> sessionDocuments = new ArrayList<>();
    for (SessionDocumentMessage msg : req.getSessionDocumentsList()) {
      sessionDocuments.add(convertToSessionDocument(msg));
    }

    // Perform bulk upsert
    List<WriteModel<SessionDocument>> bulkUpdates = new ArrayList<>();
    UpdateOptions updateOptions = new UpdateOptions().upsert(true);
    long currentTime = com.akto.dao.context.Context.now();

    for (SessionDocument sessionDocument : sessionDocuments) {
      if (sessionDocument == null || sessionDocument.getSessionIdentifier() == null || sessionDocument.getSessionIdentifier().isEmpty()) {
        continue;
      }

      Bson filter = Filters.eq(SessionDocument.SESSION_IDENTIFIER, sessionDocument.getSessionIdentifier());
      sessionDocument.setUpdatedAt(currentTime);

      Bson updates = Updates.combine(
          Updates.setOnInsert(SessionDocument.SESSION_IDENTIFIER, sessionDocument.getSessionIdentifier()),
          Updates.setOnInsert(SessionDocument.CREATED_AT, currentTime),
          Updates.set(SessionDocument.SESSION_SUMMARY, sessionDocument.getSessionSummary()),
          Updates.set(SessionDocument.CONVERSATION_INFO, sessionDocument.getConversationInfo()),
          Updates.set(SessionDocument.IS_MALICIOUS, sessionDocument.isMalicious()),
          Updates.set(SessionDocument.BLOCKED_REASON, sessionDocument.getBlockedReason()),
          Updates.set(SessionDocument.UPDATED_AT, sessionDocument.getUpdatedAt())
      );

      bulkUpdates.add(new UpdateOneModel<>(filter, updates, updateOptions));
    }

    if (!bulkUpdates.isEmpty()) {
      try {
        AgenticSessionContextDao.instance.getCollection(accountId).bulkWrite(bulkUpdates);
      } catch (Exception e) {
        logger.error("Error bulk updating session context", e);
      }
    }
  }

  private SessionDocument convertToSessionDocument(SessionDocumentMessage msg) {
    SessionDocument doc = new SessionDocument();
    doc.setSessionIdentifier(msg.getSessionIdentifier());
    doc.setSessionSummary(msg.getSessionSummary());
    doc.setMalicious(msg.getIsMalicious());
    doc.setBlockedReason(msg.getBlockedReason());

    // Convert conversation entries
    List<SessionDocument.ConversationInfo> convInfo = new ArrayList<>();
    for (ConversationEntry entry : msg.getConversationInfoList()) {
      SessionDocument.ConversationInfo conv = new SessionDocument.ConversationInfo();
      conv.setRequestId(entry.getRequestId());
      conv.setRequestPayload(entry.getRequestPayload());
      conv.setResponsePayload(entry.getResponsePayload());
      conv.setTimestamp(entry.getTimestamp());
      convInfo.add(conv);
    }
    doc.setConversationInfo(convInfo);

    return doc;
  }
}
