package com.akto.jobs.executors.ibmwatsonx;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static com.akto.jobs.executors.AIAgentConnectorConstants.CONFIG_WATSONX_CURSOR;
import static com.akto.jobs.executors.AIAgentConnectorConstants.CONFIG_WATSONX_DISCOVERY_HASH;
import static com.akto.jobs.executors.AIAgentConnectorConstants.CONFIG_WATSONX_SEEN_TRACE_IDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** One connector run: discovery, trace windows and pages, the saved cursor, budget and failures. */
public class WatsonxPollerTest {

    static final long NOW = Instant.parse("2026-10-16T12:00:00Z").toEpochMilli();
    static final long HOUR = 60L * 60 * 1000;
    static final long BIG_BUDGET = 1000L * HOUR;

    private FakeWatsonx ibm;
    private AtomicLong clock;
    /** The job's config as stored: the poller's saves are applied to it, as updateJob does. */
    private Map<String, Object> config;
    private List<Map<String, Object>> sent;
    private List<Map<String, Object>> saves;
    private boolean failTrafficSends;

    @BeforeEach
    public void setUp() {
        ibm = new FakeWatsonx();
        ibm.on("/v1/orchestrate/agents", 200, "[{\"id\":\"agent-1\",\"name\":\"support\",\"display_name\":\"Support\"}]");
        clock = new AtomicLong(NOW);
        config = new HashMap<>();
        sent = new ArrayList<>();
        saves = new ArrayList<>();
        failTrafficSends = false;
    }

    private WatsonxPoller poller(long budgetMs) {
        WatsonxClient client = new WatsonxClient(FakeWatsonx.INSTANCE, new WatsonxAuth(FakeWatsonx.INSTANCE, "k", ibm, clock::get), ibm);
        client.retryDelayMs = 0;
        WatsonxPoller.Sink sink = records -> {
            if (failTrafficSends && ((String) records.get(0).get("path")).endsWith("/runs")) {
                throw new IOException("ingestion down");
            }
            sent.addAll(records);
        };
        WatsonxPoller.StateStore state = updates -> {
            saves.add(new HashMap<>(updates));
            // stored as JSON in the job, so read back the way the next run sees it
            config.putAll(FakeWatsonx.MAPPER.readValue(WatsonxRecords.json(updates), Map.class));
        };
        return new WatsonxPoller(client, WatsonxRecordsTest.CONTEXT, sink, state, null, clock::get, budgetMs);
    }

    private List<Map<String, Object>> sentTraffic() {
        List<Map<String, Object>> traffic = new ArrayList<>();
        for (Map<String, Object> record : sent) {
            if (((String) record.get("path")).endsWith("/runs")) {
                traffic.add(record);
            }
        }
        return traffic;
    }

    private Set<String> sentTraceIds() throws Exception {
        Set<String> ids = new HashSet<>();
        for (Map<String, Object> record : sentTraffic()) {
            ids.add(WatsonxRecordsTest.headers(record).get("x-akto-installer-akto_message_id"));
        }
        return ids;
    }

    private static Set<String> set(String... values) {
        return new HashSet<>(java.util.Arrays.asList(values));
    }

    private long cursor() {
        return WatsonxPoller.asLong(config.get(CONFIG_WATSONX_CURSOR), 0);
    }

    // ---------------------------------------------------------------------------------------

    @Test
    public void firstRunReadsTheLastThreeDaysInFourHourWindows() throws Exception {
        long readUntil = NOW - WatsonxPoller.SETTLE_MS;
        ibm.traces.add(FakeWatsonx.conversation("too-old", readUntil - WatsonxPoller.FIRST_RUN_LOOKBACK_MS - HOUR));
        ibm.traces.add(FakeWatsonx.conversation("day-3", readUntil - 70 * HOUR));
        ibm.traces.add(FakeWatsonx.conversation("today", readUntil - HOUR));
        ibm.traces.add(FakeWatsonx.uiCall("ui-call", readUntil - 2 * HOUR));

        WatsonxPoller.Summary summary = poller(BIG_BUDGET).run(config);

        assertEquals(18, summary.windows);
        for (FakeWatsonx.Call call : ibm.callsTo("/v1/agentops-v3/traces")) {
            Map<String, String> query = FakeWatsonx.query(call.url);
            long span = Instant.parse(query.get("toTimestamp")).toEpochMilli() - Instant.parse(query.get("fromTimestamp")).toEpochMilli();
            assertTrue(span <= WatsonxClient.MAX_TRACE_WINDOW_MS, "IBM allows at most 4 hours per query");
        }
        assertEquals(set("day-3", "today"), sentTraceIds());
        assertEquals(2, summary.conversations);
        assertFalse(summary.stoppedForBudget);
        assertEquals(readUntil, cursor(), "the last minute is left for the next run");
    }

    @Test
    public void nextRunContinuesFromTheCursorWithoutResendingTheOverlap() throws Exception {
        long readUntil = NOW - WatsonxPoller.SETTLE_MS;
        ibm.traces.add(FakeWatsonx.conversation("before", readUntil - 5 * 60 * 1000));
        poller(BIG_BUDGET).run(config);
        assertEquals(set("before"), sentTraceIds());

        // five minutes later: a trace that showed up late (before the cursor) and a new one
        clock.addAndGet(5 * 60 * 1000);
        ibm.traces.add(FakeWatsonx.conversation("late", readUntil - 2 * 60 * 1000));
        ibm.traces.add(FakeWatsonx.conversation("after", readUntil + 2 * 60 * 1000));
        sent.clear();
        ibm.calls.clear();
        WatsonxPoller.Summary summary = poller(BIG_BUDGET).run(config);

        assertEquals(set("late", "after"), sentTraceIds());
        assertEquals(1, summary.duplicates, "the trace already sent is skipped");
        Map<String, String> query = FakeWatsonx.query(ibm.callsTo("/v1/agentops-v3/traces").get(0).url);
        assertEquals(WatsonxPoller.iso(readUntil - WatsonxPoller.OVERLAP_MS), query.get("fromTimestamp"));
        assertEquals(1, summary.windows);
    }

    @Test
    public void allPagesOfAWindowAreRead() throws Exception {
        ibm.tracesPageSize = 2;
        for (int i = 0; i < 5; i++) {
            ibm.traces.add(FakeWatsonx.conversation("t" + i, NOW - 3 * HOUR + i * 1000));
        }
        WatsonxPoller.Summary summary = poller(BIG_BUDGET).run(config);
        assertEquals(set("t0", "t1", "t2", "t3", "t4"), sentTraceIds());
        assertEquals(18 + 2, summary.pages);
    }

    @Test
    public void runOutOfTimeSavesProgressAndLaterRunsCatchUpSendingEveryTraceOnce() throws Exception {
        ibm.tracesPageSize = 2;
        ibm.onTracesCall = () -> clock.addAndGet(60 * 1000);   // each page takes a minute
        long base = NOW - 3 * HOUR;
        for (int i = 0; i < 5; i++) {
            ibm.traces.add(FakeWatsonx.conversation("t" + i, base + i * 1000));
        }
        ibm.traces.add(FakeWatsonx.conversation("old", NOW - 70 * HOUR));   // in the first window
        long budget = 150 * 1000;   // about three pages per run

        WatsonxPoller.Summary summary = poller(budget).run(config);
        assertTrue(summary.stoppedForBudget);
        assertEquals(3, summary.pages);
        assertEquals(set("old"), sentTraceIds());
        assertEquals(NOW - WatsonxPoller.SETTLE_MS
                - WatsonxPoller.FIRST_RUN_LOOKBACK_MS + 3 * WatsonxClient.MAX_TRACE_WINDOW_MS, cursor(), "cursor at the end of the third window");

        int runs = 1;
        while (poller(budget).run(config).stoppedForBudget) {
            assertTrue(++runs < 100, "makes progress");
        }
        assertEquals(set("old", "t0", "t1", "t2", "t3", "t4"), sentTraceIds());
        assertEquals(6, sentTraffic().size(), "each trace sent once");
    }

    @Test
    public void stopMidWindowMovesTheCursorOnlyToTheLastTraceRead() throws Exception {
        long readUntil = NOW - WatsonxPoller.SETTLE_MS;
        config.put(CONFIG_WATSONX_CURSOR, readUntil - HOUR);   // one window to read
        ibm.tracesPageSize = 2;
        long base = readUntil - 30 * 60 * 1000;
        for (int i = 0; i < 4; i++) {
            ibm.traces.add(FakeWatsonx.conversation("t" + i, base + i * 60 * 1000));
        }
        WatsonxPoller.Summary summary = poller(0).run(config);
        assertTrue(summary.stoppedForBudget);
        assertEquals(set("t0", "t1"), sentTraceIds());
        assertEquals(base + 60 * 1000, cursor());

        // the next run re-reads from shortly before the cursor; what was sent is skipped
        WatsonxPoller.Summary next = poller(BIG_BUDGET).run(config);
        assertFalse(next.stoppedForBudget);
        assertEquals(2, next.duplicates);
        assertEquals(set("t0", "t1", "t2", "t3"), sentTraceIds());
        assertEquals(4, sentTraffic().size());
        assertEquals(readUntil, cursor());
    }

    @Test
    public void ingestionFailureDoesNotMoveTheCursor() throws Exception {
        long readUntil = NOW - WatsonxPoller.SETTLE_MS;
        config.put(CONFIG_WATSONX_CURSOR, readUntil - HOUR);
        ibm.traces.add(FakeWatsonx.conversation("t1", readUntil - 30 * 60 * 1000));
        failTrafficSends = true;
        try {
            poller(BIG_BUDGET).run(config);
            fail("the run fails, to be retried");
        } catch (IOException expected) {
            assertEquals("ingestion down", expected.getMessage());
        }
        assertEquals(readUntil - HOUR, cursor());
        assertNull(config.get(CONFIG_WATSONX_SEEN_TRACE_IDS));

        failTrafficSends = false;
        poller(BIG_BUDGET).run(config);
        assertEquals(set("t1"), sentTraceIds());
    }

    @Test
    public void ibmErrorWhileReadingTracesKeepsTheCursor() throws Exception {
        long readUntil = NOW - WatsonxPoller.SETTLE_MS;
        config.put(CONFIG_WATSONX_CURSOR, readUntil - HOUR);
        ibm.on("/v1/agentops-v3/traces", 503, "{}");
        try {
            poller(BIG_BUDGET).run(config);
            fail("expected an IBM error");
        } catch (WatsonxApiException e) {
            assertTrue(e.isRetryable());
        }
        assertEquals(readUntil - HOUR, cursor());
    }

    @Test
    public void seenTraceIdsAreKeptOnlyForTheOverlap() throws Exception {
        long readUntil = NOW - WatsonxPoller.SETTLE_MS;
        config.put(CONFIG_WATSONX_CURSOR, readUntil - 3 * HOUR);
        ibm.traces.add(FakeWatsonx.conversation("early", readUntil - 2 * HOUR));
        ibm.traces.add(FakeWatsonx.conversation("recent", readUntil - 2 * 60 * 1000));
        poller(BIG_BUDGET).run(config);
        assertEquals(set("recent"), ((Map<?, ?>) config.get(CONFIG_WATSONX_SEEN_TRACE_IDS)).keySet());
    }

    @Test
    public void startIsNeverOlderThanIbmKeepsTraces() throws Exception {
        config.put(CONFIG_WATSONX_CURSOR, NOW - 60L * 24 * HOUR);
        poller(0).run(config);
        Map<String, String> query = FakeWatsonx.query(ibm.callsTo("/v1/agentops-v3/traces").get(0).url);
        assertEquals(WatsonxPoller.iso(NOW - WatsonxPoller.SETTLE_MS - WatsonxPoller.MAX_LOOKBACK_MS), query.get("fromTimestamp"));
    }

    // ---------------------------------------------------------------------------------------
    // Discovery
    // ---------------------------------------------------------------------------------------

    @Test
    public void discoveryIsSentWhenItChangesOrOnceADay() throws Exception {
        assertTrue(poller(0).run(config).discoverySent);
        assertTrue(sent.get(0).get("path").toString().startsWith("/v1/orchestrate/agents/agent-1"));
        String hash = (String) config.get(CONFIG_WATSONX_DISCOVERY_HASH);

        clock.addAndGet(HOUR);
        assertFalse(poller(0).run(config).discoverySent, "unchanged");

        ibm.on("/v1/orchestrate/agents", 200, "[{\"id\":\"agent-1\",\"name\":\"support\",\"display_name\":\"Support Desk\"}]");
        assertTrue(poller(0).run(config).discoverySent, "an agent was renamed");
        assertFalse(hash.equals(config.get(CONFIG_WATSONX_DISCOVERY_HASH)));

        clock.addAndGet(WatsonxPoller.DISCOVERY_RESEND_MS);
        assertTrue(poller(0).run(config).discoverySent, "daily refresh");
    }

    @Test
    public void trafficUsesDiscoveredNames() throws Exception {
        ibm.traces.add(FakeWatsonx.conversation("t1", NOW - HOUR));
        poller(BIG_BUDGET).run(config);
        assertEquals("Support", WatsonxRecordsTest.tags(sentTraffic().get(0)).get("bot-name"));
    }

    @Test
    public void discoveryOutageDoesNotStopTraffic() throws Exception {
        ibm.on("/v1/orchestrate/tools", 500, "{}");
        ibm.traces.add(FakeWatsonx.conversation("t1", NOW - HOUR));
        WatsonxPoller.Summary summary = poller(BIG_BUDGET).run(config);
        assertFalse(summary.discoverySent);
        assertEquals(set("t1"), sentTraceIds());
        assertEquals("agent-1",
                WatsonxRecordsTest.tags(sentTraffic().get(0)).get("bot-name"), "named by the agent id until discovery works");
    }

    @Test
    public void rejectedKeyStopsTheRun() {
        ibm.on(WatsonxAuth.MCSP_V2_URL, 401, "{}").on(WatsonxAuth.MCSP_V1_URL, 401, "{}");
        try {
            poller(BIG_BUDGET).run(config);
            fail("expected an auth failure");
        } catch (IOException e) {
            assertTrue(((WatsonxApiException) e).isAuthFailure());
        }
        assertTrue(sent.isEmpty());
        assertTrue(saves.isEmpty());
    }
}
