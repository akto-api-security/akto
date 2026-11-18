package com.akto.utils;

import com.akto.action.TokenExchangeAction;
import com.akto.action.TokenStatusAction;
import com.akto.dao.AuthTokensDao;
import com.akto.dao.ConfigsDao;
import com.akto.dao.context.Context;
import com.akto.database_abstractor_authenticator.JwtAuthenticator;
import com.akto.dto.AuthToken;
import com.akto.dto.Config;
import com.akto.filter.AuthFilter;
import com.akto.util.AuthTokenIds;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Proxy;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TokenRevocationFilterTest extends MongoBasedTest {

    private static final String HEARTBEAT_URI = "/api/updateModuleInfoForHeartbeat";
    private static final String DATA_URI = "/api/fetchAccountSettings";

    private static PrivateKey signingKey;

    private static class Outcome {
        int status = 200;
        boolean chainReached;
    }

    @BeforeClass
    public static void seedSigningKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        signingKey = pair.getPrivate();
        String privateKey = "-----BEGIN PRIVATE KEY-----" + Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()) + "-----END PRIVATE KEY-----";
        String publicKey = "-----BEGIN PUBLIC KEY-----" + Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()) + "-----END PUBLIC KEY-----";
        ConfigsDao.instance.getMCollection().deleteMany(new BasicDBObject());
        ConfigsDao.instance.insertOne(new Config.HybridSaasConfig(privateKey, publicKey));
    }

    @Before
    public void setUp() {
        Context.resetContextThreadLocals();
        Context.accountId.set(ACCOUNT_ID);
        TokenRegistry.resetForTests();
        AuthTokensDao.instance.getMCollection().deleteMany(new BasicDBObject());
    }

    @After
    public void tearDown() {
        TokenRegistry.resetForTests();
        Context.resetContextThreadLocals();
    }

    private String mint(Map<String, Object> extraClaims, boolean neverExpires) throws Exception {
        Map<String, Object> claims = new HashMap<>(extraClaims);
        claims.put("accountId", ACCOUNT_ID);
        if (neverExpires) {
            // the dashboard mints "never expire" tokens without an exp claim; createJWT here has no such mode
            return Jwts.builder().setIssuer("Akto").setSubject("invite_user").addClaims(claims)
                    .setIssuedAt(new Date()).signWith(signingKey).compact();
        }
        return JwtAuthenticator.createJWT(claims, "Akto", "invite_user", Calendar.MONTH, 6);
    }

    // Same claims as mint() but with an explicit issue time, so two tokens never collapse into one string.
    private String mintAt(Map<String, Object> extraClaims, Date issuedAt) {
        Map<String, Object> claims = new HashMap<>(extraClaims);
        claims.put("accountId", ACCOUNT_ID);
        return Jwts.builder().setIssuer("Akto").setSubject("invite_user").addClaims(claims)
                .setIssuedAt(issuedAt).setExpiration(new Date(issuedAt.getTime() + 180L * 24 * 3600 * 1000))
                .signWith(signingKey).compact();
    }

    private String mintExpired(Map<String, Object> extraClaims) throws Exception {
        Map<String, Object> claims = new HashMap<>(extraClaims);
        claims.put("accountId", ACCOUNT_ID);
        return JwtAuthenticator.createJWT(claims, "Akto", "invite_user", new Date(System.currentTimeMillis() - 60_000));
    }

    private Map<String, Object> claims(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private Outcome call(String token, String uri) throws Exception {
        Outcome outcome = new Outcome();
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getHeader")) return token;
                    if (method.getName().equals("getRequestURI")) return uri;
                    return null;
                });
        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpServletResponse.class}, (proxy, method, args) -> {
                    if (method.getName().equals("sendError")) outcome.status = (Integer) args[0];
                    return null;
                });
        FilterChain chain = (ServletRequest req, ServletResponse res) -> outcome.chainReached = true;
        Context.resetContextThreadLocals();
        new AuthFilter().doFilter(request, response, chain);
        return outcome;
    }

    // Stores the record the dashboard creates for a generated token: its id is the hash of the token's signature.
    private String register(String token, String status) {
        AuthToken stored = new AuthToken();
        stored.setId(AuthTokenIds.idOf(token));
        stored.setName("generated");
        stored.setStatus(status);
        stored.setSource(AuthToken.SOURCE_GENERATED);
        stored.setScope(Collections.emptyList());
        AuthTokensDao.instance.insertOne(stored);
        TokenRegistry.resetForTests();
        return stored.getId();
    }

    private void revoke(String tokenId) {
        AuthToken stored = AuthTokensDao.instance.findOne(Filters.eq(AuthToken.ID, tokenId));
        if (stored == null) {
            stored = new AuthToken();
            stored.setId(tokenId);
            stored.setName(tokenId);
            stored.setSource(AuthToken.SOURCE_GENERATED);
            stored.setScope(Collections.emptyList());
            stored.setStatus(AuthToken.STATUS_REVOKED);
            AuthTokensDao.instance.insertOne(stored);
        } else {
            AuthTokensDao.instance.getMCollection().updateOne(Filters.eq(AuthToken.ID, tokenId),
                    new BasicDBObject("$set", new BasicDBObject(AuthToken.STATUS, AuthToken.STATUS_REVOKED)));
        }
        TokenRegistry.resetForTests();
    }

    @Test
    public void validTokenPassesAndIsIdentifiedByItsSignatureHash() throws Exception {
        String token = mint(claims(), false);
        register(token, AuthToken.STATUS_ACTIVE);

        Outcome outcome = call(token, DATA_URI);

        assertTrue(outcome.chainReached);
        assertEquals(200, outcome.status);
        assertEquals(AuthTokenIds.idOf(token), Context.tokenId.get());
    }

    @Test
    public void revokedGeneratedTokenGets401AndNeverReachesTheAction() throws Exception {
        String token = mint(claims(), false);
        String id = register(token, AuthToken.STATUS_ACTIVE);
        assertTrue(call(token, DATA_URI).chainReached);

        revoke(id);
        Outcome outcome = call(token, DATA_URI);

        assertEquals(401, outcome.status);
        assertFalse(outcome.chainReached);
    }

    @Test
    public void revocationOnlyAffectsTheRevokedToken() throws Exception {
        Date now = new Date();
        String keep = mintAt(claims(), new Date(now.getTime() - 10_000));
        String gone = mintAt(claims(), now);
        assertNotEquals(AuthTokenIds.idOf(keep), AuthTokenIds.idOf(gone));
        register(keep, AuthToken.STATUS_ACTIVE);
        register(gone, AuthToken.STATUS_ACTIVE);

        revoke(AuthTokenIds.idOf(gone));

        assertTrue(call(keep, DATA_URI).chainReached);
        assertEquals(401, call(gone, DATA_URI).status);
    }

    @Test
    public void revocationIsPickedUpAfterTheCacheTtl() throws Exception {
        AtomicInteger clock = new AtomicInteger(Context.now());
        java.util.function.IntSupplier originalClock = TokenRegistry.clock;
        TokenRegistry.clock = clock::get;
        try {
            String token = mint(claims(), false);
            String id = register(token, AuthToken.STATUS_ACTIVE);
            assertTrue(call(token, DATA_URI).chainReached);

            AuthTokensDao.instance.getMCollection().updateOne(Filters.eq(AuthToken.ID, id),
                    new BasicDBObject("$set", new BasicDBObject(AuthToken.STATUS, AuthToken.STATUS_REVOKED)));
            assertTrue("still cached", call(token, DATA_URI).chainReached);

            clock.addAndGet(TokenRegistry.CACHE_TTL_SECONDS);
            assertEquals(401, call(token, DATA_URI).status);
        } finally {
            TokenRegistry.clock = originalClock;
        }
    }

    @Test
    public void rawScopedTokenCanOnlyExchangeOrListRevokedIds() throws Exception {
        String raw = mint(claims("scope", Arrays.asList("MINI_RUNTIME")), false);

        assertEquals(403, call(raw, DATA_URI).status);
        assertEquals(403, call(raw, HEARTBEAT_URI).status);
        assertTrue(call(raw, "/api/exchangeToken").chainReached);
        assertTrue(call(raw, "/api/fetchRevokedTokenIds").chainReached);
    }

    @Test
    public void exchangedTokenCarriesTheRawTokenIdAndDiesWithIt() throws Exception {
        String raw = mint(claims("scope", Arrays.asList("MINI_RUNTIME", "GUARDRAIL")), false);
        String rawId = register(raw, AuthToken.STATUS_ACTIVE);
        assertTrue(call(raw, "/api/exchangeToken").chainReached);
        TokenExchangeAction exchange = new TokenExchangeAction();
        exchange.setModuleType("GUARDRAIL");
        assertEquals("SUCCESS", exchange.exchangeToken());
        String exchanged = exchange.getToken();
        assertNotEquals(rawId, AuthTokenIds.idOf(exchanged));

        Claims body = JwtAuthenticator.authenticate(exchanged).getBody();
        assertEquals(rawId, body.get("tid"));
        assertEquals("GUARDRAIL", body.get("moduleType"));

        Outcome ok = call(exchanged, DATA_URI);
        assertTrue(ok.chainReached);
        assertEquals(rawId, Context.tokenId.get());

        revoke(rawId);
        assertEquals(401, call(exchanged, DATA_URI).status);
        assertEquals(401, call(raw, "/api/exchangeToken").status);
    }

    @Test
    public void exchangeRecordsTheModuleTypeOnTheStoredToken() throws Exception {
        String raw = mint(claims("scope", Arrays.asList("DAST")), false);
        String id = register(raw, AuthToken.STATUS_ACTIVE);

        call(raw, "/api/exchangeToken");
        TokenExchangeAction exchange = new TokenExchangeAction();
        exchange.setModuleType("DAST");
        exchange.exchangeToken();

        assertEquals("DAST", AuthTokensDao.instance.findOne(Filters.eq(AuthToken.ID, id)).getLastUsedModuleType());
    }

    @Test
    public void expiredTokenStillHeartbeatsButIsStampedWithItsId() throws Exception {
        String expired = mintExpired(claims());

        Outcome heartbeat = call(expired, HEARTBEAT_URI);

        assertTrue(heartbeat.chainReached);
        assertTrue(Boolean.TRUE.equals(Context.tokenExpired.get()));
        assertEquals(AuthTokenIds.idOf(expired), Context.tokenId.get());
        assertEquals(401, call(expired, DATA_URI).status);
    }

    @Test
    public void unknownLongLivedTokenIsDiscoveredAndCanBeRevokedById() throws Exception {
        String unknown = mint(claims(), true);
        String id = AuthTokenIds.idOf(unknown);

        Outcome first = call(unknown, DATA_URI);
        assertEquals("status", 200, first.status);
        assertTrue(first.chainReached);
        assertEquals(id, Context.tokenId.get());
        AuthToken discovered = AuthTokensDao.instance.findOne(Filters.eq(AuthToken.ID, id));
        assertNotNull(discovered);
        assertEquals(AuthToken.SOURCE_DISCOVERED, discovered.getSource());
        assertEquals(AuthTokenIds.hintOf(unknown), discovered.getHint());

        revoke(id);
        assertEquals(401, call(unknown, DATA_URI).status);
    }

    @Test
    public void generatedTokenRecordIsNotOverwrittenByDiscovery() throws Exception {
        String token = mint(claims(), true);
        String id = register(token, AuthToken.STATUS_ACTIVE);

        assertTrue(call(token, DATA_URI).chainReached);

        AuthToken stored = AuthTokensDao.instance.findOne(Filters.eq(AuthToken.ID, id));
        assertEquals(AuthToken.SOURCE_GENERATED, stored.getSource());
        assertEquals(1, AuthTokensDao.instance.getMCollection().countDocuments());
    }

    @Test
    public void shortLivedInternalTokenIsNotRegistered() throws Exception {
        Map<String, Object> internal = claims();
        internal.put("accountId", ACCOUNT_ID);
        String threeHour = JwtAuthenticator.createJWT(internal, "Akto", "invite_user", Calendar.HOUR, 3);

        assertTrue(call(threeHour, DATA_URI).chainReached);

        assertEquals(0, AuthTokensDao.instance.getMCollection().countDocuments());
    }

    @Test
    public void tamperedTokenIsStillRejected() throws Exception {
        String token = mint(claims(), false);
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertEquals(401, call(tampered, DATA_URI).status);
    }

    @Test
    public void revokedListEndpointReturnsTheAccountsRevokedIds() throws Exception {
        revoke("tok-h");
        revoke("tok-i");
        AuthToken live = new AuthToken();
        live.setId("tok-live");
        live.setStatus(AuthToken.STATUS_ACTIVE);
        live.setScope(Collections.emptyList());
        AuthTokensDao.instance.insertOne(live);
        TokenRegistry.resetForTests();
        Context.accountId.set(ACCOUNT_ID);

        TokenStatusAction action = new TokenStatusAction();
        assertEquals("SUCCESS", action.fetchRevokedTokenIds());

        assertEquals(2, action.getRevokedTokenIds().size());
        assertTrue(action.getRevokedTokenIds().containsAll(Arrays.asList("tok-h", "tok-i")));
        assertFalse(action.getRevokedTokenIds().contains("tok-live"));
    }

    @Test
    public void failingLookupFailsOpenInTheFilter() throws Exception {
        TokenRegistry.RevokedLoader originalLoader = TokenRegistry.loader;
        TokenRegistry.loader = accountId -> { throw new IllegalStateException("mongo down"); };
        try {
            Outcome outcome = call(mint(claims(), false), DATA_URI);
            assertTrue(outcome.chainReached);
            assertEquals(200, outcome.status);
        } finally {
            TokenRegistry.loader = originalLoader;
        }
    }

    @Test
    public void missingAuthorizationHeaderIsRejected() throws Exception {
        assertEquals(401, call(null, DATA_URI).status);
    }
}
