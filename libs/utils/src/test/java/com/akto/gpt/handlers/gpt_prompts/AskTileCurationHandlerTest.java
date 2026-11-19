package com.akto.gpt.handlers.gpt_prompts;

import com.mongodb.BasicDBObject;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AskTileCurationHandlerTest {

    private final AskTileCurationHandler handler = new AskTileCurationHandler();

    private static JSONObject input() throws Exception {
        JSONArray tiles = new JSONArray()
                .put(new JSONObject().put("id", "rec_open_criticals").put("label", "Open criticals").put("value", "1,234")
                        .put("severity", "CRITICAL").put("defaultPrompt", "Of these 1234 critical issues, which are likely false positives?"))
                .put(new JSONObject().put("id", "insight_AGING").put("label", "Aging criticals").put("value", "42%")
                        .put("severity", "HIGH").put("defaultPrompt", "Tell me more"));
        return new JSONObject().put("dashboard", "API Security").put("maxPicks", 4).put("tiles", tiles);
    }

    private BasicDBObject validate(String raw) throws Exception {
        return handler.validateAndBuild(raw, handler.allowedLiterals(input()));
    }

    private static String response(String... idPromptPairs) throws Exception {
        JSONArray picks = new JSONArray();
        for (int i = 0; i < idPromptPairs.length; i += 2) {
            picks.put(new JSONObject().put("id", idPromptPairs[i]).put("prompt", idPromptPairs[i + 1]));
        }
        return new JSONObject().put("picks", picks).toString();
    }

    @Test
    void testValidResponse_acceptedInOrder() throws Exception {
        BasicDBObject out = validate(response(
                "insight_AGING", "Why are 42% of criticals aging?",
                "rec_open_criticals", "Which of the 1,234 criticals are false positives?"));

        assertFalse(out.containsField("error"), String.valueOf(out.get("error")));
        List<?> picks = (List<?>) out.get("picks");
        assertEquals(2, picks.size());
        assertEquals("insight_AGING", ((BasicDBObject) picks.get(0)).getString("id"));
    }

    @Test
    void testNumberWithoutThousandsSeparator_stillAllowed() throws Exception {
        assertFalse(validate(response("rec_open_criticals", "Triage the 1234 criticals")).containsField("error"));
    }

    @Test
    void testInventedNumber_rejected() throws Exception {
        BasicDBObject out = validate(response("rec_open_criticals", "Fix the top 5 of these criticals"));
        assertTrue(out.getString("error").contains("5"));
    }

    @Test
    void testDuplicateId_rejected() throws Exception {
        assertTrue(validate(response("rec_open_criticals", "a", "rec_open_criticals", "b")).getString("error").contains("duplicate"));
    }

    @Test
    void testEmptyPicksOrMissingFields_rejected() throws Exception {
        assertTrue(validate("{\"picks\": []}").containsField("error"));
        assertTrue(validate(response("rec_open_criticals", "")).containsField("error"));
        assertTrue(validate(response("", "a question")).containsField("error"));
        assertTrue(validate("{}").containsField("error"));
    }

    @Test
    void testLinkOrOverlongPrompt_rejected() throws Exception {
        assertTrue(validate(response("rec_open_criticals", "See [issues](https://x)")).containsField("error"));
        StringBuilder longPrompt = new StringBuilder();
        for (int i = 0; i <= AskTileCurationHandler.MAX_PROMPT_WORDS; i++) longPrompt.append("word ");
        assertTrue(validate(response("rec_open_criticals", longPrompt.toString())).containsField("error"));
    }

    @Test
    void testUnparseableOrEmpty_rejected() throws Exception {
        assertTrue(validate("not json").containsField("error"));
        assertTrue(validate("").containsField("error"));
        assertTrue(validate(null).containsField("error"));
    }

    @Test
    void testAllowedLiterals_comeFromTilesOnly() throws Exception {
        Set<String> literals = handler.allowedLiterals(input());
        assertTrue(literals.contains("1,234"));
        assertTrue(literals.contains("1234"));
        assertTrue(literals.contains("42%"));
        assertFalse(literals.contains("4"), "maxPicks is an instruction, not a value the model may print");
    }

    @Test
    void testBuildPrompt_carriesTilesDashboardAndRejection() throws Exception {
        String prompt = handler.buildPrompt(input(), "numbers not present in TILES: 5");
        assertTrue(prompt.contains("rec_open_criticals"));
        assertTrue(prompt.contains("API Security"));
        assertTrue(prompt.contains("Your previous attempt was rejected: numbers not present in TILES: 5"));
    }
}
