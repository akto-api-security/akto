package com.akto.threat.backend.tasks;

import com.akto.dto.threat_detection_backend.MaliciousEventDto;
import com.akto.kafka.KafkaConfig;
import com.akto.threat.backend.cache.IgnoredEventCache;
import com.akto.threat.backend.constants.MongoDBCollection;
import com.akto.threat.backend.db.AggregateSampleMaliciousEventModel;
import com.akto.kafka.KafkaConsumerConfig;
import com.akto.kafka.Serializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.Silent.class)
public class FlushMessagesToDBTest {

    @Mock
    private KafkaConfig kafkaConfig;

    @Mock
    private MongoClient mongoClient;

    @Mock
    private MongoDatabase mongoDatabase;

    @Mock
    private MongoCollection<MaliciousEventDto> maliciousEventCollection;

    @Mock
    private MongoCollection<MaliciousEventDto> aggregateEventCollection;

    @Mock
    private KafkaConsumer<String, String> kafkaConsumer;

    private static final Gson gson = new Gson();
    private static final ObjectMapper mapper = new ObjectMapper();

    @Before
    public void setUp() {
        // Setup KafkaConfig mocks
        KafkaConsumerConfig consumerConfig = mock(KafkaConsumerConfig.class);
        when(consumerConfig.getMaxPollRecords()).thenReturn(100);
        when(consumerConfig.getPollDurationMilli()).thenReturn(1000);

        Serializer keySerializer = Serializer.STRING;
        Serializer valueSerializer = Serializer.STRING;

        when(kafkaConfig.getBootstrapServers()).thenReturn("localhost:9092");
        when(kafkaConfig.getConsumerConfig()).thenReturn(consumerConfig);
        when(kafkaConfig.getKeySerializer()).thenReturn(keySerializer);
        when(kafkaConfig.getValueSerializer()).thenReturn(valueSerializer);
        when(kafkaConfig.getGroupId()).thenReturn("test-group");

        // Setup MongoDB mocks
        when(mongoClient.getDatabase(anyString())).thenReturn(mongoDatabase);
        when(mongoDatabase.getCollection(eq(MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS), eq(MaliciousEventDto.class)))
            .thenReturn(maliciousEventCollection);
        when(mongoDatabase.getCollection(eq(MongoDBCollection.ThreatDetection.AGGREGATE_SAMPLE_MALICIOUS_REQUESTS), eq(MaliciousEventDto.class)))
            .thenReturn(aggregateEventCollection);
    }

    @Test
    public void testConstructor_SetsMongoClientForCache() {
        // Verify that constructor sets MongoClient for IgnoredEventCache
        try (MockedStatic<IgnoredEventCache> mockedCache = mockStatic(IgnoredEventCache.class)) {
            FlushMessagesToDB instance = new FlushMessagesToDB(kafkaConfig, mongoClient);

            mockedCache.verify(() -> IgnoredEventCache.setMongoClient(mongoClient), times(1));
            assertNotNull(instance);
        }
    }

    @Test
    public void testProcessMaliciousEvent_IgnoredInCache_SkipsInsertion() throws Exception {
        String accountId = "1000";
        MaliciousEventDto.Status status = MaliciousEventDto.Status.ACTIVE;

        MaliciousEventDto event = MaliciousEventDto.newBuilder()
            .setLatestApiEndpoint("/api/test")
            .setFilterId("filter-1")
            .setStatus(status)
            
            .setDetectedAt(System.currentTimeMillis())
            .build();
        event.setId("event-1");

        Map<String, Object> message = new HashMap<>();
        message.put("eventType", MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS);
        message.put("payload", mapper.writeValueAsString(event));
        message.put("accountId", accountId);

        String jsonMessage = gson.toJson(message);

        // Mock IgnoredEventCache to return true (event is ignored)
        try (MockedStatic<IgnoredEventCache> mockedCache = mockStatic(IgnoredEventCache.class)) {
            mockedCache.when(() -> IgnoredEventCache.isIgnoredInCache(accountId, event.getLatestApiEndpoint(), event.getFilterId()))
                .thenReturn(true);

            FlushMessagesToDB instance = new FlushMessagesToDB(kafkaConfig, mongoClient);

            // Use reflection to call private method
            java.lang.reflect.Method writeMessage = FlushMessagesToDB.class.getDeclaredMethod("writeMessage", String.class);
            writeMessage.setAccessible(true);
            writeMessage.invoke(instance, jsonMessage);

            // Verify that insertOne was NOT called since event is ignored
            verify(maliciousEventCollection, never()).insertOne(any(MaliciousEventDto.class));
        }
    }

    @Test
    public void testProcessMaliciousEvent_NotIgnored_InsertsEvent() throws Exception {
        String accountId = "1000";
        MaliciousEventDto.Status status = MaliciousEventDto.Status.ACTIVE;

        MaliciousEventDto event = MaliciousEventDto.newBuilder()
            .setLatestApiEndpoint("/api/test2")
            .setFilterId("filter-2")
            .setStatus(status)
            .setDetectedAt(System.currentTimeMillis())
            .build();
        event.setId("event-2");

        Map<String, Object> message = new HashMap<>();
        message.put("eventType", MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS);
        message.put("payload", mapper.writeValueAsString(event));
        message.put("accountId", accountId);

        String jsonMessage = gson.toJson(message);

        // Mock IgnoredEventCache to return false (event is not ignored)
        try (MockedStatic<IgnoredEventCache> mockedCache = mockStatic(IgnoredEventCache.class)) {
            mockedCache.when(() -> IgnoredEventCache.isIgnoredInCache(accountId, event.getLatestApiEndpoint(), event.getFilterId()))
                .thenReturn(false);

            FlushMessagesToDB instance = new FlushMessagesToDB(kafkaConfig, mongoClient);

            // Use reflection to call private method
            java.lang.reflect.Method writeMessage = FlushMessagesToDB.class.getDeclaredMethod("writeMessage", String.class);
            writeMessage.setAccessible(true);
            writeMessage.invoke(instance, jsonMessage);

            // Verify that insertOne was called since event is not ignored
            ArgumentCaptor<MaliciousEventDto> captor = ArgumentCaptor.forClass(MaliciousEventDto.class);
            verify(maliciousEventCollection, times(1)).insertOne(captor.capture());

            MaliciousEventDto insertedEvent = captor.getValue();
            assertEquals(event.getId(), insertedEvent.getId());
            assertEquals(event.getLatestApiEndpoint(), insertedEvent.getLatestApiEndpoint());
            assertEquals(event.getFilterId(), insertedEvent.getFilterId());
        }
    }

    @Test
    public void testProcessAggregateEvents_BulkInsert() throws Exception {
        String accountId = "1000";

        List<AggregateSampleMaliciousEventModel> events = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            AggregateSampleMaliciousEventModel event = AggregateSampleMaliciousEventModel.newBuilder()
                .setFilterId("filter-" + i)
                .setUrl("/api/endpoint" + i)
                .build();
            events.add(event);
        }

        Map<String, Object> message = new HashMap<>();
        message.put("eventType", MongoDBCollection.ThreatDetection.AGGREGATE_SAMPLE_MALICIOUS_REQUESTS);
        message.put("payload", mapper.writeValueAsString(events));
        message.put("accountId", accountId);

        String jsonMessage = gson.toJson(message);

        FlushMessagesToDB instance = new FlushMessagesToDB(kafkaConfig, mongoClient);

        // Use reflection to call private method
        java.lang.reflect.Method writeMessage = FlushMessagesToDB.class.getDeclaredMethod("writeMessage", String.class);
        writeMessage.setAccessible(true);
        writeMessage.invoke(instance, jsonMessage);

        // Verify bulkWrite was called
        verify(aggregateEventCollection, times(1)).bulkWrite(anyList(), any());
    }

    @Test
    public void testProcessAggregateEvents_EmptyList_NoBulkWrite() throws Exception {
        String accountId = "1000";

        List<AggregateSampleMaliciousEventModel> events = new ArrayList<>();

        Map<String, Object> message = new HashMap<>();
        message.put("eventType", MongoDBCollection.ThreatDetection.AGGREGATE_SAMPLE_MALICIOUS_REQUESTS);
        message.put("payload", mapper.writeValueAsString(events));
        message.put("accountId", accountId);

        String jsonMessage = gson.toJson(message);

        FlushMessagesToDB instance = new FlushMessagesToDB(kafkaConfig, mongoClient);

        // Use reflection to call private method
        java.lang.reflect.Method writeMessage = FlushMessagesToDB.class.getDeclaredMethod("writeMessage", String.class);
        writeMessage.setAccessible(true);
        writeMessage.invoke(instance, jsonMessage);

        // Verify bulkWrite was NOT called for empty list
        verify(aggregateEventCollection, never()).bulkWrite(anyList(), any());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testProcessInvalidEventType_ThrowsException() throws Exception {
        String accountId = "1000";

        Map<String, Object> message = new HashMap<>();
        message.put("eventType", "INVALID_EVENT_TYPE");
        message.put("payload", "{}");
        message.put("accountId", accountId);

        String jsonMessage = gson.toJson(message);

        FlushMessagesToDB instance = new FlushMessagesToDB(kafkaConfig, mongoClient);

        // Use reflection to call private method
        java.lang.reflect.Method writeMessage = FlushMessagesToDB.class.getDeclaredMethod("writeMessage", String.class);
        writeMessage.setAccessible(true);

        try {
            writeMessage.invoke(instance, jsonMessage);
        } catch (java.lang.reflect.InvocationTargetException e) {
            // Unwrap the actual exception
            throw (IllegalArgumentException) e.getCause();
        }
    }

    @Test
    public void testProcessRecords_MultipleMessages() throws Exception {
        String accountId = "1000";

        // Create multiple messages
        List<ConsumerRecord<String, String>> recordsList = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            MaliciousEventDto event = MaliciousEventDto.newBuilder()
                .setLatestApiEndpoint("/api/test" + i)
                .setFilterId("filter-" + i)
                .setStatus(MaliciousEventDto.Status.ACTIVE)
                .setDetectedAt(System.currentTimeMillis())
                .build();
            event.setId("event-" + i);

            Map<String, Object> message = new HashMap<>();
            message.put("eventType", MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS);
            message.put("payload", mapper.writeValueAsString(event));
            message.put("accountId", accountId);

            String jsonMessage = gson.toJson(message);

            ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "threat-detection-topic", 0, i, null, jsonMessage
            );
            recordsList.add(record);
        }

        Map<TopicPartition, List<ConsumerRecord<String, String>>> recordsMap = new HashMap<>();
        recordsMap.put(new TopicPartition("threat-detection-topic", 0), recordsList);
        ConsumerRecords<String, String> records = new ConsumerRecords<>(recordsMap);

        try (MockedStatic<IgnoredEventCache> mockedCache = mockStatic(IgnoredEventCache.class)) {
            // All events are not ignored
            mockedCache.when(() -> IgnoredEventCache.isIgnoredInCache(anyString(), anyString(), anyString()))
                .thenReturn(false);

            FlushMessagesToDB instance = new FlushMessagesToDB(kafkaConfig, mongoClient);

            // Use reflection to call private method
            java.lang.reflect.Method processRecords = FlushMessagesToDB.class.getDeclaredMethod("processRecords", ConsumerRecords.class);
            processRecords.setAccessible(true);
            processRecords.invoke(instance, records);

            // Verify that insertOne was called 3 times
            verify(maliciousEventCollection, times(3)).insertOne(any(MaliciousEventDto.class));
        }
    }

    @Test
    public void testNullEvent_HandledGracefully() throws Exception {
        String accountId = "1000";

        Map<String, Object> message = new HashMap<>();
        message.put("eventType", MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS);
        message.put("payload", mapper.writeValueAsString(null));
        message.put("accountId", accountId);

        String jsonMessage = gson.toJson(message);

        FlushMessagesToDB instance = new FlushMessagesToDB(kafkaConfig, mongoClient);

        // Use reflection to call private method
        java.lang.reflect.Method writeMessage = FlushMessagesToDB.class.getDeclaredMethod("writeMessage", String.class);
        writeMessage.setAccessible(true);
        writeMessage.invoke(instance, jsonMessage);

        // Verify that no operations were performed
        verify(maliciousEventCollection, never()).insertOne(any());
        verify(aggregateEventCollection, never()).bulkWrite(anyList(), any());
    }

    @Test
    public void testCacheInteraction_CorrectParameters() throws Exception {
        String accountId = "1000";
        String apiEndpoint = "/api/secure";
        String filterId = "sql-injection";

        MaliciousEventDto event = MaliciousEventDto.newBuilder()
            .setLatestApiEndpoint(apiEndpoint)
            .setFilterId(filterId)
            .setStatus(MaliciousEventDto.Status.ACTIVE)
            .setDetectedAt(System.currentTimeMillis())
            .build();
        event.setId("event-test");

        Map<String, Object> message = new HashMap<>();
        message.put("eventType", MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS);
        message.put("payload", mapper.writeValueAsString(event));
        message.put("accountId", accountId);

        String jsonMessage = gson.toJson(message);

        try (MockedStatic<IgnoredEventCache> mockedCache = mockStatic(IgnoredEventCache.class)) {
            mockedCache.when(() -> IgnoredEventCache.isIgnoredInCache(anyString(), anyString(), anyString()))
                .thenReturn(false);

            FlushMessagesToDB instance = new FlushMessagesToDB(kafkaConfig, mongoClient);

            // Use reflection to call private method
            java.lang.reflect.Method writeMessage = FlushMessagesToDB.class.getDeclaredMethod("writeMessage", String.class);
            writeMessage.setAccessible(true);
            writeMessage.invoke(instance, jsonMessage);

            // Verify cache was checked with correct parameters
            mockedCache.verify(() ->
                IgnoredEventCache.isIgnoredInCache(accountId, apiEndpoint, filterId),
                times(1)
            );
        }
    }
}