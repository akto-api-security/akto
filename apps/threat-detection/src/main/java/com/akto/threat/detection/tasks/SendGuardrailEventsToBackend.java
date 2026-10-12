package com.akto.threat.detection.tasks;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.kafka.KafkaConfig;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.proto.generated.threat_detection.service.malicious_alert_service.v1.RecordMaliciousEventRequest;
import com.akto.proto.generated.threat_detection.service.malicious_alert_service.v1.UpdateRemediationRequest;
import com.akto.testing.ApiExecutor;
import com.akto.threat.detection.utils.Utils;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;

/**
 * Drains the malicious-event buffer written by guardrails-service and forwards
 * each event to the threat backend.
 *
 * <p>Unlike {@link SendMaliciousEventsToBackend}, which reports and moves on,
 * this task never commits an event the backend has not accepted. A backend
 * outage costs latency and duplicate events; it does not cost events. That is
 * the entire reason the buffer exists.
 *
 * <p>The payload is the JSON body guardrails-service would otherwise have
 * POSTed itself, and it is forwarded byte-for-byte. Nothing here parses or
 * rewrites it beyond a validity check, so the buffered and direct paths stay
 * identical on the wire.
 *
 * <p>The buffer also carries enrichment updates (remediation, evidence line)
 * for events already in it, marked by {@link #MESSAGE_TYPE_HEADER}. Each is
 * keyed like its event, so it sits behind that event on the same partition and
 * can never reach the backend first.
 */
public class SendGuardrailEventsToBackend extends AbstractKafkaConsumerTask<byte[]> {

  private static final LoggerMaker logger =
      new LoggerMaker(SendGuardrailEventsToBackend.class, LogDb.THREAT_DETECTION);

  private static final String RECORD_MALICIOUS_EVENT_PATH =
      "/api/threat_detection/record_malicious_event";

  private static final String UPDATE_REMEDIATION_PATH =
      "/api/threat_detection/update_remediation";

  /**
   * Kafka header guardrails-service sets to the message kind. A message without
   * it is a malicious event, as everything buffered before enrichment was.
   */
  static final String MESSAGE_TYPE_HEADER = "akto-msg-type";

  static final String ENRICHMENT_UPDATE = "enrichment_update";

  private static final JsonFormat.Parser PARSER = JsonFormat.parser().ignoringUnknownFields();

  /** How long an enrichment update waits for an event this task delivered to become visible. */
  private static final long ENRICHMENT_NOT_FOUND_GRACE_MILLIS =
      Long.parseLong(
              System.getenv()
                  .getOrDefault("GUARDRAILS_THREAT_CLIENT_ENRICHMENT_GRACE_SEC", "120"))
          * 1000L;

  private static final int DELIVERED_REF_IDS_CAPACITY = 10_000;

  /**
   * Retry backoff ceiling. Must stay well under max.poll.interval.ms (Kafka's
   * 5-minute default, not overridden in KafkaConfig): the sleep happens inside
   * the poll loop, so a longer pause would have the broker evict this consumer
   * and rebalance on every retry instead of making progress.
   */
  private static final int MAX_BACKOFF_SECONDS =
      Integer.parseInt(
          System.getenv().getOrDefault("GUARDRAILS_THREAT_CLIENT_MAX_BACKOFF_SEC", "60"));

  private static final int INITIAL_BACKOFF_SECONDS = 1;

  /** Outcome of handing one buffered message to the threat backend. */
  private enum ForwardResult {
    /** Accepted. Commit it. */
    DELIVERED,
    /** Rejected in a way retrying cannot fix. Drop it and commit, or it wedges the partition. */
    DROP,
    /**
     * Backend unavailable, or an enrichment update's event is not visible yet.
     * Do not commit; the same message is redelivered.
     */
    RETRY
  }

  private int consecutiveFailedBatches = 0;

  /** refId -> delivery time of recently delivered events, oldest evicted first. */
  private final Map<String, Long> deliveredAtByRefId =
      new LinkedHashMap<String, Long>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
          return size() > DELIVERED_REF_IDS_CAPACITY;
        }
      };

  public SendGuardrailEventsToBackend(KafkaConfig kafkaConfig, String topic) {
    super(kafkaConfig, topic, SendGuardrailEventsToBackend.class.getSimpleName());
  }

  /**
   * This task commits explicitly, per partition, so that a batch which fails
   * halfway still banks the prefix it delivered.
   */
  @Override
  protected boolean shouldCommitAfterProcessing() {
    return false;
  }

  @Override
  protected void processRecords(ConsumerRecords<String, byte[]> records) {
    Map<TopicPartition, OffsetAndMetadata> delivered = new HashMap<>();
    boolean backendUnavailable = false;

    for (TopicPartition partition : records.partitions()) {
      for (ConsumerRecord<String, byte[]> record : records.records(partition)) {
        ForwardResult result = forward(record);

        if (result == ForwardResult.RETRY) {
          // Rewind to this event so the next poll redelivers it. Everything
          // before it in this partition is already in `delivered`, so the
          // successful prefix is not re-sent.
          kafkaConsumer.seek(partition, record.offset());
          backendUnavailable = true;
          break;
        }

        delivered.put(partition, new OffsetAndMetadata(record.offset() + 1));
      }
    }

    if (!delivered.isEmpty()) {
      try {
        kafkaConsumer.commitSync(delivered);
      } catch (Exception e) {
        // The events reached the backend; a failed commit only means they are
        // redelivered and duplicated.
        logger.error("Failed to commit guardrail event offsets: " + e.getMessage());
      }
    }

    if (backendUnavailable) {
      backOff();
    } else {
      consecutiveFailedBatches = 0;
    }
  }

  private ForwardResult forward(ConsumerRecord<String, byte[]> record) {
    String body = new String(record.value(), StandardCharsets.UTF_8);
    return isEnrichmentUpdate(record) ? forwardEnrichment(record, body) : forwardEvent(record, body);
  }

  private static boolean isEnrichmentUpdate(ConsumerRecord<String, byte[]> record) {
    Header type = record.headers().lastHeader(MESSAGE_TYPE_HEADER);
    return type != null
        && ENRICHMENT_UPDATE.equals(new String(type.value(), StandardCharsets.UTF_8));
  }

  private ForwardResult forwardEvent(ConsumerRecord<String, byte[]> record, String body) {
    RecordMaliciousEventRequest.Builder event = RecordMaliciousEventRequest.newBuilder();
    if (!parses(record, body, event)) {
      return ForwardResult.DROP;
    }

    ForwardResult result;
    try {
      result = classify(post(RECORD_MALICIOUS_EVENT_PATH, body));
    } catch (Exception e) {
      return ForwardResult.RETRY;
    }

    String refId = event.getMaliciousEvent().getRefId();
    if (result == ForwardResult.DELIVERED && !refId.isEmpty()) {
      deliveredAtByRefId.put(refId, System.currentTimeMillis());
    }
    return result;
  }

  private ForwardResult forwardEnrichment(ConsumerRecord<String, byte[]> record, String body) {
    UpdateRemediationRequest.Builder update = UpdateRemediationRequest.newBuilder();
    if (!parses(record, body, update)) {
      return ForwardResult.DROP;
    }

    OriginalHttpResponse response;
    try {
      response = post(UPDATE_REMEDIATION_PATH, body);
    } catch (Exception e) {
      return ForwardResult.RETRY;
    }

    if (response.getStatusCode() == 404) {
      return enrichedEventNotFound(update.getRefId());
    }
    return classify(response);
  }

  /**
   * The backend inserts events asynchronously, so an update can briefly beat an
   * event this task already delivered ahead of it on the same partition. Wait
   * for that one only. Any other miss means the event was dropped or never
   * buffered here, and waiting would stall the partition for nothing.
   */
  private ForwardResult enrichedEventNotFound(String refId) {
    Long deliveredAt = deliveredAtByRefId.get(refId);
    if (deliveredAt != null
        && System.currentTimeMillis() - deliveredAt < ENRICHMENT_NOT_FOUND_GRACE_MILLIS) {
      return ForwardResult.RETRY;
    }
    logger.error("Dropping enrichment update for unknown guardrail event, refId: " + refId);
    return ForwardResult.DROP;
  }

  /**
   * Drops bodies that are not the expected request at all, so a malformed body
   * cannot block the partition forever. Unknown fields are NOT a reason to
   * drop: guardrails-service and the backend gain fields before this image is
   * upgraded, and the backend is the authority on them. A body the backend
   * truly rejects still comes back 4xx and is dropped there.
   */
  private boolean parses(ConsumerRecord<String, byte[]> record, String body, Message.Builder builder) {
    try {
      PARSER.merge(body, builder);
      return true;
    } catch (Exception e) {
      logger.error(
          "Dropping unparseable guardrail message at offset "
              + record.offset()
              + " partition "
              + record.partition()
              + ": "
              + e.getMessage());
      return false;
    }
  }

  private OriginalHttpResponse post(String path, String body) throws Exception {
    Map<String, List<String>> headers = Utils.buildHeaders();
    headers.put("x-akto-ignore", Collections.singletonList("true"));
    headers.put("Content-Type", Collections.singletonList("application/json"));

    OriginalHttpRequest request =
        new OriginalHttpRequest(
            Utils.getThreatProtectionBackendUrl() + path, "", "POST", body, headers, "");
    return ApiExecutor.sendRequest(request, true, null, false, null);
  }

  private ForwardResult classify(OriginalHttpResponse response) {
    int statusCode = response.getStatusCode();

    if (statusCode >= 200 && statusCode < 300) {
      return ForwardResult.DELIVERED;
    }

    // 4xx means the backend will never accept this body - except 429, where
    // it is telling us to slow down.
    if (statusCode >= 400 && statusCode < 500 && statusCode != 429) {
      logger.error(
          "Dropping guardrail message rejected by threat backend, statusCode: "
              + statusCode
              + " body: "
              + response.getBody());
      return ForwardResult.DROP;
    }

    return ForwardResult.RETRY;
  }

  /**
   * Sleeps between poll cycles while the backend is down, doubling up to
   * MAX_BACKOFF_SECONDS.
   *
   * <p>Logging is deliberately sparse. Every log line here is an async POST to
   * cyborg (LoggerMaker -> ClientActor.insertProtectionLog, a 50-thread pool
   * with an unbounded queue), and this is the one code path that runs hot
   * precisely when downstream services are unhealthy. Logging every attempt
   * would grow that queue faster than it drains.
   */
  private void backOff() {
    consecutiveFailedBatches++;

    int backoffSeconds =
        (int) Math.min((long) MAX_BACKOFF_SECONDS, INITIAL_BACKOFF_SECONDS * (1L << Math.min(consecutiveFailedBatches - 1, 20)));

    if (consecutiveFailedBatches == 1 || consecutiveFailedBatches % 10 == 0) {
      logger.errorAndAddToDb(
          "Threat backend unavailable, guardrail events are buffering. Consecutive failed batches: "
              + consecutiveFailedBatches
              + ", backing off "
              + backoffSeconds
              + "s");
    }

    try {
      Thread.sleep(backoffSeconds * 1000L);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
