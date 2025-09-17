package com.akto.gen_ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.util.Pair;

public class GenAiCollectionUtilsTest {

    @BeforeEach
    public void setUp() {
        // Setup will be done in each test
    }

    private HttpResponseParams createResponseParams(String url) {
        HttpRequestParams requestParams = new HttpRequestParams();
        requestParams.setUrl(url);

        HttpResponseParams responseParams = new HttpResponseParams();
        responseParams.setRequestParams(requestParams);

        return responseParams;
    }

    private HttpResponseParams createResponseParams(String url, String payload, boolean isJson) {
        HttpRequestParams requestParams = new HttpRequestParams();
        requestParams.setUrl(url);
        requestParams.setPayload(payload);
        java.util.Map<String, java.util.List<String>> headers = new java.util.HashMap<>();
        if (isJson) {
            headers.put("content-type", java.util.Collections.singletonList("application/json"));
        }
        requestParams.setHeaders(headers);

        HttpResponseParams responseParams = new HttpResponseParams();
        responseParams.setRequestParams(requestParams);

        return responseParams;
    }

    @Test
    public void testCheckAndTagLLMCollection_OpenAIProvider() {
        HttpResponseParams responseParams = createResponseParams("https://api.openai.com/v1/chat/completions");

        Pair<Boolean, String> result = GenAiCollectionUtils.checkAndTagLLMCollection(responseParams);

        assertTrue(result.getFirst());
        assertEquals("LLM OpenAI", result.getSecond());
    }

    @Test
    public void testCheckAndTagLLMCollection_AzureOpenAIProvider() {
        HttpResponseParams responseParams = createResponseParams("https://myinstance.openai.azure.com/deployments/gpt4");

        Pair<Boolean, String> result = GenAiCollectionUtils.checkAndTagLLMCollection(responseParams);

        assertTrue(result.getFirst());
        assertEquals("LLM Azure OpenAI", result.getSecond());
    }

    @Test
    public void testCheckAndTagLLMCollection_AnthropicProvider() {
        HttpResponseParams responseParams = createResponseParams("https://api.anthropic.com/v1/messages");

        Pair<Boolean, String> result = GenAiCollectionUtils.checkAndTagLLMCollection(responseParams);

        assertTrue(result.getFirst());
        assertEquals("LLM Anthropic", result.getSecond());
    }

    @Test
    public void testCheckAndTagLLMCollection_CohereProvider() {
        HttpResponseParams responseParams = createResponseParams("https://api.cohere.ai/generate");

        Pair<Boolean, String> result = GenAiCollectionUtils.checkAndTagLLMCollection(responseParams);

        assertTrue(result.getFirst());
        assertEquals("LLM Cohere", result.getSecond());
    }

    @Test
    public void testCheckAndTagLLMCollection_GoogleProvider() {
        HttpResponseParams responseParams = createResponseParams("https://generativelanguage.googleapis.com/v1/models/gemini-pro");

        Pair<Boolean, String> result = GenAiCollectionUtils.checkAndTagLLMCollection(responseParams);

        assertTrue(result.getFirst());
        assertEquals("LLM Google", result.getSecond());
    }

    @Test
    public void testCheckAndTagLLMCollection_OllamaProvider() {
        HttpResponseParams responseParams = createResponseParams(
            "http://localhost:11434/api/generate",
            "{\"model\":\"llama2\",\"prompt\":\"Hello\"}",
            true
        );

        Pair<Boolean, String> result = GenAiCollectionUtils.checkAndTagLLMCollection(responseParams);

        assertTrue(result.getFirst());
        assertEquals("LLM Ollama", result.getSecond());
    }

    @Test
    public void testCheckAndTagLLMCollection_CustomProvider() {
        HttpResponseParams responseParams = createResponseParams(
            "http://my-custom-llm.com/v1/completions",
            "{\"model\":\"custom-model\",\"input\":\"test\"}",
            true
        );

        Pair<Boolean, String> result = GenAiCollectionUtils.checkAndTagLLMCollection(responseParams);

        assertTrue(result.getFirst());
        assertEquals("LLM Custom", result.getSecond());
    }

    @Test
    public void testCheckAndTagLLMCollection_NoMatch() {
        HttpResponseParams responseParams = createResponseParams("https://api.example.com/v1/data");

        Pair<Boolean, String> result = GenAiCollectionUtils.checkAndTagLLMCollection(responseParams);

        assertFalse(result.getFirst());
        assertNull(result.getSecond());
    }

    @Test
    public void testCheckAndTagLLMCollection_NullUrl() {
        HttpResponseParams responseParams = createResponseParams(null);

        Pair<Boolean, String> result = GenAiCollectionUtils.checkAndTagLLMCollection(responseParams);

        assertFalse(result.getFirst());
        assertNull(result.getSecond());
    }

    @Test
    public void testCheckAndTagLLMCollection_OllamaWithoutKeyword() {
        HttpResponseParams responseParams = createResponseParams(
            "http://localhost:11434/api/generate",
            "{\"prompt\":\"Hello\"}",
            true
        );

        Pair<Boolean, String> result = GenAiCollectionUtils.checkAndTagLLMCollection(responseParams);

        assertFalse(result.getFirst());
        assertNull(result.getSecond());
    }

    @Test
    public void testCheckAndTagLLMCollection_OllamaNotJsonRequest() {
        HttpResponseParams responseParams = createResponseParams(
            "http://localhost:11434/api/generate",
            "model=test&prompt=hello",
            false
        );

        Pair<Boolean, String> result = GenAiCollectionUtils.checkAndTagLLMCollection(responseParams);

        assertFalse(result.getFirst());
        assertNull(result.getSecond());
    }

    @Test
    public void testCheckAndTagLLMCollection_PayloadTooLarge() {
        // Create a payload larger than 10000 characters
        StringBuilder largePayload = new StringBuilder("{\"model\":\"test\"");
        for (int i = 0; i < 10001; i++) {
            largePayload.append("x");
        }
        largePayload.append("}");

        HttpResponseParams responseParams = createResponseParams(
            "http://localhost:11434/api/generate",
            largePayload.toString(),
            true
        );

        Pair<Boolean, String> result = GenAiCollectionUtils.checkAndTagLLMCollection(responseParams);

        assertFalse(result.getFirst());
        assertNull(result.getSecond());
    }

    @Test
    public void testCheckAndTagLLMCollection_ResultNotNull() {
        HttpResponseParams responseParams = createResponseParams("https://api.example.com/v1/data");

        Pair<Boolean, String> result = GenAiCollectionUtils.checkAndTagLLMCollection(responseParams);

        assertNotNull(result);
        assertNotNull(result.getFirst());
    }

    @Test
    public void testCheckAndTagLLMCollection_HuggingFaceProvider() {
        HttpResponseParams responseParams = createResponseParams("https://api-inference.huggingface.co/models/bert-base");

        Pair<Boolean, String> result = GenAiCollectionUtils.checkAndTagLLMCollection(responseParams);

        assertTrue(result.getFirst());
        assertEquals("LLM Hugging Face", result.getSecond());
    }

    @Test
    public void testCheckAndTagLLMCollection_IBMProvider() {
        HttpResponseParams responseParams = createResponseParams("https://us-south.ml.cloud.ibm.com/v1/generate");

        Pair<Boolean, String> result = GenAiCollectionUtils.checkAndTagLLMCollection(responseParams);

        assertTrue(result.getFirst());
        assertEquals("LLM IBM", result.getSecond());
    }

    @Test
    public void testCheckAndTagLLMCollection_ReplicateProvider() {
        HttpResponseParams responseParams = createResponseParams("https://api.replicate.com/v1/predictions");

        Pair<Boolean, String> result = GenAiCollectionUtils.checkAndTagLLMCollection(responseParams);

        assertTrue(result.getFirst());
        assertEquals("LLM Replicate", result.getSecond());
    }

    @Test
    public void testCheckAndTagLLMCollection_AI21Provider() {
        HttpResponseParams responseParams = createResponseParams("https://api.ai21.com/studio/v1/complete");

        Pair<Boolean, String> result = GenAiCollectionUtils.checkAndTagLLMCollection(responseParams);

        assertTrue(result.getFirst());
        assertEquals("LLM AI21", result.getSecond());
    }

    @Test
    public void testCheckAndTagLLMCollection_OllamaChatEndpoint() {
        HttpResponseParams responseParams = createResponseParams(
            "http://localhost:11434/api/chat",
            "{\"model\":\"llama2\",\"messages\":[]}",
            true
        );

        Pair<Boolean, String> result = GenAiCollectionUtils.checkAndTagLLMCollection(responseParams);

        assertTrue(result.getFirst());
        assertEquals("LLM Ollama", result.getSecond());
    }

    @Test
    public void testCheckAndTagLLMCollection_CustomChatCompletionsEndpoint() {
        HttpResponseParams responseParams = createResponseParams(
            "http://my-llm-server.com/v1/chat/completions",
            "{\"model\":\"gpt-3.5-turbo\",\"messages\":[]}",
            true
        );

        Pair<Boolean, String> result = GenAiCollectionUtils.checkAndTagLLMCollection(responseParams);

        assertTrue(result.getFirst());
        assertEquals("LLM Custom", result.getSecond());
    }

    @Test
    public void testCheckAndTagLLMCollection_NullPayload() {
        HttpResponseParams responseParams = createResponseParams(
            "http://localhost:11434/api/generate",
            null,
            true
        );

        Pair<Boolean, String> result = GenAiCollectionUtils.checkAndTagLLMCollection(responseParams);

        assertFalse(result.getFirst());
        assertNull(result.getSecond());
    }

    @Test
    public void testCheckAndTagLLMCollection_EmptyPayload() {
        HttpResponseParams responseParams = createResponseParams(
            "http://localhost:11434/api/generate",
            "",
            true
        );

        Pair<Boolean, String> result = GenAiCollectionUtils.checkAndTagLLMCollection(responseParams);

        assertFalse(result.getFirst());
        assertNull(result.getSecond());
    }

    @Test
    public void testCheckAndTagLLMCollection_MultipleProviderEndpoints() {
        // Test that first matching provider wins
        HttpResponseParams responseParams = createResponseParams("https://api.openai.com/v1/completions");

        Pair<Boolean, String> result = GenAiCollectionUtils.checkAndTagLLMCollection(responseParams);

        assertTrue(result.getFirst());
        assertEquals("LLM OpenAI", result.getSecond());
    }

    @Test
    public void testCheckAndTagLLMCollection_PartialUrlMatch() {
        // Test partial URL match for providers
        HttpResponseParams responseParams = createResponseParams("https://api.openai.com/v1/models");

        Pair<Boolean, String> result = GenAiCollectionUtils.checkAndTagLLMCollection(responseParams);

        assertTrue(result.getFirst());
        assertEquals("LLM OpenAI", result.getSecond());
    }

    @Test
    public void testCheckAndTagLLMCollection_CaseSensitiveUrl() {
        // URLs should be case-sensitive
        HttpResponseParams responseParams = createResponseParams("https://API.OPENAI.COM/v1/chat/completions");

        Pair<Boolean, String> result = GenAiCollectionUtils.checkAndTagLLMCollection(responseParams);

        assertFalse(result.getFirst());
        assertNull(result.getSecond());
    }

    @Test
    public void testCheckAndTagLLMCollection_HttpsAndHttp() {
        // Test both https and http URLs
        HttpResponseParams responseParamsHttps = createResponseParams("https://api.openai.com/v1/chat/completions");
        HttpResponseParams responseParamsHttp = createResponseParams("http://api.openai.com/v1/chat/completions");

        Pair<Boolean, String> resultHttps = GenAiCollectionUtils.checkAndTagLLMCollection(responseParamsHttps);
        Pair<Boolean, String> resultHttp = GenAiCollectionUtils.checkAndTagLLMCollection(responseParamsHttp);

        assertTrue(resultHttps.getFirst());
        assertEquals("LLM OpenAI", resultHttps.getSecond());

        assertTrue(resultHttp.getFirst());
        assertEquals("LLM OpenAI", resultHttp.getSecond());
    }
}