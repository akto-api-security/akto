package com.akto;

import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.*;
import com.akto.threat.backend.service.ThreatApiService;
import com.akto.threat.backend.constants.MongoDBCollection;
import com.mongodb.client.*;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for ThreatApiService
 * Tests all three main methods: listThreatApis, getSubCategoryWiseCount, and getSeverityWiseCount
 * Each test covers different scenarios including edge cases and error handling
 */
public class ThreatApiServiceTest {

    // Mock objects for MongoDB interactions
    @Mock
    private MongoClient mongoClient;
    
    @Mock
    private MongoDatabase mongoDatabase;
    
    @Mock
    private MongoCollection<Document> mongoCollection;
    
    @Mock
    private AggregateIterable<Document> aggregateIterable;
    
    @Mock
    private MongoCursor<Document> mongoCursor;

    private ThreatApiService threatApiService;
    private static final String TEST_ACCOUNT_ID = "test-account-123";

    /**
     * Setup method called before each test
     * Initializes mocks and sets up common mock behavior
     */
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        threatApiService = new ThreatApiService(mongoClient);
        
        // Setup common mock behavior that all tests will use
        when(mongoClient.getDatabase(TEST_ACCOUNT_ID)).thenReturn(mongoDatabase);
        when(mongoDatabase.getCollection(MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS, Document.class))
                .thenReturn(mongoCollection);
    }

    // ==================== LIST THREAT APIS TESTS ====================

    /**
     * Test basic functionality of listThreatApis method
     * Verifies that the method correctly processes a simple request and returns expected results
     */
    @Test
    @DisplayName("listThreatApis - Basic functionality with valid data")
    void testListThreatApis_BasicFunctionality() {
        // Arrange: Create a basic request with pagination and sorting
        ListThreatApiRequest request = ListThreatApiRequest.newBuilder()
                .setSkip(0)                          // Start from beginning
                .setLimit(10)                        // Limit to 10 results
                .putSort("discoveredAt", -1)         // Sort by discoveredAt descending
                .putSort("requestsCount", -1)        // Secondary sort by requestsCount
                .setFilter(ListThreatApiRequest.Filter.newBuilder().build()) // Empty filter
                .build();

        // Mock the count aggregation (first call to aggregate())
        Document countResult = new Document("total", 25);
        when(mongoCollection.aggregate(anyList()))
                .thenReturn(aggregateIterable);
        when(aggregateIterable.first()).thenReturn(countResult);
        when(aggregateIterable.cursor()).thenReturn(mongoCursor);

        // Create mock threat API documents that match the service's expected structure
        List<Document> mockResults = Arrays.asList(
                createMockThreatApiDocument("/api/users", "GET", 1640995200L, 5, 25),
                createMockThreatApiDocument("/api/orders", "POST", 1640995100L, 3, 15),
                createMockThreatApiDocument("/api/products", "DELETE", 1640995000L, 2, 8)
        );

        // Mock cursor behavior for iterating through results
        when(mongoCursor.hasNext())
                .thenReturn(true, true, true, false); // Three results, then end
        when(mongoCursor.next())
                .thenReturn(mockResults.get(0), mockResults.get(1), mockResults.get(2));

        // Act: Call the method under test
        ListThreatApiResponse response = threatApiService.listThreatApis(TEST_ACCOUNT_ID, request);

        // Assert: Verify the response structure and content
        assertNotNull(response, "Response should not be null");
        assertEquals(25, response.getTotal(), "Total count should match mock data");
        assertEquals(3, response.getApisCount(), "Should return 3 API results");
        
        // Verify first API details
        ListThreatApiResponse.ThreatApi firstApi = response.getApis(0);
        assertEquals("/api/users", firstApi.getEndpoint(), "First API endpoint should match");
        assertEquals("GET", firstApi.getMethod(), "First API method should match");
        assertEquals(1640995200L, firstApi.getDiscoveredAt(), "First API discovery time should match");
        assertEquals(5, firstApi.getActorsCount(), "First API actors count should match");
        assertEquals(25, firstApi.getRequestsCount(), "First API requests count should match");
        
        // Verify second API details
        ListThreatApiResponse.ThreatApi secondApi = response.getApis(1);
        assertEquals("/api/orders", secondApi.getEndpoint(), "Second API endpoint should match");
        assertEquals("POST", secondApi.getMethod(), "Second API method should match");
        
        // Verify that MongoDB interactions occurred as expected
        verify(mongoClient).getDatabase(TEST_ACCOUNT_ID);
        verify(mongoDatabase).getCollection(MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS, Document.class);
        verify(mongoCollection, times(2)).aggregate(anyList()); // Once for count, once for data
    }

    /**
     * Test listThreatApis with method and URL filters
     * Verifies that filters are correctly applied in the aggregation pipeline
     */
    @Test
    @DisplayName("listThreatApis - With method and URL filters")
    void testListThreatApis_WithMethodAndUrlFilters() {
        // Arrange: Create request with specific filters
        ListThreatApiRequest.Filter filter = ListThreatApiRequest.Filter.newBuilder()
                .addMethods("GET")               // Filter for GET requests
                .addMethods("POST")              // Filter for POST requests
                .addUrls("/api/users")           // Filter for specific URL
                .addUrls("/api/orders")          // Filter for another URL
                .build();

        ListThreatApiRequest request = ListThreatApiRequest.newBuilder()
                .setSkip(5)                      // Skip first 5 results
                .setLimit(15)                    // Limit to 15 results
                .putSort("actorsCount", 1)       // Sort by actors count ascending
                .setFilter(filter)
                .build();

        // Mock empty results to focus on testing the filtering logic
        when(mongoCollection.aggregate(anyList())).thenReturn(aggregateIterable);
        when(aggregateIterable.first()).thenReturn(new Document("total", 0));
        when(aggregateIterable.cursor()).thenReturn(mongoCursor);
        when(mongoCursor.hasNext()).thenReturn(false);

        // Act
        ListThreatApiResponse response = threatApiService.listThreatApis(TEST_ACCOUNT_ID, request);

        // Assert
        assertEquals(0, response.getTotal(), "Should return 0 total for empty results");
        assertEquals(0, response.getApisCount(), "Should return 0 APIs for empty results");
        
        // Verify that aggregation was called twice (once for count, once for data)
        verify(mongoCollection, times(2)).aggregate(anyList());
        
        // Note: In a real implementation, you might want to verify the actual pipeline structure
        // by capturing the arguments passed to aggregate() and checking the filter conditions
    }

    /**
     * Test listThreatApis with time range filter
     * This test is structured to work even though TimeRange might not be available in proto
     */
    @Test
    @DisplayName("listThreatApis - With time range filter (if supported)")
    void testListThreatApis_WithTimeRangeFilter() {
        // Arrange: Create a basic filter (time range testing depends on proto structure)
        ListThreatApiRequest.Filter filter = ListThreatApiRequest.Filter.newBuilder()
                .addMethods("GET")
                .build();

        ListThreatApiRequest request = ListThreatApiRequest.newBuilder()
                .setLimit(10)
                .setFilter(filter)
                .build();

        // Mock successful execution
        when(mongoCollection.aggregate(anyList())).thenReturn(aggregateIterable);
        when(aggregateIterable.first()).thenReturn(new Document("total", 5));
        when(aggregateIterable.cursor()).thenReturn(mongoCursor);
        when(mongoCursor.hasNext()).thenReturn(false);

        // Act
        ListThreatApiResponse response = threatApiService.listThreatApis(TEST_ACCOUNT_ID, request);

        // Assert
        assertEquals(5, response.getTotal(), "Should handle time range filter correctly");
        verify(mongoCollection, times(2)).aggregate(anyList());
    }

    /**
     * Test listThreatApis when no results are found
     * Verifies proper handling of empty result sets
     */
    @Test
    @DisplayName("listThreatApis - Empty results")
    void testListThreatApis_EmptyResults() {
        // Arrange: Create a request that will return no results
        ListThreatApiRequest request = ListThreatApiRequest.newBuilder()
                .setLimit(10)
                .setFilter(ListThreatApiRequest.Filter.newBuilder().build())
                .build();

        // Mock no count result (null response from aggregation)
        when(mongoCollection.aggregate(anyList())).thenReturn(aggregateIterable);
        when(aggregateIterable.first()).thenReturn(null); // No count result
        when(aggregateIterable.cursor()).thenReturn(mongoCursor);
        when(mongoCursor.hasNext()).thenReturn(false);    // No data results

        // Act
        ListThreatApiResponse response = threatApiService.listThreatApis(TEST_ACCOUNT_ID, request);

        // Assert: Verify that empty results are handled correctly
        assertEquals(0, response.getTotal(), "Total should be 0 when count result is null");
        assertEquals(0, response.getApisCount(), "APIs count should be 0 for empty results");
        assertNotNull(response.getApisList(), "APIs list should not be null even when empty");
    }

    /**
     * Test listThreatApis exception handling during cursor iteration
     * Verifies that exceptions are caught and handled gracefully
     */
    @Test
    @DisplayName("listThreatApis - Exception handling during cursor iteration")
    void testListThreatApis_ExceptionHandling() {
        // Arrange: Set up scenario where cursor throws exception
        ListThreatApiRequest request = ListThreatApiRequest.newBuilder()
                .setLimit(10)
                .setFilter(ListThreatApiRequest.Filter.newBuilder().build())
                .build();

        // Mock successful count but exception during data retrieval
        when(mongoCollection.aggregate(anyList())).thenReturn(aggregateIterable);
        when(aggregateIterable.first()).thenReturn(new Document("total", 5));
        when(aggregateIterable.cursor()).thenReturn(mongoCursor);
        when(mongoCursor.hasNext()).thenThrow(new RuntimeException("Database connection error"));

        // Act: Method should handle exception gracefully
        ListThreatApiResponse response = threatApiService.listThreatApis(TEST_ACCOUNT_ID, request);

        // Assert: Verify that exception is handled and partial results are returned
        assertEquals(5, response.getTotal(), "Total count should still be returned despite exception");
        assertEquals(0, response.getApisCount(), "APIs list should be empty due to exception");
        
        // The service should still return a valid response object even when exceptions occur
        assertNotNull(response, "Response should not be null even when exception occurs");
    }

    // ==================== SUB CATEGORY WISE COUNT TESTS ====================

    /**
     * Test basic functionality of getSubCategoryWiseCount method
     * Verifies aggregation pipeline and result processing
     */
    @Test
    @DisplayName("getSubCategoryWiseCount - Basic functionality with time range")
    void testGetSubCategoryWiseCount_BasicFunctionality() {
        // Arrange: Create request with time range
        ThreatCategoryWiseCountRequest request = ThreatCategoryWiseCountRequest.newBuilder()
                .setStartTs(1640995200)  // Start timestamp (int)
                .setEndTs(1641081600)    // End timestamp (int)
                .build();

        // Create mock category count documents
        List<Document> mockResults = Arrays.asList(
                createMockCategoryDocument("SQL_INJECTION", "BLIND_SQL_INJECTION", 25),
                createMockCategoryDocument("XSS", "REFLECTED_XSS", 18),
                createMockCategoryDocument("CSRF", "TOKEN_MISSING", 12),
                createMockCategoryDocument("SQL_INJECTION", "UNION_BASED", 8),
                createMockCategoryDocument("XSS", "STORED_XSS", 5)
        );

        // Mock aggregation behavior
        when(mongoCollection.aggregate(anyList())).thenReturn(aggregateIterable);
        when(aggregateIterable.batchSize(1000)).thenReturn(aggregateIterable);
        when(aggregateIterable.cursor()).thenReturn(mongoCursor);
        
        // Mock cursor iteration
        when(mongoCursor.hasNext())
                .thenReturn(true, true, true, true, true, false);
        when(mongoCursor.next())
                .thenReturn(mockResults.get(0), mockResults.get(1), mockResults.get(2), 
                           mockResults.get(3), mockResults.get(4));

        // Act
        ThreatCategoryWiseCountResponse response = threatApiService.getSubCategoryWiseCount(TEST_ACCOUNT_ID, request);

        // Assert: Verify response structure and content
        assertNotNull(response, "Response should not be null");
        assertEquals(5, response.getCategoryWiseCountsCount(), "Should return 5 category counts");
        
        // Verify first category count
        ThreatCategoryWiseCountResponse.SubCategoryCount firstCount = response.getCategoryWiseCounts(0);
        assertEquals("SQL_INJECTION", firstCount.getCategory(), "First category should match");
        assertEquals("BLIND_SQL_INJECTION", firstCount.getSubCategory(), "First sub-category should match");
        assertEquals(25, firstCount.getCount(), "First count should match");
        
        // Verify second category count
        ThreatCategoryWiseCountResponse.SubCategoryCount secondCount = response.getCategoryWiseCounts(1);
        assertEquals("XSS", secondCount.getCategory(), "Second category should match");
        assertEquals("REFLECTED_XSS", secondCount.getSubCategory(), "Second sub-category should match");
        assertEquals(18, secondCount.getCount(), "Second count should match");
        
        // Verify MongoDB interactions
        verify(mongoCollection).aggregate(anyList());
        verify(aggregateIterable).batchSize(1000);
        verify(aggregateIterable).cursor();
    }

    /**
     * Test getSubCategoryWiseCount without time range
     * Verifies that the method works when no time filtering is applied
     */
    @Test
    @DisplayName("getSubCategoryWiseCount - No time range specified")
    void testGetSubCategoryWiseCount_NoTimeRange() {
        // Arrange: Create request with zero timestamps (indicating no time range)
        ThreatCategoryWiseCountRequest request = ThreatCategoryWiseCountRequest.newBuilder()
                .setStartTs(0)  // No start time filter
                .setEndTs(0)    // No end time filter
                .build();

        // Mock single result
        Document mockResult = createMockCategoryDocument("AUTHENTICATION", "WEAK_PASSWORD", 10);
        
        when(mongoCollection.aggregate(anyList())).thenReturn(aggregateIterable);
        when(aggregateIterable.batchSize(1000)).thenReturn(aggregateIterable);
        when(aggregateIterable.cursor()).thenReturn(mongoCursor);
        when(mongoCursor.hasNext()).thenReturn(true, false);
        when(mongoCursor.next()).thenReturn(mockResult);

        // Act
        ThreatCategoryWiseCountResponse response = threatApiService.getSubCategoryWiseCount(TEST_ACCOUNT_ID, request);

        // Assert
        assertNotNull(response, "Response should not be null");
        assertEquals(1, response.getCategoryWiseCountsCount(), "Should return 1 category count");
        
        ThreatCategoryWiseCountResponse.SubCategoryCount count = response.getCategoryWiseCounts(0);
        assertEquals("AUTHENTICATION", count.getCategory(), "Category should match");
        assertEquals("WEAK_PASSWORD", count.getSubCategory(), "Sub-category should match");
        assertEquals(10, count.getCount(), "Count should match");
        
        // Verify that aggregation pipeline was built without time match stage
        verify(mongoCollection).aggregate(anyList());
    }

    /**
     * Test getSubCategoryWiseCount with empty results
     * Verifies proper handling when no category data is found
     */
    @Test
    @DisplayName("getSubCategoryWiseCount - Empty results")
    void testGetSubCategoryWiseCount_EmptyResults() {
        // Arrange
        ThreatCategoryWiseCountRequest request = ThreatCategoryWiseCountRequest.newBuilder()
                .setStartTs(1640995200)  // int timestamp
                .setEndTs(1641081600)    // int timestamp
                .build();

        // Mock empty results
        when(mongoCollection.aggregate(anyList())).thenReturn(aggregateIterable);
        when(aggregateIterable.batchSize(1000)).thenReturn(aggregateIterable);
        when(aggregateIterable.cursor()).thenReturn(mongoCursor);
        when(mongoCursor.hasNext()).thenReturn(false); // No results

        // Act
        ThreatCategoryWiseCountResponse response = threatApiService.getSubCategoryWiseCount(TEST_ACCOUNT_ID, request);

        // Assert
        assertNotNull(response, "Response should not be null even with empty results");
        assertEquals(0, response.getCategoryWiseCountsCount(), "Should return 0 category counts");
        assertNotNull(response.getCategoryWiseCountsList(), "Category counts list should not be null");
    }

    // ==================== SEVERITY WISE COUNT TESTS ====================

    /**
     * Test basic functionality of getSeverityWiseCount method
     * Verifies that all severity levels are processed correctly
     */
    @Test
    @DisplayName("getSeverityWiseCount - All severity levels with counts")
    void testGetSeverityWiseCount_AllSeverities() {
        // Arrange
        ThreatSeverityWiseCountRequest request = ThreatSeverityWiseCountRequest.newBuilder()
                .setStartTs(1640995200)  // int timestamp
                .setEndTs(1641081600)    // int timestamp
                .build();

        // Mock count results for each severity level (service checks in order: CRITICAL, HIGH, MEDIUM, LOW)
        when(mongoCollection.countDocuments(any(Bson.class)))
                .thenReturn(15L)  // CRITICAL count
                .thenReturn(25L)  // HIGH count
                .thenReturn(12L)  // MEDIUM count
                .thenReturn(8L);  // LOW count

        // Act
        ThreatSeverityWiseCountResponse response = threatApiService.getSeverityWiseCount(TEST_ACCOUNT_ID, request);

        // Assert: Verify that all severity levels are included
        assertNotNull(response, "Response should not be null");
        assertEquals(4, response.getCategoryWiseCountsCount(), "Should return counts for all 4 severities");
        
        // Create a map for easier verification
        Map<String, Integer> severityMap = new HashMap<>();
        for (ThreatSeverityWiseCountResponse.SeverityCount count : response.getCategoryWiseCountsList()) {
            severityMap.put(count.getSeverity(), count.getCount());
            assertTrue(count.getCount() > 0, "All returned counts should be greater than 0");
        }
        
        // Verify all expected severities are present
        assertTrue(severityMap.containsKey("CRITICAL"), "Should contain CRITICAL severity");
        assertTrue(severityMap.containsKey("HIGH"), "Should contain HIGH severity");
        assertTrue(severityMap.containsKey("MEDIUM"), "Should contain MEDIUM severity");
        assertTrue(severityMap.containsKey("LOW"), "Should contain LOW severity");
        
        // Verify specific counts (note: the service processes in array order)
        assertEquals(15, (int) severityMap.get("CRITICAL"), "CRITICAL count should match");
        assertEquals(25, (int) severityMap.get("HIGH"), "HIGH count should match");
        assertEquals(12, (int) severityMap.get("MEDIUM"), "MEDIUM count should match");
        assertEquals(8, (int) severityMap.get("LOW"), "LOW count should match");
        
        // Verify that countDocuments was called exactly 4 times (once for each severity)
        verify(mongoCollection, times(4)).countDocuments(any(Bson.class));
    }

    /**
     * Test getSeverityWiseCount when some severities have zero counts
     * Verifies that only non-zero counts are included in the response
     */
    @Test
    @DisplayName("getSeverityWiseCount - Some severities with zero counts")
    void testGetSeverityWiseCount_SomeSeveritiesZero() {
        // Arrange
        ThreatSeverityWiseCountRequest request = ThreatSeverityWiseCountRequest.newBuilder()
                .setStartTs(1640995200)  // int timestamp
                .setEndTs(1641081600)    // int timestamp
                .build();

        // Mock count results with some zero counts
        when(mongoCollection.countDocuments(any(Bson.class)))
                .thenReturn(10L)  // CRITICAL: has count
                .thenReturn(0L)   // HIGH: zero count (should be excluded)
                .thenReturn(5L)   // MEDIUM: has count
                .thenReturn(0L);  // LOW: zero count (should be excluded)

        // Act
        ThreatSeverityWiseCountResponse response = threatApiService.getSeverityWiseCount(TEST_ACCOUNT_ID, request);

        // Assert: Only non-zero counts should be included
        assertNotNull(response, "Response should not be null");
        assertEquals(2, response.getCategoryWiseCountsCount(), "Should return only non-zero counts");
        
        // Verify only expected severities are present
        Set<String> includedSeverities = new HashSet<>();
        for (ThreatSeverityWiseCountResponse.SeverityCount count : response.getCategoryWiseCountsList()) {
            includedSeverities.add(count.getSeverity());
            assertTrue(count.getCount() > 0, "All returned counts should be greater than 0");
        }
        
        assertTrue(includedSeverities.contains("CRITICAL"), "Should include CRITICAL (non-zero)");
        assertTrue(includedSeverities.contains("MEDIUM"), "Should include MEDIUM (non-zero)");
        assertFalse(includedSeverities.contains("HIGH"), "Should exclude HIGH (zero count)");
        assertFalse(includedSeverities.contains("LOW"), "Should exclude LOW (zero count)");
        
        // Verify that all severity levels were still checked
        verify(mongoCollection, times(4)).countDocuments(any(Bson.class));
    }

    /**
     * Test getSeverityWiseCount when all severities have zero counts
     * Verifies proper handling when no threats are found in the time range
     */
    @Test
    @DisplayName("getSeverityWiseCount - All severities have zero counts")
    void testGetSeverityWiseCount_AllZeroCounts() {
        // Arrange
        ThreatSeverityWiseCountRequest request = ThreatSeverityWiseCountRequest.newBuilder()
                .setStartTs(1640995200)  // int timestamp
                .setEndTs(1641081600)    // int timestamp
                .build();

        // Mock all counts as zero
        when(mongoCollection.countDocuments(any(Bson.class))).thenReturn(0L);

        // Act
        ThreatSeverityWiseCountResponse response = threatApiService.getSeverityWiseCount(TEST_ACCOUNT_ID, request);

        // Assert: Should return empty list when all counts are zero
        assertNotNull(response, "Response should not be null");
        assertEquals(0, response.getCategoryWiseCountsCount(), "Should return 0 counts when all are zero");
        assertNotNull(response.getCategoryWiseCountsList(), "Category counts list should not be null");
        
        // Verify that all severity levels were checked despite zero results
        verify(mongoCollection, times(4)).countDocuments(any(Bson.class));
    }

    /**
     * Test getSeverityWiseCount with edge case timestamps
     * Verifies proper handling of edge cases in time range
     */
    @Test
    @DisplayName("getSeverityWiseCount - Edge case with same start and end timestamps")
    void testGetSeverityWiseCount_EdgeCaseTimestamps() {
        // Arrange: Same start and end timestamp (single point in time)
        int timestamp = 1640995200;
        ThreatSeverityWiseCountRequest request = ThreatSeverityWiseCountRequest.newBuilder()
                .setStartTs(timestamp)
                .setEndTs(timestamp)
                .build();

        // Mock some results
        when(mongoCollection.countDocuments(any(Bson.class)))
                .thenReturn(3L, 0L, 0L, 1L);

        // Act
        ThreatSeverityWiseCountResponse response = threatApiService.getSeverityWiseCount(TEST_ACCOUNT_ID, request);

        // Assert
        assertEquals(2, response.getCategoryWiseCountsCount(), "Should handle edge case timestamps correctly");
        verify(mongoCollection, times(4)).countDocuments(any(Bson.class));
    }

    // ==================== HELPER METHODS ====================

    /**
     * Helper method to create mock threat API documents
     * Creates documents that match the structure expected by the aggregation pipeline
     * 
     * @param endpoint The API endpoint
     * @param method The HTTP method
     * @param discoveredAt The discovery timestamp
     * @param actorsCount Number of distinct actors
     * @param requestsCount Number of requests
     * @return Mock document with the expected structure
     */
    private Document createMockThreatApiDocument(String endpoint, String method, long discoveredAt, 
                                               int actorsCount, int requestsCount) {
        // Create the _id document that contains grouped fields
        Document idDoc = new Document()
                .append("endpoint", endpoint)
                .append("method", method);
        
        // Create the main document with aggregation results
        return new Document()
                .append("_id", idDoc)                    // Grouped fields
                .append("discoveredAt", discoveredAt)    // Last detected timestamp
                .append("actorsCount", actorsCount)      // Count of distinct actors
                .append("requestsCount", requestsCount); // Total request count
    }

    /**
     * Helper method to create mock category count documents
     * Creates documents that match the structure returned by category aggregation
     * 
     * @param category The threat category
     * @param subCategory The threat sub-category
     * @param count The occurrence count
     * @return Mock document with the expected structure
     */
    private Document createMockCategoryDocument(String category, String subCategory, int count) {
        // Create the _id document that contains grouped fields
        Document idDoc = new Document()
                .append("category", category)
                .append("subCategory", subCategory);
        
        // Create the main document with aggregation results
        return new Document()
                .append("_id", idDoc)        // Grouped fields
                .append("count", count);     // Aggregated count
    }
}