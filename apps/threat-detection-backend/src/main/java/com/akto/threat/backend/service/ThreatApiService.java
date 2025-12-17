package com.akto.threat.backend.service;

import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListThreatApiRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListThreatApiResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatCategoryWiseCountRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatCategoryWiseCountResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatSeverityWiseCountRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatSeverityWiseCountResponse;
import com.akto.threat.backend.dao.MaliciousEventDao;
import com.akto.threat.backend.utils.ThreatUtils;
import com.mongodb.client.MongoCursor;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;


public class ThreatApiService {

  private final MaliciousEventDao maliciousEventDao;
  private static final LoggerMaker loggerMaker = new LoggerMaker(ThreatApiService.class);

  public ThreatApiService(MaliciousEventDao maliciousEventDao) {
    this.maliciousEventDao = maliciousEventDao;
  }

  public ListThreatApiResponse listThreatApis(String accountId, ListThreatApiRequest request, String contextSource) {

    loggerMaker.info("listThreatApis start ts " + Context.now());

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

    // NOTE: filterId filter will be handled by context-aware filtering below

    if (filter.hasDetectedAtTimeRange()) {
      long start = filter.getDetectedAtTimeRange().getStart();
      long end = filter.getDetectedAtTimeRange().getEnd();
      match.append("detectedAt", new Document("$gte", start).append("$lte", end));
    }

    // Apply context-aware filtering
    List<String> latestAttackList = filter.getLatestAttackList().isEmpty() ? null : filter.getLatestAttackList();
    match = ThreatUtils.mergeContextFilter(match, contextSource, latestAttackList);

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

    loggerMaker.info("listThreatApis end ts " + Context.now());
    return ListThreatApiResponse.newBuilder().addAllApis(apis).setTotal(total).build();
  }

  public ThreatCategoryWiseCountResponse getSubCategoryWiseCount(
    String accountId, ThreatCategoryWiseCountRequest req, String contextSource) {

    loggerMaker.info("getSubCategoryWiseCount start ts " + Context.now());

    List<Document> pipeline = new ArrayList<>();
    Document match = new Document();

    // NOTE: filterId filter will be handled by context-aware filtering below

    // 1. Match on time range
    if (req.getStartTs() != 0 || req.getEndTs() != 0) {
      match.append("detectedAt", new Document("$gte", req.getStartTs()).append("$lte", req.getEndTs()));
    }

    // Apply context-aware filtering
    List<String> latestAttackList = (req.getLatestAttackList() == null || req.getLatestAttackList().isEmpty()) ?
        null : req.getLatestAttackList();
    match = ThreatUtils.mergeContextFilter(match, contextSource, latestAttackList);

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

    loggerMaker.info("getSubCategoryWiseCount end ts " + Context.now());

    return ThreatCategoryWiseCountResponse.newBuilder()
        .addAllCategoryWiseCounts(categoryWiseCounts)
        .build();
  }

  public ThreatSeverityWiseCountResponse getSeverityWiseCount(
    String accountId, ThreatSeverityWiseCountRequest req, String contextSource) {

    loggerMaker.info("getSeverityWiseCount start ts " + Context.now());

    List<ThreatSeverityWiseCountResponse.SeverityCount> categoryWiseCounts = new ArrayList<>();

    String[] severities = { "CRITICAL", "HIGH", "MEDIUM", "LOW" };

    // Build match document
    Document match = new Document()
        .append("detectedAt", new Document("$gte", req.getStartTs())
            .append("$lte", req.getEndTs()))
        .append("severity", new Document("$in", Arrays.asList(severities)));

    // Apply context-aware filtering
    List<String> latestAttackList = (req.getLatestAttackList() == null || req.getLatestAttackList().isEmpty()) ?
        null : req.getLatestAttackList();
    match = ThreatUtils.mergeContextFilter(match, contextSource, latestAttackList);

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

    loggerMaker.info("getSeverityWiseCount end ts " + Context.now());

    return ThreatSeverityWiseCountResponse.newBuilder()
        .addAllCategoryWiseCounts(categoryWiseCounts)
        .build();
  }

}
