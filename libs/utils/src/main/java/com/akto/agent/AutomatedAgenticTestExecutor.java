package com.akto.agent;

import com.akto.data_actor.ClientActor;
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
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import okhttp3.*;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.akto.runtime.utils.Utils.convertOriginalReqRespToString;

public class AutomatedAgenticTestExecutor {

    private static final LoggerMaker loggerMaker = new LoggerMaker(AutomatedAgenticTestExecutor.class, LogDb.TESTING);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Gson gson = new Gson();
    private static final Type STRING_MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private static final String BASE_URL = Constants.AUTOMATED_AGENT_BASE_URL;
    private static final String EXECUTE_ENDPOINT = "/pentest";

    private static final OkHttpClient httpClient = CoreHTTPClient.client.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.MINUTES)
            .build();

    public List<TestResult> executeAgenticTest(RawApi testReq, String testSubType) {
        try {
            String body = buildRequestBody(testReq, testSubType);
            RequestBody requestBody = RequestBody.create(body, MediaType.parse("application/json"));

            Request.Builder requestBuilder = new Request.Builder()
                    .url(BASE_URL + EXECUTE_ENDPOINT)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json");
            String authToken = ClientActor.getAuthToken();
            if (authToken != null && !authToken.isEmpty()) {
                requestBuilder.addHeader(ClientActor.AUTHORIZATION, authToken);
            }
            Request request = requestBuilder.build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    loggerMaker.errorAndAddToDb("AutomatedAgenticTestExecutor: request failed with status " + response.code() + ", body: " + responseBody);
                    return Collections.emptyList();
                }
                String responseBody = response.body() != null ? response.body().string() : null;
                if (responseBody == null || responseBody.isEmpty()) {
                    return Collections.emptyList();
                }
                List<TestResult> results = new ArrayList<>();
                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode arr = root.has("attempts") ? root.get("attempts") : root;
                for (JsonNode node : arr) {
                    TestResult result = new TestResult();
                    result.setMessage(buildAttemptMessageFromPentestEvidence(node));
                    result.setVulnerable(node.has("vulnerable") && node.get("vulnerable").asBoolean());
                    result.setPercentageMatch(node.has("percentageMatch") ? node.get("percentageMatch").asDouble() : 0.0);
                    TestResult.Confidence confidence = TestResult.Confidence.HIGH;
                    if (node.has("confidence")) {
                        try { confidence = TestResult.Confidence.valueOf(node.get("confidence").asText().toUpperCase()); } catch (Exception ignored) {}
                    }
                    result.setConfidence(confidence);
                    List<String> errors = new ArrayList<>();
                    if (node.has("errors") && node.get("errors").isArray()) {
                        node.get("errors").forEach(e -> errors.add(e.asText()));
                    }
                    result.setErrors(errors);
                    result.setResultTypeAgentic(true);
                    results.add(result);
                }
                return results;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("AutomatedAgenticTestExecutor: error executing agentic test: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private String buildRequestBody(RawApi testReq, String testSubType) throws Exception {
        OriginalHttpRequest req = testReq.getRequest();
        OriginalHttpResponse resp = testReq.getResponse();

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("method", req.getMethod());
        requestMap.put("url", req.getUrl());
        requestMap.put("headers", flattenHeaders(req.getHeaders()));
        requestMap.put("body", parseBodyToObject(req.getBody()));
        requestMap.put("type", req.getType());
        requestMap.put("queryParams", req.getQueryParams());

        Map<String, Object> responseMap = new HashMap<>();
        if (resp != null) {
            responseMap.put("status_code", resp.getStatusCode());
            responseMap.put("headers", flattenHeaders(resp.getHeaders()));
            responseMap.put("body", parseBodyToObject(resp.getBody()));
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("testSubType", testSubType != null ? testSubType : "");
        payload.put("request", requestMap);
        payload.put("response", responseMap);

        return objectMapper.writeValueAsString(payload);
    }


    private String buildAttemptMessageFromPentestEvidence(JsonNode resultNode) {
        JsonNode evidence = extractMessageObject(resultNode);
        if (evidence != null && evidence.has("request")) {
            try {
                OriginalHttpRequest req = originalHttpRequestFromEvidence(evidence.get("request"));
                OriginalHttpResponse resp = originalHttpResponseFromEvidence(evidence.get("response"));
                return convertOriginalReqRespToString(req, resp);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("AutomatedAgenticTestExecutor: convertOriginalReqRespToString failed, using raw message: " + e.getMessage());
            }
        }
        return normalizeEvidenceMessageForUi(resultNode);
    }

    private JsonNode extractMessageObject(JsonNode resultNode) {
        if (resultNode == null || !resultNode.has("message")) {
            return null;
        }
        JsonNode m = resultNode.get("message");
        try {
            if (m.isObject()) {
                return m;
            }
            if (m.isTextual()) {
                String text = m.asText();
                if (text == null || text.isEmpty()) {
                    return null;
                }
                return objectMapper.readTree(text);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("AutomatedAgenticTestExecutor: could not parse message field: " + e.getMessage());
        }
        return null;
    }

    private static OriginalHttpRequest originalHttpRequestFromEvidence(JsonNode reqNode) {
        if (reqNode == null || !reqNode.isObject()) {
            return new OriginalHttpRequest("", "", "GET", "", new HashMap<>(), "HTTP/1.1");
        }
        String url = textOrEmpty(reqNode, "url");
        String queryParams = textOrEmpty(reqNode, "queryParams");
        String method = textOrEmpty(reqNode, "method");
        if (method.isEmpty()) {
            method = "GET";
        }
        String type = textOrEmpty(reqNode, "type");
        if (type.isEmpty()) {
            type = "HTTP/1.1";
        }
        String body = jsonNodeToBodyString(reqNode.get("body"));
        Map<String, List<String>> headers = jsonNodeToHeadersMap(reqNode.get("headers"));
        return new OriginalHttpRequest(url, queryParams, method, body, headers, type);
    }

    private static OriginalHttpResponse originalHttpResponseFromEvidence(JsonNode respNode) {
        if (respNode == null || !respNode.isObject()) {
            return new OriginalHttpResponse("", new HashMap<>(), 0);
        }
        int status = statusCodeFromEvidence(respNode);
        String body = jsonNodeToBodyString(respNode.get("body"));
        Map<String, List<String>> headers = jsonNodeToHeadersMap(respNode.get("headers"));
        return new OriginalHttpResponse(body, headers, status);
    }

    /** Pentest payloads often use {@code status_code}; Akto uses {@code statusCode}. */
    private static int statusCodeFromEvidence(JsonNode respNode) {
        if (respNode.has("statusCode") && !respNode.get("statusCode").isNull()) {
            return respNode.get("statusCode").asInt(0);
        }
        if (respNode.has("status_code") && !respNode.get("status_code").isNull()) {
            return respNode.get("status_code").asInt(0);
        }
        return 0;
    }

    private static String textOrEmpty(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return n != null && !n.isNull() ? n.asText("") : "";
    }

    private static String jsonNodeToBodyString(JsonNode bodyNode) {
        if (bodyNode == null || bodyNode.isNull()) {
            return "";
        }
        if (bodyNode.isTextual()) {
            return bodyNode.asText();
        }
        try {
            return objectMapper.writeValueAsString(bodyNode);
        } catch (Exception e) {
            return bodyNode.toString();
        }
    }


    private static Map<String, List<String>> jsonNodeToHeadersMap(JsonNode headersNode) {
        Map<String, List<String>> out = new HashMap<>();
        if (headersNode == null || headersNode.isNull()) {
            return out;
        }
        try {
            if (headersNode.isTextual()) {
                Map<String, String> flat = gson.fromJson(headersNode.asText(), STRING_MAP_TYPE);
                if (flat != null) {
                    for (Map.Entry<String, String> e : flat.entrySet()) {
                        out.put(e.getKey().toLowerCase(), Collections.singletonList(e.getValue()));
                    }
                }
                return out;
            }
            if (headersNode.isObject()) {
                headersNode.fields().forEachRemaining(entry ->
                        out.put(entry.getKey().toLowerCase(), Collections.singletonList(entry.getValue().asText())));
            }
        } catch (Exception ignored) {
        }
        return out;
    }


    private String normalizeEvidenceMessageForUi(JsonNode resultNode) {
        if (resultNode == null || !resultNode.has("message")) {
            return null;
        }
        JsonNode m = resultNode.get("message");
        try {
            if (m.isObject() || m.isArray()) {
                return objectMapper.writeValueAsString(m);
            }
            if (m.isTextual()) {
                String text = m.asText();
                if (text == null || text.isEmpty()) {
                    return null;
                }
                JsonNode parsed = objectMapper.readTree(text);
                return objectMapper.writeValueAsString(parsed);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("AutomatedAgenticTestExecutor: could not normalize message for UI: " + e.getMessage());
        }
        if (m.isTextual()) {
            return m.asText();
        }
        return m.toString();
    }


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
}
