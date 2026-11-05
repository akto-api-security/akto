package com.akto.testing;

import com.akto.log.LoggerMaker;
import com.sun.net.httpserver.HttpExchange;
import io.prometheus.metrics.exporter.httpserver.HTTPServer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lets mini-testing-orchestrator scale this module without Prometheus or KEDA. It adds
 * GET /status, POST /drain and POST /undrain to the metrics server's port (see
 * PrometheusMetricsHandler); the orchestrator drains an idle pod before removing it, so a pod
 * running a test is never killed.
 *
 * Enabled by ORCHESTRATOR_ENABLED, which needs AUTO_SCALING_ENABLED for the server to start.
 * Every public method is a no-op otherwise, so callers need no flag checks of their own.
 */
public class OrchestratorControl {
    private static final String TOKEN_HEADER = "X-Akto-Orchestrator-Token";
    // How long /drain waits for an in-flight poll to settle; below the orchestrator's 3s timeout.
    private static final long DRAIN_SETTLE_MILLIS = 2000;

    // Read from the environment at startup; only tests change them, through configure().
    private static volatile boolean enabled = Boolean.parseBoolean(System.getenv("AUTO_SCALING_ENABLED"))
            && Boolean.parseBoolean(System.getenv("ORCHESTRATOR_ENABLED"));
    private static volatile String token = System.getenv("ORCHESTRATOR_TOKEN");
    // A drain lapses unless renewed, so a pod never stays out of service if the orchestrator
    // dies or fails to remove it. The orchestrator removes a drained pod within seconds.
    private static volatile long drainTtlMillis = 1000L * parseSeconds(System.getenv("ORCHESTRATOR_DRAIN_TTL_SECONDS"), 120L);
    private static final LoggerMaker loggerMaker = new LoggerMaker(OrchestratorControl.class, LoggerMaker.LogDb.TESTING);

    // holdingRun: this pod has a test run (claimed, running, or resuming after a restart) and
    // keeps it until a poll finds nothing. It is what /status reports as busy.
    // claiming: true from the start of every poll for work, about once a second, until a poll
    // finds nothing; it covers the moment a run is being claimed. Only /drain looks at it, since
    // reporting it as busy would make every idle pod look busy whenever /status lands mid-poll.
    // drainedUntil (epoch millis, 0 = not drained) stops further claims until it passes.
    // claiming and drainedUntil are written in opposite order by the poll loop and /drain: either
    // the loop sees the drain and claims nothing, or /drain sees claiming.
    // All start false: a pod that cannot reach cyborg at startup must not read as busy.
    private static volatile boolean holdingRun = false;
    private static volatile boolean claiming = false;
    private static final AtomicLong drainedUntil = new AtomicLong(0);

    /** For tests: sets what the environment would, and clears all state. */
    static void configure(boolean isEnabled, String controlToken, long ttlMillis) {
        enabled = isEnabled;
        token = controlToken;
        drainTtlMillis = ttlMillis;
        holdingRun = false;
        claiming = false;
        drainedUntil.set(0);
    }

    /** Adds the orchestrator endpoints to the metrics server. */
    static void attach(HTTPServer.Builder builder) {
        if (!enabled) {
            return;
        }
        // the default handler gets every path except /metrics and /-/healthy
        builder.defaultHandler(OrchestratorControl::handle);
        loggerMaker.warn("Serving mini-testing-orchestrator endpoints on the metrics port");
    }

    /** Call before looking for a test run. Returns false when the orchestrator has drained this pod. */
    public static boolean tryStartPolling() {
        if (!enabled) {
            return true;
        }
        claiming = true;
        long until = drainedUntil.get();
        if (until != 0 && System.currentTimeMillis() >= until && drainedUntil.compareAndSet(until, 0)) {
            // compareAndSet: a renewal racing with this keeps the pod drained
            loggerMaker.warnAndAddToDb("Drain by mini-testing-orchestrator expired after " + drainTtlMillis / 1000
                    + "s without this pod being removed; taking test runs again");
        }
        if (isDraining()) {
            // no run is held while drained; a stale holdingRun would read as busy
            holdingRun = false;
            claiming = false;
            return false;
        }
        return true;
    }

    private static boolean isDraining() {
        return System.currentTimeMillis() < drainedUntil.get();
    }

    private static long parseSeconds(String value, long fallback) {
        try {
            long seconds = Long.parseLong(value);
            return seconds > 0 ? seconds : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Call before resuming a run left over from before a restart. Returns false if the
     * orchestrator drained this pod meanwhile: it may already be on its way out, so the run is
     * left for another pod to pick up, as when a pod is lost.
     */
    public static boolean markResuming() {
        if (!enabled) {
            return true;
        }
        claiming = true;
        if (isDraining()) {
            claiming = false;
            loggerMaker.warnAndAddToDb("Not resuming the previous test run: this pod was drained by mini-testing-orchestrator");
            return false;
        }
        holdingRun = true;
        return true;
    }

    /** Call once a poll returned a run this pod will take. */
    public static void markRunClaimed() {
        if (!enabled) {
            return;
        }
        holdingRun = true;
    }

    /** Call when a poll found no run this pod can take. */
    public static void markPollIdle() {
        if (!enabled) {
            return;
        }
        holdingRun = false;
        claiming = false;
    }

    // Logs locally only: an *AndAddToDb call blocks on cyborg and would time out the orchestrator.
    private static void handle(HttpExchange exchange) throws IOException {
        try {
            if (!isAuthorized(exchange)) {
                respond(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if ("/status".equals(path) && "GET".equals(method)) {
                respond(exchange, 200, statusJson(holdingRun));
            } else if ("/drain".equals(path) && "POST".equals(method)) {
                // also renews an existing drain
                drainedUntil.set(System.currentTimeMillis() + drainTtlMillis);
                // A poll already past the draining check may still claim a run. Wait for it to
                // either find nothing or claim one, so an idle poll isn't reported as busy.
                long deadline = System.currentTimeMillis() + DRAIN_SETTLE_MILLIS;
                while (claiming && !holdingRun && System.currentTimeMillis() < deadline) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                // still claiming after the wait means a claim may be in flight: report busy
                String status = statusJson(claiming);
                loggerMaker.warn("Drained by orchestrator, status: " + status);
                respond(exchange, 200, status);
            } else if ("/undrain".equals(path) && "POST".equals(method)) {
                drainedUntil.set(0);
                String status = statusJson(holdingRun);
                loggerMaker.warn("Undrained by orchestrator, status: " + status);
                respond(exchange, 200, status);
            } else {
                respond(exchange, 404, "{\"error\":\"not found\"}");
            }
        } finally {
            exchange.close();
        }
    }

    private static boolean isAuthorized(HttpExchange exchange) {
        String expected = token;
        if (expected == null || expected.isEmpty()) {
            return true;
        }
        String given = exchange.getRequestHeaders().getFirst(TOKEN_HEADER);
        return given != null && MessageDigest.isEqual(
                given.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    private static String statusJson(boolean busy) {
        return "{\"busy\":" + busy + ",\"draining\":" + isDraining() + "}";
    }

    private static void respond(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
