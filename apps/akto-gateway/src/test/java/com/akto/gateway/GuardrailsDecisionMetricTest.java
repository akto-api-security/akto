package com.akto.gateway;

import com.akto.metrics.AktoMetrics;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Verifies the akto.guardrails.validations counter GuardrailsClient records on every callValidate.
 * A fresh SimpleMeterRegistry is added to the global registry per test so the counters
 * GuardrailsClient registers on Metrics.globalRegistry are observable and isolated.
 */
public class GuardrailsDecisionMetricTest {

    private static final String METRIC = AktoMetrics.GUARDRAILS_VALIDATIONS;
    private static final String ENDPOINT = "/api/validate/request";

    private SimpleMeterRegistry registry;

    @Before
    public void setUp() {
        registry = new SimpleMeterRegistry();
        Metrics.addRegistry(registry);
    }

    @After
    public void tearDown() {
        Metrics.removeRegistry(registry);
        registry.close();
    }

    /** {@code failure} non-null makes the call throw before any response arrives. */
    private void call(int status, String body, IOException failure) {
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
            if (failure != null) throw failure;
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(status).message("test")
                    .body(ResponseBody.create(body, MediaType.get("application/json"))).build();
        }).build();
        GuardrailsClient client = new GuardrailsClient("http://guardrails.test", http, (key, message) -> { });
        Map<String, Object> request = new HashMap<>();
        request.put("akto_account_id", "untrusted-request-account");
        client.callValidateRequest(request);
    }

    private double count(String decision) {
        try {
            return registry.get(METRIC).tag("endpoint", ENDPOINT).tag("decision", decision).counter().count();
        } catch (MeterNotFoundException e) {
            return 0.0;
        }
    }

    @Test
    public void allowedVerdictIsCounted() {
        double before = count("allowed");
        call(200, "{\"allowed\":true}", null);
        assertEquals(before + 1, count("allowed"), 0.0);
    }

    @Test
    public void blockedVerdictIsCounted() {
        double before = count("blocked");
        call(200, "{\"allowed\":false}", null);
        assertEquals(before + 1, count("blocked"), 0.0);
    }

    @Test
    public void answeredWithoutVerdictIsUnknownNotAllowed() {
        double beforeUnknown = count("unknown");
        double beforeAllowed = count("allowed");
        call(200, "{}", null); // real 200 answer, but no allowed/Allowed field
        assertEquals(beforeUnknown + 1, count("unknown"), 0.0);
        assertEquals(beforeAllowed, count("allowed"), 0.0);
    }

    @Test
    public void failOpenPathsAreCounted() {
        double before = count("fail_open");
        call(0, null, new SocketTimeoutException("timed out")); // transport timeout
        call(500, "{}", null);                                  // non-2xx
        call(200, "broken json", null);                         // unparseable body
        assertEquals(before + 3, count("fail_open"), 0.0);
    }
}
