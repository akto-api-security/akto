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

public final class TestRunStatusHelper {

    public static final String REGEX_401 = "\"statusCode\"\\s*:\\s*401";
    public static final String REGEX_403 = "\"statusCode\"\\s*:\\s*403";

    private static final int SAMPLE_LIMIT = 3000;
    private static final String MULTI_EXEC_MESSAGE_FIELD = "testResults.nodeResultMap.x1.message";
    private static final String STATUS_MESSAGE = "statusMessage";

    private static final List<PatternDefinition> PATTERN_DEFINITIONS = Arrays.asList(
            new PatternDefinition("http401", REGEX_401),
            new PatternDefinition("http403", REGEX_403),
            new PatternDefinition("http5xx", TestResultsStatsAction.REGEX_5XX),
            new PatternDefinition("http429", TestResultsStatsAction.REGEX_429),
            new PatternDefinition("cloudflare", TestResultsStatsAction.REGEX_CLOUDFLARE));

    private TestRunStatusHelper() {
    }

    public static Map<String, Integer> computeStatusCounts(ObjectId testingRunResultSummaryId) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("domainUnreachable", countDomainUnreachable(testingRunResultSummaryId));
        counts.put("skipped", countSkipped(testingRunResultSummaryId));
        counts.put("needConfig", countNeedConfig(testingRunResultSummaryId));
        counts.putAll(countAllPatterns(testingRunResultSummaryId));
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
     * sampled, coalesced-message pipeline as {@link #countAllPatterns(ObjectId)} so the
     * stats endpoint and the bulk status endpoint stay consistent.
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
     * Counts all HTTP/error patterns in a single aggregation per summary.
     * Both single-exec ({@code testResults.message}) and multi-exec
     * ({@code testResults.nodeResultMap.x1.message}) messages are coalesced into one
     * {@code statusMessage} field, then matched in parallel via a single {@code $facet}.
     */
    private static Map<String, Integer> countAllPatterns(ObjectId testingRunResultSummaryId) {
        List<Bson> pipeline = sampledMessagePipeline(testingRunResultSummaryId);

        List<Facet> facets = new ArrayList<>();
        for (PatternDefinition definition : PATTERN_DEFINITIONS) {
            facets.add(new Facet(
                    definition.key,
                    Aggregates.match(Filters.regex(STATUS_MESSAGE, definition.regex, "i")),
                    Aggregates.count("count")));
        }
        pipeline.add(Aggregates.facet(facets.toArray(new Facet[0])));

        MongoCursor<Document> cursor = TestingRunResultDao.instance.getMCollection()
                .aggregate(pipeline, Document.class)
                .cursor();

        Map<String, Integer> counts = initializePatternCounts();
        try {
            if (cursor.hasNext()) {
                Document facetResult = cursor.next();
                for (PatternDefinition definition : PATTERN_DEFINITIONS) {
                    counts.put(definition.key, extractFacetCount(facetResult, definition.key));
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

    private static Map<String, Integer> initializePatternCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PatternDefinition definition : PATTERN_DEFINITIONS) {
            counts.put(definition.key, 0);
        }
        return counts;
    }

    private static int extractFacetCount(Document facetResult, String key) {
        @SuppressWarnings("unchecked")
        List<Document> bucket = (List<Document>) facetResult.get(key);
        if (bucket == null || bucket.isEmpty()) {
            return 0;
        }
        return bucket.get(0).getInteger("count", 0);
    }

    @AllArgsConstructor
    private static final class PatternDefinition {
        private final String key;
        private final String regex;
    }
}
