package com.akto.utils;

import com.akto.dao.AuthTokensDao;
import com.akto.dao.context.Context;
import com.akto.dto.AuthToken;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.AuthTokenIds;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import io.jsonwebtoken.Claims;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

public class TokenRegistry {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TokenRegistry.class, LogDb.DB_ABS);

    // Long on purpose to spare the database; the sync endpoint below always reads fresh.
    static final int CACHE_TTL_SECONDS = 15 * 60;
    static final int FAILED_RELOAD_RETRY_SECONDS = 30;
    static final int LAST_USED_THROTTLE_SECONDS = 60;
    // Tokens living shorter than this are internal/system tokens and are never registered.
    static final int DISCOVERY_MIN_LIFETIME_SECONDS = 24 * 60 * 60;
    static final String TOKEN_ID_CLAIM = "tid";
    static final String MODULE_TYPE_CLAIM = "moduleType";
    static final String SCOPE_CLAIM = "scope";
    private static final String DISCOVERED_NAME = "Discovered token";

    interface RevokedLoader {
        Set<String> load(int accountId) throws Exception;
    }

    private static class CacheEntry {
        final Set<String> revokedIds;
        final int loadedAt;

        CacheEntry(Set<String> revokedIds, int loadedAt) {
            this.revokedIds = revokedIds;
            this.loadedAt = loadedAt;
        }
    }

    // Overridable in tests; the default reads auth_tokens of the account set in Context.
    static RevokedLoader loader = accountId -> AuthTokensDao.instance.fetchRevokedIds(Context.now());
    static IntSupplier clock = Context::now;

    private static final Map<Integer, CacheEntry> revokedCache = new ConcurrentHashMap<>();
    private static final Map<String, Integer> lastUsedWrites = new ConcurrentHashMap<>();
    private static final Set<String> discoveredIds = ConcurrentHashMap.newKeySet();

    // An exchanged token carries its parent's id as tid; null for every other token.
    private static String parentId(Claims body) {
        Object tid = body.get(TOKEN_ID_CLAIM);
        return tid == null || tid.toString().isEmpty() ? null : tid.toString();
    }

    // Any token that is not exchanged is its own id: the hash of its signature.
    public static String effectiveId(Claims body, String rawToken) {
        String parent = parentId(body);
        return parent != null ? parent : AuthTokenIds.idOf(rawToken);
    }

    // Fails open: a load error keeps the last known ids, or none, and never blocks a request.
    public static Set<String> revokedIds(int accountId) {
        return revokedIds(accountId, false);
    }

    private static Set<String> revokedIds(int accountId, boolean forceReload) {
        int now = clock.getAsInt();
        CacheEntry cached = revokedCache.get(accountId);
        if (!forceReload && cached != null && now - cached.loadedAt < CACHE_TTL_SECONDS) {
            return cached.revokedIds;
        }
        try {
            Set<String> ids = Collections.unmodifiableSet(new HashSet<>(loader.load(accountId)));
            revokedCache.put(accountId, new CacheEntry(ids, now));
            return ids;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Failed to load revoked tokens for account " + accountId + ", failing open: " + e.getMessage());
            Set<String> stale = cached != null ? cached.revokedIds : Collections.emptySet();
            // retry soon rather than after the full TTL, but not on every request while the database is down
            revokedCache.put(accountId, new CacheEntry(stale, now - CACHE_TTL_SECONDS + FAILED_RELOAD_RETRY_SECONDS));
            return stale;
        }
    }

    public static boolean isRevoked(int accountId, String tokenId) {
        return tokenId != null && revokedIds(accountId).contains(tokenId);
    }

    // Called rarely by the polling services, so it reads fresh and they lag only by their own poll interval.
    public static List<String> revokedIdList(int accountId) {
        return new ArrayList<>(revokedIds(accountId, true));
    }

    static boolean shouldDiscover(Claims body) {
        if (parentId(body) != null) {
            return false;
        }
        Date exp = body.getExpiration();
        if (exp == null) {
            return true;
        }
        Date iat = body.getIssuedAt();
        return epochSeconds(exp) - (iat != null ? epochSeconds(iat) : clock.getAsInt()) >= DISCOVERY_MIN_LIFETIME_SECONDS;
    }

    // Never throws: usage tracking must not fail a request.
    public static void recordUse(Claims body, String rawToken, String tokenId) {
        try {
            // short-lived raw tokens are internal and never registered, so there is nothing to mark as used
            if (parentId(body) == null && !shouldDiscover(body)) {
                return;
            }
            if (shouldDiscover(body)) {
                discover(body, rawToken, tokenId);
            }
            markUsed(tokenId, stringClaim(body, MODULE_TYPE_CLAIM), false);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Failed to record token use: " + e.getMessage());
        }
    }

    // Exchange is the first call of a scoped token and the only place its module type is known.
    public static void recordModuleType(String tokenId, String moduleType) {
        try {
            if (tokenId != null) {
                markUsed(tokenId, moduleType, true);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Failed to record token module type: " + e.getMessage());
        }
    }

    private static void markUsed(String tokenId, String moduleType, boolean force) {
        int now = clock.getAsInt();
        Integer last = lastUsedWrites.get(tokenId);
        if (!force && last != null && now - last < LAST_USED_THROTTLE_SECONDS) {
            return;
        }
        lastUsedWrites.put(tokenId, now);
        Bson update = Updates.set(AuthToken.LAST_USED_AT, now);
        if (moduleType != null) {
            update = Updates.combine(update, Updates.set(AuthToken.LAST_USED_MODULE_TYPE, moduleType));
        }
        AuthTokensDao.instance.getMCollection().updateOne(Filters.eq(AuthToken.ID, tokenId), update);
    }

    private static void discover(Claims body, String rawToken, String tokenId) {
        if (discoveredIds.contains(tokenId)) {
            return;
        }
        Date exp = body.getExpiration();
        Date iat = body.getIssuedAt();
        AuthToken token = new AuthToken();
        token.setId(tokenId);
        token.setName(DISCOVERED_NAME);
        token.setHint(AuthTokenIds.hintOf(rawToken));
        token.setScope(scopeOf(body));
        token.setCreatedAt(iat != null ? epochSeconds(iat) : clock.getAsInt());
        token.setExpiresAt(exp != null ? epochSeconds(exp) : 0);
        token.setDeleteAt(AuthTokenIds.deleteAtOf(token.getExpiresAt()));
        token.setStatus(AuthToken.STATUS_ACTIVE);
        token.setSource(AuthToken.SOURCE_DISCOVERED);
        // an existing record, revoked or not, is never overwritten
        if (AuthTokensDao.instance.findOne(Filters.eq(AuthToken.ID, tokenId)) == null) {
            AuthTokensDao.instance.getMCollection().insertOne(token);
        }
        discoveredIds.add(tokenId);
    }

    private static int epochSeconds(Date date) {
        return (int) (date.getTime() / 1000);
    }

    private static List<String> scopeOf(Claims body) {
        List<String> scope = new ArrayList<>();
        Object scopeObj = body.get(SCOPE_CLAIM);
        if (scopeObj instanceof List) {
            for (Object o : (List<?>) scopeObj) {
                if (o != null) scope.add(o.toString());
            }
        }
        return scope;
    }

    private static String stringClaim(Claims body, String name) {
        Object value = body.get(name);
        return value == null ? null : value.toString();
    }

    // Test hook: forget every cached/throttled entry.
    static void resetForTests() {
        revokedCache.clear();
        lastUsedWrites.clear();
        discoveredIds.clear();
    }
}
