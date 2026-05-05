package com.akto.action;

import com.akto.dao.agents.AgentModelDao;
import com.akto.dto.agents.Model;
import com.akto.dto.agents.ModelType;
import com.akto.dto.type.URLMethods.Method;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.http_util.CoreHTTPClient;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.Setter;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.collections.MapUtils;
import org.json.JSONObject;

@Getter
@Setter
public class LLMAction extends ActionSupport {

    private static final LoggerMaker logger = new LoggerMaker(LLMAction.class, LogDb.DB_ABS);
    private static final String JARVIS_ENDPOINT = "http://jarvis.internal.akto.io/api/generate";
    private static final Gson gson = new Gson();
    private static final OkHttpClient client = CoreHTTPClient.client.newBuilder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build();
    private static final String OLLAMA_SERVER_ENDPOINT = buildLlmServerUrl();

    // Azure Open AI urls
    private static final String AZURE_API_ENDPOINT = "%s/openai/deployments/%s/chat/completions?api-version=%s";
    private static final String AZURE_OPENAI_HOST = System.getenv().getOrDefault("AZURE_OPENAI_HOST", "");
    private static final String AZURE_OPENAI_DEPLOYMENT = System.getenv().getOrDefault("AZURE_OPENAI_DEPLOYMENT", "");
    private static final String AZURE_OPENAI_API_KEY = System.getenv().getOrDefault("AZURE_OPENAI_API_KEY", "");
    private static final String AZURE_OPENAI_API_VERSION = System.getenv().getOrDefault("AZURE_OPENAI_API_VERSION", "");


    private static String buildLlmServerUrl() {
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

    Map<String, Object> llmPayload;

    Map<String, Object> llmResponsePayload;

    public String getLLMResponse() {
        return executeLLMRequest(OLLAMA_SERVER_ENDPOINT, null, null);
    }

    public String getLLMResponseV2() {
        Map<String, String> headers = new HashMap<>();

        Model model = AgentModelDao.instance.findOne(Filters.eq("type", ModelType.MISTRAL_AI.name()));
        String endpoint = "";
        try {
            endpoint = buildAzureOpenAIUrl();
        } catch (Exception e) { logger.error(e.getMessage()); }
        String apiKey = AZURE_OPENAI_API_KEY;
        String modelNameFinal = "";
        if(model != null) {
            endpoint = model.getParams().getOrDefault("azureOpenAIEndpoint", "");
            apiKey = model.getParams().getOrDefault("apiKey", "");
            headers.put("authorization", apiKey);
            String modelName = model.getParams().getOrDefault("model", "");
            if(modelName != null && !modelName.isEmpty()){
                modelNameFinal = modelName;
            }
        }

        headers.put("api-key", apiKey);

        return executeLLMRequest(endpoint, headers, modelNameFinal);
    }

    private String executeLLMRequest(String endpoint, Map<String, String> additionalHeaders, String modelName) {
        MediaType mediaType = MediaType.parse("application/json");

        if (llmPayload == null || llmPayload.isEmpty()) {
            logger.error("LLM payload is empty or null");
            return Action.ERROR.toUpperCase();
        }

        JSONObject payload = new JSONObject(llmPayload);
        if(modelName != null && !modelName.isEmpty()){
            payload.put("model", modelName);
        }
        RequestBody body = RequestBody.create(payload.toString(), mediaType);

        Request.Builder requestBuilder = new Request.Builder()
            .url(endpoint)
            .method(Method.POST.name(), body)
            .addHeader("Content-Type", "application/json");

        // Add any additional headers
        if (MapUtils.isNotEmpty(additionalHeaders)) {
            for (Map.Entry<String, String> entry : additionalHeaders.entrySet()) {
                requestBuilder.addHeader(entry.getKey(), entry.getValue());
            }
        }

        Request request = requestBuilder.build();

        try (Response response = client.newCall(request).execute()) {
            logger.debug("llmPayload: {}", gson.toJson(llmPayload));
            if (!response.isSuccessful()) {
                logger.error("LLM Request failed with status code: {} and response: {}",
                    response.code(), response.body() != null ? response.body().string() : "null");
                return Action.ERROR.toUpperCase();
            }

            ResponseBody responseBody = response.body();
            String rawResponse = responseBody != null ? responseBody.string() : null;

            if (rawResponse == null) {
                logger.error("Response body is null");
                return Action.ERROR.toUpperCase();
            }

            llmResponsePayload = gson.fromJson(rawResponse,
                new TypeToken<Map<String, Object>>() {}.getType());
            logger.debug("LLM Response: {}", llmResponsePayload);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.error("Error while executing request: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    private static String buildAzureOpenAIUrl() {
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

        String endpoint = String.format(AZURE_API_ENDPOINT, AZURE_OPENAI_HOST, AZURE_OPENAI_DEPLOYMENT, apiVersion);

        logger.debug("Azure OpenAI endpoint: " + endpoint);
        return endpoint;
    }
}
