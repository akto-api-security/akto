package com.akto.utils;

import com.akto.util.AuthTokenIds;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TokenRegistryTest {

    private static TokenRegistry.RevokedLoader originalLoader;
    private static java.util.function.IntSupplier originalClock;
    private final AtomicInteger now = new AtomicInteger(1_700_000_000);

    @BeforeClass
    public static void captureDefaults() {
        originalLoader = TokenRegistry.loader;
        originalClock = TokenRegistry.clock;
    }

    @Before
    public void setUp() {
        TokenRegistry.resetForTests();
        TokenRegistry.clock = now::get;
    }

    @After
    public void tearDown() {
        TokenRegistry.loader = originalLoader;
        TokenRegistry.clock = originalClock;
        TokenRegistry.resetForTests();
    }

    private Claims claims(String tid, Integer iat, Integer exp) {
        Claims c = Jwts.claims();
        if (tid != null) c.put("tid", tid);
        if (iat != null) c.setIssuedAt(new Date(iat * 1000L));
        if (exp != null) c.setExpiration(new Date(exp * 1000L));
        return c;
    }

    @Test
    public void effectiveIdIsTheParentTidOrTheSignatureHash() {
        assertEquals("parent", TokenRegistry.effectiveId(claims("parent", null, null), "a.b.sig"));
        assertEquals(AuthTokenIds.idOf("a.b.sig"), TokenRegistry.effectiveId(claims(null, null, null), "a.b.sig"));
        assertEquals(AuthTokenIds.idOf("a.b.sig"), TokenRegistry.effectiveId(claims("", null, null), "a.b.sig"));
    }

    @Test
    public void idDependsOnlyOnTheSignature() {
        assertEquals(AuthTokenIds.idOf("a.b.c"), AuthTokenIds.idOf("a.b.c"));
        assertEquals(AuthTokenIds.idOf("a.b.c"), AuthTokenIds.idOf("x.y.c"));
        assertEquals(AuthTokenIds.idOf("a.b.c"), AuthTokenIds.idOf("Bearer a.b.c"));
        assertFalse(AuthTokenIds.idOf("a.b.c").equals(AuthTokenIds.idOf("a.b.d")));
    }

    @Test
    public void hintIsTheSignatureTail() {
        assertEquals("qrstuvwxyz", AuthTokenIds.hintOf("eyJhbGciOiJSUzI1NiJ9.payload.abcdefghijklmnopqrstuvwxyz"));
        assertEquals("abc", AuthTokenIds.hintOf("abc"));
    }

    @Test
    public void neverExpiringTokenIsDiscovered() {
        assertTrue(TokenRegistry.shouldDiscover(claims(null, now.get(), null)));
    }

    @Test
    public void tokenLivingAtLeastADayIsDiscoveredAndShorterIsNot() {
        int iat = now.get();
        assertTrue(TokenRegistry.shouldDiscover(claims(null, iat, iat + 24 * 3600)));
        assertFalse(TokenRegistry.shouldDiscover(claims(null, iat, iat + 24 * 3600 - 1)));
        assertFalse(TokenRegistry.shouldDiscover(claims(null, iat, iat + 3 * 3600)));
        assertTrue(TokenRegistry.shouldDiscover(claims(null, iat, iat + 365 * 24 * 3600)));
    }

    @Test
    public void expiryWithoutIssuedAtIsMeasuredFromNow() {
        assertFalse(TokenRegistry.shouldDiscover(claims(null, null, now.get() + 3600)));
        assertTrue(TokenRegistry.shouldDiscover(claims(null, null, now.get() + 30 * 24 * 3600)));
    }

    @Test
    public void exchangedTokensAreNeverDiscovered() {
        assertFalse(TokenRegistry.shouldDiscover(claims("tid", now.get(), null)));
    }

    @Test
    public void revokedIdsAreCachedWithinTtlAndReloadedAfter() {
        AtomicInteger loads = new AtomicInteger();
        TokenRegistry.loader = accountId -> {
            loads.incrementAndGet();
            return new HashSet<>(Collections.singletonList("revoked-" + loads.get()));
        };

        assertTrue(TokenRegistry.isRevoked(7, "revoked-1"));
        now.addAndGet(TokenRegistry.CACHE_TTL_SECONDS - 1);
        assertTrue(TokenRegistry.isRevoked(7, "revoked-1"));
        assertEquals(1, loads.get());

        now.addAndGet(1);
        assertTrue(TokenRegistry.isRevoked(7, "revoked-2"));
        assertFalse(TokenRegistry.isRevoked(7, "revoked-1"));
        assertEquals(2, loads.get());
    }

    @Test
    public void cacheIsPerAccount() {
        TokenRegistry.loader = accountId -> new HashSet<>(Collections.singletonList("only-for-" + accountId));

        assertTrue(TokenRegistry.isRevoked(1, "only-for-1"));
        assertFalse(TokenRegistry.isRevoked(2, "only-for-1"));
        assertTrue(TokenRegistry.isRevoked(2, "only-for-2"));
    }

    @Test
    public void failsOpenWhenTheFirstLoadFails() {
        TokenRegistry.loader = accountId -> { throw new IllegalStateException("mongo down"); };

        assertFalse(TokenRegistry.isRevoked(7, "anything"));
        assertTrue(TokenRegistry.revokedIds(7).isEmpty());
    }

    @Test
    public void failedReloadKeepsTheLastKnownRevokedIds() {
        TokenRegistry.loader = accountId -> new HashSet<>(Arrays.asList("a", "b"));
        assertTrue(TokenRegistry.isRevoked(7, "a"));

        TokenRegistry.loader = accountId -> { throw new IllegalStateException("mongo down"); };
        now.addAndGet(TokenRegistry.CACHE_TTL_SECONDS);
        Set<String> ids = TokenRegistry.revokedIds(7);

        assertEquals(new HashSet<>(Arrays.asList("a", "b")), ids);
        assertTrue(TokenRegistry.isRevoked(7, "b"));
    }

    @Test
    public void aFailedReloadIsRetriedAfterAShortWaitNotOnEveryRequest() {
        AtomicInteger attempts = new AtomicInteger();
        TokenRegistry.loader = accountId -> { attempts.incrementAndGet(); throw new IllegalStateException("down"); };

        TokenRegistry.revokedIds(7);
        TokenRegistry.revokedIds(7);
        assertEquals(1, attempts.get());

        now.addAndGet(TokenRegistry.FAILED_RELOAD_RETRY_SECONDS - 1);
        TokenRegistry.revokedIds(7);
        assertEquals(1, attempts.get());

        now.addAndGet(1);
        TokenRegistry.revokedIds(7);
        assertEquals(2, attempts.get());
    }

    @Test
    public void syncEndpointListIsAlwaysFreshAndRefreshesTheCache() {
        AtomicInteger loads = new AtomicInteger();
        TokenRegistry.loader = accountId -> new HashSet<>(Collections.singletonList("revoked-" + loads.incrementAndGet()));

        assertTrue(TokenRegistry.isRevoked(7, "revoked-1"));
        assertEquals(Collections.singletonList("revoked-2"), TokenRegistry.revokedIdList(7));
        assertEquals(2, loads.get());

        assertTrue(TokenRegistry.isRevoked(7, "revoked-2"));
        assertEquals(2, loads.get());
    }

    @Test
    public void nullTokenIdIsNeverRevoked() {
        TokenRegistry.loader = accountId -> new HashSet<>(Collections.singletonList("a"));
        assertFalse(TokenRegistry.isRevoked(7, null));
    }
}
