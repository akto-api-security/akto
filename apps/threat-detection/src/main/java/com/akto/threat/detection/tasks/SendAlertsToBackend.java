package com.akto.threat.detection.tasks;

import com.akto.proto.threat_protection.consumer_service.v1.*;
import com.akto.threat.detection.config.kafka.KafkaConfig;
import com.akto.threat.detection.db.malicious_event.MaliciousEventDao;
import com.akto.threat.detection.db.malicious_event.MaliciousEventModel;
import com.akto.threat.detection.dto.MessageEnvelope;
import com.akto.threat.detection.grpc.AuthToken;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import org.apache.kafka.clients.consumer.ConsumerRecords;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/*
This will read sample malicious data from kafka topic and save it to DB.
 */
public class SendAlertsToBackend extends AbstractKafkaConsumerTask {

  private final MaliciousEventDao maliciousEventDao;

  private final ConsumerServiceGrpc.ConsumerServiceStub consumerServiceStub;

  public SendAlertsToBackend(Connection conn, KafkaConfig trafficConfig, String topic) {
    super(trafficConfig, topic);
    this.maliciousEventDao = new MaliciousEventDao(conn);

    String target = "localhost:8980";
    ManagedChannel channel =
        Grpc.newChannelBuilder(target, InsecureChannelCredentials.create()).build();
    this.consumerServiceStub =
        ConsumerServiceGrpc.newStub(channel)
            .withCallCredentials(
                new AuthToken(System.getenv("AKTO_THREAT_PROTECTION_BACKEND_TOKEN")));
  }

  ExecutorService getPollingExecutor() {
    return Executors.newSingleThreadExecutor();
  }

  protected void processRecords(ConsumerRecords<String, String> records) {
    List<MaliciousEventModel> events = new ArrayList<>();
    records.forEach(
        r -> {
          String message = r.value();
          SmartEvent.Builder builder = SmartEvent.newBuilder();
          MessageEnvelope m = MessageEnvelope.unmarshal(message).orElse(null);
          if (m == null) {
            return;
          }

          try {
            JsonFormat.parser().merge(m.getData(), builder);
          } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
            return;
          }

          SmartEvent evt = builder.build();

          // Get sample data from postgres for this alert
          try {
            List<MaliciousEventModel> sampleData =
                this.maliciousEventDao.findGivenActorIdAndFilterId(
                    evt.getActorId(), evt.getFilterId(), 50);

            int totalEvents =
                this.maliciousEventDao.countTotalMaliciousEventGivenActorIdAndFilterId(
                    evt.getActorId(), evt.getFilterId());

            this.consumerServiceStub.recordAlert(
                RecordAlertRequest.newBuilder()
                    .setEvent(evt)
                    .setTotalEvents(totalEvents)
                    .addAllSampleMaliciousEvents(
                        sampleData.stream()
                            .map(
                                d ->
                                    MaliciousEvent.newBuilder()
                                        .setUrl(d.getUrl())
                                        .setMethod(d.getMethod().name())
                                        .setTimestamp(d.getTimestamp())
                                        .setPayload(d.getOrig())
                                        .setIp(d.getIp())
                                        .build())
                            .collect(Collectors.toList()))
                    .build(),
                new StreamObserver<RecordAlertResponse>() {
                  @Override
                  public void onNext(RecordAlertResponse value) {
                    // Do nothing
                  }

                  @Override
                  public void onError(Throwable t) {
                    t.printStackTrace();
                  }

                  @Override
                  public void onCompleted() {
                    // Do nothing
                  }
                });
          } catch (Exception e) {
            e.printStackTrace();
          }
        });
  }
}
