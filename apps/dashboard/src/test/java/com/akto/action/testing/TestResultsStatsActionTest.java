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
import org.bson.types.ObjectId;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class TestResultsStatsActionTest extends MongoBasedTest {

        @Test
        public void testFetchTestResultsStatsCount_MissingParameters() {
                // Test case: Both parameters missing
                TestResultsStatsAction action = new TestResultsStatsAction();

                String result = action.fetchTestResultsStatsCount();

                assertEquals("ERROR", result);
                Collection<String> errors = action.getActionErrors();
                assertFalse(errors.isEmpty());
                assertTrue(errors.iterator().next().contains("Missing required parameters"));
        }

        @Test
        public void testFetchTestResultsStatsCount_MissingTestingRunHexId() {
                // Test case: Only testingRunHexId missing
                TestResultsStatsAction action = new TestResultsStatsAction();
                action.setTestingRunResultSummaryHexId(new ObjectId().toHexString());
                // testingRunHexId not set

                String result = action.fetchTestResultsStatsCount();

                assertEquals("ERROR", result);
                Collection<String> errors = action.getActionErrors();
                assertFalse(errors.isEmpty());
                assertTrue(errors.iterator().next().contains("Missing required parameters"));
        }

        @Test
        public void testFetchTestResultsStatsCount_MissingTestingRunResultSummaryHexId() {
                // Test case: Only testingRunResultSummaryHexId missing
                TestResultsStatsAction action = new TestResultsStatsAction();
                action.setTestingRunHexId(new ObjectId().toHexString());
                // testingRunResultSummaryHexId not set

                String result = action.fetchTestResultsStatsCount();

                assertEquals("ERROR", result);
                Collection<String> errors = action.getActionErrors();
                assertFalse(errors.isEmpty());
                assertTrue(errors.iterator().next().contains("Missing required parameters"));
        }

        @Test
        public void testFetchTestResultsStatsCount_InvalidObjectIds() {
                // Test case: Various invalid ObjectId formats for both parameters
                TestResultsStatsAction action = new TestResultsStatsAction();

                // Test invalid run ID
                action.setTestingRunHexId("invalid-object-id");
                action.setTestingRunResultSummaryHexId(new ObjectId().toHexString());
                String result = action.fetchTestResultsStatsCount();
                assertEquals("ERROR", result);
                assertTrue(action.getActionErrors().iterator().next()
                                .contains("Invalid test summary id or test run id"));

                // Test invalid summary ID
                action = new TestResultsStatsAction();
                action.setTestingRunHexId(new ObjectId().toHexString());
                action.setTestingRunResultSummaryHexId("another-invalid-id");
                result = action.fetchTestResultsStatsCount();
                assertEquals("ERROR", result);
                assertTrue(action.getActionErrors().iterator().next()
                                .contains("Invalid test summary id or test run id"));

                // Test various malformed formats
                action = new TestResultsStatsAction();
                action.setTestingRunHexId("abc123"); // too short
                action.setTestingRunResultSummaryHexId("507f1f77bcf86cd799439@11"); // special chars
                result = action.fetchTestResultsStatsCount();
                assertEquals("ERROR", result);
                assertTrue(action.getActionErrors().iterator().next()
                                .contains("Invalid test summary id or test run id"));
        }

        @Test
        public void testFetchTestResultsStatsCount_EmptyStringParameters() {
                // Test case: Empty string parameters
                TestResultsStatsAction action = new TestResultsStatsAction();
                action.setTestingRunHexId("   "); // whitespace only
                action.setTestingRunResultSummaryHexId(""); // empty string

                String result = action.fetchTestResultsStatsCount();

                assertEquals("ERROR", result);
                Collection<String> errors = action.getActionErrors();
                assertFalse(errors.isEmpty());
                assertTrue(errors.iterator().next().contains("Missing required parameters"));
        }

        @Test
        public void testFetchTestResultsStatsCount_NullParameters() {
                // Test case: Explicitly null parameters
                TestResultsStatsAction action = new TestResultsStatsAction();
                action.setTestingRunHexId(null);
                action.setTestingRunResultSummaryHexId(null);

                String result = action.fetchTestResultsStatsCount();

                assertEquals("ERROR", result);
                Collection<String> errors = action.getActionErrors();
                assertFalse(errors.isEmpty());
                assertTrue(errors.iterator().next().contains("Missing required parameters"));
        }

        @Test
        public void testFetchTestResultsStatsCount_ValidParametersNoData() {
                // Test case: Various valid parameters but no data in database
                TestResultsStatsAction action = new TestResultsStatsAction();

                // Test with normal ObjectIds
                action.setTestingRunHexId(new ObjectId().toHexString());
                action.setTestingRunResultSummaryHexId(new ObjectId().toHexString());
                String result = action.fetchTestResultsStatsCount();
                assertEquals("SUCCESS", result);
                assertEquals(0, action.getCount());

                // Test with edge case ObjectIds (all zeros, all 1s)
                action = new TestResultsStatsAction();
                action.setTestingRunHexId("000000000000000000000000");
                action.setTestingRunResultSummaryHexId("111111111111111111111111");
                result = action.fetchTestResultsStatsCount();
                assertEquals("SUCCESS", result);
                assertEquals(0, action.getCount());
        }

        @Test
        public void testAction_429StatusCodeDetectionLogic() {
                // Test that validates the regex pattern used for 429 detection

                String regex = "\"statusCode\"\\s*:\\s*429";

                // Should match these 429 patterns:
                assertTrue("{\"statusCode\": 429}".matches(".*" + regex + ".*"));
                assertTrue("{\"statusCode\":429}".matches(".*" + regex + ".*"));
                assertTrue("{\"statusCode\"   :   429}".matches(".*" + regex + ".*"));
                assertTrue("{\"other\":\"data\",\"statusCode\": 429,\"message\":\"rate limited\"}"
                                .matches(".*" + regex + ".*"));

                // Should NOT match these non-429 patterns:
                assertFalse("{\"statusCode\": 200}".matches(".*" + regex + ".*"));
                assertFalse("{\"statusCode\": \"429\"}".matches(".*" + regex + ".*")); // String 429
                assertFalse("{\"status\": 429}".matches(".*" + regex + ".*")); // Wrong field name
                assertFalse("{\"message\": \"429 error\"}".matches(".*" + regex + ".*")); // 429 in message only
        }

        @Test
        public void testGettersAndSetters() {
                // Test the getter and setter methods
                TestResultsStatsAction action = new TestResultsStatsAction();

                String testRunId = new ObjectId().toHexString();
                String summaryId = new ObjectId().toHexString();

                action.setTestingRunHexId(testRunId);
                action.setTestingRunResultSummaryHexId(summaryId);

                assertEquals(testRunId, action.getTestingRunHexId());
                assertEquals(summaryId, action.getTestingRunResultSummaryHexId());

                assertEquals(0, action.getCount());
        }

        @Test
        public void testActionInitialState() {
                // Test the initial state of a new action instance
                TestResultsStatsAction action = new TestResultsStatsAction();

                // Initial values should be null/0
                assertNull(action.getTestingRunHexId());
                assertNull(action.getTestingRunResultSummaryHexId());
                assertEquals(0, action.getCount());
                assertTrue(action.getActionErrors().isEmpty());
        }

        @Test
        public void testMultipleErrorAccumulation() {
                // Test that multiple validation errors can be accumulated
                TestResultsStatsAction action = new TestResultsStatsAction();

                // First call with missing parameters
                action.fetchTestResultsStatsCount();
                int firstErrorCount = action.getActionErrors().size();
                assertTrue(firstErrorCount > 0);

                // Second call with invalid IDs (errors should accumulate)
                action.setTestingRunHexId("invalid1");
                action.setTestingRunResultSummaryHexId("invalid2");
                action.fetchTestResultsStatsCount();

                int secondErrorCount = action.getActionErrors().size();
                assertTrue("Errors should accumulate", secondErrorCount >= firstErrorCount);
        }

        @Test
        public void testFetchTestResultsStatsCount_With429Responses() {
                // Setup: Clear collection and prepare test data with 429s
                TestingRunResultDao.instance.getMCollection().drop();

                ObjectId testingRunId = new ObjectId();
                ObjectId testingRunResultSummaryId = new ObjectId();

                // Create test results with 429 status codes
                List<TestingRunResult> testingRunResults = new ArrayList<>();

                // Test result 1: Contains 429 status code
                TestResult rateLimitResult = new TestResult("{\"statusCode\": 429, \"body\": \"Too Many Requests\"}",
                                "", new ArrayList<>(), 100.0, false, TestResult.Confidence.HIGH, null);

                ApiInfo.ApiInfoKey apiInfoKey1 = new ApiInfo.ApiInfoKey(1, "/test1", URLMethods.Method.GET);
                TestingRunResult runResult1 = new TestingRunResult(
                                testingRunId, apiInfoKey1, "RATE_LIMIT", "RATE_LIMIT_TEST",
                                Arrays.asList(rateLimitResult), false, new ArrayList<SingleTypeInfo>(),
                                80, Context.now(), Context.now(), testingRunResultSummaryId,
                                null, new ArrayList<TestingRunResult.TestLog>());
                testingRunResults.add(runResult1);

                // Test result 2: Another 429 with different formatting
                TestResult throttledResult = new TestResult("{\"statusCode\":429,\"error\":\"Rate limited\"}",
                                "", new ArrayList<>(), 100.0, false, TestResult.Confidence.HIGH, null);

                ApiInfo.ApiInfoKey apiInfoKey2 = new ApiInfo.ApiInfoKey(1, "/test2", URLMethods.Method.POST);
                TestingRunResult runResult2 = new TestingRunResult(
                                testingRunId, apiInfoKey2, "RATE_LIMIT", "RATE_LIMIT_TEST",
                                Arrays.asList(throttledResult), false, new ArrayList<SingleTypeInfo>(),
                                80, Context.now(), Context.now(), testingRunResultSummaryId,
                                null, new ArrayList<TestingRunResult.TestLog>());
                testingRunResults.add(runResult2);

                // Insert test data into database
                TestingRunResultDao.instance.insertMany(testingRunResults);

                // Set up context like in TestRolesActionTest
                Context.userId.set(0);
                Context.contextSource.set(GlobalEnums.CONTEXT_SOURCE.API);

                // Set up session like in TestRolesActionTest
                TestResultsStatsAction action = new TestResultsStatsAction();
                Map<String, Object> session = new HashMap<>();
                User user = new User();
                user.setLogin("test@akto.io");
                session.put("user", user);
                action.setSession(session);

                action.setTestingRunResultSummaryHexId(testingRunResultSummaryId.toHexString());
                action.setTestingRunHexId(testingRunId.toHexString());

                String result = action.fetchTestResultsStatsCount();

                // Assert: Verify the results
                assertEquals("SUCCESS", result);
                assertEquals(2, action.getCount()); // Should find 2 results with 429 status codes
                assertTrue(action.getActionErrors().isEmpty());
        }

        @Test
        public void testFetchTestResultsStatsCount_No429Responses() {
                // Setup: Clear collection and prepare test data without 429s
                TestingRunResultDao.instance.getMCollection().drop();

                ObjectId testingRunId = new ObjectId();
                ObjectId testingRunResultSummaryId = new ObjectId();

                // Create test results WITHOUT 429 status codes
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
                action.setTestingRunHexId(testingRunId.toHexString());

                String result = action.fetchTestResultsStatsCount();

                assertEquals("SUCCESS", result);
                assertEquals(0, action.getCount()); // Should find 0 results with 429 status codes
                assertTrue(action.getActionErrors().isEmpty());
        }

        @Test
        public void testFetchTestResultsStatsCount_VariousStatusCodeFormats() {
                // Test with actual database operations using various 429 formats
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
                                        "", new ArrayList<>(), 100.0, true, TestResult.Confidence.HIGH, null);

                        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(1, "/format-429-" + i,
                                        URLMethods.Method.POST);
                        TestingRunResult runResult = new TestingRunResult(
                                        testingRunId, apiInfoKey, "RATE_LIMIT", "FORMAT_TEST",
                                        Arrays.asList(testResult), true, new ArrayList<SingleTypeInfo>(),
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
                                "{\"statusCode\": \"429\", \"note\": \"string not number\"}" // String 429 shouldn't
                                                                                             // match
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

                // Set up context
                Context.userId.set(0);
                Context.contextSource.set(GlobalEnums.CONTEXT_SOURCE.API);

                // Execute
                TestResultsStatsAction action = new TestResultsStatsAction();
                Map<String, Object> session = new HashMap<>();
                User user = new User();
                user.setLogin("test@akto.io");
                session.put("user", user);
                action.setSession(session);

                action.setTestingRunHexId(testingRunId.toHexString());
                action.setTestingRunResultSummaryHexId(testingRunResultSummaryId.toHexString());

                String result = action.fetchTestResultsStatsCount();

                // Assert: Should find exactly 6 (only the 429 numeric status codes)
                assertEquals("SUCCESS", result);
                assertEquals(6, action.getCount());
                assertTrue(action.getActionErrors().isEmpty());
        }
}
