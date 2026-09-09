package com.akto.gateway;

import okhttp3.*;
import com.akto.utils.OperationalAlerts;
import org.junit.Test;
import java.net.SocketTimeoutException;
import java.util.*;
import static org.junit.Assert.*;

public class GuardrailsFailOpenAlertTest {
    private Map<String, Object> call(int status, String body, boolean timeout, List<String> alerts) {
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
            if (timeout) throw new SocketTimeoutException("timed out");
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

    @Test public void timeoutsHttpErrorsAndInvalidResponsesAlertAndStillAllow() {
        for (String scenario : Arrays.asList("timeout", "http", "json")) {
            List<String> alerts = new ArrayList<>();
            String body = scenario.equals("json") ? "broken json" : "{}";
            Map<String, Object> result = call(scenario.equals("http") ? 503 : 200,
                    body, scenario.equals("timeout"), alerts);
            assertEquals(Boolean.TRUE, result.get("Allowed"));
            assertEquals(Boolean.TRUE, result.get("failOpen"));
            assertEquals(1, alerts.size());
            assertTrue(alerts.get(0).contains("Account: " + OperationalAlerts.deploymentAccountId()));
            assertFalse(alerts.get(0).contains("untrusted-request-account"));
            assertFalse(alerts.get(0).contains("SECRET_PAYLOAD"));
        }
    }

    @Test public void normalAllowAndPolicyBlockDoNotAlert() {
        for (boolean allowed : Arrays.asList(true, false)) {
            List<String> alerts = new ArrayList<>();
            Map<String, Object> result = call(200, "{\"Allowed\":" + allowed + "}", false, alerts);
            assertEquals(allowed, result.get("Allowed"));
            assertTrue(alerts.isEmpty());
        }
    }

    @Test public void upstreamFailOpenAlerts() {
        List<String> alerts = new ArrayList<>();
        call(200, "{\"Allowed\":true,\"failOpen\":true}", false, alerts);
        assertEquals(1, alerts.size());
    }

    @Test public void preservesExistingVerdictFormats() {
        List<String> alerts = new ArrayList<>();
        assertEquals(Boolean.FALSE, call(200, "{\"allowed\":false}", false, alerts).get("allowed"));
        assertEquals("false", call(200, "{\"Allowed\":\"false\"}", false, alerts).get("Allowed"));
        assertNull(call(200, "null", false, alerts));
        assertTrue(alerts.isEmpty());
    }
}
