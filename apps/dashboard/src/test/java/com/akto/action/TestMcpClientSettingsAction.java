package com.akto.action;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Date;

import org.junit.Before;
import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.McpOAuthClientsDao;
import com.akto.dao.McpOAuthGrantsDao;
import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.akto.dto.McpOAuthClient;
import com.akto.dto.McpOAuthGrant;
import com.akto.util.McpClientPolicy;
import com.akto.util.McpOAuthUtils;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

/* The admin actions behind the MCP clients settings page. */
public class TestMcpClientSettingsAction extends MongoBasedTest {

    private static final String ALLOWED = "https://app.example.com/oauth/callback";

    @Before
    public void setup() {
        Context.accountId.set(ACCOUNT_ID);
        AccountSettingsDao.instance.getMCollection().drop();
        AccountSettingsDao.instance.getMCollection().updateOne(AccountSettingsDao.generateFilter(), Updates.set(AccountSettings.REDACT_PAYLOAD, false), new UpdateOptions().upsert(true));
        McpOAuthClientsDao.instance.getMCollection().drop();
        McpOAuthGrantsDao.instance.getMCollection().drop();
    }

    private AdminSettingsAction action(String uri) {
        AdminSettingsAction action = new AdminSettingsAction();
        action.setMcpRedirectUri(uri);
        return action;
    }

    private AccountSettings settings() {
        return AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
    }

    private boolean refreshAlive(String secret) {
        return McpOAuthGrantsDao.instance.find(McpOAuthUtils.sha256Hex(secret), McpOAuthGrant.TYPE_REFRESH) != null;
    }

    @Test
    public void aValidUriIsAddedOnceAndRemoved() {
        assertEquals("SUCCESS", action(ALLOWED).addMcpAllowedRedirectUri());
        assertEquals("SUCCESS", action("  " + ALLOWED + "  ").addMcpAllowedRedirectUri());
        assertEquals(Collections.singletonList(ALLOWED), settings().getMcpAllowedRedirectUris());

        assertEquals("SUCCESS", action(ALLOWED).deleteMcpAllowedRedirectUri());
        assertTrue(settings().getMcpAllowedRedirectUris().isEmpty());
    }

    @Test
    public void invalidUrisAreRefusedWithAMessage() {
        for (String bad : new String[] { "http://app.example.com/cb", "https://app.example.com", "https://*.example.com/cb", "", null }) {
            AdminSettingsAction action = action(bad);
            assertEquals("ERROR", action.addMcpAllowedRedirectUri());
            assertFalse(action.getActionErrors().isEmpty());
        }
        assertNull(settings().getMcpAllowedRedirectUris());
    }

    @Test
    public void theListIsCapped() {
        for (int i = 0; i < McpClientPolicy.MAX_ALLOWED_URIS; i++) {
            assertEquals("SUCCESS", action("https://app.example.com/cb" + i).addMcpAllowedRedirectUri());
        }
        AdminSettingsAction overflow = action("https://app.example.com/one-too-many");
        assertEquals("ERROR", overflow.addMcpAllowedRedirectUri());
        assertEquals(McpClientPolicy.MAX_ALLOWED_URIS, settings().getMcpAllowedRedirectUris().size());
        // an existing entry can still be re-added at the cap
        assertEquals("SUCCESS", action("https://app.example.com/cb0").addMcpAllowedRedirectUri());
    }

    @Test
    public void turningItOnSavesTheSettingAndRevokesDisallowedClients() {
        McpOAuthClientsDao.instance.insertOne(new McpOAuthClient("attacker", "attacker", Collections.singletonList("https://attacker.example.com/cb"), 1, new Date()));
        McpOAuthClientsDao.instance.insertOne(new McpOAuthClient("claude", "claude", Collections.singletonList("https://claude.ai/api/mcp/auth_callback"), 1, new Date()));
        for (String client : new String[] { "attacker", "claude" }) {
            McpOAuthGrant grant = McpOAuthUtils.newGrant(McpOAuthGrant.TYPE_REFRESH, client + "-token", 3600);
            grant.setClientId(client);
            grant.setAccountId(ACCOUNT_ID);
            McpOAuthGrantsDao.instance.insert(grant);
        }

        AdminSettingsAction action = new AdminSettingsAction();
        action.setMcpKnownClientsOnly(true);
        assertEquals("SUCCESS", action.updateMcpKnownClientsOnly());

        assertTrue(settings().isMcpKnownClientsOnly());
        assertFalse(refreshAlive("attacker-token"));
        assertTrue(refreshAlive("claude-token"));
    }

    @Test
    public void turningItOffKeepsEveryGrant() {
        McpOAuthClientsDao.instance.insertOne(new McpOAuthClient("attacker", "attacker", Collections.singletonList("https://attacker.example.com/cb"), 1, new Date()));
        McpOAuthGrant grant = McpOAuthUtils.newGrant(McpOAuthGrant.TYPE_REFRESH, "attacker-token", 3600);
        grant.setClientId("attacker");
        grant.setAccountId(ACCOUNT_ID);
        McpOAuthGrantsDao.instance.insert(grant);

        AdminSettingsAction action = new AdminSettingsAction();
        action.setMcpKnownClientsOnly(false);
        assertEquals("SUCCESS", action.updateMcpKnownClientsOnly());

        assertFalse(settings().isMcpKnownClientsOnly());
        assertTrue(refreshAlive("attacker-token"));
    }

    @Test
    public void removingAnAllowedUriDisconnectsTheClientThatNeededIt() {
        action(ALLOWED).addMcpAllowedRedirectUri();
        McpOAuthClientsDao.instance.insertOne(new McpOAuthClient("partner", "partner", Collections.singletonList(ALLOWED), 1, new Date()));
        McpOAuthGrant grant = McpOAuthUtils.newGrant(McpOAuthGrant.TYPE_REFRESH, "partner-token", 3600);
        grant.setClientId("partner");
        grant.setAccountId(ACCOUNT_ID);
        McpOAuthGrantsDao.instance.insert(grant);

        AdminSettingsAction on = new AdminSettingsAction();
        on.setMcpKnownClientsOnly(true);
        on.updateMcpKnownClientsOnly();
        assertTrue(refreshAlive("partner-token"));

        action(ALLOWED).deleteMcpAllowedRedirectUri();
        assertFalse(refreshAlive("partner-token"));
    }
}
