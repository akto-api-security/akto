package com.akto.jobs.executors.clickup;

import com.akto.jobs.exception.RetryableJobException;
import com.akto.log.LoggerMaker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.util.concurrent.TimeUnit;

public class ClickupApiClient {

    private static final LoggerMaker logger = new LoggerMaker(ClickupApiClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build();

    private final String baseUrl;
    private final String apiToken;
    private final String workspaceId;
    private final int pageRows;

    public ClickupApiClient(String baseUrl, String apiToken, String workspaceId, int pageRows) {
        this.baseUrl = baseUrl;
        this.apiToken = apiToken;
        this.workspaceId = workspaceId;
        this.pageRows = pageRows;
    }

    public JsonNode fetchTraceSummaries(long fromTimestampMs, long toTimestampMs) throws Exception {
        try {
            HttpUrl parsedBaseUrl = HttpUrl.parse(baseUrl);
            if (parsedBaseUrl == null) {
                throw new IllegalArgumentException("Invalid ClickUp base URL: " + baseUrl);
            }

            HttpUrl url = parsedBaseUrl.newBuilder()
                .addEncodedPathSegments("auto-auditlog-service/v1/workspaces/" + workspaceId + "/trace-summaries")
                .addQueryParameter("pageRows", String.valueOf(pageRows))
                .addQueryParameter("pageDirection", "before")
                .addQueryParameter("pageTimestamp", String.valueOf(toTimestampMs))
                .addQueryParameter("pageTimestampEnd", String.valueOf(fromTimestampMs))
                .addQueryParameter("isAgent", "true")
                .build();

            Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiToken)
                .header("x-clickup-token", apiToken)
                .header("x-api-key", apiToken)
                .header("Content-Type", "application/json")
                .get()
                .build();

            logger.info("Fetching ClickUp traces from {} to {} for workspace {}", fromTimestampMs, toTimestampMs, workspaceId);
            Response response = client.newCall(request).execute();

            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new RetryableJobException("ClickUp API returned status " + response.code() + ": " + errorBody);
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            if (responseBody.isEmpty()) {
                return objectMapper.createObjectNode();
            }

            return objectMapper.readTree(responseBody);
        } catch (RetryableJobException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to fetch ClickUp trace summaries", e);
            throw new RetryableJobException("Failed to fetch ClickUp trace summaries: " + e.getMessage(), e);
        }
    }
}
