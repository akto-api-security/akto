package com.akto.service.insights;

import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.action.threat_detection.HostSeverityCount;
import com.akto.action.threat_detection.SkillSeverityCount;
import com.akto.action.threat_detection.ThreatCategoryCount;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.DeviceTag;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.McpAuditInfo;
import com.akto.dto.agentic_sessions.UserAnalysisData;
import com.akto.dto.nhi_governance.NhiIdentity;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.function.Function;
import java.util.function.Predicate;

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
                              InsightsThreatBackendAccess threatAccess) {
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

    private static final int HOST_COUNTS_BUCKET_SECONDS = 300;
    // Rounded startTs -> account-wide events. Lives as long as this (60s-cached) bundle, so repeated drill/profile
    // opens share one threat-backend call per window instead of one per request or per agent.
    private final Map<Integer, List<DashboardMaliciousEvent>> hostCountsSince = new ConcurrentHashMap<>();
    private volatile HostCollectionResolver hostResolver;

    // collectionId -> {severity -> count} since startTs in the request's context source, attributed by host, then actor
    // (event collection ids aren't reliable); null when the threat backend is unavailable.
    public Map<Integer, Map<String, Integer>> maliciousSeverityCounts(List<Integer> collectionIds, int startTs) {
        return maliciousSeverityCounts(collectionIds, startTs, null);
    }

    // Same, counting only the events that pass eventFilter (null = all events).
    public Map<Integer, Map<String, Integer>> maliciousSeverityCounts(List<Integer> collectionIds, int startTs,
                                                                        Predicate<DashboardMaliciousEvent> eventFilter) {
        return countsByCollection(collectionIds, startTs, eventFilter,
                e -> e.getSeverity() == null ? "UNKNOWN" : e.getSeverity().toUpperCase(Locale.ROOT));
    }

    // collectionId -> {flagged data ("email", "<policy> (LLM rule)") -> count} since startTs; null when unavailable.
    public Map<Integer, Map<String, Integer>> sensitiveDataCounts(List<Integer> collectionIds, int startTs) {
        return countsByCollection(collectionIds, startTs, InsightUtil::isSensitiveDataEvent, InsightUtil::sensitiveDataLabel);
    }

    private Map<Integer, Map<String, Integer>> countsByCollection(List<Integer> collectionIds, int startTs,
                                                                 Predicate<DashboardMaliciousEvent> eventFilter,
                                                                 Function<DashboardMaliciousEvent, String> keyOf) {
        List<DashboardMaliciousEvent> hostCounts = cachedEventsSince(startTs);
        if (hostCounts == null) return null;
        List<DashboardMaliciousEvent> events = eventFilter == null ? hostCounts
                : hostCounts.stream().filter(eventFilter).collect(Collectors.toList());
        Map<Integer, Map<String, Integer>> byCollection = hostResolver().countByCollection(events, keyOf);
        byCollection.keySet().retainAll(new HashSet<>(collectionIds));
        return byCollection;
    }

    private List<DashboardMaliciousEvent> cachedEventsSince(int startTs) {
        if (!threatBackendAvailable || threatAccess == null) return null;
        int since = startTs - Math.floorMod(startTs, HOST_COUNTS_BUCKET_SECONDS);
        List<DashboardMaliciousEvent> events = hostCountsSince.get(since);
        if (events == null) {
            events = threatAccess.violationEventsMinimal(since, Context.now(), 100_000, null);
            hostCountsSince.putIfAbsent(since, events);
        }
        return events;
    }

    /** Events inside the page's date range (ctx start..end), from the same cache; null when the threat
     *  backend is unavailable. */
    public List<DashboardMaliciousEvent> windowEvents() {
        List<DashboardMaliciousEvent> events = cachedEventsSince(ctx.getStartTs());
        if (events == null) return null;
        long start = ctx.getStartTs();
        long end = ctx.getEndTs() > 0 ? ctx.getEndTs() : Long.MAX_VALUE;
        return events.stream().filter(e -> e != null && e.getTimestamp() >= start && e.getTimestamp() <= end)
                .collect(Collectors.toList());
    }

    /** Attributes events to this bundle's collections (host, then actor). */
    public HostCollectionResolver hostResolver() {
        if (hostResolver == null) hostResolver = new HostCollectionResolver(collections);
        return hostResolver;
    }

    /**
     * Newest-first events for these collections; null when the threat backend is unavailable.
     *
     * Argus attributes malicious events by host, not apiCollectionId (see HostCollectionResolver's
     * own javadoc) — an event's apiCollectionId is frequently unset or stale for agentic traffic,
     * which is why maliciousSeverityCounts/countByCollection never filters by it either. This used
     * to query by apiCollectionId directly, which silently returned nothing for agents whose events
     * only carry a host match, while the severity-count sibling (host-resolved) reported a real
     * total — the two disagreeing on the same agent is what surfaced this.
     *
     * Two queries unioned, not one with both fields set: the backend ANDs "hosts" and "actors"
     * when both are present in one request, so a single call would ask for events whose host AND
     * actor both match, not either — same reasoning HostCollectionResolver.resolveEvent's own
     * host-then-actor fallback documents.
     */
    public List<DashboardMaliciousEvent> listMaliciousEvents(int startTs, int endTs, int limit, List<Integer> collectionIds) {
        if (!threatBackendAvailable || threatAccess == null) return null;
        Set<Integer> wanted = new HashSet<>(collectionIds);
        List<String> hosts = new ArrayList<>();
        for (ApiCollection c : collections) {
            if (c == null || !wanted.contains(c.getId())) continue;
            String identity = InsightUtil.assetIdentity(c);
            if (StringUtils.isNotBlank(identity)) hosts.add(identity);
        }
        if (hosts.isEmpty()) return new ArrayList<>();

        Map<String, DashboardMaliciousEvent> byId = new LinkedHashMap<>();
        for (String field : Arrays.asList("hosts", "actors")) {
            List<DashboardMaliciousEvent> page = threatAccess.violationEvents(startTs, endTs, limit,
                    Collections.singletonMap(field, hosts), null);
            if (page == null) continue;
            for (DashboardMaliciousEvent event : page) {
                if (event != null && event.getId() != null) byId.put(event.getId(), event);
            }
        }
        List<DashboardMaliciousEvent> events = new ArrayList<>(byId.values());
        events.sort(Comparator.comparingLong(DashboardMaliciousEvent::getTimestamp).reversed());
        return events.size() > limit ? events.subList(0, limit) : events;
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
                    .searchMaliciousComponentInvocations(Context.accountId.get(), maliciousTermNames, startMs, endMs, MALICIOUS_INVOCATION_LIMIT_PER_TERM);
        } catch (Exception e) {
            return null;
        }
    }
}
