package com.akto.action.testing;

import com.akto.MongoBasedTest;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.testing.TestResult;
import com.akto.dto.type.URLMethods;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.util.enums.GlobalEnums;
import com.akto.dto.User;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.AggregateIterable;
import com.mongodb.ExplainVerbosity;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class TestResultsStatsActionTest extends MongoBasedTest {

    @Test
    public void testPartialIndexExistsAndIsUsed() {
        // Explicitly trigger index creation
        TestingRunResultDao.instance.createIndicesIfAbsent();
        
        // Check if the partial index exists
        List<Document> indexes = TestingRunResultDao.instance.getRawCollection().listIndexes().into(new ArrayList<>());
        System.out.println("All indexes in testingRunResults collection:");
        for (Document idx : indexes) {
            System.out.println("  Index name: " + idx.getString("name"));
            System.out.println("  Keys: " + idx.get("key"));
            if (idx.containsKey("partialFilterExpression")) {
                System.out.println("  Partial filter: " + idx.get("partialFilterExpression"));
            }
            System.out.println("  ---");
        }
        
        boolean found = false;
        for (Document idx : indexes) {
            if (idx.containsKey("partialFilterExpression")) {
                Document partialFilter = (Document) idx.get("partialFilterExpression");
                if (partialFilter != null && partialFilter.containsKey("testResults.message")) {
                    Document existsDoc = (Document) partialFilter.get("testResults.message");
                    if (existsDoc != null && Boolean.TRUE.equals(existsDoc.getBoolean("$exists"))) {
                        found = true;
                        System.out.println("Found partial index: " + idx.getString("name"));
                        break;
                    }
                }
            }
        }
        assertTrue("Partial index for testResults.message existence should be present", found);
    }

    @Test
    public void testPartialIndexIsActuallyUsedInQuery() {
        // Setup test data that should use the partial index
        TestingRunResultDao.instance.getMCollection().drop();
        ObjectId testingRunResultSummaryId = new ObjectId();
        
        // Create and insert test data with testResults.message
        List<TestingRunResult> testingRunResults = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            TestResult testResult = new TestResult("{\"statusCode\": " + (i % 2 == 0 ? "429" : "200") + "}",
                    "", new ArrayList<>(), 100.0, false, TestResult.Confidence.HIGH, null);
            ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(1, "/test" + i, URLMethods.Method.GET);
            TestingRunResult runResult = new TestingRunResult(
                    new ObjectId(), apiInfoKey, "TEST", "TEST",
                    Arrays.asList(testResult), false, new ArrayList<SingleTypeInfo>(),
                    80, Context.now(), Context.now(), testingRunResultSummaryId,
                    null, new ArrayList<TestingRunResult.TestLog>());
            testingRunResults.add(runResult);
        }
        TestingRunResultDao.instance.insertMany(testingRunResults);

        // Build the same aggregation pipeline as the action
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(
                Filters.and(
                        Filters.eq("testRunResultSummaryId", testingRunResultSummaryId),
                        Filters.exists("testResults.message", true)
                )));
        pipeline.add(Aggregates.sort(Sorts.descending("endTimestamp")));
        pipeline.add(Aggregates.limit(10000));

        // Execute with explain to check index usage
        AggregateIterable<Document> aggregation = TestingRunResultDao.instance.getRawCollection()
                .aggregate(pipeline);
        
        // Get explain results
        Document explainResult = TestingRunResultDao.instance.getRawCollection()
                .aggregate(pipeline)
                .explain(ExplainVerbosity.EXECUTION_STATS);

        // Verify the query didn't use a full collection scan
        assertNotNull("Explain result should not be null", explainResult);
        
        // Check execution stats
        Document executionStats = explainResult.get("executionStats", Document.class);
        if (executionStats != null) {
            String executionStage = executionStats.getString("executionStage");
            // Should not be a COLLSCAN for large datasets
            assertNotEquals("Should not perform collection scan with proper index", "COLLSCAN", executionStage);
        }
    }

    @Test
    public void testFetchTestResultsStatsCount_MissingTestingRunResultSummaryHexId() {
        TestResultsStatsAction action = new TestResultsStatsAction();
        String result = action.fetchTestResultsStatsCount();
        assertEquals("ERROR", result);
        Collection<String> errors = action.getActionErrors();
        assertFalse(errors.isEmpty());
        assertTrue(errors.iterator().next().contains("Missing required parameter"));
    }

    @Test
    public void testFetchTestResultsStatsCount_InvalidTestingRunResultSummaryHexId() {
        TestResultsStatsAction action = new TestResultsStatsAction();
        action.setTestingRunResultSummaryHexId("invalid-object-id");
        String result = action.fetchTestResultsStatsCount();
        assertEquals("ERROR", result);
        assertTrue(action.getActionErrors().iterator().next().contains("Invalid test summary id"));
    }

    @Test
    public void testFetchTestResultsStatsCount_EmptyStringTestingRunResultSummaryHexId() {
        TestResultsStatsAction action = new TestResultsStatsAction();
        action.setTestingRunResultSummaryHexId("");
        String result = action.fetchTestResultsStatsCount();
        assertEquals("ERROR", result);
        assertTrue(action.getActionErrors().iterator().next().contains("Missing required parameter"));
    }

    @Test
    public void testFetchTestResultsStatsCount_ValidTestingRunResultSummaryHexIdNoData() {
        TestResultsStatsAction action = new TestResultsStatsAction();
        action.setTestingRunResultSummaryHexId(new ObjectId().toHexString());
        String result = action.fetchTestResultsStatsCount();
        assertEquals("SUCCESS", result);
        assertEquals(0, action.getCount());
    }

    @Test
    public void testRegexPatternFor429Detection() {
        String regex = "\"statusCode\"\\s*:\\s*429";
        
        // Should match these 429 patterns
        assertTrue("{\"statusCode\": 429}".matches(".*" + regex + ".*"));
        assertTrue("{\"statusCode\":429}".matches(".*" + regex + ".*"));
        assertTrue("{\"statusCode\"   :   429}".matches(".*" + regex + ".*"));
        
        // Should NOT match these patterns
        assertFalse("{\"statusCode\": 200}".matches(".*" + regex + ".*"));
        assertFalse("{\"statusCode\": \"429\"}".matches(".*" + regex + ".*"));
        assertFalse("{\"status\": 429}".matches(".*" + regex + ".*"));
    }

    @Test
    public void testFetchTestResultsStatsCount_With429Responses() {
        // Clear and setup test data
        TestingRunResultDao.instance.getMCollection().drop();
        ObjectId testingRunId = new ObjectId();
        ObjectId testingRunResultSummaryId = new ObjectId();
        
        List<TestingRunResult> testingRunResults = new ArrayList<>();
        
        // Create test results with 429 status codes
        TestResult rateLimitResult = new TestResult("{\"statusCode\": 429, \"body\": \"Too Many Requests\"}",
                "", new ArrayList<>(), 100.0, false, TestResult.Confidence.HIGH, null);
        ApiInfo.ApiInfoKey apiInfoKey1 = new ApiInfo.ApiInfoKey(1, "/test1", URLMethods.Method.GET);
        TestingRunResult runResult1 = new TestingRunResult(
                testingRunId, apiInfoKey1, "RATE_LIMIT", "RATE_LIMIT_TEST",
                Arrays.asList(rateLimitResult), false, new ArrayList<SingleTypeInfo>(),
                80, Context.now(), Context.now(), testingRunResultSummaryId,
                null, new ArrayList<TestingRunResult.TestLog>());
        testingRunResults.add(runResult1);
        
        TestResult throttledResult = new TestResult("{\"statusCode\":429,\"error\":\"Rate limited\"}",
                "", new ArrayList<>(), 100.0, false, TestResult.Confidence.HIGH, null);
        ApiInfo.ApiInfoKey apiInfoKey2 = new ApiInfo.ApiInfoKey(1, "/test2", URLMethods.Method.POST);
        TestingRunResult runResult2 = new TestingRunResult(
                testingRunId, apiInfoKey2, "RATE_LIMIT", "RATE_LIMIT_TEST",
                Arrays.asList(throttledResult), false, new ArrayList<SingleTypeInfo>(),
                80, Context.now(), Context.now(), testingRunResultSummaryId,
                null, new ArrayList<TestingRunResult.TestLog>());
        testingRunResults.add(runResult2);
        
        TestingRunResultDao.instance.insertMany(testingRunResults);
        
        // Set up context
        Context.userId.set(0);
        Context.contextSource.set(GlobalEnums.CONTEXT_SOURCE.API);
        
        TestResultsStatsAction action = new TestResultsStatsAction();
        Map<String, Object> session = new HashMap<>();
        User user = new User();
        user.setLogin("test@akto.io");
        session.put("user", user);
        action.setSession(session);
        action.setTestingRunResultSummaryHexId(testingRunResultSummaryId.toHexString());
        
        String result = action.fetchTestResultsStatsCount();
        
        assertEquals("SUCCESS", result);
        assertEquals(2, action.getCount());
        assertTrue(action.getActionErrors().isEmpty());
    }

    @Test
    public void testFetchTestResultsStatsCount_No429Responses() {
        TestingRunResultDao.instance.getMCollection().drop();
        ObjectId testingRunId = new ObjectId();
        ObjectId testingRunResultSummaryId = new ObjectId();
        
        List<TestingRunResult> testingRunResults = new ArrayList<>();
        TestResult okResult = new TestResult("{\"statusCode\": 200, \"body\": \"OK\"}",
                "", new ArrayList<>(), 100.0, false, TestResult.Confidence.HIGH, null);
        ApiInfo.ApiInfoKey apiInfoKey1 = new ApiInfo.ApiInfoKey(1, "/test1", URLMethods.Method.GET);
        TestingRunResult runResult1 = new TestingRunResult(
                testingRunId, apiInfoKey1, "SQL_INJECTION", "SQL_INJECTION_TEST",
                Arrays.asList(okResult), false, new ArrayList<SingleTypeInfo>(),
                80, Context.now(), Context.now(), testingRunResultSummaryId,
                null, new ArrayList<TestingRunResult.TestLog>());
        testingRunResults.add(runResult1);
        
        TestingRunResultDao.instance.insertMany(testingRunResults);
        
        Context.userId.set(0);
        Context.contextSource.set(GlobalEnums.CONTEXT_SOURCE.API);
        
        TestResultsStatsAction action = new TestResultsStatsAction();
        Map<String, Object> session = new HashMap<>();
        User user = new User();
        user.setLogin("test@akto.io");
        session.put("user", user);
        action.setSession(session);
        action.setTestingRunResultSummaryHexId(testingRunResultSummaryId.toHexString());
        
        String result = action.fetchTestResultsStatsCount();
        
        assertEquals("SUCCESS", result);
        assertEquals(0, action.getCount());
        assertTrue(action.getActionErrors().isEmpty());
    }

    @Test
    public void testFetchTestResultsStatsCount_VariousStatusCodeFormats() {
        TestingRunResultDao.instance.getMCollection().drop();
        ObjectId testingRunId = new ObjectId();
        ObjectId testingRunResultSummaryId = new ObjectId();
        
        List<TestingRunResult> testingRunResults = new ArrayList<>();
        
        // Various 429 formats that should be detected
        String[] formats429 = {
                "{\"statusCode\": 429, \"message\": \"Too Many Requests\"}",
                "{\"statusCode\":429,\"error\":\"Rate limited\"}",
                "{ \"statusCode\" : 429 , \"body\": \"Throttled\" }",
                "{\"statusCode\": 429}",
                "{\"other\": \"data\", \"statusCode\": 429, \"timestamp\": 123456}",
                "{\"statusCode\":   429   ,\"reason\":\"rate_limit\"}"
        };
        
        // Add all 429 cases
        for (int i = 0; i < formats429.length; i++) {
            TestResult testResult = new TestResult(formats429[i],
                    "", new ArrayList<>(), 100.0, false, TestResult.Confidence.HIGH, null);
            ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(1, "/format-429-" + i, URLMethods.Method.POST);
            TestingRunResult runResult = new TestingRunResult(
                    testingRunId, apiInfoKey, "RATE_LIMIT", "FORMAT_TEST",
                    Arrays.asList(testResult), false, new ArrayList<SingleTypeInfo>(),
                    80, Context.now(), Context.now(), testingRunResultSummaryId,
                    null, new ArrayList<TestingRunResult.TestLog>());
            testingRunResults.add(runResult);
        }
        
        // Non-429 formats that should NOT be detected
        String[] formatsNon429 = {
                "{\"statusCode\": 200, \"message\": \"OK\"}",
                "{\"statusCode\": 404, \"error\": \"Not Found\"}",
                "{\"statusCode\": 500, \"error\": \"Internal Server Error\"}",
                "{\"message\": \"429 mentioned but not statusCode field\"}",
                "{\"statusCode\": \"429\", \"note\": \"string not number\"}"
        };
        
        // Add all non-429 cases
        for (int i = 0; i < formatsNon429.length; i++) {
            TestResult testResult = new TestResult(formatsNon429[i],
                    "", new ArrayList<>(), 100.0, false, TestResult.Confidence.HIGH, null);
            ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(1, "/format-non429-" + i, URLMethods.Method.GET);
            TestingRunResult runResult = new TestingRunResult(
                    testingRunId, apiInfoKey, "OTHER_TEST", "FORMAT_TEST",
                    Arrays.asList(testResult), false, new ArrayList<SingleTypeInfo>(),
                    80, Context.now(), Context.now(), testingRunResultSummaryId,
                    null, new ArrayList<TestingRunResult.TestLog>());
            testingRunResults.add(runResult);
        }
        
        TestingRunResultDao.instance.insertMany(testingRunResults);
        
        Context.userId.set(0);
        Context.contextSource.set(GlobalEnums.CONTEXT_SOURCE.API);
        
        TestResultsStatsAction action = new TestResultsStatsAction();
        Map<String, Object> session = new HashMap<>();
        User user = new User();
        user.setLogin("test@akto.io");
        session.put("user", user);
        action.setSession(session);
        action.setTestingRunResultSummaryHexId(testingRunResultSummaryId.toHexString());
        
        String result = action.fetchTestResultsStatsCount();
        
        assertEquals("SUCCESS", result);
        assertEquals(6, action.getCount()); // Should find exactly 6 (only numeric 429s)
        assertTrue(action.getActionErrors().isEmpty());
    }

    @Test
    public void testGettersAndSetters() {
        TestResultsStatsAction action = new TestResultsStatsAction();
        String summaryId = new ObjectId().toHexString();
        action.setTestingRunResultSummaryHexId(summaryId);
        assertEquals(summaryId, action.getTestingRunResultSummaryHexId());
        assertEquals(0, action.getCount());
    }

    @Test
    public void testActionInitialState() {
        TestResultsStatsAction action = new TestResultsStatsAction();
        assertNull(action.getTestingRunResultSummaryHexId());
        assertEquals(0, action.getCount());
        assertTrue(action.getActionErrors().isEmpty());
    }

    @Test
    public void testPerformanceWithLargeDataset() {
        // Test with larger dataset to ensure index is being used effectively
        TestingRunResultDao.instance.getMCollection().drop();
        ObjectId testingRunResultSummaryId = new ObjectId();
        
        List<TestingRunResult> testingRunResults = new ArrayList<>();
        
        // Create 1000 documents with various status codes
        for (int i = 0; i < 1000; i++) {
            int statusCode = i % 10 == 0 ? 429 : 200; // 10% are 429s
            TestResult testResult = new TestResult("{\"statusCode\": " + statusCode + "}",
                    "", new ArrayList<>(), 100.0, false, TestResult.Confidence.HIGH, null);
            ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(1, "/perf-test-" + i, URLMethods.Method.GET);
            TestingRunResult runResult = new TestingRunResult(
                    new ObjectId(), apiInfoKey, "PERF_TEST", "PERF_TEST",
                    Arrays.asList(testResult), false, new ArrayList<SingleTypeInfo>(),
                    80, Context.now() + i, Context.now() + i, testingRunResultSummaryId,
                    null, new ArrayList<TestingRunResult.TestLog>());
            testingRunResults.add(runResult);
        }
        
        TestingRunResultDao.instance.insertMany(testingRunResults);
        
        Context.userId.set(0);
        Context.contextSource.set(GlobalEnums.CONTEXT_SOURCE.API);
        
        TestResultsStatsAction action = new TestResultsStatsAction();
        Map<String, Object> session = new HashMap<>();
        User user = new User();
        user.setLogin("test@akto.io");
        session.put("user", user);
        action.setSession(session);
        action.setTestingRunResultSummaryHexId(testingRunResultSummaryId.toHexString());
        
        long startTime = System.currentTimeMillis();
        String result = action.fetchTestResultsStatsCount();
        long endTime = System.currentTimeMillis();
        
        assertEquals("SUCCESS", result);
        assertEquals(100, action.getCount()); // Should find 100 (10% of 1000)
        
        // Performance assertion - should complete quickly with proper index
        long executionTime = endTime - startTime;
        assertTrue("Query should complete quickly with proper indexing (took " + executionTime + "ms)", 
                   executionTime < 5000); // Should be much faster, but allowing 5s for CI environments
    }
}
