package com.akto.threat.detection.tasks;

import com.akto.proto.threat_protection.message.smart_event.v1.SmartEvent;
import com.akto.proto.threat_protection.service.malicious_alert_service.v1.MaliciousAlertServiceGrpc;
import com.akto.proto.threat_protection.service.malicious_alert_service.v1.MaliciousAlertServiceGrpc.MaliciousAlertServiceStub;
import com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordAlertRequest;
import com.akto.proto.threat_protection.service.malicious_alert_service.v1.RecordAlertResponse;
import com.akto.proto.threat_protection.service.malicious_alert_service.v1.SampleMaliciousEvent;
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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/*
This will send alerts to threat detection backend
 */
public class SendAlertsToBackend extends AbstractKafkaConsumerTask {

  private final SessionFactory sessionFactory;

  private final MaliciousAlertServiceStub consumerServiceStub;

  public SendAlertsToBackend(SessionFactory sessionFactory, KafkaConfig trafficConfig, String topic) {
    super(trafficConfig, topic);
    this.sessionFactory = sessionFactory;

    String target = "localhost:8980";
    ManagedChannel channel = Grpc.newChannelBuilder(target, InsecureChannelCredentials.create()).build();
    this.consumerServiceStub = MaliciousAlertServiceGrpc.newStub(channel)
        .withCallCredentials(
            new AuthToken(System.getenv("AKTO_THREAT_PROTECTION_BACKEND_TOKEN")));
  }

  ExecutorService getPollingExecutor() {
    return Executors.newSingleThreadExecutor();
  }

  private List<MaliciousEventEntity> getSampleMaliciousEvents(String actor, String filterId) {
    Session session = this.sessionFactory.openSession();
    Transaction txn = session.beginTransaction();
    try {
      return session
          .createQuery("from MaliciousEventEntity m where m.actor = :actor and m.filterId = :filterId order by m.createdAt desc", MaliciousEventEntity.class)
          .setParameter("actor", actor).setParameter("filterId", filterId)
          .setMaxResults(50)
          .getResultList();
    } finally {
      txn.commit();
      session.close();
    }
  }

  private long getTotalEvents(String actor, String filterId) {
    Session session = this.sessionFactory.openSession();
    Transaction txn = session.beginTransaction();
    try {
      return session
          .createQuery("select count(m) from MaliciousEventEntity m where m.actor = :actor and m.filterId = :filterId", Long.class)
          .setParameter("actor", actor).setParameter("filterId", filterId).uniqueResult();
    } finally {
      txn.commit();
      session.close();
    }
  }

  protected void processRecords(ConsumerRecords<String, String> records) {
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
            List<MaliciousEventEntity> sampleData = this.getSampleMaliciousEvents(evt.getActor(), evt.getFilterId());

            long totalEvents = this.getTotalEvents(evt.getActor(), evt.getFilterId());

            this.consumerServiceStub.recordAlert(
                RecordAlertRequest.newBuilder()
                    .setActor(evt.getActor())
                    .setFilterId(evt.getFilterId())
                    .setTotalEvents(totalEvents)
                    .addAllSampleData(
                        sampleData.stream()
                            .map(
                                d -> SampleMaliciousEvent.newBuilder()
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
