package com.akto.otel;

import java.util.*;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.testing.ApiExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DatadogOtelClient {

    private static final LoggerMaker logger = new LoggerMaker(DatadogOtelClient.class, LogDb.DASHBOARD);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String SPANS_SEARCH_PATH = "/api/v2/llm-obs/v1/spans/events/search";

    private final String apiKey;
    private final String appKey;
    private final String site;

    public DatadogOtelClient(String apiKey, String appKey, String site) {
        this.apiKey = apiKey;
        this.appKey = appKey;
        this.site = site != null ? site : "datadoghq.com";
    }

    public String fetchSpans(long startTimeMillis, long endTimeMillis, List<String> serviceNames, int limit) throws Exception {
        String apiUrl = buildApiUrl();
        String requestBody = buildRequestBody(startTimeMillis, endTimeMillis, serviceNames, limit);


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

    private String buildRequestBody(long startTimeMillis, long endTimeMillis, List<String> serviceNames, int limit) throws Exception {
        Map<String, Object> filter = new HashMap<>();

        filter.put("span_kind", "workflow");

        filter.put("from", String.valueOf(startTimeMillis));
        filter.put("to", String.valueOf(endTimeMillis));

        if (serviceNames != null && !serviceNames.isEmpty()) {
            Map<String, String> tags = new HashMap<>();
            tags.put("service", serviceNames.get(0));
            filter.put("tags", tags);
        }

        Map<String, Object> page = new HashMap<>();
        page.put("limit", limit);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("filter", filter);
        attributes.put("page", page);
        attributes.put("sort", "timestamp");

        Map<String, Object> data = new HashMap<>();
        data.put("attributes", attributes);
        data.put("type", "spans");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("data", data);

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
