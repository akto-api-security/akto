package com.akto.threat.detection.tasks;

import com.akto.dto.type.URLMethods;
import com.akto.proto.threat_protection.message.malicious_event.v1.MaliciousEvent;
import com.akto.threat.detection.config.kafka.KafkaConfig;
import com.akto.threat.detection.db.malicious_event.MaliciousEventDao;
import com.akto.threat.detection.db.malicious_event.MaliciousEventModel;
import com.akto.threat.detection.dto.MessageEnvelope;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import org.apache.kafka.clients.consumer.ConsumerRecords;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/*
This will read sample malicious data from kafka topic and save it to DB.
 */
public class FlushSampleDataTask extends AbstractKafkaConsumerTask {

  private final MaliciousEventDao maliciousEventDao;

  public FlushSampleDataTask(Connection conn, KafkaConfig trafficConfig, String topic) {
    super(trafficConfig, topic);
    this.maliciousEventDao = new MaliciousEventDao(conn);
  }

  @Override
  ExecutorService getPollingExecutor() {
    return Executors.newSingleThreadExecutor();
  }

  protected void processRecords(ConsumerRecords<String, String> records) {
    List<MaliciousEventModel> events = new ArrayList<>();
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

          events.add(
              MaliciousEventModel.newBuilder()
                  .setActorId(m.getAccountId())
                  .setFilterId(evt.getFilterId())
                  .setUrl(evt.getUrl())
                  .setMethod(URLMethods.Method.fromString(evt.getMethod()))
                  .setTimestamp(evt.getTimestamp())
                  .setOrig(evt.getPayload())
                  .setIp(evt.getIp())
                  .build());
        });

    try {
      this.maliciousEventDao.batchInsert(events);
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }
}
