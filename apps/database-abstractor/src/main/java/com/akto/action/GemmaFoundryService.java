package com.akto.action;

import com.akto.dto.type.URLMethods.Method;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.http_util.CoreHTTPClient;
import com.google.gson.Gson;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Azure AI Foundry chat/completions client for Gemma (getGemmaResponse).
 */
public class GemmaFoundryService {

    private static final LoggerMaker logger = new LoggerMaker(GemmaFoundryService.class, LogDb.DB_ABS);
    private static final Gson gson = new Gson();
    private static final OkHttpClient client = CoreHTTPClient.client.newBuilder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    private static final String FOUNDRY_CHAT_URL =
            "https://guardrails-model-centra-resource.services.ai.azure.com/openai/v1/chat/completions";
    private static final int MAX_TOKENS = 512;
    private static final double TEMPERATURE = 0.1;

    private static final String GEMMA_FOUNDRY_API_KEY = System.getenv().getOrDefault("GEMMA_FOUNDRY_API_KEY", "");
    private static final String GEMMA_FOUNDRY_MODEL = System.getenv().getOrDefault("GEMMA_FOUNDRY_MODEL", "");

    private GemmaFoundryService() {}

    /**
     * Calls Azure Foundry chat/completions with the given messages.
     *
     * @throws Exception if config is missing or the request fails
     */
    public static Map<String, Object> callGemma(List<?> messages) throws Exception {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages is empty or null");
        }
        if (GEMMA_FOUNDRY_API_KEY == null || GEMMA_FOUNDRY_API_KEY.isEmpty()) {
            throw new IllegalArgumentException("GEMMA_FOUNDRY_API_KEY not set");
        }
        if (GEMMA_FOUNDRY_MODEL == null || GEMMA_FOUNDRY_MODEL.isEmpty()) {
            throw new IllegalArgumentException("GEMMA_FOUNDRY_MODEL not set");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", GEMMA_FOUNDRY_MODEL);
        payload.put("messages", messages);
        payload.put("max_tokens", MAX_TOKENS);
        payload.put("temperature", TEMPERATURE);

        String payloadJson = gson.toJson(payload);
        RequestBody body = RequestBody.create(payloadJson, MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url(FOUNDRY_CHAT_URL)
                .method(Method.POST.name(), body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + GEMMA_FOUNDRY_API_KEY)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "null";
                throw new RuntimeException("Gemma Foundry request failed: status=" + response.code() + " body=" + errBody);
            }
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new RuntimeException("Gemma Foundry response body is null");
            }
            String raw = responseBody.string();
            Map<String, Object> result = gson.fromJson(raw, new com.google.gson.reflect.TypeToken<Map<String, Object>>(){}.getType());
            logger.debug("Gemma Foundry response: {}", result);
            return result;
        }
    }
}
