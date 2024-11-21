package com.akto.threat.protection;

import java.util.ArrayList;
import java.util.List;

import com.akto.dao.context.Context;
import com.akto.dao.threat_detection.SampleMaliciousRequestDao;
import com.akto.dto.threat_detection.SampleMaliciousRequest;
import com.akto.proto.threat_protection.consumer_service.v1.ConsumerServiceGrpc;
import com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventRequest;
import com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventResponse;
import com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventRequest;
import com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventResponse;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.WriteModel;

import io.grpc.stub.StreamObserver;

public class ConsumerMaliciousEventService
        extends ConsumerServiceGrpc.ConsumerServiceImplBase {

    public ConsumerMaliciousEventService() {
        // Initialize the service
    }

    private void saveMaliciousEventToMongoDB(SaveMaliciousEventRequest request) {
        // Save malicious event to MongoDB
        Context.accountId.set(request.getAccountId());

        List<WriteModel<SampleMaliciousRequest>> bulkUpdates = new ArrayList<>();
        request.getEventsList().forEach(event -> {
            bulkUpdates.add(
                    new InsertOneModel<>(
                            new SampleMaliciousRequest(
                                    event.getFilterId(),
                                    event.getActorId(),
                                    event.getIp(),
                                    event.getUrl(),
                                    event.getMethod(),
                                    event.getPayload(),
                                    event.getTimestamp())));
        });

        try {
            SampleMaliciousRequestDao.instance.bulkWrite(
                    bulkUpdates,
                    new BulkWriteOptions().ordered(false));
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public void saveMaliciousEvent(
            SaveMaliciousEventRequest request,
            StreamObserver<SaveMaliciousEventResponse> responseObserver) {
        saveMaliciousEventToMongoDB(request);
        responseObserver.onNext(SaveMaliciousEventResponse.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void saveSmartEvent(
            SaveSmartEventRequest request,
            StreamObserver<SaveSmartEventResponse> responseObserver) {
        responseObserver.onNext(SaveSmartEventResponse.newBuilder().build());
        responseObserver.onCompleted();
    }
}
