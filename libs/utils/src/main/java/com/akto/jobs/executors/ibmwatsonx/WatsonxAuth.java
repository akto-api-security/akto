package com.akto.jobs.executors.ibmwatsonx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Exchanges the customer's Orchestrate API key for a short-lived bearer token, and renews it
 * automatically (the API key is long-lived; tokens last about an hour or two).
 *
 * SaaS instances (*.watson-orchestrate.ibm.com) sign in through IBM's MCSP token service (v2, then
 * v1, as IBM's ADK does); IBM Cloud instances (*.cloud.ibm.com) through IBM Cloud IAM.
 */
public class WatsonxAuth {

    static final String MCSP_V2_URL = "https://account-iam.platform.saas.ibm.com/api/2.0/apikeys/token";
    static final String MCSP_V1_URL = "https://iam.platform.saas.ibm.com/siusermgr/api/1.0/apikeys/token";
    static final String IAM_URL = "https://iam.cloud.ibm.com/identity/token";
    static final long RENEW_BEFORE_EXPIRY_MS = 2 * 60 * 1000L;
    static final long DEFAULT_TOKEN_TTL_SECONDS = 3600;

    static final String KEY_REJECTED = "IBM watsonx Orchestrate rejected the API key (invalid, expired or revoked). "
            + "Enter a valid API key in the IBM watsonx Orchestrate connector in Akto.";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final boolean ibmCloud;
    private final WatsonxTransport transport;
    private final LongSupplier clockMs;

    private String token;
    private long tokenExpiresAtMs;

    public WatsonxAuth(String instanceUrl, String apiKey, WatsonxTransport transport, LongSupplier clockMs) {
        this.apiKey = apiKey;
        this.ibmCloud = instanceUrl != null && instanceUrl.contains("cloud.ibm.com");
        this.transport = transport;
        this.clockMs = clockMs;
    }

    public synchronized String token() throws IOException {
        if (token == null || clockMs.getAsLong() >= tokenExpiresAtMs - RENEW_BEFORE_EXPIRY_MS) {
            signIn();
        }
        return token;
    }

    /** Forget the token (after IBM answered 401), so the next call signs in again. */
    public synchronized void invalidate() {
        token = null;
    }

    boolean isIbmCloud() {
        return ibmCloud;
    }

    private void signIn() throws IOException {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new WatsonxApiException(401, KEY_REJECTED, true);
        }
        List<WatsonxTransport.Response> failures = new ArrayList<>();
        List<String> urls = ibmCloud ? Collections.singletonList(IAM_URL) : java.util.Arrays.asList(MCSP_V2_URL, MCSP_V1_URL);
        for (String url : urls) {
            WatsonxTransport.Response response;
            try {
                response = ibmCloud
                        ? transport.send("POST", url, null, "grant_type=urn%3Aibm%3Aparams%3Aoauth%3Agrant-type%3Aapikey&apikey="
                                + URLEncoder.encode(apiKey.trim(), StandardCharsets.UTF_8.name()), "application/x-www-form-urlencoded")
                        : transport.send("POST", url, null, MAPPER.writeValueAsString(Collections.singletonMap("apikey", apiKey.trim())),
                                "application/json");
            } catch (IOException e) {
                throw new WatsonxApiException("Could not reach IBM's sign-in service: " + e.getMessage(), e);
            }
            if (response.ok()) {
                JsonNode body = MAPPER.readTree(response.body);
                String value = text(body, "token", text(body, "access_token", null));
                if (value != null) {
                    long ttl = body.path("expires_in").asLong(DEFAULT_TOKEN_TTL_SECONDS);
                    token = value;
                    tokenExpiresAtMs = clockMs.getAsLong() + ttl * 1000L;
                    return;
                }
            }
            failures.add(response);
        }
        // A rejected key only when every sign-in endpoint rejected it; any server error is transient.
        boolean rejected = !failures.isEmpty();
        int lastStatus = 0;
        for (WatsonxTransport.Response failure : failures) {
            lastStatus = failure.status;
            rejected &= failure.status == 400 || failure.status == 401 || failure.status == 403;
        }
        if (rejected) {
            throw new WatsonxApiException(401, KEY_REJECTED, true);
        }
        throw new WatsonxApiException(lastStatus, "IBM's sign-in service failed (HTTP " + lastStatus + ")", false);
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isTextual() && !value.asText().isEmpty() ? value.asText() : fallback;
    }
}
