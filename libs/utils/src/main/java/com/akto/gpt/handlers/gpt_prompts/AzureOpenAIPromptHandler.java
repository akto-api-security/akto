package com.akto.gpt.handlers.gpt_prompts;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.validation.ValidationException;

import com.akto.dto.agents.Model;
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
import javax.net.ssl.X509TrustManager;

public abstract class AzureOpenAIPromptHandler {

    static final String DEFAULT_API_VERSION = "2024-04-01-preview";

    /** Which Azure deployment (model) a handler runs on, and the api-version it is called with.
     *  Both come from the tier's own env vars, so repointing, renaming or re-versioning a model
     *  never needs a code change. Host and api key are shared, so each deployment must exist on
     *  that same Azure resource. */
    public enum ModelTier {
        /** gpt-4o-mini, a classic (non-reasoning) model. The long-standing AZURE_OPENAI_DEPLOYMENT /
         *  AZURE_OPENAI_API_VERSION vars; the deployment is required, no default — this is what
         *  every handler ran on before tiers existed. */
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

    /** Endpoint for the gpt-4o-mini tier — the pre-tier behaviour, kept for any external caller. */
    public static String buildAzureOpenAIUrl() {
        return buildAzureOpenAIUrl(ModelTier.GPT_4O_MINI);
    }

    public static String buildAzureOpenAIUrl(ModelTier tier) {
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

    /** Pure URL formatting — each tier is called with its own deployment and api-version. */
    static String chatCompletionsUrl(String host, ModelTier tier) {
        return String.format("%s/openai/deployments/%s/chat/completions?api-version=%s",
            host, tier.deployment, tier.apiVersion);
    }

    

    static final OkHttpClient client = CoreHTTPClient.client.newBuilder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .sslSocketFactory(CoreHTTPClient.trustAllSslSocketFactory, (X509TrustManager)CoreHTTPClient.trustAllCerts[0])
            .hostnameVerifier((hostname, session) -> true)
            .build();

    static final LoggerMaker logger = new LoggerMaker(AzureOpenAIPromptHandler.class, LogDb.DASHBOARD);
    private static volatile String AZURE_OPENAI_ENDPOINT = null;

    public static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // Environment variables for Azure OpenAI configuration
    private static final String AZURE_OPENAI_HOST = System.getenv("AZURE_OPENAI_HOST");
    private static final String AZURE_OPENAI_API_KEY = System.getenv("AZURE_OPENAI_API_KEY");

    private static String getAzureOpenAIEndpoint(ModelTier tier) {

        try {
            AZURE_OPENAI_ENDPOINT = buildAzureOpenAIUrl(tier);
            return AZURE_OPENAI_ENDPOINT;
        } catch (RuntimeException e) {
            logger.error("Failed to build Azure OpenAI endpoint: " + e.getMessage());
            throw e;
        }
    }

    public BasicDBObject handle(BasicDBObject queryData) {
        try {
            // Prompts carry captured requests (auth headers, cookies, PII), so only sizes are logged.
            validate(queryData);
            String prompt = getPrompt(queryData);
            String rawResponse = call(prompt);
            logger.info(getClass().getSimpleName() + ": prompt " + prompt.length() + " chars, response "
                + (rawResponse == null ? 0 : rawResponse.length()) + " chars");
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

    /** Model tier this handler runs on. Defaults to gpt-4o-mini, i.e. unchanged behaviour for any
     *  handler that doesn't opt in; override to GPT_5_NANO for cheap, closed-form work. */
    protected ModelTier getModelTier() {
        return ModelTier.GPT_4O_MINI;
    }

    /** Non-null opts this handler into the per-service model chain (see LlmServiceChains). */
    protected String getServiceKey() {
        return null;
    }

    // Overridable so lightweight handlers can cap output tokens / tune sampling
    // without affecting other handlers that share the global defaults.
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
     *  invisible reasoning tokens the model spends before writing the visible answer — confirmed
     *  directly against the real Azure endpoint: a pure-rendering prompt (no real judgment calls)
     *  burned an entire 4000-token budget on reasoning and returned empty content at the default
     *  effort. Fixed at "minimal" on the gpt-5-nano tier — the lowest the original gpt-5 models
     *  accept ("none" is not available there). Note "minimal" is rejected by gpt-5.1 and later,
     *  which take "none" instead, so repointing AZURE_OPENAI_5_NANO_DEPLOYMENT at one of those
     *  needs this changed. "low" on the other tier (a gpt-4o-mini var that was pointed at a
     *  reasoning model). A handler doing real analysis may override. Ignored for non-reasoning
     *  models. */
    protected String getReasoningEffort() {
        return getModelTier() == ModelTier.GPT_5_NANO ? "minimal" : "low";
    }

    // GPT-5 / o-series ("reasoning") deployments reject the classic sampling knobs outright —
    // confirmed directly against the real Azure endpoint: max_tokens -> "unsupported_parameter",
    // any non-default temperature/top_p -> "unsupported_value"/"unsupported_parameter", same for
    // frequency_penalty/presence_penalty. They also spend part of that token budget on invisible
    // reasoning tokens before writing the visible answer, so a small budget can silently return
    // empty content (finish_reason "length") rather than an error. The request sends
    // getMaxTokens() as-is, with no extra allowance for reasoning: at "minimal" effort that is
    // assumed to be ~0 tokens. If a handler's output ever gets truncated, raise that handler's
    // getMaxTokens().

    private static boolean isReasoningModel(ModelTier tier) {
        if (tier.reasoning) return true;
        // AZURE_OPENAI_DEPLOYMENT has also been pointed at gpt-5 / o-series names before; keep
        // giving those the reasoning request shape.
        if (tier.deployment == null) return false;
        String d = tier.deployment.toLowerCase(java.util.Locale.US);
        return d.startsWith("gpt-5") || d.startsWith("o1") || d.startsWith("o3") || d.startsWith("o4");
    }

    /** The chat-completions request body, split out of extractRawContent (pure, no network) so the
     *  reasoning-vs-classic parameter handling can be asserted directly. */
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

    private String extractRawContent(String prompt) throws Exception {
        String service = getServiceKey();
        if (service == null) return callAzure(getModelTier(), prompt);
        return LlmServiceChains.call(service, getModelTier(), target -> callTarget(target, prompt));
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
        String apiKey = AZURE_OPENAI_API_KEY;
        Request request = new Request.Builder()
                .url(getAzureOpenAIEndpoint(tier))
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader("api-key", apiKey)
                .addHeader("x-context-source", "AGENTIC")
                .addHeader("authorization", apiKey)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                ResponseBody errorBody = response.body();
                logger.error("Unexpected response code: " + response.code()
                        + (errorBody != null ? " body: " + errorBody.string() : ""));
                return null;
            }
            ResponseBody responseBody = response.body();
            String rawResponse = responseBody != null ? responseBody.string() : null;
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
        return cleanJSON(extractRawContent(prompt));
    }

    protected String callForArray(String prompt) throws Exception {
        return cleanJSONArray(extractRawContent(prompt));
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

    static String cleanJSONArray(String rawResponse) {
        if (rawResponse == null || rawResponse.isEmpty()) {
            return "NOT_FOUND";
        }
        int lastBracket = rawResponse.lastIndexOf(']');
        if (lastBracket != -1) {
            rawResponse = rawResponse.substring(0, lastBracket + 1);
        }
        int firstBracket = rawResponse.indexOf('[');
        if (firstBracket != -1) {
            rawResponse = rawResponse.substring(firstBracket);
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