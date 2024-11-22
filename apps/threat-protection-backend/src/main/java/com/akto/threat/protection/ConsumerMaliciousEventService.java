package com.akto.threat.protection;

import java.util.ArrayList;
import java.util.List;

import com.akto.proto.threat_protection.consumer_service.v1.ConsumerServiceGrpc;
import com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventRequest;
import com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventResponse;
import com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventRequest;
import com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventResponse;
import com.akto.proto.threat_protection.consumer_service.v1.SmartEvent;
import com.akto.threat.protection.db.MaliciousEventModel;
import com.akto.threat.protection.db.SmartEventModel;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.WriteModel;

import io.grpc.stub.StreamObserver;

public class ConsumerMaliciousEventService
        extends ConsumerServiceGrpc.ConsumerServiceImplBase {

    private MongoClient mongoClient;

    public ConsumerMaliciousEventService(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    @Override
    public void saveMaliciousEvent(
            SaveMaliciousEventRequest request,
            StreamObserver<SaveMaliciousEventResponse> responseObserver) {

        List<WriteModel<MaliciousEventModel>> bulkUpdates = new ArrayList<>();
        request.getEventsList().forEach(event -> {
            bulkUpdates.add(
                    new InsertOneModel<>(
                            new MaliciousEventModel(
                                    event.getFilterId(),
                                    event.getActorId(),
                                    event.getIp(),
                                    event.getUrl(),
                                    event.getMethod(),
                                    event.getPayload(),
                                    event.getTimestamp())));
        });
        this.mongoClient.getDatabase(request.getAccountId() + "")
                .getCollection("malicious_events", MaliciousEventModel.class)
                .bulkWrite(bulkUpdates, new BulkWriteOptions().ordered(false));
        responseObserver.onNext(SaveMaliciousEventResponse.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void saveSmartEvent(
            SaveSmartEventRequest request,
            StreamObserver<SaveSmartEventResponse> responseObserver) {
        SmartEvent event = request.getEvent();
        this.mongoClient.getDatabase(request.getAccountId() + "")
                .getCollection("smart_events", SmartEventModel.class)
                .insertOne(
                        new SmartEventModel(
                                event.getFilterId(),
                                event.getActorId(),
                                event.getDetectedAt()));
        responseObserver.onNext(SaveSmartEventResponse.newBuilder().build());
        responseObserver.onCompleted();
    }
}
