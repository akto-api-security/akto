package com.akto.threat.protection;

import java.util.ArrayList;
import java.util.List;

import com.akto.proto.threat_protection.message.malicious_event.v1.MaliciousEvent;
import com.akto.proto.threat_protection.service.malicious_alert_service.v1.MaliciousAlertServiceGrpc.MaliciousAlertServiceImplBase;
import com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordAlertRequest;
import com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordAlertResponse;
import com.akto.proto.threat_protection.service.malicious_alert_service.v1.SampleMaliciousEvent;
import com.akto.threat.protection.db.MaliciousEventModel;
import com.akto.threat.protection.db.SmartEventModel;
import com.akto.threat.protection.interceptors.Constants;
import com.akto.threat.protection.utils.KafkaUtils;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.WriteModel;

import io.grpc.stub.StreamObserver;
import javassist.tools.rmi.Sample;

public class MaliciousAlertService extends MaliciousAlertServiceImplBase {

  private final MongoClient mongoClient;

  public MaliciousAlertService(MongoClient mongoClient) {
    this.mongoClient = mongoClient;
  }

  @Override
  public void recordAlert(
      RecordAlertRequest request, StreamObserver<RecordAlertResponse> responseObserver) {

    String actor = request.getActor();
    String filterId = request.getFilterId();
    List<WriteModel<MaliciousEventModel>> bulkUpdates = new ArrayList<>();
    int accountId = Constants.ACCOUNT_ID_CONTEXT_KEY.get();

    List<SampleMaliciousEvent> maliciousEvents = request.getSampleDataList();
    List<MaliciousEventModel> events = new ArrayList<>();
    for (SampleMaliciousEvent maliciousEvent : maliciousEvents) {
        events.add(new MaliciousEventModel(filterId,
                actor,
                maliciousEvent.getIp(),
                maliciousEvent.getUrl(),
                maliciousEvent.getMethod(),
                maliciousEvent.getPayload(),
                maliciousEvent.getTimestamp()));
    }

    KafkaUtils.insertData(events, "maliciousEvents", accountId);
    KafkaUtils.insertData(new SmartEventModel(filterId, actor, request.getTotalEvents(), request.getDetectedAt()), "smartEvent", accountId);


    responseObserver.onNext(RecordAlertResponse.newBuilder().build());
    responseObserver.onCompleted();
  }

}
