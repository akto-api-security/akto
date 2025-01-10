package com.akto.threat.backend.service;

import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListThreatApiRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListThreatApiResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatCategoryWiseCountRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatCategoryWiseCountResponse;
import com.akto.threat.backend.constants.MongoDBCollection;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bson.Document;

public class ThreatApiService {

  private final MongoClient mongoClient;

  public ThreatApiService(MongoClient mongoClient) {
    this.mongoClient = mongoClient;
  }

  public ListThreatApiResponse listThreatApis(String accountId, ListThreatApiRequest request) {
    int skip = request.hasSkip() ? request.getSkip() : 0;
    int limit = request.getLimit();
    Map<String, Integer> sort = request.getSortMap();
    MongoCollection<Document> coll =
        this.mongoClient
            .getDatabase(accountId)
            .getCollection(MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS, Document.class);

    List<Document> base = new ArrayList<>();
    ListThreatApiRequest.Filter filter = request.getFilter();

    Document match = new Document();
    if (!filter.getMethodsList().isEmpty()) {
      match.append("latestApiMethod", new Document("$in", filter.getMethodsList()));
    }

    if (!filter.getUrlsList().isEmpty()) {
      match.append("latestApiEndpoint", new Document("$in", filter.getUrlsList()));
    }

    if (filter.hasDetectedAtTimeRange()) {
      long start = filter.getDetectedAtTimeRange().getStart();
      long end = filter.getDetectedAtTimeRange().getEnd();
      match.append("detectedAt", new Document("$gte", start).append("$lte", end));
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
                        .append("method", "$latestApiMethod"))
                .append("discoveredAt", new Document("$last", "$detectedAt"))
                .append("distinctActors", new Document("$addToSet", "$actor"))
                .append("requestsCount", new Document("$sum", 1))));
    base.add(
        new Document(
            "$addFields", new Document("actorsCount", new Document("$size", "$distinctActors"))));

    List<Document> countPipeline = new ArrayList<>(base);
    countPipeline.add(new Document("$count", "total"));

    Document result = coll.aggregate(countPipeline).first();
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
    try (MongoCursor<Document> cursor = coll.aggregate(pipeline).cursor()) {
      while (cursor.hasNext()) {
        Document doc = cursor.next();
        Document agg = (Document) doc.get("_id");
        apis.add(
            ListThreatApiResponse.ThreatApi.newBuilder()
                .setEndpoint(agg.getString("endpoint"))
                .setMethod(agg.getString("method"))
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
      String accountId, ThreatCategoryWiseCountRequest req) {
    MongoCollection<Document> coll =
        this.mongoClient
            .getDatabase(accountId)
            .getCollection(MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS, Document.class);

    List<Document> pipeline = new ArrayList<>();
    pipeline.add(
        new Document("$sort", new Document("category", 1).append("detectedAt", -1))); // sort
    pipeline.add(
        new Document(
            "$group",
            new Document(
                    "_id",
                    new Document("category", "$category").append("subCategory", "$subCategory"))
                .append("count", new Document("$sum", 1))));

    pipeline.add(
        new Document(
            "$sort",
            new Document("category", -1).append("subCategory", -1).append("count", -1))); // sort

    List<ThreatCategoryWiseCountResponse.SubCategoryCount> categoryWiseCounts = new ArrayList<>();

    try (MongoCursor<Document> cursor = coll.aggregate(pipeline).cursor()) {
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
}
