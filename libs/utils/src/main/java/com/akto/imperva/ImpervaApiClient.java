package com.akto.imperva;

import com.akto.imperva.model.ImpervaSchema;
import com.akto.util.http_util.CoreHTTPClient;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Client for Imperva API Security discovery inventory.
 * Docs: https://docs.imperva.com/bundle/basic-api-security/page/83093.htm
 */
public class ImpervaApiClient {

    public static final String DEFAULT_BASE_URL = "https://api.imperva.com";
    private static final String ENDPOINTS_PATH = "/api-security/v2/discovery/inventory/endpoints";
    private static final int MAX_ATTEMPTS = 3;

    private static final ObjectMapper objectMapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final OkHttpClient httpClient = CoreHTTPClient.client.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build();

    private final String baseUrl;
    private final String apiId;
    private final String apiKey;

    public ImpervaApiClient(String baseUrl, String apiId, String apiKey) {
        this.baseUrl = StringUtils.isEmpty(baseUrl) ? DEFAULT_BASE_URL : StringUtils.removeEnd(baseUrl, "/");
        this.apiId = apiId;
        this.apiKey = apiKey;
    }

    public static class EndpointSummary {
        private final long id;
        private final String hostName;
        private final String method;
        private final String resource;

        public EndpointSummary(long id, String hostName, String method, String resource) {
            this.id = id;
            this.hostName = hostName;
            this.method = method;
            this.resource = resource;
        }

        public long getId() {
            return id;
        }

        public String getHostName() {
            return hostName;
        }

        public String getMethod() {
            return method;
        }

        public String getResource() {
            return resource;
        }
    }

    /**
     * Imperva returns the full inventory in a single response.
     */
    public List<EndpointSummary> fetchAllEndpoints() throws Exception {
        JsonNode root = objectMapper.readTree(get(ENDPOINTS_PATH));
        JsonNode endpoints = root.path("data").path("endpoints");
        List<EndpointSummary> result = new ArrayList<>();
        for (JsonNode endpoint : endpoints) {
            if (!endpoint.hasNonNull("id")) continue;
            result.add(new EndpointSummary(
                endpoint.get("id").asLong(),
                endpoint.path("hostName").asText(null),
                endpoint.path("method").asText(null),
                endpoint.path("resource").asText(null)
            ));
        }
        return result;
    }

    public ImpervaSchema fetchEndpointDetails(long endpointId) throws Exception {
        JsonNode data = objectMapper.readTree(get(ENDPOINTS_PATH + "/" + endpointId)).get("data");
        if (data == null || data.isNull()) {
            throw new IOException("Missing 'data' node in Imperva endpoint details for id " + endpointId);
        }
        return objectMapper.treeToValue(data, ImpervaSchema.class);
    }

    private String get(String path) throws Exception {
        Request request = new Request.Builder()
            .url(baseUrl + path)
            .get()
            .addHeader("Content-Type", "application/json")
            .addHeader("x-API-Id", apiId)
            .addHeader("x-API-Key", apiKey)
            .build();

        // retry on network errors, 429 and 5xx; fail fast on other status codes (e.g. bad credentials)
        IOException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            int code;
            String bodyString;
            try (Response response = httpClient.newCall(request).execute()) {
                ResponseBody body = response.body();
                bodyString = body != null ? body.string() : "";
                code = response.code();
            } catch (IOException e) {
                lastError = e;
                Thread.sleep(2000L * attempt);
                continue;
            }

            if (code >= 200 && code < 300) {
                return bodyString;
            }
            lastError = new IOException("Imperva API " + path + " returned " + code + ": " + StringUtils.abbreviate(bodyString, 300));
            if (code != 429 && code < 500) {
                throw lastError;
            }
            Thread.sleep(2000L * attempt);
        }
        throw lastError;
    }
}
