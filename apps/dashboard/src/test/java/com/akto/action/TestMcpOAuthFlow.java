package com.akto.action;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dao.ConfigsDao;
import com.akto.dao.McpOAuthClientsDao;
import com.akto.dao.McpOAuthGrantsDao;
import com.akto.dao.UsersDao;
import com.akto.database_abstractor_authenticator.JwtAuthenticator;
import com.akto.dto.Config;
import com.akto.dto.McpOAuthClient;
import com.akto.dto.McpOAuthGrant;
import com.akto.dto.User;
import com.akto.dto.UserAccountEntry;
import com.akto.util.McpOAuthUtils;
import com.mongodb.BasicDBObject;

import io.jsonwebtoken.Claims;

/* Public OAuth endpoints: registration, authorize checks and the token endpoint failure paths. */
public class TestMcpOAuthFlow extends MongoBasedTest {

    private static final String RESOURCE = "https://mcp.example.com/mcp";
    private static final String REDIRECT = "https://client.example.com/callback";
    private static final String VERIFIER = "akto-mcp-oauth-test-verifier-0123456789-abcdefghij";
    private static final String CHALLENGE = "Co_a3Uz78JBxrGFVKy52TSAIEbajFNynV-YMxOBkWh4";
    private static final String CLIENT_ID = "client-a";
    private static final String USER = "oauth-user@example.com";
    private static final String CODE_SECRET = "code-secret-for-tests";

    private static class Out {
        int status;
        String body = "";
        String redirect;
        String allow;
    }

    private static class ConfiguredAction extends McpOAuthAction {
        @Override
        protected String configuredResource() {
            return RESOURCE;
        }
    }

    private static class UnconfiguredAction extends McpOAuthAction {
        @Override
        protected String configuredResource() {
            return null;
        }
    }

    @Before
    public void setup() throws Exception {
        McpOAuthClientsDao.instance.getMCollection().drop();
        McpOAuthGrantsDao.instance.getMCollection().drop();
        UsersDao.instance.deleteAll(new BasicDBObject());
        Map<String, UserAccountEntry> accounts = new HashMap<>();
        accounts.put(ACCOUNT_ID + "", new UserAccountEntry(ACCOUNT_ID));
        UsersDao.instance.insertOne(new User("test", USER, accounts, null));

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        ConfigsDao.instance.getMCollection().drop();
        ConfigsDao.instance.insertOne(new Config.HybridSaasConfig(
                Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()),
                Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())));

        McpOAuthClientsDao.instance.insertOne(new McpOAuthClient(CLIENT_ID, "Client A", Collections.singletonList(REDIRECT), 1, new Date()));
        McpOAuthClientsDao.instance.insertOne(new McpOAuthClient("client-b", "Client B", Collections.singletonList(REDIRECT), 1, new Date()));
    }

    private Out call(McpOAuthAction action, String endpoint, String method, Map<String, String> params, String body) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getParameter(anyString())).thenAnswer(invocation -> params.get((String) invocation.getArgument(0)));
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(body == null ? "" : body)));

        Out out = new Out();
        StringWriter written = new StringWriter();
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(written, true));
        doAnswer(invocation -> { out.status = invocation.getArgument(0); return null; }).when(response).setStatus(anyInt());
        doAnswer(invocation -> { out.status = invocation.getArgument(0); return null; }).when(response).sendError(anyInt());
        doAnswer(invocation -> { out.status = invocation.getArgument(0); return null; }).when(response).sendError(anyInt(), anyString());
        doAnswer(invocation -> { out.status = 302; out.redirect = invocation.getArgument(0); return null; }).when(response).sendRedirect(anyString());
        doAnswer(invocation -> { if ("Allow".equals(invocation.getArgument(0))) { out.allow = invocation.getArgument(1); } return null; }).when(response).setHeader(anyString(), anyString());

        action.setServletRequest(request);
        action.setServletResponse(response);
        switch (endpoint) {
            case "register": action.register(); break;
            case "authorize": action.authorize(); break;
            default: action.token(); break;
        }
        out.body = written.toString();
        return out;
    }

    private Out token(Map<String, String> params) throws Exception {
        return call(new ConfiguredAction(), "token", "POST", params, null);
    }

    private static Map<String, String> params(String... keyValues) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private void insertCode(String clientId) {
        McpOAuthGrant code = McpOAuthUtils.newGrant(McpOAuthGrant.TYPE_CODE, CODE_SECRET, McpOAuthUtils.CODE_TTL_SECONDS);
        code.setClientId(clientId);
        code.setRedirectUri(REDIRECT);
        code.setCodeChallenge(CHALLENGE);
        code.setResource(RESOURCE);
        code.setUsername(USER);
        code.setAccountId(ACCOUNT_ID);
        code.setContextSource("API");
        McpOAuthGrantsDao.instance.insert(code);
    }

    private Map<String, String> codeRequest(String clientId, String verifier) {
        return params("grant_type", "authorization_code", "client_id", clientId, "code", CODE_SECRET, "code_verifier", verifier, "redirect_uri", REDIRECT);
    }

    private static String field(String json, String name) {
        BasicDBObject parsed = BasicDBObject.parse(json);
        return parsed.getString(name);
    }

    @Test
    public void endpointsAreOffUntilTheMcpUrlIsConfigured() throws Exception {
        assertEquals(404, call(new UnconfiguredAction(), "register", "POST", params(), "{}").status);
        assertEquals(404, call(new UnconfiguredAction(), "authorize", "GET", params("client_id", CLIENT_ID), null).status);
        assertEquals(404, call(new UnconfiguredAction(), "token", "POST", params("client_id", CLIENT_ID), null).status);
    }

    @Test
    public void registrationAcceptsSupportedRedirectsOnly() throws Exception {
        Out wrongMethod = call(new ConfiguredAction(), "register", "GET", params(), null);
        assertEquals(405, wrongMethod.status);
        assertEquals("POST", wrongMethod.allow);

        Out insecure = call(new ConfiguredAction(), "register", "POST", params(), "{\"redirect_uris\":[\"http://evil.example.com/cb\"]}");
        assertEquals(400, insecure.status);
        assertEquals("invalid_redirect_uri", field(insecure.body, "error"));

        Out https = call(new ConfiguredAction(), "register", "POST", params(), "{\"client_name\":\"App\",\"redirect_uris\":[\"https://app.example.com/cb\"]}");
        assertEquals(201, https.status);
        assertNotNull(McpOAuthClientsDao.instance.findById(field(https.body, "client_id")));

        Out appScheme = call(new ConfiguredAction(), "register", "POST", params(), "{\"redirect_uris\":[\"cursor://anysphere.cursor-mcp/oauth/callback\",\"http://localhost:5000/cb\"]}");
        assertEquals(201, appScheme.status);
    }

    @Test
    public void authorizeNeverRedirectsToAnUnregisteredUri() throws Exception {
        Out unknownClient = call(new ConfiguredAction(), "authorize", "GET", params("client_id", "nope", "redirect_uri", REDIRECT), null);
        assertEquals(400, unknownClient.status);
        assertNull(unknownClient.redirect);

        Out otherRedirect = call(new ConfiguredAction(), "authorize", "GET", params("client_id", CLIENT_ID, "redirect_uri", "https://attacker.example.com/cb"), null);
        assertEquals(400, otherRedirect.status);
        assertNull(otherRedirect.redirect);
    }

    @Test
    public void authorizeParksTheRequestAndHonoursALoopbackPortChange() throws Exception {
        McpOAuthClientsDao.instance.insertOne(new McpOAuthClient("native", "Native", Collections.singletonList("http://localhost:3118/callback"), 1, new Date()));
        Out out = call(new ConfiguredAction(), "authorize", "GET", params(
                "client_id", "native", "redirect_uri", "http://localhost:54331/callback", "response_type", "code",
                "code_challenge", CHALLENGE, "code_challenge_method", "S256", "state", "s1", "resource", RESOURCE), null);
        assertEquals(302, out.status);
        assertTrue(out.redirect.startsWith("/dashboard/mcp-oauth/consent?request_id="));
    }

    @Test
    public void authorizeRejectsAWrongResource() throws Exception {
        Out out = call(new ConfiguredAction(), "authorize", "GET", params(
                "client_id", CLIENT_ID, "redirect_uri", REDIRECT, "response_type", "code",
                "code_challenge", CHALLENGE, "code_challenge_method", "S256", "resource", "https://other.example.com/mcp"), null);
        assertEquals(302, out.status);
        assertTrue(out.redirect.startsWith(REDIRECT + "?error=invalid_target"));
    }

    @Test
    public void tokenEndpointIsPostOnly() throws Exception {
        Out out = call(new ConfiguredAction(), "token", "GET", params("client_id", CLIENT_ID), null);
        assertEquals(405, out.status);
        assertEquals("POST", out.allow);
    }

    @Test
    public void aWrongVerifierBurnsTheCode() throws Exception {
        insertCode(CLIENT_ID);
        Out wrong = token(codeRequest(CLIENT_ID, "x".repeat(43)));
        assertEquals(400, wrong.status);
        assertEquals("invalid_grant", field(wrong.body, "error"));

        Out retry = token(codeRequest(CLIENT_ID, VERIFIER));
        assertEquals(400, retry.status);
    }

    @Test
    public void aCodeIsBoundToItsClient() throws Exception {
        insertCode(CLIENT_ID);
        Out out = token(codeRequest("client-b", VERIFIER));
        assertEquals(400, out.status);
        assertEquals("invalid_grant", field(out.body, "error"));
    }

    @Test
    public void aRemovedUserCannotRedeemACode() throws Exception {
        insertCode(CLIENT_ID);
        UsersDao.instance.deleteAll(new BasicDBObject());
        Out out = token(codeRequest(CLIENT_ID, VERIFIER));
        assertEquals(400, out.status);
        assertEquals("invalid_grant", field(out.body, "error"));
    }

    @Test
    public void aResourceThatWasNotAuthorizedIsRejected() throws Exception {
        insertCode(CLIENT_ID);
        Map<String, String> request = codeRequest(CLIENT_ID, VERIFIER);
        request.put("resource", "https://other.example.com/mcp");
        Out out = token(request);
        assertEquals(400, out.status);
        assertEquals("invalid_target", field(out.body, "error"));
    }

    @Test
    public void codeIssuesATokenForTheMcpResourceAndRefreshRotates() throws Exception {
        insertCode(CLIENT_ID);
        Out issued = token(codeRequest(CLIENT_ID, VERIFIER));
        assertEquals(200, issued.status);
        Claims claims = JwtAuthenticator.authenticate(field(issued.body, "access_token")).getBody();
        assertEquals(RESOURCE, claims.get("aud"));
        assertEquals(CLIENT_ID, claims.get("client_id"));
        assertEquals("mcp", claims.get("auth_role"));

        assertEquals(400, token(codeRequest(CLIENT_ID, VERIFIER)).status);

        String refresh = field(issued.body, "refresh_token");
        Out rotated = token(params("grant_type", "refresh_token", "client_id", CLIENT_ID, "refresh_token", refresh));
        assertEquals(200, rotated.status);
        assertTrue(!refresh.equals(field(rotated.body, "refresh_token")));

        Out replay = token(params("grant_type", "refresh_token", "client_id", CLIENT_ID, "refresh_token", refresh));
        assertEquals(400, replay.status);
        assertEquals("invalid_grant", field(replay.body, "error"));
    }

    @Test
    public void aRefreshTokenCanBeSpentOnlyOnce() {
        McpOAuthGrant refresh = McpOAuthUtils.newGrant(McpOAuthGrant.TYPE_REFRESH, "refresh-secret", 60);
        McpOAuthGrantsDao.instance.insert(refresh);
        String hash = McpOAuthUtils.sha256Hex("refresh-secret");
        assertNotNull(McpOAuthGrantsDao.instance.consume(hash, McpOAuthGrant.TYPE_REFRESH));
        assertNull(McpOAuthGrantsDao.instance.consume(hash, McpOAuthGrant.TYPE_REFRESH));
    }

    @Test
    public void unexpectedFailuresAnswerInOAuthJson() throws Exception {
        McpOAuthAction action = new ConfiguredAction();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getParameter(anyString())).thenThrow(new IllegalStateException("boom"));
        Out out = new Out();
        StringWriter written = new StringWriter();
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(written, true));
        doAnswer(invocation -> { out.status = invocation.getArgument(0); return null; }).when(response).setStatus(anyInt());
        action.setServletRequest(request);
        action.setServletResponse(response);
        action.authorize();
        assertEquals(500, out.status);
        assertEquals("server_error", field(written.toString(), "error"));
    }
}
