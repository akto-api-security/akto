package com.akto.threat.protection;

import java.util.ArrayList;
import java.util.List;

import com.akto.proto.threat_protection.consumer_service.v1.ConsumerServiceGrpc;
import com.akto.proto.threat_protection.consumer_service.v1.MaliciousEvent;
import com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventRequest;
import com.akto.proto.threat_protection.consumer_service.v1.SaveMaliciousEventResponse;
import com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventRequest;
import com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventResponse;
import com.akto.proto.threat_protection.consumer_service.v1.SmartEvent;
import com.akto.threat.protection.db.MaliciousEventModel;
import com.akto.threat.protection.db.SmartEventModel;
import com.akto.threat.protection.interceptors.Constants;
import com.akto.threat.protection.utils.KafkaUtils;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.WriteModel;

import io.grpc.stub.StreamObserver;

public class ConsumerMaliciousEventService extends ConsumerServiceGrpc.ConsumerServiceImplBase {

  private final MongoClient mongoClient;

  public ConsumerMaliciousEventService(MongoClient mongoClient) {
    this.mongoClient = mongoClient;
  }

  @Override
  public void saveMaliciousEvent(
      SaveMaliciousEventRequest request,
      StreamObserver<SaveMaliciousEventResponse> responseObserver) {

    List<MaliciousEvent> maliciousEvents = request.getEventsList();
    
    int accountId = Constants.ACCOUNT_ID_CONTEXT_KEY.get();

    KafkaUtils.insertData(maliciousEvents, "maliciousEvents", accountId);

    responseObserver.onNext(SaveMaliciousEventResponse.newBuilder().build());
    responseObserver.onCompleted();
  }

  @Override
  public void saveSmartEvent(
      SaveSmartEventRequest request, StreamObserver<SaveSmartEventResponse> responseObserver) {
    SmartEvent event = request.getEvent();
    int accountId = Constants.ACCOUNT_ID_CONTEXT_KEY.get();

    KafkaUtils.insertData(new SmartEventModel(event.getFilterId(), event.getActorId(), event.getDetectedAt()), "smartEvent", accountId);
    responseObserver.onNext(SaveSmartEventResponse.newBuilder().build());
    responseObserver.onCompleted();
  }
}
