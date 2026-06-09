package com.akto.action.testing;

import com.akto.MongoBasedTest;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TestRunStatusActionTest extends MongoBasedTest {

    private int urlCounter = 0;

    @Before
    public void cleanup() {
        TestingRunResultDao.instance.getMCollection().drop();
        urlCounter = 0;
    }

    // ---------- helpers (reused across cases) ----------

    private TestResult messageResult(String message) {
        return new TestResult(message, "", new ArrayList<>(), 0, false, TestResult.Confidence.LOW, null);
    }

    private TestResult errorResult(String error) {
        return new TestResult("", "", Collections.singletonList(error), 0, false, TestResult.Confidence.LOW, null);
    }

    private TestResult configResult() {
        TestResult tr = new TestResult("", "", new ArrayList<>(), 0, false, TestResult.Confidence.LOW, null);
        tr.setRequiresConfig(true);
        return tr;
    }

    private void insertResult(ObjectId summaryId, TestResult testResult, boolean vulnerable) {
        ApiInfo.ApiInfoKey key = new ApiInfo.ApiInfoKey(1, "/path" + (urlCounter++), URLMethods.Method.GET);
        TestingRunResult runResult = new TestingRunResult(
                new ObjectId(), key, "BOLA", "BOLA",
                Arrays.asList(testResult), vulnerable, new ArrayList<SingleTypeInfo>(),
                80, Context.now(), Context.now(), summaryId, null, new ArrayList<>());
        TestingRunResultDao.instance.insertOne(runResult);
    }

    private Map<String, Integer> fetchCounts(ObjectId summaryId) {
        TestRunStatusAction action = new TestRunStatusAction();
        action.setTestingRunResultSummaryHexIds(Collections.singletonList(summaryId.toHexString()));
        String result = action.fetchTestRunStatusSummaries();
        assertEquals("SUCCESS", result);
        Map<String, Map<String, Integer>> summaries = action.getTestRunStatusSummaries();
        assertNotNull(summaries);
        assertTrue(summaries.containsKey(summaryId.toHexString()));
        return summaries.get(summaryId.toHexString());
    }

    // ---------- input validation ----------

    @Test
    public void testMissingIds() {
        TestRunStatusAction action = new TestRunStatusAction();
        assertEquals("ERROR", action.fetchTestRunStatusSummaries());
    }

    @Test
    public void testEmptyIds() {
        TestRunStatusAction action = new TestRunStatusAction();
        action.setTestingRunResultSummaryHexIds(new ArrayList<>());
        assertEquals("ERROR", action.fetchTestRunStatusSummaries());
    }

    @Test
    public void testTooManyIds() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            ids.add(new ObjectId().toHexString());
        }
        TestRunStatusAction action = new TestRunStatusAction();
        action.setTestingRunResultSummaryHexIds(ids);
        assertEquals("ERROR", action.fetchTestRunStatusSummaries());
    }

    @Test
    public void testBlankIdIsSkippedGracefully() {
        TestRunStatusAction action = new TestRunStatusAction();
        action.setTestingRunResultSummaryHexIds(Arrays.asList("   ", ""));
        assertEquals("SUCCESS", action.fetchTestRunStatusSummaries());
        assertTrue(action.getTestRunStatusSummaries().isEmpty());
    }

    // ---------- per-category counts ----------

    @Test
    public void testDomainUnreachable() {
        ObjectId summaryId = new ObjectId();
        insertResult(summaryId, errorResult(TestResult.API_CALL_FAILED_ERROR_STRING), false);
        insertResult(summaryId, errorResult(TestResult.API_CALL_FAILED_ERROR_STRING_UNREACHABLE), false);
        assertEquals(2, (int) fetchCounts(summaryId).get("domainUnreachable"));
    }

    @Test
    public void testSkipped() {
        ObjectId summaryId = new ObjectId();
        String skipMessage = TestResult.TestError.NO_PATH.getMessage();
        insertResult(summaryId, errorResult(skipMessage), false);
        assertEquals(1, (int) fetchCounts(summaryId).get("skipped"));
    }

    @Test
    public void testNeedConfig() {
        ObjectId summaryId = new ObjectId();
        insertResult(summaryId, configResult(), false);
        assertEquals(1, (int) fetchCounts(summaryId).get("needConfig"));
    }

    @Test
    public void testHttp401() {
        ObjectId summaryId = new ObjectId();
        insertResult(summaryId, messageResult("{\"statusCode\": 401, \"body\": \"Unauthorized\"}"), false);
        assertEquals(1, (int) fetchCounts(summaryId).get("http401"));
    }

    @Test
    public void testHttp403() {
        ObjectId summaryId = new ObjectId();
        insertResult(summaryId, messageResult("{\"statusCode\": 403, \"body\": \"Forbidden\"}"), false);
        assertEquals(1, (int) fetchCounts(summaryId).get("http403"));
    }

    @Test
    public void testHttp5xx() {
        ObjectId summaryId = new ObjectId();
        insertResult(summaryId, messageResult("{\"statusCode\": 503, \"body\": \"Service Unavailable\"}"), false);
        assertEquals(1, (int) fetchCounts(summaryId).get("http5xx"));
    }

    @Test
    public void testHttp429() {
        ObjectId summaryId = new ObjectId();
        insertResult(summaryId, messageResult("{\"statusCode\": 429, \"body\": \"Too Many Requests\"}"), false);
        assertEquals(1, (int) fetchCounts(summaryId).get("http429"));
    }

    @Test
    public void testCloudflare() {
        ObjectId summaryId = new ObjectId();
        insertResult(summaryId,
                messageResult("{\"response\": {\"body\": \"<title>Attention Required! | Cloudflare</title>\"}}"),
                false);
        assertEquals(1, (int) fetchCounts(summaryId).get("cloudflare"));
    }

    // ---------- exclusions & isolation ----------

    @Test
    public void testVulnerableResultsExcluded() {
        ObjectId summaryId = new ObjectId();
        // vulnerable=true results are real findings (Issues column), not execution problems
        insertResult(summaryId, messageResult("{\"statusCode\": 403, \"body\": \"Forbidden\"}"), true);
        assertEquals(0, (int) fetchCounts(summaryId).get("http403"));
    }

    @Test
    public void testCountsIsolatedPerSummary() {
        ObjectId summaryA = new ObjectId();
        ObjectId summaryB = new ObjectId();
        insertResult(summaryA, messageResult("{\"statusCode\": 403}"), false);
        insertResult(summaryB, errorResult(TestResult.API_CALL_FAILED_ERROR_STRING), false);

        TestRunStatusAction action = new TestRunStatusAction();
        action.setTestingRunResultSummaryHexIds(
                Arrays.asList(summaryA.toHexString(), summaryB.toHexString()));
        assertEquals("SUCCESS", action.fetchTestRunStatusSummaries());

        Map<String, Map<String, Integer>> summaries = action.getTestRunStatusSummaries();
        assertEquals(1, (int) summaries.get(summaryA.toHexString()).get("http403"));
        assertEquals(0, (int) summaries.get(summaryA.toHexString()).get("domainUnreachable"));
        assertEquals(1, (int) summaries.get(summaryB.toHexString()).get("domainUnreachable"));
        assertEquals(0, (int) summaries.get(summaryB.toHexString()).get("http403"));
    }

    @Test
    public void testMixedCategoriesInSingleSummary() {
        ObjectId summaryId = new ObjectId();
        insertResult(summaryId, errorResult(TestResult.API_CALL_FAILED_ERROR_STRING_UNREACHABLE), false);
        insertResult(summaryId, messageResult("{\"statusCode\": 403}"), false);
        insertResult(summaryId, messageResult("{\"statusCode\": 500}"), false);
        insertResult(summaryId, configResult(), false);

        Map<String, Integer> counts = fetchCounts(summaryId);
        assertEquals(1, (int) counts.get("domainUnreachable"));
        assertEquals(1, (int) counts.get("http403"));
        assertEquals(1, (int) counts.get("http5xx"));
        assertEquals(1, (int) counts.get("needConfig"));
    }
}
