package com.akto.otel;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.testing.ApiExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * Client for fetching OpenTelemetry spans from Datadog API
 */
public class DatadogOtelClient {

    private static final LoggerMaker logger = new LoggerMaker(DatadogOtelClient.class, LogDb.DASHBOARD);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String SPANS_SEARCH_PATH = "/api/v2/spans/events/search";
    private static final long MILLISECONDS_PER_SECOND = 1000L;

    private final String apiKey;
    private final String appKey;
    private final String site;

    public DatadogOtelClient(String apiKey, String appKey, String site) {
        this.apiKey = apiKey;
        this.appKey = appKey;
        this.site = site != null ? site : "datadoghq.com";
    }

    /**
     * Fetches spans from Datadog within the specified time range
     *
     * @param startTimeSeconds Unix timestamp in seconds
     * @param endTimeSeconds Unix timestamp in seconds
     * @param serviceName Optional service name filter
     * @param limit Maximum number of spans to fetch
     * @return Raw JSON response from Datadog API
     * @throws Exception if the API call fails
     */
    public String fetchSpans(long startTimeSeconds, long endTimeSeconds, String serviceName, int limit) throws Exception {
        String apiUrl = buildApiUrl();
        String requestBody = buildRequestBody(startTimeSeconds, endTimeSeconds, serviceName, limit);

        OriginalHttpRequest request = createRequest(apiUrl, requestBody);
        OriginalHttpResponse response = ApiExecutor.sendRequest(request, false, null, false, new ArrayList<>());

        if (response.getStatusCode() != 200) {
            String errorMsg = String.format("Datadog API returned status %d: %s",
                response.getStatusCode(), response.getBody());
            logger.errorAndAddToDb(errorMsg);
            throw new RuntimeException(errorMsg);
        }

        return response.getBody();
    }

    private String buildApiUrl() {
        return String.format("https://api.%s%s", site, SPANS_SEARCH_PATH);
    }

    private String buildRequestBody(long startTimeSeconds, long endTimeSeconds, String serviceName, int limit) throws Exception {
        Map<String, Object> requestBody = new HashMap<>();

        Map<String, Object> filter = new HashMap<>();
        filter.put("from", startTimeSeconds * MILLISECONDS_PER_SECOND);
        filter.put("to", endTimeSeconds * MILLISECONDS_PER_SECOND);

        if (serviceName != null && !serviceName.trim().isEmpty()) {
            filter.put("query", "service:" + serviceName);
        }

        requestBody.put("filter", filter);
        requestBody.put("page", Collections.singletonMap("limit", limit));
        requestBody.put("sort", "timestamp");

        return objectMapper.writeValueAsString(requestBody);
    }

    private OriginalHttpRequest createRequest(String url, String body) {
        OriginalHttpRequest request = new OriginalHttpRequest();
        request.setUrl(url);
        request.setMethod("POST");
        request.setBody(body);

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("DD-API-KEY", Collections.singletonList(apiKey));
        headers.put("DD-APPLICATION-KEY", Collections.singletonList(appKey));
        headers.put("Content-Type", Collections.singletonList("application/json"));
        request.setHeaders(headers);

        return request;
    }
}
