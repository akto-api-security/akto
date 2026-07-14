package com.akto.action.monitoring;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.utils.elasticsearch.AgentQueryRecord;
import com.akto.utils.search.SearchClient;
import com.akto.utils.search.SearchClientFactory;
import com.opensymphony.xwork2.Action;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin orchestrator over {@link SearchClient}: resolves request-level filters/context, calls the
 * matching semantic method on whichever backend {@link SearchClientFactory} selects, and assigns
 * the result onto the getters the frontend already expects. All aggregation-building/parsing
 * logic lives in the SearchClient implementations, not here.
 */
public class LLMObservabilityAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(LLMObservabilityAction.class, LogDb.DASHBOARD);

    @Setter private int          startTime;
    @Setter private int          endTime;
    @Setter private String       searchString;
    @Setter private int          skip        = 0;
    @Setter private int          limit       = 20;
    @Setter private String       sortKey     = AgentQueryRecord.F_TIMESTAMP;
    @Setter private int          sortOrder   = 1;
    @Setter private String       traceId;
    @Setter private String       searchAfterJson;
    @Setter private String       sessionsAfterKey;
    @Setter private int          sessionsLimit    = 20;

    // Single-value fields kept for backward-compat (session drill-down in SessionsView)
    @Setter private String       sessionId;
    @Setter private String       userName;
    @Setter private String       deviceId;
    @Setter private String       serviceId;

    // Multi-value filter lists (used by MessagesView / PromptsView ag-grid set filters)
    @Setter private List<String> userNames      = new ArrayList<>();
    @Setter private List<String> serviceIds     = new ArrayList<>();
    @Setter private List<String> sessionIds     = new ArrayList<>();
    @Setter private List<String> topicFilters    = new ArrayList<>();
    @Setter private List<String> subTopicFilters = new ArrayList<>();

    @Getter private String       nextAfterKey;
    @Getter private long         totalSessions    = 0;

    @Getter private List<Map<String, Object>>  sessions      = new ArrayList<>();
    @Getter private List<Map<String, Object>>  messages      = new ArrayList<>();
    @Getter private List<Map<String, Object>>  prompts       = new ArrayList<>();
    @Getter private List<Map<String, Object>>  spans         = new ArrayList<>();
    @Getter private Map<String, List<String>>  filterChoices = new HashMap<>();
    @Getter private long                       total         = 0;

    // Aggregated stats (fetchSessionAggStats)
    @Getter private long                       aggTotalSessions   = 0;
    @Getter private long                       aggInputTokens     = 0;
    @Getter private long                       aggOutputTokens    = 0;
    @Getter private List<Map<String, Object>>  aggTopUsers        = new ArrayList<>();
    @Getter private List<Map<String, Object>>  aggUserBreakdown   = new ArrayList<>();
    @Getter private List<Long>                 aggSessionSpark      = new ArrayList<>();
    @Getter private List<Long>                 aggSessionSparkTs    = new ArrayList<>();
    @Getter private List<Long>                 aggSessionTokenSpark = new ArrayList<>();

    // Argus aggregated stats (fetchArgusStats)
    @Getter private long                       aggTotalSpans      = 0;
    @Getter private List<Map<String, Object>>  aggTopApps         = new ArrayList<>();
    @Getter private List<Map<String, Object>>  aggAppBreakdown    = new ArrayList<>();
    @Getter private List<Map<String, Object>>  aggTopTraces       = new ArrayList<>();
    @Getter private List<Long>                 aggTraceSpark      = new ArrayList<>();
    @Getter private List<Long>                 aggTokenSpark      = new ArrayList<>();
    @Getter private List<Long>                 aggTraceSparkTs    = new ArrayList<>();

    private long startMs() { return (long) startTime * 1000L; }
    private long endMs()   { return (long) endTime   * 1000L; }

    // ── Per-session view ──────────────────────────────────────────────────────

    public String fetchSessions() {
        try {
            SearchClient client = SearchClientFactory.instance();
            if (!client.isConfigured()) return Action.SUCCESS.toUpperCase();
            int accountId = Context.accountId.get();

            SearchClient.SessionsResult result = client.fetchSessions(
                accountId, startMs(), endMs(), searchString,
                buildMultiFilters(true), resolveContextAtlasFilter(),
                sessionsLimit, sessionsAfterKey);

            sessions      = result.sessions;
            nextAfterKey  = result.nextAfterKey;
            totalSessions = result.totalSessions;
        } catch (Exception e) {
            logger.error("fetchSessions error: " + e.getMessage());
            sessions = new ArrayList<>();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchMessages() {
        try {
            SearchClient client = SearchClientFactory.instance();
            if (!client.isConfigured()) return Action.SUCCESS.toUpperCase();
            int accountId = Context.accountId.get();

            messages = client.fetchMessages(accountId, startMs(), endMs(),
                buildMultiFilters(true), resolveContextAtlasFilter());
        } catch (Exception e) {
            logger.error("fetchMessages error: " + e.getMessage());
            messages = new ArrayList<>();
        }
        return Action.SUCCESS.toUpperCase();
    }

    // ── Session-level aggregated stats (accurate cardinality + token sums) ──────

    public String fetchSessionAggStats() {
        try {
            SearchClient client = SearchClientFactory.instance();
            if (!client.isConfigured()) return Action.SUCCESS.toUpperCase();
            int accountId = Context.accountId.get();

            // Sessions view always reports Atlas (endpoint) traffic, independent of the
            // ambient request context.
            SearchClient.SessionAggStats stats = client.fetchSessionAggStats(
                accountId, startMs(), endMs(), buildMultiFilters(true), Boolean.TRUE);

            aggTotalSessions     = stats.totalSessions;
            aggInputTokens       = stats.inputTokens;
            aggOutputTokens      = stats.outputTokens;
            aggTopUsers          = stats.topUsers;
            aggUserBreakdown     = stats.userBreakdown;
            aggSessionSpark      = stats.sessionSpark;
            aggSessionSparkTs    = stats.sessionSparkTs;
            aggSessionTokenSpark = stats.sessionTokenSpark;
        } catch (Exception e) {
            logger.error("fetchSessionAggStats error: " + e.getMessage());
        }
        return Action.SUCCESS.toUpperCase();
    }

    // ── Argus aggregated stats (total spans + token sums + top apps/traces + sparklines) ──

    public String fetchArgusStats() {
        try {
            SearchClient client = SearchClientFactory.instance();
            if (!client.isConfigured()) return Action.SUCCESS.toUpperCase();
            int accountId = Context.accountId.get();

            // Argus view always reports non-Atlas (agent) traffic so the total here matches
            // what the Argus paginated table reports; "false" also covers docs that predate
            // this field and were never Atlas-tagged.
            SearchClient.ArgusStats stats = client.fetchArgusStats(accountId, startMs(), endMs(), Boolean.FALSE);

            aggTotalSpans   = stats.totalSpans;
            aggInputTokens  = stats.inputTokens;
            aggOutputTokens = stats.outputTokens;
            aggTopApps      = stats.topApps;
            aggAppBreakdown = stats.appBreakdown;
            aggTopTraces    = stats.topTraces;
            aggTraceSpark   = stats.traceSpark;
            aggTokenSpark   = stats.tokenSpark;
            aggTraceSparkTs = stats.traceSparkTs;
        } catch (Exception e) {
            logger.error("fetchArgusStats error: " + e.getMessage());
        }
        return Action.SUCCESS.toUpperCase();
    }

    // ── Spans for a single message/trace ──────────────────────────────────────

    public String fetchTraceDetail() {
        try {
            SearchClient client = SearchClientFactory.instance();
            if (!client.isConfigured() || traceId == null || traceId.trim().isEmpty())
                return Action.SUCCESS.toUpperCase();
            int accountId = Context.accountId.get();

            Boolean atlasFilter = CONTEXT_SOURCE.ENDPOINT.equals(Context.contextSource.get()) ? Boolean.TRUE : null;
            spans = client.fetchTraceDetail(accountId, traceId, atlasFilter);
        } catch (Exception e) {
            spans = new ArrayList<>();
        }
        return Action.SUCCESS.toUpperCase();
    }

    // ── Filter choices (distinct values for column filters) ───────────────────

    public String fetchPromptFilters() {
        try {
            SearchClient client = SearchClientFactory.instance();
            if (!client.isConfigured()) return Action.SUCCESS.toUpperCase();
            int accountId = Context.accountId.get();

            filterChoices = client.fetchPromptFilters(accountId, startMs(), endMs());
        } catch (Exception e) {
            filterChoices = new HashMap<>();
        }
        return Action.SUCCESS.toUpperCase();
    }

    // ── Paginated flat prompt search ───────────────────────────────────────────

    public String searchPrompts() {
        try {
            SearchClient client = SearchClientFactory.instance();
            if (!client.isConfigured()) return Action.SUCCESS.toUpperCase();
            int accountId = Context.accountId.get();

            SearchClient.SearchResult result = client.searchPrompts(
                accountId, startMs(), endMs(), skip, Math.min(limit, 100),
                sortKey, sortOrder == -1, searchAfterJson,
                buildMultiFilters(true), resolveContextAtlasFilter(), searchString);

            prompts = result.hits;
            total   = result.total;
        } catch (Exception e) {
            prompts = new ArrayList<>();
            total   = 0;
        }
        return Action.SUCCESS.toUpperCase();
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    /** Atlas (endpoint) traffic in ENDPOINT context, agent traffic otherwise — never null. */
    private Boolean resolveContextAtlasFilter() {
        return CONTEXT_SOURCE.ENDPOINT.equals(Context.contextSource.get());
    }

    /** Merges single-value fields and multi-value lists into one Map for the SearchClient contract. */
    private Map<String, List<String>> buildMultiFilters(boolean includeSession) {
        Map<String, List<String>> f = new HashMap<>();
        // Session filter: prefer multi-value list, fall back to single field
        if (includeSession) {
            List<String> sessionList = nonEmpty(sessionIds);
            if (sessionList.isEmpty() && sessionId != null && !sessionId.trim().isEmpty())
                sessionList = Collections.singletonList(sessionId.trim());
            if (!sessionList.isEmpty()) f.put(AgentQueryRecord.F_SESSION_IDENTIFIER_KW, sessionList);
        }
        // userName
        List<String> users = nonEmpty(userNames);
        if (users.isEmpty() && userName != null && !userName.trim().isEmpty())
            users = Collections.singletonList(userName.trim());
        if (!users.isEmpty()) f.put(AgentQueryRecord.F_USER_NAME_KW, users);
        // serviceId
        List<String> services = nonEmpty(serviceIds);
        if (services.isEmpty() && serviceId != null && !serviceId.trim().isEmpty())
            services = Collections.singletonList(serviceId.trim());
        if (!services.isEmpty()) f.put(AgentQueryRecord.F_SERVICE_ID_KW, services);
        // deviceId (single-value only; no ag-grid filter for this field)
        if (deviceId != null && !deviceId.trim().isEmpty())
            f.put(AgentQueryRecord.F_DEVICE_ID_KW, Collections.singletonList(deviceId.trim()));
        // traceId — used when scoping Messages tab to a specific trace
        if (traceId != null && !traceId.trim().isEmpty())
            f.put(AgentQueryRecord.F_TRACE_ID_KW, Collections.singletonList(traceId.trim()));
        // topic / subTopic filters
        List<String> topics = nonEmpty(topicFilters);
        if (!topics.isEmpty()) f.put(AgentQueryRecord.F_TOPIC_KW, topics);
        List<String> subTopics = nonEmpty(subTopicFilters);
        if (!subTopics.isEmpty()) f.put(AgentQueryRecord.F_SUB_TOPIC_KW, subTopics);
        return f;
    }

    private static List<String> nonEmpty(List<String> list) {
        List<String> out = new ArrayList<>();
        if (list == null) return out;
        for (String s : list) { if (s != null && !s.trim().isEmpty()) out.add(s.trim()); }
        return out;
    }
}
