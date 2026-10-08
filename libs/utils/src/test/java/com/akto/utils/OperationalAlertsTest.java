package com.akto.utils;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;
import okhttp3.*;

public class OperationalAlertsTest {
    @Test public void missingOrInvalidWebhookHasActionableSafeDiagnostic() {
        assertTrue(OperationalAlerts.configurationProblem(null).contains("AKTO_SLACK_ALERT_WEBHOOK"));
        assertNotNull(OperationalAlerts.configurationProblem("  "));
        assertNotNull(OperationalAlerts.configurationProblem("not-a-url-secret"));
        assertFalse(OperationalAlerts.configurationProblem("not-a-url-secret").contains("not-a-url-secret"));
        assertNull(OperationalAlerts.configurationProblem(" https://hooks.slack.com/services/test "));
    }

    @Test public void reportsSlackHttpStatusWithoutResponseBodyOrWebhook() throws Exception {
        for (int status : new int[]{200, 403, 404, 429, 500}) {
            Response response = new Response.Builder().request(new Request.Builder()
                    .url("https://hooks.slack.com/services/SECRET_WEBHOOK").build())
                    .protocol(Protocol.HTTP_1_1).code(status).message("test")
                    .body(ResponseBody.create("SECRET_BODY", MediaType.get("text/plain"))).build();
            try (Response ignored = response) {
                try {
                    OperationalAlerts.checkResponse(response);
                    fail("Must require a successful acknowledgement");
                } catch (RuntimeException error) {
                    String description = OperationalAlerts.failureDescription(error);
                    assertTrue(description.contains("HTTP " + status));
                    assertFalse(description.contains("SECRET"));
                }
            }
        }
        assertEquals("SocketTimeoutException", OperationalAlerts.failureDescription(
                new IllegalStateException("outer", new java.net.SocketTimeoutException("secret URL"))));
    }

    @Test public void suppressesDuplicatesButNotOtherAccountsAndResumesAfterCooldown() {
        AtomicLong clock = new AtomicLong(1000);
        List<String> messages = new ArrayList<>();
        OperationalAlerts alerts = new OperationalAlerts(Runnable::run, messages::add, clock::get, 300);
        alerts.submit("account-a:topic", "first");
        alerts.submit("account-a:topic", "duplicate");
        alerts.submit("account-b:topic", "other account");
        assertEquals(2, messages.size());
        clock.addAndGet(300);
        alerts.submit("account-a:topic", "reminder");
        assertEquals(3, messages.size());
    }

    @Test public void defersNetworkWorkAndContainsSenderFailures() {
        List<Runnable> tasks = new ArrayList<>();
        OperationalAlerts alerts = new OperationalAlerts(tasks::add,
                message -> { throw new IllegalStateException("unavailable"); }, () -> 1000, 300);
        alerts.submit("key", "message");
        assertEquals(1, tasks.size());
        tasks.get(0).run(); // Delivery failure must not escape.
    }

    @Test public void queueSaturationDoesNotEscapeToRequestThread() {
        OperationalAlerts alerts = new OperationalAlerts(task -> { throw new RejectedExecutionException(); },
                message -> fail("Must not send"), () -> 1000, 300);
        alerts.submit("key", "message");
    }
}
