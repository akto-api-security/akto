package com.akto.utils.guardrails;

import com.akto.dao.context.Context;
import com.akto.database_abstractor_authenticator.JwtAuthenticator;
import com.akto.dto.EnterpriseLicenseComplianceCatalog;
import com.akto.dto.GuardrailPolicies;
import com.akto.util.http_util.CoreHTTPClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Thin client for the guardrails-service's replay/compare endpoint
 * (POST /api/validate/replayWithPolicy). Shared by every caller that needs to re-evaluate stored
 * or fetched traffic against a policy without touching malicious_events — the dashboard's
 * policy-edit impact analysis ({@code GuardrailPolicyReplayAction}) and the
 * account-job-executor's backfill replay job both use this instead of holding their own copies of
 * the base-URL/auth/HTTP-call logic.
 *
 * <p>This client never persists anything: {@code replay.go} / {@code handlers/validation.go} on
 * the guardrails-service side are deliberately side-effect-free. A caller that wants a detection
 * to become a real, persisted guardrail activity does that itself afterward (see
 * {@code com.akto.utils.threat_detection.ThreatDetectionBackendClient#recordMaliciousEvent}).
 */
public final class GuardrailsServiceClient {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // A page evaluates every item against up to two policies, each of which can reach the LLM
    // scanners, so allow a generous read timeout; the guardrails service bounds each evaluation.
    private static final OkHttpClient httpClient = CoreHTTPClient.client.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build();

    /** Must not exceed maxReplayItems in the guardrails service's replay handler. */
    public static final int PAGE_SIZE = 25;

    /** Account whose guardrails traffic is served by the shared ingest host rather than its own. */
    private static final int SHARED_INGEST_ACCOUNT_ID = 1768175789;
    private static final int TOKEN_VALIDITY_MINUTES = 120;

    private GuardrailsServiceClient() {
    }

    /**
     * POSTs one page (&lt;= {@link #PAGE_SIZE} items) to the guardrails service and returns the
     * verdict nodes, one per item in input order. Mints a fresh short-lived token for this one
     * call — fine for a handful of calls (e.g. the dashboard's in-process compare feature), but
     * see the overload below for a caller (e.g. a background job) that makes many calls and
     * should reuse one token instead.
     *
     * @param baselinePayload optional — when present, each item is also evaluated against this
     *     policy and the verdict carries both results (see {@code baselineDetected}). Pass null
     *     for a single-policy detection pass (e.g. a backfill replay against only the active policy).
     */
    public static Iterable<JsonNode> replay(List<BasicDBObject> items, BasicDBObject policyPayload,
                                            BasicDBObject baselinePayload, String contextSource) throws Exception {
        return replay(items, policyPayload, baselinePayload, contextSource, authToken(), Context.accountId.get());
    }

    /**
     * Same as the 4-arg overload, but takes an already-minted token instead of signing a new one.
     * A long-running caller (e.g. {@code GuardrailPolicyBackfillReplayExecutor}, which can call
     * this hundreds of times over a job's lifetime) should mint one longer-lived token up front
     * (see {@link #createLongLivedAuthToken}) and pass it here every time, rather than paying for
     * a fresh signature on every call. Does not request the extra category/subCategory/severity
     * detection details — see the 7-arg overload below for that.
     */
    public static Iterable<JsonNode> replay(List<BasicDBObject> items, BasicDBObject policyPayload,
                                            BasicDBObject baselinePayload, String contextSource,
                                            String bearerToken, int accountId) throws Exception {
        return replay(items, policyPayload, baselinePayload, contextSource, bearerToken, accountId, false);
    }

    /**
     * Same as the 6-arg overload, but additionally requests category/subCategory/severity on each
     * detected verdict — the same policy-name/rule/severity triple a live detection reports (see
     * {@code replay.go}'s {@code ReplayVerdict}). Meant for a caller that persists a real
     * malicious_events record from the replay (e.g. a backfill job), not the lightweight dashboard
     * compare feature, which uses the 6-arg overload and is unaffected by this flag.
     */
    public static Iterable<JsonNode> replay(List<BasicDBObject> items, BasicDBObject policyPayload,
                                            BasicDBObject baselinePayload, String contextSource,
                                            String bearerToken, int accountId,
                                            boolean includeDetectionDetails) throws Exception {
        BasicDBObject body = new BasicDBObject()
            .append("policy", policyPayload)
            .append("contextSource", contextSource)
            .append("items", items)
            .append("includeDetectionDetails", includeDetectionDetails);
        if (baselinePayload != null) {
            body.append("baselinePolicy", baselinePayload);
        }

        Request request = new Request.Builder()
            .url(baseUrl(accountId) + "/api/validate/replayWithPolicy")
            .post(RequestBody.create(body.toJson(), MediaType.parse("application/json")))
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", bearerToken)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String raw = responseBody != null ? responseBody.string() : "";
            if (!response.isSuccessful()) {
                throw new IllegalStateException(
                    "guardrails service returned " + response.code() + ": " + raw);
            }
            return objectMapper.readTree(raw).path("verdicts");
        }
    }

    /**
     * Base URL of the calling account's guardrails service, without a trailing slash.
     *
     * <p>{@code GUARDRAILS_SERVICE_URL} overrides the per-account host — set it to point a local or
     * self-hosted dashboard at a specific guardrails service.
     */
    public static String baseUrl(int accountId) {
        String override = System.getenv("GUARDRAILS_SERVICE_URL");
        if (StringUtils.isNotBlank(override)) {
            return StringUtils.stripEnd(override.trim(), "/");
        }
        if (accountId == SHARED_INGEST_ACCOUNT_ID) {
            return "https://ingest.akto.io";
        }
        return "https://" + accountId + "-guardrails.akto.io";
    }

    /** Short-lived JWT authenticating the caller to the guardrails service. */
    public static String authToken() throws Exception {
        Map<String, Object> claims = new HashMap<>();
        claims.put("accountId", Context.accountId.get());
        return JwtAuthenticator.createJWT(claims, "Akto", "invite_user", Calendar.MINUTE, TOKEN_VALIDITY_MINUTES);
    }

    /**
     * Mints one longer-lived JWT for a background job to carry in its own persisted state
     * (e.g. {@code AccountJob.config}) and reuse across every HTTP call it makes over its
     * lifetime, instead of signing a fresh token per call — the same shape and verification path
     * as {@link #authToken()} (and {@code ThreatDetectionBackendClient}'s token: both the Go
     * guardrails-service and threat-detection-backend's auth middleware verify only the RSA
     * signature, the {@code accountId} claim, and standard expiry — nothing subject- or
     * scope-specific), just with a caller-chosen expiry instead of the fixed short one.
     *
     * @param scopes advisory only today (neither verifier checks it), but self-documents what the
     *     token is for — same convention as {@code QuickStartAction.createTokenForAuth}.
     */
    public static String createLongLivedAuthToken(List<String> scopes, int expiryDays) throws Exception {
        Map<String, Object> claims = new HashMap<>();
        claims.put("accountId", Context.accountId.get());
        if (scopes != null && !scopes.isEmpty()) {
            claims.put("scope", scopes);
        }
        return JwtAuthenticator.createJWT(claims, "Akto", "invite_user", Calendar.DATE, expiryDays);
    }

    /** Placeholder request line for a trace-derived envelope, used by {@link #traceEnvelope} and
     *  exposed for callers (e.g. a backfill replay job) that also need to record a detection
     *  against the same synthetic method/path they evaluated: agent-query records carry the
     *  prompt but not the HTTP method/path, so there is nothing real to put here; with no field
     *  mapping matching this path the guardrails service scans the raw payload, which is what
     *  every caller of {@code traceEnvelope} wants. */
    public static final String TRACE_METHOD = "POST";
    public static final String TRACE_PATH = "/v1/messages";

    /**
     * Wraps a recorded prompt/response pair in the stored-traffic envelope the guardrails service
     * already parses (the {@code envelope} field of a {@code ReplayItem}).
     *
     * <p>{@code requestPayload} is passed through untouched because it is <em>already</em> the
     * request payload JSON — real rows look like {@code {"body": ...}} or
     * {@code {"body":..., "toolName":...}}, the same shape live gateway traffic has. Wrapping it
     * again would bury the prompt one level deeper than any field mapping or extractor looks.
     */
    public static String traceEnvelope(String requestPayload, String responsePayload) {
        return new BasicDBObject()
            .append("method", TRACE_METHOD)
            .append("path", TRACE_PATH)
            .append("requestPayload", requestPayload)
            .append("responsePayload", responsePayload == null ? "" : responsePayload)
            .toJson();
    }

    /**
     * Serializes a policy for transmission to the guardrails service, dropping Mongo-internal fields
     * and forcing {@code active} on — an inactive policy would allow everything, making the
     * comparison/replay meaningless.
     *
     * <p>Mutates {@code policy}: expands enterprise-licence categories into denied topics and fills
     * in {@code contextSource} / {@code applyOnRequest} when unset, so every caller serializes a
     * policy the same way regardless of which side of the comparison it is.
     *
     * @param fallbackName used when the policy has no name of its own (unsaved drafts)
     * @param defaultContextSource used only when the policy has no contextSource of its own
     */
    public static BasicDBObject serializePolicy(GuardrailPolicies policy, String fallbackName,
                                                com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE defaultContextSource) {
        policy.setActive(true);
        if (policy.getContextSource() == null) {
            policy.setContextSource(defaultContextSource);
        }
        // Request validation is the common case; a policy targeting neither side would no-op.
        if (!policy.isApplyOnRequest() && !policy.isApplyOnResponse()) {
            policy.setApplyOnRequest(true);
        }
        EnterpriseLicenseComplianceCatalog.applyToPolicy(policy);

        @SuppressWarnings("unchecked")
        Map<String, Object> policyMap = objectMapper.convertValue(policy, Map.class);

        policyMap.remove("id");
        policyMap.remove("hexId");
        policyMap.remove("createdTimestamp");
        policyMap.remove("updatedTimestamp");
        policyMap.remove("createdBy");
        policyMap.remove("updatedBy");

        String name = policy.getName();
        if (StringUtils.isBlank(name)) {
            name = fallbackName;
        }
        policyMap.put("name", name);
        policyMap.put("active", true);
        if (policy.getContextSource() != null) {
            policyMap.put("contextSource", policy.getContextSource().name());
        }
        policyMap.put("policyVersion", "1.0");

        return new BasicDBObject(policyMap);
    }
}
