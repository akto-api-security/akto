package com.akto.utils;

import com.akto.dao.AuthTokensDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.database_abstractor_authenticator.JwtAuthenticator;
import com.akto.dto.AuthToken;
import com.akto.dto.User;
import com.akto.util.AuthTokenIds;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import org.bson.conversions.Bson;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Single place that mints and records every token generated from the dashboard.
public class AuthTokenService {

    public static final int MAX_ACTIVE_TOKENS = 100;
    public static final int MAX_NAME_LENGTH = 64;
    public static final int MAX_REASON_LENGTH = 256;
    public static final String EXPIRY_ERROR = "Expiry time must be between 1 and 24 months";

    public static class CreatedToken {
        public final String apiToken;
        public final String tokenId;
        public final String hint;

        CreatedToken(String apiToken, String tokenId, String hint) {
            this.apiToken = apiToken;
            this.tokenId = tokenId;
            this.hint = hint;
        }
    }

    // Throws IllegalArgumentException with a user-facing message on invalid input or when the cap is hit.
    public static CreatedToken createToken(String name, List<String> scope, int expiryMonths, int userId, String userEmail) {
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty() || cleanName.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Token name must be between 1 and " + MAX_NAME_LENGTH + " characters");
        }
        if (expiryMonths == 0 || expiryMonths > 24 || expiryMonths < -1) {
            throw new IllegalArgumentException(EXPIRY_ERROR);
        }
        int now = Context.now();
        checkActiveTokenLimit(now);

        List<String> cleanScope = scope == null ? new ArrayList<>() : scope;
        String apiToken = mintJwt(cleanScope, expiryMonths);
        String tokenId = AuthTokenIds.idOf(apiToken);
        assertIdFree(tokenId);

        AuthToken record = new AuthToken();
        record.setId(tokenId);
        record.setName(cleanName);
        record.setHint(AuthTokenIds.hintOf(apiToken));
        record.setScope(cleanScope);
        record.setCreatedBy(userId);
        record.setCreatedByEmail(userEmail);
        record.setCreatedAt(now);
        record.setExpiresAt(expiryMonths < 0 ? 0 : expiryTs(expiryMonths));
        record.setDeleteAt(AuthTokenIds.deleteAtOf(record.getExpiresAt()));
        record.setStatus(AuthToken.STATUS_ACTIVE);
        record.setSource(AuthToken.SOURCE_GENERATED);
        AuthTokensDao.instance.insertOne(record);

        return new CreatedToken(apiToken, tokenId, record.getHint());
    }

    // For callers without a session user, such as QuickStartAction.createTokenForAuth.
    public static CreatedToken createTokenForContextUser(String name, List<String> scope, int expiryMonths) {
        Integer userId = Context.userId.get();
        User user = userId == null ? null : UsersDao.instance.findOne(Filters.eq("_id", userId));
        return createToken(name, scope, expiryMonths, userId == null ? 0 : userId, user == null ? "" : user.getLogin());
    }

    public static String defaultName(String prefix) {
        return prefix + "-" + LocalDate.now();
    }

    // Idempotent: returns false when the token does not exist, true once it is revoked.
    public static boolean revoke(String tokenId, String reason, String revokedBy) {
        AuthToken token = AuthTokensDao.instance.findOne(Filters.eq(AuthToken.ID, tokenId));
        if (token == null) {
            return false;
        }
        if (!AuthToken.STATUS_REVOKED.equals(token.getStatus())) {
            AuthTokensDao.instance.updateOneNoUpsert(Filters.eq(AuthToken.ID, tokenId), revokeUpdate(reason, revokedBy));
        }
        return true;
    }

    // Every active, unexpired token, generated or discovered; a token cyborg has not seen yet has no record to revoke.
    public static long revokeAllActive(String reason, String revokedBy) {
        return AuthTokensDao.instance.updateManyNoUpsert(
                Filters.and(
                        Filters.eq(AuthToken.STATUS, AuthToken.STATUS_ACTIVE),
                        Filters.or(Filters.eq(AuthToken.EXPIRES_AT, 0), Filters.gt(AuthToken.EXPIRES_AT, Context.now()))
                ),
                revokeUpdate(reason, revokedBy)
        ).getModifiedCount();
    }

    private static void checkActiveTokenLimit(int now) {
        long activeTokens = AuthTokensDao.instance.getMCollection().countDocuments(Filters.and(
                Filters.eq(AuthToken.STATUS, AuthToken.STATUS_ACTIVE),
                Filters.eq(AuthToken.SOURCE, AuthToken.SOURCE_GENERATED),
                Filters.or(Filters.eq(AuthToken.EXPIRES_AT, 0), Filters.gt(AuthToken.EXPIRES_AT, now))
        ));
        if (activeTokens >= MAX_ACTIVE_TOKENS) {
            throw new IllegalArgumentException("Too many active tokens (limit " + MAX_ACTIVE_TOKENS + "). Revoke unused tokens first.");
        }
    }

    // Identical claims minted within the same second give a byte-identical token, so the id would collide.
    public static void assertIdFree(String tokenId) {
        if (AuthTokensDao.instance.findOne(Filters.eq(AuthToken.ID, tokenId)) != null) {
            throw new IllegalArgumentException("A token with the same settings was just created. Wait a second and try again.");
        }
    }

    private static String mintJwt(List<String> scope, int expiryMonths) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("accountId", Context.accountId.get());
        if (!scope.isEmpty()) {
            claims.put("scope", scope);
        }
        try {
            return JwtAuthenticator.createJWT(claims, "Akto", "invite_user", Calendar.MONTH, expiryMonths);
        } catch (Exception e) {
            throw new IllegalStateException("Could not create the token", e);
        }
    }

    private static Bson revokeUpdate(String reason, String revokedBy) {
        String trimmed = reason == null ? "" : reason.trim();
        return Updates.combine(
                Updates.set(AuthToken.STATUS, AuthToken.STATUS_REVOKED),
                Updates.set(AuthToken.REVOKED_AT, Context.now()),
                Updates.set(AuthToken.REVOKED_BY, revokedBy),
                Updates.set(AuthToken.REVOKE_REASON, trimmed.length() > MAX_REASON_LENGTH ? trimmed.substring(0, MAX_REASON_LENGTH) : trimmed)
        );
    }

    private static int expiryTs(int months) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, months);
        return (int) (calendar.getTimeInMillis() / 1000L);
    }
}
