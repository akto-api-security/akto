package com.akto.gpt.handlers.gpt_prompts;

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

public abstract class AzureOpenAIPromptHandler {

    static final OkHttpClient client = CoreHTTPClient.client.newBuilder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    static final LoggerMaker logger = new LoggerMaker(AzureOpenAIPromptHandler.class, LogDb.DASHBOARD);
    
    // Environment variables for Azure OpenAI configuration
    private static final String AZURE_OPENAI_HOST = System.getenv("AZURE_OPENAI_HOST");
    private static final String AZURE_OPENAI_DEPLOYMENT = System.getenv("AZURE_OPENAI_DEPLOYMENT");
    private static final String AZURE_OPENAI_API_KEY = System.getenv("AZURE_OPENAI_API_KEY");
    private static final String AZURE_OPENAI_API_VERSION = System.getenv("AZURE_OPENAI_API_VERSION");
    
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
    static final String AZURE_OPENAI_ENDPOINT = buildAzureOpenAIUrl();

    public BasicDBObject handle(BasicDBObject queryData) {
        try {
            validate(queryData);
            String prompt = getPrompt(queryData);
            String rawResponse = call(prompt);
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

    protected String call(String prompt) throws Exception {
        MediaType mediaType = MediaType.parse("application/json");
        JSONObject payload = new JSONObject();
        
        // Set model parameters
        payload.put("temperature", PromptHandler.temperature);
        payload.put("top_p", 0.9);
        payload.put("max_tokens", PromptHandler.max_tokens);
        payload.put("frequency_penalty", 0);
        payload.put("presence_penalty", 0.6);
        
        // Create messages array
        JSONArray messages = new JSONArray();
        JSONObject systemMessage = new JSONObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", prompt);
        messages.put(systemMessage);
        
        payload.put("messages", messages);

        RequestBody body = RequestBody.create(payload.toString(), mediaType);
        Request request = new Request.Builder()
                .url(AZURE_OPENAI_ENDPOINT)
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader("api-key", AZURE_OPENAI_API_KEY)
                .build();

        try (
            Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.error("Unexpected response code: " + response.code());
                return null;
            }
            ResponseBody responseBody = response.body();
            String rawResponse = responseBody != null ? responseBody.string() : null;
            
            if (rawResponse == null) {
                return null;
            }
            
            // Extract content from Azure OpenAI response format
            JSONObject jsonResponse = new JSONObject(rawResponse);
            JSONArray choices = jsonResponse.getJSONArray("choices");
            JSONObject firstChoice = choices.getJSONObject(0);
            JSONObject message = firstChoice.getJSONObject("message");
            String content = message.getString("content");
            
            // Clean the content like in PromptHandler
            return cleanJSON(content);
        } catch (IOException e) {
            logger.error("Error while executing request: " + e.getMessage());
            return null;
        }
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
