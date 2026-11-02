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
import java.util.Map;

/**
 * Helper for calling the dedicated Vertex AI Gemma endpoint with a raw `predict`
 * payload (Gemma "structured_call" equivalent done client-side).
 *
 * - Uses `GEMMA_VERTEX_SA_KEY_JSON` to mint an OAuth token
 * - Retries once on Vertex `401` by invalidating the cached token
 * - Returns the full Vertex predict response JSON as-is
 */
public class GemmaVertexStructuredCallUtil {

    private static final LoggerMaker loggerMaker = new LoggerMaker(GemmaVertexStructuredCallUtil.class, LogDb.DB_ABS);

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String JSON_CONTENT_TYPE = "application/json";

    private static final TypeReference<Map<String, Object>> MAP_REF = new TypeReference<Map<String, Object>>() {};

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
}

