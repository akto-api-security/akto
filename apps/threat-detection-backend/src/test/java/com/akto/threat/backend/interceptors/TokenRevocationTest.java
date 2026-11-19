package com.akto.threat.backend.interceptors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.akto.util.AuthTokenIds;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenRevocationTest {

  private final AtomicInteger now = new AtomicInteger(1_700_000_000);
  private TokenRevocation.Loader originalLoader;
  private IntSupplier originalClock;

  @BeforeEach
  void setUp() {
    originalLoader = TokenRevocation.loader;
    originalClock = TokenRevocation.clock;
    TokenRevocation.clock = now::get;
    TokenRevocation.resetForTests();
  }

  @AfterEach
  void tearDown() {
    TokenRevocation.loader = originalLoader;
    TokenRevocation.clock = originalClock;
    TokenRevocation.resetForTests();
  }

  @Test
  void aRevokedIdIsRejectedAndOthersPass() {
    TokenRevocation.loader = accountId -> new HashSet<>(Collections.singletonList("revoked"));

    assertTrue(TokenRevocation.isRevoked(7, "revoked"));
    assertFalse(TokenRevocation.isRevoked(7, "other"));
    assertFalse(TokenRevocation.isRevoked(7, null));
  }

  @Test
  void theRevokedListIsCachedWithinTheTtlAndReloadedAfter() {
    AtomicInteger loads = new AtomicInteger();
    TokenRevocation.loader =
        accountId -> new HashSet<>(Collections.singletonList("revoked-" + loads.incrementAndGet()));

    assertTrue(TokenRevocation.isRevoked(7, "revoked-1"));
    now.addAndGet(TokenRevocation.CACHE_TTL_SECONDS - 1);
    assertTrue(TokenRevocation.isRevoked(7, "revoked-1"));
    assertEquals(1, loads.get());

    now.addAndGet(1);
    assertTrue(TokenRevocation.isRevoked(7, "revoked-2"));
    assertFalse(TokenRevocation.isRevoked(7, "revoked-1"));
    assertEquals(2, loads.get());
  }

  @Test
  void eachAccountHasItsOwnRevokedList() {
    TokenRevocation.loader = accountId -> new HashSet<>(Collections.singletonList("only-for-" + accountId));

    assertTrue(TokenRevocation.isRevoked(1, "only-for-1"));
    assertFalse(TokenRevocation.isRevoked(2, "only-for-1"));
    assertTrue(TokenRevocation.isRevoked(2, "only-for-2"));
  }

  @Test
  void aFailedLoadNeverBlocksARequest() {
    TokenRevocation.loader =
        accountId -> {
          throw new IllegalStateException("mongo down");
        };

    assertFalse(TokenRevocation.isRevoked(7, "anything"));
  }

  @Test
  void aFailedReloadKeepsTheLastKnownIdsAndRetriesAfterAShortWait() {
    AtomicInteger attempts = new AtomicInteger();
    TokenRevocation.loader = accountId -> new HashSet<>(Arrays.asList("a", "b"));
    assertTrue(TokenRevocation.isRevoked(7, "a"));

    TokenRevocation.loader =
        accountId -> {
          attempts.incrementAndGet();
          throw new IllegalStateException("mongo down");
        };
    now.addAndGet(TokenRevocation.CACHE_TTL_SECONDS);
    assertTrue(TokenRevocation.isRevoked(7, "b"));
    assertTrue(TokenRevocation.isRevoked(7, "a"));
    assertEquals(1, attempts.get());

    now.addAndGet(TokenRevocation.FAILED_RELOAD_RETRY_SECONDS);
    assertTrue(TokenRevocation.isRevoked(7, "a"));
    assertEquals(2, attempts.get());
  }

  @Test
  void anExchangedTokenIsIdentifiedByItsParentAndAnyOtherByItsSignatureHash() {
    Claims exchanged = Jwts.claims();
    exchanged.put("tid", "parent-id");
    assertEquals("parent-id", TokenRevocation.tokenIdOf(exchanged, "h.p.abc"));

    Claims emptyTid = Jwts.claims();
    emptyTid.put("tid", "");
    assertEquals(AuthTokenIds.idOf("h.p.abc"), TokenRevocation.tokenIdOf(emptyTid, "h.p.abc"));
    assertEquals(AuthTokenIds.idOf("h.p.abc"), TokenRevocation.tokenIdOf(Jwts.claims(), "x.y.abc"));
    assertEquals(
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        TokenRevocation.tokenIdOf(Jwts.claims(), "header.payload.abc"));
  }

  @Test
  void aRevokedParentAlsoRejectsItsExchangedToken() {
    String parentId = AuthTokenIds.idOf("raw.parent.signature");
    TokenRevocation.loader = accountId -> new HashSet<>(Collections.singletonList(parentId));

    Claims exchanged = Jwts.claims();
    exchanged.put("tid", parentId);
    assertTrue(TokenRevocation.isRevoked(7, TokenRevocation.tokenIdOf(exchanged, "exchanged.token.sig")));
  }
}
