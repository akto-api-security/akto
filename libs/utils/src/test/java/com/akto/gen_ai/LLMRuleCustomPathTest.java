package com.akto.gen_ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.util.Pair;

/**
 * The "Custom" rule is what classifies self-hosted LLM gateways, and it used to list only
 * /v1/completions and /v1/chat/completions. Gateways are routinely mounted without the /v1
 * prefix - a real deployment had three services calling the same gateway on /chat/completions,
 * none of which were recognised - so the rule now matches on /completions, which String.contains
 * makes a superset of all four spellings.
 */
public class LLMRuleCustomPathTest {

    private HttpResponseParams request(String url, String payload) {
        HttpRequestParams requestParams = new HttpRequestParams();
        requestParams.setUrl(url);
        requestParams.setPayload(payload);
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("content-type", Collections.singletonList("application/json"));
        requestParams.setHeaders(headers);

        HttpResponseParams responseParams = new HttpResponseParams();
        responseParams.setRequestParams(requestParams);
        return responseParams;
    }

    private static final String LLM_BODY = "{\"model\":\"gpt-4o\",\"messages\":[]}";

    private void assertLlm(String url, String message) {
        Pair<Boolean, String> result = GenAiCollectionUtils.checkAndTagLLMCollection(request(url, LLM_BODY));
        assertTrue(result.getFirst(), message);
    }

    @Test
    public void chatCompletionsWithoutV1IsRecognised() {
        assertLlm("/chat/completions", "a gateway mounted without the /v1 prefix is still an LLM gateway");
    }

    /**
     * The endpoint list is deliberately explicit rather than collapsed to "/completions", which
     * String.contains would make a superset of all four spellings. Pinning the boundary so that
     * broadening it later is a conscious edit rather than an accident.
     */
    @Test
    public void bareCompletionsIsNotMatched() {
        Pair<Boolean, String> result =
                GenAiCollectionUtils.checkAndTagLLMCollection(request("/completions", LLM_BODY));
        assertFalse(result.getFirst(),
                "only the /v1 forms and /chat/completions are listed; bare /completions is not");
    }

    @Test
    public void previouslyMatchingPathsStillMatch() {
        assertLlm("/v1/chat/completions", "must not regress the spelling that already worked");
        assertLlm("/v1/completions", "must not regress the spelling that already worked");
    }

    @Test
    public void hostPrefixedPathIsRecognised() {
        assertLlm("qa.genai-gateway.svc:8080/chat/completions",
                "matching is on the whole url, so a host-prefixed form must work too");
    }

    @Test
    public void taggedAsCustomProvider() {
        Pair<Boolean, String> result =
                GenAiCollectionUtils.checkAndTagLLMCollection(request("/chat/completions", LLM_BODY));
        assertEquals("LLM Custom", result.getSecond(), "self-hosted gateways classify as Custom");
    }

    // ---------- the body keyword is still load-bearing ----------

    @Test
    public void completionsPathWithoutModelKeywordIsNotLlm() {
        Pair<Boolean, String> result = GenAiCollectionUtils.checkAndTagLLMCollection(
                request("/chat/completions", "{\"prompt\":\"hi\"}"));
        assertFalse(result.getFirst(),
                "url alone must not classify - the broadened path relies on the body keyword to stay honest");
    }

    @Test
    public void unrelatedPathIsNotLlm() {
        Pair<Boolean, String> result =
                GenAiCollectionUtils.checkAndTagLLMCollection(request("/api/v1/bookings/search", LLM_BODY));
        assertFalse(result.getFirst(), "a json body mentioning model must not be enough on its own");
    }

    @Test
    public void autocompleteIsNotMistakenForCompletions() {
        Pair<Boolean, String> result =
                GenAiCollectionUtils.checkAndTagLLMCollection(request("/api/autocomplete", LLM_BODY));
        assertFalse(result.getFirst(), "the match requires a leading slash, so /autocomplete cannot trip it");
    }
}
