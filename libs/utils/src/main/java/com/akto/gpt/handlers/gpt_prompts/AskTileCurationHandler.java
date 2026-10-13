package com.akto.gpt.handlers.gpt_prompts;

import com.mongodb.BasicDBObject;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ask Akto overlay "Worth doing now" curation: picks the few already-computed tiles most worth the
 * user's attention and writes the question each one should ask. It chooses among tiles and writes
 * prose only — every number it may use is a tile's own Java-computed value, enforced by the shared
 * literal guard.
 *
 * Input: {"dashboard": "<label>", "maxPicks": n, "tiles": [{"id","label","value","severity","defaultPrompt"}]}.
 * Output: {"picks": [{"id": "<tile id>", "prompt": "<question>"}]}, most urgent first. Whether each
 * id is one of the input tiles is checked by the caller (AskTileCurationService), which owns the
 * tile set.
 */
public class AskTileCurationHandler extends AbstractGroundedNarrativeHandler {

    static final int MAX_PROMPT_WORDS = 30;

    @Override
    BasicDBObject validateAndBuild(String rawResponse, Set<String> allowedLiterals) {
        BasicDBObject resp = new BasicDBObject();
        if (rawResponse == null || rawResponse.isEmpty()) {
            resp.put("error", "empty response");
            return resp;
        }
        try {
            JSONArray picks = new JSONObject(rawResponse).optJSONArray("picks");
            if (picks == null || picks.length() == 0) {
                resp.put("error", "picks must be a non-empty array");
                return resp;
            }

            List<BasicDBObject> accepted = new ArrayList<>();
            Set<String> seenIds = new HashSet<>();
            Set<String> unknownLiterals = new HashSet<>();
            for (int i = 0; i < picks.length(); i++) {
                JSONObject pick = picks.optJSONObject(i);
                String id = pick == null ? "" : pick.optString("id", "").trim();
                String prompt = pick == null ? "" : pick.optString("prompt", "").trim();
                if (id.isEmpty() || prompt.isEmpty()) {
                    resp.put("error", "every pick needs a non-empty id and prompt");
                    return resp;
                }
                if (!seenIds.add(id)) {
                    resp.put("error", "duplicate tile id " + id);
                    return resp;
                }
                if (MARKDOWN_LINK.matcher(prompt).find()) {
                    resp.put("error", "a prompt contains a markdown link");
                    return resp;
                }
                if (prompt.split("\\s+").length > MAX_PROMPT_WORDS) {
                    resp.put("error", "a prompt is longer than " + MAX_PROMPT_WORDS + " words");
                    return resp;
                }
                collectUnknownLiterals(prompt, allowedLiterals, unknownLiterals);
                accepted.add(new BasicDBObject("id", id).append("prompt", prompt));
            }
            if (!unknownLiterals.isEmpty()) {
                resp.put("error", "numbers not present in TILES: " + String.join(", ", unknownLiterals));
                return resp;
            }

            resp.put("picks", accepted);
            return resp;
        } catch (Exception e) {
            resp.put("error", "unparseable response");
            return resp;
        }
    }

    @Override
    String buildPrompt(JSONObject input, String rejectedNote) {
        int maxPicks = input.optInt("maxPicks", 4);
        StringBuilder sb = new StringBuilder();
        sb.append("You are curating the \"Worth doing now\" tiles of a security dashboard's assistant for a user who ")
          .append("just opened it on the ").append(input.optString("dashboard", "")).append(" dashboard. Each tile ")
          .append("is a precomputed live number. You are a CURATOR, not an analyst: choose which tiles to show and ")
          .append("write the question each one asks the assistant. Return JSON.\n\n")
          .append("HARD RULES:\n")
          .append("1. Pick between 1 and ").append(maxPicks).append(" tiles, most urgent first. Prefer higher severity ")
          .append("(CRITICAL, then HIGH, then MEDIUM), then the larger number. Use only ids that appear in TILES.\n")
          .append("2. Every number in a prompt MUST be copied verbatim from that tile's own \"value\" or text. Never ")
          .append("compute, sum, round, or estimate a number.\n")
          .append("3. Never name an API, agent, policy, user or vendor that does not appear in TILES.\n")
          .append("4. Each prompt is ONE question the user would type, under ").append(MAX_PROMPT_WORDS)
          .append(" words, about that tile only, asking for something actionable (what to fix first, which ones ")
          .append("matter, why). Use the tile's defaultPrompt as the meaning to keep; make it more specific, never ")
          .append("change what it asks about.\n")
          .append("5. No links, no markdown, no emojis.\n\n")
          .append("TILES: ").append(input.optJSONArray("tiles")).append("\n\n")
          .append("Return exactly: {\"picks\": [{\"id\": \"<tile id>\", \"prompt\": \"<question>\"}]}. ")
          .append("This is a json response.\n");
        if (rejectedNote != null) {
            sb.append("\nYour previous attempt was rejected: ").append(rejectedNote)
              .append(". Follow the HARD RULES exactly.\n");
        }
        return sb.toString();
    }

    @Override
    Set<String> allowedLiterals(JSONObject input) {
        Set<String> out = new HashSet<>();
        JSONArray tiles = input.optJSONArray("tiles");
        if (tiles == null) return out;
        for (int i = 0; i < tiles.length(); i++) {
            JSONObject tile = tiles.optJSONObject(i);
            if (tile == null) continue;
            addLiteralsFrom(tile.optString("value", ""), out);
            addLiteralsFrom(tile.optString("label", ""), out);
            addLiteralsFrom(tile.optString("defaultPrompt", ""), out);
        }
        return out;
    }
}
