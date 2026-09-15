package com.akto.gpt.handlers.gpt_prompts;

import com.mongodb.BasicDBObject;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.validation.ValidationException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fallback classifier for the threat-detection "conversation context" panel: given a reference
 * message (the anchor) and a batch of nearby same-host messages that could NOT be deterministically
 * tied to the anchor's session (no sessionIdentifier on either side to compare — see
 * ElasticSearchClient.fetchContextWindow's RESOLUTION_ORPHAN turns), scores from content alone how
 * likely each candidate is a genuine continuation of the same conversation as the anchor. Asks for
 * a bare confidence score only (no reasoning text) — the caller only needs a number to threshold
 * against, and skipping the explanation keeps the call fast and cheap.
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

    // Output is just a confidence float per candidate - a handful of tokens each - so a small
    // budget is plenty and keeps the call fast regardless of the base handler's defaults.
    @Override
    protected int getMaxTokens() {
        return 600;
    }

    @Override
    protected void validate(BasicDBObject queryData) throws ValidationException {
        if (!queryData.containsKey(ANCHOR_QUERY) || queryData.getString(ANCHOR_QUERY, "").trim().isEmpty()) {
            throw new ValidationException("Missing mandatory param: " + ANCHOR_QUERY);
        }
    }

    /**
     * Scores every orphan candidate against the anchor in a single Azure OpenAI call. Returns one
     * result per candidate, in the same order as orphanCandidates. Falls back to per-candidate
     * calls if the model's result count doesn't match the input count.
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
        sb.append("You are given a REFERENCE message exchanged between a user and an AI agent, and ")
          .append("several CANDIDATE messages from the same host that occurred shortly before or ")
          .append("after it. A single host can carry many unrelated, interleaved conversations at ")
          .append("once, so proximity in time alone does not mean two messages belong together.\n\n")
          .append("For each numbered CANDIDATE, judge how likely it is a genuine continuation of the ")
          .append("SAME conversation/task as the REFERENCE message - the same exchange, continuing ")
          .append("the same topic, instruction, or tool-call sequence - versus unrelated traffic that ")
          .append("merely landed close in time on the same host. Weigh topical continuity, shared ")
          .append("entities or references, and whether the candidate reads as a natural next or prior ")
          .append("turn in the same exchange.\n\n")
          .append("Return ONLY a JSON object with a 'results' array containing exactly ")
          .append(candidates.size()).append(" object(s), one per candidate IN ORDER, each with a ")
          .append("single 'confidence' field from 0.0 (certainly unrelated) to 1.0 (certainly the ")
          .append("same conversation). No other fields, no explanations:\n")
          .append("{\"results\":[{\"confidence\":0.85}]}\n\n")
          .append("REFERENCE_MESSAGE: ").append(truncate(anchor.getString(ANCHOR_QUERY, ""), MAX_QUERY_CHARS)).append("\n")
          .append("REFERENCE_RESPONSE: ").append(truncate(anchor.getString(ANCHOR_RESPONSE, ""), MAX_RESPONSE_CHARS)).append("\n\n");

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
        double confidence = json.optDouble("confidence", 0.0);
        if (Double.isNaN(confidence)) confidence = 0.0;
        resp.put("confidence", Math.max(0.0, Math.min(1.0, confidence)));
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
