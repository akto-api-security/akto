package com.akto.gpt.handlers.gpt_prompts;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.validation.ValidationException;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.http_util.CoreHTTPClient;
import com.mongodb.BasicDBObject;

import okhttp3.OkHttpClient;
import java.io.IOException;
import org.json.JSONObject;
import org.json.JSONArray;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import javax.net.ssl.X509TrustManager;

public abstract class AzureOpenAIPromptHandler {

    public static String buildAzureOpenAIUrl() {
        if (AZURE_OPENAI_HOST == null || AZURE_OPENAI_HOST.isEmpty()) {
            throw new RuntimeException("AZURE_OPENAI_HOST environment variable is not set");
        }
        if (AZURE_OPENAI_DEPLOYMENT == null || AZURE_OPENAI_DEPLOYMENT.isEmpty()) {
            throw new RuntimeException("AZURE_OPENAI_DEPLOYMENT environment variable is not set");
        }
        if (AZURE_OPENAI_API_KEY == null || AZURE_OPENAI_API_KEY.isEmpty()) {
            throw new RuntimeException("AZURE_OPENAI_API_KEY environment variable is not set");
        }
        
        String apiVersion = AZURE_OPENAI_API_VERSION != null && !AZURE_OPENAI_API_VERSION.isEmpty() 
            ? AZURE_OPENAI_API_VERSION 
            : "2024-04-01-preview";
            
        String endpoint = String.format("%s/openai/deployments/%s/chat/completions?api-version=%s",
            AZURE_OPENAI_HOST, AZURE_OPENAI_DEPLOYMENT, apiVersion);
            
        logger.debug("Azure OpenAI endpoint: " + endpoint);
        return endpoint;
    }

    

    static final OkHttpClient client = CoreHTTPClient.client.newBuilder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .sslSocketFactory(CoreHTTPClient.trustAllSslSocketFactory, (X509TrustManager)CoreHTTPClient.trustAllCerts[0])
            .hostnameVerifier((hostname, session) -> true)
            .build();

    static final LoggerMaker logger = new LoggerMaker(AzureOpenAIPromptHandler.class, LogDb.DASHBOARD);
    private static volatile String AZURE_OPENAI_ENDPOINT = null;

    public static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // Environment variables for Azure OpenAI configuration
    private static final String AZURE_OPENAI_HOST = System.getenv("AZURE_OPENAI_HOST");
    private static final String AZURE_OPENAI_DEPLOYMENT = System.getenv("AZURE_OPENAI_DEPLOYMENT");
    private static final String AZURE_OPENAI_API_KEY = System.getenv("AZURE_OPENAI_API_KEY");
    private static final String AZURE_OPENAI_API_VERSION = System.getenv("AZURE_OPENAI_API_VERSION");

    private static String getAzureOpenAIEndpoint() {
        
        try {
            AZURE_OPENAI_ENDPOINT = buildAzureOpenAIUrl();
            return AZURE_OPENAI_ENDPOINT;
        } catch (RuntimeException e) {
            logger.error("Failed to build Azure OpenAI endpoint: " + e.getMessage());
            throw e;
        }
    }

    public BasicDBObject handle(BasicDBObject queryData) {
        try {
            logger.warn("Handling request with query data: " + queryData.toString());
            validate(queryData);
            logger.warn("Validated query data");
            String prompt = getPrompt(queryData);
            logger.warn("Got prompt: " + prompt);
            String rawResponse = call(prompt);
            logger.warn("Got raw response: " + rawResponse);
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
            resp.put("error", "Internal server error: " + e.getMessage());
            return resp;
        }
    }

    protected abstract void validate(BasicDBObject queryData) throws ValidationException;

    protected abstract String getPrompt(BasicDBObject queryData);

    // Overridable so lightweight handlers can cap output tokens / tune sampling
    // without affecting other handlers that share the global defaults.
    protected int getMaxTokens() {
        return PromptHandler.max_tokens;
    }

    protected double getTemperature() {
        return PromptHandler.temperature;
    }

    /** Override to request a specific response_format (e.g. json_object). Null = default (unset). */
    protected JSONObject getResponseFormat() {
        return null;
    }

    /** Reasoning-model-only knob ("minimal" | "low" | "medium" | "high") controlling how many
     *  invisible reasoning tokens the model spends before writing the visible answer — confirmed
     *  directly against the real Azure endpoint: a pure-rendering prompt (no real judgment calls)
     *  burned an entire 4000-token budget on reasoning and returned empty content at the default
     *  effort. "low" is a sane default for handlers that don't override this; a handler doing real
     *  analysis/classification may want "medium"/"high" instead. Ignored for non-reasoning models. */
    protected String getReasoningEffort() {
        return "low";
    }

    // GPT-5 / o-series ("reasoning") deployments reject the classic sampling knobs outright —
    // confirmed directly against the real Azure endpoint: max_tokens -> "unsupported_parameter",
    // any non-default temperature/top_p -> "unsupported_value"/"unsupported_parameter", same for
    // frequency_penalty/presence_penalty. They also spend part of that token budget on invisible
    // reasoning tokens before writing the visible answer, so a small budget can silently return
    // empty content (finish_reason "length") rather than an error — getMaxTokens() should stay
    // generous for these deployments.
    private static boolean isReasoningModelDeployment() {
        if (AZURE_OPENAI_DEPLOYMENT == null) return false;
        String d = AZURE_OPENAI_DEPLOYMENT.toLowerCase(java.util.Locale.US);
        return d.startsWith("gpt-5") || d.startsWith("o1") || d.startsWith("o3") || d.startsWith("o4");
    }

    private String extractRawContent(String prompt) throws Exception {
        MediaType mediaType = MediaType.parse("application/json");
        JSONObject payload = new JSONObject();

        if (isReasoningModelDeployment()) {
            payload.put("max_completion_tokens", getMaxTokens());
            payload.put("reasoning_effort", getReasoningEffort());
        } else {
            payload.put("temperature", getTemperature());
            payload.put("top_p", 0.9);
            payload.put("max_tokens", getMaxTokens());
            payload.put("frequency_penalty", 0);
            payload.put("presence_penalty", 0.6);
        }
        JSONObject responseFormat = getResponseFormat();
        if (responseFormat != null) {
            payload.put("response_format", responseFormat);
        }

        JSONArray messages = new JSONArray();
        JSONObject systemMessage = new JSONObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", prompt);
        messages.put(systemMessage);
        payload.put("messages", messages);

        RequestBody body = RequestBody.create(payload.toString(), mediaType);
        String apiKey = AZURE_OPENAI_API_KEY;
        Request request = new Request.Builder()
                .url(getAzureOpenAIEndpoint())
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader("api-key", apiKey)
                .addHeader("x-context-source", "AGENTIC")
                .addHeader("authorization", apiKey)
                .build();

        logger.warn("Calling ai with payload: " + payload.toString());
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                ResponseBody errorBody = response.body();
                logger.error("Unexpected response code: " + response.code()
                        + (errorBody != null ? " body: " + errorBody.string() : ""));
                return null;
            }
            ResponseBody responseBody = response.body();
            String rawResponse = responseBody != null ? responseBody.string() : null;
            logger.warn("Response from ai: " + rawResponse);
            if (rawResponse == null) return null;

            JSONObject jsonResponse = new JSONObject(rawResponse);
            JSONArray choices = jsonResponse.getJSONArray("choices");
            JSONObject firstChoice = choices.getJSONObject(0);
            JSONObject message = firstChoice.getJSONObject("message");
            return message.getString("content");
        } catch (IOException e) {
            logger.error("Error while executing request: " + e.getMessage());
            return null;
        }
    }

    protected String call(String prompt) throws Exception {
        return cleanJSON(extractRawContent(prompt));
    }

    protected String callForArray(String prompt) throws Exception {
        return cleanJSONArray(extractRawContent(prompt));
    }
    
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

    static String cleanJSONArray(String rawResponse) {
        if (rawResponse == null || rawResponse.isEmpty()) {
            return "NOT_FOUND";
        }
        int lastBracket = rawResponse.lastIndexOf(']');
        if (lastBracket != -1) {
            rawResponse = rawResponse.substring(0, lastBracket + 1);
        }
        int firstBracket = rawResponse.indexOf('[');
        if (firstBracket != -1) {
            rawResponse = rawResponse.substring(firstBracket);
        }
        return rawResponse.trim();
    }

    static String processOutput(String rawResponse) {
        try {
            return cleanJSON(rawResponse);
        } catch (Exception e) {
            logger.error("Failed to clean LLM response: " + rawResponse, e);
            return "NOT_FOUND";
        }
    }
    
    protected abstract BasicDBObject processResponse(String rawResponse);

}