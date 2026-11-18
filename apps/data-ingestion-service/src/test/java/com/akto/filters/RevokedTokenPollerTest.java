package com.akto.filters;

import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import okhttp3.OkHttpClient;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class RevokedTokenPollerTest {

    // sha256("abc"); the Go services and libs/dao pin the same value
    private static final String ABC_SIGNATURE_ID = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Test
    public void tidWinsOverTheTokensOwnSignature() {
        Claims claims = Jwts.claims();
        claims.put("tid", "parent");
        assertEquals("parent", RevokedTokenPoller.tokenIdOf(claims, "h.p.abc"));
    }

    @Test
    public void emptyTidIsIgnored() {
        Claims claims = Jwts.claims();
        claims.put("tid", "");
        assertEquals(ABC_SIGNATURE_ID, RevokedTokenPoller.tokenIdOf(claims, "h.p.abc"));
    }

    @Test
    public void tokenWithoutTidUsesTheSignatureHash() {
        assertEquals(ABC_SIGNATURE_ID, RevokedTokenPoller.tokenIdOf(Jwts.claims(), "header.payload.abc"));
    }

    @Test
    public void bearerPrefixAndWhitespaceGiveTheSameId() {
        for (String raw : new String[]{"h.p.abc", "  h.p.abc\n", "Bearer h.p.abc", "bearer   h.p.abc ", "BEARER other.payload.abc"}) {
            assertEquals(raw, ABC_SIGNATURE_ID, RevokedTokenPoller.tokenIdOf(Jwts.claims(), raw));
        }
    }

    @Test
    public void sameClaimsWithDifferentSignaturesGetDifferentIds() {
        assertNotEquals(RevokedTokenPoller.tokenIdOf(Jwts.claims(), "h.p.abc"), RevokedTokenPoller.tokenIdOf(Jwts.claims(), "h.p.abd"));
    }

    @Test
    public void revokedSignatureIdIsRejectedAndOthersPass() throws Exception {
        HttpServer server = serverReturning(200, "{\"revokedTokenIds\":[\"" + ABC_SIGNATURE_ID + "\"]}",
                new AtomicReference<>(), new AtomicReference<>(), new AtomicInteger());
        try {
            RevokedTokenPoller poller = pollerFor(server);
            assertTrue(poller.refresh());

            assertTrue(poller.isRevoked(RevokedTokenPoller.tokenIdOf(Jwts.claims(), "Bearer h.p.abc")));
            assertFalse(poller.isRevoked(RevokedTokenPoller.tokenIdOf(Jwts.claims(), "h.p.abd")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void refreshLoadsRevokedIdsAndSendsTheRawServiceToken() throws Exception {
        AtomicReference<String> authHeader = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        HttpServer server = serverReturning(200, "{\"revokedTokenIds\":[\"a\",\"b\"]}", authHeader, path, new AtomicInteger());
        try {
            RevokedTokenPoller poller = pollerFor(server);

            assertFalse(poller.isRevoked("a"));
            assertTrue(poller.refresh());
            assertTrue(poller.isRevoked("a"));
            assertTrue(poller.isRevoked("b"));
            assertFalse(poller.isRevoked("c"));
            assertEquals("svc-token", authHeader.get());
            assertEquals("/api/fetchRevokedTokenIds", path.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void failedRefreshKeepsLastKnownSet() throws Exception {
        AtomicInteger status = new AtomicInteger(200);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = "{\"revokedTokenIds\":[\"a\"]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), status.get() == 200 ? body.length : -1);
            if (status.get() == 200) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        try {
            RevokedTokenPoller poller = pollerFor(server);
            assertTrue(poller.refresh());

            status.set(500);
            assertFalse(poller.refresh());
            assertTrue("a failed poll must keep the last set", poller.isRevoked("a"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void failedPollIsRetriedSoonerThanTheNormalInterval() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            boolean firstCall = calls.incrementAndGet() == 1;
            byte[] body = "{\"revokedTokenIds\":[\"a\"]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(firstCall ? 500 : 200, firstCall ? -1 : body.length);
            if (!firstCall) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/api";
            RevokedTokenPoller poller = new RevokedTokenPoller(() -> url, () -> "svc-token", new OkHttpClient(), 3_600_000L, 50L);
            poller.ensureStarted();

            long deadline = System.currentTimeMillis() + 3000;
            while (!poller.isRevoked("a") && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertTrue("the retry must load the set long before the normal interval", poller.isRevoked("a"));
            assertTrue(calls.get() >= 2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void unreachableCyborgFailsOpen() {
        RevokedTokenPoller poller = new RevokedTokenPoller(
                () -> "http://127.0.0.1:1/api", () -> "svc-token", new OkHttpClient());
        assertFalse(poller.refresh());
        assertFalse(poller.isRevoked("anything"));
    }

    @Test
    public void missingServiceTokenKeepsPollingOff() {
        RevokedTokenPoller poller = new RevokedTokenPoller(() -> "http://127.0.0.1:1/api", () -> null, new OkHttpClient());
        poller.ensureStarted();
        poller.ensureStarted();
        assertFalse(poller.isRevoked("anything"));
    }

    private static RevokedTokenPoller pollerFor(HttpServer server) {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/api";
        return new RevokedTokenPoller(() -> url, () -> "svc-token", new OkHttpClient());
    }

    private static HttpServer serverReturning(int status, String json, AtomicReference<String> authHeader,
                                              AtomicReference<String> path, AtomicInteger calls) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            path.set(exchange.getRequestURI().getPath());
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }
}
