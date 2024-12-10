package com.akto.threat.detection.tasks;

import com.akto.proto.threat_protection.message.malicious_event.v1.MaliciousEvent;
import com.akto.proto.threat_protection.message.sample_request.v1.SampleMaliciousRequest;
import com.akto.proto.threat_protection.service.malicious_alert_service.v1.MaliciousEventServiceGrpc;
import com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordMaliciousEventRequest;
import com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordMaliciousEventResponse;
import com.akto.threat.detection.config.kafka.KafkaConfig;
import com.akto.threat.detection.db.entity.MaliciousEventEntity;
import com.akto.threat.detection.dto.MessageEnvelope;
import com.akto.threat.detection.grpc.AuthToken;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/*
This will send alerts to threat detection backend
 */
public class SendMaliciousRequestsToBackend extends AbstractKafkaConsumerTask {

  private final SessionFactory sessionFactory;

  private final MaliciousEventServiceGrpc.MaliciousEventServiceStub consumerServiceStub;

  public SendMaliciousRequestsToBackend(
      SessionFactory sessionFactory, KafkaConfig trafficConfig, String topic) {
    super(trafficConfig, topic);
    this.sessionFactory = sessionFactory;

    String target = "localhost:8980";
    ManagedChannel channel =
        Grpc.newChannelBuilder(target, InsecureChannelCredentials.create()).build();
    this.consumerServiceStub =
        MaliciousEventServiceGrpc.newStub(channel)
            .withCallCredentials(
                new AuthToken(System.getenv("AKTO_THREAT_PROTECTION_BACKEND_TOKEN")));
  }

  ExecutorService getPollingExecutor() {
    return Executors.newSingleThreadExecutor();
  }

  private List<MaliciousEventEntity> getSampleMaliciousRequests(String actor, String filterId) {
    Session session = this.sessionFactory.openSession();
    Transaction txn = session.beginTransaction();
    try {
      return session
          .createQuery(
              "from MaliciousEventEntity m where m.actor = :actor and m.filterId = :filterId order by m.createdAt desc",
              MaliciousEventEntity.class)
          .setParameter("actor", actor)
          .setParameter("filterId", filterId)
          .setMaxResults(50)
          .getResultList();
    } catch (Exception ex) {
      ex.printStackTrace();
      txn.rollback();
    } finally {
      txn.commit();
      session.close();
    }

    return Collections.emptyList();
  }

  protected void processRecords(ConsumerRecords<String, String> records) {
    records.forEach(
        r -> {
          String message = r.value();
          MaliciousEvent.Builder builder = MaliciousEvent.newBuilder();
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

          MaliciousEvent evt = builder.build();

          // Get sample data from postgres for this alert
          try {
            RecordMaliciousEventRequest.Builder reqBuilder =
                RecordMaliciousEventRequest.newBuilder().setMaliciousEvent(evt);
            if (MaliciousEvent.EventType.EVENT_TYPE_AGGREGATED.equals(evt.getEventType())) {
              List<MaliciousEventEntity> sampleData =
                  this.getSampleMaliciousRequests(evt.getActor(), evt.getFilterId());

              reqBuilder.addAllSampleRequests(
                  sampleData.stream()
                      .map(
                          d ->
                              SampleMaliciousRequest.newBuilder()
                                  .setUrl(d.getUrl())
                                  .setMethod(d.getMethod().name())
                                  .setTimestamp(d.getTimestamp())
                                  .setPayload(d.getOrig())
                                  .setIp(d.getIp())
                                  .setApiCollectionId(d.getApiCollectionId())
                                  .build())
                      .collect(Collectors.toList()));
            }

            this.consumerServiceStub.recordMaliciousEvent(
                reqBuilder.build(),
                new StreamObserver<RecordMaliciousEventResponse>() {
                  @Override
                  public void onNext(RecordMaliciousEventResponse value) {
                    // Do nothing
                  }

                  @Override
                  public void onError(Throwable t) {
                    t.printStackTrace();
                  }

                  @Override
                  public void onCompleted() {
                    // Do nothing
                    System.out.println("Completed");
                  }
                });
          } catch (Exception e) {
            e.printStackTrace();
          }
        });
  }
}
