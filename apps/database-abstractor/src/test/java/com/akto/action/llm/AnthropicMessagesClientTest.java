package com.akto.action.llm;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AnthropicMessagesClientTest {

    @Test
    public void buildRequest_setsModelMaxTokensAndSingleUserMessage() {
        JSONObject request = AnthropicMessagesClient.buildRequest("claude-haiku-5-5", "Hello", 2000);

        assertEquals("claude-haiku-5-5", request.getString("model"));
        assertEquals(2000, request.getInt("max_tokens"));
        assertEquals(1, request.getJSONArray("messages").length());
        assertEquals("user", request.getJSONArray("messages").getJSONObject(0).getString("role"));
        assertEquals("Hello", request.getJSONArray("messages").getJSONObject(0).getString("content"));
    }

    @Test
    public void buildRequest_disablesThinking() {
        JSONObject request = AnthropicMessagesClient.buildRequest("claude-haiku-5-5", "Hello", 100);

        assertEquals("disabled", request.getJSONObject("thinking").getString("type"));
    }

    @Test
    public void buildRequest_hasNoSystemField() {
        JSONObject request = AnthropicMessagesClient.buildRequest("claude-haiku-5-5", "Hello", 100);

        assertFalse(request.has("system"));
    }

    @Test
    public void parseResponse_concatenatesTextBlocks() throws Exception {
        String body = "{\"content\":[{\"type\":\"text\",\"text\":\"Hello \"},{\"type\":\"text\",\"text\":\"world\"}]}";
        assertEquals("Hello world", AnthropicMessagesClient.parseResponse(body));
    }

    @Test
    public void parseResponse_throwsRequestExceptionOnMaxTokensStopReason() {
        String body = "{\"stop_reason\":\"max_tokens\",\"content\":[{\"type\":\"text\",\"text\":\"partial\"}]}";
        try {
            AnthropicMessagesClient.parseResponse(body);
            fail("expected LlmCallException");
        } catch (LlmCallException e) {
            assertTrue(e.perRequest);
        }
    }

    @Test
    public void parseResponse_throwsRequestExceptionOnEmptyContent() {
        String body = "{\"content\":[]}";
        try {
            AnthropicMessagesClient.parseResponse(body);
            fail("expected LlmCallException");
        } catch (LlmCallException e) {
            assertTrue(e.perRequest);
        }
    }

    @Test
    public void httpError_classifies413AsPerRequest() {
        LlmCallException e = AnthropicMessagesClient.httpError(413, "payload too large");
        assertTrue(e.perRequest);
    }

    @Test
    public void httpError_classifiesOtherCodesAsProvider() {
        assertFalse(AnthropicMessagesClient.httpError(401, "unauthorized").perRequest);
        assertFalse(AnthropicMessagesClient.httpError(429, "rate limited").perRequest);
        assertFalse(AnthropicMessagesClient.httpError(500, "server error").perRequest);
    }
}
