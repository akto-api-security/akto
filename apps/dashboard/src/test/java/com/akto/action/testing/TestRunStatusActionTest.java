package com.akto.action.testing;

import com.akto.MongoBasedTest;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.conversions.Bson;
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

    // a result with no testResults => coalesced statusMessage resolves to null,
    // which must not break the status-code aggregation for other rows
    private void insertResultWithoutMessage(ObjectId summaryId) {
        ApiInfo.ApiInfoKey key = new ApiInfo.ApiInfoKey(1, "/path" + (urlCounter++), URLMethods.Method.GET);
        TestingRunResult runResult = new TestingRunResult(
                new ObjectId(), key, "BOLA", "BOLA",
                new ArrayList<>(), false, new ArrayList<SingleTypeInfo>(),
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

    // status-code buckets are only present when count > 0, so read them null-safely
    private int count(Map<String, Integer> counts, String key) {
        return counts.getOrDefault(key, 0);
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
        assertEquals(1, count(fetchCounts(summaryId), "http_401"));
    }

    @Test
    public void testHttp403() {
        ObjectId summaryId = new ObjectId();
        insertResult(summaryId, messageResult("{\"statusCode\": 403, \"body\": \"Forbidden\"}"), false);
        assertEquals(1, count(fetchCounts(summaryId), "http_403"));
    }

    @Test
    public void testHttp5xx() {
        ObjectId summaryId = new ObjectId();
        insertResult(summaryId, messageResult("{\"statusCode\": 503, \"body\": \"Service Unavailable\"}"), false);
        assertEquals(1, count(fetchCounts(summaryId), "http_503"));
    }

    @Test
    public void testHttp429() {
        ObjectId summaryId = new ObjectId();
        insertResult(summaryId, messageResult("{\"statusCode\": 429, \"body\": \"Too Many Requests\"}"), false);
        assertEquals(1, count(fetchCounts(summaryId), "http_429"));
    }

    @Test
    public void test404SurfacedByItsOwnCode() {
        ObjectId summaryId = new ObjectId();
        // a client error that is not specifically called out (404) must still be surfaced by its code
        insertResult(summaryId, messageResult("{\"statusCode\": 404, \"body\": \"Not Found\"}"), false);
        Map<String, Integer> counts = fetchCounts(summaryId);
        assertEquals(1, count(counts, "http_404"));
        assertEquals(0, count(counts, "http_403"));
    }

    @Test
    public void testEachStatusCodeSurfacedSeparately() {
        ObjectId summaryId = new ObjectId();
        insertResult(summaryId, messageResult("{\"statusCode\": 429, \"body\": \"Too Many Requests\"}"), false);
        insertResult(summaryId, messageResult("{\"statusCode\": 401, \"body\": \"Unauthorized\"}"), false);
        insertResult(summaryId, messageResult("{\"statusCode\": 404, \"body\": \"Not Found\"}"), false);
        Map<String, Integer> counts = fetchCounts(summaryId);
        assertEquals(1, count(counts, "http_429"));
        assertEquals(1, count(counts, "http_401"));
        assertEquals(1, count(counts, "http_404"));
    }

    @Test
    public void testDistinct5xxCodesSurfacedSeparately() {
        ObjectId summaryId = new ObjectId();
        insertResult(summaryId, messageResult("{\"statusCode\": 500, \"body\": \"Internal Server Error\"}"), false);
        insertResult(summaryId, messageResult("{\"statusCode\": 502, \"body\": \"Bad Gateway\"}"), false);
        insertResult(summaryId, messageResult("{\"statusCode\": 502, \"body\": \"Bad Gateway\"}"), false);
        Map<String, Integer> counts = fetchCounts(summaryId);
        assertEquals(1, count(counts, "http_500"));
        assertEquals(2, count(counts, "http_502"));
    }

    @Test
    public void test2xxResponsesNotCounted() {
        ObjectId summaryId = new ObjectId();
        insertResult(summaryId, messageResult("{\"statusCode\": 200, \"body\": \"OK\"}"), false);
        Map<String, Integer> counts = fetchCounts(summaryId);
        assertEquals(0, count(counts, "http_200"));
    }

    @Test
    public void testNullMessageRowsDoNotBreakStatusCodeCounts() {
        ObjectId summaryId = new ObjectId();
        // rows whose coalesced message is null must be tolerated by the $regexFind aggregation
        insertResultWithoutMessage(summaryId);
        insertResultWithoutMessage(summaryId);
        insertResult(summaryId, messageResult("{\"statusCode\": 403, \"body\": \"Forbidden\"}"), false);
        insertResult(summaryId, messageResult("{\"statusCode\": 500, \"body\": \"Internal Server Error\"}"), false);

        Map<String, Integer> counts = fetchCounts(summaryId);
        assertEquals(1, count(counts, "http_403"));
        assertEquals(1, count(counts, "http_500"));
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
        assertEquals(0, count(fetchCounts(summaryId), "http_403"));
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
        assertEquals(1, count(summaries.get(summaryA.toHexString()), "http_403"));
        assertEquals(0, count(summaries.get(summaryA.toHexString()), "domainUnreachable"));
        assertEquals(1, count(summaries.get(summaryB.toHexString()), "domainUnreachable"));
        assertEquals(0, count(summaries.get(summaryB.toHexString()), "http_403"));
    }

    @Test
    public void testMixedCategoriesInSingleSummary() {
        ObjectId summaryId = new ObjectId();
        insertResult(summaryId, errorResult(TestResult.API_CALL_FAILED_ERROR_STRING_UNREACHABLE), false);
        insertResult(summaryId, messageResult("{\"statusCode\": 403}"), false);
        insertResult(summaryId, messageResult("{\"statusCode\": 500}"), false);
        insertResult(summaryId, configResult(), false);

        Map<String, Integer> counts = fetchCounts(summaryId);
        assertEquals(1, count(counts, "domainUnreachable"));
        assertEquals(1, count(counts, "http_403"));
        assertEquals(1, count(counts, "http_500"));
        assertEquals(1, count(counts, "needConfig"));
    }

    // ---------- response code filter ----------

    private int countMatching(ObjectId summaryId, List<String> codes) {
        Bson filter = Filters.and(
                Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, summaryId),
                TestRunStatusHelper.responseCodeMessageFilter(codes));
        return TestingRunResultDao.instance.findAll(filter, Projections.include("_id")).size();
    }

    // @Test
    // public void testResponseCodeFilterMatchesOnlySelectedCode() {
    //     ObjectId summaryId = new ObjectId();
    //     insertResult(summaryId, messageResult("{\"statusCode\": 403, \"body\": \"Forbidden\"}"), false);
    //     insertResult(summaryId, messageResult("{\"statusCode\": 500, \"body\": \"Internal Server Error\"}"), false);

    //     assertEquals(1, countMatching(summaryId, Collections.singletonList("403")));
    //     assertEquals(1, countMatching(summaryId, Collections.singletonList("500")));
    //     assertEquals(0, countMatching(summaryId, Collections.singletonList("404")));
    // }

    // @Test
    // public void testResponseCodeFilterMatchesAnyOfMultipleCodes() {
    //     ObjectId summaryId = new ObjectId();
    //     insertResult(summaryId, messageResult("{\"statusCode\": 401, \"body\": \"Unauthorized\"}"), false);
    //     insertResult(summaryId, messageResult("{\"statusCode\": 403, \"body\": \"Forbidden\"}"), false);
    //     insertResult(summaryId, messageResult("{\"statusCode\": 200, \"body\": \"OK\"}"), false);

    //     assertEquals(2, countMatching(summaryId, Arrays.asList("401", "403")));
    // }

    @Test
    public void testResponseCodeFilterIgnoresInvalidCodes() {
        // invalid/non 3-digit codes yield an empty filter (no response-code constraint added)
        assertTrue(TestRunStatusHelper.responseCodeMessageFilter(Collections.singletonList("abc"))
                .equals(Filters.empty()));
        assertTrue(TestRunStatusHelper.responseCodeMessageFilter(new ArrayList<>())
                .equals(Filters.empty()));
    }

    // ---------- distinct response codes (filter choices) ----------

    private List<String> fetchDistinctCodes(ObjectId summaryId) {
        TestRunStatusAction action = new TestRunStatusAction();
        action.setTestingRunResultSummaryHexId(summaryId.toHexString());
        assertEquals("SUCCESS", action.fetchDistinctResponseCodes());
        return action.getResponseCodes();
    }

    @Test
    public void testFetchDistinctResponseCodesReturnsOnlyPresentCodesSorted() {
        ObjectId summaryId = new ObjectId();
        insertResult(summaryId, messageResult("{\"statusCode\": 500}"), false);
        insertResult(summaryId, messageResult("{\"statusCode\": 403}"), false);
        insertResult(summaryId, messageResult("{\"statusCode\": 200, \"body\": \"OK\"}"), false);

        assertEquals(Arrays.asList("403", "500"), fetchDistinctCodes(summaryId));
    }

    @Test
    public void testFetchDistinctResponseCodesEmptyWhenNoHttpErrors() {
        ObjectId summaryId = new ObjectId();
        insertResult(summaryId, errorResult(TestResult.API_CALL_FAILED_ERROR_STRING), false);
        insertResultWithoutMessage(summaryId);

        assertTrue(fetchDistinctCodes(summaryId).isEmpty());
    }

    @Test
    public void testFetchDistinctResponseCodesExcludesVulnerableResults() {
        // mirrors the scan run status column, which treats vulnerable results as findings
        // (Issues column) rather than execution problems, so codes only present on vulnerable
        // results are not offered as filter choices
        ObjectId summaryId = new ObjectId();
        insertResult(summaryId, messageResult("{\"statusCode\": 403}"), true);
        insertResult(summaryId, messageResult("{\"statusCode\": 404}"), false);

        assertEquals(Collections.singletonList("404"), fetchDistinctCodes(summaryId));
    }
}
