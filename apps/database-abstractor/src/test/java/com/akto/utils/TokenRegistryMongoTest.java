package com.akto.utils;

import com.akto.dao.AuthTokensDao;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.data_actor.DbLayer;
import com.akto.dto.AuthToken;
import com.akto.dto.monitoring.ModuleInfo;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class TokenRegistryMongoTest extends MongoBasedTest {

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
        Context.accountId.set(ACCOUNT_ID);
        Context.tokenId.remove();
        TokenRegistry.resetForTests();
        TokenRegistry.loader = originalLoader;
        TokenRegistry.clock = now::get;
        AuthTokensDao.instance.getMCollection().deleteMany(new BasicDBObject());
        ModuleInfoDao.instance.getMCollection().deleteMany(new BasicDBObject());
    }

    @After
    public void tearDown() {
        TokenRegistry.clock = originalClock;
        TokenRegistry.resetForTests();
        Context.tokenId.remove();
    }

    private AuthToken token(String id, String status, int expiresAt) {
        AuthToken t = new AuthToken();
        t.setId(id);
        t.setName(id);
        t.setStatus(status);
        t.setExpiresAt(expiresAt);
        t.setSource(AuthToken.SOURCE_GENERATED);
        t.setScope(Collections.emptyList());
        return t;
    }

    private Claims legacyClaims(int iat, Integer exp) {
        Claims c = Jwts.claims();
        c.put("accountId", ACCOUNT_ID);
        c.put("scope", Arrays.asList("MINI_RUNTIME"));
        c.setIssuedAt(new Date(iat * 1000L));
        if (exp != null) c.setExpiration(new Date(exp * 1000L));
        return c;
    }

    private ModuleInfo module(String id, String name, String forgedTokenId) {
        ModuleInfo m = new ModuleInfo();
        m.setId(id);
        m.setName(name);
        m.setModuleType(ModuleInfo.ModuleType.MINI_RUNTIME);
        m.setCurrentVersion("1.0");
        m.setStartedTs(now.get());
        m.setLastHeartbeatReceived((int) (System.currentTimeMillis() / 1000));
        m.setAdditionalData(new HashMap<>());
        m.setTokenId(forgedTokenId);
        return m;
    }

    @Test
    public void defaultLoaderReturnsOnlyRevokedTokensThatHaveNotExpired() {
        int future = Context.now() + 3600;
        int past = Context.now() - 3600;
        AuthTokensDao.instance.insertOne(token("revoked-forever", AuthToken.STATUS_REVOKED, 0));
        AuthTokensDao.instance.insertOne(token("revoked-live", AuthToken.STATUS_REVOKED, future));
        AuthTokensDao.instance.insertOne(token("revoked-expired", AuthToken.STATUS_REVOKED, past));
        AuthTokensDao.instance.insertOne(token("active", AuthToken.STATUS_ACTIVE, 0));
        TokenRegistry.clock = originalClock;

        assertEquals(new HashSet<>(Arrays.asList("revoked-forever", "revoked-live")), TokenRegistry.revokedIds(ACCOUNT_ID));
    }

    @Test
    public void legacyTokenIsRegisteredOnceAsDiscovered() {
        int iat = now.get();
        Claims claims = legacyClaims(iat, iat + 365 * 24 * 3600);
        String raw = "header.payload.signaturetail";
        String id = TokenRegistry.effectiveId(claims, raw);

        TokenRegistry.recordUse(claims, raw, id);

        AuthToken saved = AuthTokensDao.instance.findOne(Filters.eq(AuthToken.ID, id));
        assertNotNull(saved);
        assertEquals(AuthToken.SOURCE_DISCOVERED, saved.getSource());
        assertEquals(AuthToken.STATUS_ACTIVE, saved.getStatus());
        assertEquals("naturetail", saved.getHint());
        assertEquals(Arrays.asList("MINI_RUNTIME"), saved.getScope());
        assertEquals(iat, saved.getCreatedAt());
        assertEquals(iat + 365 * 24 * 3600, saved.getExpiresAt());
        assertEquals((iat + 365L * 24 * 3600 + 30L * 24 * 3600) * 1000L, saved.getDeleteAt().getTime());
        assertEquals(1, AuthTokensDao.instance.getMCollection().countDocuments());
    }

    @Test
    public void rediscoveryDoesNotResurrectARevokedToken() {
        int iat = now.get();
        Claims claims = legacyClaims(iat, null);
        String raw = "header.payload.sig";
        String id = TokenRegistry.effectiveId(claims, raw);
        TokenRegistry.recordUse(claims, raw, id);
        assertNull(AuthTokensDao.instance.findOne(Filters.eq(AuthToken.ID, id)).getDeleteAt());
        AuthTokensDao.instance.getMCollection().updateOne(Filters.eq(AuthToken.ID, id),
                new BasicDBObject("$set", new BasicDBObject(AuthToken.STATUS, AuthToken.STATUS_REVOKED)));

        TokenRegistry.resetForTests();
        TokenRegistry.recordUse(claims, raw, id);

        assertEquals(AuthToken.STATUS_REVOKED, AuthTokensDao.instance.findOne(Filters.eq(AuthToken.ID, id)).getStatus());
    }

    @Test
    public void shortLivedInternalTokensAreNotMarkedUsedEither() {
        int iat = now.get();
        Claims claims = legacyClaims(iat, iat + 3 * 3600);
        String raw = "header.payload.internal2";
        String id = TokenRegistry.effectiveId(claims, raw);
        AuthTokensDao.instance.insertOne(token(id, AuthToken.STATUS_ACTIVE, 0));

        TokenRegistry.recordUse(claims, raw, id);

        assertEquals(0, AuthTokensDao.instance.findOne(Filters.eq(AuthToken.ID, id)).getLastUsedAt());
    }

    @Test
    public void shortLivedInternalTokensAreNotRegistered() {
        int iat = now.get();
        Claims claims = legacyClaims(iat, iat + 3 * 3600);
        String raw = "header.payload.internal";

        TokenRegistry.recordUse(claims, raw, TokenRegistry.effectiveId(claims, raw));

        assertEquals(0, AuthTokensDao.instance.getMCollection().countDocuments());
    }

    @Test
    public void lastUsedIsThrottledToOneWritePerMinute() {
        AuthTokensDao.instance.insertOne(token("tok-1", AuthToken.STATUS_ACTIVE, 0));
        Claims claims = Jwts.claims();
        claims.put("moduleType", "GUARDRAIL");

        TokenRegistry.recordUse(claims, "raw", "tok-1");
        int first = now.get();
        assertEquals(first, AuthTokensDao.instance.findOne(Filters.eq(AuthToken.ID, "tok-1")).getLastUsedAt());
        assertEquals("GUARDRAIL", AuthTokensDao.instance.findOne(Filters.eq(AuthToken.ID, "tok-1")).getLastUsedModuleType());

        now.addAndGet(TokenRegistry.LAST_USED_THROTTLE_SECONDS - 1);
        TokenRegistry.recordUse(claims, "raw", "tok-1");
        assertEquals(first, AuthTokensDao.instance.findOne(Filters.eq(AuthToken.ID, "tok-1")).getLastUsedAt());

        now.addAndGet(1);
        TokenRegistry.recordUse(claims, "raw", "tok-1");
        assertEquals(now.get(), AuthTokensDao.instance.findOne(Filters.eq(AuthToken.ID, "tok-1")).getLastUsedAt());
    }

    @Test
    public void recordingUseOfAnExchangedTokenNeverCreatesARow() {
        Claims claims = Jwts.claims();
        claims.put("tid", "not-in-registry");

        TokenRegistry.recordUse(claims, "raw", "not-in-registry");

        assertEquals(0, AuthTokensDao.instance.getMCollection().countDocuments());
    }

    @Test
    public void exchangeRecordsTheModuleTypeOnTheToken() {
        AuthTokensDao.instance.insertOne(token("tok-2", AuthToken.STATUS_ACTIVE, 0));

        TokenRegistry.recordModuleType("tok-2", "MINI_TESTING");

        AuthToken saved = AuthTokensDao.instance.findOne(Filters.eq(AuthToken.ID, "tok-2"));
        assertEquals("MINI_TESTING", saved.getLastUsedModuleType());
        assertEquals(now.get(), saved.getLastUsedAt());
    }

    @Test
    public void heartbeatStampsTheVerifiedTokenIdAndIgnoresThePayload() {
        Context.tokenId.set("verified-token");

        DbLayer.updateModuleInfo(module("m-1", "runtime-1", "forged-from-client"));

        assertEquals("verified-token", ModuleInfoDao.instance.findOne(Filters.eq(ModuleInfoDao.ID, "m-1")).getTokenId());
    }

    @Test
    public void heartbeatWithoutAVerifiedTokenStampsNothing() {
        DbLayer.updateModuleInfo(module("m-2", "runtime-2", "forged-from-client"));

        assertNull(ModuleInfoDao.instance.findOne(Filters.eq(ModuleInfoDao.ID, "m-2")).getTokenId());
    }

    @Test
    public void heartbeatKeepsThePreviousTokenIdWhenNoneIsVerified() {
        Context.tokenId.set("first-token");
        DbLayer.updateModuleInfo(module("m-3", "runtime-3", null));
        Context.tokenId.remove();

        DbLayer.updateModuleInfo(module("m-3", "runtime-3", null));

        assertEquals("first-token", ModuleInfoDao.instance.findOne(Filters.eq(ModuleInfoDao.ID, "m-3")).getTokenId());
    }

    @Test
    public void heartbeatV2StampsTheVerifiedTokenId() {
        Context.tokenId.set("verified-v2");

        DbLayer.updateModuleInfoForHeartbeatV2(module("m-4", "runtime-4", "forged-from-client"));

        assertEquals("verified-v2", ModuleInfoDao.instance.findOne(Filters.eq(ModuleInfo.NAME, "runtime-4")).getTokenId());
    }

    @Test
    public void bulkHeartbeatStampsTheVerifiedTokenIdOnEveryModule() {
        Context.tokenId.set("verified-bulk");

        DbLayer.bulkUpdateModuleInfo(Arrays.asList(module("m-5", "runtime-5", "forged"), module("m-6", "runtime-6", null)));

        assertEquals("verified-bulk", ModuleInfoDao.instance.findOne(Filters.eq(ModuleInfoDao.ID, "m-5")).getTokenId());
        assertEquals("verified-bulk", ModuleInfoDao.instance.findOne(Filters.eq(ModuleInfoDao.ID, "m-6")).getTokenId());
    }
}
