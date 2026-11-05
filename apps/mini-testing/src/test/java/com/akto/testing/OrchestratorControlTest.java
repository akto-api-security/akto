package com.akto.testing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.prometheus.metrics.exporter.httpserver.HTTPServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class OrchestratorControlTest {

    private static final String TOKEN = "test-token";
    private static final long DEFAULT_TTL_MILLIS = 120_000;

    private HTTPServer server;

    @Before
    public void setUp() throws IOException {
        OrchestratorControl.configure(true, TOKEN, DEFAULT_TTL_MILLIS);
        HTTPServer.Builder builder = HTTPServer.builder().port(0);
        OrchestratorControl.attach(builder);
        server = builder.buildAndStart();
    }

    @After
    public void tearDown() {
        server.stop();
        OrchestratorControl.configure(false, null, DEFAULT_TTL_MILLIS);
    }

    private static final class Reply {
        final int code;
        final String body;

        Reply(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }

    private Reply call(String method, String path, String token) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + server.getPort() + path).openConnection();
        conn.setRequestMethod(method);
        if (token != null) {
            conn.setRequestProperty("X-Akto-Orchestrator-Token", token);
        }
        int code = conn.getResponseCode();
        InputStream in = code < 400 ? conn.getInputStream() : conn.getErrorStream();
        String body = "";
        if (in != null) {
            try (Scanner sc = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
                body = sc.hasNext() ? sc.next() : "";
            }
        }
        return new Reply(code, body);
    }

    private Reply call(String method, String path) throws IOException {
        return call(method, path, TOKEN);
    }

    @Test
    public void idlePodReportsNotBusy() throws IOException {
        Reply r = call("GET", "/status");
        assertEquals(200, r.code);
        assertEquals("{\"busy\":false,\"draining\":false}", r.body);
    }

    @Test
    public void rejectsMissingOrWrongToken() throws IOException {
        assertEquals(401, call("GET", "/status", null).code);
        assertEquals(401, call("POST", "/drain", "wrong").code);
        assertTrue(OrchestratorControl.tryStartPolling());
    }

    @Test
    public void unknownPathsAndMethodsAre404() throws IOException {
        assertEquals(404, call("GET", "/nope").code);
        assertEquals(404, call("GET", "/drain").code);
        assertEquals(404, call("POST", "/status").code);
    }

    @Test
    public void drainStopsClaimingUntilUndrained() throws IOException {
        Reply drained = call("POST", "/drain");
        assertEquals("{\"busy\":false,\"draining\":true}", drained.body);
        assertFalse(OrchestratorControl.tryStartPolling());

        Reply undrained = call("POST", "/undrain");
        assertEquals("{\"busy\":false,\"draining\":false}", undrained.body);
        assertTrue(OrchestratorControl.tryStartPolling());
    }

    @Test
    public void claimedRunReadsBusyAndIsNotDrainedAway() throws IOException {
        assertTrue(OrchestratorControl.tryStartPolling());
        OrchestratorControl.markRunClaimed();
        assertEquals("{\"busy\":true,\"draining\":false}", call("GET", "/status").body);
        // the orchestrator undrains a pod that answers busy here
        assertEquals("{\"busy\":true,\"draining\":true}", call("POST", "/drain").body);
    }

    @Test
    public void pollInProgressIsNotBusyAndDrainWaitsForIt() throws Exception {
        assertTrue(OrchestratorControl.tryStartPolling());
        // mid-poll, the pod holds nothing
        assertEquals("{\"busy\":false,\"draining\":false}", call("GET", "/status").body);

        Thread poll = new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            OrchestratorControl.markPollIdle();
        });
        poll.start();
        long start = System.currentTimeMillis();
        Reply r = call("POST", "/drain");
        long waited = System.currentTimeMillis() - start;
        poll.join();

        assertEquals("{\"busy\":false,\"draining\":true}", r.body);
        assertTrue("drain should wait for the in-flight poll, waited " + waited + "ms", waited >= 250);
    }

    @Test
    public void pollThatClaimsDuringDrainReportsBusy() throws Exception {
        assertTrue(OrchestratorControl.tryStartPolling());
        Thread poll = new Thread(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            OrchestratorControl.markRunClaimed();
        });
        poll.start();
        Reply r = call("POST", "/drain");
        poll.join();
        assertEquals("{\"busy\":true,\"draining\":true}", r.body);
    }

    @Test
    public void drainLapsesWithoutRenewal() throws Exception {
        OrchestratorControl.configure(true, TOKEN, 200);
        call("POST", "/drain");
        assertFalse(OrchestratorControl.tryStartPolling());

        Thread.sleep(300);
        assertTrue(OrchestratorControl.tryStartPolling());
        assertEquals("{\"busy\":false,\"draining\":false}", call("GET", "/status").body);
    }

    @Test
    public void drainedPodDoesNotResumeLeftoverRun() throws IOException {
        call("POST", "/drain");
        assertFalse(OrchestratorControl.markResuming());
        assertEquals("{\"busy\":false,\"draining\":true}", call("GET", "/status").body);
    }

    @Test
    public void resumingPodReadsBusy() throws IOException {
        assertTrue(OrchestratorControl.markResuming());
        assertEquals("{\"busy\":true,\"draining\":false}", call("GET", "/status").body);
    }

    @Test
    public void drainedPodWithFinishedRunIsNotReportedBusy() throws IOException {
        assertTrue(OrchestratorControl.tryStartPolling());
        OrchestratorControl.markRunClaimed();
        call("POST", "/drain");
        // the run finished; the next poll is refused and the pod holds nothing
        assertFalse(OrchestratorControl.tryStartPolling());
        assertEquals("{\"busy\":false,\"draining\":true}", call("GET", "/status").body);
    }

    @Test
    public void disabledControlIsANoOp() throws IOException {
        call("POST", "/drain");
        OrchestratorControl.configure(false, TOKEN, DEFAULT_TTL_MILLIS);
        call("POST", "/drain");
        assertTrue(OrchestratorControl.tryStartPolling());
        assertTrue(OrchestratorControl.markResuming());
        OrchestratorControl.markRunClaimed();
        OrchestratorControl.markPollIdle();
        assertTrue(OrchestratorControl.tryStartPolling());
    }
}
