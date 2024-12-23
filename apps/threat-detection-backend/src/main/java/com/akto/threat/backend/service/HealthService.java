package com.akto.threat.backend.service;

import com.akto.proto.generated.health.service.v1.CheckRequest;
import com.akto.proto.generated.health.service.v1.CheckResponse;
import com.akto.proto.generated.health.service.v1.CheckResponse.ServingStatus;
import com.akto.proto.generated.health.service.v1.HealthServiceGrpc;
import io.grpc.stub.StreamObserver;

public class HealthService extends HealthServiceGrpc.HealthServiceImplBase {

  @Override
  public void check(CheckRequest request, StreamObserver<CheckResponse> responseObserver) {
    responseObserver.onNext(
        CheckResponse.newBuilder().setStatus(ServingStatus.SERVING_STATUS_SERVING).build());
    responseObserver.onCompleted();
  }
}
