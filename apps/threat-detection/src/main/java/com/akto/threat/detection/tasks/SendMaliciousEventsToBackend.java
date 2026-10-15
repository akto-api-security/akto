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
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

/*
This will send alerts to threat detection backend
 */
public class SendMaliciousEventsToBackend extends AbstractKafkaConsumerTask<byte[]> {

  private static final LoggerMaker logger = new LoggerMaker(SendMaliciousEventsToBackend.class, LogDb.THREAT_DETECTION);

  private static final long STATS_INTERVAL_MS = 60_000;

  // Per-window forwarding stats, logged once per STATS_INTERVAL_MS. Only touched
  // from the polling thread, so plain fields are enough.
  private long statsWindowStartMs = System.currentTimeMillis();
  private long forwardedCount = 0;
  private long failedCount = 0;
  private long postLatencySumMs = 0;
  private long postLatencyMaxMs = 0;
  // Age of a record = now - its Kafka timestamp (when the detector produced it),
  // i.e. how long it sat in the alerts topic before being forwarded.
  private long recordAgeMaxMs = 0;
  private long lastRecordAgeMs = -1;

  public SendMaliciousEventsToBackend(KafkaConfig trafficConfig, String topic) {
    super(trafficConfig, topic, SendMaliciousEventsToBackend.class.getSimpleName());
  }

  protected void processRecords(ConsumerRecords<String, byte[]> records) {
    forwardRecords(records);
    maybeLogStats();
  }

  private void forwardRecords(ConsumerRecords<String, byte[]> records) {
    records.forEach(
        r -> {
          long recordAgeMs = System.currentTimeMillis() - r.timestamp();
          lastRecordAgeMs = recordAgeMs;
          recordAgeMaxMs = Math.max(recordAgeMaxMs, recordAgeMs);

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
                      long postStartMs = System.currentTimeMillis();
                      try {
                        logger.debugAndAddToDb("sending malicious event to threat backend for url " + evt.getLatestApiEndpoint() + " filterId " + evt.getFilterId() + " eventType " + evt.getEventType().toString());
                        OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
                        String responsePayload = response.getBody();
                        if (!Arrays.asList(200, 202).contains(response.getStatusCode()) || responsePayload == null) {
                          failedCount++;
                          logger.errorAndAddToDb("statusCode: " +  response.getStatusCode() + " in record_malicious_event API");
                        } else {
                          forwardedCount++;
                        }
                      } catch (Exception e) {
                        failedCount++;
                        logger.errorAndAddToDb("error sending malicious event " + e.getMessage());
                        e.printStackTrace();
                      } finally {
                        long postLatencyMs = System.currentTimeMillis() - postStartMs;
                        postLatencySumMs += postLatencyMs;
                        postLatencyMaxMs = Math.max(postLatencyMaxMs, postLatencyMs);
                      }
                    });
          } catch (Exception e) {
            e.printStackTrace();
          }
        });
  }

  /**
   * Logs alerts-topic offset lag and forwarding throughput once per STATS_INTERVAL_MS.
   *
   * <p>Events are forwarded one blocking POST at a time, so throughput is capped at
   * roughly 1000 / avgPostMs per second. If the detector produces alerts faster than
   * that, lag and record age grow steadily even though events keep reaching the UI.
   */
  private void maybeLogStats() {
    long now = System.currentTimeMillis();
    long windowMs = now - statsWindowStartMs;
    if (windowMs < STATS_INTERVAL_MS) {
      return;
    }

    try {
      long posts = forwardedCount + failedCount;
      long avgPostMs = posts == 0 ? 0 : postLatencySumMs / posts;
      String capacity = avgPostMs == 0 ? "n/a" : String.format("%.1f/s", 1000.0 / avgPostMs);

      logger.warnAndAddToDb(String.format(
          "%s stats: %s forwarded=%d failed=%d rate=%.2f/s avgPostMs=%d maxPostMs=%d maxThroughput=%s lastRecordAgeSec=%d maxRecordAgeSec=%d",
          instanceId, describeLag(), forwardedCount, failedCount, posts / (windowMs / 1000.0),
          avgPostMs, postLatencyMaxMs, capacity,
          lastRecordAgeMs < 0 ? -1 : lastRecordAgeMs / 1000, recordAgeMaxMs / 1000));
    } catch (Exception e) {
      logger.errorAndAddToDb("error logging alerts forwarder stats " + e.getMessage());
    } finally {
      statsWindowStartMs = now;
      forwardedCount = 0;
      failedCount = 0;
      postLatencySumMs = 0;
      postLatencyMaxMs = 0;
      recordAgeMaxMs = 0;
      lastRecordAgeMs = -1;
    }
  }

  private String describeLag() {
    Set<TopicPartition> assignment = kafkaConsumer.assignment();
    if (assignment.isEmpty()) {
      return "lag=unknown(no partitions assigned)";
    }
    // Same numbers kafka-consumer-groups --describe shows: LOG-END-OFFSET - CURRENT-OFFSET (committed).
    Map<TopicPartition, Long> endOffsets = kafkaConsumer.endOffsets(assignment, Duration.ofSeconds(5));
    Map<TopicPartition, OffsetAndMetadata> committed = kafkaConsumer.committed(assignment, Duration.ofSeconds(5));
    Map<Integer, String> offsetsByPartition = new TreeMap<>();
    long totalLag = 0;
    for (TopicPartition tp : assignment) {
      Long end = endOffsets.get(tp);
      if (end == null) {
        continue;
      }
      OffsetAndMetadata c = committed.get(tp);
      long committedOffset = c == null ? -1 : c.offset();
      long lag = Math.max(0, end - Math.max(0, committedOffset));
      offsetsByPartition.put(tp.partition(), "end=" + end + ",committed=" + committedOffset + ",lag=" + lag);
      totalLag += lag;
    }
    return "topic=" + kafkaTopic + " group=" + kafkaConfig.getGroupId() + " committedLag=" + totalLag + " partitions=" + offsetsByPartition;
  }
}
