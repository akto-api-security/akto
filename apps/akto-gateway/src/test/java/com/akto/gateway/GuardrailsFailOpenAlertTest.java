package com.akto.gateway;

import okhttp3.*;
import com.akto.utils.OperationalAlerts;
import org.junit.Test;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.*;
import static org.junit.Assert.*;

public class GuardrailsFailOpenAlertTest {
    /** {@code failure} non-null makes the call throw before any response arrives. */
    private Map<String, Object> call(int status, String body, IOException failure, List<String> alerts) {
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
            if (failure != null) throw failure;
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(status).message("test").body(ResponseBody.create(body, MediaType.get("application/json"))).build();
        }).build();
        GuardrailsClient client = new GuardrailsClient("http://guardrails.test", http,
                (key, message) -> alerts.add(key + "\n" + message));
        Map<String, Object> request = new HashMap<>();
        request.put("akto_account_id", "untrusted-request-account");
        request.put("requestPayload", "SECRET_PAYLOAD");
        return client.callValidateRequest(request);
    }

    private Map<String, Object> call(int status, String body, List<String> alerts) {
        return call(status, body, null, alerts);
    }

    @Test public void unreachableServiceAlertsAndStillAllows() {
        List<IOException> failures = Arrays.asList(
                new ConnectException("Failed to connect to guardrails.test/127.0.0.1:9091"),
                new UnknownHostException("guardrails.test"),
                new SocketTimeoutException("timed out"));
        for (IOException failure : failures) {
            List<String> alerts = new ArrayList<>();
            Map<String, Object> result = call(200, "{}", failure, alerts);

            assertEquals(Boolean.TRUE, result.get("Allowed"));
            assertEquals(Boolean.TRUE, result.get("failOpen"));
            assertEquals(failure.getClass().getSimpleName(), 1, alerts.size());
            assertTrue(alerts.get(0).contains("Failure: " + failure.getClass().getSimpleName()));
            assertTrue(alerts.get(0).contains("Account: " + OperationalAlerts.deploymentAccountId()));
            assertFalse(alerts.get(0).contains("untrusted-request-account"));
            assertFalse(alerts.get(0).contains("SECRET_PAYLOAD"));
        }
    }

    /**
     * Anything the service answered is not an unreachable service, however unhelpful the answer:
     * error statuses, unparseable bodies and its own failOpen verdict all stay silent.
     */
    @Test public void aServiceThatAnsweredNeverAlerts() {
        List<String> alerts = new ArrayList<>();

        assertEquals(Boolean.TRUE, call(503, "{}", alerts).get("failOpen"));
        assertEquals(Boolean.TRUE, call(404, "{}", alerts).get("failOpen"));
        assertEquals(Boolean.TRUE, call(200, "broken json", alerts).get("failOpen"));
        assertEquals(Boolean.TRUE, call(200, "{\"Allowed\":true,\"failOpen\":true}", alerts).get("failOpen"));

        assertTrue(alerts.toString(), alerts.isEmpty());
    }

    @Test public void normalAllowAndPolicyBlockDoNotAlert() {
        for (boolean allowed : Arrays.asList(true, false)) {
            List<String> alerts = new ArrayList<>();
            Map<String, Object> result = call(200, "{\"Allowed\":" + allowed + "}", alerts);
            assertEquals(allowed, result.get("Allowed"));
            assertTrue(alerts.isEmpty());
        }
    }

    @Test public void preservesExistingVerdictFormats() {
        List<String> alerts = new ArrayList<>();
        assertEquals(Boolean.FALSE, call(200, "{\"allowed\":false}", alerts).get("allowed"));
        assertEquals("false", call(200, "{\"Allowed\":\"false\"}", alerts).get("Allowed"));
        assertNull(call(200, "null", alerts));
        assertTrue(alerts.isEmpty());
    }
}
