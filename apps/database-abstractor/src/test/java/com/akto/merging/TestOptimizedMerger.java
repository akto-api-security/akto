package com.akto.merging;

import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLTemplate;
import com.akto.util.filter.DictionaryFilter;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class TestOptimizedMerger {

    @BeforeClass
    public static void initDictionary() {
        DictionaryFilter.readDictionaryBinary();
    }

    // ---- Token classification tests ----

    @Test
    public void testClassifyNumeric() {
        assertEquals(OptimizedMerger.TokenTag.INT,
                OptimizedMerger.classifyToken("123", true, true));
        assertEquals(OptimizedMerger.TokenTag.INT,
                OptimizedMerger.classifyToken("-5", true, true));
        assertEquals(OptimizedMerger.TokenTag.INT,
                OptimizedMerger.classifyToken("0", true, true));
    }

    @Test
    public void testClassifyUuid() {
        assertEquals(OptimizedMerger.TokenTag.UUID,
                OptimizedMerger.classifyToken("550e8400-e29b-41d4-a716-446655440000", true, true));
    }

    @Test
    public void testClassifyUuidBlockedWithoutStringMerging() {
        // UUID requires allowStringMerging
        assertNull(OptimizedMerger.classifyToken("550e8400-e29b-41d4-a716-446655440000", false, true));
    }

    @Test
    public void testClassifyVersion() {
        assertEquals(OptimizedMerger.TokenTag.VER,
                OptimizedMerger.classifyToken("v2", true, true));
        // Blocked without allowMergingOnVersions
        assertNotEquals(OptimizedMerger.TokenTag.VER,
                OptimizedMerger.classifyToken("v2", true, false));
    }

    @Test
    public void testClassifyLocale() {
        assertEquals(OptimizedMerger.TokenTag.LOC,
                OptimizedMerger.classifyToken("en", true, true));
        assertEquals(OptimizedMerger.TokenTag.LOC,
                OptimizedMerger.classifyToken("en-US", true, true));
    }

    @Test
    public void testClassifyAlphanumeric() {
        // isAlphanumericString: length ≥ 6, digits ≥ 3, letters ≥ 1
        assertEquals(OptimizedMerger.TokenTag.ALNUM,
                OptimizedMerger.classifyToken("abc123def", true, true));
    }

    @Test
    public void testClassifyAlnumBlockedWithoutStringMerging() {
        assertNull(OptimizedMerger.classifyToken("abc123def", false, true));
    }

    @Test
    public void testClassifyEnglishWordReturnsNull() {
        // English words are always literal (null tag)
        assertNull(OptimizedMerger.classifyToken("users", true, true));
        assertNull(OptimizedMerger.classifyToken("profile", true, true));
    }

    // ---- Tier 1 signature tests ----

    @Test
    public void testTier1SignatureSameForNumericDiffs() {
        Set<String> urls = new LinkedHashSet<>(Arrays.asList(
                "GET /api/users/123/profile",
                "GET /api/users/456/profile"
        ));
        List<OptimizedMerger.ParsedUrl> parsed = OptimizedMerger.parseUrls(urls, true, true);
        assertEquals(2, parsed.size());

        String sig1 = OptimizedMerger.buildTier1Signature(parsed.get(0));
        String sig2 = OptimizedMerger.buildTier1Signature(parsed.get(1));
        assertEquals("Same sig for numeric diff", sig1, sig2);
    }

    @Test
    public void testTier1SignatureDiffersForLiteralDiffs() {
        Set<String> urls = new LinkedHashSet<>(Arrays.asList(
                "GET /api/users/123/profile",
                "GET /api/orders/123/profile"
        ));
        List<OptimizedMerger.ParsedUrl> parsed = OptimizedMerger.parseUrls(urls, true, true);
        String sig1 = OptimizedMerger.buildTier1Signature(parsed.get(0));
        String sig2 = OptimizedMerger.buildTier1Signature(parsed.get(1));
        assertNotEquals("Different sig for literal diff", sig1, sig2);
    }

    @Test
    public void testTier1SignatureKeepsOpaqueAsLiteral() {
        Set<String> urls = new LinkedHashSet<>(Arrays.asList(
                "GET /api/users/xK9m/profile",
                "GET /api/users/pL3n/profile"
        ));
        List<OptimizedMerger.ParsedUrl> parsed = OptimizedMerger.parseUrls(urls, true, true);
        String sig1 = OptimizedMerger.buildTier1Signature(parsed.get(0));
        String sig2 = OptimizedMerger.buildTier1Signature(parsed.get(1));
        // OPAQUE tokens stay literal → different sigs
        assertNotEquals("OPAQUE stays literal in Tier 1", sig1, sig2);
    }

    // ---- Tier 1 template building ----

    @Test
    public void testBuildTier1TemplateNumeric() {
        Set<String> urls = Collections.singleton("GET /api/users/123/profile");
        List<OptimizedMerger.ParsedUrl> parsed = OptimizedMerger.parseUrls(urls, true, true);
        URLTemplate template = OptimizedMerger.buildTier1Template(parsed.get(0));
        assertEquals("/api/users/INTEGER/profile", template.getTemplateString());
    }

    @Test
    public void testBuildTier1TemplateMultipleNumeric() {
        Set<String> urls = Collections.singleton("GET /api/123/users/456");
        List<OptimizedMerger.ParsedUrl> parsed = OptimizedMerger.parseUrls(urls, true, true);
        URLTemplate template = OptimizedMerger.buildTier1Template(parsed.get(0));
        assertEquals("/api/INTEGER/users/INTEGER", template.getTemplateString());
    }

    @Test
    public void testBuildTier1TemplateUuid() {
        Set<String> urls = Collections.singleton(
                "GET /api/users/550e8400-e29b-41d4-a716-446655440000/profile");
        List<OptimizedMerger.ParsedUrl> parsed = OptimizedMerger.parseUrls(urls, true, true);
        URLTemplate template = OptimizedMerger.buildTier1Template(parsed.get(0));
        assertEquals("/api/users/STRING/profile", template.getTemplateString());
    }

    // ---- Tier 2 key tests ----

    @Test
    public void testTier2KeyGroupsAlnumDiffs() {
        Set<String> urls = new LinkedHashSet<>(Arrays.asList(
                "GET /api/users/abc123def/profile",
                "GET /api/users/xyz789ghi/profile"
        ));
        List<OptimizedMerger.ParsedUrl> parsed = OptimizedMerger.parseUrls(urls, true, true);
        // pos 2 is ALNUM
        String key1 = OptimizedMerger.buildTier2Key(parsed.get(0), 2);
        String key2 = OptimizedMerger.buildTier2Key(parsed.get(1), 2);
        assertNotNull(key1);
        assertEquals("Same Tier 2 key for ALNUM diff at same pos", key1, key2);
    }

    @Test
    public void testTier2KeyLiteralAtNonSkippedPositions() {
        Set<String> urls = new LinkedHashSet<>(Arrays.asList(
                "GET /api/users/abc123def/profile",
                "GET /api/orders/abc123def/profile"
        ));
        List<OptimizedMerger.ParsedUrl> parsed = OptimizedMerger.parseUrls(urls, true, true);
        // Skip pos 2 (ALNUM) — but pos 1 differs ("users" vs "orders")
        String key1 = OptimizedMerger.buildTier2Key(parsed.get(0), 2);
        String key2 = OptimizedMerger.buildTier2Key(parsed.get(1), 2);
        assertNotEquals("Different Tier 2 key when literals differ", key1, key2);
    }

    @Test
    public void testTier2KeyAbstractsFreeMergePositions() {
        // URLs with different INT at pos 2 AND different ALNUM at pos 3
        Set<String> urls = new LinkedHashSet<>(Arrays.asList(
                "GET /api/123/abc123def/profile",
                "GET /api/456/xyz789ghi/profile"
        ));
        List<OptimizedMerger.ParsedUrl> parsed = OptimizedMerger.parseUrls(urls, true, true);
        // Skip pos 2 (ALNUM) — pos 1 is INT (free merge, tagged in key)
        String key1 = OptimizedMerger.buildTier2Key(parsed.get(0), 2);
        String key2 = OptimizedMerger.buildTier2Key(parsed.get(1), 2);
        assertEquals("INT positions abstracted in Tier 2 key", key1, key2);
    }

    @Test
    public void testTier2KeyRejectsEnglishWordSkip() {
        Set<String> urls = Collections.singleton("GET /api/users/profile");
        List<OptimizedMerger.ParsedUrl> parsed = OptimizedMerger.parseUrls(urls, true, true);
        // "users" is English → tag=null → can't skip
        assertNull(OptimizedMerger.buildTier2Key(parsed.get(0), 1));
    }

    // ---- End-to-end merging tests ----

    @Test
    public void testMergeNumericUrls() {
        Set<String> urls = new HashSet<>(Arrays.asList(
                "GET /api/users/1",
                "GET /api/users/2",
                "GET /api/users/3"
        ));
        MergingLogic.ApiMergerResult result = OptimizedMerger.mergeStaticUrls(
                urls, true, true, 0);

        assertEquals("Should create 1 template", 1, result.templateToStaticURLs.size());
        URLTemplate template = result.templateToStaticURLs.keySet().iterator().next();
        assertEquals("/api/users/INTEGER", template.getTemplateString());
        assertEquals(3, result.templateToStaticURLs.get(template).size());
    }

    @Test
    public void testMergeUuidUrls() {
        Set<String> urls = new HashSet<>(Arrays.asList(
                "GET /api/users/550e8400-e29b-41d4-a716-446655440000/profile",
                "GET /api/users/6ba7b810-9dad-11d1-80b4-00c04fd430c8/profile"
        ));
        MergingLogic.ApiMergerResult result = OptimizedMerger.mergeStaticUrls(
                urls, true, true, 0);

        assertEquals(1, result.templateToStaticURLs.size());
        URLTemplate template = result.templateToStaticURLs.keySet().iterator().next();
        assertEquals("/api/users/STRING/profile", template.getTemplateString());
    }

    @Test
    public void testMergeMultipleIntegerPositions() {
        Set<String> urls = new HashSet<>(Arrays.asList(
                "GET /api/123/orders/456",
                "GET /api/789/orders/12"
        ));
        MergingLogic.ApiMergerResult result = OptimizedMerger.mergeStaticUrls(
                urls, true, true, 0);

        assertEquals(1, result.templateToStaticURLs.size());
        URLTemplate template = result.templateToStaticURLs.keySet().iterator().next();
        assertEquals("/api/INTEGER/orders/INTEGER", template.getTemplateString());
    }

    @Test
    public void testMergeAlphanumericViaTier2() {
        // ALNUM tokens → Tier 1 keeps literal → Tier 2 groups them
        Set<String> urls = new HashSet<>(Arrays.asList(
                "GET /api/users/abc123def/profile",
                "GET /api/users/xyz789ghi/profile"
        ));
        MergingLogic.ApiMergerResult result = OptimizedMerger.mergeStaticUrls(
                urls, true, true, 0);

        assertEquals(1, result.templateToStaticURLs.size());
        URLTemplate template = result.templateToStaticURLs.keySet().iterator().next();
        assertEquals("/api/users/STRING/profile", template.getTemplateString());
    }

    @Test
    public void testMergeIntPlusAlnumViaTier2() {
        // INT at pos 1, ALNUM at pos 3 → Tier 2 skips pos 3, tags pos 1
        Set<String> urls = new HashSet<>(Arrays.asList(
                "GET /api/123/users/abc123def",
                "GET /api/456/users/xyz789ghi"
        ));
        MergingLogic.ApiMergerResult result = OptimizedMerger.mergeStaticUrls(
                urls, true, true, 0);

        assertEquals(1, result.templateToStaticURLs.size());
        URLTemplate template = result.templateToStaticURLs.keySet().iterator().next();
        assertEquals("/api/INTEGER/users/STRING", template.getTemplateString());
    }

    @Test
    public void testNoMergeDifferentEnglishWords() {
        Set<String> urls = new HashSet<>(Arrays.asList(
                "GET /api/users/profile",
                "GET /api/orders/profile"
        ));
        MergingLogic.ApiMergerResult result = OptimizedMerger.mergeStaticUrls(
                urls, true, true, 0);

        // "users" and "orders" are English words → can't merge at that position
        assertTrue("Should not merge different English words",
                result.templateToStaticURLs.isEmpty());
    }

    @Test
    public void testNoMergeWhenStringMergingDisabled() {
        Set<String> urls = new HashSet<>(Arrays.asList(
                "GET /api/users/abc123def/profile",
                "GET /api/users/xyz789ghi/profile"
        ));
        MergingLogic.ApiMergerResult result = OptimizedMerger.mergeStaticUrls(
                urls, false, true, 0);

        // ALNUM requires allowStringMerging → no merge
        assertTrue("Should not merge ALNUM without allowStringMerging",
                result.templateToStaticURLs.isEmpty());
    }

    @Test
    public void testNumericMergeStillWorksWithoutStringMerging() {
        Set<String> urls = new HashSet<>(Arrays.asList(
                "GET /api/users/123",
                "GET /api/users/456"
        ));
        MergingLogic.ApiMergerResult result = OptimizedMerger.mergeStaticUrls(
                urls, false, true, 0);

        assertEquals("INT merge works without allowStringMerging",
                1, result.templateToStaticURLs.size());
    }

    @Test
    public void testOpaqueSkippedConservatively() {
        // Short opaque tokens (not ALNUM: length < 6 or digits < 3)
        Set<String> urls = new HashSet<>(Arrays.asList(
                "GET /api/users/ab/profile",
                "GET /api/users/cd/profile"
        ));
        MergingLogic.ApiMergerResult result = OptimizedMerger.mergeStaticUrls(
                urls, true, true, 0);

        // "ab" and "cd" are OPAQUE (too short for ALNUM) → skipped conservatively
        assertTrue("OPAQUE merge skipped (needs body match)",
                result.templateToStaticURLs.isEmpty());
    }

    @Test
    public void testSingleTokenUrlsIgnored() {
        Set<String> urls = new HashSet<>(Arrays.asList(
                "GET /123",
                "GET /456"
        ));
        MergingLogic.ApiMergerResult result = OptimizedMerger.mergeStaticUrls(
                urls, true, true, 0);

        // Token count = 1 → skipped
        assertTrue(result.templateToStaticURLs.isEmpty());
    }

    @Test
    public void testMultipleGroupsDifferentTokenCounts() {
        Set<String> urls = new HashSet<>(Arrays.asList(
                "GET /api/users/123",
                "GET /api/users/456",
                "GET /api/users/123/posts/789",
                "GET /api/users/456/posts/12"
        ));
        MergingLogic.ApiMergerResult result = OptimizedMerger.mergeStaticUrls(
                urls, true, true, 0);

        assertEquals("Should create 2 templates (different token counts)",
                2, result.templateToStaticURLs.size());
    }

    @Test
    public void testEmptyInput() {
        MergingLogic.ApiMergerResult result = OptimizedMerger.mergeStaticUrls(
                new HashSet<>(), true, true, 0);
        assertTrue(result.templateToStaticURLs.isEmpty());
    }

    @Test
    public void testSingleUrl() {
        Set<String> urls = Collections.singleton("GET /api/users/123");
        MergingLogic.ApiMergerResult result = OptimizedMerger.mergeStaticUrls(
                urls, true, true, 0);
        assertTrue(result.templateToStaticURLs.isEmpty());
    }

    @Test
    public void testDifferentMethodsNotMerged() {
        Set<String> urls = new HashSet<>(Arrays.asList(
                "GET /api/users/123",
                "POST /api/users/456"
        ));
        MergingLogic.ApiMergerResult result = OptimizedMerger.mergeStaticUrls(
                urls, true, true, 0);
        assertTrue("Different methods should not merge",
                result.templateToStaticURLs.isEmpty());
    }

    @Test
    public void testTwoAlnumPositionsNotMerged() {
        // Two ALNUM diffs → templatizedStrTokens=2 → should not merge
        // Tier 2 keeps non-skipped ALNUM as literal → different values → different keys
        Set<String> urls = new HashSet<>(Arrays.asList(
                "GET /api/abc123def/users/xyz789ghi",
                "GET /api/qwe456rty/users/asd012fgh"
        ));
        MergingLogic.ApiMergerResult result = OptimizedMerger.mergeStaticUrls(
                urls, true, true, 0);

        assertTrue("Two ALNUM positions should not merge (templatizedStrTokens would be 2)",
                result.templateToStaticURLs.isEmpty());
    }

    @Test
    public void testMixedIntAndUuidMerge() {
        Set<String> urls = new HashSet<>(Arrays.asList(
                "GET /api/123/users/550e8400-e29b-41d4-a716-446655440000",
                "GET /api/456/users/6ba7b810-9dad-11d1-80b4-00c04fd430c8"
        ));
        MergingLogic.ApiMergerResult result = OptimizedMerger.mergeStaticUrls(
                urls, true, true, 0);

        assertEquals(1, result.templateToStaticURLs.size());
        URLTemplate template = result.templateToStaticURLs.keySet().iterator().next();
        assertEquals("/api/INTEGER/users/STRING", template.getTemplateString());
    }

    @Test
    public void testInvalidUrlsSkipped() {
        Set<String> urls = new HashSet<>(Arrays.asList(
                "GET /api/users/123",
                "GET /api/users/456",
                "",
                "NOSPACE",
                null,
                "GET /api//double//slash"
        ));
        // nulls in HashSet will throw NPE, so use explicit set
        Set<String> urlSet = new LinkedHashSet<>();
        urlSet.add("GET /api/users/123");
        urlSet.add("GET /api/users/456");
        urlSet.add("");
        urlSet.add("NOSPACE");
        urlSet.add("GET /api//double//slash");

        MergingLogic.ApiMergerResult result = OptimizedMerger.mergeStaticUrls(
                urlSet, true, true, 0);

        assertEquals(1, result.templateToStaticURLs.size());
        assertEquals(2, result.templateToStaticURLs.values().iterator().next().size());
    }
}
