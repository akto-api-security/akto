package com.akto.gpt.handlers.gpt_prompts;

import com.akto.dto.agents.Model;
import com.akto.dto.agents.ModelType;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnthropicMessagesClientTest {

    private static Model haiku(String key) {
        Map<String, String> p = new HashMap<>();
        p.put(Model.PARAM_MODEL, "claude-haiku-5-5");
        if (key != null) p.put(Model.PARAM_API_KEY, key);
        return new Model("haiku", ModelType.ANTHROPIC, p);
    }

    @Test
    void requestHasModelCapPromptAndThinkingOffAndNoSamplingKnobs() throws Exception {
        JSONObject r = AnthropicMessagesClient.buildRequest(haiku("k"), "the prompt", 4000);
        assertEquals("claude-haiku-5-5", r.getString("model"));
        assertEquals(4000, r.getInt("max_tokens"));
        assertEquals("disabled", r.getJSONObject("thinking").getString("type"));
        assertEquals(1, r.getJSONArray("messages").length());
        assertEquals("user", r.getJSONArray("messages").getJSONObject(0).getString("role"));
        assertEquals("the prompt", r.getJSONArray("messages").getJSONObject(0).getString("content"));
        for (String rejected : new String[]{"temperature", "top_p", "top_k", "system"}) {
            assertFalse(r.has(rejected), rejected);
        }
    }

    @Test
    void textBlocksAreJoinedAndOtherBlocksIgnored() throws Exception {
        String body = "{\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"thinking\",\"thinking\":\"\"},"
                + "{\"type\":\"text\",\"text\":\"{\\\"a\\\":1\"},{\"type\":\"text\",\"text\":\"}\"}]}";
        assertEquals("{\"a\":1}", AnthropicMessagesClient.parseResponse(body));
    }

    @Test
    void truncationAndRefusalFailOnlyThisRequest() {
        for (String stop : new String[]{"max_tokens", "refusal"}) {
            LlmCallException e = assertThrows(LlmCallException.class, () -> AnthropicMessagesClient.parseResponse(
                    "{\"stop_reason\":\"" + stop + "\",\"content\":[{\"type\":\"text\",\"text\":\"{\\\"par\"}]}"));
            assertTrue(e.perRequest, stop);
            assertTrue(e.getMessage().contains(stop));
        }
    }

    @Test
    void emptyContentFailsOnlyThisRequestButAGarbledBodyIsAProviderProblem() {
        assertTrue(assertThrows(LlmCallException.class,
            () -> AnthropicMessagesClient.parseResponse("{\"stop_reason\":\"end_turn\",\"content\":[]}")).perRequest);
        assertTrue(assertThrows(LlmCallException.class,
            () -> AnthropicMessagesClient.parseResponse("{\"content\":[{\"type\":\"text\",\"text\":\"   \"}]}")).perRequest);
        assertFalse(assertThrows(LlmCallException.class, () -> AnthropicMessagesClient.parseResponse("not json")).perRequest);
        assertFalse(assertThrows(LlmCallException.class, () -> AnthropicMessagesClient.parseResponse("")).perRequest);
    }

    @Test
    void onlyA413IsAboutTheInputEveryOtherStatusIsAProviderProblem() {
        assertTrue(AnthropicMessagesClient.httpError(413, "").perRequest);
        for (int code : new int[]{400, 401, 403, 404, 408, 429, 500, 502, 529}) {
            assertFalse(AnthropicMessagesClient.httpError(code, "").perRequest, "" + code);
        }
    }

    @Test
    void errorSnippetIsBounded() {
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 5000; i++) big.append('x');
        assertTrue(AnthropicMessagesClient.httpError(500, big.toString()).getMessage().length() < 260);
        assertNotNull(AnthropicMessagesClient.httpError(500, null).getMessage());
    }

    @Test
    void missingKeyOrModelFailsBeforeAnyNetworkCall() {
        LlmCallException noKey = assertThrows(LlmCallException.class, () -> AnthropicMessagesClient.call(haiku(null), "p", 100));
        assertFalse(noKey.perRequest);
        Map<String, String> keyOnly = new HashMap<>();
        keyOnly.put(Model.PARAM_API_KEY, "k");
        Model noModel = new Model("m", ModelType.ANTHROPIC, keyOnly);
        assertFalse(assertThrows(LlmCallException.class, () -> AnthropicMessagesClient.call(noModel, "p", 100)).perRequest);
    }

    @Test
    void apiKeyNeverAppearsInTheRequestBody() throws Exception {
        assertFalse(AnthropicMessagesClient.buildRequest(haiku("sk-ant-secret"), "p", 10).toString().contains("sk-ant-secret"));
    }
}
