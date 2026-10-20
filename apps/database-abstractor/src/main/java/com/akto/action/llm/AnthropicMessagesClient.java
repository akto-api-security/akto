package com.akto.action.llm;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;

import com.akto.dto.agents.Model;
import com.akto.util.http_util.CoreHTTPClient;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AnthropicMessagesClient {

    static final String URL = "https://api.anthropic.com/v1/messages";
    static final String API_VERSION = "2023-06-01";

    private static final MediaType JSON = MediaType.parse("application/json");

    // Built from the shared client on purpose: it verifies TLS, unlike the Azure one.
    private static final OkHttpClient CLIENT = CoreHTTPClient.client.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .build();

    private AnthropicMessagesClient() {
    }

    // target.getParams() is this repo's equivalent of master's target.readApiKey()/getModelName() —
    // this Model has no such convenience methods, both keys live only in its params map.
    // public (unlike master): the caller, LLMService, lives in a different package (com.akto.action,
    // not com.akto.action.llm) here, where master's AzureOpenAIPromptHandler caller is same-package.
    public static String call(Model target, String prompt, int maxTokens) throws Exception {
        Map<String, String> params = target.getParams();
        String apiKey = params != null ? params.get(Model.PARAM_API_KEY) : null;
        String modelName = params != null ? params.get(Model.PARAM_MODEL) : null;
        if (isBlank(apiKey) || isBlank(modelName)) {
            throw LlmCallException.provider("anthropic entry needs a model and an apiKey");
        }
        Request request = new Request.Builder()
                .url(URL)
                .post(RequestBody.create(buildRequest(modelName, prompt, maxTokens).toString(), JSON))
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", API_VERSION)
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) throw httpError(response.code(), body);
            return parseResponse(body);
        }
    }

    // No temperature/top_p (rejected by Haiku 5.5) and thinking off so max_tokens is all answer.
    static JSONObject buildRequest(String modelName, String prompt, int maxTokens) {
        return new JSONObject()
                .put("model", modelName)
                .put("max_tokens", maxTokens)
                .put("thinking", new JSONObject().put("type", "disabled"))
                .put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", prompt)));
    }

    static String parseResponse(String body) throws LlmCallException {
        JSONObject json;
        try {
            json = new JSONObject(body);
        } catch (Exception e) {
            throw LlmCallException.provider("unparseable response");
        }
        String stopReason = json.optString("stop_reason", "");
        if ("max_tokens".equals(stopReason) || "refusal".equals(stopReason)) {
            throw LlmCallException.request("stop_reason " + stopReason);
        }
        StringBuilder text = new StringBuilder();
        JSONArray content = json.optJSONArray("content");
        for (int i = 0; content != null && i < content.length(); i++) {
            JSONObject block = content.optJSONObject(i);
            if (block != null && "text".equals(block.optString("type"))) text.append(block.optString("text", ""));
        }
        if (text.toString().trim().isEmpty()) throw LlmCallException.request("empty content");
        return text.toString();
    }

    // Only 413 is about this input; anything else (auth, 400, 429, 5xx) is a provider problem.
    static LlmCallException httpError(int code, String body) {
        String snippet = body == null ? "" : body.substring(0, Math.min(body.length(), 200));
        String message = "HTTP " + code + " " + snippet;
        return code == 413 ? LlmCallException.request(message) : LlmCallException.provider(message);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
