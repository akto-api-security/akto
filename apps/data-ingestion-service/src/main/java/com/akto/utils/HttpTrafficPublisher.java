package com.akto.utils;

import com.akto.log.LoggerMaker;
import com.akto.util.http_util.CoreHTTPClient;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.concurrent.TimeUnit;

/**
 * Sends traffic messages to the mini-runtime HTTP ingest endpoint
 * (POST /utility/ingestTraffic) instead of Kafka.
 *
 * Enabled by setting USE_HTTP_INGEST=true in data-ingestion-service.
 * Requires TRAFFIC_INGEST_URL to be set, e.g. http://mini-runtime-svc:8001
 *
 * Note: guardrails publishing is not supported in HTTP mode.
 */
public class HttpTrafficPublisher implements TrafficPublisher {

    private static final LoggerMaker logger = new LoggerMaker(HttpTrafficPublisher.class, LoggerMaker.LogDb.DATA_INGESTION);

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final OkHttpClient HTTP_CLIENT = CoreHTTPClient.client.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    private final String ingestUrl;

    public HttpTrafficPublisher() {
        String base = System.getenv("TRAFFIC_INGEST_URL");
        if (base == null || base.trim().isEmpty()) {
            logger.errorAndAddToDb("TRAFFIC_INGEST_URL env var is not set — HttpTrafficPublisher will drop all messages");
            this.ingestUrl = null;
        } else {
            this.ingestUrl = base.trim() + "/utility/ingestTraffic";
            logger.infoAndAddToDb("HttpTrafficPublisher initialized, target: " + this.ingestUrl);
        }
    }

    @Override
    public void publish(String message, String primaryTopic, boolean publishToGuardrails) {
        if (ingestUrl == null) {
            return;
        }
        RequestBody body = RequestBody.create(message, JSON);
        Request request = new Request.Builder()
                .url(ingestUrl)
                .post(body)
                .build();
        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            if (response.code() == 429) {
                logger.errorAndAddToDb("HTTP ingest queue full (429) — message dropped");
            } else if (!response.isSuccessful()) {
                logger.errorAndAddToDb("HTTP ingest returned " + response.code() + " for message: "
                        + message.substring(0, Math.min(200, message.length())));
            }
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Failed to send to HTTP ingest endpoint: " + e.getMessage());
        }
    }
}
