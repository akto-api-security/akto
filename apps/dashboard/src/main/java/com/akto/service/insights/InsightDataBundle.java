package com.akto.service.insights;

import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.action.threat_detection.HostSeverityCount;
import com.akto.action.threat_detection.SkillSeverityCount;
import com.akto.action.threat_detection.ThreatCategoryCount;
import com.akto.dto.ApiCollection;
import com.akto.dto.DeviceTag;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.McpAuditInfo;
import com.akto.dto.agentic_sessions.UserAnalysisData;
import com.akto.dto.nhi_governance.NhiIdentity;

import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * One immutable snapshot of every Mongo/threat-backend read the 10 providers need,
 * loaded once by InsightDataLoader and shared across all of them. Every collection
 * field is empty (never null) on a read failure — the same "log and return empty"
 * convention AbstractThreatDetectionAction and ElasticSearchClient already use — except
 * threatBackendAvailable, which is the one place callers actually need to tell "confirmed
 * zero" apart from "couldn't check" (the threat backend is the one dependency here that's
 * genuinely expected to be flaky).
 */
public class InsightDataBundle {

    public final InsightContext ctx;
    public final List<ApiCollection> collections;
    public final Map<String, List<ApiCollection>> collectionsByServiceName; // key: lowercased service name
    public final Map<String, String> deviceIdToUsername;      // device label -> username
    public final Map<String, List<DeviceTag>> userTags;       // username -> tags
    public final List<McpAuditInfo> auditRows;
    public final List<GuardrailPolicies> policies;
    public final Set<String> allowlistNamesLower;
    public final Map<Integer, List<String>> sensitiveByCollection;
    public final List<UserAnalysisData> userAnalysis;
    public final List<NhiIdentity> nhiIdentities;
    public final List<HostSeverityCount> hostSeverityCounts;
    public final List<ThreatCategoryCount> subCategoryCounts;
    public final List<SkillSeverityCount> skillSeverityCounts;
    public final boolean threatBackendAvailable;

    // Guardrail-insights-specific — a narrower asset projection than `collections` above (id/
    // hostName/startTs only, non-deactivated + hostName-exists), because policy-coverage checking
    // needs no traffic/risk/tag data and every extra field would be dead weight on every request.
    public final List<ApiCollection> activeCollections;
    public final Map<Integer, Integer> collectionLastTrafficSeen;

    private final InsightsThreatBackendAccess threatAccess;

    // API_POSTURE / TESTING_POSTURE lazy reads — see the memo()/withCtx() javadocs below.
    private final InsightLazySources lazy;
    private final Map<String, Object> lazyCache = new ConcurrentHashMap<>();

    public InsightDataBundle(InsightContext ctx,
                              List<ApiCollection> collections,
                              Map<String, List<ApiCollection>> collectionsByServiceName,
                              Map<String, String> deviceIdToUsername,
                              Map<String, List<DeviceTag>> userTags,
                              List<McpAuditInfo> auditRows,
                              List<GuardrailPolicies> policies,
                              Set<String> allowlistNamesLower,
                              Map<Integer, List<String>> sensitiveByCollection,
                              List<UserAnalysisData> userAnalysis,
                              List<NhiIdentity> nhiIdentities,
                              List<HostSeverityCount> hostSeverityCounts,
                              List<ThreatCategoryCount> subCategoryCounts,
                              List<SkillSeverityCount> skillSeverityCounts,
                              boolean threatBackendAvailable,
                              List<ApiCollection> activeCollections,
                              Map<Integer, Integer> collectionLastTrafficSeen,
                              InsightsThreatBackendAccess threatAccess,
                              InsightLazySources lazy) {
        this.ctx = ctx;
        this.collections = collections;
        this.collectionsByServiceName = collectionsByServiceName;
        this.deviceIdToUsername = deviceIdToUsername;
        this.userTags = userTags;
        this.auditRows = auditRows;
        this.policies = policies;
        this.allowlistNamesLower = allowlistNamesLower;
        this.sensitiveByCollection = sensitiveByCollection;
        this.userAnalysis = userAnalysis;
        this.nhiIdentities = nhiIdentities;
        this.hostSeverityCounts = hostSeverityCounts;
        this.subCategoryCounts = subCategoryCounts;
        this.skillSeverityCounts = skillSeverityCounts;
        this.threatBackendAvailable = threatBackendAvailable;
        this.activeCollections = activeCollections;
        this.collectionLastTrafficSeen = collectionLastTrafficSeen;
        this.threatAccess = threatAccess;
        this.lazy = lazy;
    }

    public List<ApiCollection> collectionsForServiceName(String serviceName) {
        if (serviceName == null) return java.util.Collections.emptyList();
        return collectionsByServiceName.getOrDefault(serviceName.toLowerCase(), java.util.Collections.emptyList());
    }

    public String usernameForDevice(String deviceId) {
        return deviceId == null ? null : deviceIdToUsername.get(deviceId);
    }

    /** First "team" device-tag value for a user, or null. "team" is a convention, not a typed field. */
    public String teamForUser(String userName) {
        List<DeviceTag> tags = userName == null ? null : userTags.get(userName);
        if (tags == null) return null;
        for (DeviceTag t : tags) {
            if ("team".equalsIgnoreCase(t.getKey())) return t.getValue();
        }
        return null;
    }

    /**
     * DETAIL scope only — heavy raw-event fetch, never called from the list path.
     * Returns null under LIST scope or on failure (the caller must not treat null as
     * zero); returns a possibly-empty list on a successful call.
     */
    public List<DashboardMaliciousEvent> fetchPiiEvents(InsightProvider.Scope scope, List<String> subCategories, int limit) {
        if (scope != InsightProvider.Scope.DETAIL) return null;
        try {
            return threatAccess.piiEvents(ctx.getStartTs(), ctx.getEndTs(), subCategories, Math.min(limit, 2000));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * DETAIL scope only, general-purpose — for a provider whose event filters don't fit
     * fetchPiiEvents' hardcoded subCategory shape (an unfiltered sweep, a latestAttack/policy-name
     * filter, or the skill-eval-mode header). Same null-under-LIST-or-failure / possibly-empty-
     * list-on-success contract as fetchPiiEvents.
     */
    public List<DashboardMaliciousEvent> fetchViolationEvents(InsightProvider.Scope scope, int limit,
                                                               Map<String, Object> filters, String skillEvalMode) {
        if (scope != InsightProvider.Scope.DETAIL) return null;
        try {
            return threatAccess.violationEvents(ctx.getStartTs(), ctx.getEndTs(), Math.min(limit, 3000), filters, skillEvalMode);
        } catch (Exception e) {
            return null;
        }
    }

    private static final long MALICIOUS_INVOCATION_WINDOW_MS = 15L * 24 * 3600 * 1000;
    private static final int MALICIOUS_INVOCATION_LIMIT_PER_TERM = 3;

    /**
     * DETAIL scope only — proves whether a component already known to be malicious for this
     * account was actually invoked, by text-searching queryPayload/responsePayload for its
     * name/skill-name over the last 15 days. Fixed recency window, independent of ctx's own
     * date range: "was this really called recently" is a standing risk question, not something
     * that should shrink to whatever range the dashboard happens to be filtered to. Row shape:
     * {term, traceId, timestamp}. Null under LIST scope, blank input, or failure.
     */
    public List<Map<String, Object>> fetchMaliciousComponentInvocations(InsightProvider.Scope scope, List<String> maliciousTermNames) {
        if (scope != InsightProvider.Scope.DETAIL || maliciousTermNames == null || maliciousTermNames.isEmpty()) return null;
        try {
            long endMs = ctx.getEndTs() * 1000L;
            long startMs = endMs - MALICIOUS_INVOCATION_WINDOW_MS;
            return com.akto.utils.search.SearchClientFactory.instance()
                    .searchMaliciousComponentInvocations(1779231193, maliciousTermNames, startMs, endMs, MALICIOUS_INVOCATION_LIMIT_PER_TERM);
        } catch (Exception e) {
            return null;
        }
    }

    private <T> T withCtx(Supplier<T> s) {
        Integer prevAcc = Context.accountId.get();
        Integer prevUser = Context.userId.get();
        CONTEXT_SOURCE prevSrc = Context.contextSource.get();
        try {
            Context.accountId.set(ctx.getAccountId());
            Context.userId.set(ctx.getUserId());
            Context.contextSource.set(ctx.getContextSource());
            return s.get();
        } finally {
            if (prevAcc == null) Context.accountId.remove(); else Context.accountId.set(prevAcc);
            if (prevUser == null) Context.userId.remove(); else Context.userId.set(prevUser);
            if (prevSrc == null) Context.contextSource.remove(); else Context.contextSource.set(prevSrc);
        }
    }

    /**
     * Memoizes one lazy read per bundle instance, so N providers sharing one read (e.g. three
     * API_POSTURE providers all wanting apiInfoRows()) still pay for it once. ConcurrentHashMap
     * .computeIfAbsent is NOT reentrant — a supplier here must never call another memo()'d
     * accessor, or a hash collision can deadlock. Every supplier below is therefore built only
     * from what InsightDataLoader already loaded eagerly (collections -> collection ids), never
     * from another lazy field.
     */
    @SuppressWarnings("unchecked")
    private <T> T memo(String key, Supplier<T> s) {
        return (T) lazyCache.computeIfAbsent(key, k -> withCtx(s));
    }

    /**
     * Bounded, RBAC-scoped api_info rows for API_POSTURE providers. Capped at
     * InsightLazySources.API_INFO_ROW_CAP — a provider that reads this MUST check
     * isApiInfoRowsTruncated() and, if true, set metricsComplete=false and add a Gap rather than
     * silently treating the capped list as the whole account (a truncated read must never become
     * a denominator).
     */
    public List<ApiInfo> apiInfoRows() {
        return memo("apiInfoRows", lazy::apiInfoRows);
    }

    public boolean isApiInfoRowsTruncated() {
        apiInfoRows(); // ensure the memoized read has actually run — the flag is a side effect of it
        return lazy.isApiInfoRowsTruncated();
    }

    /** subType (PII class) -> number of distinct endpoints exposing it in a response — an API
     *  count, not a hit/location count. See SingleTypeInfoDao.responseSensitiveSubtypeApiCounts()
     *  for why SingleTypeInfo.count isn't summed here (it isn't a reliable hit counter). */
    public Map<String, Integer> sensitiveApiCountBySubType() {
        return memo("sensitiveApiCountBySubType", lazy::sensitiveApiCountBySubType);
    }

    /** apiCollectionId -> {severity -> open-issue count}, RBAC-scoped via
     *  TestingRunIssuesDao.addCollectionsFilterForDashboard — see
     *  InsightLazySources.openIssueSeverityByCollection() for why that filter, not
     *  TestingRunIssuesDao.getSeveritiesMapForCollections's own weaker best-effort one, is used
     *  here. */
    public Map<Integer, Map<String, Integer>> openIssueSeverityByCollection() {
        return memo("openIssueSeverityByCollection", lazy::openIssueSeverityByCollection);
    }

    /** OPEN issues with creationTime older than 30 days (wall clock, not ctx — ctx's end
     *  timestamp can be a far-future "All time" sentinel). */
    public List<TestingRunIssues> agingOpenIssues() {
        return memo("agingOpenIssues", lazy::agingOpenIssues);
    }

    /** {apiInfoKey, testSubType} -> how many distinct test runs saw that finding — the
     *  repetition score. See InsightLazySources.issueRecurrence() for the merge across
     *  vulnerable_testing_run_results and the legacy testing_run_result collection, and why
     *  distinctRuns is an $addToSet of summaryId rather than a raw document count. */
    public List<IssueRecurrenceRow> issueRecurrence() {
        return memo("issueRecurrence", lazy::issueRecurrence);
    }
}
