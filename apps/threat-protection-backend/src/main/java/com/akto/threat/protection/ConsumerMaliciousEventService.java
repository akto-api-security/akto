package com.akto.threat.protection;

import java.util.ArrayList;
import java.util.List;

import com.akto.proto.threat_protection.service.consumer_service.v1.ConsumerServiceGrpc;
import com.akto.proto.threat_protection.service.consumer_service.v1.RecordAlertRequest;
import com.akto.proto.threat_protection.service.consumer_service.v1.RecordAlertResponse;
import com.akto.threat.protection.db.MaliciousEventModel;
import com.akto.threat.protection.db.SmartEventModel;
import com.akto.threat.protection.interceptors.Constants;
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
  public void recordAlert(
      RecordAlertRequest request, StreamObserver<RecordAlertResponse> responseObserver) {

    String actor = request.getActor();
    String filterId = request.getFilterId();
    List<WriteModel<MaliciousEventModel>> bulkUpdates = new ArrayList<>();
    request
        .getSampleDataList()
        .forEach(
            event -> {
              bulkUpdates.add(
                  new InsertOneModel<>(
                      new MaliciousEventModel(
                          filterId,
                          actor,
                          event.getIp(),
                          event.getUrl(),
                          event.getMethod(),
                          event.getPayload(),
                          event.getTimestamp())));
            });
    int accountId = Constants.ACCOUNT_ID_CONTEXT_KEY.get();
    this.mongoClient
        .getDatabase(accountId + "")
        .getCollection("malicious_events", MaliciousEventModel.class)
        .bulkWrite(bulkUpdates, new BulkWriteOptions().ordered(false));

    this.mongoClient
        .getDatabase(accountId + "")
        .getCollection("smart_events", SmartEventModel.class)
        .insertOne(
            new SmartEventModel(
                filterId, actor, request.getTotalEvents(), request.getDetectedAt()));
    responseObserver.onNext(RecordAlertResponse.newBuilder().build());
    responseObserver.onCompleted();
  }
}
