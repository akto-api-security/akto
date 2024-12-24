package com.akto.threat.backend.service;

import com.akto.proto.generated.threat_detection.message.malicious_event.dashboard.v1.DashboardMaliciousEventMessage;
import com.akto.proto.generated.threat_detection.message.threat_actor.dashboard.v1.DashboardThreatActor;
import com.akto.proto.generated.threat_detection.message.threat_api.dashboard.v1.DashboardThreatApi;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchAlertFiltersRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchAlertFiltersResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListThreatActorResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListThreatActorsRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListThreatApiRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListThreatApiResponse;
import com.akto.threat.backend.constants.MongoDBCollection;
import com.akto.threat.backend.db.MaliciousEventModel;
import com.mongodb.BasicDBObject;
import com.mongodb.client.DistinctIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bson.Document;
import org.bson.conversions.Bson;

public class DashboardService {
  private final MongoClient mongoClient;

  public DashboardService(MongoClient mongoClient) {
    this.mongoClient = mongoClient;
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

  public FetchAlertFiltersResponse fetchAlertFilters(
      String accountId, FetchAlertFiltersRequest request) {
    MongoCollection<MaliciousEventModel> coll =
        this.mongoClient
            .getDatabase(accountId)
            .getCollection("malicious_events", MaliciousEventModel.class);

    Set<String> actors =
        DashboardService.<String>findDistinctFields(coll, "actor", String.class, Filters.empty());
    Set<String> urls =
        DashboardService.<String>findDistinctFields(
            coll, "latestApiEndpoint", String.class, Filters.empty());

    return FetchAlertFiltersResponse.newBuilder().addAllActors(actors).addAllUrls(urls).build();
  }

  public ListMaliciousRequestsResponse listMaliciousRequests(
      String accountId, ListMaliciousRequestsRequest request) {
    int limit = request.getLimit();
    int skip = request.hasSkip() ? request.getSkip() : 0;

    MongoCollection<MaliciousEventModel> coll =
        this.mongoClient
            .getDatabase(accountId)
            .getCollection(
                MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS, MaliciousEventModel.class);

    BasicDBObject query = new BasicDBObject();
    try (MongoCursor<MaliciousEventModel> cursor =
        coll.find(query)
            .sort(new BasicDBObject("detectedAt", -1))
            .skip(skip)
            .limit(limit)
            .cursor()) {
      List<DashboardMaliciousEventMessage> maliciousEvents = new ArrayList<>();
      while (cursor.hasNext()) {
        MaliciousEventModel evt = cursor.next();
        maliciousEvents.add(
            DashboardMaliciousEventMessage.newBuilder()
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
                .build());
      }
      return ListMaliciousRequestsResponse.newBuilder()
          .setTotal(maliciousEvents.size())
          .addAllMaliciousEvents(maliciousEvents)
          .build();
    }
  }

  public ListThreatActorResponse listThreatActors(
      String accountId, ListThreatActorsRequest request) {
    int skip = request.hasSkip() ? request.getSkip() : 0;
    int limit = request.getLimit();
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
                .append("discoveredAt", new Document("$last", "$detectedAt"))));
    pipeline.add(new Document("$skip", skip));
    pipeline.add(new Document("$limit", limit));

    List<DashboardThreatActor> actors = new ArrayList<>();
    try (MongoCursor<Document> cursor = coll.aggregate(pipeline).cursor()) {
      while (cursor.hasNext()) {
        Document doc = cursor.next();
        actors.add(
            DashboardThreatActor.newBuilder()
                .setId(doc.getString("_id"))
                .setLatestApiEndpoint(doc.getString("latestApiEndpoint"))
                .setLatestApiMethod(doc.getString("latestApiMethod"))
                .setLatestApiIp(doc.getString("latestApiIp"))
                .setDiscoveredAt(doc.getLong("discoveredAt"))
                .build());
      }
    }

    return ListThreatActorResponse.newBuilder()
        .addAllActors(actors)
        .setTotal(actors.size())
        .build();
  }

  public ListThreatApiResponse listThreatApis(String accountId, ListThreatApiRequest request) {
    int skip = request.hasSkip() ? request.getSkip() : 0;
    int limit = request.getLimit();
    MongoCollection<Document> coll =
        this.mongoClient
            .getDatabase(accountId)
            .getCollection(MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS, Document.class);

    List<Document> pipeline = new ArrayList<>();
    pipeline.add(
        new Document(
            "$sort",
            new Document("latestApiEndpoint", 1)
                .append("latestApiMethod", 1)
                .append("detectedAt", -1))); // sort
    pipeline.add(
        new Document(
            "$group",
            new Document(
                    "_id",
                    new Document("endpoint", "$latestApiEndpoint")
                        .append("method", "$latestApiMethod"))
                .append("discoveredAt", new Document("$last", "$detectedAt"))
                .append("distinctActors", new Document("$addToSet", "$actor"))));
    pipeline.add(new Document("$skip", skip));
    pipeline.add(new Document("$limit", limit));

    List<DashboardThreatApi> apis = new ArrayList<>();
    try (MongoCursor<Document> cursor = coll.aggregate(pipeline).cursor()) {
      while (cursor.hasNext()) {
        Document doc = cursor.next();
        Document agg = (Document) doc.get("_id");
        apis.add(
            DashboardThreatApi.newBuilder()
                .setEndpoint(agg.getString("endpoint"))
                .setMethod(agg.getString("method"))
                .setDiscoveredAt(doc.getLong("discoveredAt"))
                .setActorsCount(doc.getList("distinctActors", String.class).size())
                .build());
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    return ListThreatApiResponse.newBuilder().addAllApis(apis).setTotal(apis.size()).build();
  }
}
