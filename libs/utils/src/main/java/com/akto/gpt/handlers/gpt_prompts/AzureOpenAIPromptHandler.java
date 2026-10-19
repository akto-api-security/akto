package com.akto.gpt.handlers.gpt_prompts;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.validation.ValidationException;

import com.akto.dao.agents.AgentModelDao;
import com.akto.dto.agents.Model;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.http_util.CoreHTTPClient;
import com.mongodb.BasicDBObject;
import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public abstract class AzureOpenAIPromptHandler {

    static final String DEFAULT_API_VERSION = "2024-04-01-preview";

    /** Which Azure deployment (model) a handler runs on, and the api-version it is called with,
     *  when it opts into a per-service chain (see getServiceKey()). Both come from the tier's own
     *  env vars, so repointing, renaming or re-versioning a model never needs a code change. Host
     *  and api key are shared, so each deployment must exist on that same Azure resource. */
    public enum ModelTier {
        /** gpt-4o-mini, a classic (non-reasoning) model. The long-standing AZURE_OPENAI_DEPLOYMENT /
         *  AZURE_OPENAI_API_VERSION vars; the deployment is required, no default — this is what
         *  every chain-opted handler runs on unless it asks for the nano tier. */
        GPT_4O_MINI("AZURE_OPENAI_DEPLOYMENT", "gpt-4o-mini", "AZURE_OPENAI_API_VERSION", false),
        /** gpt-5-nano, a reasoning model, always called at "minimal" reasoning effort. Treated as a
         *  reasoning model by tier, not by deployment name, so a custom deployment name still gets
         *  the reasoning request shape. Its api-version is its own var, falling back to the shared
         *  AZURE_OPENAI_API_VERSION when unset. */
        GPT_5_NANO("AZURE_OPENAI_5_NANO_DEPLOYMENT", "gpt-5-nano", "AZURE_OPENAI_5_NANO_API_VERSION", true);

        final String deployment;
        final String apiVersion;
        final boolean reasoning;

        ModelTier(String deploymentEnvVar, String defaultDeployment, String apiVersionEnvVar, boolean reasoning) {
            this.deployment = resolveDeployment(System.getenv(deploymentEnvVar), defaultDeployment);
            this.apiVersion = resolveApiVersion(System.getenv(apiVersionEnvVar), System.getenv("AZURE_OPENAI_API_VERSION"));
            this.reasoning = reasoning;
        }
    }

    /** Env value wins when set and non-blank, else the tier's default. */
    static String resolveDeployment(String envValue, String defaultDeployment) {
        return envValue != null && !envValue.trim().isEmpty() ? envValue.trim() : defaultDeployment;
    }

    /** The tier's own api-version when set, else the shared AZURE_OPENAI_API_VERSION, else the default. */
    static String resolveApiVersion(String tierEnvValue, String sharedEnvValue) {
        if (tierEnvValue != null && !tierEnvValue.trim().isEmpty()) return tierEnvValue.trim();
        if (sharedEnvValue != null && !sharedEnvValue.trim().isEmpty()) return sharedEnvValue.trim();
        return DEFAULT_API_VERSION;
    }

    /** Pure URL formatting — each tier is called with its own deployment and api-version. */
    static String chatCompletionsUrl(String host, ModelTier tier) {
        return String.format("%s/openai/deployments/%s/chat/completions?api-version=%s",
            host, tier.deployment, tier.apiVersion);
    }

    static final OkHttpClient client = CoreHTTPClient.client.newBuilder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    static final LoggerMaker logger = new LoggerMaker(AzureOpenAIPromptHandler.class, LogDb.DASHBOARD);

    // Env vars for the per-service chain's raw Azure fallback (see LlmServiceChains / callAzure).
    // Separate from LLMProviderClient's own env-var fallback, which backs the legacy (no service
    // key) path below.
    private static final String AZURE_OPENAI_HOST = System.getenv("AZURE_OPENAI_HOST");
    private static final String AZURE_OPENAI_API_KEY = System.getenv("AZURE_OPENAI_API_KEY");

    private static String getAzureOpenAIEndpoint(ModelTier tier) {
        if (AZURE_OPENAI_HOST == null || AZURE_OPENAI_HOST.isEmpty()) {
            throw new RuntimeException("AZURE_OPENAI_HOST environment variable is not set");
        }
        if (tier.deployment == null || tier.deployment.isEmpty()) {
            throw new RuntimeException("AZURE_OPENAI_DEPLOYMENT environment variable is not set");
        }
        if (AZURE_OPENAI_API_KEY == null || AZURE_OPENAI_API_KEY.isEmpty()) {
            throw new RuntimeException("AZURE_OPENAI_API_KEY environment variable is not set");
        }
        String endpoint = chatCompletionsUrl(AZURE_OPENAI_HOST, tier);
        logger.debug("Azure OpenAI endpoint: " + endpoint);
        return endpoint;
    }

    /**
     * Resolves the LLM model to use. Checks the agent_models DB collection.
     * Returns null if no model is configured in DB, which signals the caller
     * to fall back to Azure OpenAI env vars.
     */
    private static Model resolveModel() {
        try {
            Model model = AgentModelDao.instance.findFirstModel();
            if (model != null) {
                logger.info("resolveModel: found model in DB: name=" + model.getName() + ", type=" + model.getType());
            } else {
                logger.info("resolveModel: no model found in DB, will fall back to Azure OpenAI env vars");
            }
            return model;
        } catch (Exception e) {
            logger.error("resolveModel: failed to fetch model from DB: " + e.getMessage());
            return null;
        }
    }

    public BasicDBObject handle(BasicDBObject queryData) {
        String handlerName = this.getClass().getSimpleName();
        logger.info(handlerName + ".handle: starting");
        try {
            validate(queryData);
            logger.info(handlerName + ".handle: validation passed");

            String prompt = getPrompt(queryData);
            logger.info(handlerName + ".handle: prompt built, length=" + (prompt != null ? prompt.length() : 0));

            String rawResponse = call(prompt);
            logger.info(handlerName + ".handle: LLM call completed, response length=" + (rawResponse != null ? rawResponse.length() : 0));

            BasicDBObject resp = processResponse(rawResponse);
            logger.info(handlerName + ".handle: response processed successfully, keys=" + (resp != null ? resp.keySet() : "null"));
            return resp;
        } catch (ValidationException exception) {
            logger.error(handlerName + ".handle: validation failed: " + exception.getMessage());
            BasicDBObject resp = new BasicDBObject();
            resp.put("error", "Invalid input parameters.");
            return resp;
        } catch (Exception e) {
            logger.error(handlerName + ".handle: error: " + e);
            BasicDBObject resp = new BasicDBObject();
            resp.put("error", "Internal server error: " + e.getMessage());
            return resp;
        }
    }

    protected abstract void validate(BasicDBObject queryData) throws ValidationException;

    protected abstract String getPrompt(BasicDBObject queryData);

    /** Model tier this handler runs on when it opts into a per-service chain via getServiceKey().
     *  Defaults to gpt-4o-mini; override to GPT_5_NANO for cheap, closed-form work. Has no effect
     *  on handlers that don't set a service key — those keep resolving their model from
     *  AgentModelDao / env vars exactly as before. */
    protected ModelTier getModelTier() {
        return ModelTier.GPT_4O_MINI;
    }

    /** Non-null opts this handler into the per-service model chain (see LlmServiceChains), backed
     *  by the llm_service_config DB collection, instead of the legacy AgentModelDao override. */
    protected String getServiceKey() {
        return null;
    }

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
     *  invisible reasoning tokens the model spends before writing the visible answer. Fixed at
     *  "minimal" on the gpt-5-nano tier (the lowest these models accept); "low" otherwise. Ignored
     *  for non-reasoning models. A handler doing real analysis may override. */
    protected String getReasoningEffort() {
        return getModelTier() == ModelTier.GPT_5_NANO ? "minimal" : "low";
    }

    // GPT-5 / o-series ("reasoning") deployments reject the classic sampling knobs outright and
    // spend part of the token budget on invisible reasoning tokens before writing the visible
    // answer, so a small budget can silently return empty content rather than an error.
    private static boolean isReasoningModel(ModelTier tier) {
        if (tier.reasoning) return true;
        if (tier.deployment == null) return false;
        String d = tier.deployment.toLowerCase(java.util.Locale.US);
        return d.startsWith("gpt-5") || d.startsWith("o1") || d.startsWith("o3") || d.startsWith("o4");
    }

    /** The chat-completions request body for the service-chain's raw Azure fallback — pure, no
     *  network, so the reasoning-vs-classic parameter handling can be asserted directly. */
    JSONObject buildPayload(String prompt) throws org.json.JSONException {
        return buildPayload(prompt, getModelTier());
    }

    JSONObject buildPayload(String prompt, ModelTier tier) throws org.json.JSONException {
        JSONObject payload = new JSONObject();

        if (isReasoningModel(tier)) {
            payload.put("max_completion_tokens", getMaxTokens());
            payload.put("reasoning_effort", tier == ModelTier.GPT_5_NANO ? "minimal" : getReasoningEffort());
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
        return payload;
    }

    private String callTarget(Model target, String prompt) throws Exception {
        switch (target.getType()) {
            case ANTHROPIC:
                return AnthropicMessagesClient.call(target, prompt, getMaxTokens());
            case AZURE_OPENAI:
                return callAzure(LlmServiceChains.tierOf(target), prompt);
            default:
                throw LlmCallException.provider("unsupported provider " + target.getType());
        }
    }

    private String callAzure(ModelTier tier, String prompt) throws Exception {
        MediaType mediaType = MediaType.parse("application/json");
        JSONObject payload = buildPayload(prompt, tier);

        RequestBody body = RequestBody.create(payload.toString(), mediaType);
        Request request = new Request.Builder()
                .url(getAzureOpenAIEndpoint(tier))
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader("api-key", AZURE_OPENAI_API_KEY)
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
        String service = getServiceKey();
        String content;
        if (service == null) {
            Model model = resolveModel();
            logger.info("call: resolved model=" + (model != null ? model.getName() + "/" + model.getType() : "null (env var fallback)"));
            content = LLMProviderClient.callLLM(model, prompt, client, getMaxTokens(), getTemperature(), getResponseFormat());
        } else {
            content = LlmServiceChains.call(service, getModelTier(), target -> callTarget(target, prompt));
        }
        logger.info("call: raw content length=" + (content != null ? content.length() : 0));
        String cleaned = content != null ? cleanJSON(content) : null;
        logger.info("call: cleaned response length=" + (cleaned != null ? cleaned.length() : 0));
        return cleaned;
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
