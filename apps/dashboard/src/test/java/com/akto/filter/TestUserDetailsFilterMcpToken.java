package com.akto.filter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.junit.Before;
import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dao.ConfigsDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.akto.dto.User;
import com.akto.dto.UserAccountEntry;
import com.akto.util.McpTokenGenerator;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.BasicDBObject;

/* The MCP-token branch: OAuth tokens (client_id claim) reach tool endpoints only; Ask Akto tokens are unchanged. */
public class TestUserDetailsFilterMcpToken extends MongoBasedTest {

    private static final String USER = "token-user@example.com";
    private static final String RESOURCE = "https://mcp.example.com/mcp";

    @Before
    public void setup() throws Exception {
        Context.accountId.set(ACCOUNT_ID);
        Context.contextSource.set(CONTEXT_SOURCE.API);
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
    }

    private static class Result {
        FilterChain chain = mock(FilterChain.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
    }

    private Result request(String uri, String mcpToken) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getHeader("x-akto-mcp-token")).thenReturn(mcpToken);
        when(request.getSession(true)).thenReturn(session);
        Result result = new Result();
        new UserDetailsFilter().doFilter(request, result.response, result.chain);
        return result;
    }

    private String oauthToken() {
        return McpTokenGenerator.generateOAuthToken(USER, ACCOUNT_ID, "API", RESOURCE, "client-a");
    }

    private void assertReached(Result result) throws Exception {
        verify(result.chain).doFilter(any(ServletRequest.class), any(ServletResponse.class));
        verify(result.response, never()).sendError(anyInt());
    }

    private void assertBlocked(Result result) throws Exception {
        verify(result.response).sendError(403);
        verify(result.chain, never()).doFilter(any(ServletRequest.class), any(ServletResponse.class));
    }

    @Test
    public void anOAuthTokenReachesAToolEndpoint() throws Exception {
        assertReached(request("/api/getAllCollections", oauthToken()));
    }

    @Test
    public void anOAuthTokenCannotMintApiKeysOrInviteUsers() throws Exception {
        assertBlocked(request("/api/addApiToken", oauthToken()));
        assertBlocked(request("/api/inviteUsers", oauthToken()));
    }

    @Test
    public void anOAuthTokenCannotReachEndpointsOutsideTheTools() throws Exception {
        assertBlocked(request("/api/getAllUsers", oauthToken()));
        assertBlocked(request("/api/mcpOAuth/approveAuthorizeRequest", oauthToken()));
        assertBlocked(request("/dashboard/observe/inventory", oauthToken()));
    }

    @Test
    public void anAskAktoTokenIsUnchanged() throws Exception {
        String askAktoToken = McpTokenGenerator.generateToken(USER);
        assertReached(request("/api/getAllCollections", askAktoToken));
        assertReached(request("/api/getAllUsers", askAktoToken));
    }
}
