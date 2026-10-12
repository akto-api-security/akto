package com.akto.threat.detection.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static com.akto.threat.detection.tasks.SendGuardrailEventsToBackend.ENRICHMENT_UPDATE;
import static com.akto.threat.detection.tasks.SendGuardrailEventsToBackend.MESSAGE_TYPE_HEADER;

import com.akto.dto.OriginalHttpResponse;
import com.akto.kafka.KafkaConfig;
import com.akto.kafka.KafkaConsumerConfig;
import com.akto.kafka.KafkaProducerConfig;
import com.akto.kafka.Serializer;
import com.akto.testing.ApiExecutor;
import com.akto.threat.detection.constants.KafkaTopic;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Covers the commit contract, which is the whole point of this task: an event
 * is committed only once the backend has taken it, so a backend outage delays
 * events instead of dropping them.
 */
public class SendGuardrailEventsToBackendTest {

  private static final String TOPIC = KafkaTopic.ThreatDetection.GUARDRAIL_EVENTS;
  private static final TopicPartition PARTITION = new TopicPartition(TOPIC, 0);

  /** A body the backend's strict protobuf JSON parser accepts. */
  private static final String VALID_EVENT =
      "{\"maliciousEvent\":{\"actor\":\"1.2.3.4\",\"filterId\":\"PromptInjection\","
          + "\"latestApiEndpoint\":\"/mcp/tools/call\",\"category\":\"PromptInjection\","
          + "\"subCategory\":\"DirectInjection\",\"severity\":\"CRITICAL\","
          + "\"sessionId\":\"sess-1\",\"refId\":\"ref-1\"}}";

  /** An enrichment update for VALID_EVENT, as guardrails-service buffers it. */
  private static final String ENRICHMENT_BODY =
      "{\"refId\":\"ref-1\",\"remediation\":\"rotate the key\"}";

  private SendGuardrailEventsToBackend task;
  private MockConsumer<String, byte[]> consumer;

  @BeforeEach
  public void setUp() {
    KafkaConfig config =
        KafkaConfig.newBuilder()
            .setGroupId("akto.guardrails_threat_client")
            .setBootstrapServers("localhost:9092")
            .setConsumerConfig(
                KafkaConsumerConfig.newBuilder().setMaxPollRecords(100).setPollDurationMilli(100).build())
            .setProducerConfig(KafkaProducerConfig.newBuilder().setBatchSize(1).setLingerMs(1).build())
            .setKeySerializer(Serializer.STRING)
            .setValueSerializer(Serializer.BYTE_ARRAY)
            .build();

    task = new SendGuardrailEventsToBackend(config, TOPIC);

    consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
    consumer.assign(Collections.singletonList(PARTITION));
    Map<TopicPartition, Long> beginning = new HashMap<>();
    beginning.put(PARTITION, 0L);
    consumer.updateBeginningOffsets(beginning);
    task.kafkaConsumer = consumer;
  }

  /** The base class must never blanket-commit for this task. */
  @Test
  public void shouldNeverLetBaseClassCommit() {
    assertFalse(task.shouldCommitAfterProcessing());
  }

  @Test
  public void commitsAfterBackendAccepts() {
    try (MockedStatic<ApiExecutor> api = mockStatic(ApiExecutor.class)) {
      stubResponse(api, 202, "");

      task.processRecords(recordsOf(VALID_EVENT, VALID_EVENT));

      // Both delivered -> committed past the last one.
      assertEquals(2L, consumer.committed(Collections.singleton(PARTITION)).get(PARTITION).offset());
    }
  }

  /**
   * The core guarantee: a 5xx must leave the offset uncommitted and rewind, so
   * the event is redelivered rather than lost.
   */
  @Test
  public void doesNotCommitWhenBackendIsDown() {
    try (MockedStatic<ApiExecutor> api = mockStatic(ApiExecutor.class)) {
      stubResponse(api, 503, "service unavailable");

      task.processRecords(recordsOf(VALID_EVENT));

      assertTrue(
          consumer.committed(Collections.singleton(PARTITION)).get(PARTITION) == null,
          "nothing may be committed while the backend is unavailable");
      assertEquals(0L, consumer.position(PARTITION), "must rewind to redeliver the failed event");
    }
  }

  /**
   * A batch that fails halfway still banks the prefix it delivered, so recovery
   * does not re-send what already landed.
   */
  @Test
  public void commitsDeliveredPrefixThenRewindsToTheFailure() {
    try (MockedStatic<ApiExecutor> api = mockStatic(ApiExecutor.class)) {
      api.when(() -> ApiExecutor.sendRequest(any(), anyBoolean(), eq(null), anyBoolean(), eq(null)))
          .thenReturn(response(202, ""))
          .thenReturn(response(202, ""))
          .thenReturn(response(500, "boom"));

      task.processRecords(recordsOf(VALID_EVENT, VALID_EVENT, VALID_EVENT));

      assertEquals(
          2L,
          consumer.committed(Collections.singleton(PARTITION)).get(PARTITION).offset(),
          "the two delivered events should be committed");
      assertEquals(2L, consumer.position(PARTITION), "must rewind to the first undelivered event");
    }
  }

  /**
   * A 4xx is a body the backend will never accept. Retrying it forever would
   * wedge the partition, so it is dropped and committed.
   */
  @Test
  public void dropsAndCommitsOn4xx() {
    try (MockedStatic<ApiExecutor> api = mockStatic(ApiExecutor.class)) {
      stubResponse(api, 400, "Invalid request");

      task.processRecords(recordsOf(VALID_EVENT));

      assertEquals(1L, consumer.committed(Collections.singleton(PARTITION)).get(PARTITION).offset());
    }
  }

  /** 429 is the one 4xx worth retrying. */
  @Test
  public void retries429() {
    try (MockedStatic<ApiExecutor> api = mockStatic(ApiExecutor.class)) {
      stubResponse(api, 429, "slow down");

      task.processRecords(recordsOf(VALID_EVENT));

      assertTrue(consumer.committed(Collections.singleton(PARTITION)).get(PARTITION) == null);
    }
  }

  /**
   * An unparseable body is dropped without ever reaching the backend - it would
   * come back 400 from the same parser anyway.
   */
  @Test
  public void dropsUnparseableEventWithoutCallingBackend() {
    try (MockedStatic<ApiExecutor> api = mockStatic(ApiExecutor.class)) {
      task.processRecords(recordsOf("not json at all"));

      api.verifyNoInteractions();
      assertEquals(1L, consumer.committed(Collections.singleton(PARTITION)).get(PARTITION).offset());
    }
  }

  /**
   * guardrails-service gains fields before this image is upgraded. A field this
   * build's proto does not know must still be forwarded, not dropped here.
   */
  @Test
  public void forwardsEventWithFieldUnknownToThisBuild() {
    String withNewerField = VALID_EVENT.replace("\"sessionId\"", "\"fieldFromNewerGuardrails\":\"x\",\"sessionId\"");
    try (MockedStatic<ApiExecutor> api = mockStatic(ApiExecutor.class)) {
      stubResponse(api, 202, "");

      task.processRecords(recordsOf(withNewerField));

      api.verify(() -> ApiExecutor.sendRequest(any(), anyBoolean(), eq(null), anyBoolean(), eq(null)));
      assertEquals(1L, consumer.committed(Collections.singleton(PARTITION)).get(PARTITION).offset());
    }
  }

  /** An enrichment update goes to update_remediation, not record_malicious_event. */
  @Test
  public void routesEnrichmentUpdateToUpdateRemediation() {
    try (MockedStatic<ApiExecutor> api = mockStatic(ApiExecutor.class)) {
      stubResponse(api, 200, "");

      task.processRecords(batch(Arrays.asList(record(0, ENRICHMENT_BODY, ENRICHMENT_UPDATE))));

      api.verify(
          () ->
              ApiExecutor.sendRequest(
                  argThat(r -> r.getUrl().endsWith("/api/threat_detection/update_remediation")),
                  anyBoolean(),
                  eq(null),
                  anyBoolean(),
                  eq(null)));
      assertEquals(1L, consumer.committed(Collections.singleton(PARTITION)).get(PARTITION).offset());
    }
  }

  /**
   * The backend inserts events asynchronously, so an update right behind an
   * event this task just delivered can 404 for a moment. That must be retried,
   * not dropped, or the enrichment is lost.
   */
  @Test
  public void retriesEnrichmentWhileItsDeliveredEventIsNotVisibleYet() {
    try (MockedStatic<ApiExecutor> api = mockStatic(ApiExecutor.class)) {
      api.when(() -> ApiExecutor.sendRequest(any(), anyBoolean(), eq(null), anyBoolean(), eq(null)))
          .thenReturn(response(202, ""))
          .thenReturn(response(404, "Event not found"));

      task.processRecords(
          batch(
              Arrays.asList(
                  record(0, VALID_EVENT, null), record(1, ENRICHMENT_BODY, ENRICHMENT_UPDATE))));

      assertEquals(
          1L,
          consumer.committed(Collections.singleton(PARTITION)).get(PARTITION).offset(),
          "the event is committed, the update is not");
      assertEquals(1L, consumer.position(PARTITION), "must rewind to the update");
    }
  }

  /**
   * A 404 for an event this task never delivered (dropped, or never buffered
   * here) will not resolve. Retrying would stall the partition, so drop it.
   */
  @Test
  public void dropsEnrichmentForUnknownEvent() {
    try (MockedStatic<ApiExecutor> api = mockStatic(ApiExecutor.class)) {
      stubResponse(api, 404, "Event not found");

      task.processRecords(batch(Arrays.asList(record(0, ENRICHMENT_BODY, ENRICHMENT_UPDATE))));

      assertEquals(1L, consumer.committed(Collections.singleton(PARTITION)).get(PARTITION).offset());
    }
  }

  /** A transport failure is retryable, not a rejection. */
  @Test
  public void treatsTransportFailureAsRetryable() {
    try (MockedStatic<ApiExecutor> api = mockStatic(ApiExecutor.class)) {
      api.when(() -> ApiExecutor.sendRequest(any(), anyBoolean(), eq(null), anyBoolean(), eq(null)))
          .thenThrow(new RuntimeException("connection refused"));

      task.processRecords(recordsOf(VALID_EVENT));

      assertTrue(consumer.committed(Collections.singleton(PARTITION)).get(PARTITION) == null);
      assertEquals(0L, consumer.position(PARTITION));
    }
  }

  private void stubResponse(MockedStatic<ApiExecutor> api, int statusCode, String body) {
    api.when(() -> ApiExecutor.sendRequest(any(), anyBoolean(), eq(null), anyBoolean(), eq(null)))
        .thenReturn(response(statusCode, body));
  }

  private OriginalHttpResponse response(int statusCode, String body) {
    return new OriginalHttpResponse(body, new HashMap<>(), statusCode);
  }

  private ConsumerRecords<String, byte[]> recordsOf(String... bodies) {
    List<ConsumerRecord<String, byte[]>> list = new ArrayList<>();
    for (int i = 0; i < bodies.length; i++) {
      list.add(record(i, bodies[i], null));
    }
    return batch(list);
  }

  /** A buffered message; a null type leaves the header off, as for an event. */
  private ConsumerRecord<String, byte[]> record(int offset, String body, String type) {
    RecordHeaders headers = new RecordHeaders();
    if (type != null) {
      headers.add(MESSAGE_TYPE_HEADER, type.getBytes(StandardCharsets.UTF_8));
    }
    return new ConsumerRecord<>(
        TOPIC, 0, offset, ConsumerRecord.NO_TIMESTAMP, TimestampType.NO_TIMESTAMP_TYPE,
        ConsumerRecord.NULL_SIZE, ConsumerRecord.NULL_SIZE, "key-" + offset,
        body.getBytes(StandardCharsets.UTF_8), headers, Optional.empty());
  }

  private ConsumerRecords<String, byte[]> batch(List<ConsumerRecord<String, byte[]>> list) {
    // Seek to the end of what we are handing over, mirroring what a real poll()
    // leaves behind - so a rewind is observable as a position change.
    consumer.seek(PARTITION, list.size());
    return new ConsumerRecords<>(Collections.singletonMap(PARTITION, list));
  }
}
