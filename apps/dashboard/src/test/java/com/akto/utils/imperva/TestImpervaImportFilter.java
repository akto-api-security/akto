package com.akto.utils.imperva;

import org.junit.Test;

import static com.akto.utils.imperva.ImpervaApiEndpointsImportJobUtil.matchesAnyPattern;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestImpervaImportFilter {

    @Test
    public void noPatternsMatchEverything() {
        assertTrue(matchesAnyPattern("api.example.com", null, true));
        assertTrue(matchesAnyPattern("api.example.com", "", true));
        assertTrue(matchesAnyPattern("/v1/users", " , ", false));
    }

    @Test
    public void exactPatternWithoutWildcard() {
        assertTrue(matchesAnyPattern("api.example.com", "api.example.com", true));
        assertTrue(matchesAnyPattern("API.Example.com", "api.example.com", true));
        assertFalse(matchesAnyPattern("api.example.com.evil.com", "api.example.com", true));
        assertFalse(matchesAnyPattern("/v1/users/1", "/v1/users", false));
    }

    @Test
    public void wildcardPatterns() {
        assertTrue(matchesAnyPattern("pay.internal.example.com", "*.internal.example.com", true));
        assertFalse(matchesAnyPattern("internal.example.com", "*.internal.example.com", true));
        assertTrue(matchesAnyPattern("/v1/payments/123/refunds", "/v1/payments/*", false));
        assertFalse(matchesAnyPattern("/v2/payments/123", "/v1/payments/*", false));
    }

    @Test
    public void anyOfCommaSeparatedPatterns() {
        assertTrue(matchesAnyPattern("pay.example.com", "api.example.com, pay.example.com", true));
        assertTrue(matchesAnyPattern("/health", "/v1/payments/*, /health", false));
        assertFalse(matchesAnyPattern("/metrics", "/v1/payments/*, /health", false));
    }

    @Test
    public void regexCharactersAreLiteral() {
        assertFalse(matchesAnyPattern("apiXexample.com", "api.example.com", true));
        assertTrue(matchesAnyPattern("/v1/items?id=1", "/v1/items?id=*", false));
    }

    @Test
    public void pathIsCaseSensitiveAndMissingValueDoesNotMatch() {
        assertFalse(matchesAnyPattern("/V1/Users", "/v1/users", false));
        assertFalse(matchesAnyPattern(null, "/v1/*", false));
    }
}
