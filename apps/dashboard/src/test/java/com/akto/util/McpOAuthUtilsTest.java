package com.akto.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
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
        assertFalse(McpOAuthUtils.isAllowedRedirectUri("custom-scheme://callback"));
    }

    @Test
    public void sha256MatchesKnownVectorAndSecretsHaveFullEntropyLength() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", McpOAuthUtils.sha256Hex("abc"));
        // 32 random bytes as unpadded base64url
        assertEquals(43, McpOAuthUtils.newSecret().length());
    }
}
