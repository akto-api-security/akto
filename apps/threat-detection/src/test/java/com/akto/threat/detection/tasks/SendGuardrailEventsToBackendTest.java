package com.akto.threat.detection.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import com.akto.dto.OriginalHttpResponse;
import com.akto.kafka.KafkaConfig;
import com.akto.kafka.KafkaConsumerConfig;
import com.akto.kafka.KafkaProducerConfig;
import com.akto.kafka.Serializer;
import com.akto.testing.ApiExecutor;
import com.akto.threat.detection.constants.KafkaTopic;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
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
          + "\"sessionId\":\"sess-1\"}}";

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
      list.add(
          new ConsumerRecord<>(
              TOPIC, 0, i, "key-" + i, bodies[i].getBytes(StandardCharsets.UTF_8)));
    }
    // Seek to the end of what we are handing over, mirroring what a real poll()
    // leaves behind - so a rewind is observable as a position change.
    consumer.seek(PARTITION, bodies.length);
    return new ConsumerRecords<>(Collections.singletonMap(PARTITION, list));
  }
}
