package com.akto.action;

import com.akto.llm.AnthropicMessagesClient;
import com.akto.llm.LlmCallException;
import com.akto.llm.LlmServiceChains;
import com.akto.llm.ModelTier;
import com.akto.llm.ServiceKey;
import com.akto.dao.agents.AgentModelDao;
import com.akto.dto.agents.Model;
import com.akto.dto.agents.ModelType;
import com.akto.dto.type.URLMethods.Method;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.http_util.CoreHTTPClient;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.Collections;
import java.util.HashMap;
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
import org.apache.commons.collections.MapUtils;

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

    private static final String AZURE_OPENAI_HOST = System.getenv().getOrDefault("AZURE_OPENAI_HOST", "");
    private static final String AZURE_OPENAI_API_KEY = System.getenv().getOrDefault("AZURE_OPENAI_API_KEY", "");

    private LLMService() {}

    /**
     * Calls an LLM for the given service with the given OpenAI-chat-completions-shaped payload
     * ({@code messages}, {@code max_tokens}, {@code temperature}, ...) and returns a response Map
     * shaped the same way ({@code choices[0].message.content}), regardless of which provider actually
     * served it. The Azure dispatch forwards {@code llmPayload} (and whatever {@code max_tokens} the
     * caller put in it) unchanged, as it always has; the Anthropic dispatch has no payload to read a
     * token budget from (its request shape has no such field), so it uses
     * {@code serviceKey.getMaxTokens()} instead.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>If the account has a {@code ModelType.MISTRAL_AI} {@link Model} configured in
     *       {@link AgentModelDao}, that model's own endpoint/key is called directly — no fallback,
     *       same behavior as before this method took a serviceKey/tier.</li>
     *   <li>Otherwise, {@code serviceKey} is resolved through {@link LlmServiceChains}: an
     *       admin-configured ordered chain (falling back to the env-configured Azure {@code tier})
     *       with a circuit breaker across entries.</li>
     * </ol>
     *
     * @throws Exception if the request fails or the response cannot be parsed
     */
    public static Map<String, Object> callLLM(Map<String, Object> llmPayload, ServiceKey serviceKey, ModelTier tier) throws Exception {
        if (llmPayload == null || llmPayload.isEmpty()) {
            throw new IllegalArgumentException("LLM payload is empty or null");
        }

        Model accountModel = AgentModelDao.instance.findOne(AgentModelDao.instance.filterByType(ModelType.MISTRAL_AI));
        if (accountModel != null) {
            return callAccountOverride(accountModel, llmPayload);
        }

        String content = LlmServiceChains.call(serviceKey.getKey(), tier, target -> dispatch(target, llmPayload, serviceKey.getMaxTokens()));
        return wrapAsChoices(content);
    }

    /**
     * V3: a plain prompt in, plain content out — no OpenAI-shaped payload for the caller to build,
     * no choices/message wrapper in the response. Builds the one-message payload internally from
     * {@code serviceKey}'s own temperature/max_tokens (the caller has no say over either). Unlike
     * {@link #callLLM}, never wraps the chain path's result into {@code choices} shape just to
     * unwrap it again here — the chain already returns a plain string, so that's returned directly.
     * The account-override path extracts once, since that Map is Azure's raw response, not one we
     * built ourselves.
     *
     * @throws Exception if the request fails or the response cannot be parsed
     */
    public static String callLLMSimple(String prompt, ServiceKey serviceKey, ModelTier tier) throws Exception {
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);
        Map<String, Object> payload = new HashMap<>();
        payload.put("messages", Collections.singletonList(message));
        payload.put("temperature", serviceKey.getTemperature());
        payload.put("max_tokens", serviceKey.getMaxTokens());

        Model accountModel = AgentModelDao.instance.findOne(AgentModelDao.instance.filterByType(ModelType.MISTRAL_AI));
        if (accountModel != null) {
            return extractContent(callAccountOverride(accountModel, payload));
        }

        return LlmServiceChains.call(serviceKey.getKey(), tier, target -> dispatch(target, payload, serviceKey.getMaxTokens()));
    }

    private static String dispatch(Model target, Map<String, Object> llmPayload, int maxTokens) throws Exception {
        switch (target.getType()) {
            case ANTHROPIC:
                return AnthropicMessagesClient.call(target, extractPrompt(llmPayload), maxTokens);
            case AZURE_OPENAI:
                return callAzureTier(target, llmPayload);
            default:
                throw LlmCallException.provider("unsupported provider " + target.getType());
        }
    }

    // AnthropicMessagesClient.call takes a plain prompt (same as master's version); every caller of
    // this class builds llmPayload with exactly one user message, so that's the prompt.
    @SuppressWarnings("unchecked")
    // package-private (not private) so LLMServiceTest can exercise it directly
    static String extractPrompt(Map<String, Object> llmPayload) {
        Object rawMessages = llmPayload.get("messages");
        if (!(rawMessages instanceof List) || ((List<?>) rawMessages).isEmpty()) {
            throw new IllegalArgumentException("llmPayload has no messages");
        }
        Object firstMessage = ((List<?>) rawMessages).get(0);
        if (!(firstMessage instanceof Map)) {
            throw new IllegalArgumentException("llmPayload message is not an object");
        }
        Object content = ((Map<String, Object>) firstMessage).get("content");
        return content == null ? null : content.toString();
    }

    // The account's own configured endpoint/key — called directly, no chain, no breaker. Returns the
    // raw provider response map as-is (unchanged behavior from before this method took a serviceKey).
    private static Map<String, Object> callAccountOverride(Model model, Map<String, Object> llmPayload) throws Exception {
        Map<String, String> params = model.getParams();
        String endpoint = params != null ? params.getOrDefault(Model.PARAM_AZURE_OPENAI_ENDPOINT, "") : "";
        String apiKey = params != null ? params.getOrDefault(Model.PARAM_API_KEY, "") : "";
        String modelNameFinal = params != null ? params.getOrDefault(Model.PARAM_MODEL, "") : "";

        Map<String, String> headers = new HashMap<>();
        if (endpoint.contains("api.openai.com")) {
            headers.put("Authorization", "Bearer " + apiKey);
        } else {
            headers.put("api-key", apiKey);
            headers.put("authorization", apiKey);
        }

        return executeRequest(endpoint, headers, modelNameFinal, llmPayload);
    }

    // The caller's own env-tier Azure deployment — used both as the sole default chain entry and for
    // any admin-configured AZURE_OPENAI chain entry. Returns just the extracted content string (not
    // the raw map) so it fits LlmServiceChains' Call contract; null signals a failed attempt so the
    // circuit breaker can skip this entry for a while.
    private static String callAzureTier(Model target, Map<String, Object> llmPayload) {
        ModelTier tier = LlmServiceChains.tierOf(target);
        if (tier == null) return null;
        try {
            String endpoint = tier.buildAzureOpenAIUrl(AZURE_OPENAI_HOST);
            Map<String, String> headers = new HashMap<>();
            headers.put("api-key", AZURE_OPENAI_API_KEY);
            headers.put("authorization", AZURE_OPENAI_API_KEY);
            Map<String, Object> raw = executeRequest(endpoint, headers, null, llmPayload);
            return extractContent(raw);
        } catch (Exception e) {
            logger.error("Azure tier call failed: " + e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    // package-private (not private) so LLMServiceTest can exercise it directly
    static String extractContent(Map<String, Object> raw) {
        if (raw == null) return null;
        Object choicesObj = raw.get("choices");
        if (!(choicesObj instanceof List) || ((List<?>) choicesObj).isEmpty()) return null;
        Object firstChoice = ((List<?>) choicesObj).get(0);
        if (!(firstChoice instanceof Map)) return null;
        Object messageObj = ((Map<String, Object>) firstChoice).get("message");
        if (!(messageObj instanceof Map)) return null;
        Object content = ((Map<String, Object>) messageObj).get("content");
        return content == null ? null : content.toString();
    }

    // package-private (not private) so LLMServiceTest can exercise it directly
    static Map<String, Object> wrapAsChoices(String content) {
        if (content == null) return null;
        Map<String, Object> message = new HashMap<>();
        message.put("role", "assistant");
        message.put("content", content);
        Map<String, Object> choice = new HashMap<>();
        choice.put("message", message);
        Map<String, Object> response = new HashMap<>();
        response.put("choices", Collections.singletonList(choice));
        return response;
    }

    private static Map<String, Object> executeRequest(String endpoint, Map<String, String> extraHeaders,
                                                       String modelName, Map<String, Object> llmPayload) throws Exception {
        Map<String, Object> payloadMap = new LinkedHashMap<>(llmPayload);
        if (modelName != null && !modelName.isEmpty()) {
            payloadMap.put("model", modelName);
        }
        String payloadJson = gson.toJson(payloadMap);
        RequestBody body = RequestBody.create(payloadJson, MediaType.parse("application/json"));

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
            Map<String, Object> result = gson.fromJson(raw, new TypeToken<Map<String, Object>>(){}.getType());
            logger.debug("LLM response: {}", result);
            return result;
        }
    }
}
