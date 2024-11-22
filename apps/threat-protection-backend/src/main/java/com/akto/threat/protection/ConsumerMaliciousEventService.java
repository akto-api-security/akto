package com.akto.threat.protection;

import com.akto.proto.threat_protection.consumer_service.v1.ConsumerServiceGrpc;
import com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventRequest;
import com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventResponse;
import com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventRequest;
import com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventResponse;
import com.akto.threat.protection.db.DBService;
import io.grpc.stub.StreamObserver;

public class ConsumerMaliciousEventService
        extends ConsumerServiceGrpc.ConsumerServiceImplBase {

    private final DBService dbService;

    public ConsumerMaliciousEventService(DBService dbService) {
        this.dbService = dbService;
    }

    @Override
    public void saveMaliciousEvent(
            SaveMaliciousEventRequest request,
            StreamObserver<SaveMaliciousEventResponse> responseObserver) {
        this.dbService.saveMaliciousEvents(request.getAccountId() + "", request.getEventsList());
        responseObserver.onNext(SaveMaliciousEventResponse.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void saveSmartEvent(
            SaveSmartEventRequest request,
            StreamObserver<SaveSmartEventResponse> responseObserver) {
        this.dbService.saveSmartEvent(request.getAccountId() + "", request.getEvent());
        responseObserver.onNext(SaveSmartEventResponse.newBuilder().build());
        responseObserver.onCompleted();
    }
}
