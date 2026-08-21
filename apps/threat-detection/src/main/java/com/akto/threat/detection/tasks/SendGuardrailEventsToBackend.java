package com.akto.threat.detection.tasks;

import com.akto.ProtoMessageUtils;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.kafka.KafkaConfig;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.proto.generated.threat_detection.service.malicious_alert_service.v1.RecordMaliciousEventRequest;
import com.akto.testing.ApiExecutor;
import com.akto.threat.detection.utils.Utils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

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
 */
public class SendGuardrailEventsToBackend extends AbstractKafkaConsumerTask<byte[]> {

  private static final LoggerMaker logger =
      new LoggerMaker(SendGuardrailEventsToBackend.class, LogDb.THREAT_DETECTION);

  private static final String RECORD_MALICIOUS_EVENT_PATH =
      "/api/threat_detection/record_malicious_event";

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

  /** Outcome of handing one event to the threat backend. */
  private enum ForwardResult {
    /** Accepted. Commit it. */
    DELIVERED,
    /** Rejected in a way retrying cannot fix. Drop it and commit, or it wedges the partition. */
    DROP,
    /** Backend unavailable. Do not commit; the same event is redelivered. */
    RETRY
  }

  private int consecutiveFailedBatches = 0;

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

    // Validate with the same strict parser the backend uses
    // (ThreatDetectionRouter -> ProtoMessageUtils). Anything rejected here
    // would come back as a 400 anyway, so dropping it now costs nothing and
    // keeps a malformed body from blocking the partition forever.
    if (!ProtoMessageUtils.toProtoMessage(RecordMaliciousEventRequest.class, body).isPresent()) {
      logger.error(
          "Dropping unparseable guardrail event at offset "
              + record.offset()
              + " partition "
              + record.partition());
      return ForwardResult.DROP;
    }

    Map<String, List<String>> headers = Utils.buildHeaders();
    headers.put("x-akto-ignore", Collections.singletonList("true"));
    headers.put("Content-Type", Collections.singletonList("application/json"));

    OriginalHttpRequest request =
        new OriginalHttpRequest(
            Utils.getThreatProtectionBackendUrl() + RECORD_MALICIOUS_EVENT_PATH,
            "",
            "POST",
            body,
            headers,
            "");

    try {
      OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
      int statusCode = response.getStatusCode();

      if (Arrays.asList(200, 202).contains(statusCode)) {
        return ForwardResult.DELIVERED;
      }

      // 4xx means the backend will never accept this body - except 429, where
      // it is telling us to slow down.
      if (statusCode >= 400 && statusCode < 500 && statusCode != 429) {
        logger.error(
            "Dropping guardrail event rejected by threat backend, statusCode: "
                + statusCode
                + " body: "
                + response.getBody());
        return ForwardResult.DROP;
      }

      return ForwardResult.RETRY;
    } catch (Exception e) {
      return ForwardResult.RETRY;
    }
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
