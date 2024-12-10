package com.akto.threat.protection.service;

import java.util.ArrayList;
import java.util.List;

import com.akto.dto.type.URLMethods;
import com.akto.proto.threat_protection.message.malicious_event.v1.MaliciousEvent;
import com.akto.proto.threat_protection.message.sample_request.v1.SampleMaliciousRequest;
import com.akto.proto.threat_protection.service.malicious_alert_service.v1.MaliciousEventServiceGrpc;
import com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordMaliciousEventRequest;
import com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordMaliciousEventResponse;
import com.akto.threat.protection.db.AggregateSampleMaliciousEventModel;
import com.akto.threat.protection.db.MaliciousEventModel;
import com.akto.threat.protection.interceptors.Constants;
import com.akto.threat.protection.utils.KafkaUtils;
import com.mongodb.client.model.WriteModel;
import io.grpc.stub.StreamObserver;

public class MaliciousEventService extends MaliciousEventServiceGrpc.MaliciousEventServiceImplBase {

  public MaliciousEventService() {}

  @Override
  public void recordMaliciousEvent(
      RecordMaliciousEventRequest request,
      StreamObserver<RecordMaliciousEventResponse> responseObserver) {

    MaliciousEvent evt = request.getMaliciousEvent();
    String actor = evt.getActor();
    String filterId = evt.getFilterId();
    List<WriteModel<AggregateSampleMaliciousEventModel>> bulkUpdates = new ArrayList<>();
    int accountId = Constants.ACCOUNT_ID_CONTEXT_KEY.get();

    MaliciousEvent.EventType eventType = evt.getEventType();

    MaliciousEventModel.EventType maliciousEventType =
        MaliciousEvent.EventType.EVENT_TYPE_AGGREGATED.equals(eventType)
            ? MaliciousEventModel.EventType.AGGREGATED
            : MaliciousEventModel.EventType.SINGLE;

    MaliciousEventModel maliciousEventModel =
        MaliciousEventModel.newBuilder()
            .setDetectedAt(evt.getDetectedAt())
            .setActor(actor)
            .setFilterId(filterId)
            .setLatestApiEndpoint(evt.getLatestApiEndpoint())
            .setLatestApiMethod(URLMethods.Method.fromString(evt.getLatestApiMethod()))
            .setLatestApiOrig(evt.getLatestApiPayload())
            .setLatestApiCollectionId(evt.getLatestApiCollectionId())
            .setEventType(maliciousEventType)
            .build();

    if (MaliciousEventModel.EventType.AGGREGATED.equals(maliciousEventType)) {
      List<AggregateSampleMaliciousEventModel> events = new ArrayList<>();
      for (SampleMaliciousRequest sampleReq : request.getSampleRequestsList()) {
        events.add(
            AggregateSampleMaliciousEventModel.newBuilder()
                .setActor(actor)
                .setIp(sampleReq.getIp())
                .setUrl(sampleReq.getUrl())
                .setMethod(URLMethods.Method.fromString(sampleReq.getMethod()))
                .setOrig(sampleReq.getPayload())
                .setRequestTime(sampleReq.getTimestamp())
                .setApiCollectionId(sampleReq.getApiCollectionId())
                .setFilterId(filterId)
                .build());
      }

      KafkaUtils.insertData(events, "maliciousEvents", accountId);
    }

    KafkaUtils.insertData(maliciousEventModel, "smartEvent", accountId);

    responseObserver.onNext(RecordMaliciousEventResponse.newBuilder().build());
    responseObserver.onCompleted();
  }
}
