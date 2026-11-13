package com.akto.threat.backend.service;

import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListThreatApiRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListThreatApiResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatCategoryWiseCountRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatCategoryWiseCountResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatSeverityWiseCountRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatSeverityWiseCountResponse;
import com.akto.threat.backend.cache.DashboardFilterCache;
import com.akto.threat.backend.dao.MaliciousEventDao;
import com.akto.threat.backend.utils.ThreatUtils;
import com.akto.dto.threat_detection_backend.MaliciousEventDto;
import com.akto.log.LoggerMaker;
import com.mongodb.MongoExecutionTimeoutException;
import com.mongodb.client.MongoCursor;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;


public class ThreatApiService {

  private final MaliciousEventDao maliciousEventDao;
  private static final DashboardFilterCache<ThreatCategoryWiseCountResponse> subCategoryCountCache = new DashboardFilterCache<>();

  public ThreatApiService(MaliciousEventDao maliciousEventDao) {
    this.maliciousEventDao = maliciousEventDao;
  }

  public ListThreatApiResponse listThreatApis(String accountId, ListThreatApiRequest request, String contextSource) {

    int skip = request.hasSkip() ? request.getSkip() : 0;
    int limit = request.getLimit();
    Map<String, Integer> sort = request.getSortMap();

    List<Document> base = new ArrayList<>();
    ListThreatApiRequest.Filter filter = request.getFilter();

    Document match = new Document();
    if (!filter.getMethodsList().isEmpty()) {
      match.append("latestApiMethod", new Document("$in", filter.getMethodsList()));
    }

    if (!filter.getUrlsList().isEmpty()) {
      match.append("latestApiEndpoint", new Document("$in", filter.getUrlsList()));
    }

    if (!filter.getLatestAttackList().isEmpty()) {
      match.append("filterId", new Document("$in", filter.getLatestAttackList()));
    }

    if (filter.hasDetectedAtTimeRange()) {
      long start = filter.getDetectedAtTimeRange().getStart();
      long end = filter.getDetectedAtTimeRange().getEnd();
      match.append("detectedAt", new Document("$gte", start).append("$lte", end));
    }

    // Apply simple context filter (only for ENDPOINT and AGENTIC)
    Document contextFilter = ThreatUtils.buildSimpleContextFilter(contextSource, accountId);
    if (!contextFilter.isEmpty()) {
      match.putAll(contextFilter);
    }
    match.putAll(ThreatUtils.excludeSkillEndpointFilter(contextSource));

    if (!match.isEmpty()) {
      base.add(new Document("$match", match));
    }
    ThreatUtils.addHostScope(base, request.hasHostScope() ? ThreatUtils.hostScopeMatch(request.getHostScope()) : null);

    base.add(new Document("$sort", new Document("detectedAt", -1))); // sort
    base.add(
        new Document(
            "$group",
            new Document(
                    "_id",
                    new Document("endpoint", "$latestApiEndpoint")
                        .append("method", "$latestApiMethod")
                        .append("host", "$host"))
                .append("discoveredAt", new Document("$last", "$detectedAt"))
                .append("distinctActors", new Document("$addToSet", "$actor"))
                .append("requestsCount", new Document("$sum", 1))));
    base.add(
        new Document(
            "$addFields", new Document("actorsCount", new Document("$size", "$distinctActors"))));

    List<Document> countPipeline = new ArrayList<>(base);
    countPipeline.add(new Document("$count", "total"));

    Document result = maliciousEventDao.aggregateRaw(accountId, countPipeline).first();
    long total = result != null ? result.getInteger("total", 0) : 0;

    List<Document> pipeline = new ArrayList<>(base);

    pipeline.add(new Document("$project", new Document("distinctActors", 0)));
    pipeline.add(new Document("$skip", skip));
    pipeline.add(new Document("$limit", limit));
    // add sort
    pipeline.add(
        new Document(
            "$sort",
            new Document("discoveredAt", sort.getOrDefault("discoveredAt", -1))
                .append("requestsCount", sort.getOrDefault("requestsCount", -1))
                .append("actorsCount", sort.getOrDefault("actorsCount", -1))));

    List<ListThreatApiResponse.ThreatApi> apis = new ArrayList<>();
    try (MongoCursor<Document> cursor = maliciousEventDao.aggregateRaw(accountId, pipeline).cursor()) {
      while (cursor.hasNext()) {
        Document doc = cursor.next();
        Document agg = (Document) doc.get("_id");
        apis.add(
            ListThreatApiResponse.ThreatApi.newBuilder()
                .setEndpoint(agg.getString("endpoint"))
                .setMethod(agg.getString("method"))
                .setHost(agg.getString("host") != null ? agg.getString("host") : "")
                .setDiscoveredAt(doc.getLong("discoveredAt"))
                .setActorsCount(doc.getInteger("actorsCount", 0))
                .setRequestsCount(doc.getInteger("requestsCount", 0))
                .build());
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    return ListThreatApiResponse.newBuilder().addAllApis(apis).setTotal(total).build();
  }

  public ThreatCategoryWiseCountResponse getSubCategoryWiseCount(
    String accountId, ThreatCategoryWiseCountRequest req, String contextSource) {

    // Skip cache when latestAttackList is present — results are filter-specific
    if (!req.getLatestAttackList().isEmpty()) {
      return computeSubCategoryWiseCount(accountId, req, contextSource);
    }

    String statusKey = (req.getStatus() != null && !req.getStatus().isEmpty()) ? req.getStatus() : "ALL";
    String cacheKey = "subCategoryCount|get_subcategory_wise_count|" + accountId + "|" + contextSource
        + "|" + DashboardFilterCache.bucketDay(req.getStartTs())
        + "|" + DashboardFilterCache.bucketDay(req.getEndTs())
        + "|" + statusKey
        + (req.hasHostScope() ? "|" + req.getHostScope().toString() : "");

    return subCategoryCountCache.get(cacheKey,
        () -> computeSubCategoryWiseCount(accountId, req, contextSource));
  }

  private ThreatCategoryWiseCountResponse computeSubCategoryWiseCount(
    String accountId, ThreatCategoryWiseCountRequest req, String contextSource) {

    List<Document> pipeline = new ArrayList<>();
    Document match = new Document();

    if(!req.getLatestAttackList().isEmpty()) {
      match.append("filterId", new Document("$in", req.getLatestAttackList()));
    }

    if (req.getStatus() != null && !req.getStatus().isEmpty()) {
      match.append("status", req.getStatus());
    }

    // 1. Match on time range
    if (req.getStartTs() != 0 || req.getEndTs() != 0) {
      match.append("detectedAt", new Document("$gte", req.getStartTs()).append("$lte", req.getEndTs()));
    }

    // Apply simple context filter (only for ENDPOINT and AGENTIC)
    Document contextFilter = ThreatUtils.buildSimpleContextFilter(contextSource, accountId);
    if (!contextFilter.isEmpty()) {
      match.putAll(contextFilter);
    }
    boolean byEvalType = ThreatUtils.readsByEvalType(contextSource, null, ThreatUtils.USE_EVAL_TYPE_READS);
    Document hostScopeMatch = req.hasHostScope() ? ThreatUtils.hostScopeMatch(req.getHostScope()) : null;
    Document subCategoryGroupId = new Document("category", "$category").append("subCategory", "$subCategory");

    if (byEvalType) {
      Map<Object, Integer> counts = countExcludingEvalTypes(accountId, match, hostScopeMatch, subCategoryGroupId, null, 0);
      return ThreatCategoryWiseCountResponse.newBuilder()
          .addAllCategoryWiseCounts(subCategoryCounts(counts))
          .build();
    }

    match.putAll(ThreatUtils.excludeSkillEndpointFilter(contextSource));

    pipeline.add(new Document("$match", match));
    ThreatUtils.addHostScope(pipeline, hostScopeMatch);

    // 3. Collapse misconfiguration re-detections.
    pipeline.addAll(ThreatUtils.configScanDedupeStages(pipeline, contextSource));

    // 4. Group by category and subCategory
    pipeline.add(new Document("$group",
        new Document("_id",
            new Document("category", "$category")
            .append("subCategory", "$subCategory"))
            .append("count", new Document("$sum", 1))));

    // 5. Sort by count descending
    pipeline.add(new Document("$sort", new Document("count", -1)));

    List<ThreatCategoryWiseCountResponse.SubCategoryCount> categoryWiseCounts = new ArrayList<>();

    // 5. Execute aggregation with controlled batch size
    try (MongoCursor<Document> cursor = maliciousEventDao.aggregateRaw(accountId, pipeline).batchSize(1000).cursor()) {
      while (cursor.hasNext()) {
        Document doc = cursor.next();
        Document agg = (Document) doc.get("_id");

        categoryWiseCounts.add(
            ThreatCategoryWiseCountResponse.SubCategoryCount.newBuilder()
                .setCategory(agg.getString("category"))
                .setSubCategory(agg.getString("subCategory"))
                .setCount(doc.getInteger("count", 0))
                .build());
      }
    }

    return ThreatCategoryWiseCountResponse.newBuilder()
        .addAllCategoryWiseCounts(categoryWiseCounts)
        .build();
  }

  public ThreatSeverityWiseCountResponse getSeverityWiseCount(
    String accountId, ThreatSeverityWiseCountRequest req, String contextSource) {
    return getSeverityWiseCount(accountId, req, contextSource, null, null);
  }

  public ThreatSeverityWiseCountResponse getSeverityWiseCount(
    String accountId, ThreatSeverityWiseCountRequest req, String contextSource, String skillEvalMode, String configEvalMode) {

    List<ThreatSeverityWiseCountResponse.SeverityCount> categoryWiseCounts = new ArrayList<>();

    String[] severities = { "CRITICAL", "HIGH", "MEDIUM", "LOW" };

    // Build match document
    Document match = new Document()
        .append("detectedAt", new Document("$gte", req.getStartTs())
            .append("$lte", req.getEndTs()))
        .append("severity", new Document("$in", Arrays.asList(severities)));

    if (!req.getLatestAttackList().isEmpty()) {
        match.append("filterId", new Document("$in", req.getLatestAttackList()));
    }

    if (req.getStatus() != null && !req.getStatus().isEmpty()) {
        match.append("status", req.getStatus());
    }

      // Apply simple context filter (only for ENDPOINT and AGENTIC)
      Document contextFilter = ThreatUtils.buildSimpleContextFilter(contextSource, accountId);
      if (!contextFilter.isEmpty()) {
          match.putAll(contextFilter);
      }
      boolean byEvalType = ThreatUtils.readsByEvalType(contextSource, skillEvalMode, ThreatUtils.USE_EVAL_TYPE_READS);
      List<Document> evaluationModeConditions = byEvalType
          ? Collections.emptyList()
          : ThreatUtils.evaluationModeConditions(contextSource, skillEvalMode, configEvalMode);
      if (!byEvalType) {
          match.putAll(ThreatUtils.excludeSkillEndpointFilter(contextSource));
      }
      if (!evaluationModeConditions.isEmpty()) {
          match.append("$and", evaluationModeConditions);
      }

    Map<String, Integer> severityToCount = new HashMap<>();
    long severityMaxTimeMs = evaluationModeConditions.isEmpty() && !byEvalType ? 0 : ThreatUtils.REGEX_MAX_TIME_MS;
    Document hostScopeMatch = req.hasHostScope() ? ThreatUtils.hostScopeMatch(req.getHostScope()) : null;
    try {
      if (byEvalType) {
        countExcludingEvalTypes(accountId, match, hostScopeMatch, "$severity", configEvalMode, severityMaxTimeMs)
            .forEach((severity, count) -> severityToCount.put((String) severity, count));
      } else {
        List<Document> pipeline = new ArrayList<>();
        pipeline.add(new Document("$match", match));
        ThreatUtils.addHostScope(pipeline, hostScopeMatch);
        // The dedupe only collapses /config/ events; with those excluded it is a sort+group over every event for nothing.
        if (!"exclude".equalsIgnoreCase(configEvalMode)) {
            pipeline.addAll(ThreatUtils.configScanDedupeStages(pipeline, contextSource));
        }
        pipeline.add(new Document("$group", new Document("_id", "$severity")
            .append("count", new Document("$sum", 1))));
        severityToCount.putAll(runGroupedCounts(accountId, pipeline, severityMaxTimeMs, String.class));
      }
    } catch (MongoExecutionTimeoutException e) {
      new LoggerMaker(ThreatApiService.class, LoggerMaker.LogDb.THREAT_DETECTION).errorAndAddToDb("Severity counts exceeded " + ThreatUtils.REGEX_MAX_TIME_MS + "ms, returning none: " + e.getMessage(), LoggerMaker.LogDb.THREAT_DETECTION);
    }

    for (String severity : severities) {
      Integer count = severityToCount.get(severity);
      if (count != null && count > 0) {
        categoryWiseCounts.add(
            ThreatSeverityWiseCountResponse.SeverityCount.newBuilder()
                .setSeverity(severity)
                .setCount(count)
                .build());
      }
    }

    return ThreatSeverityWiseCountResponse.newBuilder()
        .addAllCategoryWiseCounts(categoryWiseCounts)
        .build();
  }


  private Map<Object, Integer> countExcludingEvalTypes(
    String accountId, Document match, Document hostScopeMatch, Object groupId, String configEvalMode, long maxTimeMs) {

    if ("only".equalsIgnoreCase(configEvalMode)) {
      return runGroupedCounts(accountId, groupedPipeline(
          new Document(match).append(MaliciousEventDto.EVAL_TYPE, MaliciousEventDto.EVAL_TYPE_CONFIG), hostScopeMatch, groupId),
          maxTimeMs, Object.class);
    }

    Map<Object, Integer> counts = runGroupedCounts(accountId, groupedPipeline(match, hostScopeMatch, groupId), maxTimeMs, Object.class);
    Document excluded = new Document(match).append(MaliciousEventDto.EVAL_TYPE,
        new Document("$in", ThreatUtils.evalTypesExcludedFromCounts(configEvalMode)));
    runGroupedCounts(accountId, groupedPipeline(excluded, hostScopeMatch, groupId), maxTimeMs, Object.class)
        .forEach((key, count) -> counts.merge(key, -count, Integer::sum));
    return counts;
  }

  private List<Document> groupedPipeline(Document match, Document hostScopeMatch, Object groupId) {
    List<Document> pipeline = new ArrayList<>();
    pipeline.add(new Document("$match", match));
    ThreatUtils.addHostScope(pipeline, hostScopeMatch);
    pipeline.add(new Document("$group", new Document("_id", groupId).append("count", new Document("$sum", 1))));
    return pipeline;
  }

  private <K> Map<K, Integer> runGroupedCounts(String accountId, List<Document> pipeline, long maxTimeMs, Class<K> keyType) {
    Map<K, Integer> counts = new HashMap<>();
    try (MongoCursor<Document> cursor = maliciousEventDao.aggregateRaw(accountId, pipeline)
        .maxTime(maxTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS).cursor()) {
      while (cursor.hasNext()) {
        Document doc = cursor.next();
        counts.put(keyType.cast(doc.get("_id")), doc.getInteger("count", 0));
      }
    }
    return counts;
  }

  private List<ThreatCategoryWiseCountResponse.SubCategoryCount> subCategoryCounts(Map<Object, Integer> counts) {
    return counts.entrySet().stream()
        .filter(entry -> entry.getValue() > 0)
        .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
        .map(entry -> {
          Document id = (Document) entry.getKey();
          return ThreatCategoryWiseCountResponse.SubCategoryCount.newBuilder()
              .setCategory(id.getString("category"))
              .setSubCategory(id.getString("subCategory"))
              .setCount(entry.getValue())
              .build();
        })
        .collect(java.util.stream.Collectors.toList());
  }

}
