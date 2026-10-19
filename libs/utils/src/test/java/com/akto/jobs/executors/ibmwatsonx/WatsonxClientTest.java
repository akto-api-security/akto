package com.akto.jobs.executors.ibmwatsonx;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Sign-in with the API key, token renewal, and how the client handles IBM's answers. */
public class WatsonxClientTest {

    private FakeWatsonx ibm;
    private AtomicLong clock;

    @BeforeEach
    public void setUp() {
        ibm = new FakeWatsonx();
        clock = new AtomicLong(1_000_000_000L);
    }

    private WatsonxClient client(String instanceUrl) {
        WatsonxClient client = new WatsonxClient(instanceUrl, new WatsonxAuth(instanceUrl, "api-key-1", ibm, clock::get), ibm);
        client.retryDelayMs = 0;
        return client;
    }

    private static WatsonxApiException failure(ThrowingCall call) {
        try {
            call.run();
        } catch (WatsonxApiException e) {
            return e;
        } catch (Exception e) {
            fail("expected WatsonxApiException, got " + e);
        }
        fail("expected WatsonxApiException");
        return null;
    }

    interface ThrowingCall {
        void run() throws Exception;
    }

    // ---------------------------------------------------------------------------------------
    // Sign-in
    // ---------------------------------------------------------------------------------------

    @Test
    public void signsInWithTheApiKeyAndSendsTheToken() throws Exception {
        client(FakeWatsonx.INSTANCE).agents();
        FakeWatsonx.Call signIn = ibm.callsTo(WatsonxAuth.MCSP_V2_URL).get(0);
        assertEquals("{\"apikey\":\"api-key-1\"}", signIn.body);
        FakeWatsonx.Call agents = ibm.callsTo("/v1/orchestrate/agents").get(0);
        assertEquals(FakeWatsonx.INSTANCE + "/v1/orchestrate/agents", agents.url);
        assertEquals("Bearer token-1", agents.headers.get("Authorization"));
    }

    @Test
    public void tokenIsReusedAndRenewedBeforeItExpires() throws Exception {
        WatsonxClient client = client(FakeWatsonx.INSTANCE);
        client.agents();
        client.tools();
        assertEquals(1, ibm.count(WatsonxAuth.MCSP_V2_URL));
        clock.addAndGet(3600_000L - WatsonxAuth.RENEW_BEFORE_EXPIRY_MS);
        client.tools();
        assertEquals(2, ibm.count(WatsonxAuth.MCSP_V2_URL));
    }

    @Test
    public void fallsBackToTheV1SignInService() throws Exception {
        ibm.on(WatsonxAuth.MCSP_V2_URL, 404, "{}")
                .on(WatsonxAuth.MCSP_V1_URL, 200, "{\"token\":\"token-v1\",\"expires_in\":7200}");
        client(FakeWatsonx.INSTANCE).agents();
        assertEquals("Bearer token-v1", ibm.callsTo("/v1/orchestrate/agents").get(0).headers.get("Authorization"));
    }

    @Test
    public void ibmCloudInstancesSignInThroughIam() throws Exception {
        String cloudInstance = "https://api.us-south.watson-orchestrate.cloud.ibm.com/instances/instance-2";
        ibm.on(WatsonxAuth.IAM_URL, 200, "{\"access_token\":\"iam-token\",\"expires_in\":3600}");
        client(cloudInstance).agents();
        assertEquals(0, ibm.count(WatsonxAuth.MCSP_V2_URL));
        FakeWatsonx.Call signIn = ibm.callsTo(WatsonxAuth.IAM_URL).get(0);
        assertTrue(signIn.body.startsWith("grant_type=urn%3Aibm%3Aparams%3Aoauth%3Agrant-type%3Aapikey&apikey=api-key-1"));
        assertEquals("Bearer iam-token", ibm.callsTo("/v1/orchestrate/agents").get(0).headers.get("Authorization"));
    }

    @Test
    public void keyRejectedEverywhereIsAnAuthFailure() {
        ibm.on(WatsonxAuth.MCSP_V2_URL, 401, "{}").on(WatsonxAuth.MCSP_V1_URL, 400, "{}");
        WatsonxApiException e = failure(() -> client(FakeWatsonx.INSTANCE).agents());
        assertTrue(e.isAuthFailure());
        assertFalse(e.isRetryable());
        assertEquals(WatsonxAuth.KEY_REJECTED, e.getMessage());
    }

    @Test
    public void signInServiceOutageIsRetryableNotARejectedKey() {
        ibm.on(WatsonxAuth.MCSP_V2_URL, 401, "{}").on(WatsonxAuth.MCSP_V1_URL, 503, "{}");
        WatsonxApiException e = failure(() -> client(FakeWatsonx.INSTANCE).agents());
        assertFalse(e.isAuthFailure());
        assertTrue(e.isRetryable());
    }

    @Test
    public void emptyKeyIsAnAuthFailureWithoutCallingIbm() {
        WatsonxAuth auth = new WatsonxAuth(FakeWatsonx.INSTANCE, " ", ibm, clock::get);
        assertTrue(failure(auth::token).isAuthFailure());
        assertTrue(ibm.calls.isEmpty());
    }

    // ---------------------------------------------------------------------------------------
    // API calls
    // ---------------------------------------------------------------------------------------

    @Test
    public void expiredTokenIsRenewedOnceAfter401() throws Exception {
        ibm.on(WatsonxAuth.MCSP_V2_URL, 200, "{\"token\":\"token-1\"}", 200, "{\"token\":\"token-2\"}")
                .on("/v1/orchestrate/agents", 401, "{}", 200, "[{\"id\":\"agent-1\"}]");
        assertEquals(1, client(FakeWatsonx.INSTANCE).agents().size());
        assertEquals(2, ibm.count(WatsonxAuth.MCSP_V2_URL));
        assertEquals("Bearer token-2", ibm.callsTo("/v1/orchestrate/agents").get(1).headers.get("Authorization"));
    }

    @Test
    public void tokenRejectedRightAfterSignInIsAnAuthFailure() {
        ibm.on("/v1/orchestrate/agents", 401, "{}");
        WatsonxApiException e = failure(() -> client(FakeWatsonx.INSTANCE).agents());
        assertTrue(e.isAuthFailure());
        assertEquals(2, ibm.count("/v1/orchestrate/agents"));
    }

    @Test
    public void rateLimitIsRetried() throws Exception {
        ibm.on("/v1/orchestrate/tools", 429, "{}", 200, "[{\"id\":\"tool-1\"}]");
        assertEquals(1, client(FakeWatsonx.INSTANCE).tools().size());
        assertEquals(2, ibm.count("/v1/orchestrate/tools"));
    }

    @Test
    public void persistentServerErrorFailsRetryablyAfterThreeAttempts() {
        ibm.on("/v1/orchestrate/tools", 500, "boom");
        WatsonxApiException e = failure(() -> client(FakeWatsonx.INSTANCE).tools());
        assertEquals(500, e.getStatusCode());
        assertTrue(e.isRetryable());
        assertEquals(WatsonxClient.MAX_ATTEMPTS, ibm.count("/v1/orchestrate/tools"));
    }

    @Test
    public void optionalResourcesTheUserCannotReadAreEmpty() throws Exception {
        ibm.on("/v1/orchestrate/agents/external-chat", 403, "{}").on("/v1/orchestrate/policies/bindings", 404, "{}");
        WatsonxClient client = client(FakeWatsonx.INSTANCE);
        assertEquals(0, client.externalAgents().size());
        assertEquals(0, client.controlBindings().size());
    }

    @Test
    public void wrappedListsAreUnwrapped() throws Exception {
        ibm.on("/v1/orchestrate/models/list", 200, "{\"resources\":[{\"id\":\"m1\"},{\"id\":\"m2\"}]}")
                .on("/v1/orchestrate/connections/applications", 200, "{\"applications\":[{\"connection_id\":\"c1\"}]}");
        WatsonxClient client = client(FakeWatsonx.INSTANCE);
        assertEquals(2, client.models().size());
        assertEquals(1, client.connections().size());
        assertTrue(ibm.callsTo("/connections/applications").get(0).url.endsWith("?limit=500"));
    }

    @Test
    public void tracesPageAsksForOneWindowOldestFirstWithSteps() throws Exception {
        client(FakeWatsonx.INSTANCE).tracesPage("2026-10-15T00:00:00Z", "2026-10-15T04:00:00Z", 2);
        Map<String, String> query = FakeWatsonx.query(ibm.callsTo("/v1/agentops-v3/traces").get(0).url);
        assertEquals("2026-10-15T00:00:00Z", query.get("fromTimestamp"));
        assertEquals("2026-10-15T04:00:00Z", query.get("toTimestamp"));
        assertEquals("timestamp.asc", query.get("orderBy"));
        assertEquals("2", query.get("page"));
        assertEquals(String.valueOf(WatsonxClient.TRACE_PAGE_SIZE), query.get("limit"));
        assertEquals("true", query.get("include_trace_details"));
    }

    @Test
    public void identityIsReadFromTheUiHostAndIsOptional() {
        ibm.on("/mfe_connections/api/v1/connections/agents/agent-1/identity", 200, "{\"creator_email\":\"member-1\"}");
        WatsonxClient client = client(FakeWatsonx.INSTANCE);
        assertEquals("member-1", client.identity("agent-1", false).path("creator_email").asText());
        assertTrue(ibm.callsTo("/identity").get(0).url
                .startsWith("https://eu-de.watson-orchestrate.ibm.com/mfe_connections/api/v1/connections/agents/agent-1/identity?"));
        assertEquals("native", FakeWatsonx.query(ibm.callsTo("/identity").get(0).url).get("agent_category"));
        assertNull(client.identity("agent-2", true), "not available: no identity details, no error");
    }

    @Test
    public void instanceUrlIsNormalisedAndValidated() {
        assertEquals(FakeWatsonx.INSTANCE, WatsonxClient.normalizeInstanceUrl("  " + FakeWatsonx.INSTANCE + "//  "));
        assertNull(WatsonxClient.normalizeInstanceUrl("http://api.eu-de.watson-orchestrate.ibm.com/instances/x"));
        assertNull(WatsonxClient.normalizeInstanceUrl("https://eu-de.watson-orchestrate.ibm.com/chat"));
        assertNull(WatsonxClient.normalizeInstanceUrl(null));
    }
}
