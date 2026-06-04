package com.akto.merging;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Comprehensive tests for optimizedTemplateToStaticMatch.
 * Every test compares optimized result against brute-force to ensure parity.
 * apiCollectionId=0 is used throughout (no demerged URLs in test context).
 */
public class TestOptimizedTemplateToStaticMatch {

    // Helper: build staticUrlToSti map from a list of URL strings (values are empty sets, not needed for matching)
    private static Map<String, Set<String>> toStaticMap(String... urls) {
        Map<String, Set<String>> map = new LinkedHashMap<>();
        for (String url : urls) {
            map.put(url, new HashSet<>());
        }
        return map;
    }

    // Brute force reference implementation — iterates all templates for each static URL
    private static Set<String> bruteForceMatch(List<String> templateUrls, Map<String, Set<String>> staticUrlToSti, int apiCollectionId) {
        Set<String> matched = new HashSet<>();
        for (String staticURL : staticUrlToSti.keySet()) {
            if (staticURL == null || staticURL.isEmpty() || !staticURL.contains(" ")) continue;
            String[] parts = staticURL.split(" ", 2);
            if (parts.length < 2) continue;
            String endpoint = parts[1];
            if (endpoint.contains("//") || endpoint.isEmpty()) continue;
            com.akto.dto.type.URLMethods.Method method = com.akto.dto.type.URLMethods.Method.fromString(parts[0]);

            for (String templateURL : templateUrls) {
                if (templateURL == null || templateURL.isEmpty() || !templateURL.contains(" ")) continue;
                String[] tParts = templateURL.split(" ", 2);
                if (tParts.length < 2) continue;
                String tEndpoint = tParts[1];
                if (tEndpoint.contains("//") || tEndpoint.isEmpty()) continue;
                com.akto.dto.type.URLTemplate urlTemplate = MergingLogic.createUrlTemplate(tEndpoint,
                        com.akto.dto.type.URLMethods.Method.fromString(tParts[0]));
                if (urlTemplate.match(endpoint, method)) {
                    matched.add(staticURL);
                    break;
                }
            }
        }
        return matched;
    }

    /** Wrapper that adapts the new void signature back to a Set<String> return for test convenience. */
    private static Set<String> callOptimized(List<String> templates, Map<String, Set<String>> statics, int apiCollectionId) {
        MergingLogic.ApiMergerResult result = new MergingLogic.ApiMergerResult(new HashMap<>());
        MergingLogic.optimizedTemplateToStaticMatch(templates, statics, apiCollectionId, result);
        return result.deleteStaticUrls;
    }

    private void assertMatchesBruteForce(List<String> templates, Map<String, Set<String>> statics, int expectedSize) {
        Set<String> brute = bruteForceMatch(templates, statics, 0);
        Set<String> optimized = callOptimized(templates, statics, 0);
        assertEquals("Optimized should match brute force", brute, optimized);
        assertEquals("Expected match count", expectedSize, optimized.size());
    }

    // ==================== BASIC MATCHING ====================

    @Test
    public void testSingleIntegerTemplate() {
        List<String> templates = Arrays.asList("GET /api/users/INTEGER");
        Map<String, Set<String>> statics = toStaticMap(
                "GET /api/users/123",
                "GET /api/users/456",
                "GET /api/users/0",
                "GET /api/users/-1"
        );
        assertMatchesBruteForce(templates, statics, 4);
    }

    @Test
    public void testSingleStringTemplate() {
        List<String> templates = Arrays.asList("POST /v1/STRING/config");
        Map<String, Set<String>> statics = toStaticMap(
                "POST /v1/123/config"         // numbers also match STRING
        );
        assertMatchesBruteForce(templates, statics, 1);
    }

    @Test
    public void testSingleLocaleTemplate() {
        List<String> templates = Arrays.asList("GET /api/LOCALE/products");
        Map<String, Set<String>> statics = toStaticMap(
                "GET /api/en/products",
                "GET /api/fr/products",
                "GET /api/ja/products",
                "GET /api/notlocale/products"  // might not match LOCALE type
        );
        // LOCALE is not recognized by createUrlTemplate, treated as literal "LOCALE"
        assertMatchesBruteForce(templates, statics, 4);
    }

    // ==================== NO MATCH CASES ====================

    @Test
    public void testNoMatchDifferentPrefix() {
        List<String> templates = Arrays.asList("GET /api/users/INTEGER");
        Map<String, Set<String>> statics = toStaticMap(
                "GET /api/orders/123",
                "GET /v2/users/123",
                "GET /users/123"
        );
        assertMatchesBruteForce(templates, statics, 0);
    }

    @Test
    public void testNoMatchDifferentTokenCount() {
        List<String> templates = Arrays.asList("GET /api/users/INTEGER");
        Map<String, Set<String>> statics = toStaticMap(
                "GET /api/users/123/profile",
                "GET /api/users",
                "GET /api/users/123/posts/456"
        );
        assertMatchesBruteForce(templates, statics, 0);
    }

    @Test
    public void testNoMatchDifferentMethod() {
        List<String> templates = Arrays.asList("GET /api/users/INTEGER");
        Map<String, Set<String>> statics = toStaticMap(
                "POST /api/users/123",
                "PUT /api/users/123",
                "DELETE /api/users/123",
                "PATCH /api/users/123"
        );
        assertMatchesBruteForce(templates, statics, 0);
    }

    @Test
    public void testNoMatchEnglishWordRejectsInteger() {
        // DictionaryFilter.isEnglishWord rejects English words for INTEGER matching
        List<String> templates = Arrays.asList("GET /api/users/INTEGER");
        Map<String, Set<String>> statics = toStaticMap(
                "GET /api/users/hello",
                "GET /api/users/world",
                "GET /api/users/admin"
        );
        assertMatchesBruteForce(templates, statics, 0);
    }

    @Test
    public void testEmptyTemplates() {
        List<String> templates = Collections.emptyList();
        Map<String, Set<String>> statics = toStaticMap("GET /api/users/123");
        Set<String> result = callOptimized(templates, statics, 0);
        assertTrue("Empty templates should match nothing", result.isEmpty());
    }

    @Test
    public void testEmptyStatics() {
        List<String> templates = Arrays.asList("GET /api/users/INTEGER");
        Map<String, Set<String>> statics = new HashMap<>();
        Set<String> result = callOptimized(templates, statics, 0);
        assertTrue("Empty statics should match nothing", result.isEmpty());
    }

    @Test
    public void testBothEmpty() {
        Set<String> result = callOptimized(Collections.emptyList(), new HashMap<>(), 0);
        assertTrue("Both empty should match nothing", result.isEmpty());
    }

    // ==================== MULTIPLE WILDCARDS ====================

    @Test
    public void testTwoWildcardsInTemplate() {
        List<String> templates = Arrays.asList("GET /api/INTEGER/items/INTEGER");
        Map<String, Set<String>> statics = toStaticMap(
                "GET /api/5/items/10",
                "GET /api/99/items/200",
                "GET /api/0/items/0"
        );
        assertMatchesBruteForce(templates, statics, 3);
    }

    @Test
    public void testTwoWildcardsAtStartAndEnd() {
        List<String> templates = Arrays.asList("GET /INTEGER/api/items/INTEGER");
        Map<String, Set<String>> statics = toStaticMap(
                "GET /5/api/items/10",
                "GET /99/api/items/200"
        );
        assertMatchesBruteForce(templates, statics, 2);
    }

    @Test
    public void testTwoAdjacentWildcards() {
        List<String> templates = Arrays.asList("GET /api/INTEGER/INTEGER/details");
        Map<String, Set<String>> statics = toStaticMap(
                "GET /api/5/10/details",
                "GET /api/99/200/details"
        );
        assertMatchesBruteForce(templates, statics, 2);
    }

    @Test
    public void testMixedWildcardTypes() {
        List<String> templates = Arrays.asList("GET /api/STRING/items/INTEGER");
        Map<String, Set<String>> statics = toStaticMap(
                "GET /api/mystore/items/10",
                "GET /api/abc123/items/99"
        );
        assertMatchesBruteForce(templates, statics, 2);
    }

    // ==================== MULTIPLE TEMPLATES ====================

    @Test
    public void testMultipleTemplatesSameTokenCount() {
        List<String> templates = Arrays.asList(
                "GET /api/users/INTEGER",
                "GET /api/orders/INTEGER",
                "GET /api/items/INTEGER"
        );
        Map<String, Set<String>> statics = toStaticMap(
                "GET /api/users/42",
                "GET /api/orders/99",
                "GET /api/items/7",
                "GET /api/other/123"  // no match
        );
        assertMatchesBruteForce(templates, statics, 3);
    }

    @Test
    public void testMultipleTemplatesDifferentTokenCount() {
        List<String> templates = Arrays.asList(
                "GET /api/users/INTEGER",
                "GET /api/users/INTEGER/posts",
                "GET /api/users/INTEGER/posts/INTEGER"
        );
        Map<String, Set<String>> statics = toStaticMap(
                "GET /api/users/42",
                "GET /api/users/42/posts",
                "GET /api/users/42/posts/7"
        );
        assertMatchesBruteForce(templates, statics, 3);
    }

    @Test
    public void testMultipleTemplatesDifferentMethods() {
        List<String> templates = Arrays.asList(
                "GET /api/users/INTEGER",
                "POST /api/users/INTEGER",
                "DELETE /api/users/INTEGER"
        );
        Map<String, Set<String>> statics = toStaticMap(
                "GET /api/users/42",
                "POST /api/users/42",
                "DELETE /api/users/42",
                "PUT /api/users/42"   // no matching template
        );
        assertMatchesBruteForce(templates, statics, 3);
    }

    // ==================== EXACT MATCH (template URL = static URL) ====================

    @Test
    public void testExactMatchTemplateEqualsStatic() {
        // A template URL string that is also a valid static URL (e.g., /health)
        List<String> templates = Arrays.asList("GET /health");
        Map<String, Set<String>> statics = toStaticMap("GET /health");
        // /health has no wildcard tokens, so createUrlTemplate produces all-fixed tokens
        // match() does exact string comparison first
        assertMatchesBruteForce(templates, statics, 1);
    }

    @Test
    public void testExactMatchTemplateStringInStatic() {
        // Static URL literally contains "INTEGER" as a path token
        List<String> templates = Arrays.asList("GET /api/users/INTEGER");
        Map<String, Set<String>> statics = toStaticMap("GET /api/users/INTEGER");
        // URLTemplate.match does exact string comparison: url == templateString → true
        assertMatchesBruteForce(templates, statics, 1);
    }

    // ==================== URL NORMALIZATION EDGE CASES ====================

    @Test
    public void testLeadingSlash() {
        List<String> templates = Arrays.asList("GET /api/users/INTEGER");
        Map<String, Set<String>> statics = toStaticMap("GET /api/users/123");
        assertMatchesBruteForce(templates, statics, 1);
    }

    @Test
    public void testTrailingSlash() {
        List<String> templates = Arrays.asList("GET /api/users/INTEGER");
        Map<String, Set<String>> statics = toStaticMap("GET /api/users/123/");
        assertMatchesBruteForce(templates, statics, 1);
    }

    @Test
    public void testDoubleSlashInStaticSkipped() {
        List<String> templates = Arrays.asList("GET /api/users/INTEGER");
        Map<String, Set<String>> statics = toStaticMap("GET /api//users/123");
        // Double slash should be skipped
        Set<String> result = callOptimized(templates, statics, 0);
        assertTrue("Double-slash URLs should be skipped", result.isEmpty());
    }

    @Test
    public void testDoubleSlashInTemplateSkipped() {
        List<String> templates = Arrays.asList("GET /api//users/INTEGER");
        Map<String, Set<String>> statics = toStaticMap("GET /api/users/123");
        // buildTemplateIndex skips templates with //
        Set<String> result = callOptimized(templates, statics, 0);
        assertMatchesBruteForce(templates, statics, 0);
    }

    @Test
    public void testEmptyEndpointSkipped() {
        List<String> templates = Arrays.asList("GET /api/users/INTEGER");
        Map<String, Set<String>> statics = toStaticMap("GET ");
        Set<String> result = callOptimized(templates, statics, 0);
        assertTrue("Empty endpoint should be skipped", result.isEmpty());
    }

    // ==================== INVALID INPUT HANDLING ====================

    @Test
    public void testNullStaticURL() {
        List<String> templates = Arrays.asList("GET /api/users/INTEGER");
        Map<String, Set<String>> statics = new HashMap<>();
        statics.put(null, new HashSet<>());
        statics.put("GET /api/users/123", new HashSet<>());
        Set<String> result = callOptimized(templates, statics, 0);
        // Should not crash, should still match the valid one
        assertTrue("Should match valid URL", result.contains("GET /api/users/123"));
    }

    @Test
    public void testEmptyStringStaticURL() {
        List<String> templates = Arrays.asList("GET /api/users/INTEGER");
        Map<String, Set<String>> statics = new HashMap<>();
        statics.put("", new HashSet<>());
        statics.put("GET /api/users/123", new HashSet<>());
        Set<String> result = callOptimized(templates, statics, 0);
        assertTrue("Should match valid URL", result.contains("GET /api/users/123"));
        assertEquals(1, result.size());
    }

    @Test
    public void testStaticURLWithNoSpace() {
        List<String> templates = Arrays.asList("GET /api/users/INTEGER");
        Map<String, Set<String>> statics = toStaticMap("GET/api/users/123");  // no space
        Set<String> result = callOptimized(templates, statics, 0);
        // "GET/api/users/123" has no space → skipped
        assertTrue("No-space URL should be skipped", result.isEmpty());
    }


    @Test
    public void testEmptyTemplateInList() {
        List<String> templates = new ArrayList<>();
        templates.add("");
        templates.add("GET /api/users/INTEGER");
        Map<String, Set<String>> statics = toStaticMap("GET /api/users/123");
        Set<String> result = callOptimized(templates, statics, 0);
        assertTrue("Should match despite empty template", result.contains("GET /api/users/123"));
    }

    // ==================== SINGLE TOKEN URLS ====================

    @Test
    public void testSingleTokenStaticURL() {
        List<String> templates = Arrays.asList("GET /INTEGER");
        Map<String, Set<String>> statics = toStaticMap("GET /123", "GET /456");
        assertMatchesBruteForce(templates, statics, 2);
    }

    @Test
    public void testRootURL() {
        List<String> templates = Arrays.asList("GET /api/users/INTEGER");
        Map<String, Set<String>> statics = toStaticMap("GET /");
        assertMatchesBruteForce(templates, statics, 0);
    }

    // ==================== LONG URLS ====================

    @Test
    public void testLongURL() {
        List<String> templates = Arrays.asList("GET /a/b/c/d/e/f/g/h/INTEGER/j");
        Map<String, Set<String>> statics = toStaticMap(
                "GET /a/b/c/d/e/f/g/h/123/j",
                "GET /a/b/c/d/e/f/g/h/456/j"
        );
        assertMatchesBruteForce(templates, statics, 2);
    }

    @Test
    public void testVeryLongURLManyTokens() {
        // 12 tokens, 1 wildcard — should work with maxWildcards=2
        List<String> templates = Arrays.asList("GET /a/b/c/d/e/f/g/h/i/j/k/INTEGER");
        Map<String, Set<String>> statics = toStaticMap("GET /a/b/c/d/e/f/g/h/i/j/k/999");
        assertMatchesBruteForce(templates, statics, 1);
    }

    // ==================== OVERLAPPING TEMPLATES ====================

    @Test
    public void testOverlappingTemplatesSameStructure() {
        // Two templates with same structure but different fixed tokens
        List<String> templates = Arrays.asList(
                "GET /api/users/INTEGER",
                "GET /api/orders/INTEGER"
        );
        Map<String, Set<String>> statics = toStaticMap(
                "GET /api/users/42",
                "GET /api/orders/42"
        );
        assertMatchesBruteForce(templates, statics, 2);
    }

    @Test
    public void testStaticMatchesFirstTemplateOnly() {
        // Static URL could theoretically pattern-match multiple templates, but should stop at first
        List<String> templates = Arrays.asList(
                "GET /api/INTEGER/config",
                "GET /api/STRING/config"
        );
        Map<String, Set<String>> statics = toStaticMap("GET /api/123/config");
        // Both templates could match — should match first found (INTEGER)
        assertMatchesBruteForce(templates, statics, 1);
    }

    // ==================== SPECIAL URL PATTERNS ====================

    @Test
    public void testURLWithHyphens() {
        List<String> templates = Arrays.asList("GET /api/my-service/INTEGER");
        Map<String, Set<String>> statics = toStaticMap(
                "GET /api/my-service/123",
                "GET /api/my-service/456"
        );
        assertMatchesBruteForce(templates, statics, 2);
    }

    @Test
    public void testURLWithDots() {
        List<String> templates = Arrays.asList("GET /api/v1.0/users/INTEGER");
        Map<String, Set<String>> statics = toStaticMap(
                "GET /api/v1.0/users/123",
                "GET /api/v2.0/users/123"  // different prefix, no match
        );
        assertMatchesBruteForce(templates, statics, 1);
    }

    @Test
    public void testURLWithUnderscores() {
        List<String> templates = Arrays.asList("GET /api/user_profile/INTEGER");
        Map<String, Set<String>> statics = toStaticMap("GET /api/user_profile/42");
        assertMatchesBruteForce(templates, statics, 1);
    }

    @Test
    public void testURLWithCaseSensitiveTokens() {
        List<String> templates = Arrays.asList("GET /Api/Users/INTEGER");
        Map<String, Set<String>> statics = toStaticMap(
                "GET /Api/Users/123",     // exact case match
                "GET /api/users/123",     // different case — should NOT match
                "GET /API/USERS/123"      // different case — should NOT match
        );
        assertMatchesBruteForce(templates, statics, 1);
    }

    // ==================== NUMERIC EDGE CASES ====================

    @Test
    public void testLargeNumbers() {
        List<String> templates = Arrays.asList("GET /api/users/INTEGER");
        Map<String, Set<String>> statics = toStaticMap(
                "GET /api/users/999999999999",
                "GET /api/users/0",
                "GET /api/users/1"
        );
        assertMatchesBruteForce(templates, statics, 3);
    }

    @Test
    public void testNegativeNumbers() {
        List<String> templates = Arrays.asList("GET /api/users/INTEGER");
        Map<String, Set<String>> statics = toStaticMap(
                "GET /api/users/-1",
                "GET /api/users/-999"
        );
        assertMatchesBruteForce(templates, statics, 2);
    }

    @Test
    public void testFloatNotMatchingInteger() {
        List<String> templates = Arrays.asList("GET /api/users/INTEGER");
        Map<String, Set<String>> statics = toStaticMap(
                "GET /api/users/1.5",
                "GET /api/users/0.0"
        );
        // Floats should NOT match INTEGER template
        assertMatchesBruteForce(templates, statics, 0);
    }

    // ==================== UNRECOGNIZED TEMPLATE TYPES (OBJECT_ID, FLOAT, UUID, VERSIONED) ====================

    @Test
    public void testObjectIdInTemplateTreatedAsLiteral() {
        // createUrlTemplate doesn't recognize OBJECT_ID — treats it as literal string
        List<String> templates = Arrays.asList("GET /api/users/OBJECT_ID");
        Map<String, Set<String>> statics = toStaticMap(
                "GET /api/users/507f1f77bcf86cd799439011",
                "GET /api/users/OBJECT_ID"  // exact literal match
        );
        assertMatchesBruteForce(templates, statics, 1);
    }

    @Test
    public void testUuidInTemplateTreatedAsLiteral() {
        List<String> templates = Arrays.asList("GET /api/users/UUID");
        Map<String, Set<String>> statics = toStaticMap(
                "GET /api/users/550e8400-e29b-41d4-a716-446655440000",
                "GET /api/users/UUID"  // exact literal match
        );
        assertMatchesBruteForce(templates, statics, 1);
    }

    @Test
    public void testFloatInTemplateTreatedAsLiteral() {
        List<String> templates = Arrays.asList("GET /api/users/FLOAT");
        Map<String, Set<String>> statics = toStaticMap(
                "GET /api/users/1.5",
                "GET /api/users/FLOAT"
        );
        assertMatchesBruteForce(templates, statics, 1);
    }

    @Test
    public void testVersionedInTemplateTreatedAsLiteral() {
        List<String> templates = Arrays.asList("GET /api/users/VERSIONED");
        Map<String, Set<String>> statics = toStaticMap(
                "GET /api/users/v2",
                "GET /api/users/VERSIONED"
        );
        assertMatchesBruteForce(templates, statics, 1);
    }

    // ==================== MIXED MATCH AND NO-MATCH ====================

    @Test
    public void testMixedMatchAndNoMatch() {
        List<String> templates = Arrays.asList(
                "GET /api/users/INTEGER",
                "GET /api/users/INTEGER/posts/INTEGER",
                "POST /v1/STRING/config",
                "GET /health"
        );
        Map<String, Set<String>> statics = toStaticMap(
                "GET /api/users/42",               // match
                "GET /api/users/42/posts/7",        // match
                "POST /v1/myservice/config",        // match
                "GET /health",                      // match (exact)
                "GET /api/users/42/comments",       // no match (no template)
                "DELETE /api/users/42",             // no match (method)
                "GET /api/products/42",             // no match (different prefix)
                "GET /api/users",                   // no match (token count)
                "GET /api/users/42/posts"           // no match (token count)
        );
        assertMatchesBruteForce(templates, statics, 4);
    }

    // ==================== SCALE / MANY TEMPLATES ====================

    @Test
    public void testManyTemplatesSameTokenCount() {
        // 1000 templates all with 3 tokens, only some match
        List<String> templates = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            templates.add("GET /api/svc" + i + "/INTEGER");
        }
        Map<String, Set<String>> statics = toStaticMap(
                "GET /api/svc0/123",
                "GET /api/svc500/456",
                "GET /api/svc999/789",
                "GET /api/svc1000/123",  // no match — svc1000 doesn't exist
                "GET /api/other/123"      // no match
        );
        assertMatchesBruteForce(templates, statics, 3);
    }

    @Test
    public void testManyStaticURLs() {
        List<String> templates = Arrays.asList("GET /api/users/INTEGER");
        Map<String, Set<String>> statics = new LinkedHashMap<>();
        for (int i = 0; i < 10000; i++) {
            statics.put("GET /api/users/" + i, new HashSet<>());
        }
        Set<String> result = callOptimized(templates, statics, 0);
        Set<String> brute = bruteForceMatch(templates, statics, 0);
        assertEquals(brute, result);
        assertEquals(10000, result.size());
    }

    // ==================== WILDCARD AT EVERY POSITION ====================

    @Test
    public void testWildcardAtFirstPosition() {
        List<String> templates = Arrays.asList("GET /INTEGER/users/list");
        Map<String, Set<String>> statics = toStaticMap("GET /42/users/list");
        assertMatchesBruteForce(templates, statics, 1);
    }

    @Test
    public void testWildcardAtMiddlePosition() {
        List<String> templates = Arrays.asList("GET /api/INTEGER/list");
        Map<String, Set<String>> statics = toStaticMap("GET /api/42/list");
        assertMatchesBruteForce(templates, statics, 1);
    }

    @Test
    public void testWildcardAtLastPosition() {
        List<String> templates = Arrays.asList("GET /api/users/INTEGER");
        Map<String, Set<String>> statics = toStaticMap("GET /api/users/42");
        assertMatchesBruteForce(templates, statics, 1);
    }

    @Test
    public void testAllPositionsWildcard() {
        // Template with all wildcard positions (2 tokens, both INTEGER)
        List<String> templates = Arrays.asList("GET /INTEGER/INTEGER");
        Map<String, Set<String>> statics = toStaticMap(
                "GET /1/2",
                "GET /99/100"
        );
        assertMatchesBruteForce(templates, statics, 2);
    }

    // ==================== HTTP METHODS ====================

    @Test
    public void testAllHttpMethods() {
        List<String> templates = Arrays.asList(
                "GET /api/INTEGER",
                "POST /api/INTEGER",
                "PUT /api/INTEGER",
                "DELETE /api/INTEGER",
                "PATCH /api/INTEGER",
                "HEAD /api/INTEGER",
                "OPTIONS /api/INTEGER"
        );
        Map<String, Set<String>> statics = toStaticMap(
                "GET /api/1",
                "POST /api/2",
                "PUT /api/3",
                "DELETE /api/4",
                "PATCH /api/5",
                "HEAD /api/6",
                "OPTIONS /api/7"
        );
        assertMatchesBruteForce(templates, statics, 7);
    }

    // ==================== DUPLICATE HANDLING ====================

    @Test
    public void testDuplicateTemplates() {
        List<String> templates = Arrays.asList(
                "GET /api/users/INTEGER",
                "GET /api/users/INTEGER"  // duplicate
        );
        Map<String, Set<String>> statics = toStaticMap("GET /api/users/123");
        assertMatchesBruteForce(templates, statics, 1);
    }

    @Test
    public void testDuplicateStaticURLs() {
        // Map keys are unique, so this just tests that the map handles it
        List<String> templates = Arrays.asList("GET /api/users/INTEGER");
        Map<String, Set<String>> statics = new LinkedHashMap<>();
        statics.put("GET /api/users/123", new HashSet<>());
        statics.put("GET /api/users/123", new HashSet<>());  // overwrites in map
        assertMatchesBruteForce(templates, statics, 1);
    }

    // ==================== PERFORMANCE PARITY ====================

    @Test
    public void testLargeScaleParityWithBruteForce() {
        // 500 templates, 5000 statics, mix of matches and non-matches
        List<String> templates = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            templates.add("GET /api/svc" + i + "/INTEGER");
        }

        Map<String, Set<String>> statics = new LinkedHashMap<>();
        for (int i = 0; i < 5000; i++) {
            if (i % 3 == 0) {
                // matches a template
                statics.put("GET /api/svc" + (i % 500) + "/" + i, new HashSet<>());
            } else if (i % 3 == 1) {
                // no match — different prefix
                statics.put("GET /api/other" + i + "/" + i, new HashSet<>());
            } else {
                // no match — different method
                statics.put("POST /api/svc" + (i % 500) + "/" + i, new HashSet<>());
            }
        }

        Set<String> brute = bruteForceMatch(templates, statics, 0);
        Set<String> optimized = callOptimized(templates, statics, 0);
        assertEquals("Large scale parity", brute, optimized);
    }

    // ==================== REGRESSION: TEMPLATE WITH NO WILDCARDS ====================

    @Test
    public void testTemplateWithNoWildcardsExactMatch() {
        // Template that has no parameterized tokens — just a fixed URL
        // This happens when STI URL contains "INTEGER" as literal but createUrlTemplate
        // also recognizes it, so let's use a truly fixed template
        List<String> templates = Arrays.asList("GET /api/health/check");
        Map<String, Set<String>> statics = toStaticMap(
                "GET /api/health/check",    // exact match
                "GET /api/health/other"     // no match
        );
        assertMatchesBruteForce(templates, statics, 1);
    }

    // ==================== CONSISTENCY: ORDERING INDEPENDENCE ====================

    @Test
    public void testResultIndependentOfStaticOrder() {
        List<String> templates = Arrays.asList("GET /api/users/INTEGER");
        Map<String, Set<String>> statics1 = new LinkedHashMap<>();
        statics1.put("GET /api/users/123", new HashSet<>());
        statics1.put("GET /api/users/456", new HashSet<>());
        statics1.put("GET /api/other/789", new HashSet<>());

        Map<String, Set<String>> statics2 = new LinkedHashMap<>();
        statics2.put("GET /api/other/789", new HashSet<>());
        statics2.put("GET /api/users/456", new HashSet<>());
        statics2.put("GET /api/users/123", new HashSet<>());

        Set<String> result1 = callOptimized(templates, statics1, 0);
        Set<String> result2 = callOptimized(templates, statics2, 0);
        assertEquals("Order should not affect result", result1, result2);
    }

    @Test
    public void testResultIndependentOfTemplateOrder() {
        List<String> templates1 = Arrays.asList(
                "GET /api/users/INTEGER",
                "GET /api/orders/INTEGER"
        );
        List<String> templates2 = Arrays.asList(
                "GET /api/orders/INTEGER",
                "GET /api/users/INTEGER"
        );
        Map<String, Set<String>> statics = toStaticMap(
                "GET /api/users/42",
                "GET /api/orders/99"
        );

        Set<String> result1 = callOptimized(templates1, statics, 0);
        Set<String> result2 = callOptimized(templates2, statics, 0);
        assertEquals("Template order should not affect result", result1, result2);
    }
}
