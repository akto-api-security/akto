package com.akto.threat.protection.service;

import com.akto.proto.threat_protection.message.malicious_event.dashboard.v1.DashboardMaliciousEventMessage;
import com.akto.proto.threat_protection.service.dashboard_service.v1.DashboardServiceGrpc.DashboardServiceImplBase;
import com.akto.proto.threat_protection.service.dashboard_service.v1.FetchAlertFiltersRequest;
import com.akto.proto.threat_protection.service.dashboard_service.v1.FetchAlertFiltersResponse;
import com.akto.proto.threat_protection.service.dashboard_service.v1.ListMaliciousRequestsRequest;
import com.akto.proto.threat_protection.service.dashboard_service.v1.ListMaliciousRequestsResponse;
import com.akto.threat.protection.constants.MongoDBCollection;
import com.akto.threat.protection.db.AggregateSampleMaliciousEventModel;
import com.akto.threat.protection.db.MaliciousEventModel;
import com.akto.threat.protection.interceptors.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.DistinctIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bson.conversions.Bson;

public class DashboardService extends DashboardServiceImplBase {
  private final MongoClient mongoClient;

  public DashboardService(MongoClient mongoClient) {
    this.mongoClient = mongoClient;
  }

  private static <T> Set<T> findDistinctFields(
      MongoCollection<AggregateSampleMaliciousEventModel> coll,
      String fieldName,
      Class<T> tClass,
      Bson filters) {
    DistinctIterable<T> r = coll.distinct(fieldName, filters, tClass);
    Set<T> result = new HashSet<>();
    MongoCursor<T> cursor = r.cursor();
    while (cursor.hasNext()) {
      result.add(cursor.next());
    }
    return result;
  }

  @Override
  public void fetchAlertFilters(
      FetchAlertFiltersRequest request,
      StreamObserver<FetchAlertFiltersResponse> responseObserver) {
    int accountId = Constants.ACCOUNT_ID_CONTEXT_KEY.get();
    MongoCollection<AggregateSampleMaliciousEventModel> coll =
        this.mongoClient
            .getDatabase(accountId + "")
            .getCollection("malicious_events", AggregateSampleMaliciousEventModel.class);

    Set<String> actors =
        DashboardService.<String>findDistinctFields(coll, "actor", String.class, Filters.empty());
    Set<String> urls =
        DashboardService.<String>findDistinctFields(coll, "url", String.class, Filters.empty());

    FetchAlertFiltersResponse response =
        FetchAlertFiltersResponse.newBuilder().addAllActors(actors).addAllUrls(urls).build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void listMaliciousRequests(
      ListMaliciousRequestsRequest request,
      StreamObserver<ListMaliciousRequestsResponse> responseObserver) {
    int accountId = Constants.ACCOUNT_ID_CONTEXT_KEY.get();

    int page = request.hasPage() ? request.getPage() : 1;
    int limit = request.getLimit();
    int skip = (page - 1) * limit;

    MongoCollection<MaliciousEventModel> coll =
        this.mongoClient
            .getDatabase(accountId + "")
            .getCollection(
                MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS, MaliciousEventModel.class);

    BasicDBObject query = new BasicDBObject();
    try (MongoCursor<MaliciousEventModel> cursor =
        coll.find(query).skip(skip).limit(limit).cursor()) {
      List<DashboardMaliciousEventMessage> maliciousEvents = new ArrayList<>();
      while (cursor.hasNext()) {
        MaliciousEventModel evt = cursor.next();
        maliciousEvents.add(
            DashboardMaliciousEventMessage.newBuilder()
                .setActor(evt.getActor())
                .setFilterId(evt.getFilterId())
                .setFilterId(evt.getFilterId())
                .setId(evt.getId())
                .setIp(evt.getLatestIp())
                .setCountry(evt.getCountry())
                .setPayload(evt.getLatestApiOrig())
                .setEndpoint(evt.getLatestApiEndpoint())
                .setMethod(evt.getLatestApiMethod().name())
                .setDetectedAt(evt.getDetectedAt())
                .build());
      }
      ListMaliciousRequestsResponse response =
          ListMaliciousRequestsResponse.newBuilder()
              .setPage(page)
              .setTotal(maliciousEvents.size())
              .addAllMaliciousEvents(maliciousEvents)
              .build();
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }
  }
}
