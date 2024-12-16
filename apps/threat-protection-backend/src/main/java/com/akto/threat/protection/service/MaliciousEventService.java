package com.akto.threat.protection.service;

import com.akto.dto.type.URLMethods;
import com.akto.kafka.Kafka;
import com.akto.kafka.KafkaConfig;
import com.akto.proto.threat_protection.message.malicious_event.event_type.v1.EventType;
import com.akto.proto.threat_protection.message.malicious_event.v1.MaliciousEventMessage;
import com.akto.proto.threat_protection.message.sample_request.v1.SampleMaliciousRequest;
import com.akto.proto.threat_protection.service.malicious_alert_service.v1.MaliciousEventServiceGrpc;
import com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordMaliciousEventRequest;
import com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordMaliciousEventResponse;
import com.akto.threat.protection.constants.KafkaTopic;
import com.akto.threat.protection.constants.MongoDBCollection;
import com.akto.threat.protection.db.AggregateSampleMaliciousEventModel;
import com.akto.threat.protection.db.MaliciousEventModel;
import com.akto.threat.protection.interceptors.Constants;
import com.akto.threat.protection.utils.KafkaUtils;
import com.mongodb.client.model.WriteModel;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;

public class MaliciousEventService extends MaliciousEventServiceGrpc.MaliciousEventServiceImplBase {

  private final Kafka kafka;

  public MaliciousEventService(KafkaConfig kafkaConfig) {
    this.kafka = new Kafka(kafkaConfig);
  }

  @Override
  public void recordMaliciousEvent(
      RecordMaliciousEventRequest request,
      StreamObserver<RecordMaliciousEventResponse> responseObserver) {

    MaliciousEventMessage evt = request.getMaliciousEvent();
    String actor = evt.getActor();
    String filterId = evt.getFilterId();
    List<WriteModel<AggregateSampleMaliciousEventModel>> bulkUpdates = new ArrayList<>();
    int accountId = Constants.ACCOUNT_ID_CONTEXT_KEY.get();

    EventType eventType = evt.getEventType();

    MaliciousEventModel.EventType maliciousEventType =
        EventType.EVENT_TYPE_AGGREGATED.equals(eventType)
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
            .setLatestApiIp(evt.getLatestApiIp())
            .setCountry("US")
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

      this.kafka.send(
          KafkaUtils.generateMsg(
              events,
              MongoDBCollection.ThreatDetection.AGGREGATE_SAMPLE_MALICIOUS_REQUESTS,
              accountId),
          KafkaTopic.ThreatDetection.INTERNAL_DB_MESSAGES);
    }

    this.kafka.send(
        KafkaUtils.generateMsg(
            maliciousEventModel, MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS, accountId),
        KafkaTopic.ThreatDetection.INTERNAL_DB_MESSAGES);

    responseObserver.onNext(RecordMaliciousEventResponse.newBuilder().build());
    responseObserver.onCompleted();
  }
}
