package com.akto.utils.search;

import com.akto.dao.agentic_sessions.AgentQueryTopicMappingDao;
import com.akto.dto.agentic_sessions.AgentQueryTopicMapping;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.utils.elasticsearch.AgentQueryRecord;

import com.microsoft.azure.kusto.data.Client;
import com.microsoft.azure.kusto.data.ClientFactory;
import com.microsoft.azure.kusto.data.KustoOperationResult;
import com.microsoft.azure.kusto.data.KustoResultColumn;
import com.microsoft.azure.kusto.data.KustoResultSetTable;
import com.microsoft.azure.kusto.data.auth.ConnectionStringBuilder;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Azure Data Explorer (ADX/Kusto)-backed implementation of {@link SearchClient}.
 *
 * ADX is append-only — there is no in-place per-row field update the way Elasticsearch's
 * {@code _bulk update} works, and topic classification runs asynchronously after ingestion, so
 * ADX rows never carry topic/subTopic columns at all. Under this backend, classified
 * topic/subTopic lives exclusively in {@link AgentQueryTopicMappingDao} (keyed by docId) and is
 * joined against ADX query results at read time — see {@link #attachTopicHierarchy} (bounded,
 * per-narrowed-result-set lookup) and {@link #appendTopicFilterClause} (bounded, time-windowed
 * candidate-list push-down for topic filters).
 *
 * Column names are assumed to match {@link AgentQueryRecord}'s base field-name constants
 * (accountId, serviceId, deviceId, userName, sessionIdentifier, queryPayload, responsePayload,
 * timestamp [datetime], inputTokens, outputTokens, traceId, spanId, isAtlasTraffic), plus a
 * {@code docId} column uniquely identifying each ingested row. This has not been exercised
 * against a live ADX cluster; verify these query shapes (KQL syntax, exact SDK accessor names)
 * against a real/test cluster before relying on this backend in production, per the plan's
 * verification section.
 */
public class AzureDataExplorerClient extends SearchClient {

    private static final LoggerMaker logger = new LoggerMaker(AzureDataExplorerClient.class, LogDb.DASHBOARD);

    private static final String ADX_CLUSTER_URI   = System.getenv("ADX_CLUSTER_URI");
    private static final String ADX_DATABASE      = System.getenv("ADX_DATABASE");
    private static final String ADX_TABLE         = System.getenv("ADX_TABLE");
    private static final String ADX_TENANT_ID     = System.getenv("ADX_TENANT_ID");
    private static final String ADX_CLIENT_ID     = System.getenv("ADX_CLIENT_ID");
    private static final String ADX_CLIENT_SECRET = System.getenv("ADX_CLIENT_SECRET");

    /** Column carrying each ingested row's unique id — the join key into the Mongo topic mapping. */
    private static final String COL_DOC_ID = "docId";

    // Row-shape keys (KEY_SPAN_COUNT, KEY_LATEST_TS, etc.) are inherited from SearchClient —
    // both backends must produce identical row shapes, so they're declared once there.

    // Same per-method caps as the Elasticsearch implementation, so both backends return
    // comparably-sized result sets under the same request.
    private static final int SESSIONS_SUMMARY_SIZE      = 500;
    private static final int SESSIONS_PAGINATED_CAP     = 10_000;
    private static final int MESSAGES_SIZE              = 500;
    private static final int TOP_N_USERS                = 10;
    private static final int USER_BREAKDOWN_SIZE         = 3;
    private static final int TOP_N_APPS_TRACES           = 10;
    private static final int TRACE_DETAIL_SIZE           = 500;
    private static final int TOPIC_HIERARCHY_BREADTH     = 5;
    // Defense-in-depth cap on the topic-filter push-down candidate list (see plan: Mongo join).
    private static final int TOPIC_FILTER_CANDIDATE_CAP  = 10_000;

    private static final AzureDataExplorerClient INSTANCE = new AzureDataExplorerClient();
    public static AzureDataExplorerClient instance() { return INSTANCE; }

    private final Client client;

    private AzureDataExplorerClient() {
        Client c = null;
        if (isConfigured()) {
            try {
                ConnectionStringBuilder csb = ConnectionStringBuilder.createWithAadApplicationCredentials(
                    ADX_CLUSTER_URI, ADX_CLIENT_ID, ADX_CLIENT_SECRET, ADX_TENANT_ID);
                c = ClientFactory.createClient(csb);
            } catch (Exception e) {
                logger.error("AzureDataExplorerClient: failed to create Kusto client: " + e.getMessage());
            }
        }
        this.client = c;
    }

    @Override
    public boolean isConfigured() {
        return notEmpty(ADX_CLUSTER_URI) && notEmpty(ADX_DATABASE) && notEmpty(ADX_TABLE)
            && notEmpty(ADX_TENANT_ID) && notEmpty(ADX_CLIENT_ID) && notEmpty(ADX_CLIENT_SECRET);
    }

    private static boolean notEmpty(String s) { return s != null && !s.isEmpty(); }

    // ── Per-session / per-message views ─────────────────────────────────────────

    @Override
    public SessionsResult fetchSessions(int accountId, long startMs, long endMs, String searchString,
                                         Map<String, List<String>> filters, Boolean atlasTrafficFilter,
                                         int sessionsLimit, String sessionsAfterKey) {
        List<Map<String, Object>> sessions = new ArrayList<>();
        String nextAfterKey = null;
        long totalSessions = 0;
        if (!isConfigured()) return new SessionsResult(sessions, null, 0);
        try {
            String where = buildWhereConditions(accountId, startMs, endMs, filters, atlasTrafficFilter)
                + buildSearchClause(searchString);
            String groupField = AgentQueryRecord.F_SESSION_IDENTIFIER;

            int pageSize = 0, pageOffset = 0, fetchSize;
            if (sessionsLimit > 0) {
                pageSize = Math.min(sessionsLimit, 100);
                if (sessionsAfterKey != null && !sessionsAfterKey.trim().isEmpty()) {
                    try { pageOffset = Integer.parseInt(sessionsAfterKey.trim()); }
                    catch (NumberFormatException ignored) {}
                }
                fetchSize = Math.min(pageOffset + pageSize, SESSIONS_PAGINATED_CAP);
            } else {
                fetchSize = SESSIONS_SUMMARY_SIZE;
            }

            String kql = ADX_TABLE + " | where " + where + " and isnotempty(" + groupField + ")"
                + " | summarize maxTs=max(" + AgentQueryRecord.F_TIMESTAMP + "), minTs=min(" + AgentQueryRecord.F_TIMESTAMP + "),"
                + " sumIn=sum(" + AgentQueryRecord.F_INPUT_TOKENS + "), sumOut=sum(" + AgentQueryRecord.F_OUTPUT_TOKENS + "),"
                + " msgCount=dcount(" + AgentQueryRecord.F_TRACE_ID + "), docCount=count() by " + groupField
                + " | extend maxTsMs=datetime_diff('millisecond', maxTs, datetime(1970-01-01)),"
                + " minTsMs=datetime_diff('millisecond', minTs, datetime(1970-01-01))"
                + " | top " + fetchSize + " by maxTsMs desc";

            LinkedHashMap<String, Map<String, Object>> rows = new LinkedHashMap<>();
            KustoResultSetTable rs = query(kql);
            if (rs != null) {
                while (rs.next()) {
                    String key = rs.getString(groupField);
                    if (key == null || key.isEmpty()) continue;
                    rows.put(key, baseGroupRow(groupField, key, rs.getLong("maxTsMs"), rs.getLong("minTsMs"),
                        rs.getLong("sumIn"), rs.getLong("sumOut"), rs.getLong("docCount"), rs.getLong("msgCount")));
                }
            }

            attachFilteredFirstHit(where, groupField, rows.keySet(), rows);
            attachTopicHierarchy(where, groupField, rows.keySet(), rows);

            List<Map<String, Object>> allSessions = new ArrayList<>(rows.values());
            if (sessionsLimit > 0) {
                int fromIdx = Math.min(pageOffset, allSessions.size());
                int toIdx   = Math.min(pageOffset + pageSize, allSessions.size());
                sessions = new ArrayList<>(allSessions.subList(fromIdx, toIdx));
                nextAfterKey = toIdx < allSessions.size() ? String.valueOf(toIdx) : null;
                totalSessions = countDistinct(where, groupField);
            } else {
                sessions = allSessions;
                totalSessions = sessions.size();
            }
        } catch (Exception e) {
            logger.error("fetchSessions error for accountId=" + accountId + ": " + e.getMessage());
            sessions = new ArrayList<>();
        }
        return new SessionsResult(sessions, nextAfterKey, totalSessions);
    }

    @Override
    public List<Map<String, Object>> fetchMessages(int accountId, long startMs, long endMs,
                                                     Map<String, List<String>> filters, Boolean atlasTrafficFilter) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (!isConfigured()) return messages;
        try {
            String where = buildWhereConditions(accountId, startMs, endMs, filters, atlasTrafficFilter);
            String groupField = AgentQueryRecord.F_TRACE_ID;

            // Unlike fetchSessions, ES's original firstHit for messages carries no filter
            // predicate, so arg_min composes directly into the primary summarize — no second
            // query needed here.
            String kql = ADX_TABLE + " | where " + where + " and isnotempty(" + groupField + ")"
                + " | summarize maxTs=max(" + AgentQueryRecord.F_TIMESTAMP + "), minTs=min(" + AgentQueryRecord.F_TIMESTAMP + "),"
                + " sumIn=sum(" + AgentQueryRecord.F_INPUT_TOKENS + "), sumOut=sum(" + AgentQueryRecord.F_OUTPUT_TOKENS + "), docCount=count(),"
                + " arg_min(" + AgentQueryRecord.F_TIMESTAMP + ", " + AgentQueryRecord.F_QUERY_PAYLOAD + ", "
                + AgentQueryRecord.F_RESPONSE_PAYLOAD + ", " + AgentQueryRecord.F_SERVICE_ID + ", "
                + AgentQueryRecord.F_USER_NAME + ", " + AgentQueryRecord.F_DEVICE_ID + ", "
                + AgentQueryRecord.F_SESSION_IDENTIFIER + ") by " + groupField
                + " | extend maxTsMs=datetime_diff('millisecond', maxTs, datetime(1970-01-01)),"
                + " minTsMs=datetime_diff('millisecond', minTs, datetime(1970-01-01))"
                + " | top " + MESSAGES_SIZE + " by maxTsMs desc";

            LinkedHashMap<String, Map<String, Object>> rows = new LinkedHashMap<>();
            KustoResultSetTable rs = query(kql);
            if (rs != null) {
                while (rs.next()) {
                    String key = rs.getString(groupField);
                    if (key == null || key.isEmpty()) continue;
                    Map<String, Object> row = baseGroupRow(groupField, key, rs.getLong("maxTsMs"), rs.getLong("minTsMs"),
                        rs.getLong("sumIn"), rs.getLong("sumOut"), rs.getLong("docCount"), 0L);
                    row.put(AgentQueryRecord.F_QUERY_PAYLOAD,      rs.getString(AgentQueryRecord.F_QUERY_PAYLOAD));
                    row.put(AgentQueryRecord.F_RESPONSE_PAYLOAD,   rs.getString(AgentQueryRecord.F_RESPONSE_PAYLOAD));
                    row.put(AgentQueryRecord.F_SERVICE_ID,         rs.getString(AgentQueryRecord.F_SERVICE_ID));
                    row.put(AgentQueryRecord.F_USER_NAME,          rs.getString(AgentQueryRecord.F_USER_NAME));
                    row.put(AgentQueryRecord.F_DEVICE_ID,          rs.getString(AgentQueryRecord.F_DEVICE_ID));
                    row.put(AgentQueryRecord.F_SESSION_IDENTIFIER, rs.getString(AgentQueryRecord.F_SESSION_IDENTIFIER));
                    rows.put(key, row);
                }
            }

            attachTopicHierarchy(where, groupField, rows.keySet(), rows);
            messages = new ArrayList<>(rows.values());
        } catch (Exception e) {
            logger.error("fetchMessages error for accountId=" + accountId + ": " + e.getMessage());
            messages = new ArrayList<>();
        }
        return messages;
    }

    private Map<String, Object> baseGroupRow(String groupField, String key, long maxTsMs, long minTsMs,
                                              long sumIn, long sumOut, long docCount, long msgCount) {
        Map<String, Object> row = new HashMap<>();
        row.put(groupField, key);
        row.put(KEY_SPAN_COUNT, docCount);
        row.put(KEY_LATEST_TS, maxTsMs);
        row.put(KEY_FIRST_TS, minTsMs);
        row.put(KEY_DURATION_MS, maxTsMs > minTsMs ? maxTsMs - minTsMs : 0);
        row.put(AgentQueryRecord.F_INPUT_TOKENS, sumIn);
        row.put(AgentQueryRecord.F_OUTPUT_TOKENS, sumOut);
        row.put(KEY_TOTAL_TOKENS, sumIn + sumOut);
        row.put(KEY_MSG_COUNT, msgCount);
        return row;
    }

    /**
     * Sessions-only: ES's firstHit filters to "LLM-shaped" rows before picking the earliest one,
     * a predicate that only applies to this one field, not the sibling sum/count metrics — can't
     * share one summarize, so this runs as a second, id-scoped query (bounded by rows.keySet()).
     */
    private void attachFilteredFirstHit(String where, String groupField, java.util.Collection<String> groupKeys,
                                         Map<String, Map<String, Object>> rows) {
        if (groupKeys.isEmpty()) return;
        String llmShapedPredicate =
            "(" + AgentQueryRecord.F_RESPONSE_PAYLOAD + " contains 'model' or "
            + "(" + AgentQueryRecord.F_QUERY_PAYLOAD + " contains '\"body\"' and "
            + AgentQueryRecord.F_QUERY_PAYLOAD + " !contains 'toolName' and "
            + AgentQueryRecord.F_QUERY_PAYLOAD + " !contains 'tools/call'))";
        String kql = ADX_TABLE + " | where " + where + " and " + groupField + " in (" + quotedList(new ArrayList<>(groupKeys)) + ")"
            + " and " + llmShapedPredicate
            + " | summarize arg_min(" + AgentQueryRecord.F_TIMESTAMP + ", " + AgentQueryRecord.F_QUERY_PAYLOAD + ", "
            + AgentQueryRecord.F_RESPONSE_PAYLOAD + ", " + AgentQueryRecord.F_SERVICE_ID + ", "
            + AgentQueryRecord.F_USER_NAME + ", " + AgentQueryRecord.F_DEVICE_ID + ") by " + groupField;

        KustoResultSetTable rs = query(kql);
        if (rs == null) return;
        while (rs.next()) {
            String key = rs.getString(groupField);
            Map<String, Object> row = rows.get(key);
            if (row == null) continue;
            row.put(AgentQueryRecord.F_QUERY_PAYLOAD,    rs.getString(AgentQueryRecord.F_QUERY_PAYLOAD));
            row.put(AgentQueryRecord.F_RESPONSE_PAYLOAD, rs.getString(AgentQueryRecord.F_RESPONSE_PAYLOAD));
            row.put(AgentQueryRecord.F_SERVICE_ID,       rs.getString(AgentQueryRecord.F_SERVICE_ID));
            row.put(AgentQueryRecord.F_USER_NAME,        rs.getString(AgentQueryRecord.F_USER_NAME));
            row.put(AgentQueryRecord.F_DEVICE_ID,        rs.getString(AgentQueryRecord.F_DEVICE_ID));
        }
    }

    /**
     * ADX rows carry no topic/subTopic columns at all (see class javadoc) — this fetches the
     * (groupField, docId) pairs for the already-narrowed set of group keys, batch-resolves their
     * topic/subTopic from the Mongo mapping, and rolls the counts up into the same
     * domain→[subDomains] shape ElasticSearchClient's nested terms-agg produces.
     */
    private void attachTopicHierarchy(String where, String groupField, java.util.Collection<String> groupKeys,
                                       Map<String, Map<String, Object>> rows) {
        if (groupKeys.isEmpty()) return;
        String kql = ADX_TABLE + " | where " + where + " and " + groupField + " in (" + quotedList(new ArrayList<>(groupKeys)) + ")"
            + " | project " + groupField + ", " + COL_DOC_ID;
        KustoResultSetTable rs = query(kql);
        if (rs == null) return;

        Map<String, List<String>> docIdsByGroup = new HashMap<>();
        List<String> allDocIds = new ArrayList<>();
        while (rs.next()) {
            String key = rs.getString(groupField);
            String docId = rs.getString(COL_DOC_ID);
            if (key == null || docId == null || docId.isEmpty()) continue;
            docIdsByGroup.computeIfAbsent(key, k -> new ArrayList<>()).add(docId);
            allDocIds.add(docId);
        }
        if (allDocIds.isEmpty()) return;

        Map<String, AgentQueryTopicMapping> topicByDocId = AgentQueryTopicMappingDao.instance.bulkGet(allDocIds);
        if (topicByDocId.isEmpty()) return;

        for (Map.Entry<String, List<String>> e : docIdsByGroup.entrySet()) {
            Map<String, Object> row = rows.get(e.getKey());
            if (row == null) continue;

            Map<String, Map<String, Integer>> counts = new LinkedHashMap<>();
            for (String docId : e.getValue()) {
                AgentQueryTopicMapping mapping = topicByDocId.get(docId);
                if (mapping == null || mapping.getTopic() == null || mapping.getTopic().isEmpty()) continue;
                String subTopic = mapping.getSubTopic() == null ? "" : mapping.getSubTopic();
                counts.computeIfAbsent(mapping.getTopic(), t -> new LinkedHashMap<>()).merge(subTopic, 1, Integer::sum);
            }
            if (counts.isEmpty()) continue;

            // Truncate to top-5 topics / top-5 subTopics by count, matching ES's terms-agg size:5.
            Map<String, Object> hierarchy = new LinkedHashMap<>();
            counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(sumCounts(b.getValue()), sumCounts(a.getValue())))
                .limit(TOPIC_HIERARCHY_BREADTH)
                .forEach(topicEntry -> {
                    List<String> subTopics = topicEntry.getValue().entrySet().stream()
                        .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                        .limit(TOPIC_HIERARCHY_BREADTH)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());
                    hierarchy.put(topicEntry.getKey(), subTopics);
                });
            row.put(KEY_TOPIC_HIERARCHY, hierarchy);
        }
    }

    private static int sumCounts(Map<String, Integer> counts) {
        int sum = 0;
        for (int v : counts.values()) sum += v;
        return sum;
    }

    private long countDistinct(String where, String field) {
        String kql = ADX_TABLE + " | where " + where + " and isnotempty(" + field + ") | summarize cnt=dcount(" + field + ")";
        KustoResultSetTable rs = query(kql);
        if (rs != null && rs.next()) return rs.getLong("cnt");
        return 0;
    }

    // ── Session-level aggregated stats ───────────────────────────────────────────

    @Override
    public SessionAggStats fetchSessionAggStats(int accountId, long startMs, long endMs,
                                                 Map<String, List<String>> filters, Boolean atlasTrafficFilter) {
        long aggTotalSessions = 0, aggInputTokens = 0, aggOutputTokens = 0;
        List<Map<String, Object>> aggTopUsers = new ArrayList<>();
        List<Map<String, Object>> aggUserBreakdown = new ArrayList<>();
        List<Long> aggSessionSpark = new ArrayList<>();
        List<Long> aggSessionSparkTs = new ArrayList<>();
        List<Long> aggSessionTokenSpark = new ArrayList<>();

        if (!isConfigured()) {
            return new SessionAggStats(0, 0, 0, aggTopUsers, aggUserBreakdown,
                aggSessionSpark, aggSessionSparkTs, aggSessionTokenSpark);
        }
        try {
            String where = buildWhereConditions(accountId, startMs, endMs, filters, atlasTrafficFilter)
                + " and isnotempty(" + AgentQueryRecord.F_SESSION_IDENTIFIER + ")";

            String totalsKql = ADX_TABLE + " | where " + where
                + " | summarize totalSessions=dcount(" + AgentQueryRecord.F_SESSION_IDENTIFIER + "),"
                + " sumIn=sum(" + AgentQueryRecord.F_INPUT_TOKENS + "), sumOut=sum(" + AgentQueryRecord.F_OUTPUT_TOKENS + ")";
            KustoResultSetTable totalsRs = query(totalsKql);
            if (totalsRs != null && totalsRs.next()) {
                aggTotalSessions = totalsRs.getLong("totalSessions");
                aggInputTokens   = totalsRs.getLong("sumIn");
                aggOutputTokens  = totalsRs.getLong("sumOut");
            }

            String usersKql = ADX_TABLE + " | where " + where + " and isnotempty(" + AgentQueryRecord.F_USER_NAME + ")"
                + " | summarize sumIn=sum(" + AgentQueryRecord.F_INPUT_TOKENS + "), sumOut=sum(" + AgentQueryRecord.F_OUTPUT_TOKENS + "), docCount=count() by "
                + AgentQueryRecord.F_USER_NAME + " | order by docCount desc";
            KustoResultSetTable usersRs = query(usersKql);
            List<Map<String, Object>> allUsersByCount = new ArrayList<>();
            if (usersRs != null) {
                while (usersRs.next()) {
                    String user = usersRs.getString(AgentQueryRecord.F_USER_NAME);
                    if (user == null || user.isEmpty()) continue;
                    long in = usersRs.getLong("sumIn");
                    long out = usersRs.getLong("sumOut");
                    Map<String, Object> row = new HashMap<>();
                    row.put(AgentQueryRecord.F_USER_NAME, user);
                    row.put(AgentQueryRecord.F_INPUT_TOKENS, in);
                    row.put(AgentQueryRecord.F_OUTPUT_TOKENS, out);
                    row.put(KEY_COUNT, usersRs.getLong("docCount"));
                    row.put(KEY_TOTAL_TOKENS, in + out);
                    allUsersByCount.add(row);
                }
            }
            List<Map<String, Object>> allUsersByTokens = new ArrayList<>(allUsersByCount);
            allUsersByTokens.sort((a, b) -> Long.compare(
                ((Number) b.get(KEY_TOTAL_TOKENS)).longValue(), ((Number) a.get(KEY_TOTAL_TOKENS)).longValue()));
            for (int i = 0; i < Math.min(TOP_N_USERS, allUsersByTokens.size()); i++) aggTopUsers.add(allUsersByTokens.get(i));
            for (int i = 0; i < Math.min(USER_BREAKDOWN_SIZE, allUsersByCount.size()); i++) {
                Map<String, Object> u = allUsersByCount.get(i);
                Map<String, Object> entry = new HashMap<>();
                entry.put(KEY_LABEL, u.get(AgentQueryRecord.F_USER_NAME));
                entry.put(KEY_COUNT, u.get(KEY_COUNT));
                aggUserBreakdown.add(entry);
            }

            long sparkEndMs = Math.min(endMs, System.currentTimeMillis());
            long[] range = queryDataRange(where, sparkEndMs);
            long intervalMs = sparklineIntervalMs(range[0], range[1]);
            Map<Long, long[]> buckets = queryBucketedMetrics(where, intervalMs,
                "dcount(" + AgentQueryRecord.F_SESSION_IDENTIFIER + ")");
            fillSpark(range[1], intervalMs, buckets, aggSessionSpark, aggSessionSparkTs, aggSessionTokenSpark);
        } catch (Exception e) {
            logger.error("fetchSessionAggStats error for accountId=" + accountId + ": " + e.getMessage());
        }
        return new SessionAggStats(aggTotalSessions, aggInputTokens, aggOutputTokens,
            aggTopUsers, aggUserBreakdown, aggSessionSpark, aggSessionSparkTs, aggSessionTokenSpark);
    }

    // ── Argus aggregated stats ────────────────────────────────────────────────────

    @Override
    public ArgusStats fetchArgusStats(int accountId, long startMs, long endMs, Boolean atlasTrafficFilter) {
        long aggTotalSpans = 0, aggInputTokens = 0, aggOutputTokens = 0;
        List<Map<String, Object>> aggTopApps = new ArrayList<>();
        List<Map<String, Object>> aggAppBreakdown = new ArrayList<>();
        List<Map<String, Object>> aggTopTraces = new ArrayList<>();
        List<Long> aggTraceSpark = new ArrayList<>();
        List<Long> aggTokenSpark = new ArrayList<>();
        List<Long> aggTraceSparkTs = new ArrayList<>();

        if (!isConfigured()) {
            return new ArgusStats(0, 0, 0, aggTopApps, aggAppBreakdown, aggTopTraces,
                aggTraceSpark, aggTokenSpark, aggTraceSparkTs);
        }
        try {
            String where = buildWhereConditions(accountId, startMs, endMs, null, atlasTrafficFilter);

            String totalsKql = ADX_TABLE + " | where " + where
                + " | summarize totalSpans=count(), sumIn=sum(" + AgentQueryRecord.F_INPUT_TOKENS + "), sumOut=sum(" + AgentQueryRecord.F_OUTPUT_TOKENS + ")";
            KustoResultSetTable totalsRs = query(totalsKql);
            if (totalsRs != null && totalsRs.next()) {
                aggTotalSpans   = totalsRs.getLong("totalSpans");
                aggInputTokens  = totalsRs.getLong("sumIn");
                aggOutputTokens = totalsRs.getLong("sumOut");
            }

            String appsKql = ADX_TABLE + " | where " + where + " and isnotempty(" + AgentQueryRecord.F_SERVICE_ID + ")"
                + " | summarize sumIn=sum(" + AgentQueryRecord.F_INPUT_TOKENS + "), sumOut=sum(" + AgentQueryRecord.F_OUTPUT_TOKENS + "), docCount=count() by "
                + AgentQueryRecord.F_SERVICE_ID + " | top " + TOP_N_APPS_TRACES + " by sumIn desc";
            KustoResultSetTable appsRs = query(appsKql);
            if (appsRs != null) {
                while (appsRs.next()) {
                    String app = appsRs.getString(AgentQueryRecord.F_SERVICE_ID);
                    if (app == null || app.isEmpty()) continue;
                    Map<String, Object> row = new HashMap<>();
                    row.put(AgentQueryRecord.F_SERVICE_ID, app);
                    row.put(AgentQueryRecord.F_INPUT_TOKENS, appsRs.getLong("sumIn"));
                    row.put(AgentQueryRecord.F_OUTPUT_TOKENS, appsRs.getLong("sumOut"));
                    row.put(KEY_COUNT, appsRs.getLong("docCount"));
                    aggTopApps.add(row);
                }
            }
            for (int i = 0; i < Math.min(3, aggTopApps.size()); i++) {
                Map<String, Object> app = aggTopApps.get(i);
                Map<String, Object> entry = new HashMap<>();
                entry.put(KEY_LABEL, app.get(AgentQueryRecord.F_SERVICE_ID));
                entry.put(KEY_COUNT, app.get(KEY_COUNT));
                aggAppBreakdown.add(entry);
            }

            String tracesKql = ADX_TABLE + " | where " + where + " and isnotempty(" + AgentQueryRecord.F_TRACE_ID + ")"
                + " | summarize sumIn=sum(" + AgentQueryRecord.F_INPUT_TOKENS + "), sumOut=sum(" + AgentQueryRecord.F_OUTPUT_TOKENS + "),"
                + " arg_min(" + AgentQueryRecord.F_TIMESTAMP + ", " + AgentQueryRecord.F_QUERY_PAYLOAD + ", "
                + AgentQueryRecord.F_RESPONSE_PAYLOAD + ", " + AgentQueryRecord.F_SERVICE_ID + ") by " + AgentQueryRecord.F_TRACE_ID
                + " | top " + TOP_N_APPS_TRACES + " by sumIn desc";
            KustoResultSetTable tracesRs = query(tracesKql);
            if (tracesRs != null) {
                while (tracesRs.next()) {
                    String tid = tracesRs.getString(AgentQueryRecord.F_TRACE_ID);
                    if (tid == null || tid.isEmpty()) continue;
                    Map<String, Object> row = new HashMap<>();
                    row.put(AgentQueryRecord.F_TRACE_ID, tid);
                    row.put(AgentQueryRecord.F_INPUT_TOKENS, tracesRs.getLong("sumIn"));
                    row.put(AgentQueryRecord.F_OUTPUT_TOKENS, tracesRs.getLong("sumOut"));
                    row.put(AgentQueryRecord.F_QUERY_PAYLOAD,    tracesRs.getString(AgentQueryRecord.F_QUERY_PAYLOAD));
                    row.put(AgentQueryRecord.F_RESPONSE_PAYLOAD, tracesRs.getString(AgentQueryRecord.F_RESPONSE_PAYLOAD));
                    row.put(AgentQueryRecord.F_SERVICE_ID,       tracesRs.getString(AgentQueryRecord.F_SERVICE_ID));
                    aggTopTraces.add(row);
                }
            }

            long argusSparkEndMs = Math.min(endMs, System.currentTimeMillis());
            long[] range = queryDataRange(where, argusSparkEndMs);
            long argusIntervalMs = sparklineIntervalMs(range[0], range[1]);
            Map<Long, long[]> buckets = queryBucketedMetrics(where, argusIntervalMs, "count()");
            fillSpark(range[1], argusIntervalMs, buckets, aggTraceSpark, aggTraceSparkTs, aggTokenSpark);
        } catch (Exception e) {
            logger.error("fetchArgusStats error for accountId=" + accountId + ": " + e.getMessage());
        }
        return new ArgusStats(aggTotalSpans, aggInputTokens, aggOutputTokens,
            aggTopApps, aggAppBreakdown, aggTopTraces, aggTraceSpark, aggTokenSpark, aggTraceSparkTs);
    }

    private long[] queryDataRange(String where, long fallbackMs) {
        String kql = ADX_TABLE + " | where " + where
            + " | summarize minTs=min(" + AgentQueryRecord.F_TIMESTAMP + "), maxTs=max(" + AgentQueryRecord.F_TIMESTAMP + ")"
            + " | extend minTsMs=datetime_diff('millisecond', minTs, datetime(1970-01-01)),"
            + " maxTsMs=datetime_diff('millisecond', maxTs, datetime(1970-01-01))";
        KustoResultSetTable rs = query(kql);
        if (rs != null && rs.next()) {
            long minMs = rs.getLong("minTsMs");
            long maxMs = Math.min(rs.getLong("maxTsMs"), fallbackMs);
            return new long[]{minMs, maxMs};
        }
        return new long[]{fallbackMs, fallbackMs};
    }

    /** Per-bucket metric ("dcount(sessionIdentifier)" or "count()") + token sums, one row per bin. */
    private Map<Long, long[]> queryBucketedMetrics(String where, long intervalMs, String metricExpr) {
        Map<Long, long[]> out = new HashMap<>();
        String kql = ADX_TABLE + " | where " + where
            + " | summarize metric=" + metricExpr + ", sumIn=sum(" + AgentQueryRecord.F_INPUT_TOKENS + "), sumOut=sum(" + AgentQueryRecord.F_OUTPUT_TOKENS + ")"
            + " by bucketMs=datetime_diff('millisecond', bin(" + AgentQueryRecord.F_TIMESTAMP + ", " + intervalMs + "ms), datetime(1970-01-01))";
        KustoResultSetTable rs = query(kql);
        if (rs != null) {
            while (rs.next()) {
                out.put(rs.getLong("bucketMs"), new long[]{rs.getLong("metric"), rs.getLong("sumIn"), rs.getLong("sumOut")});
            }
        }
        return out;
    }

    /**
     * Fills the last 12 sparkline buckets, generating expected bucket boundaries in Java (Kusto's
     * bin-based summarize only returns buckets that have at least one row — there's no
     * extended_bounds/min_doc_count:0 equivalent), defaulting missing buckets to 0. Bucket
     * boundaries are aligned to bin()'s own epoch-relative bucketing so lookups match.
     */
    private void fillSpark(long dataMaxMs, long intervalMs, Map<Long, long[]> buckets,
                            List<Long> countSpark, List<Long> tsSpark, List<Long> tokenSpark) {
        long histStart = dataMaxMs - 12L * intervalMs;
        long firstBinMs = (histStart / intervalMs) * intervalMs;
        List<Long> counts = new ArrayList<>(), timestamps = new ArrayList<>(), tokens = new ArrayList<>();
        for (long t = firstBinMs; t <= dataMaxMs; t += intervalMs) {
            long[] v = buckets.get(t);
            counts.add(v != null ? v[0] : 0L);
            timestamps.add(t / 1000L);
            tokens.add(v != null ? v[1] + v[2] : 0L);
        }
        int n = counts.size();
        int start = Math.max(0, n - 12);
        countSpark.addAll(counts.subList(start, n));
        tsSpark.addAll(timestamps.subList(start, n));
        tokenSpark.addAll(tokens.subList(start, n));
        if (countSpark.isEmpty()) { countSpark.add(0L); tsSpark.add(0L); tokenSpark.add(0L); }
    }

    // ── Spans for a single message/trace ──────────────────────────────────────────

    @Override
    public List<Map<String, Object>> fetchTraceDetail(int accountId, String traceId, Boolean atlasTrafficFilter) {
        List<Map<String, Object>> spans = new ArrayList<>();
        if (!isConfigured() || traceId == null || traceId.trim().isEmpty()) return spans;
        try {
            Map<String, List<String>> filters = new HashMap<>();
            filters.put(AgentQueryRecord.F_TRACE_ID_KW, Collections.singletonList(traceId.trim()));
            String where = buildWhereConditions(accountId, 0L, Long.MAX_VALUE, filters, atlasTrafficFilter);

            String kql = ADX_TABLE + " | where " + where
                + " | order by " + AgentQueryRecord.F_TIMESTAMP + " asc | take " + TRACE_DETAIL_SIZE;
            spans = queryRows(kql);
        } catch (Exception e) {
            logger.error("fetchTraceDetail error for accountId=" + accountId + ": " + e.getMessage());
            return new ArrayList<>();
        }
        return spans;
    }

    // ── Filter choices (distinct values for column filters) ───────────────────────

    @Override
    public Map<String, List<String>> fetchPromptFilters(int accountId, long startMs, long endMs) {
        Map<String, List<String>> filterChoices = new HashMap<>();
        if (!isConfigured()) return filterChoices;
        try {
            String where = buildWhereConditions(accountId, startMs, endMs, null, null);
            filterChoices.put(AgentQueryRecord.F_USER_NAME,  distinctValues(where, AgentQueryRecord.F_USER_NAME, 500));
            filterChoices.put(AgentQueryRecord.F_DEVICE_ID,  distinctValues(where, AgentQueryRecord.F_DEVICE_ID, 500));
            filterChoices.put(AgentQueryRecord.F_SERVICE_ID, distinctValues(where, AgentQueryRecord.F_SERVICE_ID, 500));
            // topic/subTopic live only in the Mongo mapping under this backend, not on ADX rows.
            filterChoices.put("topic",    AgentQueryTopicMappingDao.instance.distinctTopics(100));
            filterChoices.put("subTopic", AgentQueryTopicMappingDao.instance.distinctSubTopics(200));
        } catch (Exception e) {
            return new HashMap<>();
        }
        return filterChoices;
    }

    private List<String> distinctValues(String where, String field, int size) {
        List<String> out = new ArrayList<>();
        String kql = ADX_TABLE + " | where " + where + " and isnotempty(" + field + ")"
            + " | summarize cnt=count() by " + field + " | top " + size + " by cnt desc | project " + field;
        KustoResultSetTable rs = query(kql);
        if (rs != null) {
            while (rs.next()) {
                String v = rs.getString(field);
                if (v != null && !v.isEmpty()) out.add(v);
            }
        }
        return out;
    }

    // ── Paginated flat prompt search ───────────────────────────────────────────────

    @Override
    public SearchResult searchPrompts(int accountId, long startMs, long endMs, int skip, int limit,
                                       String sortKey, boolean sortAsc, String searchAfterJson,
                                       Map<String, List<String>> filters, Boolean atlasTrafficFilter, String searchString) {
        if (!isConfigured()) return new SearchResult(new ArrayList<>(), 0);
        try {
            String where = buildWhereConditions(accountId, startMs, endMs, filters, atlasTrafficFilter)
                + buildSearchClause(searchString);
            String sortField = toAdxSortField(sortKey);
            int cappedLimit = Math.min(limit, 100);

            String cursorClause = "";
            if (searchAfterJson != null && !searchAfterJson.trim().isEmpty()) {
                String[] parts = searchAfterJson.trim().split("\\|", 2);
                if (parts.length == 2) {
                    try {
                        long lastTs = Long.parseLong(parts[0]);
                        String op = sortAsc ? ">" : "<";
                        cursorClause = " and (" + AgentQueryRecord.F_TIMESTAMP + " " + op + " unixtime_milliseconds_todatetime(" + lastTs + ")"
                            + " or (" + AgentQueryRecord.F_TIMESTAMP + " == unixtime_milliseconds_todatetime(" + lastTs + ")"
                            + " and " + COL_DOC_ID + " " + op + " '" + esc(parts[1]) + "'))";
                    } catch (NumberFormatException ignored) {}
                }
            }
            // Deep from-style paging is only reachable when no cursor was supplied, mirroring
            // ElasticSearchClient.executeSearch's own skip<10000 cap on `from`.
            int skipAmount = cursorClause.isEmpty() && skip > 0 && skip < SESSIONS_PAGINATED_CAP ? skip : 0;

            long total = 0;
            KustoResultSetTable countRs = query(ADX_TABLE + " | where " + where + " | count");
            if (countRs != null && countRs.next()) total = countRs.getLong("Count");

            StringBuilder kql = new StringBuilder(ADX_TABLE + " | where " + where + cursorClause
                + " | order by " + sortField + (sortAsc ? " asc" : " desc"));
            if (skipAmount > 0) kql.append(" | serialize | extend rn_=row_number() | where rn_ > ").append(skipAmount);
            kql.append(" | take ").append(cappedLimit);

            List<Map<String, Object>> hits = queryRows(kql.toString());
            return new SearchResult(hits, total);
        } catch (Exception e) {
            logger.error("searchPrompts error for accountId=" + accountId + ": " + e.getMessage());
            return new SearchResult(new ArrayList<>(), 0);
        }
    }

    private static String toAdxSortField(String frontendKey) {
        if (frontendKey == null) return AgentQueryRecord.F_TIMESTAMP;
        switch (frontendKey) {
            case "timeStampMs":
            case "timestamp": return AgentQueryRecord.F_TIMESTAMP;
            case "userName":  return AgentQueryRecord.F_USER_NAME;
            case "serviceId": return AgentQueryRecord.F_SERVICE_ID;
            default:          return AgentQueryRecord.F_TIMESTAMP;
        }
    }

    // ── Topic write-back (used by crons after classification) ─────────────────────

    @Override
    public void bulkUpdateTopics(List<TopicUpdate> updates) {
        if (updates == null || updates.isEmpty()) return;
        List<AgentQueryTopicMapping> entries = new ArrayList<>();
        for (TopicUpdate u : updates) {
            if (u.docId == null || u.docId.isEmpty()) continue;
            entries.add(new AgentQueryTopicMapping(u.docId, u.topic, u.subTopic, u.timestampMs));
        }
        AgentQueryTopicMappingDao.instance.bulkUpsert(entries);
    }

    // ── Paged record fetch (used by crons) ──────────────────────────────────────────

    @Override
    public void scrollQueryData(int accountId, long startTsMs, long endTsMs, int pageSize,
                                int maxRecords, Consumer<AgentQueryRecord> handler) {
        if (!isConfigured()) return;
        try {
            long cursorTs = startTsMs;
            String cursorDocId = null;
            int delivered = 0;

            while (delivered < maxRecords) {
                String where = buildWhereConditions(accountId, cursorTs, endTsMs, null, null);
                String cursorClause = cursorDocId == null ? "" :
                    " and (" + AgentQueryRecord.F_TIMESTAMP + " > unixtime_milliseconds_todatetime(" + cursorTs + ")"
                    + " or (" + AgentQueryRecord.F_TIMESTAMP + " == unixtime_milliseconds_todatetime(" + cursorTs + ")"
                    + " and " + COL_DOC_ID + " > '" + esc(cursorDocId) + "'))";

                String kql = ADX_TABLE + " | where " + where + cursorClause
                    + " | order by " + AgentQueryRecord.F_TIMESTAMP + " asc, " + COL_DOC_ID + " asc | take " + pageSize;
                KustoResultSetTable rs = query(kql);
                if (rs == null) break;

                List<AgentQueryRecord> page = new ArrayList<>();
                while (rs.next()) page.add(parseRow(rs));
                if (page.isEmpty()) break;

                List<String> docIds = new ArrayList<>();
                for (AgentQueryRecord rec : page) if (rec.getDocId() != null) docIds.add(rec.getDocId());
                Map<String, AgentQueryTopicMapping> alreadyMapped = AgentQueryTopicMappingDao.instance.bulkGet(docIds);

                for (AgentQueryRecord rec : page) {
                    if (delivered >= maxRecords) break;
                    if (!alreadyMapped.containsKey(rec.getDocId())) {
                        handler.accept(rec);
                        delivered++;
                    }
                }

                AgentQueryRecord last = page.get(page.size() - 1);
                cursorTs = last.getTimeStampMs();
                cursorDocId = last.getDocId();
                if (page.size() < pageSize) break;
            }
        } catch (Exception e) {
            logger.error("scrollQueryData error for accountId=" + accountId + ": " + e.getMessage());
        }
    }

    private AgentQueryRecord parseRow(KustoResultSetTable rs) {
        return new AgentQueryRecord(
            rs.getString(COL_DOC_ID),
            (int) rs.getLong(AgentQueryRecord.F_ACCOUNT_ID),
            rs.getString(AgentQueryRecord.F_SERVICE_ID),
            rs.getString(AgentQueryRecord.F_DEVICE_ID),
            rs.getString(AgentQueryRecord.F_USER_NAME),
            rs.getString(AgentQueryRecord.F_SESSION_IDENTIFIER),
            rs.getString(AgentQueryRecord.F_QUERY_PAYLOAD),
            rs.getString(AgentQueryRecord.F_RESPONSE_PAYLOAD),
            datetimeColToMs(rs, AgentQueryRecord.F_TIMESTAMP),
            (int) rs.getLong(AgentQueryRecord.F_INPUT_TOKENS),
            (int) rs.getLong(AgentQueryRecord.F_OUTPUT_TOKENS),
            rs.getString(AgentQueryRecord.F_TRACE_ID),
            rs.getString("spanId"),
            rs.getBoolean(AgentQueryRecord.F_IS_ATLAS_TRAFFIC),
            "", ""
        );
    }

    private static long datetimeColToMs(KustoResultSetTable rs, String col) {
        java.time.LocalDateTime dt = rs.getKustoDateTime(col);
        if (dt == null) return 0L;
        return dt.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    // ── Query execution + row materialization ───────────────────────────────────────

    private KustoResultSetTable query(String kql) {
        if (client == null) return null;
        try {
            KustoOperationResult result = client.executeQuery(ADX_DATABASE, kql);
            return result.getPrimaryResults();
        } catch (Exception e) {
            logger.error("ADX query error: " + e.getMessage() + " | kql=" + kql);
            return null;
        }
    }

    /** Materializes every column of every row generically — used for flat (non-aggregated) fetches. */
    private List<Map<String, Object>> queryRows(String kql) {
        List<Map<String, Object>> out = new ArrayList<>();
        KustoResultSetTable rs = query(kql);
        if (rs == null) return out;
        List<String> columns = new ArrayList<>();
        for (KustoResultColumn c : rs.getColumns()) columns.add(c.getColumnName());
        while (rs.next()) {
            Map<String, Object> row = new HashMap<>();
            for (String col : columns) row.put(col, rs.getObject(col));
            row.put("id", String.valueOf(row.getOrDefault(COL_DOC_ID, "")));
            out.add(row);
        }
        return out;
    }

    // ── Query builders ───────────────────────────────────────────────────────────────

    /**
     * Unified WHERE-condition builder mirroring ElasticSearchClient.buildQuery's contract:
     * filters are always an "in" clause (single-value filters are just size-1 lists), and
     * isAtlasTraffic is a first-class tri-state parameter. Topic/subTopic filter entries are
     * intercepted and pushed down through the Mongo mapping (see appendTopicFilterClause) since
     * ADX rows carry no topic/subTopic columns.
     */
    private String buildWhereConditions(int accountId, long startMs, long endMs,
                                        Map<String, List<String>> filters, Boolean atlasTrafficFilter) {
        StringBuilder sb = new StringBuilder();
        sb.append(AgentQueryRecord.F_ACCOUNT_ID).append(" == ").append(accountId);
        if (startMs > 0) {
            sb.append(" and ").append(AgentQueryRecord.F_TIMESTAMP)
              .append(" >= unixtime_milliseconds_todatetime(").append(startMs).append(")");
        }
        if (endMs < Long.MAX_VALUE) {
            sb.append(" and ").append(AgentQueryRecord.F_TIMESTAMP)
              .append(" < unixtime_milliseconds_todatetime(").append(endMs).append(")");
        }

        if (filters != null) {
            List<String> topics = null, subTopics = null;
            for (Map.Entry<String, List<String>> e : filters.entrySet()) {
                List<String> vals = e.getValue();
                if (vals == null || vals.isEmpty()) continue;
                if (AgentQueryRecord.F_TOPIC_KW.equals(e.getKey())) { topics = vals; continue; }
                if (AgentQueryRecord.F_SUB_TOPIC_KW.equals(e.getKey())) { subTopics = vals; continue; }
                sb.append(" and ").append(adxField(e.getKey())).append(" in (").append(quotedList(vals)).append(")");
            }
            if (topics != null || subTopics != null) {
                appendTopicFilterClause(sb, topics, subTopics, startMs, endMs);
            }
        }

        if (atlasTrafficFilter != null) {
            if (atlasTrafficFilter) {
                sb.append(" and ").append(AgentQueryRecord.F_IS_ATLAS_TRAFFIC).append(" == true");
            } else {
                // "false" also covers docs that predate this field and never had it set.
                sb.append(" and (").append(AgentQueryRecord.F_IS_ATLAS_TRAFFIC).append(" == false or isnull(")
                  .append(AgentQueryRecord.F_IS_ATLAS_TRAFFIC).append("))");
            }
        }
        return sb.toString();
    }

    /**
     * Filtering-by-topic direction of the Mongo join (see class javadoc): resolves a bounded,
     * time-windowed docId candidate list first, then pushes it down as a plain "in" clause
     * alongside every other filter in the same ADX query — never an unbounded per-row join.
     */
    private void appendTopicFilterClause(StringBuilder sb, List<String> topics, List<String> subTopics,
                                         long startMs, long endMs) {
        AgentQueryTopicMappingDao.TopicLookupResult lookup = AgentQueryTopicMappingDao.instance
            .findDocIdsByTopics(topics, subTopics, startMs, endMs, TOPIC_FILTER_CANDIDATE_CAP);
        if (lookup.truncated) {
            logger.error("Topic filter candidate list exceeded cap (" + TOPIC_FILTER_CANDIDATE_CAP
                + ") for topics=" + topics + " subTopics=" + subTopics + " — results may be incomplete; narrow the time range.");
        }
        if (lookup.docIds.isEmpty()) {
            // No matching docs — force an empty result set rather than silently ignoring the filter.
            sb.append(" and ").append(COL_DOC_ID).append(" in ('')");
            return;
        }
        sb.append(" and ").append(COL_DOC_ID).append(" in (").append(quotedList(lookup.docIds)).append(")");
    }

    /** Faithful analog of Elasticsearch's match_phrase_prefix: exact adjacency on every word but
     *  the last, prefix match on the last word — via a constructed regex, not a lossy substring
     *  or single-token approximation. Single-word searches shortcut to startswith (cheaper). */
    private String buildSearchClause(String searchString) {
        if (searchString == null || searchString.isEmpty()) return "";
        String trimmed = searchString.trim();
        String[] words = trimmed.split("\\s+");

        String clause;
        if (words.length <= 1) {
            String w = esc(trimmed);
            clause = String.format(
                "(startswith(%s, '%s') or startswith(%s, '%s') or startswith(%s, '%s'))",
                AgentQueryRecord.F_QUERY_PAYLOAD, w, AgentQueryRecord.F_USER_NAME, w, AgentQueryRecord.F_SERVICE_ID, w);
        } else {
            StringBuilder rawPattern = new StringBuilder();
            for (int i = 0; i < words.length; i++) {
                if (i > 0) rawPattern.append("\\s+");
                rawPattern.append(escapeRegexLiteral(words[i]));
                if (i == words.length - 1) rawPattern.append("\\w*");
            }
            String p = esc(rawPattern.toString());
            clause = String.format(
                "(%s matches regex '%s' or %s matches regex '%s' or %s matches regex '%s')",
                AgentQueryRecord.F_QUERY_PAYLOAD, p, AgentQueryRecord.F_USER_NAME, p, AgentQueryRecord.F_SERVICE_ID, p);
        }
        return " and " + clause;
    }

    private static String escapeRegexLiteral(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ("\\^$.|?*+()[]{}".indexOf(c) >= 0) sb.append('\\');
            sb.append(c);
        }
        return sb.toString();
    }

    private static String quotedList(List<String> vals) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vals.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append('\'').append(esc(vals.get(i))).append('\'');
        }
        return sb.toString();
    }

    /** KQL string-literal escaping — every value interpolated into a query MUST go through this. */
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }

    /** Strips Elasticsearch's ".keyword" sub-field suffix to get the underlying ADX column name. */
    private static String adxField(String esField) {
        if (esField == null) return "";
        return esField.endsWith(".keyword") ? esField.substring(0, esField.length() - ".keyword".length()) : esField;
    }
}
