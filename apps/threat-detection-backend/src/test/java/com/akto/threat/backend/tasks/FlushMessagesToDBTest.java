package com.akto.threat.backend.tasks;

import com.akto.dto.threat_detection_backend.MaliciousEventDto;
import com.akto.dto.type.URLMethods;
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

    private MaliciousEventDto eventWith(String contextSource, String url, String metadata) {
        MaliciousEventDto event = MaliciousEventDto.newBuilder()
            .setContextSource(contextSource)
            .setLatestApiEndpoint(url)
            .setLatestApiMethod(URLMethods.Method.POST)
            .setFilterId("filter-derived")
            .setMetadata(metadata)
            .setStatus(MaliciousEventDto.Status.ACTIVE)
            .setDetectedAt(System.currentTimeMillis())
            .build();
        event.setId("event-derived");
        return event;
    }

    private MaliciousEventDto ingest(MaliciousEventDto event) throws Exception {
        Map<String, Object> message = new HashMap<>();
        message.put("eventType", MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS);
        message.put("payload", mapper.writeValueAsString(event));
        message.put("accountId", "1000");

        try (MockedStatic<IgnoredEventCache> mockedCache = mockStatic(IgnoredEventCache.class)) {
            mockedCache.when(() -> IgnoredEventCache.isIgnoredInCache(any(), any(), any())).thenReturn(false);

            FlushMessagesToDB instance = new FlushMessagesToDB(kafkaConfig, mongoClient);
            java.lang.reflect.Method writeMessage = FlushMessagesToDB.class.getDeclaredMethod("writeMessage", String.class);
            writeMessage.setAccessible(true);
            writeMessage.invoke(instance, gson.toJson(message));

            ArgumentCaptor<MaliciousEventDto> captor = ArgumentCaptor.forClass(MaliciousEventDto.class);
            verify(maliciousEventCollection, times(1)).insertOne(captor.capture());
            return captor.getValue();
        }
    }

    @Test
    public void testIngest_EndpointToolCall_StoresToolTypeToolKindAndBehaviour() throws Exception {
        MaliciousEventDto stored = ingest(eventWith("ENDPOINT", "/claude/guardrail/tool-call", "policy_name: \"P\"\nbehaviour: \"block\"\n"));
        assertEquals("Tool", stored.getAgenticType());
        assertEquals("TOOL", stored.getEndpointKind());
        assertEquals("block", stored.getBehaviour());
    }

    @Test
    public void testIngest_EndpointPlainPostPath_IsPromptAndKeepsPost() throws Exception {
        MaliciousEventDto stored = ingest(eventWith("ENDPOINT", "/claude/guardrail", "behaviour: \"alert\"\n"));
        assertEquals("Prompt", stored.getAgenticType());
        assertEquals("POST", stored.getEndpointKind());
        assertEquals("alert", stored.getBehaviour());
    }

    @Test
    public void testIngest_EndpointConfigPath_IsConfig() throws Exception {
        MaliciousEventDto stored = ingest(eventWith("ENDPOINT", "/claude/config/settings.json", "behaviour: \"alert\"\n"));
        assertEquals("Config", stored.getAgenticType());
        assertEquals("CONFIG", stored.getEndpointKind());
    }

    @Test
    public void testIngest_AgenticSkillPath_IsSkill() throws Exception {
        MaliciousEventDto stored = ingest(eventWith("AGENTIC", "/skills/pdf", "behaviour: \"block\"\n"));
        assertEquals("Skill", stored.getAgenticType());
        assertEquals("SKILL", stored.getEndpointKind());
    }

    @Test
    public void testIngest_McpServerPath_IsToolInNewUiAndServerInOldUi() throws Exception {
        MaliciousEventDto stored = ingest(eventWith("MCP", "/mcp/server/ab12", "behaviour: \"block\"\n"));
        assertEquals("Tool", stored.getAgenticType());
        assertEquals("SERVER", stored.getEndpointKind());
    }

    @Test
    public void testIngest_ResourcePath_IsResource() throws Exception {
        MaliciousEventDto stored = ingest(eventWith("ENDPOINT", "/mcp/resource/read/x", "behaviour: \"block\"\n"));
        assertEquals("Resource", stored.getAgenticType());
        assertEquals("RESOURCE", stored.getEndpointKind());
    }

    @Test
    public void testIngest_EndpointWithoutBehaviour_StoresTypeButNoBehaviour() throws Exception {
        MaliciousEventDto stored = ingest(eventWith("ENDPOINT", "/claude/guardrail", "policy_name: \"P\"\n"));
        assertEquals("Prompt", stored.getAgenticType());
        assertNull(stored.getBehaviour());
    }

    @Test
    public void testIngest_ApiSecurityEvent_GetsNoDerivedFields() throws Exception {
        MaliciousEventDto stored = ingest(eventWith("API", "/claude/guardrail/tool-call", "behaviour: \"block\"\n"));
        assertNull(stored.getAgenticType());
        assertNull(stored.getEndpointKind());
        assertNull(stored.getBehaviour());
        assertEquals("/claude/guardrail/tool-call", stored.getLatestApiEndpoint());
    }

    @Test
    public void testIngest_DastEvent_GetsNoDerivedFields() throws Exception {
        MaliciousEventDto stored = ingest(eventWith("DAST", "/skills/pdf", "behaviour: \"block\"\n"));
        assertNull(stored.getAgenticType());
        assertNull(stored.getEndpointKind());
        assertNull(stored.getBehaviour());
    }

    @Test
    public void testIngest_EventWithoutContextSource_GetsNoDerivedFields() throws Exception {
        MaliciousEventDto stored = ingest(eventWith(null, "/claude/guardrail/tool-call", "behaviour: \"block\"\n"));
        assertNull(stored.getAgenticType());
        assertNull(stored.getEndpointKind());
        assertNull(stored.getBehaviour());
    }

    @Test
    public void testIngest_DerivationDoesNotChangeTheReportedFields() throws Exception {
        MaliciousEventDto sent = eventWith("ENDPOINT", "/claude/guardrail/tool-call", "behaviour: \"block\"\n");
        MaliciousEventDto stored = ingest(sent);
        assertEquals(sent.getId(), stored.getId());
        assertEquals(sent.getFilterId(), stored.getFilterId());
        assertEquals(sent.getLatestApiEndpoint(), stored.getLatestApiEndpoint());
        assertEquals(sent.getLatestApiMethod(), stored.getLatestApiMethod());
        assertEquals(sent.getMetadata(), stored.getMetadata());
        assertEquals(sent.getContextSource(), stored.getContextSource());
        assertEquals(sent.getStatus(), stored.getStatus());
    }

    @Test
    public void testIngest_IgnoredEvent_IsStillNotInsertedAndNothingIsDerived() throws Exception {
        MaliciousEventDto event = eventWith("ENDPOINT", "/claude/guardrail/tool-call", "behaviour: \"block\"\n");
        Map<String, Object> message = new HashMap<>();
        message.put("eventType", MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS);
        message.put("payload", mapper.writeValueAsString(event));
        message.put("accountId", "1000");

        try (MockedStatic<IgnoredEventCache> mockedCache = mockStatic(IgnoredEventCache.class)) {
            mockedCache.when(() -> IgnoredEventCache.isIgnoredInCache(any(), any(), any())).thenReturn(true);
            FlushMessagesToDB instance = new FlushMessagesToDB(kafkaConfig, mongoClient);
            java.lang.reflect.Method writeMessage = FlushMessagesToDB.class.getDeclaredMethod("writeMessage", String.class);
            writeMessage.setAccessible(true);
            writeMessage.invoke(instance, gson.toJson(message));
            verify(maliciousEventCollection, never()).insertOne(any(MaliciousEventDto.class));
        }
    }
}
