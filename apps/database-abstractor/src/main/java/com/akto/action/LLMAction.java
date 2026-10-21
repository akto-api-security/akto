package com.akto.action;

import com.akto.llm.ModelTier;
import com.akto.llm.ServiceKey;
import com.akto.dto.type.URLMethods.Method;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.http_util.CoreHTTPClient;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.Setter;
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

    // V2: optional, falls back to the legacy default chain. V3: required, see getLLMResponseV3.
    // See ServiceKey.externallySelectable.
    String serviceKey;

    // V3 only
    String prompt;
    String content;

    public String getLLMResponse() {
        return executeLLMRequest(OLLAMA_SERVER_ENDPOINT, null, null);
    }

    public String getLLMResponseV2() {
        if (llmPayload == null || llmPayload.isEmpty()) {
            logger.error("LLM payload is empty or null");
            return Action.ERROR.toUpperCase();
        }

        try {
            llmResponsePayload = LLMService.callLLM(llmPayload, resolveServiceKey(), ModelTier.GPT_4O_MINI);
            if (llmResponsePayload == null) {
                logger.error("Empty LLM response");
                return Action.ERROR.toUpperCase();
            }
            logger.debug("LLM Response: {}", llmResponsePayload);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.error("Error while executing request: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    /**
     * V3: a plain prompt in, plain content out (response: {@code {"content": "..."}}). Unlike V2,
     * serviceKey is required here — V3 has no legacy caller to be backward-compatible with, so there's
     * no reason to silently fall back to the default chain for a missing/unrecognized one.
     */
    public String getLLMResponseV3() {
        if (prompt == null || prompt.trim().isEmpty()) {
            addActionError("prompt is required");
            logger.error("prompt is empty or null");
            return Action.ERROR.toUpperCase();
        }

        ServiceKey requested = ServiceKey.fromKey(serviceKey);
        if (requested == null || !requested.isExternallySelectable()) {
            addActionError("serviceKey is required and must be a valid, externally-selectable key");
            logger.error("Invalid or missing serviceKey: " + serviceKey);
            return Action.ERROR.toUpperCase();
        }

        try {
            content = LLMService.callLLMSimple(prompt, requested, ModelTier.GPT_4O_MINI);
            if (content == null) {
                logger.error("Empty LLM response");
                return Action.ERROR.toUpperCase();
            }
            logger.debug("LLM Response: {}", content);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.error("Error while executing request: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    // package-private (not private) so LLMActionTest can exercise it directly
    ServiceKey resolveServiceKey() {
        ServiceKey requested = ServiceKey.fromKey(serviceKey);
        if (requested != null && requested.isExternallySelectable()) {
            return requested;
        }
        return ServiceKey.DEFAULT;
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
}
