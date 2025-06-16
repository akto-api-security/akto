package com.akto.threat.detection.tasks;

import com.akto.ProtoMessageUtils;
import com.akto.kafka.KafkaConfig;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.proto.generated.threat_detection.message.malicious_event.v1.MaliciousEventKafkaEnvelope;
import com.akto.proto.generated.threat_detection.message.malicious_event.v1.MaliciousEventMessage;
import com.akto.proto.generated.threat_detection.service.malicious_alert_service.v1.RecordMaliciousEventRequest;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.kafka.clients.consumer.ConsumerRecords;

/*
This will send alerts to threat detection backend
 */
public class SendMaliciousEventsToBackend extends AbstractKafkaConsumerTask<byte[]> {

  private final CloseableHttpClient httpClient;
  private static final LoggerMaker logger = new LoggerMaker(SendMaliciousEventsToBackend.class, LogDb.THREAT_DETECTION);
  private static final PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
  

  public SendMaliciousEventsToBackend(KafkaConfig trafficConfig, String topic) {
    super(trafficConfig, topic);
    connManager.setMaxTotal(100);
    connManager.setDefaultMaxPerRoute(100);
    this.httpClient = HttpClients.custom()
        .setConnectionManager(connManager)
        .setKeepAliveStrategy((response, context) -> 30_000)
        .build();
  }

  protected void processRecords(ConsumerRecords<String, byte[]> records) {
    records.forEach(
        r -> {
          MaliciousEventKafkaEnvelope envelope;
          try {
            envelope = MaliciousEventKafkaEnvelope.parseFrom(r.value());
          } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
            return;
          }

          if (envelope == null) {
            return;
          }

          try {
            MaliciousEventMessage evt = envelope.getMaliciousEvent();

            RecordMaliciousEventRequest.Builder reqBuilder =
                RecordMaliciousEventRequest.newBuilder().setMaliciousEvent(evt);

            RecordMaliciousEventRequest maliciousEventRequest = reqBuilder.build();
            String url = System.getenv("AKTO_THREAT_PROTECTION_BACKEND_URL");
            String token = System.getenv("AKTO_THREAT_PROTECTION_BACKEND_TOKEN");
            ProtoMessageUtils.toString(maliciousEventRequest)
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
                        logger.debugAndAddToDb("sending malicious event to threat backend for url " + evt.getLatestApiEndpoint() + " filterId " + evt.getFilterId() + " eventType " + evt.getEventType().toString());
                        this.httpClient.execute(req);
                      } catch (IOException e) {
                        logger.errorAndAddToDb("error sending malicious event " + e.getMessage());
                        e.printStackTrace();
                      }

                    });
          } catch (Exception e) {
            e.printStackTrace();
          }
        });
  }
}
