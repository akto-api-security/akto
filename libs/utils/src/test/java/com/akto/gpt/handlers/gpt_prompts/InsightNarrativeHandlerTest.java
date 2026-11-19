package com.akto.gpt.handlers.gpt_prompts;

import com.mongodb.BasicDBObject;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Logic-level coverage for the epoch-timestamp fix in {@link InsightNarrativeHandler}'s prompt —
 * "the AI summary prints raw epoch seconds instead of human-readable timing" was fixed in the
 * prompt only, not with a new structured field, so this exercises {@code buildPrompt}/
 * {@code nowForPrompt} directly (pure string building, no network call), same convention
 * {@code PromptHandlerTest} already uses for this package's other handlers. {@code validateAndBuild}
 * is also covered directly for the same reason — it's the enforcement mechanism this fix leans on
 * (a model that ignores the new HARD RULE and states a raw epoch/invented date is still rejected by
 * the pre-existing literal-verbatim guard, not by the new rule alone).
 */
class InsightNarrativeHandlerTest {

    private static JSONObject minimalInput() throws org.json.JSONException {
        JSONObject input = new JSONObject();
        input.put("metrics", new org.json.JSONArray()
                .put(new JSONObject().put("key", "incidents").put("label", "Incidents").put("formatted", "5")));
        input.put("evidence", new org.json.JSONArray());
        input.put("caveats", new org.json.JSONArray());
        input.put("dataGaps", new org.json.JSONArray());
        input.put("severity", "");
        input.put("draftConcern", "");
        input.put("draftImpact", "");
        input.put("draftRemediation", "");
        return input;
    }

    @Test
    void testBuildPrompt_mentionsEvidenceSampleGrounding() throws org.json.JSONException {
        String prompt = new InsightNarrativeHandler().buildPrompt(minimalInput(), null);
        assertTrue(prompt.contains("evidenceSample"));
        assertTrue(prompt.toLowerCase().contains("paraphrase"));
    }

    @Test
    void testBuildPrompt_includesEpochHardRuleNamingTheTimestampFields() throws org.json.JSONException {
        String prompt = new InsightNarrativeHandler().buildPrompt(minimalInput(), null);
        assertTrue(prompt.contains("detectedAt"));
        assertTrue(prompt.contains("firstSeen"));
        assertTrue(prompt.contains("lastSeen"));
        assertTrue(prompt.contains("lastScannedAt"));
        assertTrue(prompt.contains("timestamp"));
        assertTrue(prompt.contains("Never print one of these as a bare number"));
        assertTrue(prompt.contains("CURRENT_TIME"));
    }

    @Test
    void testBuildPrompt_neverAsksModelToComputeADateOrDayCount() throws org.json.JSONException {
        String prompt = new InsightNarrativeHandler().buildPrompt(minimalInput(), null);
        // The fix couldn't be "state the real date" — this handler NEVER computes a number (rule 1),
        // and a day-count/age would itself be a computed number. Confirm the rule spells that out.
        assertTrue(prompt.toLowerCase().contains("never state or compute a specific date"));
    }

    @Test
    void testNowForPrompt_reflectsRealClockAndIncludesIsoDate() {
        long before = System.currentTimeMillis() / 1000;
        String now = InsightNarrativeHandler.nowForPrompt();
        long after = System.currentTimeMillis() / 1000;

        long epochInPrompt = Long.parseLong(now.substring(0, now.indexOf(' ')));
        assertTrue(epochInPrompt >= before && epochInPrompt <= after);
        assertTrue(now.contains("(")); // "<epoch> (<ISO date>)"
    }

    // ── validateAndBuild — the actual enforcement backstop behind the new rule ──────

    @Test
    void testValidateAndBuild_rejectsARawEpochNumberNotPresentInFacts() throws org.json.JSONException {
        InsightNarrativeHandler handler = new InsightNarrativeHandler();
        Set<String> allowedLiterals = handler.allowedLiterals(minimalInput()); // only "5" is allowed

        String modelResponse = new JSONObject()
                .put("narrative", "Last seen at 1758375000, this device had 5 incidents.")
                .put("concern", "5 incidents found")
                .put("impact", "Ongoing risk")
                .put("remediation", "Review the policy")
                .toString();

        BasicDBObject result = handler.validateAndBuild(modelResponse, allowedLiterals);
        assertTrue(result.containsField("error"));
        assertTrue(result.getString("error").contains("1758375000"));
    }

    @Test
    void testValidateAndBuild_acceptsRelativePhrasingWithNoNewLiterals() throws org.json.JSONException {
        InsightNarrativeHandler handler = new InsightNarrativeHandler();
        Set<String> allowedLiterals = handler.allowedLiterals(minimalInput());

        String modelResponse = new JSONObject()
                .put("narrative", "This device recorded 5 incidents recently, within the selected window.")
                .put("concern", "5 incidents found on this device")
                .put("impact", "Sensitive data may still be exposed")
                .put("remediation", "Review the device's recent activity")
                .toString();

        BasicDBObject result = handler.validateAndBuild(modelResponse, allowedLiterals);
        assertFalse(result.containsField("error"));
        assertEquals("This device recorded 5 incidents recently, within the selected window.", result.getString("markdown"));
    }

    @Test
    void testAllowedLiterals_doesNotGrantLicenseToStateCurrentYear() throws org.json.JSONException {
        // CURRENT_TIME is orientation for the model, appended by buildPrompt itself — it is never
        // part of the narrativeInput JSON, so allowedLiterals (which only scans the input) must not
        // treat the current year/epoch as a copy-able fact.
        InsightNarrativeHandler handler = new InsightNarrativeHandler();
        Set<String> literals = handler.allowedLiterals(minimalInput());
        String currentYear = String.valueOf(java.time.Year.now(java.time.ZoneOffset.UTC).getValue());
        assertFalse(literals.contains(currentYear));
    }
}
