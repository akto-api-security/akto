package com.akto.threat.detection.tasks;

import com.akto.kafka.KafkaConfig;
import com.akto.proto.generated.threat_detection.message.malicious_event.event_type.v1.EventType;
import com.akto.proto.generated.threat_detection.message.malicious_event.v1.MaliciousEventMessage;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.SampleMaliciousRequest;
import com.akto.proto.generated.threat_detection.service.malicious_alert_service.v1.RecordMaliciousEventRequest;
import com.akto.proto.utils.ProtoMessageUtils;
import com.akto.threat.detection.db.entity.MaliciousEventEntity;
import com.akto.threat.detection.dto.MessageEnvelope;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

/*
This will send alerts to threat detection backend
 */
public class SendMaliciousEventsToBackend extends AbstractKafkaConsumerTask {

  private final SessionFactory sessionFactory;
  private final CloseableHttpClient httpClient;

  public SendMaliciousEventsToBackend(
      SessionFactory sessionFactory, KafkaConfig trafficConfig, String topic) {
    super(trafficConfig, topic);
    this.sessionFactory = sessionFactory;
    this.httpClient = HttpClients.createDefault();
  }

  private List<MaliciousEventEntity> getSampleMaliciousRequests(String actor, String filterId) {
    Session session = this.sessionFactory.openSession();
    Transaction txn = session.beginTransaction();
    try {
      return session
          .createQuery(
              "from MaliciousEventEntity m where m.actor = :actor and m.filterId = :filterId order"
                  + " by m.createdAt desc",
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
          MaliciousEventMessage.Builder builder = MaliciousEventMessage.newBuilder();
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

          MaliciousEventMessage evt = builder.build();

          // Get sample data from postgres for this alert
          try {
            RecordMaliciousEventRequest.Builder reqBuilder =
                RecordMaliciousEventRequest.newBuilder().setMaliciousEvent(evt);
            if (EventType.EVENT_TYPE_AGGREGATED.equals(evt.getEventType())) {
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

            String url = System.getenv("AKTO_THREAT_PROTECTION_BACKEND_URL");
            String token = System.getenv("AKTO_THREAT_PROTECTION_BACKEND_TOKEN");
            ProtoMessageUtils.toString(reqBuilder.build())
                .ifPresent(
                    msg -> {
                      StringEntity requestEntity =
                          new StringEntity(msg, ContentType.APPLICATION_JSON);
                      HttpPost req =
                          new HttpPost(
                              String.format("%s/api/threat_detection/record_malicious_event", url));
                      req.addHeader("Authorization", "Bearer " + token);
                      req.setEntity(requestEntity);
                      try {
                        System.out.println("Sending request to backend: " + msg);
                        this.httpClient.execute(req);
                      } catch (IOException e) {
                        e.printStackTrace();
                      }
                    });
          } catch (Exception e) {
            e.printStackTrace();
          }
        });
  }
}
