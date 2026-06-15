package com.akto.action.testing;

import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.VulnerableTestingRunResultDao;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Facet;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import lombok.AllArgsConstructor;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class TestRunStatusHelper {

    public static final String REGEX_401 = "\"statusCode\"\\s*:\\s*401";
    public static final String REGEX_403 = "\"statusCode\"\\s*:\\s*403";

    private static final int SAMPLE_LIMIT = 3000;
    private static final String MULTI_EXEC_MESSAGE_FIELD = "testResults.nodeResultMap.x1.message";
    private static final String STATUS_MESSAGE = "statusMessage";

    private static final String CLOUDFLARE_KEY = "cloudflare";
    /** Per-status-code counts are keyed as {@code http_<code>}, e.g. {@code http_429}. */
    private static final String HTTP_CODE_KEY_PREFIX = "http_";
    /**
     * Captures the actual HTTP status code from a result message, but only for 4xx/5xx
     * responses. This lets us surface every client/server error by its real code (the way 429
     * was surfaced) instead of collapsing them into generic buckets. The negative lookahead
     * guards against 4-digit values (e.g. Cloudflare 1xxx codes) being truncated to 3 digits.
     */
    private static final String STATUS_CODE_CAPTURE_REGEX =
            "\"statusCode\"\\s*:\\s*([45][0-9]{2})(?![0-9])";

    /*
     * Execution status counts are derived from TestingRunResult docs, which are immutable
     * once a run finishes. The bulk status API is only called for COMPLETED runs, so caching
     * per (accountId, summaryId) avoids re-running these heavy aggregations on every page load.
     */
    private static final long CACHE_TTL_MILLIS = TimeUnit.MINUTES.toMillis(30);
    private static final int CACHE_MAX_SIZE = 20000;
    private static final Map<String, CacheEntry> STATUS_COUNTS_CACHE = new ConcurrentHashMap<>();

    private TestRunStatusHelper() {
    }

    /**
     * Returns cached execution status counts for a summary, computing and caching them on a miss.
     * Cache is scoped by account to avoid cross-tenant leakage.
     */
    public static Map<String, Integer> computeStatusCountsCached(int accountId, ObjectId testingRunResultSummaryId) {
        String cacheKey = accountId + ":" + testingRunResultSummaryId.toHexString();
        long now = System.currentTimeMillis();

        CacheEntry entry = STATUS_COUNTS_CACHE.get(cacheKey);
        if (entry != null && entry.expiryAt > now) {
            return entry.counts;
        }

        Map<String, Integer> counts = computeStatusCounts(testingRunResultSummaryId);

        if (STATUS_COUNTS_CACHE.size() >= CACHE_MAX_SIZE) {
            STATUS_COUNTS_CACHE.entrySet().removeIf(e -> e.getValue().expiryAt <= now);
            if (STATUS_COUNTS_CACHE.size() >= CACHE_MAX_SIZE) {
                STATUS_COUNTS_CACHE.clear();
            }
        }
        STATUS_COUNTS_CACHE.put(cacheKey, new CacheEntry(counts, now + CACHE_TTL_MILLIS));
        return counts;
    }

    public static Map<String, Integer> computeStatusCounts(ObjectId testingRunResultSummaryId) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("domainUnreachable", countDomainUnreachable(testingRunResultSummaryId));
        counts.put("skipped", countSkipped(testingRunResultSummaryId));
        counts.put("needConfig", countNeedConfig(testingRunResultSummaryId));
        counts.putAll(countStatusCodesAndCloudflare(testingRunResultSummaryId));
        return counts;
    }

    public static int countDomainUnreachable(ObjectId testingRunResultSummaryId) {
        List<Bson> filterList = new ArrayList<>();
        filterList.add(Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, testingRunResultSummaryId));
        filterList.add(Filters.eq(TestingRunResult.VULNERABLE, false));
        filterList.add(Filters.in(
                TestingRunResultDao.ERRORS_KEY,
                TestResult.API_CALL_FAILED_ERROR_STRING,
                TestResult.API_CALL_FAILED_ERROR_STRING_UNREACHABLE));
        return VulnerableTestingRunResultDao.instance.countFromDb(Filters.and(filterList), false);
    }

    public static int countSkipped(ObjectId testingRunResultSummaryId) {
        List<Bson> filterList = new ArrayList<>();
        filterList.add(Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, testingRunResultSummaryId));
        filterList.add(Filters.eq(TestingRunResult.VULNERABLE, false));
        filterList.add(Filters.in(TestingRunResultDao.ERRORS_KEY, TestResult.TestError.getErrorsToSkipTests()));
        return VulnerableTestingRunResultDao.instance.countFromDb(Filters.and(filterList), false);
    }

    public static int countNeedConfig(ObjectId testingRunResultSummaryId) {
        List<Bson> filterList = new ArrayList<>();
        filterList.add(Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, testingRunResultSummaryId));
        filterList.add(Filters.eq(TestingRunResult.VULNERABLE, false));
        filterList.add(Filters.eq(TestingRunResult.REQUIRES_CONFIG, true));
        return VulnerableTestingRunResultDao.instance.countFromDb(Filters.and(filterList), false);
    }

    /**
     * Counts results matching a single message pattern for a summary. Reuses the same
     * sampled, coalesced-message pipeline as {@link #countStatusCodesAndCloudflare(ObjectId)}
     * so the stats endpoint and the bulk status endpoint stay consistent.
     */
    public static int countByPattern(ObjectId testingRunResultSummaryId, String regex) {
        List<Bson> pipeline = sampledMessagePipeline(testingRunResultSummaryId);
        pipeline.add(Aggregates.match(Filters.regex(STATUS_MESSAGE, regex, "i")));
        pipeline.add(Aggregates.count("count"));

        MongoCursor<BasicDBObject> cursor = TestingRunResultDao.instance.getMCollection()
                .aggregate(pipeline, BasicDBObject.class).cursor();
        try {
            return cursor.hasNext() ? cursor.next().getInt("count", 0) : 0;
        } finally {
            cursor.close();
        }
    }

    /**
     * Surfaces every 4xx/5xx response by its actual HTTP status code (e.g. {@code http_429},
     * {@code http_404}, {@code http_500}) plus a Cloudflare bucket, in a single aggregation per
     * summary. Both single-exec ({@code testResults.message}) and multi-exec
     * ({@code testResults.nodeResultMap.x1.message}) messages are coalesced into one
     * {@code statusMessage}; one {@code $facet} branch groups by the extracted status code while
     * the other counts Cloudflare blocking pages (which are body-based, not status-code based).
     */
    private static Map<String, Integer> countStatusCodesAndCloudflare(ObjectId testingRunResultSummaryId) {
        List<Bson> pipeline = sampledMessagePipeline(testingRunResultSummaryId);

        // $regexFind errors out unless "input" is a string. statusMessage can resolve to a
        // non-string (e.g. an empty array when a result has no testResults, since the multi-exec
        // fallback path is evaluated over the testResults array) which would fail the whole
        // aggregation. Coerce any non-string to "" so it simply yields no match, mirroring the
        // null/type-tolerant behaviour of the previous $match/$regex approach.
        BasicDBObject statusMessageAsString = new BasicDBObject("$cond", Arrays.asList(
                new BasicDBObject("$eq", Arrays.asList(
                        new BasicDBObject("$type", "$" + STATUS_MESSAGE), "string")),
                "$" + STATUS_MESSAGE,
                ""));
        BasicDBObject regexFind = new BasicDBObject("$regexFind",
                new BasicDBObject("input", statusMessageAsString)
                        .append("regex", STATUS_CODE_CAPTURE_REGEX)
                        .append("options", "i"));

        Facet byCode = new Facet("byCode",
                new BasicDBObject("$project", new BasicDBObject("codeMatch", regexFind)),
                new BasicDBObject("$match", new BasicDBObject("codeMatch", new BasicDBObject("$ne", null))),
                new BasicDBObject("$project", new BasicDBObject("code",
                        new BasicDBObject("$arrayElemAt", Arrays.asList("$codeMatch.captures", 0)))),
                new BasicDBObject("$group", new BasicDBObject("_id", "$code")
                        .append("count", new BasicDBObject("$sum", 1))));

        Facet cloudflare = new Facet(CLOUDFLARE_KEY,
                Aggregates.match(Filters.regex(STATUS_MESSAGE, TestResultsStatsAction.REGEX_CLOUDFLARE, "i")),
                Aggregates.count("count"));

        pipeline.add(Aggregates.facet(byCode, cloudflare));

        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(CLOUDFLARE_KEY, 0);

        MongoCursor<Document> cursor = TestingRunResultDao.instance.getMCollection()
                .aggregate(pipeline, Document.class)
                .cursor();
        try {
            if (cursor.hasNext()) {
                Document facetResult = cursor.next();

                List<Document> codeBuckets = facetResult.getList("byCode", Document.class);
                if (codeBuckets != null) {
                    for (Document bucket : codeBuckets) {
                        String code = bucket.getString("_id");
                        if (code == null || code.isEmpty()) {
                            continue;
                        }
                        counts.put(HTTP_CODE_KEY_PREFIX + code, bucket.getInteger("count", 0));
                    }
                }

                List<Document> cloudflareBuckets = facetResult.getList(CLOUDFLARE_KEY, Document.class);
                if (cloudflareBuckets != null && !cloudflareBuckets.isEmpty()) {
                    counts.put(CLOUDFLARE_KEY, cloudflareBuckets.get(0).getInteger("count", 0));
                }
            }
        } finally {
            cursor.close();
        }
        return counts;
    }

    /**
     * Shared pipeline prefix: samples up to {@link #SAMPLE_LIMIT} non-vulnerable results for a
     * summary and projects a single {@code statusMessage} coalescing single-exec and multi-exec
     * message fields.
     */
    private static List<Bson> sampledMessagePipeline(ObjectId testingRunResultSummaryId) {
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(Filters.and(
                Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, testingRunResultSummaryId),
                Filters.eq(TestingRunResult.VULNERABLE, false))));
        pipeline.add(Aggregates.limit(SAMPLE_LIMIT));
        pipeline.add(Aggregates.project(
                Projections.computed(STATUS_MESSAGE,
                        new BasicDBObject("$ifNull", Arrays.asList(
                                new BasicDBObject("$arrayElemAt",
                                        Arrays.asList("$testResults.message", -1)),
                                "$" + MULTI_EXEC_MESSAGE_FIELD)))));
        return pipeline;
    }

    @AllArgsConstructor
    private static final class CacheEntry {
        private final Map<String, Integer> counts;
        private final long expiryAt;
    }
}
