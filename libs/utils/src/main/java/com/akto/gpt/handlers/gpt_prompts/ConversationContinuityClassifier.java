package com.akto.gpt.handlers.gpt_prompts;

import com.mongodb.BasicDBObject;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.validation.ValidationException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fallback classifier for the threat-detection "conversation context" panel: given a flagged
 * message (the anchor) and a batch of nearby same-host messages that could NOT be deterministically
 * tied to the anchor's session (no sessionIdentifier on either side to compare — see
 * ElasticSearchClient.fetchContextWindow's RESOLUTION_ORPHAN turns), judges from content alone
 * whether each candidate plausibly belongs to the same conversation as the anchor.
 */
public class ConversationContinuityClassifier extends AzureOpenAIPromptHandler {

    public static final String ANCHOR_QUERY       = "anchorQuery";
    public static final String ANCHOR_RESPONSE    = "anchorResponse";
    public static final String CANDIDATE_QUERY    = "candidateQuery";
    public static final String CANDIDATE_RESPONSE = "candidateResponse";
    public static final String CANDIDATE_POSITION = "candidatePosition"; // "before" | "after"

    private static final int MAX_QUERY_CHARS    = 2000;
    private static final int MAX_RESPONSE_CHARS = 1500;

    @Override
    protected JSONObject getResponseFormat() {
        try { return new JSONObject("{\"type\":\"json_object\"}"); }
        catch (Exception e) { return null; }
    }

    @Override
    protected void validate(BasicDBObject queryData) throws ValidationException {
        if (!queryData.containsKey(ANCHOR_QUERY) || queryData.getString(ANCHOR_QUERY, "").trim().isEmpty()) {
            throw new ValidationException("Missing mandatory param: " + ANCHOR_QUERY);
        }
    }

    /**
     * Classifies every orphan candidate against the anchor in a single Azure OpenAI call.
     * Returns one result per candidate, in the same order as orphanCandidates. Falls back to
     * per-candidate calls if the model's result count doesn't match the input count.
     */
    public List<BasicDBObject> classifyOrphans(BasicDBObject anchor, List<BasicDBObject> orphanCandidates) {
        if (orphanCandidates == null || orphanCandidates.isEmpty()) return new ArrayList<>();

        String rawResponse;
        try {
            rawResponse = call(buildPrompt(anchor, orphanCandidates));
        } catch (Exception e) {
            logger.error("ConversationContinuityClassifier: batch call error, falling back: " + e.getMessage());
            return fallbackToIndividual(anchor, orphanCandidates);
        }

        List<BasicDBObject> results = parseResultsArray(rawResponse);
        if (results.size() != orphanCandidates.size()) {
            logger.error("ConversationContinuityClassifier: expected " + orphanCandidates.size()
                + " results but got " + results.size() + ", falling back");
            return fallbackToIndividual(anchor, orphanCandidates);
        }
        return results;
    }

    // getPrompt/processResponse implement the abstract contract for any direct handle() caller;
    // classifyOrphans (the real entry point for this handler) does not go through them.
    @Override
    protected String getPrompt(BasicDBObject queryData) {
        return buildPrompt(queryData, Collections.singletonList(queryData));
    }

    @Override
    protected BasicDBObject processResponse(String rawResponse) {
        List<BasicDBObject> items = parseResultsArray(rawResponse);
        return items.isEmpty() ? new BasicDBObject() : items.get(0);
    }

    private String buildPrompt(BasicDBObject anchor, List<BasicDBObject> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are reviewing traffic from an AI agent's API. All messages below share the ")
          .append("same host, but a single host can serve many unrelated, concurrent conversations.\n\n")
          .append("ANCHOR is a message that was flagged as a security threat. For each numbered ")
          .append("CANDIDATE (each one either occurred shortly BEFORE or shortly AFTER the anchor), ")
          .append("decide whether it plausibly belongs to the SAME conversation/interaction thread as ")
          .append("the anchor (same user/agent exchange, continuing topic or context), as opposed to ")
          .append("unrelated traffic that merely landed close in time on the same host.\n\n")
          .append("Return a JSON object with a 'results' array containing exactly ")
          .append(candidates.size()).append(" object(s), one per candidate IN ORDER:\n")
          .append("{\"results\":[{\"sameConversation\":true,\"confidence\":\"high\",\"reason\":\"...\"}]}\n")
          .append("confidence must be one of: high, medium, low.\n\n")
          .append("ANCHOR_MESSAGE: ").append(truncate(anchor.getString(ANCHOR_QUERY, ""), MAX_QUERY_CHARS)).append("\n")
          .append("ANCHOR_RESPONSE: ").append(truncate(anchor.getString(ANCHOR_RESPONSE, ""), MAX_RESPONSE_CHARS)).append("\n\n");

        for (int i = 0; i < candidates.size(); i++) {
            BasicDBObject c = candidates.get(i);
            sb.append(i + 1).append(". [").append(c.getString(CANDIDATE_POSITION, "?")).append("] CANDIDATE_MESSAGE: ")
              .append(truncate(c.getString(CANDIDATE_QUERY, ""), MAX_QUERY_CHARS)).append("\n")
              .append("   CANDIDATE_RESPONSE: ")
              .append(truncate(c.getString(CANDIDATE_RESPONSE, ""), MAX_RESPONSE_CHARS)).append("\n\n");
        }
        return sb.toString();
    }

    private List<BasicDBObject> parseResultsArray(String rawResponse) {
        List<BasicDBObject> out = new ArrayList<>();
        if (rawResponse == null || rawResponse.isEmpty() || "NOT_FOUND".equalsIgnoreCase(rawResponse)) return out;
        try {
            JSONObject json = new JSONObject(rawResponse);
            JSONArray arr = json.getJSONArray("results");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                out.add(item != null ? parseItem(item) : new BasicDBObject());
            }
        } catch (Exception e) {
            logger.error("ConversationContinuityClassifier: failed to parse results array: " + e.getMessage());
        }
        return out;
    }

    private static BasicDBObject parseItem(JSONObject json) {
        BasicDBObject resp = new BasicDBObject();
        resp.put("sameConversation", json.optBoolean("sameConversation", false));
        resp.put("confidence", json.optString("confidence", "low"));
        resp.put("reason", json.optString("reason", ""));
        return resp;
    }

    // Fallback for individual candidates — calls the HTTP endpoint directly without going
    // through classifyOrphans to avoid any recursive loop.
    private List<BasicDBObject> fallbackToIndividual(BasicDBObject anchor, List<BasicDBObject> candidates) {
        List<BasicDBObject> results = new ArrayList<>();
        for (BasicDBObject candidate : candidates) {
            try {
                String rawResponse = call(buildPrompt(anchor, Collections.singletonList(candidate)));
                List<BasicDBObject> single = parseResultsArray(rawResponse);
                results.add(single.isEmpty() ? null : single.get(0));
            } catch (Exception e) {
                logger.error("ConversationContinuityClassifier: individual fallback error: " + e.getMessage());
                results.add(null);
            }
        }
        return results;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
