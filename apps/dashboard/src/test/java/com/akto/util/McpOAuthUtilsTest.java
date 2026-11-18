package com.akto.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class McpOAuthUtilsTest {

    private static final String VERIFIER = "akto-mcp-oauth-test-verifier-0123456789-abcdefghij";
    private static final String CHALLENGE = "Co_a3Uz78JBxrGFVKy52TSAIEbajFNynV-YMxOBkWh4";

    @Test
    public void pkceAcceptsMatchingVerifier() {
        assertTrue(McpOAuthUtils.verifyPkce(VERIFIER, CHALLENGE));
    }

    @Test
    public void pkceRejectsWrongOrShortVerifier() {
        assertFalse(McpOAuthUtils.verifyPkce(VERIFIER + "x", CHALLENGE));
        assertFalse(McpOAuthUtils.verifyPkce("too-short", CHALLENGE));
        assertFalse(McpOAuthUtils.verifyPkce(null, CHALLENGE));
        assertFalse(McpOAuthUtils.verifyPkce(VERIFIER, null));
    }

    @Test
    public void redirectUriAllowList() {
        assertTrue(McpOAuthUtils.isAllowedRedirectUri("https://claude.ai/api/mcp/auth_callback"));
        assertTrue(McpOAuthUtils.isAllowedRedirectUri("http://localhost:6274/cb"));
        assertTrue(McpOAuthUtils.isAllowedRedirectUri("http://127.0.0.1:8123/cb"));
        assertFalse(McpOAuthUtils.isAllowedRedirectUri("http://example.com/cb"));
        assertFalse(McpOAuthUtils.isAllowedRedirectUri("javascript:alert(1)"));
        assertFalse(McpOAuthUtils.isAllowedRedirectUri("https://example.com/cb#frag"));
        assertFalse(McpOAuthUtils.isAllowedRedirectUri("/relative/path"));
        assertFalse(McpOAuthUtils.isAllowedRedirectUri(null));
    }

    @Test
    public void withParamsHandlesExistingQueryAndSkipsNulls() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("code", "abc");
        params.put("state", null);
        params.put("note", "a b");
        assertEquals("https://example.com/cb?x=1&code=abc&note=a+b",
                McpOAuthUtils.withParams("https://example.com/cb?x=1", params));
        assertEquals("https://example.com/cb?code=abc&note=a+b",
                McpOAuthUtils.withParams("https://example.com/cb", params));
    }

    @Test
    public void secretsAreUniqueAndHashIsStable() {
        assertNotEquals(McpOAuthUtils.newSecret(), McpOAuthUtils.newSecret());
        assertEquals(64, McpOAuthUtils.sha256Hex("secret").length());
        assertEquals(McpOAuthUtils.sha256Hex("secret"), McpOAuthUtils.sha256Hex("secret"));
    }

    @Test
    public void resourceValidationAndDefaulting() {
        String mcp = "https://mcp.example.com/mcp";
        assertTrue(McpOAuthUtils.isResourceAllowed(null, mcp));
        assertTrue(McpOAuthUtils.isResourceAllowed(mcp, mcp));
        assertTrue(McpOAuthUtils.isResourceAllowed(mcp + "/", mcp));
        assertTrue(McpOAuthUtils.isResourceAllowed("https://anything.example.com", null));
        assertFalse(McpOAuthUtils.isResourceAllowed("https://other.example.com/mcp", mcp));

        assertEquals(mcp, McpOAuthUtils.resolveResource(null, mcp));
        assertEquals(mcp, McpOAuthUtils.resolveResource(mcp + "/", mcp));
        assertEquals("https://anything.example.com", McpOAuthUtils.resolveResource("https://anything.example.com", null));
    }

    @Test
    public void codeChallengeFormat() {
        assertTrue(McpOAuthUtils.isValidCodeChallenge(CHALLENGE));
        assertFalse(McpOAuthUtils.isValidCodeChallenge("short"));
        assertFalse(McpOAuthUtils.isValidCodeChallenge(CHALLENGE + "="));
        assertFalse(McpOAuthUtils.isValidCodeChallenge(null));
    }

    @Test
    public void paramLimit() {
        assertFalse(McpOAuthUtils.exceedsParamLimit(null));
        assertFalse(McpOAuthUtils.exceedsParamLimit("state-value"));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= McpOAuthUtils.MAX_PARAM_LENGTH; i++) sb.append('a');
        assertTrue(McpOAuthUtils.exceedsParamLimit(sb.toString()));
    }

    @Test
    public void grantIsKeyedByHashOfSecretAndExpires() {
        long before = System.currentTimeMillis();
        com.akto.dto.McpOAuthGrant grant = McpOAuthUtils.newGrant(com.akto.dto.McpOAuthGrant.TYPE_CODE, "secret", 60);
        assertEquals(McpOAuthUtils.sha256Hex("secret"), grant.getId());
        assertEquals(com.akto.dto.McpOAuthGrant.TYPE_CODE, grant.getType());
        assertTrue(grant.getExpiresAt().getTime() >= before + 60_000L);
    }

    @Test
    public void redirectWithEchoesStateAndSkipsMissingState() {
        assertEquals("https://example.com/cb?code=abc&state=xyz", McpOAuthUtils.redirectWith("https://example.com/cb", "xyz", "code", "abc"));
        assertEquals("https://example.com/cb?error=access_denied", McpOAuthUtils.redirectWith("https://example.com/cb", null, "error", "access_denied"));
    }

    @Test
    public void redirectUriEdgeCases() {
        assertTrue(McpOAuthUtils.isAllowedRedirectUri("http://[::1]:8080/cb"));
        assertTrue(McpOAuthUtils.isAllowedRedirectUri("HTTPS://example.com/cb"));
        assertFalse(McpOAuthUtils.isAllowedRedirectUri("http://localhost.evil.example.com/cb"));
        assertTrue(McpOAuthUtils.isAllowedRedirectUri("custom-scheme://callback"));
    }

    @Test
    public void sha256MatchesKnownVectorAndSecretsHaveFullEntropyLength() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", McpOAuthUtils.sha256Hex("abc"));
        // 32 random bytes as unpadded base64url
        assertEquals(43, McpOAuthUtils.newSecret().length());
    }

    @Test
    public void appSchemesAreAcceptedAndBrowserExecutableSchemesStayBlocked() {
        assertTrue(McpOAuthUtils.isAllowedRedirectUri("cursor://anysphere.cursor-mcp/oauth/callback"));
        assertTrue(McpOAuthUtils.isAllowedRedirectUri("vscode://vscode.github-authentication/did-authenticate"));
        assertTrue(McpOAuthUtils.isAllowedRedirectUri("com.example.app:/oauth2redirect"));
        assertFalse(McpOAuthUtils.isAllowedRedirectUri("data:text/html,x"));
        assertFalse(McpOAuthUtils.isAllowedRedirectUri("file:///etc/passwd"));
        assertFalse(McpOAuthUtils.isAllowedRedirectUri("vbscript:x"));
        assertFalse(McpOAuthUtils.isAllowedRedirectUri("blob:https://example.com/id"));
        assertFalse(McpOAuthUtils.isAllowedRedirectUri("about:blank"));
        assertFalse(McpOAuthUtils.isAllowedRedirectUri("opaque:payload"));
        assertFalse(McpOAuthUtils.isAllowedRedirectUri("com.example.app://cb#frag"));
        assertFalse(McpOAuthUtils.isAllowedRedirectUri("https:///no-host"));
    }

    @Test
    public void loopbackRedirectsMatchOnAnyPortButOthersMatchExactly() {
        assertTrue(McpOAuthUtils.redirectUriMatches("http://localhost:54331/callback", "http://localhost:3118/callback"));
        assertTrue(McpOAuthUtils.redirectUriMatches("http://127.0.0.1:9/cb?x=1", "http://127.0.0.1:8/cb?x=1"));
        assertTrue(McpOAuthUtils.redirectUriMatches("http://[::1]:9/cb", "http://[::1]/cb"));
        assertFalse(McpOAuthUtils.redirectUriMatches("http://localhost:9/other", "http://localhost:8/cb"));
        assertFalse(McpOAuthUtils.redirectUriMatches("http://127.0.0.1:9/cb", "http://localhost:9/cb"));
        assertFalse(McpOAuthUtils.redirectUriMatches("https://example.com:444/cb", "https://example.com/cb"));
        assertTrue(McpOAuthUtils.redirectUriMatches("https://claude.ai/api/mcp/auth_callback", "https://claude.ai/api/mcp/auth_callback"));
        assertFalse(McpOAuthUtils.redirectUriMatches(null, "https://claude.ai/cb"));
    }

    @Test
    public void knownRedirectsAreExactUrisNotHostsOrSubdomains() {
        assertTrue(McpOAuthUtils.isKnownRedirect("https://claude.ai/api/mcp/auth_callback"));
        assertTrue(McpOAuthUtils.isKnownRedirect("https://chatgpt.com/connector_platform_oauth_redirect"));
        assertTrue(McpOAuthUtils.isKnownRedirect("https://chatgpt.com/connector/oauth/abc123"));
        assertTrue(McpOAuthUtils.isKnownRedirect("http://localhost:6274/cb"));
        assertTrue(McpOAuthUtils.isKnownRedirect("cursor://anysphere.cursor-mcp/oauth/callback"));
        assertFalse(McpOAuthUtils.isKnownRedirect("https://claude.ai/some/other/page"));
        assertFalse(McpOAuthUtils.isKnownRedirect("https://claude.ai/api/mcp/auth_callback/extra"));
        assertFalse(McpOAuthUtils.isKnownRedirect("https://user-content.claude.ai/api/mcp/auth_callback"));
        assertFalse(McpOAuthUtils.isKnownRedirect("https://chatgpt.com/connector/oauthx"));
        assertFalse(McpOAuthUtils.isKnownRedirect("https://app.cursor.com/cb"));
        assertFalse(McpOAuthUtils.isKnownRedirect("https://attacker.example.com/cb"));
        assertFalse(McpOAuthUtils.isKnownRedirect("https://claude.ai.attacker.example.com/cb"));
    }

    @Test
    public void requestBindingCookieIsHttpOnlyLaxAndSecureOnlyOverTls() {
        String hash = McpOAuthUtils.sha256Hex("request-secret");
        assertEquals("mcp_oauth_" + hash.substring(0, 16), McpOAuthUtils.requestBindingCookieName(hash));
        String plain = McpOAuthUtils.requestBindingCookie(hash, false);
        assertTrue(plain.startsWith("mcp_oauth_" + hash.substring(0, 16) + "=" + hash));
        assertTrue(plain.contains("HttpOnly") && plain.contains("SameSite=Lax") && plain.contains("Max-Age=" + McpOAuthUtils.REQUEST_TTL_SECONDS));
        assertFalse(plain.contains("Secure"));
        assertTrue(McpOAuthUtils.requestBindingCookie(hash, true).endsWith("; Secure"));
    }

    @Test
    public void consentShowsHostOrSchemeForHostlessUris() {
        assertEquals("claude.ai", McpOAuthUtils.redirectDisplayHost("https://claude.ai/cb"));
        assertEquals("localhost", McpOAuthUtils.redirectDisplayHost("http://localhost:1/cb"));
        assertEquals("com.example.app", McpOAuthUtils.redirectDisplayHost("com.example.app:/oauth2redirect"));
        assertEquals("", McpOAuthUtils.redirectDisplayHost(null));
    }

    @Test
    public void adminAllowedUrisExtendTheKnownListExactlyOrByPrefix() {
        List<String> admin = Arrays.asList("https://app.example.com/oauth/callback", "https://agents.example.com/connect/*");
        assertTrue(McpOAuthUtils.isKnownRedirect("https://app.example.com/oauth/callback", admin));
        assertTrue(McpOAuthUtils.isKnownRedirect("https://agents.example.com/connect/abc", admin));
        assertFalse(McpOAuthUtils.isKnownRedirect("https://app.example.com/oauth/callback/extra", admin));
        assertFalse(McpOAuthUtils.isKnownRedirect("https://agents.example.com/other", admin));
        assertFalse(McpOAuthUtils.isKnownRedirect("https://app.example.com/oauth/callback", null));
        assertTrue(McpOAuthUtils.isKnownRedirect("https://claude.ai/api/mcp/auth_callback", admin));
    }

    @Test
    public void adminEntriesMustBeHttpsWithAHostAndPath() {
        assertTrue(McpOAuthUtils.isValidAllowedRedirectEntry("https://app.example.com/oauth/callback"));
        assertTrue(McpOAuthUtils.isValidAllowedRedirectEntry("https://app.example.com:8443/cb/*"));
        assertTrue(McpOAuthUtils.isValidAllowedRedirectEntry("https://app.example.com/*"));
        assertFalse(McpOAuthUtils.isValidAllowedRedirectEntry("http://app.example.com/cb"));
        assertFalse(McpOAuthUtils.isValidAllowedRedirectEntry("https://app.example.com"));
        assertFalse(McpOAuthUtils.isValidAllowedRedirectEntry("https://*.example.com/cb"));
        assertFalse(McpOAuthUtils.isValidAllowedRedirectEntry("https://*/cb"));
        assertFalse(McpOAuthUtils.isValidAllowedRedirectEntry("https://app.example.com/a*b"));
        assertFalse(McpOAuthUtils.isValidAllowedRedirectEntry("https://user@app.example.com/cb"));
        assertFalse(McpOAuthUtils.isValidAllowedRedirectEntry("https://app.example.com/cb?x=1"));
        assertFalse(McpOAuthUtils.isValidAllowedRedirectEntry("cursor://anysphere.cursor-mcp/oauth/callback"));
        assertFalse(McpOAuthUtils.isValidAllowedRedirectEntry(""));
        assertFalse(McpOAuthUtils.isValidAllowedRedirectEntry(null));
    }

    @Test
    public void oauthTokensReachToolEndpointsOnly() {
        assertTrue(McpOAuthUtils.isToolEndpoint("/api/getAllCollections"));
        assertTrue(McpOAuthUtils.isToolEndpoint("/api/fetchAllIssues"));
        assertFalse(McpOAuthUtils.isToolEndpoint("/api/inviteUsers"));
        assertFalse(McpOAuthUtils.isToolEndpoint("/api/addApiToken"));
        assertFalse(McpOAuthUtils.isToolEndpoint("/api/fetchApiTokens"));
        assertFalse(McpOAuthUtils.isToolEndpoint("/api/mcpOAuth/approveAuthorizeRequest"));
        assertFalse(McpOAuthUtils.isToolEndpoint("/api/getAllCollections/"));
        assertFalse(McpOAuthUtils.isToolEndpoint("/API/getAllCollections"));
        assertFalse(McpOAuthUtils.isToolEndpoint("/dashboard/observe/inventory"));
        assertFalse(McpOAuthUtils.isToolEndpoint(""));
        assertFalse(McpOAuthUtils.isToolEndpoint(null));
    }
}
