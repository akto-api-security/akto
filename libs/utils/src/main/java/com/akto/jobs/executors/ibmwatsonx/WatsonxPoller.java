package com.akto.jobs.executors.ibmwatsonx;

import com.akto.log.LoggerMaker;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

import static com.akto.jobs.executors.AIAgentConnectorConstants.CONFIG_WATSONX_CURSOR;
import static com.akto.jobs.executors.AIAgentConnectorConstants.CONFIG_WATSONX_DISCOVERY_HASH;
import static com.akto.jobs.executors.AIAgentConnectorConstants.CONFIG_WATSONX_DISCOVERY_SENT_AT;
import static com.akto.jobs.executors.AIAgentConnectorConstants.CONFIG_WATSONX_SEEN_TRACE_IDS;

/**
 * One run of the IBM watsonx Orchestrate connector:
 *
 * 1. discovery (agents, external agents, tools, MCP servers, models, controls, connections,
 *    identity), sent when it changed or once a day;
 * 2. conversation traces from the saved cursor, in windows of at most 4 hours (IBM's limit), page
 *    by page, converted and sent to Akto;
 * 3. the cursor and the recently sent trace ids saved after every page, so a run that stops (budget,
 *    error, restart) resumes where it was, without gaps or duplicates.
 */
public class WatsonxPoller {

    private static final LoggerMaker logger = new LoggerMaker(WatsonxPoller.class);

    /** First run: how far back to read. */
    public static final long FIRST_RUN_LOOKBACK_MS = 3L * 24 * 60 * 60 * 1000;
    /** IBM keeps traces about 30 days; never ask for older ones. */
    static final long MAX_LOOKBACK_MS = 29L * 24 * 60 * 60 * 1000;
    /** Re-read this much before the cursor: traces can appear in the API a little after they happen. */
    static final long OVERLAP_MS = 10L * 60 * 1000;
    /** Do not read the last minute: traces still being written. */
    static final long SETTLE_MS = 60L * 1000;
    static final long DISCOVERY_RESEND_MS = 24L * 60 * 60 * 1000;
    static final int SEND_BATCH_SIZE = 50;
    static final int MAX_SEEN_IDS = 5000;

    public interface Sink {
        void send(List<Map<String, Object>> records) throws IOException;
    }

    public interface StateStore {
        /** Saves these keys into the job's config (they are read back on the next run). */
        void save(Map<String, Object> updates) throws IOException;
    }

    public static final class Summary {
        public int agents;
        public boolean discoverySent;
        public int windows;
        public int pages;
        public int traces;
        public int conversations;
        public int duplicates;
        public int records;
        public boolean stoppedForBudget;
        public long cursorMs;

        @Override
        public String toString() {
            return "agents=" + agents + ", discoverySent=" + discoverySent + ", windows=" + windows + ", pages=" + pages
                    + ", traces=" + traces + ", conversations=" + conversations + ", duplicates=" + duplicates
                    + ", records=" + records + ", stoppedForBudget=" + stoppedForBudget + ", cursor=" + Instant.ofEpochMilli(cursorMs);
        }
    }

    private final WatsonxClient client;
    private final WatsonxRecords.Context context;
    private final Sink sink;
    private final StateStore state;
    private final Runnable heartbeat;
    private final LongSupplier clockMs;
    private final long budgetEndMs;

    public WatsonxPoller(WatsonxClient client, WatsonxRecords.Context context, Sink sink, StateStore state,
            Runnable heartbeat, LongSupplier clockMs, long budgetMs) {
        this.client = client;
        this.context = context;
        this.sink = sink;
        this.state = state;
        this.heartbeat = heartbeat == null ? () -> { } : heartbeat;
        this.clockMs = clockMs;
        this.budgetEndMs = clockMs.getAsLong() + budgetMs;
    }

    public Summary run(Map<String, Object> config) throws IOException {
        Summary summary = new Summary();
        Map<String, Map<String, Object>> inventory = discover(config, summary);
        readTraces(config, inventory, summary);
        return summary;
    }

    // ---------------------------------------------------------------------------------------
    // 1. Discovery
    // ---------------------------------------------------------------------------------------

    private Map<String, Map<String, Object>> discover(Map<String, Object> config, Summary summary) throws IOException {
        WatsonxDiscovery.Result result;
        try {
            result = WatsonxDiscovery.discover(client);
        } catch (WatsonxApiException e) {
            if (e.isAuthFailure()) {
                throw e;
            }
            // Traffic does not depend on it: agent names then come from the traces themselves.
            logger.warn("watsonx discovery failed, reading traces without it: {}", e.getMessage());
            return new HashMap<>();
        }
        summary.agents = result.agents.size();
        heartbeat.run();

        long now = clockMs.getAsLong();
        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, Object> agent : result.agents.values()) {
            records.add(WatsonxRecords.agentDiscoveryRecord(agent, context, now));
        }
        records.addAll(WatsonxRecords.controlPlaneRecords(result.controlPlane, context, now));

        String hash = fingerprint(result);
        long sentAt = asLong(config.get(CONFIG_WATSONX_DISCOVERY_SENT_AT), 0);
        if (!hash.equals(config.get(CONFIG_WATSONX_DISCOVERY_HASH)) || now - sentAt >= DISCOVERY_RESEND_MS) {
            sendInBatches(records);
            Map<String, Object> updates = new HashMap<>();
            updates.put(CONFIG_WATSONX_DISCOVERY_HASH, hash);
            updates.put(CONFIG_WATSONX_DISCOVERY_SENT_AT, now);
            state.save(updates);
            summary.discoverySent = true;
        }
        return result.agents;
    }

    // ---------------------------------------------------------------------------------------
    // 2. Conversation traces
    // ---------------------------------------------------------------------------------------

    private void readTraces(Map<String, Object> config, Map<String, Map<String, Object>> inventory, Summary summary)
            throws IOException {
        long now = clockMs.getAsLong() - SETTLE_MS;
        long savedCursor = asLong(config.get(CONFIG_WATSONX_CURSOR), 0);
        long start = savedCursor > 0 ? savedCursor - OVERLAP_MS : now - FIRST_RUN_LOOKBACK_MS;
        start = Math.max(start, now - MAX_LOOKBACK_MS);
        Map<String, Long> seen = seenIds(config.get(CONFIG_WATSONX_SEEN_TRACE_IDS));
        long cursor = Math.max(savedCursor, start);
        summary.cursorMs = cursor;

        while (start < now) {
            long end = Math.min(start + WatsonxClient.MAX_TRACE_WINDOW_MS, now);
            summary.windows++;
            int totalPages = 1;
            for (int page = 1; page <= totalPages; page++) {
                JsonNode body = client.tracesPage(iso(start), iso(end), page);
                totalPages = Math.max(1, body.path("meta").path("totalPages").asInt(1));
                summary.pages++;

                List<Map<String, Object>> records = new ArrayList<>();
                long lastTraceMs = cursor;
                for (JsonNode trace : body.path("data")) {
                    summary.traces++;
                    long traceMs = WatsonxRecords.traceTimeMs(trace);
                    lastTraceMs = Math.max(lastTraceMs, traceMs);
                    if (!WatsonxRecords.isConversation(trace)) {
                        continue;
                    }
                    String id = trace.path("id").asText();
                    if (seen.containsKey(id)) {
                        summary.duplicates++;
                        continue;
                    }
                    summary.conversations++;
                    records.addAll(WatsonxRecords.conversationRecords(trace, inventory, context));
                    seen.put(id, traceMs);
                }
                sendInBatches(records);
                summary.records += records.size();
                heartbeat.run();

                boolean lastPage = page >= totalPages;
                // A finished window moves the cursor to its end; a run stopped mid-window to its last trace.
                cursor = lastPage ? end : Math.max(cursor, Math.min(lastTraceMs, end));
                saveProgress(cursor, seen);
                summary.cursorMs = cursor;

                if (clockMs.getAsLong() >= budgetEndMs) {
                    boolean caughtUp = lastPage && end >= now;
                    if (!caughtUp) {
                        summary.stoppedForBudget = true;
                        logger.info("watsonx run budget used, next run continues from {}", iso(cursor));
                    }
                    return;
                }
            }
            start = end;
        }
    }

    private void saveProgress(long cursor, Map<String, Long> seen) throws IOException {
        // Keep only the ids that the next run can read again (within the overlap before the cursor).
        long keepFrom = cursor - OVERLAP_MS - SETTLE_MS;
        List<Map.Entry<String, Long>> entries = new ArrayList<>();
        for (Map.Entry<String, Long> entry : seen.entrySet()) {
            if (entry.getValue() >= keepFrom) {
                entries.add(entry);
            }
        }
        entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        Map<String, Object> kept = new LinkedHashMap<>();
        for (int i = 0; i < entries.size() && i < MAX_SEEN_IDS; i++) {
            kept.put(entries.get(i).getKey(), entries.get(i).getValue());
        }
        seen.keySet().retainAll(kept.keySet());

        Map<String, Object> updates = new HashMap<>();
        updates.put(CONFIG_WATSONX_CURSOR, cursor);
        updates.put(CONFIG_WATSONX_SEEN_TRACE_IDS, kept);
        state.save(updates);
    }

    // ---------------------------------------------------------------------------------------

    private void sendInBatches(List<Map<String, Object>> records) throws IOException {
        for (int from = 0; from < records.size(); from += SEND_BATCH_SIZE) {
            sink.send(records.subList(from, Math.min(from + SEND_BATCH_SIZE, records.size())));
            heartbeat.run();
        }
    }

    /** Fingerprint of what discovery found (without times), to send it only when something changed. */
    static String fingerprint(WatsonxDiscovery.Result result) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("agents", result.agents);
        content.put("controlPlane", result.controlPlane);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(WatsonxRecords.json(content).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return String.valueOf(content.hashCode());
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Long> seenIds(Object stored) {
        Map<String, Long> seen = new HashMap<>();
        if (stored instanceof Map) {
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) stored).entrySet()) {
                seen.put(entry.getKey(), asLong(entry.getValue(), 0));
            }
        }
        return seen;
    }

    static long asLong(Object value, long fallback) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    static String iso(long ms) {
        return Instant.ofEpochMilli(ms).toString().replaceAll("\\.\\d+Z$", "Z");
    }
}
