package com.akto.threat.detection.tasks;

import com.akto.ProtoMessageUtils;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.kafka.KafkaConfig;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.proto.generated.threat_detection.message.malicious_event.v1.MaliciousEventKafkaEnvelope;
import com.akto.proto.generated.threat_detection.message.malicious_event.v1.MaliciousEventMessage;
import com.akto.proto.generated.threat_detection.service.malicious_alert_service.v1.RecordMaliciousEventRequest;
import com.akto.testing.ApiExecutor;
import com.akto.threat.detection.utils.Utils;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecords;

/*
This will send alerts to threat detection backend
 */
public class SendMaliciousEventsToBackend extends AbstractKafkaConsumerTask<byte[]> {

  private static final LoggerMaker logger = new LoggerMaker(SendMaliciousEventsToBackend.class, LogDb.THREAT_DETECTION);

  public SendMaliciousEventsToBackend(KafkaConfig trafficConfig, String topic) {
    super(trafficConfig, topic);
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
            ProtoMessageUtils.toString(maliciousEventRequest)
                .ifPresent(
                    msg -> {
                      Map<String, List<String>> headers = Utils.buildHeaders();
                      headers.put("x-akto-ignore", Collections.singletonList("true"));
                      headers.put("Content-Type", Collections.singletonList("application/json"));
                      OriginalHttpRequest request = new OriginalHttpRequest(Utils.getThreatProtectionBackendUrl() + "/api/threat_detection/record_malicious_event", "","POST", msg, headers, "");
                      try {
                        logger.debugAndAddToDb("sending malicious event to threat backend for url " + evt.getLatestApiEndpoint() + " filterId " + evt.getFilterId() + " eventType " + evt.getEventType().toString());
                        OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
                        String responsePayload = response.getBody();
                        if (response.getStatusCode() != 200 || responsePayload == null) {
                          logger.errorAndAddToDb("non 2xx response in record_malicious_event");
                        }
                      } catch (Exception e) {
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
