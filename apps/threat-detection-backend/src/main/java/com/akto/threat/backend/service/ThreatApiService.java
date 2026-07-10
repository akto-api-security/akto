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
import com.mongodb.client.MongoCursor;
import com.fasterxml.jackson.databind.ObjectMapper;


import java.util.ArrayList;
import java.util.Arrays;
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

    if (!match.isEmpty()) {
      base.add(new Document("$match", match));
    }

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

    String cacheKey = "subCategoryCount|get_subcategory_wise_count|" + accountId + "|" + contextSource
        + "|" + DashboardFilterCache.bucketDay(req.getStartTs())
        + "|" + DashboardFilterCache.bucketDay(req.getEndTs());

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

    // 1. Match on time range
    if (req.getStartTs() != 0 || req.getEndTs() != 0) {
      match.append("detectedAt", new Document("$gte", req.getStartTs()).append("$lte", req.getEndTs()));
    }

    // Apply simple context filter (only for ENDPOINT and AGENTIC)
    Document contextFilter = ThreatUtils.buildSimpleContextFilter(contextSource, accountId);
    if (!contextFilter.isEmpty()) {
      match.putAll(contextFilter);
    }

    pipeline.add(new Document("$match", match));

    // 3. Group by category and subCategory
    pipeline.add(new Document("$group",
        new Document("_id",
            new Document("category", "$category")
            .append("subCategory", "$subCategory"))
            .append("count", new Document("$sum", 1))));

    // 4. Sort by count descending
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

      // Apply simple context filter (only for ENDPOINT and AGENTIC)
      Document contextFilter = ThreatUtils.buildSimpleContextFilter(contextSource, accountId);
      if (!contextFilter.isEmpty()) {
          match.putAll(contextFilter);
      }

      List<Document> pipeline = new ArrayList<>();
      pipeline.add(new Document("$match", match));
      pipeline.add(new Document("$group", new Document("_id", "$severity")
          .append("count", new Document("$sum", 1))));

    Map<String, Integer> severityToCount = new HashMap<>();
    try (MongoCursor<Document> cursor = maliciousEventDao.aggregateRaw(accountId, pipeline).cursor()) {
      while (cursor.hasNext()) {
        Document doc = cursor.next();
        severityToCount.put(doc.getString("_id"), doc.getInteger("count", 0));
      }
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

  public String aggregateEventsByApiEndpoint(
      String accountId,
      String filterId,
      long startTs,
      long endTs,
      int skip,
      int limit,
      String contextSource) {

    Document match = new Document();
    match.append("filterId", filterId);

    if (startTs > 0 || endTs > 0) {
      Document timeRange = new Document();
      if (startTs > 0) {
        timeRange.append("$gte", startTs);
      }
      if (endTs > 0) {
        timeRange.append("$lte", endTs);
      }
      match.append("detectedAt", timeRange);
    }

    Document contextFilter = ThreatUtils.buildSimpleContextFilter(contextSource, accountId);
    if (!contextFilter.isEmpty()) {
      match.putAll(contextFilter);
    }

    List<Document> baseMatch = new ArrayList<>();
    if (!match.isEmpty()) {
      baseMatch.add(new Document("$match", match));
    }

    List<Document> countPipeline = new ArrayList<>(baseMatch);
    countPipeline.add(new Document("$count", "total"));

    Document countResult = maliciousEventDao.aggregateRaw(accountId, countPipeline).first();
    long total = countResult != null ? countResult.getInteger("total", 0) : 0;

    List<Document> dataPipeline = new ArrayList<>(baseMatch);
    dataPipeline.add(new Document("$sort", new Document("detectedAt", -1)));
    
    dataPipeline.add(
        new Document(
            "$group",
            new Document("_id", "$latestApiEndpoint")
                .append("count", new Document("$sum", 1))
                .append("events", new Document("$push", new Document()
                    .append("id", "$_id")
                    .append("actor", "$actor")
                    .append("host", "$host")
                    .append("apiCollectionId", "$apiCollectionId")
                    .append("method", "$latestApiMethod")
                    .append("severity", "$severity")
                    .append("filterId", "$filterId")
                    .append("detectedAt", "$detectedAt")
                    .append("type", "$type")
                    .append("category", "$category")
                    .append("subCategory", "$subCategory")
                    .append("successfulExploit", "$successfulExploit")
                    .append("status", "$status")
                    .append("label", "$label")
                    .append("country", "$country")
                    .append("payload", "$payload")
                    .append("metadata", "$metadata")
                    .append("owaspCategories", "$owaspCategories")
                    .append("refId", "$refId")
                    .append("jiraTicketUrl", "$jiraTicketUrl")))));
    
    dataPipeline.add(new Document("$skip", skip));
    dataPipeline.add(new Document("$limit", limit));
    
    dataPipeline.add(new Document("$addFields", 
        new Document("events", new Document("$slice", Arrays.asList("$events", 10)))));

    List<Map<String, Object>> apis = new ArrayList<>();

    try (MongoCursor<Document> cursor = maliciousEventDao.aggregateRaw(accountId, dataPipeline).cursor()) {
      while (cursor.hasNext()) {
        Document doc = cursor.next();
        String endpoint = (String) doc.get("_id");
        long count = ((Number) doc.get("count")).longValue();
        List<Document> events = (List<Document>) doc.get("events");

        Map<String, Object> apiData = new HashMap<>();
        apiData.put("endpoint", endpoint);
        apiData.put("count", count);

        List<Map<String, Object>> eventsList = new ArrayList<>();
        if (events != null) {
          for (Document eventDoc : events) {
            Map<String, Object> eventMap = new HashMap<>(eventDoc);
            eventsList.add(eventMap);
          }
        }
        apiData.put("events", eventsList);
        apis.add(apiData);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    Map<String, Object> response = new HashMap<>();
    
    List<Map<String, Object>> dataArray = new ArrayList<>();
    for (Map<String, Object> api : apis) {
      Map<String, Object> item = new HashMap<>();
      item.put("api", api.get("endpoint"));
      item.put("events", api.get("events"));
      item.put("count", api.get("count"));
      dataArray.add(item);
    }
    
    response.put("data", dataArray);
    response.put("totalEndpoints", apis.size());
    response.put("total", total);

    try {
      ObjectMapper mapper = new ObjectMapper();
      return mapper.writeValueAsString(response);
    } catch (Exception e) {
      e.printStackTrace();
      return "{\"error\": \"Failed to serialize response\"}";
    }
  }

  @SuppressWarnings("unchecked")
  public String getSchemaViolationsOverTime(
      String accountId,
      String filterId,
      long startTs,
      long endTs,
      int skip,
      int limit,
      String contextSource) {

    Document match = new Document();
    match.append("filterId", filterId);
    match.append("category", "SchemaConform");

    if (startTs > 0 || endTs > 0) {
      Document timeRange = new Document();
      if (startTs > 0) {
        timeRange.append("$gte", startTs);
      }
      if (endTs > 0) {
        timeRange.append("$lte", endTs);
      }
      match.append("detectedAt", timeRange);
    }

    Document contextFilter = ThreatUtils.buildSimpleContextFilter(contextSource, accountId);
    if (!contextFilter.isEmpty()) {
      match.putAll(contextFilter);
    }

    List<Document> baseMatch = new ArrayList<>();
    if (!match.isEmpty()) {
      baseMatch.add(new Document("$match", match));
    }

    List<Document> countPipeline = new ArrayList<>(baseMatch);
    countPipeline.add(new Document("$count", "total"));

    Document countResult = maliciousEventDao.aggregateRaw(accountId, countPipeline).first();
    long totalViolations = countResult != null ? countResult.getInteger("total", 0) : 0;

    if (totalViolations == 0) {
      Map<String, Object> response = new HashMap<>();
      response.put("data", new ArrayList<>());
      response.put("total", 0);

      try {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(response);
      } catch (Exception e) {
        return "{\"error\": \"Failed to serialize response\"}";
      }
    }

    long daysBetween = 0;
    if (startTs > 0 && endTs > 0) {
      daysBetween = (endTs - startTs) / 86400;
    }

    List<Document> dataPipeline = new ArrayList<>(baseMatch);
    dataPipeline.add(new Document("$sort", new Document("detectedAt", 1)));
    dataPipeline.add(new Document("$skip", skip));
    dataPipeline.add(new Document("$limit", limit));

    List<Map<String, Object>> eventsData = new ArrayList<>();
    try (MongoCursor<Document> cursor = maliciousEventDao.aggregateRaw(accountId, dataPipeline).cursor()) {
      while (cursor.hasNext()) {
        Document doc = cursor.next();
        long detectedAt = ((Number) doc.get("detectedAt")).longValue();

        Map<String, Object> item = new HashMap<>();
        item.put("detectedAt", detectedAt);
        eventsData.add(item);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    Map<String, Object> response = new HashMap<>();
    response.put("data", eventsData);
    response.put("total", totalViolations);
    response.put("daysBetween", daysBetween);

    try {
      ObjectMapper mapper = new ObjectMapper();
      return mapper.writeValueAsString(response);
    } catch (Exception e) {
      e.printStackTrace();
      return "{\"error\": \"Failed to serialize response\"}";
    }
  }

}
