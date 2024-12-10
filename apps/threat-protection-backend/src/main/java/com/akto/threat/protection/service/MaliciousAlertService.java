package com.akto.threat.protection.service;

import java.util.ArrayList;
import java.util.List;

import com.akto.dto.type.URLMethods;
import com.akto.proto.threat_protection.service.malicious_alert_service.v1.MaliciousAlertServiceGrpc.MaliciousAlertServiceImplBase;
import com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordAlertRequest;
import com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordAlertResponse;
import com.akto.proto.threat_protection.service.malicious_alert_service.v1.SampleMaliciousEvent;
import com.akto.threat.protection.db.MaliciousEventModel;
import com.akto.threat.protection.db.SmartEventModel;
import com.akto.threat.protection.interceptors.Constants;
import com.akto.threat.protection.utils.KafkaUtils;
import com.mongodb.client.model.WriteModel;
import io.grpc.stub.StreamObserver;

public class MaliciousAlertService extends MaliciousAlertServiceImplBase {

  public MaliciousAlertService() {}

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
      events.add(
          MaliciousEventModel.newBuilder()
              .setActor(actor)
              .setIp(maliciousEvent.getIp())
              .setUrl(maliciousEvent.getUrl())
              .setMethod(URLMethods.Method.fromString(maliciousEvent.getMethod()))
              .setOrig(maliciousEvent.getPayload())
              .setRequestTime(maliciousEvent.getTimestamp())
              .setFilterId(filterId)
              .build());
    }

    KafkaUtils.insertData(events, "maliciousEvents", accountId);
    KafkaUtils.insertData(
        new SmartEventModel(filterId, actor, request.getTotalEvents(), request.getDetectedAt()),
        "smartEvent",
        accountId);

    responseObserver.onNext(RecordAlertResponse.newBuilder().build());
    responseObserver.onCompleted();
  }
}
