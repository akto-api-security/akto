package com.akto.threat.backend.service;

import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListThreatActorResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListThreatActorsRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatActorByCountryRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatActorByCountryResponse;
import com.akto.threat.backend.constants.MongoDBCollection;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bson.Document;

public class ThreatActorService {

  private final MongoClient mongoClient;

  public ThreatActorService(MongoClient mongoClient) {
    this.mongoClient = mongoClient;
  }

  public ListThreatActorResponse listThreatActors(
      String accountId, ListThreatActorsRequest request) {
    int skip = request.hasSkip() ? request.getSkip() : 0;
    int limit = request.getLimit();
    Map<String, Integer> sort = request.getSortMap();
    MongoCollection<Document> coll =
        this.mongoClient
            .getDatabase(accountId)
            .getCollection(MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS, Document.class);

    List<Document> pipeline = new ArrayList<>();
    pipeline.add(new Document("$sort", new Document("actor", 1).append("detectedAt", -1))); // sort
    pipeline.add(
        new Document(
            "$group",
            new Document("_id", "$actor")
                .append("latestApiEndpoint", new Document("$last", "$latestApiEndpoint"))
                .append("latestApiMethod", new Document("$last", "$latestApiMethod"))
                .append("latestApiIp", new Document("$last", "$latestApiIp"))
                .append("country", new Document("$last", "$country"))
                .append("discoveredAt", new Document("$last", "$detectedAt"))));
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

    return ListThreatActorResponse.newBuilder()
        .addAllActors(actors)
        .setTotal(actors.size())
        .build();
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
