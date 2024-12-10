package com.akto.threat.protection.service;

import com.akto.proto.threat_protection.service.dashboard_service.v1.DashboardServiceGrpc.DashboardServiceImplBase;
import com.akto.proto.threat_protection.service.dashboard_service.v1.FetchAlertFiltersRequest;
import com.akto.proto.threat_protection.service.dashboard_service.v1.FetchAlertFiltersResponse;
import com.akto.proto.threat_protection.service.dashboard_service.v1.ListMaliciousRequestsRequest;
import com.akto.proto.threat_protection.service.dashboard_service.v1.ListMaliciousRequestsResponse;
import com.akto.proto.threat_protection.service.dashboard_service.v1.MaliciousRequest;
import com.akto.threat.protection.db.AggregateSampleMaliciousEventModel;
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

    MongoCollection<AggregateSampleMaliciousEventModel> coll =
        this.mongoClient
            .getDatabase(accountId + "")
            .getCollection("malicious_events", AggregateSampleMaliciousEventModel.class);

    BasicDBObject query = new BasicDBObject();
    try (MongoCursor<AggregateSampleMaliciousEventModel> cursor =
        coll.find(query).skip(skip).limit(limit).cursor()) {
      List<MaliciousRequest> alerts = new ArrayList<>();
      while (cursor.hasNext()) {
        AggregateSampleMaliciousEventModel evt = cursor.next();
        alerts.add(
            MaliciousRequest.newBuilder()
                .setActor(evt.getActor())
                .setFilterId(evt.getFilterId())
                .setFilterId(evt.getFilterId())
                .setId(evt.getId())
                .setIp(evt.getIp())
                .setCountry(evt.getCountry())
                .setOrig(evt.getOrig())
                .setUrl(evt.getUrl())
                .setMethod(evt.getMethod().name())
                .setTimestamp(evt.getRequestTime())
                .build());
      }
      ListMaliciousRequestsResponse response =
          ListMaliciousRequestsResponse.newBuilder().setPage(page).setTotal(alerts.size()).build();
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }
  }
}
