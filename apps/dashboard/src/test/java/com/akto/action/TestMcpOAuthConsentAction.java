package com.akto.action;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.apache.struts2.json.DefaultJSONWriter;
import org.apache.struts2.json.JSONUtil;
import org.junit.Before;
import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.AccountsDao;
import com.akto.dao.McpOAuthGrantsDao;
import com.akto.dao.RBACDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
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

    private static final int OTHER_ACCOUNT_ID = ACCOUNT_ID + 1;

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

    // The user also belongs to a second account; roles are per account
    private void joinOtherAccount(RBAC.Role roleInOther) {
        AccountsDao.instance.getMCollection().drop();
        AccountsDao.instance.insertOne(new Account(ACCOUNT_ID, "Alpha"));
        AccountsDao.instance.insertOne(new Account(OTHER_ACCOUNT_ID, "Beta"));
        Map<String, UserAccountEntry> accounts = new HashMap<>();
        accounts.put(ACCOUNT_ID + "", new UserAccountEntry(ACCOUNT_ID));
        accounts.put(OTHER_ACCOUNT_ID + "", new UserAccountEntry(OTHER_ACCOUNT_ID));
        user.setAccounts(accounts);
        RBACDao.instance.insertOne(new RBAC(user.getId(), roleInOther.name(), OTHER_ACCOUNT_ID));
        // Roles are cached per user and account across tests
        RBACDao.clearAccountCache(ACCOUNT_ID);
        RBACDao.clearAccountCache(OTHER_ACCOUNT_ID);
    }

    private static String codeIn(String redirectUrl) {
        return redirectUrl.substring(redirectUrl.indexOf("code=") + "code=".length());
    }

    @Test
    public void fetchListsTheUsersAccountsByNameAndReportsTheOneInUse() {
        joinOtherAccount(RBAC.Role.MEMBER);
        parkRequest(KNOWN_REDIRECT);
        McpOAuthConsentAction action = action(new HashMap<>());
        assertEquals("SUCCESS", action.fetchAuthorizeRequest());
        assertEquals(Integer.valueOf(ACCOUNT_ID), action.getAccountId());
        assertEquals("Alpha", action.getAccountName());
        assertEquals(Arrays.asList("Alpha", "Beta"), namesOf(action));
    }

    private static List<String> namesOf(McpOAuthConsentAction action) {
        List<String> names = new ArrayList<>();
        for (McpOAuthConsentAction.AccountOption option : action.getAccounts()) {
            names.add(option.getName());
        }
        return names;
    }

    @Test
    public void fetchForAnotherAccountUsesThatAccountAndRestoresTheSessionAccount() {
        joinOtherAccount(RBAC.Role.MEMBER);
        parkRequest(KNOWN_REDIRECT);
        McpOAuthConsentAction action = action(new HashMap<>());
        action.setAccountId(OTHER_ACCOUNT_ID);
        assertEquals("SUCCESS", action.fetchAuthorizeRequest());
        assertEquals("Beta", action.getAccountName());
        assertEquals(Integer.valueOf(OTHER_ACCOUNT_ID), action.getAccountId());
        assertEquals(Integer.valueOf(ACCOUNT_ID), Context.accountId.get());
    }

    @Test
    public void anAccountWithoutProductAccessIsRefusedAtFetch() {
        joinOtherAccount(RBAC.Role.NO_ACCESS);
        parkRequest(KNOWN_REDIRECT);
        McpOAuthConsentAction action = action(new HashMap<>());
        action.setAccountId(OTHER_ACCOUNT_ID);
        assertEquals("ERROR", action.fetchAuthorizeRequest());
        assertEquals(Integer.valueOf(ACCOUNT_ID), Context.accountId.get());
    }

    @Test
    public void anAccountTheUserDoesNotBelongToIsRefusedAndTheRequestIsKept() {
        joinOtherAccount(RBAC.Role.MEMBER);
        parkRequest(KNOWN_REDIRECT);
        McpOAuthConsentAction fetch = action(new HashMap<>());
        fetch.setAccountId(OTHER_ACCOUNT_ID + 1);
        assertEquals("ERROR", fetch.fetchAuthorizeRequest());
        McpOAuthConsentAction approve = action(new HashMap<>());
        approve.setAccountId(OTHER_ACCOUNT_ID + 1);
        assertEquals("ERROR", approve.approveAuthorizeRequest());
        assertNull(approve.getRedirectUrl());
        assertTrue(requestStillParked());
        assertEquals(Integer.valueOf(ACCOUNT_ID), Context.accountId.get());
    }

    @Test
    public void approvingForAnotherAccountPutsThatAccountInTheCode() {
        joinOtherAccount(RBAC.Role.MEMBER);
        parkRequest(KNOWN_REDIRECT);
        McpOAuthConsentAction action = action(new HashMap<>());
        action.setAccountId(OTHER_ACCOUNT_ID);
        assertEquals("SUCCESS", action.approveAuthorizeRequest());
        McpOAuthGrant code = McpOAuthGrantsDao.instance.find(McpOAuthUtils.sha256Hex(codeIn(action.getRedirectUrl())), McpOAuthGrant.TYPE_CODE);
        assertEquals(OTHER_ACCOUNT_ID, code.getAccountId());
        assertEquals(Integer.valueOf(ACCOUNT_ID), Context.accountId.get());
    }

    @Test
    public void approvingWithoutAnAccountKeepsTheSessionAccount() {
        joinOtherAccount(RBAC.Role.MEMBER);
        parkRequest(KNOWN_REDIRECT);
        McpOAuthConsentAction action = action(new HashMap<>());
        assertEquals("SUCCESS", action.approveAuthorizeRequest());
        McpOAuthGrant code = McpOAuthGrantsDao.instance.find(McpOAuthUtils.sha256Hex(codeIn(action.getRedirectUrl())), McpOAuthGrant.TYPE_CODE);
        assertEquals(ACCOUNT_ID, code.getAccountId());
    }

    @Test
    public void theAdminPolicyOfTheChosenAccountApplies() {
        joinOtherAccount(RBAC.Role.MEMBER);
        Context.accountId.set(OTHER_ACCOUNT_ID);
        setPolicy(true);
        Context.accountId.set(ACCOUNT_ID);

        parkRequest(UNKNOWN_REDIRECT);
        McpOAuthConsentAction blocked = action(new HashMap<>());
        blocked.setAccountId(OTHER_ACCOUNT_ID);
        blocked.setConfirmUnverified(true);
        assertEquals("ERROR", blocked.approveAuthorizeRequest());
        assertTrue(requestStillParked());

        McpOAuthConsentAction allowed = action(new HashMap<>());
        allowed.setConfirmUnverified(true);
        assertEquals("SUCCESS", allowed.approveAuthorizeRequest());
    }

    // The page reads the accounts from the response; struts' JSON plugin drops nested fields unless the parent is also included
    @Test
    public void everyConsentActionReturnsTheAccountsThroughTheStrutsJsonResult() throws Exception {
        joinOtherAccount(RBAC.Role.MEMBER);
        parkRequest(KNOWN_REDIRECT);
        McpOAuthConsentAction fetched = action(new HashMap<>());
        assertEquals("SUCCESS", fetched.fetchAuthorizeRequest());

        String struts;
        try (InputStream in = getClass().getResourceAsStream("/struts.xml")) {
            struts = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String name : new String[] { "fetchAuthorizeRequest", "approveAuthorizeRequest", "denyAuthorizeRequest" }) {
            Matcher block = Pattern.compile("<action name=\"api/mcpOAuth/" + name + "\".*?name=\"includeProperties\">([^<]*)</param>", Pattern.DOTALL).matcher(struts);
            assertTrue(name, block.find());
            List<Pattern> includes = new ArrayList<>();
            for (String pattern : block.group(1).split(",")) {
                includes.add(Pattern.compile(pattern.trim()));
            }
            JSONUtil json = new JSONUtil();
            json.setWriter(new DefaultJSONWriter());
            String body = json.serialize(fetched, null, includes, true, false);
            assertTrue(name + " -> " + body, body.contains("\"accounts\":[") && body.contains("\"name\":\"Beta\"") && body.contains("\"accountId\":" + ACCOUNT_ID));
        }
    }
}
