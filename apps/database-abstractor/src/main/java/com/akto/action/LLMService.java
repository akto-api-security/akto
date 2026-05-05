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
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.collections.MapUtils;
import org.json.JSONObject;

/**
 * Shared LLM invocation logic consumed by LLMAction and SkillValidationAction.
 */
public class LLMService {

    private static final LoggerMaker logger = new LoggerMaker(LLMService.class, LogDb.DB_ABS);
    private static final Gson gson = new Gson();
    private static final OkHttpClient client = CoreHTTPClient.client.newBuilder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    private static final String AZURE_API_ENDPOINT = "%s/openai/deployments/%s/chat/completions?api-version=%s";
    private static final String AZURE_OPENAI_HOST = System.getenv().getOrDefault("AZURE_OPENAI_HOST", "");
    private static final String AZURE_OPENAI_DEPLOYMENT = System.getenv().getOrDefault("AZURE_OPENAI_DEPLOYMENT", "");
    private static final String AZURE_OPENAI_API_KEY = System.getenv().getOrDefault("AZURE_OPENAI_API_KEY", "");
    private static final String AZURE_OPENAI_API_VERSION = System.getenv().getOrDefault("AZURE_OPENAI_API_VERSION", "");

    private LLMService() {}

    /**
     * Calls the configured LLM (Azure OpenAI or MISTRAL_AI override from AgentModelDao)
     * with the given payload and returns the parsed response map.
     *
     * @throws Exception if the request fails or the response cannot be parsed
     */
    public static Map<String, Object> callLLM(Map<String, Object> llmPayload) throws Exception {
        if (llmPayload == null || llmPayload.isEmpty()) {
            throw new IllegalArgumentException("LLM payload is empty or null");
        }

        Map<String, String> headers = new java.util.HashMap<>();
        String endpoint;
        String apiKey = AZURE_OPENAI_API_KEY;
        String modelNameFinal = "";

        Model model = AgentModelDao.instance.findOne(Filters.eq("type", ModelType.MISTRAL_AI.name()));
        if (model != null) {
            endpoint = model.getParams().getOrDefault("azureOpenAIEndpoint", "");
            apiKey = model.getParams().getOrDefault("apiKey", "");
            headers.put("authorization", apiKey);
            String modelName = model.getParams().getOrDefault("model", "");
            if (modelName != null && !modelName.isEmpty()) {
                modelNameFinal = modelName;
            }
        } else {
            endpoint = buildAzureOpenAIUrl();
        }

        headers.put("api-key", apiKey);

        return executeRequest(endpoint, headers, modelNameFinal, llmPayload);
    }

    private static Map<String, Object> executeRequest(String endpoint, Map<String, String> extraHeaders,
                                                       String modelName, Map<String, Object> llmPayload) throws Exception {
        JSONObject payload = new JSONObject(llmPayload);
        if (modelName != null && !modelName.isEmpty()) {
            payload.put("model", modelName);
        }
        RequestBody body = RequestBody.create(payload.toString(), MediaType.parse("application/json"));

        Request.Builder builder = new Request.Builder()
                .url(endpoint)
                .method(Method.POST.name(), body)
                .addHeader("Content-Type", "application/json");

        if (MapUtils.isNotEmpty(extraHeaders)) {
            for (Map.Entry<String, String> e : extraHeaders.entrySet()) {
                builder.addHeader(e.getKey(), e.getValue());
            }
        }

        try (Response response = client.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "null";
                throw new RuntimeException("LLM request failed: status=" + response.code() + " body=" + errBody);
            }
            ResponseBody responseBody = response.body();
            if (responseBody == null) throw new RuntimeException("LLM response body is null");
            String raw = responseBody.string();
            Map<String, Object> result = gson.fromJson(raw, new com.google.gson.reflect.TypeToken<Map<String, Object>>() {}.getType());
            logger.debug("LLM response: {}", result);
            return result;
        }
    }

    private static String buildAzureOpenAIUrl() {
        if (AZURE_OPENAI_HOST == null || AZURE_OPENAI_HOST.isEmpty()) {
            throw new RuntimeException("AZURE_OPENAI_HOST not set");
        }
        if (AZURE_OPENAI_DEPLOYMENT == null || AZURE_OPENAI_DEPLOYMENT.isEmpty()) {
            throw new RuntimeException("AZURE_OPENAI_DEPLOYMENT not set");
        }
        String apiVersion = (AZURE_OPENAI_API_VERSION != null && !AZURE_OPENAI_API_VERSION.isEmpty())
                ? AZURE_OPENAI_API_VERSION : "2024-04-01-preview";
        return String.format(AZURE_API_ENDPOINT, AZURE_OPENAI_HOST, AZURE_OPENAI_DEPLOYMENT, apiVersion);
    }
}
