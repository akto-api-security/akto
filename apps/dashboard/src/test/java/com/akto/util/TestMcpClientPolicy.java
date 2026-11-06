package com.akto.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dao.McpOAuthClientsDao;
import com.akto.dao.McpOAuthGrantsDao;
import com.akto.dao.context.Context;
import com.akto.dto.McpOAuthClient;
import com.akto.dto.McpOAuthGrant;

/* The per-account policy: which redirects it blocks, and whose refresh tokens it revokes. */
public class TestMcpClientPolicy extends MongoBasedTest {

    private static final String CLAUDE = "https://claude.ai/api/mcp/auth_callback";
    private static final String ATTACKER = "https://attacker.example.com/callback";
    private static final int OTHER_ACCOUNT = ACCOUNT_ID + 1;

    @Before
    public void setup() {
        Context.accountId.set(ACCOUNT_ID);
        McpOAuthClientsDao.instance.getMCollection().drop();
        McpOAuthGrantsDao.instance.getMCollection().drop();
        client("claude", CLAUDE);
        client("attacker", ATTACKER);
        client("mixed", CLAUDE, ATTACKER);
        client("local", "http://localhost:3118/callback");
    }

    private void client(String id, String... redirectUris) {
        McpOAuthClientsDao.instance.insertOne(new McpOAuthClient(id, id, Arrays.asList(redirectUris), 1, new Date()));
    }

    private void refreshToken(String secret, String clientId, int accountId) {
        McpOAuthGrant grant = McpOAuthUtils.newGrant(McpOAuthGrant.TYPE_REFRESH, secret, 3600);
        grant.setClientId(clientId);
        grant.setAccountId(accountId);
        McpOAuthGrantsDao.instance.insert(grant);
    }

    private boolean alive(String secret) {
        return McpOAuthGrantsDao.instance.find(McpOAuthUtils.sha256Hex(secret), McpOAuthGrant.TYPE_REFRESH) != null;
    }

    @Test
    public void blocksUnknownHttpsOnlyWhenTheSettingIsOn() {
        assertFalse(new McpClientPolicy(false, null).blocks(ATTACKER));
        assertTrue(new McpClientPolicy(true, null).blocks(ATTACKER));
        assertFalse(new McpClientPolicy(true, null).blocks(CLAUDE));
        assertFalse(new McpClientPolicy(true, null).blocks("http://localhost:6274/cb"));
        assertFalse(new McpClientPolicy(true, null).blocks("cursor://anysphere.cursor-mcp/oauth/callback"));
    }

    @Test
    public void anAdminAllowedUriIsKnownAndNotBlocked() {
        McpClientPolicy policy = new McpClientPolicy(true, Collections.singletonList(ATTACKER));
        assertFalse(policy.blocks(ATTACKER));
        assertTrue(policy.isKnown(ATTACKER));
    }

    @Test
    public void aClientIsAllowedOnlyWhenEveryRedirectIsKnown() {
        McpClientPolicy policy = new McpClientPolicy(true, null);
        assertTrue(policy.allowsClient(McpOAuthClientsDao.instance.findById("claude")));
        assertTrue(policy.allowsClient(McpOAuthClientsDao.instance.findById("local")));
        assertFalse(policy.allowsClient(McpOAuthClientsDao.instance.findById("attacker")));
        assertFalse(policy.allowsClient(McpOAuthClientsDao.instance.findById("mixed")));
    }

    @Test
    public void revokingRemovesOnlyThisAccountsGrantsOfDisallowedClients() {
        refreshToken("claude-here", "claude", ACCOUNT_ID);
        refreshToken("local-here", "local", ACCOUNT_ID);
        refreshToken("attacker-here", "attacker", ACCOUNT_ID);
        refreshToken("mixed-here", "mixed", ACCOUNT_ID);
        refreshToken("attacker-elsewhere", "attacker", OTHER_ACCOUNT);

        assertEquals(2, new McpClientPolicy(true, null).revokeDisallowedGrants());

        assertTrue(alive("claude-here"));
        assertTrue(alive("local-here"));
        assertFalse(alive("attacker-here"));
        assertFalse(alive("mixed-here"));
        assertTrue(alive("attacker-elsewhere"));
    }

    @Test
    public void allowingTheUriKeepsTheClientsGrants() {
        refreshToken("attacker-here", "attacker", ACCOUNT_ID);
        assertEquals(0, new McpClientPolicy(true, Collections.singletonList(ATTACKER)).revokeDisallowedGrants());
        assertTrue(alive("attacker-here"));
    }

    @Test
    public void aClientThatNoLongerExistsLosesItsGrants() {
        refreshToken("ghost-here", "ghost", ACCOUNT_ID);
        assertEquals(1, new McpClientPolicy(true, null).revokeDisallowedGrants());
        assertFalse(alive("ghost-here"));
    }

    @Test
    public void nothingIsRevokedWhenTheSettingIsOff() {
        refreshToken("attacker-here", "attacker", ACCOUNT_ID);
        assertEquals(0, new McpClientPolicy(false, null).revokeDisallowedGrants());
        assertTrue(alive("attacker-here"));
    }
}
