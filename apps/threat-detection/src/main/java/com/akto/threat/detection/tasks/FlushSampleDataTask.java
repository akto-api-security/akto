package com.akto.threat.detection.tasks;

import com.akto.dto.type.URLMethods;
import com.akto.kafka.KafkaConfig;
import com.akto.proto.threat_protection.message.sample_request.v1.SampleMaliciousRequest;
import com.akto.threat.detection.db.entity.MaliciousEventEntity;
import com.akto.threat.detection.dto.MessageEnvelope;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

/*
This will read sample malicious data from kafka topic and save it to DB.
 */
public class FlushSampleDataTask extends AbstractKafkaConsumerTask {

  private final SessionFactory sessionFactory;

  public FlushSampleDataTask(
      SessionFactory sessionFactory, KafkaConfig trafficConfig, String topic) {
    super(trafficConfig, topic);
    this.sessionFactory = sessionFactory;
  }

  protected void processRecords(ConsumerRecords<String, String> records) {
    List<MaliciousEventEntity> events = new ArrayList<>();
    records.forEach(
        r -> {
          String message = r.value();
          SampleMaliciousRequest.Builder builder = SampleMaliciousRequest.newBuilder();
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

          SampleMaliciousRequest evt = builder.build();

          events.add(
              MaliciousEventEntity.newBuilder()
                  .setActor(m.getAccountId())
                  .setFilterId(evt.getFilterId())
                  .setUrl(evt.getUrl())
                  .setMethod(URLMethods.Method.fromString(evt.getMethod()))
                  .setTimestamp(evt.getTimestamp())
                  .setOrig(evt.getPayload())
                  .setApiCollectionId(evt.getApiCollectionId())
                  .setIp(evt.getIp())
                  .build());
        });

    Session session = this.sessionFactory.openSession();
    Transaction txn = session.beginTransaction();
    try {
      // Commit these events in 2 batches
      for (int i = 0; i < events.size(); i += 2) {
        session.persist(events.get(i));
        if (i % 50 == 0) {
          session.flush();
          session.clear();
        }
      }

      txn.commit();
    } catch (Exception e) {
      e.printStackTrace();
      txn.rollback();
    } finally {
      session.close();
    }
  }
}
