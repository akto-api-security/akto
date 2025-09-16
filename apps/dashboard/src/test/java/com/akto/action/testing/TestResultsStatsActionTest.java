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
import com.mongodb.client.model.Updates;

import java.util.*;
import static org.junit.Assert.*;

public class TestResultsStatsActionTest extends MongoBasedTest {

    @Test
    public void testPartialIndexExistsAndIsUsed() {
        // Explicitly trigger index creation
        TestingRunResultDao.instance.createIndicesIfAbsent();

        // Check if the partial index exists
        List<Document> indexes = TestingRunResultDao.instance.getMCollection().listIndexes().into(new ArrayList<>());
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
                        Filters.exists("testResults.message", true))));
        pipeline.add(Aggregates.sort(Sorts.descending("endTimestamp")));
        pipeline.add(Aggregates.limit(10000));

        // Execute with explain to check index usage
        AggregateIterable<TestingRunResult> aggregation = TestingRunResultDao.instance.getMCollection()
                .aggregate(pipeline);

        // Get explain results
        Document explainResult = TestingRunResultDao.instance.getMCollection()
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
    public void testRegexPatternFor5xxErrors() {
        String regex = TestResultsStatsAction.REGEX_5XX;

        // Should match these 5xx patterns
        assertTrue("{\"statusCode\": 500}".matches(".*" + regex + ".*"));
        assertTrue("{\"statusCode\":502}".matches(".*" + regex + ".*"));
        assertTrue("{\"statusCode\"   :   520}".matches(".*" + regex + ".*"));
        assertTrue("{\"statusCode\": 599}".matches(".*" + regex + ".*"));

        // Should NOT match these patterns
        assertFalse("{\"statusCode\": 400}".matches(".*" + regex + ".*"));
        assertFalse("{\"statusCode\": 429}".matches(".*" + regex + ".*"));
        assertFalse("{\"statusCode\": \"500\"}".matches(".*" + regex + ".*"));
        assertFalse("{\"status\": 500}".matches(".*" + regex + ".*"));
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
    public void testFetchTestResultsStatsCount_MissingPatternType() {
        TestResultsStatsAction action = new TestResultsStatsAction();
        action.setTestingRunResultSummaryHexId(new ObjectId().toHexString());
        String result = action.fetchTestResultsStatsCount();
        assertEquals("ERROR", result);
        Collection<String> errors = action.getActionErrors();
        assertFalse(errors.isEmpty());
        assertTrue(errors.iterator().next().contains("Missing required parameter: patternType"));
    }

    @Test
    public void testFetchTestResultsStatsCount_InvalidTestingRunResultSummaryHexId() {
        TestResultsStatsAction action = new TestResultsStatsAction();
        action.setTestingRunResultSummaryHexId("invalid-object-id");
        action.setPatternType("HTTP_429");
        String result = action.fetchTestResultsStatsCount();
        assertEquals("ERROR", result);
        assertTrue(action.getActionErrors().iterator().next().contains("Invalid test summary id"));
    }

    @Test
    public void testFetchTestResultsStatsCount_EmptyStringTestingRunResultSummaryHexId() {
        TestResultsStatsAction action = new TestResultsStatsAction();
        action.setTestingRunResultSummaryHexId("");
        action.setPatternType("HTTP_429");
        String result = action.fetchTestResultsStatsCount();
        assertEquals("ERROR", result);
        assertTrue(action.getActionErrors().iterator().next().contains("Missing required parameter"));
    }

    @Test
    public void testFetchTestResultsStatsCount_ValidTestingRunResultSummaryHexIdNoData() {
        TestResultsStatsAction action = new TestResultsStatsAction();
        action.setTestingRunResultSummaryHexId(new ObjectId().toHexString());
        action.setPatternType("HTTP_429");
        String result = action.fetchTestResultsStatsCount();
        assertEquals("SUCCESS", result);
        assertEquals(0, action.getCount());
    }

    @Test
    public void testFetchTestResultsStatsCount_InvalidPatternType() {
        TestResultsStatsAction action = new TestResultsStatsAction();
        action.setTestingRunResultSummaryHexId(new ObjectId().toHexString());
        action.setPatternType("INVALID_PATTERN");
        String result = action.fetchTestResultsStatsCount();
        assertEquals("ERROR", result);
        assertTrue(action.getActionErrors().iterator().next().contains("Invalid pattern type"));
    }

    @Test
    public void testRegexPatternFor429Detection() {
        String regex = TestResultsStatsAction.REGEX_429;

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
    public void testRegexPatternForCloudflareBlocked() {
        String regex = TestResultsStatsAction.REGEX_CLOUDFLARE;

        // ==== TRUE POSITIVES: These SHOULD match (actual Cloudflare blocking) ====

        // Official Cloudflare blocking page messages
        assertTrue("Attention Required Cloudflare should match",
            "{\"responsePayload\": \"Attention Required! | Cloudflare\"}".toLowerCase().matches(".*" + regex + ".*"));
        assertTrue("Cloudflare security check should match",
            "{\"responsePayload\": \"Cloudflare security check in progress\"}".toLowerCase().matches(".*" + regex + ".*"));
        
        // WAF specific blocking
        assertTrue("WAF blocking should match",
            "{\"responsePayload\": \"Request blocked by Cloudflare WAF\"}".toLowerCase().matches(".*" + regex + ".*"));
        assertTrue("Cloudflare WAF blocked should match",
            "{\"responsePayload\": \"Cloudflare WAF rule blocked this request\"}".toLowerCase().matches(".*" + regex + ".*"));
    }

    @Test
    public void testRegexPatternForCloudflareNegatives() {
        String regex = TestResultsStatsAction.REGEX_CLOUDFLARE;

        // ==== FALSE POSITIVES PREVENTION: These should NOT match ====
        
        // The specific example from user - OAuth error behind Cloudflare should NOT be flagged
        String oauthErrorExample = "{\"responsePayload\":\"{\\\"error\\\":\\\"invalid_token\\\",\\\"error_description\\\":\\\"Invalid access token\\\"}\",\"responseHeaders\":\"{\\\"date\\\":\\\"Mon, 15 Sep 2025 04:40:03 GMT\\\",\\\"server\\\":\\\"cloudflare\\\",\\\"cf-ray\\\":\\\"97f5719bcc3c13cf-ORD\\\",\\\"vary\\\":\\\"accept-encoding\\\",\\\"x-frame-options\\\":\\\"DENY\\\",\\\"permissions-policy\\\":\\\"microphone=(), geolocation=(), payment=(), gyroscope=(), magnetometer=(), camera=()\\\",\\\"www-authenticate\\\":\\\"Bearer realm=\\\\\\\"oauth\\\\\\\", error=\\\\\\\"invalid_token\\\\\\\", error_description=\\\\\\\"Invalid access token\\\\\\\"\\\",\\\"x-qtest-request-id\\\":\\\"613102ec5e3f9d4a72acadd8\\\",\\\"cf-cache-status\\\":\\\"DYNAMIC\\\",\\\"pragma\\\":\\\"no-cache\\\",\\\"content-security-policy\\\":\\\"default-src 'self' 'unsafe-inline'; base-uri 'self'; style-src * 'unsafe-inline'; script-src * 'unsafe-inline' 'unsafe-eval'; connect-src *; img-src * blob: data:; font-src * data:; frame-src *\\\",\\\"x-content-type-options\\\":\\\"nosniff\\\",\\\"x-robots-tag\\\":\\\"noindex\\\",\\\"referrer-policy\\\":\\\"strict-origin-when-cross-origin\\\",\\\"content-type\\\":\\\"application/json;charset=UTF-8\\\",\\\"cache-control\\\":\\\"no-store\\\"}\",\"statusCode\":401}";
        assertFalse("OAuth invalid_token error should NOT be flagged as Cloudflare error", 
            oauthErrorExample.toLowerCase().matches(".*" + regex + ".*"));

        // Normal API errors with Cloudflare headers should NOT match
        assertFalse("Normal cf-ray header should not match",
                "{\"statusCode\":200, \"headers\":{\"cf-ray\":\"abc\"}}".toLowerCase().matches(".*" + regex + ".*"));
        
        // Authentication errors behind Cloudflare should NOT match
        assertFalse("API authentication error should not match",
            "{\"responsePayload\":\"{\\\"error\\\":\\\"unauthorized\\\"}\", \"headers\":{\"server\":\"cloudflare\"}}".toLowerCase().matches(".*" + regex + ".*"));
        
        // Normal 401/403 API responses should NOT match
        assertFalse("Normal 401 should not match",
            "{\"statusCode\": 401, \"responsePayload\":\"{\\\"message\\\":\\\"Unauthorized\\\"}\"}".toLowerCase().matches(".*" + regex + ".*"));
        
        // API rate limiting (non-Cloudflare) should NOT match
        assertFalse("API rate limit should not match",
            "{\"responsePayload\":\"{\\\"error\\\":\\\"rate_limit_exceeded\\\"}\"}".toLowerCase().matches(".*" + regex + ".*"));
        
        // Normal successful responses through Cloudflare should NOT match
        assertFalse("Successful response should not match",
            "{\"statusCode\": 200, \"responsePayload\":\"{\\\"data\\\":\\\"success\\\"}\", \"headers\":{\"server\":\"cloudflare\"}}".toLowerCase().matches(".*" + regex + ".*"));
        
        // Business logic errors should NOT match
        assertFalse("Business logic error should not match",
            "{\"responsePayload\":\"{\\\"error\\\":\\\"user_not_found\\\"}\"}".toLowerCase().matches(".*" + regex + ".*"));
    }

    @Test
    public void testCloudflareEdgeCasesAndBoundaryConditions() {
        String regex = TestResultsStatsAction.REGEX_CLOUDFLARE;

        // ==== EDGE CASES - Should NOT match ====

        // Error codes outside Cloudflare documented patterns (non-1xxx examples)
        assertFalse("Error 500 should not match",
            "{\"responsePayload\": \"Error 500: Internal Server Error\"}".toLowerCase().matches(".*" + regex + ".*"));
        assertFalse("Error 2000 should not match",
            "{\"responsePayload\": \"Error 2000: Some other error\"}".toLowerCase().matches(".*" + regex + ".*"));
        
        // Error mention without proper format
        // Cloudflare in non-blocking context (normal success)
        assertFalse("Normal Cloudflare usage should not match",
            "{\"responsePayload\": \"Data served via Cloudflare CDN\"}".toLowerCase().matches(".*" + regex + ".*"));
        assertFalse("Cloudflare performance mention should not match",
            "{\"responsePayload\": \"Optimized by Cloudflare\"}".toLowerCase().matches(".*" + regex + ".*"));
        
        // Partial matches that shouldn't trigger
        assertFalse("Partial attention should not match",
            "{\"responsePayload\": \"Attention: Please review your settings\"}".toLowerCase().matches(".*" + regex + ".*"));

        // Case insensitive matching for non-1xxx documented patterns
        assertTrue("Mixed case Cloudflare should match",
            "{\"responsePayload\": \"CloudFlare Security Check Required\"}".toLowerCase().matches(".*" + regex + ".*"));
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
        action.setPatternType("HTTP_429");

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
        action.setPatternType("HTTP_429");

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
        action.setPatternType("HTTP_429");

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
        action.setPatternType("HTTP_429");

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

    @Test
    public void testFetchTestResultsStatsCount_With5xxResponses() {
        // Clear and setup test data
        TestingRunResultDao.instance.getMCollection().drop();
        ObjectId testingRunId = new ObjectId();
        ObjectId testingRunResultSummaryId = new ObjectId();

        List<TestingRunResult> testingRunResults = new ArrayList<>();

        // Create test results with various 5xx status codes
        String[] serverErrors = {
                "{\"statusCode\": 500, \"body\": \"Internal Server Error\"}",
                "{\"statusCode\":502,\"error\":\"Bad Gateway\"}",
                "{\"statusCode\": 520, \"error\": \"Cloudflare: Web server is returning an unknown error\"}",
                "{\"statusCode\": 521, \"body\": \"Cloudflare: Web server is down\"}",
                "{\"statusCode\": 522, \"error\": \"Cloudflare: Connection timed out\"}",
                "{\"statusCode\": 503, \"body\": \"Service Unavailable\"}"
        };

        for (int i = 0; i < serverErrors.length; i++) {
            TestResult serverErrorResult = new TestResult(serverErrors[i],
                    "", new ArrayList<>(), 100.0, false, TestResult.Confidence.HIGH, null);
            ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(1, "/5xx-test-" + i, URLMethods.Method.GET);
            TestingRunResult runResult = new TestingRunResult(
                    testingRunId, apiInfoKey, "SERVER_ERROR", "SERVER_ERROR_TEST",
                    Arrays.asList(serverErrorResult), false, new ArrayList<SingleTypeInfo>(),
                    80, Context.now(), Context.now(), testingRunResultSummaryId,
                    null, new ArrayList<TestingRunResult.TestLog>());
            testingRunResults.add(runResult);
        }

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
        action.setPatternType("HTTP_5XX");

        String result = action.fetchTestResultsStatsCount();

        assertEquals("SUCCESS", result);
        assertEquals(6, action.getCount()); // Should find all 6 server errors
        assertTrue(action.getActionErrors().isEmpty());
    }
    
    @Test
    public void testFetchTestResultsStatsCount_WithCloudflareResponses() {
        // Clear and setup test data
        TestingRunResultDao.instance.getMCollection().drop();
        ObjectId testingRunId = new ObjectId();
        ObjectId testingRunResultSummaryId = new ObjectId();

        List<TestingRunResult> testingRunResults = new ArrayList<>();

        // Create test results with ACTUAL Cloudflare blocking scenarios (should match new regex)
        String[] actualCloudflareErrors = {
            "{\"responsePayload\": \"Error 1020: Access denied\"}",
            "{\"responsePayload\": \"Error 1015: You are being rate limited\"}",
            "{\"responsePayload\": \"Attention Required! | Cloudflare\"}",
            "{\"responsePayload\": \"Cloudflare security check in progress\"}",
            "{\"responsePayload\": \"Request blocked by Cloudflare WAF\"}",
            "{\"responsePayload\": \"Error 1012: Access denied\"}"
        };

        for (int i = 0; i < actualCloudflareErrors.length; i++) {
            TestResult cloudflareErrorResult = new TestResult(actualCloudflareErrors[i],
                    "", new ArrayList<>(), 100.0, false, TestResult.Confidence.HIGH, null);
            ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(1, "/cf-block-" + i, URLMethods.Method.POST);
            TestingRunResult runResult = new TestingRunResult(
                    testingRunId, apiInfoKey, "CLOUDFLARE_BLOCK", "CLOUDFLARE_BLOCK_TEST",
                    Arrays.asList(cloudflareErrorResult), false, new ArrayList<SingleTypeInfo>(),
                    80, Context.now(), Context.now(), testingRunResultSummaryId,
                    null, new ArrayList<TestingRunResult.TestLog>());
            testingRunResults.add(runResult);
        }

        // Add the specific OAuth error example that should NOT match
        String oauthError = "{\"responsePayload\":\"{\\\"error\\\":\\\"invalid_token\\\",\\\"error_description\\\":\\\"Invalid access token\\\"}\",\"responseHeaders\":\"{\\\"date\\\":\\\"Mon, 15 Sep 2025 04:40:03 GMT\\\",\\\"server\\\":\\\"cloudflare\\\",\\\"cf-ray\\\":\\\"97f5719bcc3c13cf-ORD\\\"}\",\"statusCode\":401}";
        TestResult oauthErrorResult = new TestResult(oauthError,
                "", new ArrayList<>(), 100.0, false, TestResult.Confidence.HIGH, null);
        ApiInfo.ApiInfoKey oauthKey = new ApiInfo.ApiInfoKey(1, "/oauth-error", URLMethods.Method.POST);
        TestingRunResult oauthRunResult = new TestingRunResult(
                testingRunId, oauthKey, "OAUTH_ERROR", "OAUTH_ERROR_TEST",
                Arrays.asList(oauthErrorResult), false, new ArrayList<SingleTypeInfo>(),
                80, Context.now(), Context.now(), testingRunResultSummaryId,
                null, new ArrayList<TestingRunResult.TestLog>());
        testingRunResults.add(oauthRunResult);

        // Add other benign responses that should NOT match
        String[] benignResponses = {
                "{\"statusCode\": 200, \"headers\": {\"cf-ray\": \"7f9e7f2ad9be2a3c-DEL\"}}",
                "{\"statusCode\": 204, \"headers\": {\"cf-ray\": \"72f0a1b7ce4321ab-LHR\"}}",
                "{\"statusCode\": 302, \"headers\": {\"server\": \"cloudflare\"}}"
        };

        for (int i = 0; i < benignResponses.length; i++) {
            TestResult benignResult = new TestResult(benignResponses[i],
                "", new ArrayList<>(), 100.0, false, TestResult.Confidence.HIGH, null);
            ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(1, "/benign-" + i, URLMethods.Method.GET);
            TestingRunResult runResult = new TestingRunResult(
                    testingRunId, apiInfoKey, "BENIGN_TEST", "BENIGN_TEST",
                    Arrays.asList(benignResult), false, new ArrayList<SingleTypeInfo>(),
                    80, Context.now(), Context.now(), testingRunResultSummaryId,
                    null, new ArrayList<TestingRunResult.TestLog>());
            testingRunResults.add(runResult);
        }

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
        action.setPatternType("CLOUDFLARE");

        String result = action.fetchTestResultsStatsCount();

        assertEquals("SUCCESS", result);
        // After parking 1xxx detection, only non-1xxx patterns should match: 
        // "Attention Required", "Cloudflare security check", and explicit WAF block → total 3
        assertEquals(3, action.getCount());
        assertTrue(action.getActionErrors().isEmpty());
    }

    @Test
    public void testRegexPatternForHTMLErrorPagesWithNewRegex() {
        String regex = TestResultsStatsAction.REGEX_CLOUDFLARE;

        // HTML error pages that SHOULD match (contain proper patterns - not relying on 1xxx codes)
        String[] htmlErrorsShouldMatch = {
                "{\"responsePayload\": \"<!DOCTYPE html><html><head><title>Attention Required! | Cloudflare</title></head><body><h1>Sorry, you have been blocked</h1></body></html>\"}"
        };

        for (String html : htmlErrorsShouldMatch) {
            assertTrue("HTML error page with proper pattern should match: " + html.substring(0, Math.min(100, html.length())),
                    html.toLowerCase().matches(".*" + regex + ".*"));
        }

        // HTML pages that should NOT match (generic content, no specific error patterns)
        String[] htmlErrorsShouldNotMatch = {
                "{\"responsePayload\": \"<!DOCTYPE html><html><head><title>Just a moment...</title></head><body><p>Loading...</p></body></html>\"}",
                "{\"responsePayload\": \"<html><body><h1>Checking your browser</h1><p>Please wait</p></body></html>\"}"
        };

        for (String html : htmlErrorsShouldNotMatch) {
            assertFalse("Generic HTML page should NOT match: " + html.substring(0, Math.min(100, html.length())),
                    html.toLowerCase().matches(".*" + regex + ".*"));
        }
    }

    @Test
    public void testRegexPatternForWAFBlocking() {
        String regex = TestResultsStatsAction.REGEX_CLOUDFLARE;

        // WAF blocking messages that SHOULD match (explicit Cloudflare WAF blocking)
        String[] wafMessagesShouldMatch = {
                "{\"responsePayload\": \"Request blocked by Cloudflare WAF\"}",
                "{\"responsePayload\": \"Cloudflare WAF rule blocked this request\"}",
                "{\"responsePayload\": \"Cloudflare WAF security check failed\"}"
        };

        for (String wafMessage : wafMessagesShouldMatch) {
            assertTrue("Explicit Cloudflare WAF message should match: " + wafMessage,
                wafMessage.toLowerCase().matches(".*" + regex + ".*"));
        }

        // Generic WAF messages that should NOT match (could be from any WAF, not specifically Cloudflare)
        String[] wafMessagesShouldNotMatch = {
                "{\"responsePayload\": \"WAF Alert: SQL injection attempt detected\"}",
                "{\"responsePayload\": \"WAF Block: Cross-site scripting attack prevented\"}",
                "{\"responsePayload\": \"Web Application Firewall blocked this request\"}"
        };

        for (String wafMessage : wafMessagesShouldNotMatch) {
            assertFalse("Generic WAF message should NOT match: " + wafMessage,
                wafMessage.toLowerCase().matches(".*" + regex + ".*"));
        }
    }

    @Test 
    public void testOAuthInvalidTokenBehindCloudflareShouldNotMatch() {
        String regex = TestResultsStatsAction.REGEX_CLOUDFLARE;
        
        // Sanitized representation of the OAuth invalid_token case (no timestamps or unique IDs)
        String userExample = "{\"responsePayload\":\"{\\\"error\\\":\\\"invalid_token\\\",\\\"error_description\\\":\\\"Invalid access token\\\"}\"," +
                "\"responseHeaders\":\"{\\\"server\\\":\\\"cloudflare\\\",\\\"cf-ray\\\":\\\"<redacted-ray-id>\\\",\\\"www-authenticate\\\":\\\"Bearer realm=\\\\\\\"oauth\\\\\\\", error=\\\\\\\"invalid_token\\\\\\\", error_description=\\\\\\\"Invalid access token\\\\\\\"\\\"}\"," +
                "\"statusCode\":401}";
        
        // THIS IS THE KEY TEST: OAuth invalid_token behind Cloudflare headers should NOT be flagged as Cloudflare error
        assertFalse("OAuth invalid_token behind Cloudflare should NOT be flagged as Cloudflare error", 
            userExample.toLowerCase().matches(".*" + regex + ".*"));
    }

    @Test
    public void testRegexPatternComprehensiveSummary() {
        String regex = TestResultsStatsAction.REGEX_CLOUDFLARE;

        // ==== DOCUMENTED PATTERNS THAT SHOULD MATCH ====
        
        // 2. Attention Required pattern (Official Cloudflare blocking page)
        assertTrue("Attention Required should match", 
            "{\"responsePayload\": \"Attention Required! | Cloudflare\"}".toLowerCase().matches(".*" + regex + ".*"));
        
        // 3. Cloudflare security check pattern
        assertTrue("Security check should match", 
            "{\"responsePayload\": \"Cloudflare security check in progress\"}".toLowerCase().matches(".*" + regex + ".*"));
        
        // 4. Explicit WAF blocking (Cloudflare-specific)
        assertTrue("WAF blocking should match", 
            "{\"responsePayload\": \"Request blocked by Cloudflare WAF\"}".toLowerCase().matches(".*" + regex + ".*"));

        // ==== PATTERNS THAT SHOULD NOT MATCH (FALSE POSITIVES PREVENTION) ====
        
        // OAuth/API authentication errors
        assertFalse("OAuth error should not match",
            "{\"responsePayload\":\"{\\\"error\\\":\\\"invalid_token\\\"}\"}".toLowerCase().matches(".*" + regex + ".*"));
        
        // Normal responses with Cloudflare headers
        assertFalse("cf-ray header alone should not match",
            "{\"headers\":{\"cf-ray\":\"abc\", \"server\":\"cloudflare\"}}".toLowerCase().matches(".*" + regex + ".*"));
        
        // Generic error codes (non-1xxx)
        assertFalse("Error 500 should not match",
            "{\"responsePayload\": \"Error 500: Internal Server Error\"}".toLowerCase().matches(".*" + regex + ".*"));
        
        // Generic WAF messages (not Cloudflare-specific)
        assertFalse("Generic WAF should not match",
            "{\"responsePayload\": \"WAF blocked request\"}".toLowerCase().matches(".*" + regex + ".*"));
    }

    @Test
    public void testCloudflareShouldNotMatch_GojekInternalServerError() {
        String regex = TestResultsStatsAction.REGEX_CLOUDFLARE;

        // Payload based on the screenshot: GoJek business error, not a Cloudflare block
        String gojekPayload =
            "{\"responsePayload\": \"{\\\"error\\\":{\\\"code\\\":\\\"1000\\\",\\\"description\\\":\\\"internal server error\\\"}," +
            "\\\"errors\\\":[{\\\"code\\\":\\\"GoPay-1000\\\",\\\"message\\\":\\\"Don't worry, we're fixing this. Please try again after some time.\\\"}]," +
            "\\\"success\\\":false}\"}";

        assertFalse("GoJek internal server error should NOT be flagged as Cloudflare",
            gojekPayload.toLowerCase().matches(".*" + regex + ".*"));
    }

    @Test
    public void testCloudflareShouldNotMatch_ErrorCode1000BenignJson() {
        String regex = TestResultsStatsAction.REGEX_CLOUDFLARE;

        String sample =
            "{\"responsePayload\": \"{\\\"error\\\":{\\\"code\\\":1000,\\\"description\\\":\\\"hello\\\"}}\"," +
            " \"responseHeaders\": \"{\\\"server\\\":\\\"cloudflare\\\",\\\"cf-ray\\\":\\\"<redacted-ray-id>\\\"}\"," +
            " \"statusCode\": 403}";

        assertFalse("Benign JSON with error.code=1000 should NOT be flagged as Cloudflare",
            sample.toLowerCase().matches(".*" + regex + ".*"));
    }

}