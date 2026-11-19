package com.akto.action;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.action.quick_start.QuickStartAction;
import com.akto.action.settings.ModuleInfoAction;
import com.akto.dao.AuthTokensDao;
import com.akto.dao.ConfigsDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.database_abstractor_authenticator.JwtAuthenticator;
import com.akto.dto.AuthToken;
import com.akto.dto.Config;
import com.akto.dto.User;
import com.akto.dto.UserAccountEntry;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.util.AuthTokenIds;
import com.akto.utils.AuthTokenService;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;

public class TestAuthTokenAction extends MongoBasedTest {

    private static final String USER = "admin@example.com";
    private User user;

    @Before
    public void setup() throws Exception {
        Context.accountId.set(ACCOUNT_ID);
        AuthTokensDao.instance.getMCollection().drop();
        ModuleInfoDao.instance.getMCollection().drop();
        UsersDao.instance.deleteAll(new BasicDBObject());
        Map<String, UserAccountEntry> accounts = new HashMap<>();
        accounts.put(ACCOUNT_ID + "", new UserAccountEntry(ACCOUNT_ID));
        user = new User("admin", USER, accounts, null);
        UsersDao.instance.insertOne(user);

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        ConfigsDao.instance.getMCollection().drop();
        ConfigsDao.instance.insertOne(new Config.HybridSaasConfig(
                Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()),
                Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())));
    }

    private AuthTokenAction action() {
        AuthTokenAction action = new AuthTokenAction();
        Map<String, Object> session = new HashMap<>();
        session.put("user", user);
        action.setSession(session);
        return action;
    }

    private AuthTokenAction create(String name, List<String> scope, int months) {
        AuthTokenAction action = action();
        action.setName(name);
        action.setScope(scope);
        action.setExpiryTimeInMonth(months);
        assertEquals(Action.SUCCESS.toUpperCase(), action.createAuthToken());
        return action;
    }

    private void insertToken(String id, String status, String source, int createdAt) {
        AuthToken token = new AuthToken();
        token.setId(id);
        token.setName(id);
        token.setStatus(status);
        token.setSource(source);
        token.setCreatedAt(createdAt);
        AuthTokensDao.instance.insertOne(token);
    }

    private AuthToken find(String id) {
        return AuthTokensDao.instance.findOne(Filters.eq(AuthToken.ID, id));
    }

    @Test
    public void testCreateStoresRecordAndIdIsTheSignatureHash() throws Exception {
        AuthTokenAction action = create("  prod runtime  ", Arrays.asList("MINI_RUNTIME"), 6);

        String jwt = action.getApiToken();
        assertNotNull(jwt);
        assertEquals(action.getTokenId(), AuthTokenIds.idOf(jwt));
        assertNull(JwtAuthenticator.authenticate(jwt).getBody().getId());
        assertEquals(ACCOUNT_ID, ((Number) JwtAuthenticator.authenticate(jwt).getBody().get("accountId")).intValue());

        AuthToken record = find(action.getTokenId());
        assertEquals("prod runtime", record.getName());
        assertEquals(jwt.substring(jwt.length() - 10), record.getHint());
        assertEquals(action.getHint(), record.getHint());
        assertEquals(Arrays.asList("MINI_RUNTIME"), record.getScope());
        assertEquals(user.getId(), record.getCreatedBy());
        assertEquals(USER, record.getCreatedByEmail());
        assertEquals(AuthToken.STATUS_ACTIVE, record.getStatus());
        assertEquals(AuthToken.SOURCE_GENERATED, record.getSource());
        assertTrue(record.getExpiresAt() > Context.now());
        assertEquals((record.getExpiresAt() + 30L * 24 * 3600) * 1000L, record.getDeleteAt().getTime());
        assertFalse(jwt.contains(record.getName()));
    }

    @Test
    public void testNeverExpireAndValidation() throws Exception {
        AuthTokenAction never = create("never", null, -1);
        assertEquals(0, find(never.getTokenId()).getExpiresAt());
        assertNull(find(never.getTokenId()).getDeleteAt());
        assertNull(JwtAuthenticator.authenticate(never.getApiToken()).getBody().getExpiration());

        AuthTokenAction noName = action();
        noName.setName("   ");
        noName.setExpiryTimeInMonth(6);
        assertEquals(Action.ERROR.toUpperCase(), noName.createAuthToken());

        AuthTokenAction badExpiry = action();
        badExpiry.setName("x");
        badExpiry.setExpiryTimeInMonth(0);
        assertEquals(Action.ERROR.toUpperCase(), badExpiry.createAuthToken());
        assertTrue(badExpiry.getActionErrors().contains(AuthTokenService.EXPIRY_ERROR));
        assertEquals(1, AuthTokensDao.instance.getMCollection().countDocuments());
    }

    @Test
    public void testActiveTokenCap() {
        for (int i = 0; i < AuthTokenService.MAX_ACTIVE_TOKENS; i++) {
            insertToken("g" + i, AuthToken.STATUS_ACTIVE, AuthToken.SOURCE_GENERATED, Context.now());
        }
        insertToken("discovered", AuthToken.STATUS_ACTIVE, AuthToken.SOURCE_DISCOVERED, Context.now());

        AuthTokenAction blocked = action();
        blocked.setName("over cap");
        blocked.setExpiryTimeInMonth(6);
        assertEquals(Action.ERROR.toUpperCase(), blocked.createAuthToken());

        AuthTokenAction revoke = action();
        revoke.setTokenId("g0");
        assertEquals(Action.SUCCESS.toUpperCase(), revoke.revokeAuthToken());
        create("fits again", null, 6);
    }

    @Test
    public void testRevokeOneIsIdempotentAndRejectsUnknown() {
        AuthTokenAction first = create("first", null, 6);
        // a different expiry, since the name is not in the token and identical settings in one second collide
        AuthTokenAction second = create("second", null, 9);

        AuthTokenAction revoke = action();
        revoke.setTokenId(first.getTokenId());
        revoke.setReason("leaked");
        assertEquals(Action.SUCCESS.toUpperCase(), revoke.revokeAuthToken());

        AuthToken revoked = find(first.getTokenId());
        assertEquals(AuthToken.STATUS_REVOKED, revoked.getStatus());
        assertEquals(USER, revoked.getRevokedBy());
        assertEquals("leaked", revoked.getRevokeReason());
        assertTrue(revoked.getRevokedAt() > 0);
        assertEquals(AuthToken.STATUS_ACTIVE, find(second.getTokenId()).getStatus());

        AuthTokenAction again = action();
        again.setTokenId(first.getTokenId());
        again.setReason("different reason");
        assertEquals(Action.SUCCESS.toUpperCase(), again.revokeAuthToken());
        assertEquals("leaked", find(first.getTokenId()).getRevokeReason());
        assertEquals(revoked.getRevokedAt(), find(first.getTokenId()).getRevokedAt());

        AuthTokenAction unknown = action();
        unknown.setTokenId("does-not-exist");
        assertEquals(Action.ERROR.toUpperCase(), unknown.revokeAuthToken());
    }

    @Test
    public void testRevokeAllRevokesEveryActiveTokenIncludingDiscoveredOnes() {
        insertToken("a", AuthToken.STATUS_ACTIVE, AuthToken.SOURCE_GENERATED, 10);
        insertToken("b", AuthToken.STATUS_ACTIVE, AuthToken.SOURCE_GENERATED, 20);
        insertToken("already", AuthToken.STATUS_REVOKED, AuthToken.SOURCE_GENERATED, 30);
        insertToken("legacy", AuthToken.STATUS_ACTIVE, AuthToken.SOURCE_DISCOVERED, 40);
        AuthToken expired = new AuthToken();
        expired.setId("expired");
        expired.setName("expired");
        expired.setStatus(AuthToken.STATUS_ACTIVE);
        expired.setSource(AuthToken.SOURCE_DISCOVERED);
        expired.setExpiresAt(Context.now() - 100);
        AuthTokensDao.instance.insertOne(expired);

        AuthTokenAction action = action();
        action.setReason("rotation");
        assertEquals(Action.SUCCESS.toUpperCase(), action.revokeAllAuthTokens());

        assertEquals(3, action.getRevokedCount());
        for (String id : new String[]{"a", "b", "legacy"}) {
            assertEquals(AuthToken.STATUS_REVOKED, find(id).getStatus());
            assertEquals("rotation", find(id).getRevokeReason());
            assertEquals(USER, find(id).getRevokedBy());
        }
        assertEquals(0, find("already").getRevokedAt());
        assertEquals(AuthToken.STATUS_ACTIVE, find("expired").getStatus());
    }

    @Test
    public void testFetchAuthTokensJoinsModulesNewestFirst() {
        insertToken("old", AuthToken.STATUS_ACTIVE, AuthToken.SOURCE_GENERATED, 100);
        insertToken("new", AuthToken.STATUS_ACTIVE, AuthToken.SOURCE_GENERATED, 200);
        int now = Context.now();
        ModuleInfoDao.instance.insertOne(ModuleInfo.builder().id("m-live").name("runtime-1")
                .moduleType(ModuleInfo.ModuleType.MINI_RUNTIME).lastHeartbeatReceived(now).tokenId("new").build());
        ModuleInfoDao.instance.insertOne(ModuleInfo.builder().id("m-stale").name("runtime-2")
                .moduleType(ModuleInfo.ModuleType.GUARDRAIL).lastHeartbeatReceived(now - 1000).tokenId("new").build());
        ModuleInfoDao.instance.insertOne(ModuleInfo.builder().id("m-other").name("other")
                .moduleType(ModuleInfo.ModuleType.MINI_TESTING).lastHeartbeatReceived(now).tokenId("unknown-token").build());
        ModuleInfoDao.instance.insertOne(ModuleInfo.builder().id("m-none").name("none")
                .moduleType(ModuleInfo.ModuleType.MINI_TESTING).lastHeartbeatReceived(now).build());

        AuthTokenAction action = action();
        assertEquals(Action.SUCCESS.toUpperCase(), action.fetchAuthTokens());

        List<Map<String, Object>> rows = action.getAuthTokens();
        assertEquals(2, rows.size());
        assertEquals("new", rows.get(0).get("id"));
        assertEquals("old", rows.get(1).get("id"));
        assertEquals(Collections.emptyList(), rows.get(1).get("modules"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modules = (List<Map<String, Object>>) rows.get(0).get("modules");
        assertEquals(2, modules.size());
        for (Map<String, Object> module : modules) {
            boolean live = "m-live".equals(module.get("id"));
            assertEquals(live, module.get("active"));
            assertEquals(live ? "runtime-1" : "runtime-2", module.get("name"));
        }
    }

    @Test
    public void testFetchAuthTokensCapsModulesPerTokenNewestFirstAndReportsTheTotal() {
        insertToken("shared", AuthToken.STATUS_ACTIVE, AuthToken.SOURCE_GENERATED, 100);
        for (int i = 0; i < 25; i++) {
            ModuleInfoDao.instance.insertOne(ModuleInfo.builder().id("m-" + i).name("device-" + i)
                    .moduleType(ModuleInfo.ModuleType.MCP_ENDPOINT_SHIELD).lastHeartbeatReceived(1000 + i).tokenId("shared").build());
        }

        AuthTokenAction action = action();
        assertEquals(Action.SUCCESS.toUpperCase(), action.fetchAuthTokens());

        Map<String, Object> row = action.getAuthTokens().get(0);
        assertEquals(25, row.get("moduleCount"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modules = (List<Map<String, Object>>) row.get("modules");
        assertEquals(20, modules.size());
        assertEquals("m-24", modules.get(0).get("id"));
        assertEquals("m-5", modules.get(19).get("id"));
    }

    @Test
    public void testFetchModuleInfoFlagsRevokedTokensWithoutPersisting() {
        insertToken("good", AuthToken.STATUS_ACTIVE, AuthToken.SOURCE_GENERATED, 100);
        insertToken("bad", AuthToken.STATUS_REVOKED, AuthToken.SOURCE_GENERATED, 100);
        int now = Context.now();
        ModuleInfoDao.instance.insertOne(ModuleInfo.builder().id("m-good").name("good-module")
                .moduleType(ModuleInfo.ModuleType.MINI_RUNTIME).lastHeartbeatReceived(now).tokenId("good").build());
        ModuleInfoDao.instance.insertOne(ModuleInfo.builder().id("m-bad").name("bad-module")
                .moduleType(ModuleInfo.ModuleType.MINI_RUNTIME).lastHeartbeatReceived(now).tokenId("bad").build());
        ModuleInfoDao.instance.insertOne(ModuleInfo.builder().id("m-legacy").name("legacy-module")
                .moduleType(ModuleInfo.ModuleType.MINI_RUNTIME).lastHeartbeatReceived(now).build());

        ModuleInfoAction action = new ModuleInfoAction();
        assertEquals(Action.SUCCESS.toUpperCase(), action.fetchModuleInfo());

        Map<String, ModuleInfo> byId = new HashMap<>();
        for (ModuleInfo module : action.getModuleInfos()) {
            byId.put(module.getId(), module);
        }
        assertEquals(Boolean.TRUE, byId.get("m-bad").getAdditionalData().get("tokenRevoked"));
        assertTrue(byId.get("m-good").getAdditionalData() == null || !byId.get("m-good").getAdditionalData().containsKey("tokenRevoked"));
        assertTrue(byId.get("m-legacy").getAdditionalData() == null || !byId.get("m-legacy").getAdditionalData().containsKey("tokenRevoked"));

        for (ModuleInfo module : byId.values()) {
            assertNull(module.getTokenId());
        }

        ModuleInfo stored = ModuleInfoDao.instance.findOne(Filters.eq(ModuleInfoDao.ID, "m-bad"));
        assertTrue(stored.getAdditionalData() == null || !stored.getAdditionalData().containsKey("tokenRevoked"));
        assertEquals("bad", stored.getTokenId());
    }

    @Test
    public void testLegacyCreateTokenForAuthIsTracked() throws Exception {
        String jwt = QuickStartAction.createTokenForAuth(3, Arrays.asList("GUARDRAIL"));
        AuthToken record = find(AuthTokenIds.idOf(jwt));
        assertNotNull(record);
        assertTrue(record.getName().startsWith("quickstart-"));
        assertEquals(AuthToken.SOURCE_GENERATED, record.getSource());
    }

    @Test
    public void testIdGuardRejectsAnAlreadyRecordedToken() {
        String id = AuthTokenIds.idOf("header.payload.signature");
        AuthTokenService.assertIdFree(id);

        insertToken(id, AuthToken.STATUS_ACTIVE, AuthToken.SOURCE_GENERATED, Context.now());
        try {
            AuthTokenService.assertIdFree(id);
            fail("expected the duplicate id to be rejected");
        } catch (IllegalArgumentException e) {
            assertEquals("A token with the same settings was just created. Wait a second and try again.", e.getMessage());
        }
    }

    @Test
    public void testTokensWithDifferentSettingsGetDifferentIds() throws Exception {
        AuthTokenAction runtime = create("a", Arrays.asList("MINI_RUNTIME"), 6);
        AuthTokenAction guardrail = create("b", Arrays.asList("GUARDRAIL"), 6);
        assertTrue(!runtime.getTokenId().equals(guardrail.getTokenId()));
        assertEquals(64, runtime.getTokenId().length());
        assertNotNull(find(runtime.getTokenId()));
        assertNotNull(find(guardrail.getTokenId()));
    }
}
