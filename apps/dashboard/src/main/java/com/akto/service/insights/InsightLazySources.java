package com.akto.service.insights;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.VulnerableTestingRunResultDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UnwindOptions;

import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The API_POSTURE / TESTING_POSTURE lazy reads InsightDataBundle memoizes — moved out of
 * InsightDataLoader (which builds and hands off a bundle, it shouldn't also own a large nested
 * type the bundle depends on) into its own file, sitting next to InsightDataBundle instead of
 * inside the loader that merely constructs one. See InsightDataBundle's memo()/withCtx() javadocs
 * for the calling convention: every method here runs lazily, at most once per bundle instance,
 * on whichever thread first asks for it.
 */
final class InsightLazySources {

    private static final LoggerMaker logger = new LoggerMaker(InsightLazySources.class, LogDb.DASHBOARD);

    // MCollection.findAll defaults to a 1,000,000-doc limit and ApiInfo is a fat document; a
    // provider hitting this cap MUST set metricsComplete=false and add a Gap rather than silently
    // treating the capped list as the whole account.
    static final int API_INFO_ROW_CAP = 50_000;
    private static final int AGING_ISSUES_ROW_CAP = 20_000;
    private static final int RECURRENCE_ROW_CAP = 20_000;
    private static final long AGING_ISSUE_WINDOW_SECONDS = 30L * 24 * 3600;

    private final InsightContext ctx;
    private volatile boolean apiInfoRowsTruncated = false;

    InsightLazySources(InsightContext ctx) {
        this.ctx = ctx;
    }

    boolean isApiInfoRowsTruncated() {
        return apiInfoRowsTruncated;
    }

    /** Bounded, RBAC-scoped api_info rows (ApiInfoDao extends AccountsContextDaoWithRbac, and
     *  the 5-arg findAll overload is the one that applies addRbacFilter). Each row's
     *  actualAuthType is (re)computed from allAuthTypesFound before returning, since providers
     *  need the resolved auth type, not the raw historical set-of-sets. */
    List<ApiInfo> apiInfoRows() {
        try {
            Bson filter = UsageMetricCalculator.excludeDemosAndDeactivated(ApiInfo.ID_API_COLLECTION_ID);
            Bson projection = Projections.include(ApiInfo.ID_API_COLLECTION_ID, ApiInfo.ID_URL, ApiInfo.ID_METHOD,
                    ApiInfo.LAST_TESTED, ApiInfo.RISK_SCORE, ApiInfo.IS_SENSITIVE, ApiInfo.ALL_AUTH_TYPES_FOUND,
                    ApiInfo.API_ACCESS_TYPES, ApiInfo.DISCOVERED_TIMESTAMP, ApiInfo.COLLECTION_IDS);
            List<ApiInfo> rows = ApiInfoDao.instance.findAll(filter, 0, API_INFO_ROW_CAP, null, projection);
            apiInfoRowsTruncated = rows.size() >= API_INFO_ROW_CAP;
            for (ApiInfo row : rows) {
                try {
                    row.calculateActualAuth();
                } catch (Exception ignoredPerRow) {
                    // allAuthTypesFound missing/null on this row — leave actualAuthType null;
                    // callers must not treat a null actualAuthType as "no auth type".
                }
            }
            return rows;
        } catch (Exception e) {
            logger.error("InsightLazySources: apiInfoRows failed: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /** Sensitive subType (PII class) -> number of distinct endpoints exposing it in a response.
     *  Delegates to SingleTypeInfoDao.responseSensitiveSubtypeApiCounts() — an API count, not a
     *  hit/location count, since SingleTypeInfo.count isn't a reliable hit counter (see that
     *  method's own javadoc). Shared with RecommendationCatalog.sensitiveDataTypesInResponse(),
     *  which was duplicating this exact match-clause-building logic before it moved into the
     *  DAO. */
    Map<String, Integer> sensitiveApiCountBySubType() {
        Bson extraFilter = Filters.nin(SingleTypeInfo._API_COLLECTION_ID,
                new ArrayList<>(UsageMetricCalculator.getDemosAndDeactivated()));
        return SingleTypeInfoDao.instance.responseSensitiveSubtypeApiCounts(extraFilter);
    }

    /** apiCollectionId -> {severity -> open-issue count}. Deliberately NOT
     *  TestingRunIssuesDao.getSeveritiesMapForCollections(): that method aggregates through
     *  getMCollection() directly with only a manual, best-effort UsersCollectionsList filter
     *  baked into the pipeline (no admin/dashboardContext handling, and the collectionIds
     *  lookup is wrapped in a try/catch that silently no-ops on failure) — not the same RBAC
     *  guarantee addCollectionsFilterForDashboard gives, which is what agingOpenIssues() right
     *  below already uses for the same TestingRunIssues collection. This replicates
     *  getSeveritiesMapForCollections()'s own expand-API-groups + group-by-severity shape
     *  (unwind on collectionIds, since one issue can belong to more than one API group) but
     *  swaps in that same RBAC filter instead. */
    Map<Integer, Map<String, Integer>> openIssueSeverityByCollection() {
        Map<Integer, Map<String, Integer>> resultMap = new HashMap<>();
        try {
            Bson rbacFilter = TestingRunIssuesDao.instance.addCollectionsFilterForDashboard(
                    Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, "OPEN"));

            List<Bson> pipeline = new ArrayList<>();
            pipeline.add(Aggregates.match(rbacFilter));
            UnwindOptions unwindOptions = new UnwindOptions();
            unwindOptions.preserveNullAndEmptyArrays(false);
            pipeline.add(Aggregates.unwind("$" + SingleTypeInfo._COLLECTION_IDS, unwindOptions));
            BasicDBObject groupedId = new BasicDBObject(SingleTypeInfo._API_COLLECTION_ID, "$" + SingleTypeInfo._COLLECTION_IDS)
                    .append(TestingRunIssues.KEY_SEVERITY, "$" + TestingRunIssues.KEY_SEVERITY);
            pipeline.add(Aggregates.group(groupedId, Accumulators.sum("count", 1)));

            try (MongoCursor<BasicDBObject> cursor =
                         TestingRunIssuesDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor()) {
                while (cursor.hasNext()) {
                    BasicDBObject doc = cursor.next();
                    BasicDBObject id = (BasicDBObject) doc.get(Constants.ID);
                    String severity = id.getString(TestingRunIssues.KEY_SEVERITY);
                    int apiCollectionId = id.getInt(SingleTypeInfo._API_COLLECTION_ID);
                    int count = doc.getInt("count");
                    resultMap.computeIfAbsent(apiCollectionId, k -> new HashMap<>()).merge(severity, count, Integer::sum);
                }
            }
        } catch (Exception e) {
            logger.error("InsightLazySources: openIssueSeverityByCollection failed: " + e.getMessage());
        }
        return resultMap;
    }

    /** OPEN issues older than 30 days by WALL CLOCK, not ctx.getEndTs() — ctx can carry a
     *  far-future "All time" sentinel (see WhatChangedThisWeekProvider's own comment on this
     *  exact trap), which would silently zero this out. Uses
     *  addCollectionsFilterForDashboard — the same dashboard-purpose-built RBAC + context
     *  filter IssuesAction.fetchAllIssues uses — rather than the inherited
     *  AccountsContextDaoWithRbac.findAll filter, which answers a subtly different question
     *  (OR's in a summary-id clause and skips the dashboardContext narrowing) and would
     *  disagree with what the Issues page itself shows for the same account. */
    List<TestingRunIssues> agingOpenIssues() {
        try {
            int cutoff = (int) (System.currentTimeMillis() / 1000 - AGING_ISSUE_WINDOW_SECONDS);
            Bson baseFilter = Filters.and(
                    Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, "OPEN"),
                    Filters.lt(TestingRunIssues.CREATION_TIME, cutoff));
            Bson rbacFilter = TestingRunIssuesDao.instance.addCollectionsFilterForDashboard(baseFilter);
            List<TestingRunIssues> rows = new ArrayList<>();
            try (MongoCursor<TestingRunIssues> cursor =
                         TestingRunIssuesDao.instance.getMCollection().find(rbacFilter).limit(AGING_ISSUES_ROW_CAP).cursor()) {
                while (cursor.hasNext()) rows.add(cursor.next());
            }
            return rows;
        } catch (Exception e) {
            logger.error("InsightLazySources: agingOpenIssues failed: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * {apiCollectionId, url, method, testSubType} -> how many distinct test runs saw that
     * exact finding — the repetition score. Reads BOTH physical result collections and merges
     * them: newer summaries write into vulnerable_testing_run_results; older vulnerable
     * results live in testing_run_result with vulnerable=true. There is no FK from an issue
     * to its results — the join is the same 3-part value match IssuesAction.
     * fetchTestingRunResult uses (apiInfoKey, testSubType, testRunResultSummaryId) — but here
     * we deliberately group ACROSS summaries rather than joining to one, since the whole point
     * is "how many summaries", not "the result for one summary".
     *
     * distinctRuns is built from $addToSet of testRunResultSummaryId, never $sum:1 — a single
     * summary can hold more than one result row for the same key (reruns), which would
     * otherwise overstate the recurrence count. This does NOT attempt fix-then-reopen
     * detection: TestingRunIssues carries no status-history, and lastSeen is set on every run
     * that tests an issue (vulnerable or not), not just when it's found vulnerable again — so
     * "was this ever fixed in between" is not derivable from what's stored today. See the
     * plan's Part 3c for the full reasoning; this method deliberately stops at the recurrence
     * count.
     */
    List<IssueRecurrenceRow> issueRecurrence() {
        try {
            Map<String, MergedRecurrence> merged = new HashMap<>();
            accumulateRecurrence(TestingRunResultDao.instance.getRawCollection(), true, merged);
            accumulateRecurrence(VulnerableTestingRunResultDao.instance.getRawCollection(), false, merged);

            List<IssueRecurrenceRow> rows = new ArrayList<>();
            for (MergedRecurrence m : merged.values()) {
                rows.add(new IssueRecurrenceRow(m.apiCollectionId, m.url, m.method, m.testSubType,
                        m.runIds.size(), m.firstSeen, m.lastSeen));
            }
            return rows;
        } catch (Exception e) {
            logger.error("InsightLazySources: issueRecurrence failed: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private void accumulateRecurrence(MongoCollection<Document> rawColl, boolean legacyCollection,
                                       Map<String, MergedRecurrence> merged) {
        List<Bson> matchClauses = new ArrayList<>();
        // Only the legacy testing_run_result collection mixes vulnerable and non-vulnerable
        // rows together; vulnerable_testing_run_results is vulnerable-only by construction.
        if (legacyCollection) matchClauses.add(Filters.eq(TestingRunResult.VULNERABLE, true));
        addRbacCollectionFilter(matchClauses, TestingRunResult.API_INFO_KEY + "." + ApiInfo.ApiInfoKey.API_COLLECTION_ID);

        List<Bson> pipeline = new ArrayList<>();
        if (!matchClauses.isEmpty()) pipeline.add(Aggregates.match(Filters.and(matchClauses)));
        BasicDBObject groupId = new BasicDBObject("apiCollectionId", "$" + TestingRunResult.API_INFO_KEY + "." + ApiInfo.ApiInfoKey.API_COLLECTION_ID)
                .append("url", "$" + TestingRunResult.API_INFO_KEY + "." + ApiInfo.ApiInfoKey.URL)
                .append("method", "$" + TestingRunResult.API_INFO_KEY + "." + ApiInfo.ApiInfoKey.METHOD)
                .append("testSubType", "$" + TestingRunResult.TEST_SUB_TYPE);
        pipeline.add(Aggregates.group(groupId,
                Accumulators.addToSet("runIds", "$" + TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID),
                Accumulators.min("firstSeen", "$" + TestingRunResult.END_TIMESTAMP),
                Accumulators.max("lastSeen", "$" + TestingRunResult.END_TIMESTAMP)));
        pipeline.add(Aggregates.limit(RECURRENCE_ROW_CAP));

        try (MongoCursor<BasicDBObject> cursor = rawColl.aggregate(pipeline, BasicDBObject.class).cursor()) {
            while (cursor.hasNext()) {
                BasicDBObject doc = cursor.next();
                BasicDBObject id = (BasicDBObject) doc.get("_id");
                int apiCollectionId = id.getInt("apiCollectionId");
                String url = id.getString("url");
                String method = id.getString("method");
                String testSubType = id.getString("testSubType");
                String key = apiCollectionId + "|" + url + "|" + method + "|" + testSubType;

                MergedRecurrence m = merged.computeIfAbsent(key,
                        k -> new MergedRecurrence(apiCollectionId, url, method, testSubType));
                List<?> runIdsRaw = (List<?>) doc.get("runIds");
                if (runIdsRaw != null) {
                    for (Object runId : runIdsRaw) m.runIds.add(String.valueOf(runId));
                }
                int firstSeen = doc.getInt("firstSeen", 0);
                int lastSeen = doc.getInt("lastSeen", 0);
                if (firstSeen != 0 && (m.firstSeen == 0 || firstSeen < m.firstSeen)) m.firstSeen = firstSeen;
                if (lastSeen > m.lastSeen) m.lastSeen = lastSeen;
            }
        }
    }

    /** The manual "scope this aggregation to the caller's accessible collections" idiom
     *  already used by SingleTypeInfoDao.execute() and
     *  TestingRunIssuesDao.getPipelineForSeverityCount() — aggregation pipelines bypass
     *  AccountsContextDaoWithRbac.modifyFilters entirely, so every aggregation in this class
     *  must apply RBAC by hand via this same helper. A null collectionIds list means RBAC is
     *  not in force for this caller (e.g. admin) — matching the convention of every other
     *  caller of getCollectionsIdForUser in this codebase. */
    private void addRbacCollectionFilter(List<Bson> matchClauses, String collectionIdField) {
        try {
            List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            if (collectionIds != null) {
                matchClauses.add(Filters.in(collectionIdField, collectionIds));
            }
        } catch (Exception ignored) {
        }
    }

    /** Accumulator for merging the same {apiCollectionId,url,method,testSubType} key across
     *  both vulnerable-result collections — runIds is a Set, not a running count, so a
     *  summary id that somehow appeared via both collections is still counted once. */
    private static final class MergedRecurrence {
        final int apiCollectionId;
        final String url;
        final String method;
        final String testSubType;
        final Set<String> runIds = new HashSet<>();
        int firstSeen = 0;
        int lastSeen = 0;

        MergedRecurrence(int apiCollectionId, String url, String method, String testSubType) {
            this.apiCollectionId = apiCollectionId;
            this.url = url;
            this.method = method;
            this.testSubType = testSubType;
        }
    }
}
