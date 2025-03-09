package com.akto.threat.backend.service;

import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchMaliciousEventsRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchMaliciousEventsResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListThreatActorResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListThreatActorsRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatActorByCountryRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatActorByCountryResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchMaliciousEventsResponse.MaliciousPayloadsResponse;
import com.akto.threat.backend.constants.MongoDBCollection;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.conversions.Bson;

public class ThreatActorService {

  private final MongoClient mongoClient;

  public ThreatActorService(MongoClient mongoClient) {
    this.mongoClient = mongoClient;
  }

  public ListThreatActorResponse listThreatActors(
      String accountId, ListThreatActorsRequest request) {
      
    // getDailyActorCounts(accountId, 0, 1773011064);
    int skip = request.hasSkip() ? request.getSkip() : 0;
    int limit = request.getLimit();
    Map<String, Integer> sort = request.getSortMap();
    MongoCollection<Document> coll =
        this.mongoClient
            .getDatabase(accountId)
            .getCollection(MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS, Document.class);

    ListThreatActorsRequest.Filter filter = request.getFilter();

    List<Document> base = new ArrayList<>();

    Document match = new Document();

    if (!filter.getActorsList().isEmpty()) {
      match.append("actor", new Document("$in", filter.getActorsList()));
    }

    if (!filter.getLatestIpsList().isEmpty()) {
      match.append("latestApiIp", new Document("$in", filter.getLatestIpsList()));
    }

    if (filter.hasDetectedAtTimeRange()) {
      long start = filter.getDetectedAtTimeRange().getStart();
      long end = filter.getDetectedAtTimeRange().getEnd();
      match.append("detectedAt", new Document("$gte", start).append("$lte", end));
    }

    if (!match.isEmpty()) {
      base.add(new Document("$match", match));
    }

    base.add(new Document("$sort", new Document("detectedAt", -1)));
    base.add(
        new Document(
            "$group",
            new Document("_id", "$actor")
                .append("latestApiEndpoint", new Document("$last", "$latestApiEndpoint"))
                .append("latestApiMethod", new Document("$last", "$latestApiMethod"))
                .append("latestApiIp", new Document("$last", "$latestApiIp"))
                .append("country", new Document("$last", "$country"))
                .append("discoveredAt", new Document("$last", "$detectedAt"))));

    List<Document> countPipeline = new ArrayList<>(base);
    countPipeline.add(new Document("$count", "total"));

    Document result = coll.aggregate(countPipeline).first();
    long total = result != null ? result.getInteger("total", 0) : 0;

    List<Document> pipeline = new ArrayList<>(base);

    pipeline.add(new Document("$skip", skip));
    pipeline.add(new Document("$limit", limit));

    pipeline.add(
        new Document(
            "$sort", new Document("discoveredAt", sort.getOrDefault("discoveredAt", -1)))); // sort

    List<ListThreatActorResponse.ThreatActor> actors = new ArrayList<>();
    try (MongoCursor<Document> cursor = coll.aggregate(pipeline).cursor()) {
      while (cursor.hasNext()) {
        Document doc = cursor.next();
        actors.add(
            ListThreatActorResponse.ThreatActor.newBuilder()
                .setId(doc.getString("_id"))
                .setLatestApiEndpoint(doc.getString("latestApiEndpoint"))
                .setLatestApiMethod(doc.getString("latestApiMethod"))
                .setLatestApiIp(doc.getString("latestApiIp"))
                .setDiscoveredAt(doc.getLong("discoveredAt"))
                .setCountry(doc.getString("country"))
                .build());
      }
    }

    return ListThreatActorResponse.newBuilder().addAllActors(actors).setTotal(total).build();
  }

  public void getDailyActorCounts(String accountId, long startTs, long endTs) {
        MongoCollection<Document> coll = this.mongoClient
            .getDatabase(accountId)
            .getCollection(MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS, Document.class);

            List<Document> pipeline = new ArrayList<>();

            // 1. Match documents within the time range (all in seconds)
            Document matchConditions = new Document("detectedAt", new Document("$lte", endTs));
            if (startTs > 0) {
                matchConditions.get("detectedAt", Document.class).append("$gte", startTs);
            }
            pipeline.add(new Document("$match", matchConditions));
        
            pipeline.add(new Document("$project", 
        new Document("actor", 1)
            .append("severity", 1)
            .append("severityPriority", 
                new Document("$switch", 
                    new Document("branches", Arrays.asList(
                        new Document("case", new Document("$eq", Arrays.asList("$severity", "CRITICAL"))).append("then", 4),
                        new Document("case", new Document("$eq", Arrays.asList("$severity", "HIGH"))).append("then", 3),
                        new Document("case", new Document("$eq", Arrays.asList("$severity", "MEDIUM"))).append("then", 2),
                        new Document("case", new Document("$eq", Arrays.asList("$severity", "LOW"))).append("then", 1)))
                    .append("default", 0)))
            .append("dayStart", 
                new Document("$dateTrunc", 
                    new Document("date", new Document("$toDate", new Document("$multiply", Arrays.asList("$detectedAt", 1000))))
                        .append("unit", "day")))));

            // 3. Group by dayStart and actor, taking the MAX severity priority
            pipeline.add(new Document("$group", 
                new Document("_id", 
                    new Document("dayStart", "$dayStart")
                        .append("actor", "$actor"))
                .append("severity", new Document("$max", "$severityPriority"))));

            // 4. Convert severityPriority back to string for comparison
            pipeline.add(new Document("$project", 
                new Document("dayStart", "$_id.dayStart")
                    .append("severity", 
                        new Document("$switch", 
                            new Document("branches", Arrays.asList(
                                new Document("case", new Document("$eq", Arrays.asList("$severity", 4))).append("then", "CRITICAL"),
                                new Document("case", new Document("$eq", Arrays.asList("$severity", 3))).append("then", "HIGH"),
                                new Document("case", new Document("$eq", Arrays.asList("$severity", 2))).append("then", "MEDIUM"),
                                new Document("case", new Document("$eq", Arrays.asList("$severity", 1))).append("then", "LOW")))
                            .append("default", "UNKNOWN")))));

            // 5. Group by dayStart to count total actors and severity-specific actors
            pipeline.add(new Document("$group", 
                new Document("_id", "$dayStart")
                    .append("totalActors", new Document("$sum", 1))
                    .append("severityActors", 
                        new Document("$sum", 
                            new Document("$cond", 
                                Arrays.asList(
                                    new Document("$eq", Arrays.asList("$severity", "HIGH")),
                                    1,
                                    0))))));
        
            // Execute the pipeline and collect results
            Map<Long, Object> result = new HashMap<>();
            try (MongoCursor<Document> cursor = coll.aggregate(pipeline).cursor()) {
                while (cursor.hasNext()) {
                    Document doc = cursor.next();
                    // Convert dayStart from Date (ms) back to seconds
                    long dayStartEpochSeconds = doc.getDate("_id").getTime() / 1000;
                    int totalActors = doc.getInteger("totalActors");
                    int highSeverityActors = doc.getInteger("severityActors");
                    System.out.println(totalActors);
                    System.out.println(highSeverityActors);
                    result.put(dayStartEpochSeconds, new Object());
                }
            }
    System.out.print(result);
    return;
  }

  public FetchMaliciousEventsResponse fetchAggregateMaliciousRequests(
      String accountId, FetchMaliciousEventsRequest request) {

    MongoCollection<Document> coll =
        this.mongoClient
            .getDatabase(accountId)
            .getCollection(MongoDBCollection.ThreatDetection.AGGREGATE_SAMPLE_MALICIOUS_REQUESTS, Document.class);

    String refId = request.getRefId();
    
    Bson filters = Filters.eq("refId", refId);
    FindIterable<Document> respList = (FindIterable<Document>) coll.find(filters);
    List<FetchMaliciousEventsResponse.MaliciousPayloadsResponse> maliciousPayloadsResponse = new ArrayList<>();
    for (Document doc: respList) {
      maliciousPayloadsResponse.add(
        FetchMaliciousEventsResponse.MaliciousPayloadsResponse.newBuilder().
        setOrig((doc.getString("orig"))).
        setTs(doc.getLong("requestTime")).build());
    }

    return FetchMaliciousEventsResponse.newBuilder().addAllMaliciousPayloadsResponse(maliciousPayloadsResponse).build();
  }

  public ThreatActorByCountryResponse getThreatActorByCountry(
      String accountId, ThreatActorByCountryRequest request) {
    MongoCollection<Document> coll =
        this.mongoClient
            .getDatabase(accountId)
            .getCollection(MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS, Document.class);

    List<Document> pipeline = new ArrayList<>();
    pipeline.add(
        new Document("$sort", new Document("country", 1).append("detectedAt", -1))); // sort
    pipeline.add(
        new Document(
            "$group",
            new Document("_id", "$country")
                .append("distinctActors", new Document("$addToSet", "$actor"))));

    pipeline.add(
        new Document(
            "$addFields", new Document("actorsCount", new Document("$size", "$distinctActors"))));

    pipeline.add(new Document("$sort", new Document("actorsCount", -1))); // sort

    List<ThreatActorByCountryResponse.CountryCount> actorsByCountryCount = new ArrayList<>();

    try (MongoCursor<Document> cursor = coll.aggregate(pipeline).cursor()) {
      while (cursor.hasNext()) {
        Document doc = cursor.next();
        actorsByCountryCount.add(
            ThreatActorByCountryResponse.CountryCount.newBuilder()
                .setCode(doc.getString("_id"))
                .setCount(doc.getInteger("actorsCount", 0))
                .build());
      }
    }

    return ThreatActorByCountryResponse.newBuilder().addAllCountries(actorsByCountryCount).build();
  }
}
