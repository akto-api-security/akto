package com.akto.utils.search;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.akto.dto.agentic_sessions.UserAnalysisData;
import com.akto.utils.elasticsearch.AgentQueryRecord;

/**
 * The unified Argus list: conversations and lone invocations merged into one page.
 *
 * Both halves come back time-descending and are merged on that key alone, so the things
 * worth pinning are the interleave itself, the slice that serves a requested page, and the
 * fields a lone invocation has to be given before one set of columns can render both kinds.
 *
 * The two source queries are stubbed - what they return is covered by the backend's own
 * tests, and stubbing them is what makes the merge testable without an engine behind it.
 */
public class SearchClientUnifiedMergeTest {

    /** A session bucket as fetchSessions emits it: keyed on its latest activity. */
    private static Map<String, Object> session(String id, long latestMs, long traces) {
        Map<String, Object> row = new HashMap<>();
        row.put(AgentQueryRecord.F_SESSION_IDENTIFIER, id);
        row.put("latestTimestamp", latestMs);
        row.put("messageCount", traces);
        return row;
    }

    /** A flat record as searchPrompts emits it: its own timestamp, no aggregates. */
    private static Map<String, Object> trace(String spanId, long tsMs, boolean guardrailHit) {
        Map<String, Object> row = new HashMap<>();
        row.put("spanId", spanId);
        row.put(AgentQueryRecord.F_TIMESTAMP, tsMs);
        row.put(AgentQueryRecord.F_GUARDRAIL_VIOLATED, guardrailHit);
        return row;
    }

    private static SearchClient clientWith(List<Map<String, Object>> sessions, long totalSessions,
                                           List<Map<String, Object>> traces, long totalTraces) {
        return new StubSearchClient(sessions, totalSessions, traces, totalTraces);
    }

    private static List<String> idsOf(List<Map<String, Object>> rows) {
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Object sid = r.get(AgentQueryRecord.F_SESSION_IDENTIFIER);
            ids.add(sid != null ? sid.toString() : String.valueOf(r.get("spanId")));
        }
        return ids;
    }

    private SearchClient.UnifiedResult page(SearchClient client, int skip, int limit) {
        return client.fetchSessionsAndTraces(1, 0L, 9_000_000L, null,
                new HashMap<>(), Boolean.FALSE, skip, limit, true);
    }

    /** The point of the view: the two kinds interleave by time rather than sitting in blocks. */
    @Test
    public void rowsOfBothKindsInterleaveByTime() {
        SearchClient client = clientWith(
            Arrays.asList(session("s-late", 500, 3), session("s-early", 300, 2)), 2,
            Arrays.asList(trace("t-mid", 400, false), trace("t-oldest", 200, false)), 2);

        List<Map<String, Object>> rows = page(client, 0, 10).rows;
        assertEquals(Arrays.asList("s-late", "t-mid", "s-early", "t-oldest"), idsOf(rows));
    }

    /** Each row says which half it came from, so the UI picks a click target without guessing. */
    @Test
    public void eachRowCarriesItsKind() {
        SearchClient client = clientWith(
            Arrays.asList(session("s1", 500, 3)), 1,
            Arrays.asList(trace("t1", 400, false)), 1);

        List<Map<String, Object>> rows = page(client, 0, 10).rows;
        assertEquals(SearchClient.ROW_KIND_SESSION, rows.get(0).get("_rowKind"));
        assertEquals(SearchClient.ROW_KIND_TRACE,   rows.get(1).get("_rowKind"));
    }

    /** One invocation is one trace - a blank there would read as "no traces at all". */
    @Test
    public void loneInvocationCountsAsOneTrace() {
        SearchClient client = clientWith(
            new ArrayList<>(), 0, Arrays.asList(trace("t1", 400, false)), 1);

        assertEquals(1L, page(client, 0, 10).rows.get(0).get("messageCount"));
    }

    /** A record's own verdict is the only guardrail answer it has; absent must not read as a hit. */
    @Test
    public void loneInvocationKeepsItsOwnGuardrailVerdict() {
        SearchClient client = clientWith(new ArrayList<>(), 0,
            Arrays.asList(trace("hit", 500, true), trace("clean", 400, false)), 2);

        List<Map<String, Object>> rows = page(client, 0, 10).rows;
        assertEquals(Boolean.TRUE,  rows.get(0).get("hasActiveGuardrail"));
        assertEquals(Boolean.FALSE, rows.get(1).get("hasActiveGuardrail"));
    }

    /** Paging slices the merged list, not either half - otherwise page 2 would repeat rows. */
    @Test
    public void laterPagesSliceTheMergedOrder() {
        SearchClient client = clientWith(
            Arrays.asList(session("s-late", 500, 3), session("s-early", 300, 2)), 2,
            Arrays.asList(trace("t-mid", 400, false), trace("t-oldest", 200, false)), 2);

        assertEquals(Arrays.asList("s-late", "t-mid"),    idsOf(page(client, 0, 2).rows));
        assertEquals(Arrays.asList("s-early", "t-oldest"), idsOf(page(client, 2, 2).rows));
    }

    /** Asking past the end returns nothing rather than throwing on the sublist. */
    @Test
    public void skippingBeyondTheEndIsEmpty() {
        SearchClient client = clientWith(
            Arrays.asList(session("s1", 500, 3)), 1, new ArrayList<>(), 0);

        assertTrue(page(client, 50, 20).rows.isEmpty());
    }

    /** The count behind the table is both halves, since the table shows both. */
    @Test
    public void totalCombinesBothHalves() {
        SearchClient client = clientWith(
            Arrays.asList(session("s1", 500, 3)), 7, Arrays.asList(trace("t1", 400, false)), 11);

        assertEquals(18L, page(client, 0, 10).total);
    }

    /** An account with only sessions, or only loose records, still renders. */
    @Test
    public void oneSidedDataStillProducesAPage() {
        SearchClient onlySessions = clientWith(
            Arrays.asList(session("s1", 500, 3)), 1, new ArrayList<>(), 0);
        SearchClient onlyTraces = clientWith(
            new ArrayList<>(), 0, Arrays.asList(trace("t1", 400, false)), 1);

        assertEquals(Arrays.asList("s1"), idsOf(page(onlySessions, 0, 10).rows));
        assertEquals(Arrays.asList("t1"), idsOf(page(onlyTraces, 0, 10).rows));
    }

    /**
     * A backend that ignores onlyWithoutSession answers with everything, which would list a
     * conversation's records both inside its session row and again beside it.
     */
    @Test
    public void sessionedRecordsAreNeverListedTwice() {
        Map<String, Object> alreadyGrouped = trace("t-grouped", 450, false);
        alreadyGrouped.put(AgentQueryRecord.F_SESSION_IDENTIFIER, "s1");

        SearchClient client = clientWith(
            Arrays.asList(session("s1", 500, 3)), 1,
            Arrays.asList(alreadyGrouped, trace("t-loose", 400, false)), 2);

        assertEquals(Arrays.asList("s1", "t-loose"), idsOf(page(client, 0, 10).rows));
    }

    /** The loose half must be requested with onlyWithoutSession, or rows would be listed twice. */
    @Test
    public void looseHalfIsRequestedWithoutSessionsOnly() {
        StubSearchClient client = new StubSearchClient(
            Arrays.asList(session("s1", 500, 3)), 1, Arrays.asList(trace("t1", 400, false)), 1);

        page(client, 0, 10);
        assertTrue(client.lastOnlyWithoutSession,
            "the flat query must exclude sessioned records");
    }

    // ── stub ──────────────────────────────────────────────────────────────────

    private static class StubSearchClient extends SearchClient {
        private final List<Map<String, Object>> sessions;
        private final long totalSessions;
        private final List<Map<String, Object>> traces;
        private final long totalTraces;
        boolean lastOnlyWithoutSession;

        StubSearchClient(List<Map<String, Object>> sessions, long totalSessions,
                         List<Map<String, Object>> traces, long totalTraces) {
            this.sessions = sessions;
            this.totalSessions = totalSessions;
            this.traces = traces;
            this.totalTraces = totalTraces;
        }

        @Override public boolean isConfigured() { return true; }

        @Override
        public SessionsResult fetchSessions(int accountId, long startMs, long endMs, String searchString,
                Map<String, List<String>> filters, Boolean atlasTrafficFilter,
                int sessionsLimit, String sessionsAfterKey, boolean includeTracesContent) {
            return new SessionsResult(new ArrayList<>(sessions), null, totalSessions);
        }

        @Override
        public SearchResult searchPrompts(int accountId, long startMs, long endMs, int skip, int limit,
                String sortKey, boolean sortAsc, String searchAfterJson,
                Map<String, List<String>> filters, Boolean atlasTrafficFilter, String searchString,
                boolean includeTracesContent, boolean onlyWithoutSession) {
            lastOnlyWithoutSession = onlyWithoutSession;
            return searchPrompts(accountId, startMs, endMs, skip, limit, sortKey, sortAsc,
                    searchAfterJson, filters, atlasTrafficFilter, searchString, includeTracesContent);
        }

        @Override
        public SearchResult searchPrompts(int accountId, long startMs, long endMs, int skip, int limit,
                String sortKey, boolean sortAsc, String searchAfterJson,
                Map<String, List<String>> filters, Boolean atlasTrafficFilter, String searchString,
                boolean includeTracesContent) {
            return new SearchResult(new ArrayList<>(traces), totalTraces);
        }

        @Override public List<Map<String, Object>> fetchMessages(int a, long s, long e,
                Map<String, List<String>> f, Boolean t) { return new ArrayList<>(); }
        @Override public SessionAggStats fetchSessionAggStats(int a, long s, long e,
                Map<String, List<String>> f, Boolean t) { return null; }
        @Override public ArgusStats fetchArgusStats(int a, long s, long e,
                Map<String, List<String>> f, Boolean t, boolean i) { return null; }
        @Override public List<UserAnalysisData> fetchUserAnalysisTokenTotals(int a, long s, long e) {
            return new ArrayList<>(); }
        @Override public List<UserAnalysisData> fetchAgenticServiceObservability(int a, long s, long e, int c) {
            return new ArrayList<>(); }
        @Override public Map<String, Map<String, Integer>> fetchAgenticGlobalTopicHierarchy(
                int a, long s, long e, int t, int st) { return new HashMap<>(); }
        @Override public List<Map<String, Object>> fetchTraceDetail(int a, String t, Boolean f) {
            return new ArrayList<>(); }
        @Override public Map<String, List<String>> fetchPromptFilters(int a, long s, long e) {
            return new HashMap<>(); }
        @Override public void scrollQueryData(int a, long s, long e, int p, int m,
                Consumer<AgentQueryRecord> h) { }
        @Override public List<Map<String, Object>> searchMaliciousComponentInvocations(
                int a, List<String> n, long s, long e, int l) { return new ArrayList<>(); }
        @Override public void bulkUpdateTopics(List<TopicUpdate> u) { }
    }
}
