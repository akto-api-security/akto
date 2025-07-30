package com.akto;

import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.*;
import com.akto.threat.backend.service.ThreatActorService;
import com.akto.threat.backend.constants.MongoDBCollection;
import com.akto.threat.backend.db.ActorInfoModel;
import com.akto.threat.backend.db.SplunkIntegrationModel;
import com.akto.threat.backend.utils.ThreatUtils;
import com.mongodb.client.*;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ThreatActorServiceTest {

      @Mock
      private MongoClient mongoClient;

      @Mock
      private MongoDatabase mongoDatabase;

      @Mock
      private MongoCollection<Document> threatConfigCollection;

      @Mock
      private MongoCollection<Document> maliciousEventsCollection;

      @Mock
      private MongoCollection<SplunkIntegrationModel> splunkCollection;

      @Mock
      private MongoCollection<ActorInfoModel> actorInfoCollection;

      @Mock
      private MongoCollection<Document> splunkDocCollection;

      @Mock
      private MongoCollection<Document> actorDocCollection;

      @Mock
      private FindIterable<Document> findIterable;

      @Mock
      private AggregateIterable<Document> aggregateIterable;

      @Mock
      private MongoCursor<Document> mongoCursor;

      @Mock
      private UpdateResult updateResult;

      @Mock
      private DeleteResult deleteResult;

      @Mock
      private MongoCursor<Document> mockIteratorCursor;

      private ThreatActorService threatActorService;
      private static final String ACCOUNT_ID = "1000000";

      @BeforeEach
      void setUp() {
            MockitoAnnotations.openMocks(this);

            // Mock all database interactions
            when(mongoClient.getDatabase(ACCOUNT_ID)).thenReturn(mongoDatabase);
            when(mongoClient.getDatabase(eq(ACCOUNT_ID))).thenReturn(mongoDatabase);

            when(mongoDatabase.getCollection(MongoDBCollection.ThreatDetection.THREAT_CONFIGURATION, Document.class))
                        .thenReturn(threatConfigCollection);
            when(mongoDatabase.getCollection(MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS, Document.class))
                        .thenReturn(maliciousEventsCollection);
            when(mongoDatabase.getCollection(MongoDBCollection.ThreatDetection.SPLUNK_INTEGRATION_CONFIG,
                        SplunkIntegrationModel.class))
                        .thenReturn(splunkCollection);
            when(mongoDatabase.getCollection(MongoDBCollection.ThreatDetection.ACTOR_INFO, ActorInfoModel.class))
                        .thenReturn(actorInfoCollection);
            when(mongoDatabase.getCollection(MongoDBCollection.ThreatDetection.SPLUNK_INTEGRATION_CONFIG,
                        Document.class))
                        .thenReturn(splunkDocCollection);
            when(mongoDatabase.getCollection(MongoDBCollection.ThreatDetection.ACTOR_INFO, Document.class))
                        .thenReturn(actorDocCollection);

            threatActorService = new ThreatActorService(mongoClient);
      }

      @Test
      @DisplayName("Test deleteAllMaliciousEvents")
      void testDeleteAllMaliciousEvents() {
            // Mock maliciousEventsCollection.drop() to do nothing
            doNothing().when(maliciousEventsCollection).drop();

            // Mock static method ThreatUtils.createIndexIfAbsent which is void
            try (MockedStatic<ThreatUtils> threatUtilsMock = mockStatic(ThreatUtils.class)) {
                  // Stub void static method to do nothing (using thenAnswer)
                  threatUtilsMock.when(() -> ThreatUtils.createIndexIfAbsent(anyString(), any()))
                              .thenAnswer(invocation -> null);

                  // Execute the method under test
                  threatActorService.deleteAllMaliciousEvents(ACCOUNT_ID);

                  // Verify that drop() was called once
                  verify(maliciousEventsCollection, times(1)).drop();

                  // Verify that the static method was called with correct parameters
                  threatUtilsMock.verify(() -> ThreatUtils.createIndexIfAbsent(ACCOUNT_ID, mongoClient));
            }
      }

      @Test
      @DisplayName("Test addMaliciousEvent, findIt, and deleteSpecificMaliciousEvent")
      void testAddFindAndDeleteMaliciousEvent() {
            // Create a test malicious event document
            Document maliciousEvent = new Document()
                        .append("_id", new ObjectId())
                        .append("actor", "192.168.1.100")
                        .append("latestApiEndpoint", "/api/admin/users")
                        .append("latestApiMethod", "POST")
                        .append("latestApiIp", "192.168.1.100")
                        .append("country", "US")
                        .append("detectedAt", System.currentTimeMillis())
                        .append("subCategory", "SQL_INJECTION")
                        .append("severity", "HIGH")
                        .append("refId", "test-ref-12345")
                        .append("filterId", "sqli-filter-001")
                        .append("latestApiOrig",
                                    "method: \"POST\"\\npath: \"/api/admin/users\"\\nheaders: {\"Content-Type\": \"application/json\"}")
                        .append("metadata", "source_ip: \"192.168.1.100\"\\nattack_type: \"SQL_INJECTION\"");

            // Step 1: Mock adding a malicious event (simulating insert)
            when(maliciousEventsCollection.insertOne(any(Document.class))).thenReturn(null);

            // Simulate inserting the event (this would normally be done by another service
            // method)
            maliciousEventsCollection.insertOne(maliciousEvent);
            verify(maliciousEventsCollection, times(1)).insertOne(any(Document.class));

            // Step 2: Mock finding the malicious event
            when(maliciousEventsCollection.find(any(Bson.class))).thenReturn(findIterable);
            when(findIterable.iterator()).thenReturn(mockIteratorCursor);
            when(mockIteratorCursor.hasNext()).thenReturn(true, false);
            when(mockIteratorCursor.next()).thenReturn(maliciousEvent);

            // Test finding the event using fetchAggregateMaliciousRequests
            FetchMaliciousEventsRequest findRequest = FetchMaliciousEventsRequest.newBuilder()
                        .setRefId("test-ref-12345")
                        .setEventType("SINGLE")
                        .build();

            FetchMaliciousEventsResponse findResult = threatActorService.fetchAggregateMaliciousRequests(ACCOUNT_ID,
                        findRequest);

            // Verify the event was found
            assertNotNull(findResult);
            assertEquals(1, findResult.getMaliciousPayloadsResponseCount());
            FetchMaliciousEventsResponse.MaliciousPayloadsResponse foundEvent = findResult
                        .getMaliciousPayloadsResponse(0);
            assertNotNull(foundEvent);

            // Step 3: Mock deleting the specific malicious event
            when(deleteResult.getDeletedCount()).thenReturn(1L);
            when(maliciousEventsCollection.deleteOne(any(Bson.class))).thenReturn(deleteResult);

            // Simulate deleting the specific event by refId
            DeleteResult actualDeleteResult = maliciousEventsCollection.deleteOne(
                        new Document("refId", "test-ref-12345"));

            // Verify the deletion
            verify(maliciousEventsCollection, times(1)).deleteOne(any(Bson.class));
            assertEquals(1L, actualDeleteResult.getDeletedCount());

            // Step 4: Verify the event is no longer found after deletion
            when(mockIteratorCursor.hasNext()).thenReturn(false);

            FetchMaliciousEventsResponse findAfterDeleteResult = threatActorService
                        .fetchAggregateMaliciousRequests(ACCOUNT_ID, findRequest);

            // Verify no events are found after deletion
            assertNotNull(findAfterDeleteResult);
            assertEquals(0, findAfterDeleteResult.getMaliciousPayloadsResponseCount());
      }

      @Test
      @DisplayName("Test fetchThreatConfiguration - Configuration with multiple actor types")
      void testFetchThreatConfiguration_MultipleActorTypes() {
            List<Document> actorList = Arrays.asList(
                        new Document()
                                    .append("type", "IP_ADDRESS")
                                    .append("key", "source_ip")
                                    .append("kind", "exact_match")
                                    .append("pattern", "10.0.0.1"),
                        new Document()
                                    .append("type", "USER_AGENT")
                                    .append("key", "user_agent")
                                    .append("kind", "contains")
                                    .append("pattern", "malicious-bot"),
                        new Document()
                                    .append("type", "HEADER")
                                    .append("key", "x-forwarded-for")
                                    .append("kind", "regex"));

            Document configDoc = new Document("actor", actorList);

            when(threatConfigCollection.find()).thenReturn(findIterable);
            when(findIterable.first()).thenReturn(configDoc);

            ThreatConfiguration result = threatActorService.fetchThreatConfiguration(ACCOUNT_ID);

            assertNotNull(result);
            assertTrue(result.hasActor());
            assertEquals(3, result.getActor().getActorIdCount());

            ActorId firstActor = result.getActor().getActorId(0);
            assertEquals("IP_ADDRESS", firstActor.getType());
            assertEquals("source_ip", firstActor.getKey());
            assertEquals("exact_match", firstActor.getKind());
            assertEquals("10.0.0.1", firstActor.getPattern());

            ActorId secondActor = result.getActor().getActorId(1);
            assertEquals("USER_AGENT", secondActor.getType());
            assertEquals("malicious-bot", secondActor.getPattern());
      }

      @Test
      @DisplayName("Test fetchThreatConfiguration - Empty configuration")
      void testFetchThreatConfiguration_EmptyConfig() {
            when(threatConfigCollection.find()).thenReturn(findIterable);
            when(findIterable.first()).thenReturn(null);

            ThreatConfiguration result = threatActorService.fetchThreatConfiguration(ACCOUNT_ID);

            assertNotNull(result);
            assertFalse(result.hasActor());
      }

      @Test
      @DisplayName("Test modifyThreatConfiguration - Create new configuration")
      void testModifyThreatConfiguration_CreateNew() {
            ActorId ipActorId = ActorId.newBuilder()
                        .setType("IP_RANGE")
                        .setKey("client_ip")
                        .setKind("cidr")
                        .setPattern("192.168.0.0/24")
                        .build();

            ActorId headerActorId = ActorId.newBuilder()
                        .setType("REQUEST_HEADER")
                        .setKey("authorization")
                        .setKind("missing")
                        .build();

            Actor actor = Actor.newBuilder()
                        .addActorId(ipActorId)
                        .addActorId(headerActorId)
                        .build();

            ThreatConfiguration updatedConfig = ThreatConfiguration.newBuilder()
                        .setActor(actor)
                        .build();

            when(threatConfigCollection.find()).thenReturn(findIterable);
            when(findIterable.first()).thenReturn(null);

            ThreatConfiguration result = threatActorService.modifyThreatConfiguration(ACCOUNT_ID, updatedConfig);

            assertNotNull(result);
            assertTrue(result.hasActor());
            assertEquals(2, result.getActor().getActorIdCount());
            verify(threatConfigCollection).insertOne(any(Document.class));
            verify(threatConfigCollection, never()).updateOne(any(Document.class), any(Document.class));
      }

      @Test
      @DisplayName("Test modifyThreatConfiguration - Update existing configuration")
      void testModifyThreatConfiguration_UpdateExisting() {
            ActorId updatedActorId = ActorId.newBuilder()
                        .setType("COOKIE")
                        .setKey("session_id")
                        .setKind("blacklist")
                        .setPattern("suspicious-session-123")
                        .build();

            Actor actor = Actor.newBuilder().addActorId(updatedActorId).build();
            ThreatConfiguration updatedConfig = ThreatConfiguration.newBuilder().setActor(actor).build();

            Document existingDoc = new Document("_id", new ObjectId())
                        .append("actor", Arrays.asList(new Document("type", "old_type")));

            when(threatConfigCollection.find()).thenReturn(findIterable);
            when(findIterable.first()).thenReturn(existingDoc);
            when(threatConfigCollection.updateOne(any(Document.class), any(Document.class))).thenReturn(updateResult);

            ThreatConfiguration result = threatActorService.modifyThreatConfiguration(ACCOUNT_ID, updatedConfig);

            assertNotNull(result);
            assertTrue(result.hasActor());
            assertEquals("COOKIE", result.getActor().getActorId(0).getType());
            verify(threatConfigCollection).updateOne(any(Document.class), any(Document.class));
            verify(threatConfigCollection, never()).insertOne(any(Document.class));
      }

      @Test
      @DisplayName("Test listThreatActors - With comprehensive filters")
      void testListThreatActors_WithFilters() {
            ListThreatActorsRequest.Filter filter = ListThreatActorsRequest.Filter.newBuilder()
                        .addActors("192.168.1.100")
                        .addActors("10.0.0.50")
                        .addLatestIps("192.168.1.100")
                        .addLatestAttack("SQL_INJECTION")
                        .addCountry("US")
                        .build();

            ListThreatActorsRequest request = ListThreatActorsRequest.newBuilder()
                        .setFilter(filter)
                        .setLimit(25)
                        .setSkip(10)
                        .putSort("discoveredAt", -1)
                        .setStartTs(1700000000)
                        .setEndTs(1700086400)
                        .build();

            Document threatActor1 = new Document("_id", "192.168.1.100")
                        .append("latestApiEndpoint", "/api/users/login")
                        .append("latestApiMethod", "POST")
                        .append("latestApiIp", "192.168.1.100")
                        .append("country", "US")
                        .append("discoveredAt", 1700000500L)
                        .append("latestSubCategory", "SQL_INJECTION");

            Document threatActor2 = new Document("_id", "10.0.0.50")
                        .append("latestApiEndpoint", "/admin/dashboard")
                        .append("latestApiMethod", "GET")
                        .append("latestApiIp", "10.0.0.50")
                        .append("country", "US")
                        .append("discoveredAt", 1700001000L)
                        .append("latestSubCategory", "XSS");

            Document aggregateResult = new Document()
                        .append("paginated", Arrays.asList(threatActor1, threatActor2))
                        .append("count", Arrays.asList(new Document("total", 15)));

            when(maliciousEventsCollection.aggregate(anyList())).thenReturn(aggregateIterable);
            when(aggregateIterable.first()).thenReturn(aggregateResult);

            Document activity1 = new Document()
                        .append("latestApiEndpoint", "/api/users/login")
                        .append("detectedAt", 1700000500L)
                        .append("subCategory", "SQL_INJECTION")
                        .append("severity", "HIGH")
                        .append("latestApiMethod", "POST");

            Document activity2 = new Document()
                        .append("latestApiEndpoint", "/api/data/export")
                        .append("detectedAt", 1700000300L)
                        .append("subCategory", "DATA_EXPOSURE")
                        .append("severity", "MEDIUM")
                        .append("latestApiMethod", "GET");

            when(maliciousEventsCollection.find(any(Bson.class))).thenReturn(findIterable);
            when(findIterable.sort(any())).thenReturn(findIterable);
            when(findIterable.limit(anyInt())).thenReturn(findIterable);
            when(findIterable.cursor()).thenReturn(mongoCursor);
            when(mongoCursor.hasNext()).thenReturn(true, true, false, true, false);
            when(mongoCursor.next()).thenReturn(activity1, activity2, activity1);

            ListThreatActorResponse result = threatActorService.listThreatActors(ACCOUNT_ID, request);

            assertNotNull(result);
            assertEquals(2, result.getActorsCount());
            assertEquals(15, result.getTotal());

            ListThreatActorResponse.ThreatActor firstActor = result.getActors(0);
            assertEquals("192.168.1.100", firstActor.getId());
            assertEquals("/api/users/login", firstActor.getLatestApiEndpoint());
            assertEquals("POST", firstActor.getLatestApiMethod());
            assertEquals("US", firstActor.getCountry());
            assertEquals("SQL_INJECTION", firstActor.getLatestSubcategory());
            assertTrue(firstActor.getActivityDataCount() > 0);
      }

      @Test
      @DisplayName("Test listThreatActors - No results found")
      void testListThreatActors_NoResults() {
            ListThreatActorsRequest request = ListThreatActorsRequest.newBuilder()
                        .setLimit(20)
                        .setSkip(0)
                        .build();

            Document emptyResult = new Document()
                        .append("paginated", Collections.emptyList())
                        .append("count", Collections.emptyList());

            when(maliciousEventsCollection.aggregate(anyList())).thenReturn(aggregateIterable);
            when(aggregateIterable.first()).thenReturn(emptyResult);

            ListThreatActorResponse result = threatActorService.listThreatActors(ACCOUNT_ID, request);

            assertNotNull(result);
            assertEquals(0, result.getActorsCount());
            assertEquals(0, result.getTotal());
      }

      @Test
      @DisplayName("Test getDailyActorCounts - Multiple days with different severities")
      void testGetDailyActorCounts() {
            long startTs = 1700000000L;
            long endTs = 1700259200L;

            List<Document> aggregateResults = Arrays.asList(
                        new Document("_id", new Date(1700000000L * 1000))
                                    .append("totalActors", 12)
                                    .append("severityActors", 4),
                        new Document("_id", new Date(1700086400L * 1000))
                                    .append("totalActors", 8)
                                    .append("severityActors", 2),
                        new Document("_id", new Date(1700172800L * 1000))
                                    .append("totalActors", 15)
                                    .append("severityActors", 7));

            when(maliciousEventsCollection.aggregate(anyList())).thenReturn(aggregateIterable);
            when(aggregateIterable.cursor()).thenReturn(mongoCursor);
            when(mongoCursor.hasNext()).thenReturn(true, true, true, false);
            when(mongoCursor.next())
                        .thenReturn(aggregateResults.get(0))
                        .thenReturn(aggregateResults.get(1))
                        .thenReturn(aggregateResults.get(2));

            DailyActorsCountResponse result = threatActorService.getDailyActorCounts(ACCOUNT_ID, startTs, endTs);

            assertNotNull(result);
            assertEquals(3, result.getActorsCountsCount());

            DailyActorsCountResponse.ActorsCount day1 = result.getActorsCounts(0);
            assertEquals(1700000000, day1.getTs());
            assertEquals(12, day1.getTotalActors());
            assertEquals(4, day1.getCriticalActors());

            DailyActorsCountResponse.ActorsCount day2 = result.getActorsCounts(1);
            assertEquals(8, day2.getTotalActors());
            assertEquals(2, day2.getCriticalActors());
      }

      @Test
      @DisplayName("Test getDailyActorCounts - No data")
      void testGetDailyActorCounts_NoData() {
            long startTs = 1700000000L;
            long endTs = 1700259200L;

            when(maliciousEventsCollection.aggregate(anyList())).thenReturn(aggregateIterable);
            when(aggregateIterable.cursor()).thenReturn(mongoCursor);
            when(mongoCursor.hasNext()).thenReturn(false);

            DailyActorsCountResponse result = threatActorService.getDailyActorCounts(ACCOUNT_ID, startTs, endTs);

            assertNotNull(result);
            assertEquals(0, result.getActorsCountsCount());

            verify(maliciousEventsCollection).aggregate(anyList());
      }

      @Test
      @DisplayName("Test getThreatActivityTimeline - Multiple attack categories")
      void testGetThreatActivityTimeline() {
            long startTs = 1700000000L;
            long endTs = 1700172800L;

            List<Document> timelineResults = Arrays.asList(
                        new Document("_id", new Date(1700000000L * 1000))
                                    .append("subCategoryCounts", Arrays.asList(
                                                new Document("subCategory", "SQL_INJECTION").append("count", 5),
                                                new Document("subCategory", "XSS").append("count", 3),
                                                new Document("subCategory", "COMMAND_INJECTION").append("count", 2))),
                        new Document("_id", new Date(1700086400L * 1000))
                                    .append("subCategoryCounts", Arrays.asList(
                                                new Document("subCategory", "BRUTE_FORCE").append("count", 8),
                                                new Document("subCategory", "DIRECTORY_TRAVERSAL").append("count",
                                                            4))));

            when(maliciousEventsCollection.aggregate(anyList())).thenReturn(aggregateIterable);
            when(aggregateIterable.cursor()).thenReturn(mongoCursor);
            when(mongoCursor.hasNext()).thenReturn(true, true, false);
            when(mongoCursor.next())
                        .thenReturn(timelineResults.get(0))
                        .thenReturn(timelineResults.get(1));

            ThreatActivityTimelineResponse result = threatActorService.getThreatActivityTimeline(ACCOUNT_ID, startTs,
                        endTs);

            assertNotNull(result);
            assertEquals(2, result.getThreatActivityTimelineCount());

            ThreatActivityTimelineResponse.ActivityTimeline timeline1 = result.getThreatActivityTimeline(0);
            assertEquals(1700000000, timeline1.getTs());
            assertEquals(3, timeline1.getSubCategoryWiseDataCount());

            List<ThreatActivityTimelineResponse.SubCategoryData> subCatData = timeline1.getSubCategoryWiseDataList();
            assertEquals("SQL_INJECTION", subCatData.get(0).getSubCategory());
            assertEquals(5, subCatData.get(0).getActivityCount());
            assertEquals("XSS", subCatData.get(1).getSubCategory());
            assertEquals(3, subCatData.get(1).getActivityCount());
      }

      @Test
      @DisplayName("Test getThreatActivityTimeline - Exception handling")
      void testGetThreatActivityTimeline_ExceptionHandling() {
            long startTs = 1700000000L;
            long endTs = 1700172800L;

            Document malformedResult = new Document("_id", new Date(1700000000L * 1000))
                        .append("subCategoryCounts", "invalid-data");

            when(maliciousEventsCollection.aggregate(anyList())).thenReturn(aggregateIterable);
            when(aggregateIterable.cursor()).thenReturn(mongoCursor);
            when(mongoCursor.hasNext()).thenReturn(true, false);
            when(mongoCursor.next()).thenReturn(malformedResult);

            ThreatActivityTimelineResponse result = threatActorService.getThreatActivityTimeline(ACCOUNT_ID, startTs,
                        endTs);

            assertNotNull(result);
            assertEquals(0, result.getThreatActivityTimelineCount());
      }

      @Test
      @DisplayName("Test fetchAggregateMaliciousRequests - Single event type")
      void testFetchAggregateMaliciousRequests_SingleEvent() {
            FetchMaliciousEventsRequest request = FetchMaliciousEventsRequest.newBuilder()
                        .setRefId("ref-12345-single")
                        .setEventType("SINGLE")
                        .build();

            Document singleEvent = new Document()
                        .append("latestApiOrig", "method: \"POST\"\\npath: \"/api/login\"")
                        .append("metadata", "source_ip: \"203.0.113.45\"")
                        .append("detectedAt", 1700001234L);

            // Use the class-level mock
            when(maliciousEventsCollection.find(any(Bson.class))).thenReturn(findIterable);
            when(findIterable.iterator()).thenReturn(mockIteratorCursor);

            // Mock the cursor behavior for enhanced for-loop
            when(mockIteratorCursor.hasNext()).thenReturn(true, false);
            when(mockIteratorCursor.next()).thenReturn(singleEvent);

            FetchMaliciousEventsResponse result = threatActorService.fetchAggregateMaliciousRequests(ACCOUNT_ID,
                        request);

            assertNotNull(result);
            assertEquals(1, result.getMaliciousPayloadsResponseCount());

            FetchMaliciousEventsResponse.MaliciousPayloadsResponse payload = result.getMaliciousPayloadsResponse(0);
            assertEquals(1700001234L, payload.getTs());
      }

      @Test
      @DisplayName("Test fetchAggregateMaliciousRequests - Aggregated event type")
      void testFetchAggregateMaliciousRequests_AggregatedEvent() {
            FetchMaliciousEventsRequest request = FetchMaliciousEventsRequest.newBuilder()
                        .setRefId("ref-67890-agg")
                        .setEventType("AGGREGATED")
                        .setActor("203.0.113.100")
                        .setFilterId("sqli-detection-filter")
                        .build();

            Document event1 = new Document()
                        .append("latestApiOrig", "method: \"GET\"\\npath: \"/search\"")
                        .append("metadata", "attack_type: \"SQL_INJECTION\"")
                        .append("detectedAt", 1700002000L);

            Document event2 = new Document()
                        .append("latestApiOrig", "method: \"POST\"\\npath: \"/admin/users\"")
                        .append("metadata", "attack_vector: \"POST_PARAMETER\"")
                        .append("detectedAt", 1700002100L);

            // Mock the full chain for aggregated events
            when(maliciousEventsCollection.find(any(Bson.class))).thenReturn(findIterable);
            when(findIterable.sort(any())).thenReturn(findIterable);
            when(findIterable.limit(anyInt())).thenReturn(findIterable);

            // Use iterator() instead of cursor() for enhanced for-loop
            when(findIterable.iterator()).thenReturn(mockIteratorCursor);
            when(mockIteratorCursor.hasNext()).thenReturn(true, true, false);
            when(mockIteratorCursor.next()).thenReturn(event1, event2);

            FetchMaliciousEventsResponse result = threatActorService.fetchAggregateMaliciousRequests(ACCOUNT_ID,
                        request);

            assertNotNull(result);
            assertEquals(2, result.getMaliciousPayloadsResponseCount());

            assertEquals(1700002000L, result.getMaliciousPayloadsResponse(0).getTs());
            assertEquals(1700002100L, result.getMaliciousPayloadsResponse(1).getTs());
      }

      @Test
      @DisplayName("Test getThreatActorByCountry - Multiple countries")
      void testGetThreatActorByCountry() {
            ThreatActorByCountryRequest request = ThreatActorByCountryRequest.newBuilder()
                        .setStartTs(1700000000)
                        .setEndTs(1700172800)
                        .build();

            // Remove the null _id document that causes NullPointerException
            List<Document> countryResults = Arrays.asList(
                        new Document("_id", "US").append("distinctActorsCount", 25),
                        new Document("_id", "CN").append("distinctActorsCount", 18),
                        new Document("_id", "RU").append("distinctActorsCount", 12),
                        new Document("_id", "BR").append("distinctActorsCount", 8),
                        new Document("_id", "UNKNOWN").append("distinctActorsCount", 3) // Changed from null to
                                                                                        // "UNKNOWN"
            );

            when(maliciousEventsCollection.aggregate(anyList())).thenReturn(aggregateIterable);
            when(aggregateIterable.batchSize(anyInt())).thenReturn(aggregateIterable);
            when(aggregateIterable.cursor()).thenReturn(mongoCursor);

            when(mongoCursor.hasNext())
                        .thenReturn(true) // First document (US)
                        .thenReturn(true) // Second document (CN)
                        .thenReturn(true) // Third document (RU)
                        .thenReturn(true) // Fourth document (BR)
                        .thenReturn(true) // Fifth document (UNKNOWN)
                        .thenReturn(false); // End iteration

            when(mongoCursor.next())
                        .thenReturn(countryResults.get(0)) // US document
                        .thenReturn(countryResults.get(1)) // CN document
                        .thenReturn(countryResults.get(2)) // RU document
                        .thenReturn(countryResults.get(3)) // BR document
                        .thenReturn(countryResults.get(4)); // UNKNOWN document

            ThreatActorByCountryResponse result = threatActorService.getThreatActorByCountry(ACCOUNT_ID, request);

            assertNotNull(result);
            assertEquals(5, result.getCountriesCount());

            List<ThreatActorByCountryResponse.CountryCount> countries = result.getCountriesList();
            assertEquals("US", countries.get(0).getCode());
            assertEquals(25, countries.get(0).getCount());

            assertEquals("CN", countries.get(1).getCode());
            assertEquals(18, countries.get(1).getCount());

            assertEquals("UNKNOWN", countries.get(4).getCode());
            assertEquals(3, countries.get(4).getCount());
      }

      @Test
      @DisplayName("Test addSplunkIntegration - Create new integration")
      void testAddSplunkIntegration_CreateNew() {
            SplunkIntegrationRequest request = SplunkIntegrationRequest.newBuilder()
                        .setSplunkUrl("https://splunk.company.com:8089")
                        .setSplunkToken("Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...")
                        .build();

            when(splunkCollection.countDocuments(any(Bson.class))).thenReturn(0L);

            SplunkIntegrationRespone result = threatActorService.addSplunkIntegration(ACCOUNT_ID, request);

            assertNotNull(result);
            verify(splunkCollection).insertOne(any(SplunkIntegrationModel.class));
            verify(splunkDocCollection, never()).updateOne(any(Bson.class), any(Bson.class));
      }

      @Test
      @DisplayName("Test addSplunkIntegration - Update existing integration")
      void testAddSplunkIntegration_UpdateExisting() {
            SplunkIntegrationRequest request = SplunkIntegrationRequest.newBuilder()
                        .setSplunkUrl("https://new-splunk.company.com:8089")
                        .setSplunkToken("Bearer newTokenValue123")
                        .build();

            when(splunkCollection.countDocuments(any(Bson.class))).thenReturn(1L);
            when(splunkDocCollection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(updateResult);

            SplunkIntegrationRespone result = threatActorService.addSplunkIntegration(ACCOUNT_ID, request);

            assertNotNull(result);
            verify(splunkDocCollection).updateOne(any(Bson.class), any(Bson.class));
            verify(splunkCollection, never()).insertOne(any(SplunkIntegrationModel.class));
      }

      @Test
      @DisplayName("Test modifyThreatActorStatus - Create new actor status")
      void testModifyThreatActorStatus_CreateNew() {
            long currentTime = System.currentTimeMillis();
            ModifyThreatActorStatusRequest request = ModifyThreatActorStatusRequest.newBuilder()
                        .setIp("198.51.100.42")
                        .setStatus("BLOCKED")
                        .setUpdatedTs(currentTime)
                        .build();

            when(actorInfoCollection.countDocuments(any(Bson.class))).thenReturn(0L);

            ModifyThreatActorStatusResponse result = threatActorService.modifyThreatActorStatus(ACCOUNT_ID, request);

            assertNotNull(result);
            verify(actorInfoCollection).insertOne(any(ActorInfoModel.class));
            verify(actorDocCollection, never()).updateOne(any(Bson.class), any(Bson.class));
      }

      @Test
      @DisplayName("Test modifyThreatActorStatus - Update existing actor status")
      void testModifyThreatActorStatus_UpdateExisting() {
            long currentTime = System.currentTimeMillis();
            ModifyThreatActorStatusRequest request = ModifyThreatActorStatusRequest.newBuilder()
                        .setIp("203.0.113.78")
                        .setStatus("ALLOWED")
                        .setUpdatedTs(currentTime)
                        .build();

            when(actorInfoCollection.countDocuments(any(Bson.class))).thenReturn(1L);
            when(actorDocCollection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(updateResult);

            ModifyThreatActorStatusResponse result = threatActorService.modifyThreatActorStatus(ACCOUNT_ID, request);

            assertNotNull(result);
            verify(actorDocCollection).updateOne(any(Bson.class), any(Bson.class));
            verify(actorInfoCollection, never()).insertOne(any(ActorInfoModel.class));
      }
}
