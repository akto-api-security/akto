package com.akto.utils;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.http_util.CoreHTTPClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vertex AI dedicated endpoint structured-call helper (Gemma "structured_call" equivalent).
 *
 * Mirrors the python reference behavior:
 * - schema -> example JSON payload
 * - POST Vertex predict
 * - parse fenced ```json ... ``` from model output
 * - on 401: invalidate cached token + retry once
 */
public class GemmaVertexStructuredCallUtil {

    private static final LoggerMaker loggerMaker = new LoggerMaker(GemmaVertexStructuredCallUtil.class, LogDb.DB_ABS);

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String JSON_CONTENT_TYPE = "application/json";

    private static final int MAX_TOKENS = 1024;
    private static final double TEMPERATURE = 0.0;

    private static final Pattern JSON_FENCE_RE = Pattern.compile("```(?:json)?\\s*(.*?)\\s*```", Pattern.DOTALL);

    private static final TypeReference<Map<String, Object>> MAP_REF = new TypeReference<Map<String, Object>>() {};

    public static Map<String, Object> structuredCall(
            String serviceAccountKeyJson,
            String system,
            String user,
            Map<String, Object> schema
    ) throws Exception {
        String vertexDns = System.getenv().getOrDefault("GEMMA_VERTEX_DEDICATED_DNS", "");
        String vertexProject = System.getenv().getOrDefault("GEMMA_VERTEX_PROJECT", "");
        String vertexLocation = System.getenv().getOrDefault("GEMMA_VERTEX_LOCATION", "");
        String vertexEndpointId = System.getenv().getOrDefault("GEMMA_VERTEX_ENDPOINT_ID", "");

        if (vertexDns.isEmpty() || vertexProject.isEmpty() || vertexLocation.isEmpty() || vertexEndpointId.isEmpty()) {
            throw new IllegalArgumentException("Missing one of GEMMA_VERTEX_DEDICATED_DNS/PROJECT/LOCATION/ENDPOINT_ID env vars");
        }
        if (serviceAccountKeyJson == null || serviceAccountKeyJson.isEmpty()) {
            throw new IllegalArgumentException("Missing GEMMA_VERTEX_SA_KEY_JSON env var");
        }
        if (schema == null) {
            throw new IllegalArgumentException("Missing request field: schema");
        }

        Object example = exampleFromSchema(schema);
        String userWithSchema = user + "\n\nRespond ONLY with valid JSON in exactly this shape (this is an example — replace every placeholder with your real answer):"
                + "\n" + safeJsonStringify(example);

        Map<String, Object> payload = new HashMap<>();
        List<Object> instances = new ArrayList<>();

        Map<String, Object> instance = new HashMap<>();
        instance.put("@requestFormat", "chatCompletions");

        List<Object> messages = new ArrayList<>();
        Map<String, Object> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", system);
        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userWithSchema);
        messages.add(systemMsg);
        messages.add(userMsg);

        instance.put("messages", messages);
        instance.put("max_tokens", MAX_TOKENS);
        instance.put("temperature", TEMPERATURE);
        instances.add(instance);

        payload.put("instances", instances);

        String predictUrl = "https://" + vertexDns + "/v1/projects/" + vertexProject
                + "/locations/" + vertexLocation + "/endpoints/" + vertexEndpointId + ":predict";

        // Retry once on 401 by invalidating the cached token for this SA.
        Exception lastError = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String token = GcpVertexAuthUtil.getAccessToken(serviceAccountKeyJson);

                RequestBody body = RequestBody.create(
                        objectMapper.writeValueAsString(payload),
                        MediaType.parse(JSON_CONTENT_TYPE)
                );
                Request request = new Request.Builder()
                        .url(predictUrl)
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", JSON_CONTENT_TYPE)
                        .header("Accept-Encoding", "identity")
                        .post(body)
                        .build();

                try (Response resp = CoreHTTPClient.client.newCall(request).execute()) {
                    if (resp == null) {
                        throw new RuntimeException("Vertex predict returned null response");
                    }

                    int status = resp.code();
                    String respBody = resp.body() == null ? "" : resp.body().string();

                    if (status == 401 && attempt == 1) {
                        // Token looks invalid: clear cache and retry once.
                        GcpVertexAuthUtil.invalidateCachedToken(serviceAccountKeyJson);
                        continue;
                    }

                    if (status < 200 || status >= 300) {
                        throw new RuntimeException("Vertex predict failed: status=" + status + " body=" + respBody);
                    }

                    Map<String, Object> data = objectMapper.readValue(respBody, MAP_REF);
                    String content = extractMessageContent(data);
                    if (content == null || content.trim().isEmpty()) {
                        throw new RuntimeException("Could not find message content in Vertex response");
                    }

                    return parseJsonFromModelOutput(content);
                }
            } catch (Exception e) {
                lastError = e;
                loggerMaker.errorAndAddToDb(e, "Vertex structuredCall attempt " + attempt + " failed: " + e);
                if (attempt == 2) {
                    throw e;
                }
            }
        }

        throw lastError != null ? lastError : new RuntimeException("Vertex structuredCall failed");
    }

    /**
     * Raw Vertex predict payload path.
     *
     * Caller supplies the full `predict` JSON body (at least `instances`),
     * so we do not do any schema->example prompting or message rewriting.
     */
    public static Map<String, Object> predictAndParseVertexPayload(
            String serviceAccountKeyJson,
            Map<String, Object> vertexPredictPayload
    ) throws Exception {
        if (vertexPredictPayload == null || vertexPredictPayload.isEmpty()) {
            throw new IllegalArgumentException("Missing request field: instances");
        }

        String vertexDns = System.getenv().getOrDefault("GEMMA_VERTEX_DEDICATED_DNS", "");
        String vertexProject = System.getenv().getOrDefault("GEMMA_VERTEX_PROJECT", "");
        String vertexLocation = System.getenv().getOrDefault("GEMMA_VERTEX_LOCATION", "");
        String vertexEndpointId = System.getenv().getOrDefault("GEMMA_VERTEX_ENDPOINT_ID", "");

        if (vertexDns.isEmpty() || vertexProject.isEmpty() || vertexLocation.isEmpty() || vertexEndpointId.isEmpty()) {
            throw new IllegalArgumentException("Missing one of GEMMA_VERTEX_DEDICATED_DNS/PROJECT/LOCATION/ENDPOINT_ID env vars");
        }
        if (serviceAccountKeyJson == null || serviceAccountKeyJson.isEmpty()) {
            throw new IllegalArgumentException("Missing GEMMA_VERTEX_SA_KEY_JSON env var");
        }

        String predictUrl = "https://" + vertexDns + "/v1/projects/" + vertexProject
                + "/locations/" + vertexLocation + "/endpoints/" + vertexEndpointId + ":predict";

        Exception lastError = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String token = GcpVertexAuthUtil.getAccessToken(serviceAccountKeyJson);

                RequestBody body = RequestBody.create(
                        objectMapper.writeValueAsString(vertexPredictPayload),
                        MediaType.parse(JSON_CONTENT_TYPE)
                );
                Request request = new Request.Builder()
                        .url(predictUrl)
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", JSON_CONTENT_TYPE)
                        .header("Accept-Encoding", "identity")
                        .post(body)
                        .build();

                try (Response resp = CoreHTTPClient.client.newCall(request).execute()) {
                    if (resp == null) {
                        throw new RuntimeException("Vertex predict returned null response");
                    }

                    int status = resp.code();
                    String respBody = resp.body() == null ? "" : resp.body().string();

                    if (status == 401 && attempt == 1) {
                        GcpVertexAuthUtil.invalidateCachedToken(serviceAccountKeyJson);
                        continue;
                    }

                    if (status < 200 || status >= 300) {
                        throw new RuntimeException("Vertex predict failed: status=" + status + " body=" + respBody);
                    }

                    // For `/api/getGemmaResponse`, return the full Vertex predict response.
                    // No parsing/extracting from `predictions.*.choices[0].message.content`.
                    return objectMapper.readValue(respBody, MAP_REF);
                }
            } catch (Exception e) {
                lastError = e;
                loggerMaker.errorAndAddToDb(e, "Vertex raw predict attempt " + attempt + " failed: " + e);
                if (attempt == 2) {
                    throw e;
                }
            }
        }

        throw lastError != null ? lastError : new RuntimeException("Vertex raw predict failed");
    }

    private static Object exampleFromSchema(Map<String, Object> schema) {
        Map<String, Object> defs = schema.get("$defs") instanceof Map
                ? (Map<String, Object>) schema.get("$defs")
                : new HashMap<>();
        return resolveNode(schema, defs);
    }

    private static Object resolveNode(Object nodeObj, Map<String, Object> defs) {
        if (!(nodeObj instanceof Map)) {
            return "<string>";
        }
        Map<String, Object> node = (Map<String, Object>) nodeObj;

        if (node.containsKey("$ref")) {
            Object refRaw = node.get("$ref");
            if (refRaw instanceof String) {
                String ref = (String) refRaw;
                String refName = ref.contains("/") ? ref.substring(ref.lastIndexOf('/') + 1) : ref;
                Object resolved = defs.get(refName);
                return resolveNode(resolved, defs);
            }
        }

        if (node.containsKey("enum")) {
            Object enumRaw = node.get("enum");
            if (enumRaw instanceof List) {
                List<?> values = (List<?>) enumRaw;
                // Java 8 compatibility: Stream#toList() isn't available.
                String joined = String.join(" | ", values.stream().map(String::valueOf).collect(java.util.stream.Collectors.toList()));
                return "<one of: " + joined + ">";
            }
        }

        String type = node.get("type") instanceof String ? (String) node.get("type") : null;
        if ("object".equals(type) || node.containsKey("properties")) {
            Object propsRaw = node.get("properties");
            Map<String, Object> props = propsRaw instanceof Map ? (Map<String, Object>) propsRaw : new HashMap<>();
            Map<String, Object> out = new HashMap<>();
            for (Map.Entry<String, Object> e : props.entrySet()) {
                out.put(e.getKey(), resolveNode(e.getValue(), defs));
            }
            return out;
        }
        if ("array".equals(type) || node.containsKey("items")) {
            Object itemsRaw = node.get("items");
            Object exampleItem = resolveNode(itemsRaw, defs);
            List<Object> out = new ArrayList<>();
            out.add(exampleItem);
            return out;
        }
        if ("boolean".equals(type)) {
            return true;
        }
        if ("integer".equals(type)) {
            return 0;
        }
        if ("number".equals(type)) {
            return 0.0;
        }

        return "<string>";
    }

    private static Map<String, Object> parseJsonFromModelOutput(String content) throws Exception {
        Matcher matcher = JSON_FENCE_RE.matcher(content);
        String extracted = matcher.find() ? matcher.group(1) : content;
        extracted = extracted.trim();
        // Best-effort JSON object parse.
        try {
            return objectMapper.readValue(extracted, MAP_REF);
        } catch (Exception primary) {
            // Fallback: try to parse the first {...} JSON object inside the text.
            int firstBrace = content.indexOf('{');
            int lastBrace = content.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                String candidate = content.substring(firstBrace, lastBrace + 1).trim();
                try {
                    return objectMapper.readValue(candidate, MAP_REF);
                } catch (Exception ignored) {
                    // continue to raw fallback
                }
            }

            // Last resort: return the whole model output instead of failing the API call.
            Map<String, Object> raw = new HashMap<>();
            raw.put("raw", content);
            return raw;
        }
    }

    private static String extractMessageContent(Map<String, Object> data) {
        Object predictions = data.get("predictions");
        List<Map<String, Object>> candidates = new ArrayList<>();

        if (predictions instanceof Map) {
            candidates.add((Map<String, Object>) predictions);
        } else if (predictions instanceof List && !((List<?>) predictions).isEmpty()) {
            Object first = ((List<?>) predictions).get(0);
            if (first instanceof Map) {
                candidates.add((Map<String, Object>) first);
            }
        }

        candidates.add(data);

        for (Map<String, Object> candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            Object choices = candidate.get("choices");
            if (choices instanceof List && !((List<?>) choices).isEmpty()) {
                Object firstChoice = ((List<?>) choices).get(0);
                if (firstChoice instanceof Map) {
                    Object message = ((Map<?, ?>) firstChoice).get("message");
                    if (message instanceof Map) {
                        Object content = ((Map<?, ?>) message).get("content");
                        if (content instanceof String && !((String) content).trim().isEmpty()) {
                            return (String) content;
                        }
                    }
                }
            }
        }

        return null;
    }

    private static String safeJsonStringify(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}

