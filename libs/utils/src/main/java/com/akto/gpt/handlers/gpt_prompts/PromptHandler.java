package com.akto.gpt.handlers.gpt_prompts;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.testing.ApiExecutor;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.validation.ValidationException;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.http_util.CoreHTTPClient;
import com.mongodb.BasicDBObject;

import okhttp3.OkHttpClient;
import java.io.IOException;
import org.json.JSONObject;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public abstract class PromptHandler {

    static final OkHttpClient client = CoreHTTPClient.client.newBuilder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    static final LoggerMaker logger = new LoggerMaker(PromptHandler.class, LogDb.DASHBOARD);
    static final String JARVIS_ENDPOINT = "http://jarvis.internal.akto.io/api/generate";
    static final String OLLAMA_SERVER_ENDPOINT = buildLlmServerUrl();

    public static String buildLlmServerUrl() {
        String serverEndpoint = JARVIS_ENDPOINT;
        String userServerEndpoint = System.getenv("OLLAMA_SERVER_ENDPOINT");
        if (userServerEndpoint != null && !userServerEndpoint.isEmpty()) {
            serverEndpoint = userServerEndpoint;
        }
        
        logger.debug("llm server url " + serverEndpoint);
        if (serverEndpoint.endsWith("/")) {
            serverEndpoint = serverEndpoint.substring(0, serverEndpoint.length() - 1);
        }
        return serverEndpoint;
    }

    static final String OLLAMA_MODEL = "llama3:8b";
    static final Double temperature = 0.1;
    static final int max_tokens = 4000;

    /**
     * Process the input query data and return a String response.
     */
    public BasicDBObject handle(BasicDBObject queryData) {
        try {
            validate(queryData);
            String prompt = getPrompt(queryData);
            logger.info(">>>>>>>>>>>>>Prompt: " + prompt);
            String rawResponse = call(prompt, OLLAMA_MODEL, temperature, max_tokens);
            BasicDBObject resp = processResponse(rawResponse);
            return resp;
        } catch (ValidationException exception) {
            logger.error("Validation error: " + exception.getMessage());
            BasicDBObject resp = new BasicDBObject();
            resp.put("error", "Invalid input parameters.");
            return resp;
        } catch (Exception e) {
            logger.error("Error while handling request: " + e);
            BasicDBObject resp = new BasicDBObject();
            resp.put("error", "Internal server error" + e.getMessage());
            return resp;
        }
    }

    /**
     * Validate input parameters.
     */
    protected abstract void validate(BasicDBObject queryData) throws ValidationException;

    /**
     * Return the prompt string to be sent to the AI.
     */
    protected abstract String getPrompt(BasicDBObject queryData);

    /**
     * Call the AI model with the provided prompt and parameters
     */
    protected String call(String prompt, String model, Double temperature, int maxTokens) throws Exception {
        MediaType mediaType = MediaType.parse("application/json");
        JSONObject payload = new JSONObject();
        payload.put("model", model);
        payload.put("prompt", prompt);
        JSONObject options = new JSONObject();
        options.put("temperature", temperature);
        options.put("num_predict", maxTokens);
        options.put("top_p", 0.9); // Added top_p
        options.put("top_k", 50); // Added top_k
        options.put("repeat_penalty", 1.1); // Penalize repetitions
        options.put("presence_penalty", 0.6); // Discourage new topic jumps
        options.put("frequency_penalty", 0.0); // Don't punish frequency
        payload.put("options", options);
        payload.put("stream", false);

        return getLLMPromptResponse(payload);
    }

    /**
     * Process the raw response (e.g., clean answer).
     */
    protected abstract BasicDBObject processResponse(String rawResponse);

    static String cleanJSON(String rawResponse) {
        if (rawResponse == null || rawResponse.isEmpty()) {
            return "NOT_FOUND";
        }

        // Truncate at the last closing brace to remove any trailing notes
        int lastBrace = rawResponse.lastIndexOf('}');
        if (lastBrace != -1) {
            rawResponse = rawResponse.substring(0, lastBrace + 1);
        }

        // Start at the first opening brace to remove any forward notes
        int firstBrace = rawResponse.indexOf('{');
        if (firstBrace != -1) {
            rawResponse = rawResponse.substring(firstBrace);
        }
        return rawResponse.trim();
    }

    static String processOutput(String rawResponse) {
        try {

            rawResponse = cleanJSON(rawResponse);

            JSONObject jsonResponse = new JSONObject(rawResponse);
            String cleanResponse = jsonResponse.getString("response");

            logger.info(">>>>>>>>>>>Cleaned response: " + cleanResponse);
    
            // Remove <think> tags
            cleanResponse = cleanResponse.replaceAll("(?s)<think>.*?</think>", "").trim();
    
            // If wrapped in escaped quotes, unescape it
            if (cleanResponse.startsWith("\"") && cleanResponse.endsWith("\"")) {
                cleanResponse = cleanResponse.substring(1, cleanResponse.length() - 1)
                                             .replace("\\\"", "");
            }
    
            return cleanResponse.trim();
        } catch (Exception e) {
            logger.error("Failed to clean LLM response: " + rawResponse, e);
            return "NOT_FOUND";
        }
    }

    private String getLLMPromptResponse(JSONObject promptPayload) {
        try {
            JSONObject requestJson = new JSONObject();
            requestJson.put("llmPayload", promptPayload);

            OriginalHttpRequest request = new OriginalHttpRequest(
                "https://cyborg.akto.io/api/getLLMResponse",
                "",
                "POST",
                requestJson.toString(),
                buildHeaders(),
                ""
            );

            logger.debug("Sending request to LLM server: {}", requestJson);

            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);

            if (response == null) {
                logger.errorAndAddToDb("Response object is null from LLM server");
                return null;
            }

            String responsePayload = response.getBody();

            if (response.getStatusCode() != 200) {
                logger.errorAndAddToDb("Non-2xx response in getLLMResponse: " + response.getStatusCode());
                return null;
            }

            if (responsePayload == null || responsePayload.trim().isEmpty()) {
                logger.errorAndAddToDb("Empty or null response body from LLM server");
                return null;
            }

            logger.debug("Received response from LLM server: {}", responsePayload);
            return responsePayload;

        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Exception in getLLMResponse.");
        }
        return null;
    }

    public Map<String, List<String>> buildHeaders() {
        Map<String, List<String>> headers = new HashMap<>();
        // add token
        headers.put("Authorization", Collections.singletonList(""));
        return headers;
    }
}