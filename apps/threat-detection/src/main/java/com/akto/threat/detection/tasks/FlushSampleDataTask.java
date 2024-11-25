package com.akto.threat.detection.tasks;

import com.akto.dto.type.URLMethods;
import com.akto.proto.threat_protection.consumer_service.v1.MaliciousEvent;
import com.akto.runtime.utils.Utils;
import com.akto.threat.detection.config.kafka.KafkaConfig;
import com.akto.threat.detection.db.malicious_event.MaliciousEventDao;
import com.akto.threat.detection.db.malicious_event.MaliciousEventModel;
import com.akto.threat.detection.dto.MaliciousMessageEnvelope;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/*
This will read sample malicious data from kafka topic and save it to DB.
 */
public class FlushSampleDataTask implements Task {

  private final Connection conn;
  private static final ExecutorService pollingExecutor = Executors.newSingleThreadExecutor();
  private final Consumer<String, String> kafkaConsumer;
  private final KafkaConfig kafkaConfig;
  private final MaliciousEventDao maliciousEventDao;

  public FlushSampleDataTask(Connection conn, KafkaConfig trafficConfig) {
    this.conn = conn;
    this.kafkaConfig = trafficConfig;

    String kafkaBrokerUrl = trafficConfig.getBootstrapServers();
    String groupId = trafficConfig.getGroupId();

    Properties properties =
        Utils.configProperties(
            kafkaBrokerUrl, groupId, trafficConfig.getConsumerConfig().getMaxPollRecords());
    this.kafkaConsumer = new KafkaConsumer<>(properties);

    this.maliciousEventDao = new MaliciousEventDao(conn);
  }

  @Override
  public void run() {
    this.kafkaConsumer.subscribe(Collections.singletonList("akto.malicious"));

    pollingExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            // Poll data from Kafka topic
            while (true) {
              ConsumerRecords<String, String> records =
                  kafkaConsumer.poll(
                      Duration.ofMillis(kafkaConfig.getConsumerConfig().getPollDurationMilli()));
              if (records.isEmpty()) {
                continue;
              }

              processRecords(records);
            }
          }
        });
  }

  private void processRecords(ConsumerRecords<String, String> records) {
    List<MaliciousEventModel> events = new ArrayList<>();
    records.forEach(
        r -> {
          String message = r.value();
          MaliciousEvent.Builder builder = MaliciousEvent.newBuilder();
          MaliciousMessageEnvelope m = MaliciousMessageEnvelope.unmarshal(message).orElse(null);
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
                  .setCountry("US") // TODO: Call maxmind to get country code for IP
                  .build());
        });

    try {
      this.maliciousEventDao.batchInsert(events);
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }
}
