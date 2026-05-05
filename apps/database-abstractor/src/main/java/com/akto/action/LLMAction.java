package com.akto.action;

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

    public String getLLMResponse() {
        return executeLLMRequest(OLLAMA_SERVER_ENDPOINT, null, null);
    }

    public String getLLMResponseV2() {
        try {
            llmResponsePayload = LLMService.callLLM(llmPayload);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.error("getLLMResponseV2 failed: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    private String executeLLMRequest(String endpoint, Map<String, String> additionalHeaders, String modelName) {
        if (llmPayload == null || llmPayload.isEmpty()) {
            logger.error("LLM payload is empty or null");
            return Action.ERROR.toUpperCase();
        }

        JSONObject payload = new JSONObject(llmPayload);
        if (modelName != null && !modelName.isEmpty()) {
            payload.put("model", modelName);
        }
        RequestBody body = RequestBody.create(payload.toString(), MediaType.parse("application/json"));

        Request.Builder requestBuilder = new Request.Builder()
            .url(endpoint)
            .method(Method.POST.name(), body)
            .addHeader("Content-Type", "application/json");

        if (MapUtils.isNotEmpty(additionalHeaders)) {
            for (Map.Entry<String, String> entry : additionalHeaders.entrySet()) {
                requestBuilder.addHeader(entry.getKey(), entry.getValue());
            }
        }

        try (Response response = client.newCall(requestBuilder.build()).execute()) {
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
            llmResponsePayload = gson.fromJson(rawResponse, new TypeToken<Map<String, Object>>() {}.getType());
            logger.debug("LLM Response: {}", llmResponsePayload);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.error("Error while executing request: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }
}
