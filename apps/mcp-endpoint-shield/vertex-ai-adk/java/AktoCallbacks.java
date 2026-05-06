/**
 * Akto Guardrails callbacks for Google Vertex AI ADK.
 *
 * <p>Usage:
 * <pre>{@code
 * import com.akto.guardrails.AktoCallbacks;
 *
 * LlmAgent agent = LlmAgent.builder()
 *     .model("gemini-2.0-flash")
 *     .name("my_agent")
 *     .instruction("You are a helpful assistant.")
 *     .beforeModelCallbackSync(AktoCallbacks::aktoBeforeModelCallback)
 *     .afterModelCallbackSync(AktoCallbacks::aktoAfterModelCallback)
 *     .build();
 * }</pre>
 *
 * <p>Environment Variables:
 * <ul>
 *   <li>{@code DATA_INGESTION_URL}   - Base URL for Akto's data ingestion service (required).</li>
 *   <li>{@code SYNC_MODE}            - "true" (default) to block before the LLM call;
 *                                       "false" to validate/log asynchronously after the response.</li>
 *   <li>{@code TIMEOUT}              - HTTP request timeout in seconds (default: 5).</li>
 * </ul>
 *
 * <p>Auto-detected (Vertex AI Agent Engine injects these at runtime):
 * <ul>
 *   <li>{@code GOOGLE_CLOUD_PROJECT}           - GCP project ID.</li>
 *   <li>{@code GOOGLE_CLOUD_LOCATION}          - Region (e.g. "us-central1").</li>
 *   <li>{@code GOOGLE_CLOUD_AGENT_ENGINE_ID}   - Reasoning Engine resource ID.</li>
 * </ul>
 */
package com.akto.guardrails;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.google.adk.agents.CallbackContext;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class AktoCallbacks {

    private static final Logger logger = Logger.getLogger(AktoCallbacks.class.getName());
    private static final Gson gson = new Gson();

    // -----------------------------------------------------------------------
    // Internal types
    // -----------------------------------------------------------------------

    private static class AktoSnapshot {
        String model;
        List<Map<String, Object>> messages;
        boolean blocked;

        AktoSnapshot(String model, List<Map<String, Object>> messages, boolean blocked) {
            this.model = model;
            this.messages = messages;
            this.blocked = blocked;
        }
    }

    private static class GuardrailsResult {
        boolean allowed;
        String reason;

        GuardrailsResult(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }
    }

    // -----------------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------------

    private static final String DATA_INGESTION_URL = System.getenv("DATA_INGESTION_URL");
    private static final boolean SYNC_MODE =
            "true".equalsIgnoreCase(getEnvDefault("SYNC_MODE", "true"));
    private static final double TIMEOUT = parseDouble(getEnvDefault("TIMEOUT", "5"), 5.0);
    private static final String AKTO_CONNECTOR_NAME = "vertex-ai-adk";
    private static final String HTTP_PROXY_PATH = "/api/http-proxy";

    // State key used to pass request data from before_model_callback to after_model_callback.
    private static final String AKTO_SNAPSHOT_KEY = "__akto_request_snapshot";

    // Shared HTTP client.
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds((long) TIMEOUT))
            .build();

    static {
        logger.info("Akto Guardrails ADK callbacks initialized | sync_mode=" + SYNC_MODE);
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private static String getEnvDefault(String key, String fallback) {
        String v = System.getenv(key);
        return (v != null && !v.isEmpty()) ? v : fallback;
    }

    private static double parseDouble(String s, double fallback) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String[] getVertexEndpointInfo() {
        String project = System.getenv("GOOGLE_CLOUD_PROJECT");
        String location = System.getenv("GOOGLE_CLOUD_LOCATION");
        String engineId = System.getenv("GOOGLE_CLOUD_AGENT_ENGINE_ID");

        if (engineId != null && !engineId.isEmpty()
                && project != null && !project.isEmpty()
                && location != null && !location.isEmpty()) {
            // Agent Engine mode
            String host = location + "-aiplatform.googleapis.com";
            String path = String.format(
                    "/v1/projects/%s/locations/%s/reasoningEngines/%s:query",
                    project, location, engineId);
            return new String[]{host, path};
        }

        String kService = System.getenv("K_SERVICE");
        if (kService != null && !kService.isEmpty()) {
            // Cloud Run mode
            String serviceUrl = System.getenv("CLOUD_RUN_SERVICE_URL");
            if (serviceUrl == null) serviceUrl = "";
            serviceUrl = serviceUrl.replaceFirst("^https?://", "").replaceAll("/+$", "");
            if (serviceUrl.isEmpty()) serviceUrl = "run.googleapis.com";
            return new String[]{serviceUrl, "/"};
        }

        // Local dev fallback
        return new String[]{"generativelanguage.googleapis.com", "/v1/chat/completions"};
    }

    private static String buildQueryString(boolean guardrails, boolean ingestData) {
        StringBuilder sb = new StringBuilder();
        sb.append("akto_connector=").append(encode(AKTO_CONNECTOR_NAME));
        if (guardrails) {
            sb.append("&guardrails=true");
        }
        if (ingestData) {
            sb.append("&ingest_data=true");
        }
        return sb.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static GuardrailsResult parseGuardrailsResult(Map<String, Object> result) {
        if (result == null) {
            return new GuardrailsResult(true, "");
        }
        Object dataObj = result.get("data");
        if (!(dataObj instanceof Map)) {
            return new GuardrailsResult(true, "");
        }
        Map<String, Object> data = (Map<String, Object>) dataObj;
        Object grObj = data.get("guardrailsResult");
        if (!(grObj instanceof Map)) {
            return new GuardrailsResult(true, "");
        }
        Map<String, Object> gr = (Map<String, Object>) grObj;
        boolean allowed = true;
        if (gr.containsKey("Allowed")) {
            Object allowedObj = gr.get("Allowed");
            if (allowedObj instanceof Boolean) {
                allowed = (Boolean) allowedObj;
            }
        }
        String reason = "";
        if (gr.containsKey("Reason") && gr.get("Reason") instanceof String) {
            reason = (String) gr.get("Reason");
        }
        return new GuardrailsResult(allowed, reason);
    }

    private static List<Map<String, Object>> contentsToMessages(List<Content> contents) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (contents == null) {
            return messages;
        }
        for (Content content : contents) {
            if (content == null) {
                continue;
            }
            String role = content.role().orElse("user");
            List<Part> parts = content.parts().orElse(Collections.emptyList());
            String text = parts.stream()
                    .map(p -> p.text().orElse(""))
                    .filter(t -> !t.isEmpty())
                    .collect(Collectors.joining(" "));
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", role);
            msg.put("content", text);
            messages.add(msg);
        }
        return messages;
    }

    private static Map<String, Object> llmResponseToDict(LlmResponse llmResponse) {
        if (llmResponse == null) {
            return null;
        }
        Optional<Content> contentOpt = llmResponse.content();
        if (contentOpt.isEmpty()) {
            return null;
        }
        Content content = contentOpt.get();
        String role = content.role().orElse("model");
        List<Part> parts = content.parts().orElse(Collections.emptyList());
        String text = parts.stream()
                .map(p -> p.text().orElse(""))
                .filter(t -> !t.isEmpty())
                .collect(Collectors.joining(" "));
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", text);
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("message", message);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("choices", Collections.singletonList(choice));
        return result;
    }

    private static LlmResponse makeBlockedResponse(String reason) {
        String msg = (reason != null && !reason.isEmpty())
                ? "Blocked by Akto Guardrails: " + reason
                : "Blocked by Akto Guardrails";
        Content content = Content.builder()
                .role("model")
                .parts(Part.fromText(msg))
                .build();
        return LlmResponse.builder()
                .content(content)
                .build();
    }

    private static Map<String, Object> buildPayload(
            CallbackContext ctx,
            String model,
            List<Map<String, Object>> messages,
            Object responseBody,
            int statusCode) {

        String agentName = ctx.agentName();
        if (agentName == null || agentName.isEmpty()) {
            agentName = "unknown-agent";
        }
        String invocationId = ctx.invocationId() != null ? ctx.invocationId() : "";
        String engineId = System.getenv("GOOGLE_CLOUD_AGENT_ENGINE_ID");
        if (engineId == null || engineId.isEmpty()) {
            engineId = System.getenv("K_SERVICE");
        }
        String[] endpointInfo = getVertexEndpointInfo();
        String host = endpointInfo[0];
        String path = endpointInfo[1];
        String timestamp = String.valueOf(System.currentTimeMillis());
        String statusStr = String.valueOf(statusCode);

        Map<String, Object> modelBodyPayload = new LinkedHashMap<>();
        modelBodyPayload.put("model", model);
        modelBodyPayload.put("messages", messages);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("call_type", "completion");
        metadata.put("model", model);
        metadata.put("agent_name", agentName);
        metadata.put("invocation_id", invocationId);

        Map<String, Object> tags = new LinkedHashMap<>();
        tags.put("gen-ai", "Gen AI");
        tags.put("ai-agent", AKTO_CONNECTOR_NAME);
        tags.put("bot-name", engineId);
        tags.put("source", "VERTEX_AI");

        Map<String, Object> requestBodyWrapper = new LinkedHashMap<>();
        requestBodyWrapper.put("body", gson.toJson(modelBodyPayload));
        String requestPayload = gson.toJson(requestBodyWrapper);

        String responsePayload;
        if (responseBody == null) {
            responsePayload = gson.toJson(Collections.emptyMap());
        } else {
            Map<String, Object> responseBodyWrapper = new LinkedHashMap<>();
            responseBodyWrapper.put("body", gson.toJson(responseBody));
            responsePayload = gson.toJson(responseBodyWrapper);
        }

        Map<String, Object> reqHeaders = new LinkedHashMap<>();
        reqHeaders.put("host", host);
        reqHeaders.put("content-type", "application/json");

        Map<String, Object> respHeaders = new LinkedHashMap<>();
        respHeaders.put("content-type", "application/json");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("path", path);
        payload.put("requestHeaders", gson.toJson(reqHeaders));
        payload.put("responseHeaders", gson.toJson(respHeaders));
        payload.put("method", "POST");
        payload.put("requestPayload", requestPayload);
        payload.put("responsePayload", responsePayload);
        payload.put("ip", "0.0.0.0");
        payload.put("destIp", "127.0.0.1");
        payload.put("time", timestamp);
        payload.put("statusCode", statusStr);
        payload.put("type", "HTTP/1.1");
        payload.put("status", statusStr);
        payload.put("akto_account_id", "1000000");
        payload.put("akto_vxlan_id", "0");
        payload.put("is_pending", "false");
        payload.put("source", "MIRRORING");
        payload.put("direction", null);
        payload.put("process_id", null);
        payload.put("socket_id", null);
        payload.put("daemonset_id", null);
        payload.put("enabled_graph", null);
        payload.put("tag", gson.toJson(tags));
        payload.put("metadata", gson.toJson(metadata));
        payload.put("contextSource", "AGENTIC");

        return payload;
    }

    private static HttpResponse<String> postHTTPProxy(
            boolean guardrails, boolean ingestData, Map<String, Object> payload) throws Exception {
        String endpoint = DATA_INGESTION_URL + HTTP_PROXY_PATH;
        String queryString = buildQueryString(guardrails, ingestData);
        URI uri = URI.create(endpoint + "?" + queryString);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds((long) TIMEOUT))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static GuardrailsResult callGuardrailsValidation(
            CallbackContext ctx,
            String model,
            List<Map<String, Object>> messages) {
        if (DATA_INGESTION_URL == null || DATA_INGESTION_URL.isEmpty()) {
            return new GuardrailsResult(true, "");
        }

        Map<String, Object> payload = buildPayload(ctx, model, messages, null, 200);
        try {
            HttpResponse<String> response = postHTTPProxy(true, false, payload);
            if (response.statusCode() != 200) {
                logger.info("Guardrails validation returned HTTP " + response.statusCode()
                        + " (fail-open)");
                return new GuardrailsResult(true, "");
            }
            Map<String, Object> result = gson.fromJson(
                    response.body(),
                    new TypeToken<Map<String, Object>>() {}.getType());
            return parseGuardrailsResult(result);
        } catch (Exception e) {
            logger.log(Level.INFO, "Guardrails validation failed (fail-open): " + e.getMessage(), e);
            return new GuardrailsResult(true, "");
        }
    }

    private static void ingestResponsePayload(
            CallbackContext ctx,
            String model,
            List<Map<String, Object>> messages,
            Object responseBody,
            int statusCode,
            boolean logHTTPError) {
        if (DATA_INGESTION_URL == null || DATA_INGESTION_URL.isEmpty()) {
            return;
        }

        Map<String, Object> payload = buildPayload(ctx, model, messages, responseBody, statusCode);
        try {
            HttpResponse<String> response = postHTTPProxy(false, true, payload);
            if (logHTTPError && response.statusCode() != 200) {
                logger.severe("Ingestion failed: HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            logger.severe("Ingestion failed: " + e.getMessage());
        }
    }

    private static void asyncValidateAndIngest(
            CallbackContext ctx,
            String model,
            List<Map<String, Object>> messages,
            Map<String, Object> responseDict) {
        if (DATA_INGESTION_URL == null || DATA_INGESTION_URL.isEmpty()) {
            return;
        }

        try {
            Map<String, Object> payload = buildPayload(ctx, model, messages, responseDict, 200);
            HttpResponse<String> response = postHTTPProxy(true, true, payload);
            if (response.statusCode() == 200) {
                Map<String, Object> result = gson.fromJson(
                        response.body(),
                        new TypeToken<Map<String, Object>>() {}.getType());
                GuardrailsResult gr = parseGuardrailsResult(result);
                if (!gr.allowed) {
                    logger.info("Response flagged by guardrails (async mode, logged only): "
                            + gr.reason);
                }
            }
        } catch (Exception e) {
            logger.severe("Guardrails async validation error: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Public callback functions
    // -----------------------------------------------------------------------

    /**
     * ADK before_model_callback — integrates Akto Guardrails pre-call validation.
     *
     * <p>Matches the {@code BeforeModelCallbackSync} functional interface signature:
     * {@code Optional<LlmResponse> call(CallbackContext, LlmRequest.Builder)}.
     *
     * <p>Register via:
     * <pre>{@code
     * LlmAgent.builder()
     *     .beforeModelCallbackSync(AktoCallbacks::aktoBeforeModelCallback)
     * }</pre>
     *
     * <p>Behaviour depends on SYNC_MODE:
     *
     * <ul>
     *   <li>SYNC_MODE=true (default): Sends the request to Akto's guardrails endpoint
     *       before forwarding it to the LLM. If the request is denied, a blocking LlmResponse
     *       is returned and the LLM is never called. The blocked interaction is also ingested
     *       for observability.</li>
     *   <li>SYNC_MODE=false: Snapshots the request data in session state so that
     *       aktoAfterModelCallback can perform a combined validate-and-ingest call after the
     *       LLM responds. Returns empty so the LLM call proceeds uninterrupted.</li>
     * </ul>
     *
     * <p>Fail-open: any exception during validation allows the request through.
     */
    public static Optional<LlmResponse> aktoBeforeModelCallback(
            CallbackContext callbackContext,
            LlmRequest.Builder llmRequestBuilder) {

        // Build the request to read model and contents.
        LlmRequest llmRequest = llmRequestBuilder.build();

        String model = llmRequest.model().orElse("");
        List<Content> contents = llmRequest.contents() != null
                ? llmRequest.contents() : Collections.emptyList();
        List<Map<String, Object>> messages = contentsToMessages(contents);

        // Extract only the latest user message for guardrails validation.
        List<Map<String, Object>> latestUserMsg = new ArrayList<>();
        for (Map<String, Object> m : messages) {
            if ("user".equals(m.get("role"))) {
                latestUserMsg = new ArrayList<>(Collections.singletonList(m));
            }
        }
        if (latestUserMsg.isEmpty() && !messages.isEmpty()) {
            latestUserMsg = new ArrayList<>(
                    Collections.singletonList(messages.get(messages.size() - 1)));
        }

        // Snapshot request data for aktoAfterModelCallback.
        callbackContext.state().put(AKTO_SNAPSHOT_KEY,
                new AktoSnapshot(model, latestUserMsg, false));

        if (!SYNC_MODE) {
            return Optional.empty();
        }

        try {
            GuardrailsResult gr = callGuardrailsValidation(callbackContext, model, latestUserMsg);
            if (!gr.allowed) {
                // Ingest the blocked interaction before surfacing the error.
                Map<String, Object> blockedBody = new LinkedHashMap<>();
                blockedBody.put("x-blocked-by", "Akto Proxy");
                ingestResponsePayload(callbackContext, model, latestUserMsg, blockedBody, 403, false);

                // Mark snapshot so after_model_callback skips duplicate ingestion.
                callbackContext.state().put(AKTO_SNAPSHOT_KEY,
                        new AktoSnapshot(model, latestUserMsg, true));
                logger.info("Request blocked by Akto Guardrails: " + gr.reason);
                return Optional.of(makeBlockedResponse(gr.reason));
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Guardrails before-model error (fail-open): " + e.getMessage(), e);
        }

        return Optional.empty();
    }

    /**
     * ADK after_model_callback — ingests or validates the completed interaction.
     *
     * <p>Matches the {@code AfterModelCallbackSync} functional interface signature:
     * {@code Optional<LlmResponse> call(CallbackContext, LlmResponse)}.
     *
     * <p>Register via:
     * <pre>{@code
     * LlmAgent.builder()
     *     .afterModelCallbackSync(AktoCallbacks::aktoAfterModelCallback)
     * }</pre>
     *
     * <p>Behaviour depends on SYNC_MODE:
     *
     * <ul>
     *   <li>SYNC_MODE=true (default): Ingests the request + LLM response pair for observability.</li>
     *   <li>SYNC_MODE=false: Sends a combined validate-and-ingest request. If the response is
     *       flagged by guardrails it is logged but NOT blocked (the LLM response is already
     *       being returned to the caller).</li>
     * </ul>
     *
     * <p>Always returns empty so the original LlmResponse is used unchanged.
     */
    public static Optional<LlmResponse> aktoAfterModelCallback(
            CallbackContext callbackContext,
            LlmResponse llmResponse) {
        try {
            Object snapshotObj = callbackContext.state().get(AKTO_SNAPSHOT_KEY);
            if (!(snapshotObj instanceof AktoSnapshot)) {
                return Optional.empty();
            }
            AktoSnapshot snapshot = (AktoSnapshot) snapshotObj;

            // Skip if this request was already blocked and ingested in before_model_callback.
            if (snapshot.blocked) {
                return Optional.empty();
            }

            Map<String, Object> responseDict = llmResponseToDict(llmResponse);

            if (SYNC_MODE) {
                ingestResponsePayload(
                        callbackContext, snapshot.model, snapshot.messages,
                        responseDict, 200, true);
            } else {
                asyncValidateAndIngest(
                        callbackContext, snapshot.model, snapshot.messages, responseDict);
            }
        } catch (Exception e) {
            logger.severe("Guardrails after-model error: " + e.getMessage());
        }

        return Optional.empty();
    }
}
