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
                List<Document> indexes = TestingRunResultDao.instance.getRawCollection().listIndexes()
                                .into(new ArrayList<>());
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
                        assertNotEquals("Should not perform collection scan with proper index", "COLLSCAN",
                                        executionStage);
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

                assertTrue("Error 1020 should match",
                                "Error 1020: Access denied".toLowerCase().matches(".*" + regex + ".*"));
                assertTrue("Error 1015 rate limited should match",
                                "Error 1015: You are being rate limited".toLowerCase().matches(".*" + regex + ".*"));
                assertTrue("Attention Required Cloudflare should match",
                                "Attention Required! | Cloudflare".toLowerCase().matches(".*" + regex + ".*"));
                assertTrue("Blocked Cloudflare should match",
                                "User blocked by Cloudflare".toLowerCase().matches(".*" + regex + ".*"));
                assertTrue("Security service protect should match",
                                "This website is using a security service to protect itself from online attacks."
                                                .toLowerCase()
                                                .matches(".*" + regex + ".*"));
                assertTrue("Ray ID blocked should match",
                                "Ray ID ABC blocked".toLowerCase().matches(".*" + regex + ".*"));

                // Should NOT match benign 2xx with cf-ray header
                assertFalse("cf-ray header alone should not match",
                                "{\"statusCode\":200, \"headers\":{\"cf-ray\":\"abc\"}}".toLowerCase()
                                                .matches(".*" + regex + ".*"));
        }

        @Test
        public void testCloudflareCfRayHeadersDoNotMatch() {
                String regex = TestResultsStatsAction.REGEX_CLOUDFLARE;

                String[] benignHeaders = new String[] {
                                "{\"statusCode\": 200, \"headers\": {\"cf-ray\": \"7f9e7f2ad9be2a3c-DEL\", \"server\": \"cloudflare\"}}",
                                "{\"statusCode\": 204, \"headers\": {\"cf-ray\": \"72f0a1b7ce4321ab-LHR\"}}",
                                "{\"statusCode\": 302, \"headers\": {\"cf-ray\": \"6a5d1e2f3c4b9abc-SIN\", \"cf-cache-status\": \"HIT\"}}",
                                "{\"statusCode\": 200, \"headers\": {\"server\": \"cloudflare\", \"cf-ray\": \"8090abcd1234efgh-BOM\"}}"
                };

                for (String h : benignHeaders) {
                        assertFalse("Benign cf-ray header should not match: " + h,
                                        h.toLowerCase().matches(".*" + regex + ".*"));
                }

                String[] blockedSamples = new String[] {
                                "{\"statusCode\": 403, \"body\": \"Error 1020: Access denied\"}",
                                "{\"statusCode\": 429, \"body\": \"You are being rate limited by Cloudflare\"}",
                                "{\"statusCode\": 403, \"body\": \"Attention Required! | Cloudflare\"}",
                                "{\"statusCode\": 403, \"body\": \"This website is using a security service to protect itself from online attacks.\"}",
                                "{\"statusCode\": 403, \"body\": \"Ray ID XYZ blocked\"}",
                                "{\"statusCode\": 403, \"body\": \"WAF rule triggered: Malicious request detected\"}",
                                "{\"statusCode\": 403, \"body\": \"WAF block: SQL injection attempt detected\"}"
                };

                for (String s : blockedSamples) {
                        assertTrue("Blocked sample should match: " + s, s.toLowerCase().matches(".*" + regex + ".*"));
                }
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
                        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(1, "/format-429-" + i,
                                        URLMethods.Method.POST);
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
                        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(1, "/format-non429-" + i,
                                        URLMethods.Method.GET);
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
                        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(1, "/perf-test-" + i,
                                        URLMethods.Method.GET);
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
                        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(1, "/5xx-test-" + i,
                                        URLMethods.Method.GET);
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
        public void testFetchTestResultsStatsCount_UsesApiErrorsWhenPresent() {
                TestingRunResultDao.instance.getMCollection().drop();
                ObjectId testingRunResultSummaryId = new ObjectId();

                // Insert minimal docs
                List<TestingRunResult> testingRunResults = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                        TestResult placeholder = new TestResult("{}",
                                        "", new ArrayList<>(), 100.0, false, TestResult.Confidence.HIGH, null);
                        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(1, "/api-errors-map-" + i,
                                        URLMethods.Method.GET);
                        TestingRunResult tr = new TestingRunResult(new ObjectId(), apiInfoKey, "TEST", "TEST",
                                        Arrays.asList(placeholder), false, new ArrayList<SingleTypeInfo>(),
                                        80, Context.now(), Context.now(), testingRunResultSummaryId,
                                        null, new ArrayList<TestingRunResult.TestLog>());
                        testingRunResults.add(tr);
                }
                TestingRunResultDao.instance.insertMany(testingRunResults);

                // Set apiErrors map: total 4 matches for 429 (2 + 1 + 1)
                TestingRunResultDao.instance.getRawCollection().updateOne(
                                new Document("testRunResultSummaryId", testingRunResultSummaryId)
                                                .append("apiInfoKey.url", "/api-errors-map-0"),
                                Updates.set("apiErrors",
                                                new Document("429", 2).append("5xx", 0).append("cloudflare", 0)));
                TestingRunResultDao.instance.getRawCollection().updateOne(
                                new Document("testRunResultSummaryId", testingRunResultSummaryId)
                                                .append("apiInfoKey.url", "/api-errors-map-1"),
                                Updates.set("apiErrors", new Document("429", 1)));
                TestingRunResultDao.instance.getRawCollection().updateOne(
                                new Document("testRunResultSummaryId", testingRunResultSummaryId)
                                                .append("apiInfoKey.url", "/api-errors-map-2"),
                                Updates.set("apiErrors", new Document("429", 1).append("5xx", 3)));

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
                assertEquals(4, action.getCount());
                assertTrue(action.isFromApiErrors());
        }

        @Test
        public void testFetchTestResultsStatsCount_FallbackWhenApiErrorsAbsent() {
                TestingRunResultDao.instance.getMCollection().drop();
                ObjectId testingRunResultSummaryId = new ObjectId();

                List<TestingRunResult> testingRunResults = new ArrayList<>();
                TestResult rateLimitResult = new TestResult("{\"statusCode\": 429}",
                                "", new ArrayList<>(), 100.0, false, TestResult.Confidence.HIGH, null);
                ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(1, "/no-api-errors", URLMethods.Method.GET);
                TestingRunResult tr = new TestingRunResult(new ObjectId(), apiInfoKey, "TEST", "TEST",
                                Arrays.asList(rateLimitResult), false, new ArrayList<SingleTypeInfo>(),
                                80, Context.now(), Context.now(), testingRunResultSummaryId,
                                null, new ArrayList<TestingRunResult.TestLog>());
                testingRunResults.add(tr);
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
                assertEquals(1, action.getCount());
                assertFalse(action.isFromApiErrors());
        }

        @Test
        public void testFetchTestResultsStatsCount_WithCloudflareResponses() {
                // Clear and setup test data
                TestingRunResultDao.instance.getMCollection().drop();
                ObjectId testingRunId = new ObjectId();
                ObjectId testingRunResultSummaryId = new ObjectId();

                List<TestingRunResult> testingRunResults = new ArrayList<>();

                // Create test results with various Cloudflare blocking scenarios
                String[] cloudflareErrors = {
                                "{\"statusCode\": 403, \"body\": \"Error 1020: Access denied\"}",
                                "{\"statusCode\": 429, \"body\": \"Error 1015: You are being rate limited\"}",
                                "{\"statusCode\": 403, \"body\": \"Attention Required! | Cloudflare\"}",
                                "{\"statusCode\": 403, \"body\": \"User blocked by Cloudflare\"}",
                                "{\"statusCode\": 403, \"body\": \"This website is using a security service to protect itself from online attacks.\"}",
                                "{\"statusCode\": 403, \"body\": \"Ray ID XYZ123 blocked\"}",
                                "{\"statusCode\": 403, \"body\": \"WAF rule triggered: SQL injection detected\"}",
                                "{\"statusCode\": 403, \"body\": \"WAF block: Malicious payload detected\"}",
                                "{\"statusCode\": 403, \"body\": \"Error 1012: Access denied\"}"
                };

                for (int i = 0; i < cloudflareErrors.length; i++) {
                        TestResult cloudflareErrorResult = new TestResult(cloudflareErrors[i],
                                        "", new ArrayList<>(), 100.0, false, TestResult.Confidence.HIGH, null);
                        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(1, "/cf-test-" + i,
                                        URLMethods.Method.POST);
                        TestingRunResult runResult = new TestingRunResult(
                                        testingRunId, apiInfoKey, "CLOUDFLARE_BLOCK", "CLOUDFLARE_BLOCK_TEST",
                                        Arrays.asList(cloudflareErrorResult), false, new ArrayList<SingleTypeInfo>(),
                                        80, Context.now(), Context.now(), testingRunResultSummaryId,
                                        null, new ArrayList<TestingRunResult.TestLog>());
                        testingRunResults.add(runResult);
                }

                // Add some benign responses that should NOT match
                String[] benignResponses = {
                                "{\"statusCode\": 200, \"headers\": {\"cf-ray\": \"7f9e7f2ad9be2a3c-DEL\"}}",
                                "{\"statusCode\": 204, \"headers\": {\"cf-ray\": \"72f0a1b7ce4321ab-LHR\"}}",
                                "{\"statusCode\": 302, \"headers\": {\"server\": \"cloudflare\"}}"
                };

                for (int i = 0; i < benignResponses.length; i++) {
                        TestResult benignResult = new TestResult(benignResponses[i],
                                        "", new ArrayList<>(), 100.0, false, TestResult.Confidence.HIGH, null);
                        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(1, "/benign-" + i,
                                        URLMethods.Method.GET);
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
                assertEquals(9, action.getCount()); // Should find 9 Cloudflare blocking scenarios, excluding benign
                                                    // responses
                assertTrue(action.getActionErrors().isEmpty());
        }

        @Test
        public void testRegexPatternForCloudflareHTMLErrorPages() {
                String regex = TestResultsStatsAction.REGEX_CLOUDFLARE;

                // HTML error pages that should match
                String[] htmlErrors = {
                                "<!DOCTYPE html><html><head><title>Attention Required! | Cloudflare</title></head><body><h1>Sorry, you have been blocked</h1></body></html>",
                                "<!DOCTYPE html><html><head><title>Just a moment...</title></head><body><p>DDoS protection by Cloudflare</p></body></html>",
                                "<!DOCTYPE html><html><head><title>Security Check - Cloudflare</title></head><body><div class=\"error-code\">Error 1020: Access denied</div></body></html>",
                                "<!DOCTYPE html><html><head><title>Under Attack Mode - Cloudflare</title></head><body><h1>Website Under Attack Mode</h1></body></html>",
                                "<html><body><h1>Checking your browser before accessing the website.</h1><p>DDoS protection by Cloudflare</p></body></html>"
                };

                for (String html : htmlErrors) {
                        assertTrue("HTML error page should match: " + html.substring(0, Math.min(100, html.length())),
                                        html.toLowerCase().matches(".*" + regex + ".*"));
                }
        }

        @Test
        public void testRegexPatternForCloudflareAPIErrors() {
                String regex = TestResultsStatsAction.REGEX_CLOUDFLARE;

                // API error responses that should match
                String[] apiErrors = {
                                "{\"error\":{\"code\":1020,\"message\":\"Access denied\"},\"ray_id\":\"7f2a8c9b4e1d3a6f\"}",
                                "{\"error\":{\"code\":1015,\"message\":\"Rate limit exceeded\"},\"ray_id\":\"8g3b9d0c5f2e4b7g\"}",
                                "{\"error\":{\"code\":1012,\"message\":\"Access denied\"},\"ray_id\":\"9h4c0e1d6g3f5c8h\"}",
                                "{\"error\":{\"code\":1025,\"message\":\"Please check back later\"},\"ray_id\":\"0i5d1f2e7h4g6d9i\"}",
                                "{\"error\":{\"code\":1101,\"message\":\"Worker threw exception\"},\"ray_id\":\"1j6e2g3f8i5h7e0j\"}",
                                "{\"error\":{\"code\":1102,\"message\":\"Worker exceeded CPU time limit\"},\"ray_id\":\"2k7f3h4g9j6i8f1k\"}",
                                "{\"error\":{\"code\":1200,\"message\":\"HTTP/1.1 400 Bad Request\"},\"ray_id\":\"3l8g4i5h0k7j9g2l\"}",
                                "{\"error\":{\"code\":10001,\"message\":\"API rate limit exceeded\"},\"ray_id\":\"4m9h5j6i1l8k0h3m\"}"
                };

                for (String apiError : apiErrors) {
                        assertTrue("API error should match: " + apiError,
                                        apiError.toLowerCase().matches(".*" + regex + ".*"));
                }
        }

        @Test
        public void testRegexPatternForWAFBlocking() {
                String regex = TestResultsStatsAction.REGEX_CLOUDFLARE;

                // WAF blocking messages that should match
                String[] wafMessages = {
                                "WAF Alert: SQL injection attempt detected and blocked by Cloudflare security rules",
                                "WAF Block: Cross-site scripting (XSS) attack prevented by Web Application Firewall",
                                "WAF Security: Malicious payload detected in request headers - access denied",
                                "WAF Protection: Command injection attempt blocked by security policy",
                                "WAF Triggered: Suspicious file upload blocked by Cloudflare WAF rules",
                                "WAF Defense: Directory traversal attack prevented by Web Application Firewall",
                                "WAF Alert: Server-side template injection attempt blocked",
                                "WAF Security: XML external entity (XXE) attack prevented",
                                "WAF Block: LDAP injection attempt detected and denied",
                                "WAF Protection: Remote file inclusion attack blocked by security rules"
                };

                for (String wafMessage : wafMessages) {
                        assertTrue("WAF message should match: " + wafMessage,
                                        wafMessage.toLowerCase().matches(".*" + regex + ".*"));
                }
        }

        @Test
        public void testRegexPatternForSpecificCloudflareErrorCodes() {
                String regex = TestResultsStatsAction.REGEX_CLOUDFLARE;

                // Test specific Cloudflare error codes
                String[] errorCodes = {
                                "Error 1000: DNS points to prohibited IP",
                                "Error 1001: DNS resolution error",
                                "Error 1002: DNS points to prohibited IP",
                                "Error 1003: Direct IP access not allowed",
                                "Error 1004: Host not configured to serve web traffic over HTTPS",
                                "Error 1005: Country or region blocked by administrator",
                                "Error 1006: Access denied due to robot activity",
                                "Error 1007: Access denied due to proxy traffic",
                                "Error 1008: Access denied due to VPN traffic",
                                "Error 1009: Access denied due to banned country",
                                "Error 1010: The owner of this website bans your access based on your browser",
                                "Error 1011: Access denied due to hotlinking",
                                "Error 1012: Access denied",
                                "Error 1013: HTTP hostname and TLS SNI hostname mismatch",
                                "Error 1014: CNAME Cross-User Banned",
                                "Error 1015: You are being rate limited",
                                "Error 1016: Origin DNS error",
                                "Error 1017: Origin web server connection failed",
                                "Error 1018: Could not route to the origin server",
                                "Error 1019: Compute server error",
                                "Error 1020: Access denied",
                                "Error 1021: The request is not allowed",
                                "Error 1022: The request is not allowed",
                                "Error 1023: Access is denied",
                                "Error 1024: Please check back later",
                                "Error 1025: Please check back later",
                                "Error 10000: Unknown error",
                                "Error 10001: Country blocked",
                                "Error 10002: Suspected bot activity",
                                "Error 10003: Request denied for security reasons",
                                "Error 10004: Too many requests",
                                "Error 10005: Access denied by security rule",
                                "Error 10006: Website temporarily disabled"
                };

                for (String errorCode : errorCodes) {
                        assertTrue("Error code should match: " + errorCode,
                                        errorCode.toLowerCase().matches(".*" + regex + ".*"));
                }
        }

        @Test
        public void testRegexPatternForUnderAttackMode() {
                String regex = TestResultsStatsAction.REGEX_CLOUDFLARE;

                // Under attack mode messages
                String[] underAttackMessages = {
                                "Website under attack mode activated by Cloudflare",
                                "This website is currently under attack and has activated Cloudflare protection",
                                "Under attack mode: additional security measures by Cloudflare",
                                "Cloudflare under attack mode: Please wait while we check your browser",
                                "DDoS protection: under attack mode enabled for this website",
                                "Security check in progress - under attack mode by Cloudflare"
                };

                for (String message : underAttackMessages) {
                        assertTrue("Under attack message should match: " + message,
                                        message.toLowerCase().matches(".*" + regex + ".*"));
                }
        }

        @Test
        public void testRegexPatternForDDoSProtection() {
                String regex = TestResultsStatsAction.REGEX_CLOUDFLARE;

                // DDoS protection messages
                String[] ddosMessages = {
                                "DDoS protection by Cloudflare activated",
                                "Cloudflare DDoS protection: request being verified",
                                "Anti-DDoS measures enabled by Cloudflare",
                                "DDoS attack mitigation in progress - protected by Cloudflare",
                                "Cloudflare is protecting this website from DDoS attacks"
                };

                for (String ddosMessage : ddosMessages) {
                        assertTrue("DDoS message should match: " + ddosMessage,
                                        ddosMessage.toLowerCase().matches(".*" + regex + ".*"));
                }
        }

        @Test
        public void testRegexPatternForSecurityChecks() {
                String regex = TestResultsStatsAction.REGEX_CLOUDFLARE;

                // Security check messages
                String[] securityMessages = {
                                "Security check required by Cloudflare",
                                "Cloudflare security check: verifying your browser",
                                "Additional security verification by Cloudflare required",
                                "Security challenge activated by Cloudflare protection",
                                "Cloudflare security screening in progress"
                };

                for (String securityMessage : securityMessages) {
                        assertTrue("Security message should match: " + securityMessage,
                                        securityMessage.toLowerCase().matches(".*" + regex + ".*"));
                }
        }

        @Test
        public void testRegexPatternForRayIdBlocked() {
                String regex = TestResultsStatsAction.REGEX_CLOUDFLARE;

                // Ray ID blocking scenarios
                String[] rayIdMessages = {
                                "Ray ID 7f2a8c9b4e1d3a6f blocked due to security violation",
                                "Request blocked - Ray ID: 8g3b9d0c5f2e4b7g",
                                "Access denied: Ray ID 9h4c0e1d6g3f5c8h blocked",
                                "Security violation - Ray ID abc123def456 blocked",
                                "Ray ID XYZ789 blocked by security policy"
                };

                for (String rayMessage : rayIdMessages) {
                        assertTrue("Ray ID blocked message should match: " + rayMessage,
                                        rayMessage.toLowerCase().matches(".*" + regex + ".*"));
                }
        }

        @Test
        public void testRegexPatternForBenignCloudflareTraffic() {
                String regex = TestResultsStatsAction.REGEX_CLOUDFLARE;

                // These should NOT match - benign Cloudflare traffic
                String[] benignMessages = {
                                "{\"statusCode\": 200, \"headers\": {\"cf-ray\": \"7f9e7f2ad9be2a3c-DEL\", \"server\": \"cloudflare\"}}",
                                "{\"statusCode\": 201, \"headers\": {\"cf-ray\": \"72f0a1b7ce4321ab-LHR\"}}",
                                "{\"statusCode\": 204, \"headers\": {\"cf-ray\": \"6a5d1e2f3c4b9abc-SIN\", \"cf-cache-status\": \"HIT\"}}",
                                "{\"statusCode\": 301, \"headers\": {\"server\": \"cloudflare\", \"cf-ray\": \"8090abcd1234efgh-BOM\"}}",
                                "{\"statusCode\": 302, \"headers\": {\"server\": \"cloudflare\"}}",
                                "Successful response via Cloudflare CDN",
                                "Content delivered by Cloudflare edge server",
                                "Response cached by Cloudflare",
                                "Static content served through Cloudflare network"
                };

                for (String benignMessage : benignMessages) {
                        assertFalse("Benign Cloudflare traffic should NOT match: " + benignMessage,
                                        benignMessage.toLowerCase().matches(".*" + regex + ".*"));
                }
        }
}
