package com.akto.gpt.handlers.gpt_prompts;

import com.mongodb.BasicDBObject;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.validation.ValidationException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders one insight's precomputed metric bundle into markdown. This handler NEVER
 * computes a number — every figure in its input is already Java-computed (see
 * InsightService.buildNarrativeInput); its only job is prose. processResponse()
 * mechanically enforces that: any numeric literal in the model's output that isn't
 * copied verbatim from the input is grounds for rejection, with one retry before
 * giving up. Bump PROMPT_VERSION whenever the prompt changes — it is baked into the
 * narrative cache key so old prose can never outlive a changed prompt.
 */
public class InsightNarrativeHandler extends AzureOpenAIPromptHandler {

    public static final int PROMPT_VERSION = 1;
    public static final String NARRATIVE_INPUT = "narrativeInput"; // JSON string

    private static final Pattern NUMERIC_LITERAL = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?%?");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[[^\\]]*\\]\\([^)]*\\)");
    private static final int MAX_WORDS = 260;

    @Override
    protected JSONObject getResponseFormat() {
        try { return new JSONObject("{\"type\":\"json_object\"}"); }
        catch (Exception e) { return null; }
    }

    @Override
    protected int getMaxTokens() { return 1200; }

    @Override
    protected double getTemperature() { return 0.0; }

    @Override
    protected void validate(BasicDBObject queryData) throws ValidationException {
        String input = queryData.getString(NARRATIVE_INPUT);
        if (input == null || input.trim().isEmpty()) {
            throw new ValidationException(NARRATIVE_INPUT + " is required");
        }
    }

    // Overridden (not just getPrompt/processResponse) for two reasons: avoid the base
    // class's verbose logger.warn(queryData)/logger.warn(prompt) — every cache miss
    // would otherwise dump asset/user/team names into the DASHBOARD log DB — and to run
    // the one-retry-on-validation-failure loop.
    @Override
    public BasicDBObject handle(BasicDBObject queryData) {
        try {
            validate(queryData);
            JSONObject input = new JSONObject(queryData.getString(NARRATIVE_INPUT));
            Set<String> allowedLiterals = allowedLiterals(input);
            Set<String> factKeys = factKeys(input);

            String prompt = buildPrompt(input, null);
            BasicDBObject result = tryOnce(prompt, allowedLiterals, factKeys);
            if (result.containsField("error")) {
                // One retry, naming the offending literals back to the model.
                String rejectedNote = result.getString("error");
                prompt = buildPrompt(input, rejectedNote);
                result = tryOnce(prompt, allowedLiterals, factKeys);
            }
            return result;
        } catch (ValidationException e) {
            BasicDBObject resp = new BasicDBObject();
            resp.put("error", "Invalid input parameters.");
            return resp;
        } catch (Exception e) {
            logger.error("InsightNarrativeHandler: " + e.getMessage());
            BasicDBObject resp = new BasicDBObject();
            resp.put("error", "Internal server error: " + e.getMessage());
            return resp;
        }
    }

    private BasicDBObject tryOnce(String prompt, Set<String> allowedLiterals, Set<String> factKeys) throws Exception {
        String rawResponse = call(prompt);
        return validateAndBuild(rawResponse, allowedLiterals, factKeys);
    }

    private BasicDBObject validateAndBuild(String rawResponse, Set<String> allowedLiterals, Set<String> factKeys) {
        BasicDBObject resp = new BasicDBObject();
        if (rawResponse == null || rawResponse.isEmpty() || "NOT_FOUND".equalsIgnoreCase(rawResponse)) {
            resp.put("error", "empty response");
            return resp;
        }
        try {
            JSONObject json = new JSONObject(rawResponse);
            String narrative = json.optString("narrative", "");
            JSONArray factsUsedArr = json.optJSONArray("factsUsed");

            if (narrative.isEmpty()) { resp.put("error", "no narrative"); return resp; }
            if (MARKDOWN_LINK.matcher(narrative).find()) { resp.put("error", "contains a markdown link"); return resp; }
            if (narrative.split("\\s+").length > MAX_WORDS) { resp.put("error", "too long"); return resp; }

            Set<String> unknownLiterals = new HashSet<>();
            Matcher m = NUMERIC_LITERAL.matcher(narrative);
            while (m.find()) {
                String literal = m.group();
                if (!allowedLiterals.contains(literal)) unknownLiterals.add(literal);
            }
            if (!unknownLiterals.isEmpty()) {
                resp.put("error", "numbers not present in FACTS: " + String.join(", ", unknownLiterals));
                return resp;
            }

            if (factsUsedArr != null) {
                for (int i = 0; i < factsUsedArr.length(); i++) {
                    if (!factKeys.contains(factsUsedArr.optString(i))) {
                        resp.put("error", "factsUsed references an unknown key");
                        return resp;
                    }
                }
            }

            resp.put("markdown", narrative);
            return resp;
        } catch (Exception e) {
            resp.put("error", "unparseable response");
            return resp;
        }
    }

    private String buildPrompt(JSONObject input, String rejectedNote) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are rendering a precomputed security finding into markdown prose. You are a ")
          .append("RENDERER, not an analyst — every number below has already been computed in Java. ")
          .append("Return JSON.\n\n")
          .append("HARD RULES:\n")
          .append("1. Every number in your output MUST be copied verbatim from a \"formatted\" value in ")
          .append("FACTS or a cell value in EVIDENCE. Never compute, sum, round, or estimate a number.\n")
          .append("2. Never name an asset, user, team, server or tool that does not appear verbatim in ")
          .append("FACTS or EVIDENCE.\n")
          .append("3. Never write a link, URL, or call to action — those are rendered separately.\n")
          .append("4. Include every sentence in CAVEATS and every \"impact\" in DATA_GAPS, verbatim.\n")
          .append("5. If something is not in FACTS, say it is unavailable — never estimate it.\n\n")
          .append("FACTS: ").append(input.optJSONArray("metrics")).append("\n\n")
          .append("EVIDENCE: ").append(input.optJSONArray("evidence")).append("\n\n")
          .append("CAVEATS: ").append(input.optJSONArray("caveats")).append("\n\n")
          .append("DATA_GAPS: ").append(input.optJSONArray("dataGaps")).append("\n\n")
          .append("Write: one sentence on what this means, a short \"what we found\" list, then \"why it ")
          .append("matters\". Under 200 words total, no headings other than plain prose, no emojis.\n\n")
          .append("Return exactly: {\"narrative\": \"<markdown>\", \"factsUsed\": [\"<fact key>\", ...]}. ")
          .append("This is a json response.\n");
        if (rejectedNote != null) {
            sb.append("\nYour previous attempt was rejected: ").append(rejectedNote)
              .append(". Return only numbers copied verbatim from FACTS/EVIDENCE.\n");
        }
        return sb.toString();
    }

    /** Every "formatted" string in FACTS, plus every numeric-looking cell value in EVIDENCE, plus totalRowCount. */
    private Set<String> allowedLiterals(JSONObject input) {
        Set<String> out = new HashSet<>();
        JSONArray metrics = input.optJSONArray("metrics");
        if (metrics != null) {
            for (int i = 0; i < metrics.length(); i++) {
                String formatted = metrics.optJSONObject(i) != null ? metrics.optJSONObject(i).optString("formatted", "") : "";
                addLiteralsFrom(formatted, out);
            }
        }
        JSONArray evidence = input.optJSONArray("evidence");
        if (evidence != null) {
            for (int i = 0; i < evidence.length(); i++) {
                JSONObject table = evidence.optJSONObject(i);
                if (table == null) continue;
                addLiteralsFrom(String.valueOf(table.opt("totalRowCount")), out);
                JSONArray rows = table.optJSONArray("rows");
                if (rows == null) continue;
                for (int j = 0; j < rows.length(); j++) {
                    JSONObject row = rows.optJSONObject(j);
                    if (row == null) continue;
                    for (java.util.Iterator<?> it = row.keys(); it.hasNext(); ) {
                        String key = String.valueOf(it.next());
                        addLiteralsFrom(String.valueOf(row.opt(key)), out);
                    }
                }
            }
        }
        return out;
    }

    private void addLiteralsFrom(String text, Set<String> out) {
        if (text == null) return;
        Matcher m = NUMERIC_LITERAL.matcher(text);
        while (m.find()) out.add(m.group());
    }

    private Set<String> factKeys(JSONObject input) {
        Set<String> out = new HashSet<>();
        JSONArray metrics = input.optJSONArray("metrics");
        if (metrics != null) {
            for (int i = 0; i < metrics.length(); i++) {
                JSONObject metric = metrics.optJSONObject(i);
                if (metric != null) out.add(metric.optString("key", ""));
            }
        }
        return out;
    }

    @Override
    protected String getPrompt(BasicDBObject queryData) {
        try {
            return buildPrompt(new JSONObject(queryData.getString(NARRATIVE_INPUT)), null);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected BasicDBObject processResponse(String rawResponse) {
        // handle() is overridden and does its own validation; this exists only to satisfy
        // the abstract contract for any super.handle() caller.
        BasicDBObject resp = new BasicDBObject();
        resp.put("markdown", rawResponse);
        return resp;
    }
}
