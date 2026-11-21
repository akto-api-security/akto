package com.akto.database_abstractor_authenticator;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestJwtAuthenticator {

    private final AtomicInteger loads = new AtomicInteger();
    private volatile KeyPair current;
    private volatile boolean dbDown;

    private static KeyPair newKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        return gen.generateKeyPair();
    }

    private static String token(KeyPair pair, String accountId) {
        return Jwts.builder()
                .claim("accountId", accountId)
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(pair.getPrivate())
                .compact();
    }

    private PublicKeyCache originalCache;

    @BeforeEach
    public void setUp() throws Exception {
        current = newKeyPair();
        originalCache = JwtAuthenticator.publicKeyCache;
        JwtAuthenticator.publicKeyCache = new PublicKeyCache(() -> {
            loads.incrementAndGet();
            if (dbDown) throw new IllegalStateException("db down");
            return current.getPublic();
        });
    }

    @AfterEach
    public void tearDown() {
        JwtAuthenticator.publicKeyCache = originalCache;
    }

    @Test
    public void testKeyIsLoadedOnceAcrossManyRequests() throws Exception {
        String jws = token(current, "1");
        for (int i = 0; i < 1000; i++) {
            Claims claims = JwtAuthenticator.authenticate(jws).getBody();
            assertEquals("1", claims.get("accountId"));
        }
        assertEquals(1, loads.get());
    }

    @Test
    public void testConcurrentFirstRequestsLoadOnce() throws Exception {
        String jws = token(current, "1");
        int threads = 64;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                for (int j = 0; j < 200; j++) JwtAuthenticator.authenticate(jws);
                return true;
            }));
        }
        start.countDown();
        for (Future<Boolean> f : results) assertTrue(f.get(30, TimeUnit.SECONDS));
        pool.shutdown();
        assertEquals(1, loads.get());
    }

    @Test
    public void testRotatedKeyIsAcceptedAfterOneReload() throws Exception {
        JwtAuthenticator.authenticate(token(current, "1"));
        assertEquals(1, loads.get());

        current = newKeyPair();
        Claims claims = JwtAuthenticator.authenticate(token(current, "2")).getBody();

        assertEquals("2", claims.get("accountId"));
        assertEquals(2, loads.get());
    }

    @Test
    public void testForgedTokenIsRejectedAndReloadIsRateLimited() throws Exception {
        JwtAuthenticator.authenticate(token(current, "1"));
        KeyPair attacker = newKeyPair();

        for (int i = 0; i < 50; i++) {
            try {
                JwtAuthenticator.authenticate(token(attacker, "1"));
                fail("forged token accepted");
            } catch (SignatureException expected) {
                // rejected
            }
        }
        // 1 initial load + at most 1 forced reload within the min-reload window
        assertEquals(2, loads.get());
        // the genuine key still works afterwards
        JwtAuthenticator.authenticate(token(current, "1"));
    }

    @Test
    public void testGarbageAndNullTokensDoNotReload() throws Exception {
        JwtAuthenticator.authenticate(token(current, "1"));
        for (String bad : new String[]{"garbage", "a.b.c", ""}) {
            try {
                JwtAuthenticator.authenticate(bad);
                fail("accepted " + bad);
            } catch (Exception expected) {
                // rejected
            }
        }
        try {
            JwtAuthenticator.authenticate(null);
            fail("accepted null");
        } catch (Exception expected) {
            // rejected
        }
        assertEquals(1, loads.get());
    }

    @Test
    public void testExpiredTokenStillThrowsExpired() throws Exception {
        String expired = Jwts.builder()
                .claim("accountId", "1")
                .setExpiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(current.getPrivate())
                .compact();
        try {
            JwtAuthenticator.authenticate(expired);
            fail("expired token accepted");
        } catch (io.jsonwebtoken.ExpiredJwtException expected) {
            assertEquals("1", expected.getClaims().get("accountId"));
        }
        assertEquals(1, loads.get());
    }

    @Test
    public void testFirstLoadFailureIsNotCachedAndPropagates() throws Exception {
        dbDown = true;
        try {
            JwtAuthenticator.authenticate(token(current, "1"));
            fail("expected failure with no cached key");
        } catch (IllegalStateException expected) {
            // same behaviour as before: caller turns this into a 401
        }
        dbDown = false;
        assertNotNull(JwtAuthenticator.authenticate(token(current, "1")));
    }

    @Test
    public void testStaleKeyKeepsWorkingWhenRefreshFails() throws Exception {
        JwtAuthenticator.authenticate(token(current, "1"));
        // Rotate the key while the DB is down: a forced reload fails, the cached key is kept
        KeyPair rotated = newKeyPair();
        dbDown = true;
        try {
            JwtAuthenticator.authenticate(token(rotated, "1"));
            fail("token for an unknown key accepted");
        } catch (SignatureException expected) {
            // rejected, but no exception from the failed reload leaks out
        }
        // tokens for the cached key still authenticate during the outage
        assertNotNull(JwtAuthenticator.authenticate(token(current, "1")));
    }
}
