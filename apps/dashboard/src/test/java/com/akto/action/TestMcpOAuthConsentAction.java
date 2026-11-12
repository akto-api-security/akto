package com.akto.action;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.junit.Before;
import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.McpOAuthGrantsDao;
import com.akto.dao.RBACDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.akto.dto.McpOAuthGrant;
import com.akto.dto.RBAC;
import com.akto.dto.User;
import com.akto.dto.UserAccountEntry;
import com.akto.util.McpOAuthUtils;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

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
        AccountSettingsDao.instance.getMCollection().drop();
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

    private static HttpServletRequest requestWith(Cookie... cookies) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(cookies.length == 0 ? null : cookies);
        return request;
    }

    // The cookie the authorize endpoint sets in the browser that started the request
    private static Cookie bindingCookie() {
        String hash = McpOAuthUtils.sha256Hex(REQUEST_SECRET);
        return new Cookie(McpOAuthUtils.requestBindingCookieName(hash), hash);
    }

    private McpOAuthConsentAction action(Map<String, Object> extraSession, HttpServletRequest request) {
        Map<String, Object> session = new HashMap<>();
        session.put("user", user);
        session.putAll(extraSession);
        McpOAuthConsentAction action = new McpOAuthConsentAction();
        action.setSession(session);
        action.setServletRequest(request);
        action.setRequestId(REQUEST_SECRET);
        action.setContextSource("API");
        return action;
    }

    private McpOAuthConsentAction action(Map<String, Object> extraSession) {
        return action(extraSession, requestWith(bindingCookie()));
    }

    // The admin's MCP client settings for this account
    private void setPolicy(boolean knownClientsOnly, String... allowedUris) {
        AccountSettingsDao.instance.getMCollection().updateOne(AccountSettingsDao.generateFilter(),
                Updates.combine(Updates.set(AccountSettings.MCP_KNOWN_CLIENTS_ONLY, knownClientsOnly),
                        Updates.set(AccountSettings.MCP_ALLOWED_REDIRECT_URIS, Arrays.asList(allowedUris))),
                new UpdateOptions().upsert(true));
    }

    private boolean requestStillParked() {
        return McpOAuthGrantsDao.instance.find(McpOAuthUtils.sha256Hex(REQUEST_SECRET), McpOAuthGrant.TYPE_REQUEST) != null;
    }

    @Test
    public void aConsentLinkOpenedInAnotherBrowserCannotApproveOrBeFetched() {
        parkRequest(KNOWN_REDIRECT);
        assertEquals("ERROR", action(new HashMap<>(), requestWith()).approveAuthorizeRequest());
        assertEquals("ERROR", action(new HashMap<>(), requestWith(new Cookie("mcp_oauth_other", "x"))).approveAuthorizeRequest());
        assertEquals("ERROR", action(new HashMap<>(), requestWith()).fetchAuthorizeRequest());
        assertTrue(requestStillParked());

        String wrongValue = McpOAuthUtils.requestBindingCookieName(McpOAuthUtils.sha256Hex(REQUEST_SECRET));
        assertEquals("ERROR", action(new HashMap<>(), requestWith(new Cookie(wrongValue, "not-the-hash"))).approveAuthorizeRequest());
        assertTrue(requestStillParked());
    }

    @Test
    public void knownClientsOnlyRefusesAnUnknownHostEvenWhenConfirmed() {
        setPolicy(true);
        parkRequest(UNKNOWN_REDIRECT);
        McpOAuthConsentAction fetch = action(new HashMap<>());
        assertEquals("ERROR", fetch.fetchAuthorizeRequest());
        assertTrue(fetch.getActionErrors().iterator().next().contains("unknown.example.com"));

        McpOAuthConsentAction approve = action(new HashMap<>());
        approve.setConfirmUnverified(true);
        assertEquals("ERROR", approve.approveAuthorizeRequest());
        assertNull(approve.getRedirectUrl());
        assertTrue(requestStillParked());
    }

    @Test
    public void knownClientsOnlyStillApprovesKnownAndAdminAllowedClients() {
        setPolicy(true, UNKNOWN_REDIRECT);
        parkRequest(UNKNOWN_REDIRECT);
        McpOAuthConsentAction fetch = action(new HashMap<>());
        assertEquals("SUCCESS", fetch.fetchAuthorizeRequest());
        assertFalse(fetch.isUnverifiedRedirect());
        assertEquals("SUCCESS", action(new HashMap<>()).approveAuthorizeRequest());

        parkRequest(KNOWN_REDIRECT);
        assertEquals("SUCCESS", action(new HashMap<>()).approveAuthorizeRequest());
    }

    @Test
    public void anAdminAllowedUriNeedsNoConfirmationWhenTheSettingIsOff() {
        setPolicy(false, UNKNOWN_REDIRECT);
        parkRequest(UNKNOWN_REDIRECT);
        assertEquals("SUCCESS", action(new HashMap<>()).approveAuthorizeRequest());
    }

    @Test
    public void denyStillWorksWhenTheClientIsBlocked() {
        setPolicy(true);
        parkRequest(UNKNOWN_REDIRECT);
        assertEquals("SUCCESS", action(new HashMap<>()).denyAuthorizeRequest());
        assertFalse(requestStillParked());
    }

    @Test
    public void fetchReturnsTheFullRedirectUri() {
        parkRequest(UNKNOWN_REDIRECT);
        McpOAuthConsentAction action = action(new HashMap<>());
        assertEquals("SUCCESS", action.fetchAuthorizeRequest());
        assertEquals(UNKNOWN_REDIRECT, action.getRedirectUri());
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
        action.setServletRequest(requestWith(bindingCookie()));
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
