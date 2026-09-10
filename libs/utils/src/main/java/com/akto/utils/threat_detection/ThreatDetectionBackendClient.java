package com.akto.utils.threat_detection;

import com.akto.ProtoMessageUtils;
import com.akto.database_abstractor_authenticator.JwtAuthenticator;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsResponse;
import com.akto.util.http_util.CoreHTTPClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * Thin client for threat-detection-backend's dashboard-facing HTTP API. Shared by every caller
 * that needs to read or write {@code malicious_events} over HTTP rather than through the gRPC
 * service used internally by {@code apps/threat-detection} — originally lived only on
 * {@code AbstractThreatDetectionAction} (apps/dashboard), factored out here so
 * apps/account-job-executor can call {@link #recordMaliciousEvent} too without depending on the
 * dashboard module.
 */
public final class ThreatDetectionBackendClient {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final OkHttpClient httpClient = CoreHTTPClient.client.newBuilder().build();
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final String RECORD_MALICIOUS_EVENT_PATH = "/api/threat_detection/record_malicious_event";
    private static final String LIST_MALICIOUS_REQUESTS_PATH = "/api/dashboard/list_malicious_requests";

    private ThreatDetectionBackendClient() {
    }

    /** Base URL of threat-detection-backend, without a trailing slash. */
    public static String backendUrl() {
        return System.getenv().getOrDefault("THREAT_DETECTION_BACKEND_URL", "https://tbs.akto.io");
    }

    /**
     * Short-lived JWT authenticating the caller to threat-detection-backend. Not cached: the token
     * is valid for only one minute, so a per-call token is simpler than a cache that would rarely
     * hit anyway.
     */
    public static String apiToken(int accountId) throws Exception {
        Map<String, Object> claims = new HashMap<>();
        claims.put("accountId", accountId);
        return JwtAuthenticator.createJWT(claims, "Akto", "access_tbs", Calendar.MINUTE, 5);
    }

    /**
     * Records one malicious/guardrail event, the same way {@code apps/threat-detection}'s
     * SendGuardrailEventsToBackend forwards a live detection — except the caller controls
     * {@code detectedAt} directly, which is what lets a backfill job attribute a detection to the
     * original traffic time instead of "now". Mints a fresh short-lived token for this one call;
     * see the overload below for a caller making many calls that should reuse one token instead.
     *
     * @param maliciousEventJson a JSON object matching the {@code MaliciousEventMessage} proto
     *     (camelCase field names: filterId, detectedAt, latestApiPayload, label, contextSource,
     *     refId, ...), wrapped by this method into the {@code {"maliciousEvent": ...}} request body
     *     {@code RecordMaliciousEventRequest} expects.
     */
    public static void recordMaliciousEvent(int accountId, Map<String, Object> maliciousEventJson) throws Exception {
        recordMaliciousEvent(accountId, maliciousEventJson, apiToken(accountId));
    }

    /**
     * Same as the 2-arg overload, but takes an already-minted bearer token instead of signing a
     * new one. A long-running caller (e.g. {@code GuardrailPolicyBackfillReplayExecutor}, which
     * can call this once per detection over a job's lifetime) should mint one longer-lived token
     * up front (see {@code GuardrailsServiceClient#createLongLivedAuthToken}) and pass it here
     * every time, rather than paying for a fresh signature on every detection.
     *
     * @param bearerToken the full header value, e.g. {@code "Bearer <jwt>"}.
     */
    public static void recordMaliciousEvent(int accountId, Map<String, Object> maliciousEventJson,
                                            String bearerToken) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("maliciousEvent", maliciousEventJson);
        String msg = objectMapper.valueToTree(body).toString();

        RequestBody requestBody = RequestBody.create(msg, JSON);
        Request request = new Request.Builder()
            .url(backendUrl() + RECORD_MALICIOUS_EVENT_PATH)
            .post(requestBody)
            .addHeader("Authorization", bearerToken)
            .addHeader("Content-Type", "application/json")
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            int code = response.code();
            if (code != 200 && code != 202) {
                String responseBody = response.body() != null ? response.body().string() : "";
                throw new IllegalStateException(
                    "record_malicious_event returned " + code + ": " + responseBody);
            }
        }
    }

    /**
     * Fetch malicious events from threat-detection-backend.
     *
     * @param startTimestamp Start timestamp for time range filter (0 or negative to skip)
     * @param endTimestamp End timestamp for time range filter (0 or negative to skip)
     * @param limit Maximum number of events to fetch
     * @param additionalFilters Optional additional filters to add to the request (can be null or empty)
     * @param contextSourceValue value for the x-context-source header (empty string if none)
     * @param skillEvalMode Optional "x-skill-eval-mode" header value (see the 4-arg overload note below)
     */
    public static ListMaliciousRequestsResponse listMaliciousRequests(
            int accountId,
            int startTimestamp,
            int endTimestamp,
            int limit,
            Map<String, Object> additionalFilters,
            String contextSourceValue,
            String skillEvalMode) throws Exception {
        String url = backendUrl() + LIST_MALICIOUS_REQUESTS_PATH;

        Map<String, Object> filter = new HashMap<>();
        Map<String, Integer> timeRange = new HashMap<>();
        if (startTimestamp > 0) {
            timeRange.put("start", startTimestamp);
        }
        if (endTimestamp > 0) {
            timeRange.put("end", endTimestamp);
        }
        filter.put("detected_at_time_range", timeRange);
        if (additionalFilters != null && !additionalFilters.isEmpty()) {
            filter.putAll(additionalFilters);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("skip", 0);
        body.put("limit", limit);
        Map<String, Integer> sort = new HashMap<>();
        sort.put("detectedAt", -1);
        body.put("sort", sort);
        body.put("filter", filter);

        String msg = objectMapper.valueToTree(body).toString();

        RequestBody requestBody = RequestBody.create(msg, JSON);
        Request.Builder requestBuilder = new Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Authorization", "Bearer " + apiToken(accountId))
            .addHeader("Content-Type", "application/json")
            .addHeader("x-context-source", contextSourceValue == null ? "" : contextSourceValue);
        if (skillEvalMode != null && !skillEvalMode.isEmpty()) {
            requestBuilder.addHeader("x-skill-eval-mode", skillEvalMode);
        }

        try (Response resp = httpClient.newCall(requestBuilder.build()).execute()) {
            String responseBody = resp.body() != null ? resp.body().string() : "";
            return ProtoMessageUtils.<ListMaliciousRequestsResponse>toProtoMessage(
                ListMaliciousRequestsResponse.class, responseBody
            ).orElse(null);
        }
    }
}
