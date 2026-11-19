package com.akto.threat.backend.interceptors;

import com.akto.dao.AuthTokensDao;
import com.akto.dao.context.Context;
import com.akto.util.AuthTokenIds;
import io.jsonwebtoken.Claims;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

// Rejects tokens an admin revoked in the dashboard. The revoked ids are read from the account's auth_tokens, cached per
// account for 15 minutes, and a failed read keeps the last known ids so a database problem never blocks requests.
public final class TokenRevocation {

  static final int CACHE_TTL_SECONDS = 15 * 60;
  static final int FAILED_RELOAD_RETRY_SECONDS = 30;

  interface Loader {
    Set<String> load(int accountId) throws Exception;
  }

  private static final class Entry {
    final Set<String> revokedIds;
    final int loadedAt;

    Entry(Set<String> revokedIds, int loadedAt) {
      this.revokedIds = revokedIds;
      this.loadedAt = loadedAt;
    }
  }

  // Overridable in tests; the default reads auth_tokens of the given account.
  static Loader loader =
      accountId -> {
        Context.accountId.set(accountId);
        try {
          return AuthTokensDao.instance.fetchRevokedIds(Context.now());
        } finally {
          Context.accountId.remove();
        }
      };
  static IntSupplier clock = Context::now;

  private static final Map<Integer, Entry> cache = new ConcurrentHashMap<>();

  private TokenRevocation() {}

  // An exchanged token carries its parent's id as tid; any other token is its own id, the hash of its signature.
  public static String tokenIdOf(Claims claims, String rawToken) {
    Object tid = claims.get("tid");
    return tid == null || tid.toString().isEmpty() ? AuthTokenIds.idOf(rawToken) : tid.toString();
  }

  public static boolean isRevoked(int accountId, String tokenId) {
    return tokenId != null && revokedIds(accountId).contains(tokenId);
  }

  private static Set<String> revokedIds(int accountId) {
    int now = clock.getAsInt();
    Entry cached = cache.get(accountId);
    if (cached != null && now - cached.loadedAt < CACHE_TTL_SECONDS) {
      return cached.revokedIds;
    }
    try {
      Set<String> ids = Collections.unmodifiableSet(new HashSet<>(loader.load(accountId)));
      cache.put(accountId, new Entry(ids, now));
      return ids;
    } catch (Exception e) {
      System.out.println("Failed to load revoked tokens for account " + accountId + ", failing open: " + e);
      Set<String> stale = cached != null ? cached.revokedIds : Collections.emptySet();
      // retry soon rather than after the full TTL, but not on every request while the database is down
      cache.put(accountId, new Entry(stale, now - CACHE_TTL_SECONDS + FAILED_RELOAD_RETRY_SECONDS));
      return stale;
    }
  }

  // Test hook: forget every cached entry.
  static void resetForTests() {
    cache.clear();
  }
}
