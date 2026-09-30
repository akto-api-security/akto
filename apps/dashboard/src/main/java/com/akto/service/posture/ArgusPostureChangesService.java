package com.akto.service.posture;

import com.akto.dao.AgenticPostureScoreHistoryDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.AgenticPostureScoreHistory;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.service.insights.InsightDataBundle;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

// "Changes since last week": rolling 7-day window, computed on read and cached for a day per account/user/environment.
public class ArgusPostureChangesService {

    private static final int WEEK_SECONDS = 7 * 86400;
    private static final List<String> SEVERITIES = Arrays.asList("CRITICAL", "HIGH", "MEDIUM", "LOW");
    private static final long CACHE_TTL_MS = 24L * 60 * 60 * 1000;
    private static final Map<String, CachedChanges> CACHE = new ConcurrentHashMap<>();

    private static final class CachedChanges {
        final BasicDBObject response;
        final long computedAtMs;
        CachedChanges(BasicDBObject response, long computedAtMs) {
            this.response = response;
            this.computedAtMs = computedAtMs;
        }
    }

    // bundleSupplier is only called on a cache miss, so a cached read skips the bundle load entirely.
    public BasicDBObject fetchChanges(int accountId, int userId, String environment,
                                      Supplier<InsightDataBundle> bundleSupplier) {
        // userId is part of the key because the underlying reads are scoped to that user's collections.
        String key = accountId + "|" + userId + "|" + ArgusPostureService.environmentKey(environment);
        CachedChanges cached = CACHE.get(key);
        if (cached != null && System.currentTimeMillis() - cached.computedAtMs < CACHE_TTL_MS) return cached.response;

        BasicDBObject response = compute(bundleSupplier.get(), environment, Context.now());
        CACHE.put(key, new CachedChanges(response, System.currentTimeMillis()));
        return response;
    }

    public static void invalidateAccount(int accountId) {
        String prefix = accountId + "|";
        CACHE.keySet().removeIf(k -> k.startsWith(prefix));
    }

    // Rows with nothing to report (zero count or unavailable source) are left out.
    private BasicDBObject compute(InsightDataBundle bundle, String environment, int now) {
        int weekStart = now - WEEK_SECONDS;
        List<ApiCollection> agents = ArgusPostureService.assetsIn(bundle.collections.stream()
                .filter(c -> c != null && !c.isDeactivated() && ArgusPostureService.isAgenticInScope(c))
                .collect(Collectors.toList()), environment);
        List<Integer> ids = agents.stream().map(ApiCollection::getId).collect(Collectors.toList());

        List<BasicDBObject> rows = new ArrayList<>();
        rows.add(row("newAgents", "plus", null, agents.stream().filter(a -> a.getStartTs() >= weekStart).count(),
                "new agents discovered", null));
        if (!ids.isEmpty()) {
            Bson inScope = Filters.in(ApiInfo.ID_API_COLLECTION_ID, ids);
            rows.add(row("newTools", "plus", null, ApiInfoDao.instance.count(
                    Filters.and(inScope, Filters.gte(ApiInfo.DISCOVERED_TIMESTAMP, weekStart))),
                    "new tools or endpoints discovered", null));
            rows.add(row("newPrivilegedTools", "alert", "warning", ApiInfoDao.instance.count(Filters.and(inScope,
                    Filters.gte(ApiInfo.TOOL_INFO_CALCULATED_AT, weekStart),
                    Filters.exists(ApiInfo.TOOL_INFO_CAPABILITY), Filters.ne(ApiInfo.TOOL_INFO_CAPABILITY, "SAFE"))),
                    "tools newly classified as privileged", null));
            rows.add(newFindingsRow(ids, weekStart));
            rows.add(row("resolvedFindings", "tick", "success", TestingRunIssuesDao.instance.count(Filters.and(
                    Filters.in(TestingRunIssues.ID_API_COLLECTION_ID, ids),
                    Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, "FIXED"),
                    Filters.gte(TestingRunIssues.LAST_UPDATED, weekStart))),
                    "red-team findings resolved", null));
            rows.add(maliciousEventsRow(bundle, ids, weekStart));
        }
        rows.add(policiesRow(bundle.policies, weekStart));
        rows.add(postureScoreRow(now));
        rows.removeIf(r -> r == null || Long.valueOf(0).equals(r.get("count")));

        return new BasicDBObject("rows", rows)
                .append("windowStart", weekStart)
                .append("windowEnd", now)
                .append("computedAt", now);
    }

    private static BasicDBObject newFindingsRow(List<Integer> ids, int weekStart) {
        BasicDBObject groupedId = new BasicDBObject(SingleTypeInfo._API_COLLECTION_ID, "$" + TestingRunIssues.ID_API_COLLECTION_ID)
                .append(TestingRunIssues.KEY_SEVERITY, "$" + TestingRunIssues.KEY_SEVERITY);
        Map<Integer, Map<String, Integer>> bySeverity = TestingRunIssuesDao.instance.getSeveritiesMapForCollections(
                Filters.and(Filters.in(TestingRunIssues.ID_API_COLLECTION_ID, ids),
                        Filters.gte(TestingRunIssues.CREATION_TIME, weekStart)), false, groupedId);
        Map<String, Long> totals = new HashMap<>();
        for (Map<String, Integer> perCollection : bySeverity.values()) {
            perCollection.forEach((severity, n) -> { if (n != null) totals.merge(severity, (long) n, Long::sum); });
        }
        long total = 0;
        List<String> parts = new ArrayList<>();
        for (String severity : SEVERITIES) {
            long n = totals.getOrDefault(severity, 0L);
            total += n;
            if (n > 0) parts.add(n + " " + severity.toLowerCase(Locale.ROOT));
        }
        boolean severe = totals.getOrDefault("CRITICAL", 0L) + totals.getOrDefault("HIGH", 0L) > 0;
        return row("newFindings", "alert", severe ? "critical" : "warning", total, "new open red-team findings",
                parts.isEmpty() ? null : String.join(" · ", parts));
    }

    // Same threat-backend aggregation the posture score uses; null when it is unavailable.
    private static BasicDBObject maliciousEventsRow(InsightDataBundle bundle, List<Integer> ids, int weekStart) {
        Map<Integer, Map<String, Integer>> sinceThisWeek = bundle.maliciousSeverityCounts(ids, weekStart);
        Map<Integer, Map<String, Integer>> sinceLastWeek = bundle.maliciousSeverityCounts(ids, weekStart - WEEK_SECONDS);
        if (sinceThisWeek == null || sinceLastWeek == null) return null;
        long thisWeek = total(sinceThisWeek);
        long diff = thisWeek - (total(sinceLastWeek) - thisWeek);
        String detail = diff == 0 ? "same as last week" : (diff > 0 ? "+" : "") + diff + " vs last week";
        return row("maliciousEvents", "alert", "critical", thisWeek, "guardrail and malicious events", detail);
    }

    private static BasicDBObject policiesRow(List<GuardrailPolicies> policies, int weekStart) {
        long created = 0, updated = 0;
        for (GuardrailPolicies p : policies) {
            if (p == null) continue;
            if (p.getCreatedTimestamp() >= weekStart) created++;
            else if (p.getUpdatedTimestamp() >= weekStart) updated++;
        }
        String detail = created + updated == 0 ? null : created + " new · " + updated + " updated";
        return row("policiesChanged", "plus", null, created + updated, "guardrail policies created or updated", detail);
    }

    // Account-wide (not environment-scoped), same history the posture score card reads.
    private static BasicDBObject postureScoreRow(int now) {
        List<AgenticPostureScoreHistory> latest = AgenticPostureScoreHistoryDao.instance.findAll(
                Filters.empty(), 0, 1, Sorts.descending(AgenticPostureScoreHistory.COMPUTED_AT));
        List<AgenticPostureScoreHistory> weekAgo = AgenticPostureScoreHistoryDao.instance.findAll(
                Filters.lte(AgenticPostureScoreHistory.COMPUTED_AT, now - WEEK_SECONDS),
                0, 1, Sorts.descending(AgenticPostureScoreHistory.COMPUTED_AT));
        if (latest.isEmpty() || weekAgo.isEmpty()) return null;
        long current = Math.round(latest.get(0).getValue());
        long previous = Math.round(weekAgo.get(0).getValue());
        long diff = current - previous;
        if (diff == 0) return null;
        // Higher score is worse, so a drop is the good direction.
        return row("postureScoreChange", diff < 0 ? "tick" : "alert", diff < 0 ? "success" : "critical",
                (diff > 0 ? "+" : "") + diff, "points posture score change", "now " + current + " / 100, was " + previous);
    }

    private static long total(Map<Integer, Map<String, Integer>> countsByCollection) {
        return countsByCollection.values().stream().flatMap(m -> m.values().stream()).mapToLong(Integer::longValue).sum();
    }

    private static BasicDBObject row(String id, String icon, String tone, Object count, String label, String detail) {
        BasicDBObject row = new BasicDBObject("id", id).append("icon", icon).append("count", count).append("label", label);
        if (tone != null) row.append("tone", tone);
        if (detail != null) row.append("detail", detail);
        return row;
    }
}
