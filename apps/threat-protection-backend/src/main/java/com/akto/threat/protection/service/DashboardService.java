package com.akto.threat.protection.service;

import com.akto.proto.threat_protection.service.dashboard_service.v1.ListAlertsRequest;
import com.akto.proto.threat_protection.service.dashboard_service.v1.ListAlertsResponse;
import com.akto.proto.threat_protection.service.dashboard_service.v1.DashboardServiceGrpc.DashboardServiceImplBase;
import com.mongodb.client.MongoClient;

import io.grpc.stub.StreamObserver;

public class DashboardService extends DashboardServiceImplBase {
  private final MongoClient mongoClient;

  public DashboardService(MongoClient mongoClient) {
    this.mongoClient = mongoClient;
  }

  @Override
  public void listAlerts(ListAlertsRequest request, StreamObserver<ListAlertsResponse> responseObserver) {
    throw new UnsupportedOperationException("Not implemented");
  }
}
