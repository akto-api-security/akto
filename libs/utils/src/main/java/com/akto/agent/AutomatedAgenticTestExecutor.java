package com.akto.agent;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.testing.TestResult;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.akto.util.http_util.CoreHTTPClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class AutomatedAgenticTestExecutor {

    private static final LoggerMaker loggerMaker = new LoggerMaker(AutomatedAgenticTestExecutor.class, LogDb.TESTING);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String BASE_URL = Constants.AUTOMATED_AGENT_BASE_URL;
    private static final String EXECUTE_ENDPOINT = "/execute/agentic-test";

    private static final OkHttpClient httpClient = CoreHTTPClient.client.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    public List<TestResult> executeAgenticTest(RawApi testReq, String yamlTemplateContent) {
        try {
            String body = buildRequestBody(testReq, yamlTemplateContent);
            RequestBody requestBody = RequestBody.create(body, MediaType.parse("application/json"));

            Request request = new Request.Builder()
                    .url(BASE_URL + EXECUTE_ENDPOINT)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    loggerMaker.errorAndAddToDb("AutomatedAgenticTestExecutor: request failed with status " + response.code() + ", body: " + responseBody);
                    return Collections.emptyList();
                }
                String responseBody = response.body() != null ? response.body().string() : null;
                return parseResponse(responseBody);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("AutomatedAgenticTestExecutor: error executing agentic test: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private String buildRequestBody(RawApi testReq, String yamlTemplateContent) throws Exception {
        OriginalHttpRequest req = testReq.getRequest();
        OriginalHttpResponse resp = testReq.getResponse();

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("method", req.getMethod());
        requestMap.put("url", req.getUrl());
        requestMap.put("headers", flattenHeaders(req.getHeaders()));
        requestMap.put("body", parseBodyToObject(req.getBody()));

        Map<String, Object> responseMap = new HashMap<>();
        if (resp != null) {
            responseMap.put("status_code", resp.getStatusCode());
            responseMap.put("headers", flattenHeaders(resp.getHeaders()));
            responseMap.put("body", parseBodyToObject(resp.getBody()));
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("request", requestMap);
        payload.put("response", responseMap);
        payload.put("scenario", yamlTemplateContent != null ? yamlTemplateContent : "");

        return objectMapper.writeValueAsString(payload);
    }

    /*
     * Headers in OriginalHttpRequest are Map<String, List<String>>.
     * Flatten to Map<String, String> (first value per key) for external API compatibility.
     */
    private Map<String, String> flattenHeaders(Map<String, List<String>> headers) {
        Map<String, String> flat = new HashMap<>();
        if (headers == null) return flat;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                flat.put(entry.getKey(), entry.getValue().get(0));
            }
        }
        return flat;
    }

    private Object parseBodyToObject(String body) {
        if (body == null || body.isEmpty()) return null;
        try {
            return objectMapper.readValue(body, Object.class);
        } catch (Exception e) {
            return body;
        }
    }

    /*
     * Expected response from /execute/agentic-test (array of results):
     * [
     *   {
     *     "vulnerable": true,
     *     "message": "<HTTP request-response string shown as attack evidence>",
     *     "errors": [],
     *     "percentageMatch": 0.0,
     *     "confidence": "HIGH"   // one of: CRITICAL, HIGH, MEDIUM, LOW, INFO
     *   }
     * ]
     */
    private List<TestResult> parseResponse(String responseBody) {
        List<TestResult> results = new ArrayList<>();
        if (responseBody == null || responseBody.isEmpty()) return results;
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode arr = root.isArray() ? root : (root.has("results") ? root.get("results") : null);
            if (arr == null || !arr.isArray()) return results;

            for (JsonNode node : arr) {
                TestResult tr = new TestResult();
                tr.setVulnerable(node.has("vulnerable") && node.get("vulnerable").asBoolean());
                tr.setMessage(node.has("message") ? node.get("message").asText() : null);

                List<String> errors = new ArrayList<>();
                if (node.has("errors") && node.get("errors").isArray()) {
                    for (JsonNode err : node.get("errors")) {
                        errors.add(err.asText());
                    }
                }
                tr.setErrors(errors);
                tr.setPercentageMatch(node.has("percentageMatch") ? node.get("percentageMatch").asDouble() : (tr.getVulnerable() ? 0.0 : 100.0));

                TestResult.Confidence confidence = TestResult.Confidence.HIGH;
                if (node.has("confidence")) {
                    try {
                        confidence = TestResult.Confidence.valueOf(node.get("confidence").asText().toUpperCase());
                    } catch (Exception ignored) {}
                }
                tr.setConfidence(confidence);
                tr.setResultTypeAgentic(true);
                results.add(tr);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("AutomatedAgenticTestExecutor: error parsing response: " + e.getMessage());
        }
        return results;
    }
}
