package com.akto.action.threat_detection;

import com.akto.dao.context.Context;
import com.akto.gpt.handlers.gpt_prompts.AzureOpenAIPromptHandler;
import com.akto.gpt.handlers.gpt_prompts.ConversationContinuityClassifier;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.utils.elasticsearch.AgentQueryRecord;
import com.akto.utils.elasticsearch.ElasticSearchClient;
import com.akto.utils.elasticsearch.ElasticSearchClient.ContextWindowResult;
import com.mongodb.BasicDBObject;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Conversation-context fallback for a flagged Agentic Security event that carries no sessionId
 * of its own: fetches the nearest before/after messages on the same host (serviceId) from ES,
 * deterministically tags each against the flagged message's own session where possible, and
 * only calls Azure OpenAI for whatever's left unresolved (see ElasticSearchClient.fetchContextWindow
 * and ConversationContinuityClassifier for the two-stage resolution). Supplementary context
 * panel, not critical path — every failure mode here returns an empty/partial result rather
 * than an error, so it never blocks the rest of the flyout from rendering.
 */
public class ContextWindowAction extends AbstractThreatDetectionAction {

    private static final LoggerMaker logger = new LoggerMaker(ContextWindowAction.class, LogDb.DASHBOARD);

    // A repeat open of the same event (same host+timestamp) is extremely common — same analyst
    // reopening the flyout, or several analysts looking at the same alert — and the window is
    // fixed in the past, so its resolved result never changes. Cache the whole fetch+classify
    // result to skip both the ES round trip and the AI call entirely on a repeat view. Process-
    // local only (not shared across dashboard replicas) — acceptable for a first cut; a durable
    // shared cache is the natural next step if this needs to survive across instances.
    private static final long CACHE_TTL_MS = 30L * 60 * 1000; // 30 min
    private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<>();

    // A before/after turn is only worth surfacing in the UI if it's either right next to the
    // flagged message in time, or the AI fallback is confident it's the same conversation.
    private static final long HIGHLIGHT_TIME_DELTA_MS = 2 * 60 * 1000; // 2 minutes
    private static final double HIGHLIGHT_CONFIDENCE_THRESHOLD = 0.8;
    private static final int DISPLAY_COUNT = 3;

    @Setter private String host;
    @Setter private long   timestamp; // SECONDS — the flagged event's detectedAt, as sent by the frontend
    @Setter private int    beforeCount = 6;
    @Setter private int    afterCount  = 6;

    @Getter private Map<String, Object> anchor;
    @Getter private List<Map<String, Object>> before = new ArrayList<>();
    @Getter private List<Map<String, Object>> after  = new ArrayList<>();
    @Getter private boolean llmInvoked = false;

    public String fetchContextMessages() {
        try {
            if (host == null || host.trim().isEmpty()) {
                return SUCCESS.toUpperCase();
            }

            ElasticSearchClient client = ElasticSearchClient.instance();
            if (!client.isConfigured()) {
                return SUCCESS.toUpperCase();
            }

            int accountId = Context.accountId.get();
            String cacheKey = accountId + "|" + host + "|" + timestamp + "|" + beforeCount + "|" + afterCount;
            CacheEntry cached = CACHE.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                anchor = cached.anchor;
                before = cached.before;
                after  = cached.after;
                llmInvoked = cached.llmInvoked;
                return SUCCESS.toUpperCase();
            }

            long targetTsMs = timestamp * 1000L; // seconds -> ms, ES timestamps are epoch millis

            ContextWindowResult result = client.fetchContextWindow(accountId, host, targetTsMs, beforeCount, afterCount, Context.contextSource.get().equals(CONTEXT_SOURCE.ENDPOINT));
            anchor = result.anchor;
            before = result.before;
            after  = result.after;

            annotateOrphansWithLLM();
            applyDisplayFilter();

            CACHE.put(cacheKey, new CacheEntry(anchor, before, after, llmInvoked));
        } catch (Exception e) {
            logger.error("fetchContextMessages error: " + e.getMessage());
        }
        return SUCCESS.toUpperCase();
    }

    /**
     * Runs the LLM fallback only over turns fetchContextWindow couldn't deterministically tie to
     * the anchor's session (RESOLUTION_ORPHAN). The before-side and after-side orphans don't
     * depend on each other, so they're classified as two concurrent calls (on the shared Azure
     * OpenAI executor already used by AzureOpenAIPromptHandler) instead of one serial combined
     * call — cuts wall-clock latency roughly in half on a cache miss. Leaves orphan turns exactly
     * as they were (no confidence score) if the classifier fails.
     */
    private void annotateOrphansWithLLM() {
        if (anchor == null) return;

        List<Map<String, Object>> beforeOrphans = collectOrphans(before);
        List<Map<String, Object>> afterOrphans  = collectOrphans(after);
        if (beforeOrphans.isEmpty() && afterOrphans.isEmpty()) return;

        BasicDBObject anchorInput = new BasicDBObject();
        anchorInput.put(ConversationContinuityClassifier.ANCHOR_QUERY, strVal(anchor.get(AgentQueryRecord.F_QUERY_PAYLOAD)));
        anchorInput.put(ConversationContinuityClassifier.ANCHOR_RESPONSE, strVal(anchor.get(AgentQueryRecord.F_RESPONSE_PAYLOAD)));

        llmInvoked = true;
        ConversationContinuityClassifier classifier = new ConversationContinuityClassifier();

        CompletableFuture<List<BasicDBObject>> beforeFuture = beforeOrphans.isEmpty()
            ? CompletableFuture.completedFuture(new ArrayList<>())
            : CompletableFuture.supplyAsync(
                () -> classifier.classifyOrphans(anchorInput, toCandidateInputs(beforeOrphans, "before")),
                AzureOpenAIPromptHandler.scheduler);
        CompletableFuture<List<BasicDBObject>> afterFuture = afterOrphans.isEmpty()
            ? CompletableFuture.completedFuture(new ArrayList<>())
            : CompletableFuture.supplyAsync(
                () -> classifier.classifyOrphans(anchorInput, toCandidateInputs(afterOrphans, "after")),
                AzureOpenAIPromptHandler.scheduler);

        try {
            applyVerdicts(beforeOrphans, beforeFuture.get(20, TimeUnit.SECONDS));
            applyVerdicts(afterOrphans, afterFuture.get(20, TimeUnit.SECONDS));
        } catch (Exception e) {
            logger.error("ConversationContinuityClassifier error, leaving orphan turns unresolved: " + e.getMessage());
        }
    }

    private static List<Map<String, Object>> collectOrphans(List<Map<String, Object>> turns) {
        List<Map<String, Object>> orphans = new ArrayList<>();
        for (Map<String, Object> turn : turns) {
            if (ElasticSearchClient.RESOLUTION_ORPHAN.equals(turn.get(ElasticSearchClient.KEY_RESOLUTION_METHOD))) {
                orphans.add(turn);
            }
        }
        return orphans;
    }

    private static List<BasicDBObject> toCandidateInputs(List<Map<String, Object>> turns, String position) {
        List<BasicDBObject> inputs = new ArrayList<>();
        for (Map<String, Object> turn : turns) {
            BasicDBObject c = new BasicDBObject();
            c.put(ConversationContinuityClassifier.CANDIDATE_QUERY, strVal(turn.get(AgentQueryRecord.F_QUERY_PAYLOAD)));
            c.put(ConversationContinuityClassifier.CANDIDATE_RESPONSE, strVal(turn.get(AgentQueryRecord.F_RESPONSE_PAYLOAD)));
            c.put(ConversationContinuityClassifier.CANDIDATE_POSITION, position);
            inputs.add(c);
        }
        return inputs;
    }

    private static void applyVerdicts(List<Map<String, Object>> turns, List<BasicDBObject> verdicts) {
        for (int i = 0; i < turns.size() && verdicts != null && i < verdicts.size(); i++) {
            BasicDBObject verdict = verdicts.get(i);
            if (verdict == null) continue;
            turns.get(i).put("confidence", verdict.getDouble("confidence", 0.0));
        }
    }

    /**
     * Trims before/after down to what's actually worth showing: at most DISPLAY_COUNT turns per
     * side, nearest to the flagged message, and only turns that are either close in time or the
     * AI fallback is confident about. A deterministic session match is always kept regardless of
     * time/confidence — it's a harder signal than either.
     */
    private void applyDisplayFilter() {
        if (anchor == null) return;
        long anchorTs = asLong(anchor.get("latestTimestamp"));
        before = filterRelevant(before, anchorTs, true);
        after  = filterRelevant(after, anchorTs, false);
    }

    private static List<Map<String, Object>> filterRelevant(List<Map<String, Object>> turns, long anchorTs, boolean keepTail) {
        List<Map<String, Object>> passed = new ArrayList<>();
        for (Map<String, Object> turn : turns) {
            if (isRelevant(turn, anchorTs)) passed.add(turn);
        }
        int size = passed.size();
        if (size <= DISPLAY_COUNT) return passed;
        // Turns are chronological ascending: for "before" the closest-to-anchor entries are at
        // the tail; for "after" they're at the head.
        return keepTail
            ? new ArrayList<>(passed.subList(size - DISPLAY_COUNT, size))
            : new ArrayList<>(passed.subList(0, DISPLAY_COUNT));
    }

    private static boolean isRelevant(Map<String, Object> turn, long anchorTs) {
        if (ElasticSearchClient.RESOLUTION_SESSION_MATCH.equals(turn.get(ElasticSearchClient.KEY_RESOLUTION_METHOD))) {
            return true;
        }
        long ts = asLong(turn.get("latestTimestamp"));
        if (ts > 0 && anchorTs > 0 && Math.abs(ts - anchorTs) < HIGHLIGHT_TIME_DELTA_MS) return true;
        Object confObj = turn.get("confidence");
        double confidence = confObj instanceof Number ? ((Number) confObj).doubleValue() : -1;
        return confidence > HIGHLIGHT_CONFIDENCE_THRESHOLD;
    }

    private static long asLong(Object v) {
        return v instanceof Number ? ((Number) v).longValue() : 0L;
    }

    private static String strVal(Object v) {
        return v != null ? v.toString() : "";
    }

    private static class CacheEntry {
        final Map<String, Object> anchor;
        final List<Map<String, Object>> before;
        final List<Map<String, Object>> after;
        final boolean llmInvoked;
        final long cachedAtMs;

        CacheEntry(Map<String, Object> anchor, List<Map<String, Object>> before,
                   List<Map<String, Object>> after, boolean llmInvoked) {
            this.anchor = anchor;
            this.before = before;
            this.after = after;
            this.llmInvoked = llmInvoked;
            this.cachedAtMs = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - cachedAtMs > CACHE_TTL_MS;
        }
    }
}
