package com.akto.gpt.handlers.gpt_prompts;

import com.mongodb.BasicDBObject;
import org.json.JSONObject;

import javax.validation.ValidationException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders one insight's precomputed metric bundle into markdown, plus grounds the
 * provider's own concern/impact/remediation drafts in the real evidence rows (naming
 * actual hosts/users/topics instead of just aggregate counts). This handler NEVER
 * computes a number — every figure in its input is already Java-computed (see
 * InsightService.buildNarrativeInput); its only job is prose. validateAndBuild()
 * mechanically enforces that for every field it returns: any numeric literal in the
 * model's output that isn't copied verbatim from the input is grounds for rejection,
 * with one retry before giving up. Bump PROMPT_VERSION whenever the prompt changes —
 * it is baked into the narrative cache key so old prose can never outlive a changed
 * prompt.
 */
public class InsightNarrativeHandler extends AzureOpenAIPromptHandler {

    public static final int PROMPT_VERSION = 3;
    public static final String NARRATIVE_INPUT = "narrativeInput"; // JSON string

    private static final Pattern NUMERIC_LITERAL = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?%?");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[[^\\]]*\\]\\([^)]*\\)");
    private static final int MAX_WORDS = 260;
    private static final int MAX_SUMMARY_FIELD_WORDS = 60;

    @Override
    protected JSONObject getResponseFormat() {
        try { return new JSONObject("{\"type\":\"json_object\"}"); }
        catch (Exception e) { return null; }
    }

    // Confirmed directly against the real Azure endpoint: at the default reasoning effort, this
    // prompt burned an entire 4000-token budget on invisible reasoning and returned empty content
    // (finish_reason "length") without ever writing the answer — raising the budget alone doesn't
    // fix that, it just burns more tokens/latency reasoning about a job with no real judgment call
    // in it. "minimal" is the right effort here: this handler is a RENDERER, not an analyst — every
    // fact is already computed, its only job is copying values into prose/JSON.
    @Override
    protected String getReasoningEffort() { return "minimal"; }

    @Override
    protected int getMaxTokens() { return 4000; }

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

            String prompt = buildPrompt(input, null);
            BasicDBObject result = tryOnce(prompt, allowedLiterals);
            if (result.containsField("error")) {
                // One retry, naming the offending literals back to the model.
                String rejectedNote = result.getString("error");
                prompt = buildPrompt(input, rejectedNote);
                result = tryOnce(prompt, allowedLiterals);
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

    private BasicDBObject tryOnce(String prompt, Set<String> allowedLiterals) throws Exception {
        String rawResponse = call(prompt);
        return validateAndBuild(rawResponse, allowedLiterals);
    }

    private BasicDBObject validateAndBuild(String rawResponse, Set<String> allowedLiterals) {
        BasicDBObject resp = new BasicDBObject();
        if (rawResponse == null || rawResponse.isEmpty() || "NOT_FOUND".equalsIgnoreCase(rawResponse)) {
            resp.put("error", "empty response");
            return resp;
        }
        try {
            JSONObject json = new JSONObject(rawResponse);
            String narrative = json.optString("narrative", "");
            String concern = json.optString("concern", "");
            String impact = json.optString("impact", "");
            String remediation = json.optString("remediation", "");

            if (narrative.isEmpty()) { resp.put("error", "no narrative"); return resp; }
            if (MARKDOWN_LINK.matcher(narrative).find()) { resp.put("error", "contains a markdown link"); return resp; }
            if (narrative.split("\\s+").length > MAX_WORDS) { resp.put("error", "too long"); return resp; }

            Set<String> unknownLiterals = new HashSet<>();
            collectUnknownLiterals(narrative, allowedLiterals, unknownLiterals);

            Map<String, String> summaryFields = new LinkedHashMap<>();
            summaryFields.put("concern", concern);
            summaryFields.put("impact", impact);
            summaryFields.put("remediation", remediation);
            for (Map.Entry<String, String> e : summaryFields.entrySet()) {
                String value = e.getValue();
                if (value.isEmpty()) continue; // model may leave one blank when there's genuinely nothing grounded to add
                if (value.split("\\s+").length > MAX_SUMMARY_FIELD_WORDS) {
                    resp.put("error", e.getKey() + " is too long"); return resp;
                }
                if (MARKDOWN_LINK.matcher(value).find()) { resp.put("error", e.getKey() + " contains a markdown link"); return resp; }
                collectUnknownLiterals(value, allowedLiterals, unknownLiterals);
            }
            if (!unknownLiterals.isEmpty()) {
                resp.put("error", "numbers not present in FACTS: " + String.join(", ", unknownLiterals));
                return resp;
            }

            resp.put("markdown", narrative);
            resp.put("concern", concern);
            resp.put("impact", impact);
            resp.put("remediation", remediation);
            return resp;
        } catch (Exception e) {
            resp.put("error", "unparseable response");
            return resp;
        }
    }

    private void collectUnknownLiterals(String text, Set<String> allowedLiterals, Set<String> out) {
        // allowedLiterals already carries both comma and no-comma forms of every number (see
        // addLiteralsFrom) — the model sometimes adds/drops thousands-separators when copying a
        // number (e.g. writes "1,252" for a fact whose formatted string is "1252"), which is the
        // same number, not a fabrication.
        Matcher m = NUMERIC_LITERAL.matcher(text);
        while (m.find()) {
            String literal = m.group();
            if (!allowedLiterals.contains(literal)) out.add(literal);
        }
    }

    private String buildPrompt(JSONObject input, String rejectedNote) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are rendering a precomputed security finding into prose for someone new to this ")
          .append("product who won't know what to do next. You are a RENDERER, not an analyst — every ")
          .append("number below has already been computed in Java; your job is to make it specific and ")
          .append("concrete, grounded in the real rows in EVIDENCE (actual hosts/users/topics/examples), ")
          .append("not just the aggregate counts in FACTS. Return JSON.\n\n")
          .append("HARD RULES:\n")
          .append("1. Every number in your output (in every field) MUST be copied verbatim from a ")
          .append("\"formatted\" value in FACTS or a cell value in EVIDENCE. Never compute, sum, round, or ")
          .append("estimate a number.\n")
          .append("2. Never name an asset, user, team, server, tool, or topic that does not appear verbatim ")
          .append("in FACTS or EVIDENCE.\n")
          .append("3. Never write a link, URL, or call to action — those are rendered separately.\n")
          .append("4. Include every sentence in CAVEATS and every \"impact\" in DATA_GAPS, verbatim, ")
          .append("somewhere in narrative.\n")
          .append("5. If something is not in FACTS or EVIDENCE, say it is unavailable — never estimate it.\n\n")
          .append("FACTS: ").append(input.optJSONArray("metrics")).append("\n\n")
          .append("EVIDENCE: ").append(input.optJSONArray("evidence")).append("\n\n")
          .append("CAVEATS: ").append(input.optJSONArray("caveats")).append("\n\n")
          .append("DATA_GAPS: ").append(input.optJSONArray("dataGaps")).append("\n\n")
          .append("A provider-computed DRAFT is included below for concern/impact/remediation — it's ")
          .append("correct but generic (it only knows aggregate numbers, not the real rows in EVIDENCE). ")
          .append("Rewrite each one to reference specific rows from EVIDENCE where they exist (name the ")
          .append("actual host/user/topic/example), keeping the same underlying claim. If EVIDENCE has ")
          .append("nothing to add over the draft, you may return the draft unchanged, or \"\" if there is ")
          .append("no draft and nothing grounded to say.\n")
          .append("DRAFT_CONCERN: ").append(input.optString("draftConcern", "")).append("\n")
          .append("DRAFT_IMPACT: ").append(input.optString("draftImpact", "")).append("\n")
          .append("DRAFT_REMEDIATION: ").append(input.optString("draftRemediation", "")).append("\n\n")
          .append("Write four fields:\n")
          .append("- narrative: the detail behind concern/impact/remediation below, for a reader who wants the ")
          .append("specifics — not a restatement of them. 2-4 short sentences of flowing prose, each one naming ")
          .append("a specific fact from FACTS/EVIDENCE (its number and what it's about). Plain sentences only — ")
          .append("no markdown list syntax (no \"- \" or \"* \" bullets, they don't render as a list here, only ")
          .append("as a stray dash), no literal section labels like \"What we found\", \"Why it matters\", ")
          .append("\"Summary\", no markdown heading (#, ##). Under 200 words total, no emojis.\n")
          .append("- concern: one sentence, under 40 words, on what was specifically found — name real ")
          .append("entities from EVIDENCE where possible.\n")
          .append("- impact: one to two sentences, under 40 words, on what happens if this is left ")
          .append("unaddressed.\n")
          .append("- remediation: one to two sentences, under 40 words, the concrete next step to take.\n\n")
          .append("Return exactly: {\"narrative\": \"<markdown>\", \"concern\": \"<text>\", ")
          .append("\"impact\": \"<text>\", \"remediation\": \"<text>\"}. This is a json response.\n");
        if (rejectedNote != null) {
            sb.append("\nYour previous attempt was rejected: ").append(rejectedNote)
              .append(". Return only numbers copied verbatim from FACTS/EVIDENCE, in every field.\n");
        }
        return sb.toString();
    }

    /** Every numeric literal anywhere in FACTS/EVIDENCE/CAVEATS/DATA_GAPS/the DRAFT fields — i.e.
     *  everything actually embedded in the prompt the model sees (see buildPrompt). Deliberately
     *  scans whole serialized blocks rather than cherry-picking fields like "formatted": a metric's
     *  "label" ("more than 5 in 24h") or an evidence table's own title can carry a real number too,
     *  and the model can't tell those apart from a "formatted" value — it just sees text with a
     *  number in it. Field-by-field extraction only catches up with each new case one bug report at
     *  a time; scanning everything the prompt actually contains closes the whole class at once. */
    private Set<String> allowedLiterals(JSONObject input) {
        Set<String> out = new HashSet<>();
        for (String field : new String[] { "metrics", "evidence", "caveats", "dataGaps" }) {
            Object value = input.opt(field);
            if (value != null) addLiteralsFrom(value.toString(), out);
        }
        addLiteralsFrom(input.optString("draftConcern", ""), out);
        addLiteralsFrom(input.optString("draftImpact", ""), out);
        addLiteralsFrom(input.optString("draftRemediation", ""), out);
        return out;
    }

    private void addLiteralsFrom(String text, Set<String> out) {
        if (text == null) return;
        Matcher m = NUMERIC_LITERAL.matcher(text);
        while (m.find()) {
            String literal = m.group();
            out.add(literal);
            out.add(literal.replace(",", "")); // also allow the same number without a thousands separator
        }
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
