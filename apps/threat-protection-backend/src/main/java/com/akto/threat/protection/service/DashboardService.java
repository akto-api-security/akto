package com.akto.threat.protection.service;

import com.akto.proto.threat_protection.service.dashboard_service.v1.DashboardServiceGrpc.DashboardServiceImplBase;
import com.akto.proto.threat_protection.service.dashboard_service.v1.ListMaliciousRequestsRequest;
import com.akto.proto.threat_protection.service.dashboard_service.v1.ListMaliciousRequestsResponse;
import com.akto.proto.threat_protection.service.dashboard_service.v1.MaliciousRequest;
import com.akto.threat.protection.db.MaliciousEventModel;
import com.akto.threat.protection.interceptors.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;

public class DashboardService extends DashboardServiceImplBase {
  private final MongoClient mongoClient;

  public DashboardService(MongoClient mongoClient) {
    this.mongoClient = mongoClient;
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
            .getCollection("malicious_events", MaliciousEventModel.class);

    BasicDBObject query = new BasicDBObject();
    try (MongoCursor<MaliciousEventModel> cursor =
        coll.find(query).skip(skip).limit(limit).cursor()) {
      List<MaliciousRequest> alerts = new ArrayList<>();
      while (cursor.hasNext()) {
        MaliciousEventModel evt = cursor.next();
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
