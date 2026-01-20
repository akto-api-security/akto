package com.akto.otel;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * Converts OpenTelemetry spans from Datadog format to Akto HttpResponseParams format
 */
public class OtelSpanConverter {

    private static final LoggerMaker logger = new LoggerMaker(OtelSpanConverter.class, LogDb.DASHBOARD);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final long NANOSECONDS_TO_SECONDS = 1_000_000L;
    private static final int DEFAULT_API_COLLECTION_ID = 0;

    // OpenTelemetry semantic convention attribute names
    private static final class OtelAttributes {
        static final String HTTP_METHOD = "http.method";
        static final String HTTP_URL = "http.url";
        static final String HTTP_STATUS_CODE = "http.status_code";
        static final String HTTP_REQUEST_BODY = "http.request.body";
        static final String HTTP_RESPONSE_BODY = "http.response.body";
        static final String HTTP_REQUEST_HEADERS_PREFIX = "http.request.headers.";
        static final String HTTP_RESPONSE_HEADERS_PREFIX = "http.response.headers.";
        static final String NETWORK_CLIENT_IP = "network.client.ip";
        static final String PEER_ADDRESS = "peer.address";
        static final String NETWORK_DESTINATION_IP = "network.destination.ip";
    }

    /**
     * Converts a Datadog spans response JSON to list of HttpResponseParams
     *
     * @param datadogResponseJson Raw JSON response from Datadog API
     * @param accountId Account ID for the converted traces
     * @return ConversionResult containing converted traces and statistics
     */
    public ConversionResult convert(String datadogResponseJson, int accountId) throws Exception {
        JsonNode root = objectMapper.readTree(datadogResponseJson);
        JsonNode dataNode = root.get("data");

        if (dataNode == null || !dataNode.isArray()) {
            return new ConversionResult(Collections.emptyList(), 0, 0);
        }

        List<HttpResponseParams> convertedTraces = new ArrayList<>();
        int totalProcessed = dataNode.size();

        for (JsonNode span : dataNode) {
            try {
                HttpResponseParams trace = convertSpan(span, accountId);
                if (trace != null) {
                    convertedTraces.add(trace);
                }
            } catch (Exception e) {
                logger.errorAndAddToDb("Failed to convert span: " + e.getMessage());
            }
        }

        return new ConversionResult(convertedTraces, totalProcessed, convertedTraces.size());
    }

    private HttpResponseParams convertSpan(JsonNode span, int accountId) {
        JsonNode attributes = span.get("attributes");
        if (attributes == null) {
            return null;
        }

        String httpMethod = extractString(attributes, OtelAttributes.HTTP_METHOD);
        String httpUrl = extractString(attributes, OtelAttributes.HTTP_URL);

        if (httpMethod == null || httpUrl == null) {
            return null; // Skip non-HTTP spans
        }

        HttpRequestParams requestParams = buildRequestParams(attributes, httpMethod, httpUrl);
        return buildResponseParams(span, attributes, requestParams, accountId);
    }

    private HttpRequestParams buildRequestParams(JsonNode attributes, String method, String url) {
        HttpRequestParams requestParams = new HttpRequestParams();
        requestParams.setMethod(method);
        requestParams.setUrl(url);
        requestParams.type = "HTTP/1.1";

        Map<String, List<String>> requestHeaders = extractHeaders(attributes, OtelAttributes.HTTP_REQUEST_HEADERS_PREFIX);
        requestParams.setHeaders(requestHeaders);

        String requestBody = extractString(attributes, OtelAttributes.HTTP_REQUEST_BODY);
        requestParams.setPayload(requestBody != null ? requestBody : "{}");
        requestParams.setApiCollectionId(DEFAULT_API_COLLECTION_ID);

        return requestParams;
    }

    private HttpResponseParams buildResponseParams(JsonNode span, JsonNode attributes,
                                                   HttpRequestParams requestParams, int accountId) {
        Integer statusCode = extractInt(attributes, OtelAttributes.HTTP_STATUS_CODE);
        String responseBody = extractString(attributes, OtelAttributes.HTTP_RESPONSE_BODY);
        Map<String, List<String>> responseHeaders = extractHeaders(attributes, OtelAttributes.HTTP_RESPONSE_HEADERS_PREFIX);

        int status = statusCode != null ? statusCode : 200;
        long timestampNanos = span.has("timestamp") ? span.get("timestamp").asLong() : 0;
        int timestampSeconds = timestampNanos > 0 ? (int) (timestampNanos / NANOSECONDS_TO_SECONDS) : getCurrentTimestamp();

        String sourceIp = extractSourceIp(attributes);
        String destIp = extractString(attributes, OtelAttributes.NETWORK_DESTINATION_IP);

        return new HttpResponseParams(
            "HTTP/1.1",
            status,
            getStatusText(status),
            responseHeaders,
            responseBody != null ? responseBody : "{}",
            requestParams,
            timestampSeconds,
            String.valueOf(accountId),
            false,
            HttpResponseParams.Source.OTHER,
            "opentelemetry",
            sourceIp != null ? sourceIp : "",
            destIp != null ? destIp : "",
            ""
        );
    }

    private String extractSourceIp(JsonNode attributes) {
        String sourceIp = extractString(attributes, OtelAttributes.NETWORK_CLIENT_IP);
        if (sourceIp == null) {
            sourceIp = extractString(attributes, OtelAttributes.PEER_ADDRESS);
        }
        return sourceIp;
    }

    private String extractString(JsonNode attributes, String key) {
        JsonNode node = attributes.get(key);
        return (node != null && !node.isNull()) ? node.asText() : null;
    }

    private Integer extractInt(JsonNode attributes, String key) {
        JsonNode node = attributes.get(key);
        return (node != null && !node.isNull()) ? node.asInt() : null;
    }

    private Map<String, List<String>> extractHeaders(JsonNode attributes, String prefix) {
        Map<String, List<String>> headers = new HashMap<>();

        attributes.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            if (key.startsWith(prefix)) {
                String headerName = key.substring(prefix.length());
                String headerValue = entry.getValue().asText();
                headers.put(headerName, Collections.singletonList(headerValue));
            }
        });

        return headers;
    }

    private String getStatusText(int statusCode) {
        int category = statusCode / 100;
        switch (category) {
            case 2: return "OK";
            case 3: return "Redirect";
            case 4: return "Client Error";
            case 5: return "Server Error";
            default: return "Unknown";
        }
    }

    private int getCurrentTimestamp() {
        return (int) (System.currentTimeMillis() / 1000);
    }

    /**
     * Result of span conversion operation
     */
    public static class ConversionResult {
        private final List<HttpResponseParams> traces;
        private final int totalProcessed;
        private final int successfullyConverted;

        public ConversionResult(List<HttpResponseParams> traces, int totalProcessed, int successfullyConverted) {
            this.traces = traces;
            this.totalProcessed = totalProcessed;
            this.successfullyConverted = successfullyConverted;
        }

        public List<HttpResponseParams> getTraces() {
            return traces;
        }

        public int getTotalProcessed() {
            return totalProcessed;
        }

        public int getSuccessfullyConverted() {
            return successfullyConverted;
        }
    }
}
