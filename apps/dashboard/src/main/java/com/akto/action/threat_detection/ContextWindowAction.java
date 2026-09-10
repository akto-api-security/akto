package com.akto.action.threat_detection;

import com.akto.dao.context.Context;
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

    @Setter private String host;
    @Setter private long   timestamp; // SECONDS — the flagged event's detectedAt, as sent by the frontend
    @Setter private int    beforeCount = 10;
    @Setter private int    afterCount  = 10;

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
            long targetTsMs = timestamp * 1000L; // seconds -> ms, ES timestamps are epoch millis

            ContextWindowResult result = client.fetchContextWindow(accountId, host, targetTsMs, beforeCount, afterCount, Context.contextSource.get().equals(CONTEXT_SOURCE.ENDPOINT));
            anchor = result.anchor;
            before = result.before;
            after  = result.after;

            annotateOrphansWithLLM();
        } catch (Exception e) {
            logger.error("fetchContextMessages error: " + e.getMessage());
        }
        return SUCCESS.toUpperCase();
    }

    /**
     * Runs the LLM fallback only over turns fetchContextWindow couldn't deterministically tie to
     * the anchor's session (RESOLUTION_ORPHAN) — one batched call for the whole window, so cost
     * stays flat regardless of how many turns need it. Leaves orphan turns exactly as they were
     * (unresolved, no sameConversation verdict) on any classifier failure.
     */
    private void annotateOrphansWithLLM() {
        if (anchor == null) return;

        List<Map<String, Object>> orphanTurns = new ArrayList<>();
        List<String> positions = new ArrayList<>();
        for (Map<String, Object> turn : before) {
            if (ElasticSearchClient.RESOLUTION_ORPHAN.equals(turn.get(ElasticSearchClient.KEY_RESOLUTION_METHOD))) {
                orphanTurns.add(turn);
                positions.add("before");
            }
        }
        for (Map<String, Object> turn : after) {
            if (ElasticSearchClient.RESOLUTION_ORPHAN.equals(turn.get(ElasticSearchClient.KEY_RESOLUTION_METHOD))) {
                orphanTurns.add(turn);
                positions.add("after");
            }
        }
        if (orphanTurns.isEmpty()) return;

        try {
            BasicDBObject anchorInput = new BasicDBObject();
            anchorInput.put(ConversationContinuityClassifier.ANCHOR_QUERY, strVal(anchor.get(AgentQueryRecord.F_QUERY_PAYLOAD)));
            anchorInput.put(ConversationContinuityClassifier.ANCHOR_RESPONSE, strVal(anchor.get(AgentQueryRecord.F_RESPONSE_PAYLOAD)));

            List<BasicDBObject> candidateInputs = new ArrayList<>();
            for (int i = 0; i < orphanTurns.size(); i++) {
                Map<String, Object> turn = orphanTurns.get(i);
                BasicDBObject c = new BasicDBObject();
                c.put(ConversationContinuityClassifier.CANDIDATE_QUERY, strVal(turn.get(AgentQueryRecord.F_QUERY_PAYLOAD)));
                c.put(ConversationContinuityClassifier.CANDIDATE_RESPONSE, strVal(turn.get(AgentQueryRecord.F_RESPONSE_PAYLOAD)));
                c.put(ConversationContinuityClassifier.CANDIDATE_POSITION, positions.get(i));
                candidateInputs.add(c);
            }

            llmInvoked = true;
            List<BasicDBObject> verdicts = new ConversationContinuityClassifier().classifyOrphans(anchorInput, candidateInputs);
            for (int i = 0; i < orphanTurns.size() && i < verdicts.size(); i++) {
                BasicDBObject verdict = verdicts.get(i);
                if (verdict == null) continue;
                orphanTurns.get(i).put("sameConversation", verdict.getBoolean("sameConversation", false));
                orphanTurns.get(i).put("confidence", verdict.getString("confidence", ""));
                orphanTurns.get(i).put("reason", verdict.getString("reason", ""));
            }
        } catch (Exception e) {
            logger.error("ConversationContinuityClassifier error, leaving orphan turns unresolved: " + e.getMessage());
        }
    }

    private static String strVal(Object v) {
        return v != null ? v.toString() : "";
    }
}
