package com.akto.gpt.handlers.gpt_prompts;

import com.mongodb.BasicDBObject;
import org.json.JSONObject;

import javax.validation.ValidationException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared machinery for a "renderer, not analyst" LLM handler that turns Java-precomputed
 * facts/evidence into prose: the one-retry-on-validation-failure loop, the numeric-literal guard
 * (every number in the model's output must trace back to a real formatted value it was shown),
 * and the model-call tuning (minimal reasoning effort, deterministic temperature, json_object
 * response format) every such handler needs identically. Extracted from InsightNarrativeHandler
 * so AgenticInsightNarrativeHandler (a different output shape — per-finding titles, not
 * concern/impact/remediation) can reuse this exact guard instead of a second hand-copied one.
 *
 * A subclass owns everything shape-specific: buildPrompt (what FACTS/EVIDENCE/HARD RULES look
 * like and what fields to ask for), allowedLiterals (which input fields actually get embedded in
 * the prompt), and validateAndBuild (what fields the response must have and their own per-field
 * checks) — this class only owns what's identical across every such handler.
 */
public abstract class AbstractGroundedNarrativeHandler extends AzureOpenAIPromptHandler {

    public static final String NARRATIVE_INPUT = "narrativeInput"; // JSON string

    protected static final Pattern NUMERIC_LITERAL = Pattern.compile("(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?%?");
    protected static final Pattern MARKDOWN_LINK = Pattern.compile("\\[[^\\]]*\\]\\([^)]*\\)");

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

    /** Package-private (not protected/private): pure string-building, no network call — the exact
     *  prompt the model sees for this handler's shape. Package-private so each subclass's own test
     *  (in this same package) can call it directly, same convention InsightNarrativeHandlerTest
     *  already relies on. */
    abstract String buildPrompt(JSONObject input, String rejectedNote);

    /** Every numeric literal anywhere the prompt actually embeds — see each subclass's own
     *  javadoc for exactly which input fields that covers. */
    abstract Set<String> allowedLiterals(JSONObject input);

    /** Parses/validates the model's raw response against this handler's own output shape,
     *  returning either the accepted fields or {"error": "<reason>"} for the one-retry loop. */
    abstract BasicDBObject validateAndBuild(String rawResponse, Set<String> allowedLiterals);

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
            logger.error(getClass().getSimpleName() + ": " + e.getMessage());
            BasicDBObject resp = new BasicDBObject();
            resp.put("error", "Internal server error: " + e.getMessage());
            return resp;
        }
    }

    private BasicDBObject tryOnce(String prompt, Set<String> allowedLiterals) throws Exception {
        String rawResponse = call(prompt);
        return validateAndBuild(rawResponse, allowedLiterals);
    }

    /** allowedLiterals already carries both comma and no-comma forms of every number (see
     *  addLiteralsFrom) — the model sometimes adds/drops thousands-separators when copying a
     *  number (e.g. writes "1,252" for a fact whose formatted string is "1252"), which is the
     *  same number, not a fabrication. */
    protected void collectUnknownLiterals(String text, Set<String> allowedLiterals, Set<String> out) {
        Matcher m = NUMERIC_LITERAL.matcher(text);
        while (m.find()) {
            String literal = m.group();
            if (!allowedLiterals.contains(literal)) out.add(literal);
        }
    }

    protected void addLiteralsFrom(String text, Set<String> out) {
        if (text == null) return;
        Matcher m = NUMERIC_LITERAL.matcher(text);
        while (m.find()) {
            String literal = m.group();
            out.add(literal);
            String stripped = literal.replace(",", "");
            out.add(stripped); // also allow the same number without a thousands separator
            out.add(withThousandsSeparators(stripped)); // ...and WITH one, even if the source had none —
            // a raw evidence-row count (e.g. a plain "10495" int, not a pre-formatted "formatted"
            // string) has no comma to begin with, but the model naturally writes large numbers with
            // one in prose. Without this, a real, correctly-copied number gets rejected every single
            // retry (the source number never changes), which is what actually caused an infinite
            // regenerate loop for PostureDrillNarrativeService's evidence-only (no "formatted" field)
            // input shape.
        }
    }

    protected String withThousandsSeparators(String numeric) {
        String suffix = "";
        String body = numeric;
        if (body.endsWith("%")) { suffix = "%"; body = body.substring(0, body.length() - 1); }
        String intPart = body;
        String fracPart = "";
        int dot = body.indexOf('.');
        if (dot >= 0) { intPart = body.substring(0, dot); fracPart = body.substring(dot); }
        if (intPart.isEmpty() || intPart.length() <= 3) return numeric;
        StringBuilder grouped = new StringBuilder();
        int digitsSinceComma = 0;
        for (int i = intPart.length() - 1; i >= 0; i--) {
            grouped.append(intPart.charAt(i));
            digitsSinceComma++;
            if (digitsSinceComma % 3 == 0 && i != 0) grouped.append(',');
        }
        return grouped.reverse().toString() + fracPart + suffix;
    }

    /** "<epoch> (<ISO date>)" — orientation for the "describe timing in relative words" hard rule
     *  every subclass's prompt carries, never a value the model is meant to copy into its output
     *  (it isn't added to allowedLiterals). */
    protected static String nowForPrompt() {
        long nowEpoch = System.currentTimeMillis() / 1000;
        String iso = Instant.ofEpochSecond(nowEpoch).atZone(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.ROOT));
        return nowEpoch + " (" + iso + ")";
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
