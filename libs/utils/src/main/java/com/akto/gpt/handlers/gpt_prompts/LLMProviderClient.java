package com.akto.gpt.handlers.gpt_prompts;

import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONObject;

import com.akto.dto.agents.Model;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class LLMProviderClient {

    private static final LoggerMaker logger = new LoggerMaker(LLMProviderClient.class, LogDb.DASHBOARD);
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");

    /**
     * Calls the LLM provider based on model type and returns the extracted content string.
     * If model is null, falls back to Azure OpenAI env vars.
     */
    public static String callLLM(Model model, String prompt, OkHttpClient client) throws Exception {
        if (model == null) {
            logger.infoAndAddToDb("No model configured, falling back to Azure OpenAI env vars");
            return callAzureOpenAIFromEnv(prompt, client);
        }
        logger.infoAndAddToDb("Routing LLM call to provider: " + model.getType() + ", model: " + model.getName());
        switch (model.getType()) {
            case AZURE_OPENAI:
                return callAzureOpenAI(model, prompt, client);
            case DATABRICKS:
                return callDatabricks(model, prompt, client);

                // TODO: Implement other providers
            case OPENAI:
            case ANTHROPIC:
            case OLLAMA:
                throw new RuntimeException("Provider " + model.getType() + " is not yet supported for prompt handlers");
            default:
                throw new RuntimeException("Unsupported model type: " + model.getType());
        }
    }

    // --- Shared helpers ---

    static JSONObject buildChatCompletionsPayload(String prompt, String modelName) throws org.json.JSONException {
        JSONObject payload = new JSONObject();
        payload.put("temperature", PromptHandler.temperature);
        payload.put("top_p", 0.9);
        payload.put("max_tokens", PromptHandler.max_tokens);
        payload.put("frequency_penalty", 0);
        payload.put("presence_penalty", 0.6);
        payload.put("stream", false);

        if (modelName != null && !modelName.isEmpty()) {
            payload.put("model", modelName);
        }

        JSONArray messages = new JSONArray();
        JSONObject systemMessage = new JSONObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", prompt);
        messages.put(systemMessage);
        payload.put("messages", messages);

        return payload;
    }

    static JSONObject buildDatabricksPayload(String prompt, String modelName) throws org.json.JSONException {
        JSONObject payload = new JSONObject();
        payload.put("model", modelName);
        payload.put("max_tokens", PromptHandler.max_tokens);
        payload.put("stream", false);

        JSONArray messages = new JSONArray();
        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messages.put(userMessage);
        payload.put("messages", messages);

        return payload;
    }

    static String extractChatCompletionsContent(String rawResponse) throws org.json.JSONException {
        if (rawResponse == null) {
            logger.infoAndAddToDb("Received null response from LLM provider");
            return null;
        }
        JSONObject jsonResponse = new JSONObject(rawResponse);
        JSONArray choices = jsonResponse.getJSONArray("choices");
        JSONObject firstChoice = choices.getJSONObject(0);
        JSONObject message = firstChoice.getJSONObject("message");
        return message.getString("content");
    }

    private static String executeRequest(Request request, OkHttpClient client) throws IOException {
        logger.infoAndAddToDb("Sending request to: " + request.url());
        try (Response response = client.newCall(request).execute()) {
            logger.infoAndAddToDb("Received response with status: " + response.code());
            ResponseBody responseBody = response.body();
            String bodyStr = responseBody != null ? responseBody.string() : null;
            if (!response.isSuccessful()) {
                logger.error("Unexpected response code: " + response.code() + ", error body: " + bodyStr);
                return null;
            }
            return bodyStr;
        }
    }

    // --- Provider implementations ---

    private static String callAzureOpenAI(Model model, String prompt, OkHttpClient client) throws IOException, org.json.JSONException {
        String endpoint = model.getAzureEndpoint();
        String apiKey = model.getApiKey();
        String modelName = model.getModelName();

        if (endpoint == null || endpoint.isEmpty()) {
            throw new RuntimeException("Azure OpenAI endpoint is not configured in model params");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("Azure OpenAI API key is not configured in model params");
        }
        if (modelName == null || modelName.isEmpty()) {
            throw new RuntimeException("Azure OpenAI model/deployment name is not configured in model params");
        }

        String apiVersion = "2024-04-01-preview";
        String url = String.format("%s/openai/deployments/%s/chat/completions?api-version=%s",
                endpoint, modelName, apiVersion);

        logger.infoAndAddToDb("Calling Azure OpenAI, deployment: " + modelName + ", endpoint: " + endpoint);

        JSONObject payload = buildChatCompletionsPayload(prompt, null);
        RequestBody body = RequestBody.create(payload.toString(), JSON_MEDIA_TYPE);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("api-key", apiKey)
                .build();

        String rawResponse = executeRequest(request, client);
        String content = extractChatCompletionsContent(rawResponse);
        logger.infoAndAddToDb("Azure OpenAI response extracted successfully");
        return content;
    }

    private static String callDatabricks(Model model, String prompt, OkHttpClient client) throws IOException, org.json.JSONException {
        String databricksEndpoint = model.getDatabricksEndpoint();
        String apiKey = model.getApiKey();
        String modelName = model.getModelName();

        if (databricksEndpoint == null || databricksEndpoint.isEmpty()) {
            throw new RuntimeException("Databricks workspace endpoint is not configured in model params");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("Databricks API key is not configured in model params");
        }
        if (modelName == null || modelName.isEmpty()) {
            throw new RuntimeException("Databricks model name is not configured in model params");
        }

        // Remove trailing slash
        if (databricksEndpoint.endsWith("/")) {
            databricksEndpoint = databricksEndpoint.substring(0, databricksEndpoint.length() - 1);
        }

        String url = databricksEndpoint;

        logger.infoAndAddToDb("Calling Databricks, model: " + modelName + ", endpoint: " + databricksEndpoint);

        JSONObject payload = buildDatabricksPayload(prompt, modelName);
        logger.infoAndAddToDb("Databricks request payload keys: " + payload.names() + ", prompt length: " + (prompt != null ? prompt.length() : 0));
        RequestBody body = RequestBody.create(payload.toString(), JSON_MEDIA_TYPE);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();

        String rawResponse = executeRequest(request, client);
        String content = extractChatCompletionsContent(rawResponse);
        logger.infoAndAddToDb("Databricks response extracted successfully");
        return content;
    }

    // --- Env var fallback (preserves older AzureOpenAIPromptHandler behavior) ---

    static String callAzureOpenAIFromEnv(String prompt, OkHttpClient client) throws IOException, org.json.JSONException {
        String host = System.getenv("AZURE_OPENAI_HOST");
        String deployment = System.getenv("AZURE_OPENAI_DEPLOYMENT");
        String apiKey = System.getenv("AZURE_OPENAI_API_KEY");
        String apiVersion = System.getenv("AZURE_OPENAI_API_VERSION");

        if (host == null || host.isEmpty()) {
            throw new RuntimeException("AZURE_OPENAI_HOST environment variable is not set");
        }
        if (deployment == null || deployment.isEmpty()) {
            throw new RuntimeException("AZURE_OPENAI_DEPLOYMENT environment variable is not set");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("AZURE_OPENAI_API_KEY environment variable is not set");
        }

        if (apiVersion == null || apiVersion.isEmpty()) {
            apiVersion = "2024-04-01-preview";
        }

        String url = String.format("%s/openai/deployments/%s/chat/completions?api-version=%s",
                host, deployment, apiVersion);

        logger.infoAndAddToDb("Calling Azure OpenAI (env fallback), deployment: " + deployment + ", host: " + host);

        JSONObject payload = buildChatCompletionsPayload(prompt, null);
        RequestBody body = RequestBody.create(payload.toString(), JSON_MEDIA_TYPE);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("api-key", apiKey)
                .build();

        String rawResponse = executeRequest(request, client);
        String content = extractChatCompletionsContent(rawResponse);
        logger.infoAndAddToDb("Azure OpenAI (env fallback) response extracted successfully");
        return content;
    }
}
