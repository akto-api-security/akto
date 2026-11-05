package com.akto.action;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dao.McpOAuthGrantsDao;
import com.akto.dao.RBACDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.McpOAuthGrant;
import com.akto.dto.RBAC;
import com.akto.dto.User;
import com.akto.dto.UserAccountEntry;
import com.akto.util.McpOAuthUtils;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.BasicDBObject;

/* Consent approval: only interactive sessions can approve, and unknown redirect hosts need a confirmation. */
public class TestMcpOAuthConsentAction extends MongoBasedTest {

    private static final String REQUEST_SECRET = "request-secret-for-tests";
    private static final String KNOWN_REDIRECT = "https://claude.ai/api/mcp/auth_callback";
    private static final String UNKNOWN_REDIRECT = "https://unknown.example.com/callback";

    private User user;

    @Before
    public void setup() {
        Context.accountId.set(ACCOUNT_ID);
        Context.contextSource.set(CONTEXT_SOURCE.API);
        McpOAuthGrantsDao.instance.getMCollection().drop();
        UsersDao.instance.deleteAll(new BasicDBObject());
        RBACDao.instance.deleteAll(new BasicDBObject());
        Map<String, UserAccountEntry> accounts = new HashMap<>();
        accounts.put(ACCOUNT_ID + "", new UserAccountEntry(ACCOUNT_ID));
        UsersDao.instance.insertOne(new User("test", "consent-user@example.com", accounts, null));
        user = UsersDao.instance.findOne(new BasicDBObject());
        RBACDao.instance.insertOne(new RBAC(user.getId(), RBAC.Role.ADMIN.name(), ACCOUNT_ID));
    }

    private void parkRequest(String redirectUri) {
        McpOAuthGrant request = McpOAuthUtils.newGrant(McpOAuthGrant.TYPE_REQUEST, REQUEST_SECRET, McpOAuthUtils.REQUEST_TTL_SECONDS);
        request.setClientId("client-a");
        request.setRedirectUri(redirectUri);
        request.setCodeChallenge("c".repeat(43));
        request.setResource("https://mcp.example.com/mcp");
        McpOAuthGrantsDao.instance.insert(request);
    }

    private McpOAuthConsentAction action(Map<String, Object> extraSession) {
        Map<String, Object> session = new HashMap<>();
        session.put("user", user);
        session.putAll(extraSession);
        McpOAuthConsentAction action = new McpOAuthConsentAction();
        action.setSession(session);
        action.setRequestId(REQUEST_SECRET);
        action.setContextSource("API");
        return action;
    }

    private boolean requestStillParked() {
        return McpOAuthGrantsDao.instance.find(McpOAuthUtils.sha256Hex(REQUEST_SECRET), McpOAuthGrant.TYPE_REQUEST) != null;
    }

    @Test
    public void aKnownHostIsApprovedWithoutConfirmation() {
        parkRequest(KNOWN_REDIRECT);
        McpOAuthConsentAction action = action(new HashMap<>());
        assertEquals("SUCCESS", action.approveAuthorizeRequest());
        assertTrue(action.getRedirectUrl().startsWith(KNOWN_REDIRECT + "?code="));
        assertFalse(requestStillParked());
    }

    @Test
    public void anUnknownHostNeedsConfirmationAndKeepsTheRequestUntilThen() {
        parkRequest(UNKNOWN_REDIRECT);
        McpOAuthConsentAction unconfirmed = action(new HashMap<>());
        assertEquals("ERROR", unconfirmed.approveAuthorizeRequest());
        assertNull(unconfirmed.getRedirectUrl());
        assertTrue(requestStillParked());

        McpOAuthConsentAction confirmed = action(new HashMap<>());
        confirmed.setConfirmUnverified(true);
        assertEquals("SUCCESS", confirmed.approveAuthorizeRequest());
        assertTrue(confirmed.getRedirectUrl().startsWith(UNKNOWN_REDIRECT + "?code="));
    }

    @Test
    public void fetchFlagsAnUnknownRedirectHost() {
        parkRequest(UNKNOWN_REDIRECT);
        McpOAuthConsentAction action = action(new HashMap<>());
        assertEquals("SUCCESS", action.fetchAuthorizeRequest());
        assertTrue(action.isUnverifiedRedirect());
        assertEquals("unknown.example.com", action.getRedirectHost());
        assertNotNull(action.getAllowedContexts());
    }

    @Test
    public void anMcpTokenSessionCannotApprove() {
        parkRequest(KNOWN_REDIRECT);
        Map<String, Object> flagged = new HashMap<>();
        flagged.put(McpOAuthUtils.SESSION_MCP_TOKEN_AUTH, Boolean.TRUE);
        assertEquals("ERROR", action(flagged).approveAuthorizeRequest());
        assertTrue(requestStillParked());
    }

    @Test
    public void anApiKeySessionCannotApprove() {
        parkRequest(KNOWN_REDIRECT);
        Map<String, Object> apiKey = new HashMap<>();
        apiKey.put("utility", "EXTERNAL_API");
        assertEquals("ERROR", action(apiKey).approveAuthorizeRequest());
        assertTrue(requestStillParked());
    }

    @Test
    public void aMissingSessionFailsClosed() {
        parkRequest(KNOWN_REDIRECT);
        McpOAuthConsentAction action = new McpOAuthConsentAction();
        action.setRequestId(REQUEST_SECRET);
        assertEquals("ERROR", action.approveAuthorizeRequest());
        assertTrue(requestStillParked());
    }

    @Test
    public void denyReturnsAccessDeniedAndSpendsTheRequest() {
        parkRequest(KNOWN_REDIRECT);
        McpOAuthConsentAction action = action(new HashMap<>());
        assertEquals("SUCCESS", action.denyAuthorizeRequest());
        assertTrue(action.getRedirectUrl().startsWith(KNOWN_REDIRECT + "?error=access_denied"));
        assertFalse(requestStillParked());
    }
}
