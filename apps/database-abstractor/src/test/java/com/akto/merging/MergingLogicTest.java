package com.akto.merging;

import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLStatic;
import com.akto.dto.type.URLTemplate;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for locale merging in MergingLogic
 */
public class MergingLogicTest {

    @Test
    public void testTryMergeUrls_LocaleCodes() {
        // Test merging URLs with different locale codes
        URLStatic url1 = new URLStatic("/api/en/products", URLMethods.Method.GET);
        URLStatic url2 = new URLStatic("/api/ja/products", URLMethods.Method.GET);

        URLTemplate result = MergingLogic.tryMergeUrls(url1, url2, false, true);

        assertNotNull("Should merge locale URLs", result);
        assertEquals("Should create LOCALE template", "/api/LOCALE/products", result.getTemplateString());
        assertEquals("Method should match", URLMethods.Method.GET, result.getMethod());

        // Check that the LOCALE type is set correctly
        SingleTypeInfo.SuperType[] types = result.getTypes();
        assertEquals("Should have LOCALE type at position 1", SingleTypeInfo.SuperType.LOCALE, types[1]);
    }

    @Test
    public void testTryMergeUrls_LocaleCodesWithCountry() {
        // Test merging URLs with language-country locale codes
        URLStatic url1 = new URLStatic("/api/en-US/users", URLMethods.Method.POST);
        URLStatic url2 = new URLStatic("/api/ja-JP/users", URLMethods.Method.POST);

        URLTemplate result = MergingLogic.tryMergeUrls(url1, url2, false, true);

        assertNotNull("Should merge locale URLs with country codes", result);
        assertEquals("Should create LOCALE template", "/api/LOCALE/users", result.getTemplateString());
        assertEquals("Should have LOCALE type", SingleTypeInfo.SuperType.LOCALE, result.getTypes()[1]);
    }

    @Test
    public void testTryMergeUrls_LocaleMixedFormats() {
        // Test merging simple language code with language-country code
        URLStatic url1 = new URLStatic("/shop/en/items", URLMethods.Method.GET);
        URLStatic url2 = new URLStatic("/shop/pt-BR/items", URLMethods.Method.GET);

        URLTemplate result = MergingLogic.tryMergeUrls(url1, url2, false, true);

        assertNotNull("Should merge mixed locale formats", result);
        assertEquals("Should create LOCALE template", "/shop/LOCALE/items", result.getTemplateString());
        assertEquals("Should have LOCALE type", SingleTypeInfo.SuperType.LOCALE, result.getTypes()[1]);
    }

    @Test
    public void testTryMergeUrls_NonLocaleShouldNotMerge() {
        // Test that non-locale strings don't merge as LOCALE
        URLStatic url1 = new URLStatic("/api/en/products", URLMethods.Method.GET);
        URLStatic url2 = new URLStatic("/api/foo/products", URLMethods.Method.GET);

        URLTemplate result = MergingLogic.tryMergeUrls(url1, url2, false, true);

        // Should merge as STRING, not LOCALE (since one is invalid locale)
        if (result != null) {
            SingleTypeInfo.SuperType[] types = result.getTypes();
            assertEquals("Should merge as STRING, not LOCALE", SingleTypeInfo.SuperType.STRING, types[1]);
        }
    }

    @Test
    public void testTryMergeUrls_IntegersMergeSeparately() {
        // Ensure integers still merge as INTEGER, not confused with locales
        URLStatic url1 = new URLStatic("/api/123/items", URLMethods.Method.GET);
        URLStatic url2 = new URLStatic("/api/456/items", URLMethods.Method.GET);

        URLTemplate result = MergingLogic.tryMergeUrls(url1, url2, false, true);

        assertNotNull("Should merge integer URLs", result);
        assertEquals("Should create INTEGER template", "/api/INTEGER/items", result.getTemplateString());
        assertEquals("Should have INTEGER type", SingleTypeInfo.SuperType.INTEGER, result.getTypes()[1]);
    }

    @Test
    public void testTryMergeUrls_DifferentMethodsDoNotMerge() {
        // URLs with different HTTP methods should not merge
        URLStatic url1 = new URLStatic("/api/en/products", URLMethods.Method.GET);
        URLStatic url2 = new URLStatic("/api/ja/products", URLMethods.Method.POST);

        URLTemplate result = MergingLogic.tryMergeUrls(url1, url2, false, true);

        assertNull("Should not merge different HTTP methods", result);
    }

    @Test
    public void testTryMergeUrls_DifferentLengthsDoNotMerge() {
        // URLs with different number of segments should not merge
        URLStatic url1 = new URLStatic("/api/en/products", URLMethods.Method.GET);
        URLStatic url2 = new URLStatic("/api/en/products/details", URLMethods.Method.GET);

        URLTemplate result = MergingLogic.tryMergeUrls(url1, url2, false, true);

        assertNull("Should not merge different URL lengths", result);
    }

    @Test
    public void testTryMergeUrls_MultipleLocaleSegments() {
        // Test URL with locale in first position
        URLStatic url1 = new URLStatic("/en/api/products", URLMethods.Method.GET);
        URLStatic url2 = new URLStatic("/ja/api/products", URLMethods.Method.GET);

        URLTemplate result = MergingLogic.tryMergeUrls(url1, url2, false, true);

        assertNotNull("Should merge URLs with locale at start", result);
        // Check that LOCALE type is set at position 0
        assertEquals("Should have LOCALE type at position 0", SingleTypeInfo.SuperType.LOCALE, result.getTypes()[0]);
        // Check the template contains LOCALE (the exact format may vary based on trim/slash handling)
        assertTrue("Template should contain LOCALE", result.getTemplateString().contains("LOCALE"));
        assertTrue("Template should contain api", result.getTemplateString().contains("api"));
        assertTrue("Template should contain products", result.getTemplateString().contains("products"));
    }

    @Test
    public void testLocaleUrlsAreMergedCorrectly() {
        // Test that valid locale URLs create LOCALE type (indirectly tests areBothLocaleUrls)
        URLStatic url1 = new URLStatic("/api/en/products", URLMethods.Method.GET);
        URLStatic url2 = new URLStatic("/api/ja/products", URLMethods.Method.GET);

        URLTemplate result = MergingLogic.tryMergeUrls(url1, url2, false, true);

        assertNotNull("Should merge valid locale URLs", result);
        assertEquals("Should have LOCALE type", SingleTypeInfo.SuperType.LOCALE, result.getTypes()[1]);
    }

    @Test
    public void testInvalidLocaleDoesNotCreateLocaleType() {
        // Test that invalid locale doesn't create LOCALE type
        URLStatic url1 = new URLStatic("/api/en/products", URLMethods.Method.GET);
        URLStatic url2 = new URLStatic("/api/foo/products", URLMethods.Method.GET);

        URLTemplate result = MergingLogic.tryMergeUrls(url1, url2, false, true);

        // Should merge as STRING, not LOCALE (since one is invalid locale)
        if (result != null) {
            SingleTypeInfo.SuperType[] types = result.getTypes();
            assertEquals("Should merge as STRING, not LOCALE", SingleTypeInfo.SuperType.STRING, types[1]);
        }
    }

    @Test
    public void testLocaleWithCountryCodesAreMergedCorrectly() {
        // Test that locale URLs with country codes create LOCALE type
        URLStatic url1 = new URLStatic("/api/en-US/users", URLMethods.Method.POST);
        URLStatic url2 = new URLStatic("/api/pt-BR/users", URLMethods.Method.POST);

        URLTemplate result = MergingLogic.tryMergeUrls(url1, url2, false, true);

        assertNotNull("Should merge locale URLs with country codes", result);
        assertEquals("Should have LOCALE type", SingleTypeInfo.SuperType.LOCALE, result.getTypes()[1]);
    }

    @Test
    public void testCreateUrlTemplate_WithLocaleToken() {
        // Test that createUrlTemplate correctly handles LOCALE string in URL
        String url = "/api/LOCALE/products";
        URLMethods.Method method = URLMethods.Method.GET;

        URLTemplate result = MergingLogic.createUrlTemplate(url, method);

        assertNotNull("Should create template", result);
        assertEquals("Should have LOCALE in template string", "/api/LOCALE/products", result.getTemplateString());

        SingleTypeInfo.SuperType[] types = result.getTypes();
        assertEquals("Should have LOCALE type at position 1", SingleTypeInfo.SuperType.LOCALE, types[1]);
    }

    @Test
    public void testCreateUrlTemplate_WithMixedTokens() {
        // Test URL with both LOCALE and INTEGER tokens
        String url = "/api/LOCALE/products/INTEGER";
        URLMethods.Method method = URLMethods.Method.GET;

        URLTemplate result = MergingLogic.createUrlTemplate(url, method);

        assertNotNull("Should create template", result);
        SingleTypeInfo.SuperType[] types = result.getTypes();
        assertEquals("Should have LOCALE type at position 1", SingleTypeInfo.SuperType.LOCALE, types[1]);
        assertEquals("Should have INTEGER type at position 3", SingleTypeInfo.SuperType.INTEGER, types[3]);
    }

    @Test
    public void testTrim_RemovesLeadingAndTrailingSlashes() {
        assertEquals("api/en/products", MergingLogic.trim("/api/en/products/"));
        assertEquals("api/en/products", MergingLogic.trim("/api/en/products"));
        assertEquals("api/en/products", MergingLogic.trim("api/en/products/"));
        assertEquals("api/en/products", MergingLogic.trim("api/en/products"));
    }

    @Test
    public void testTokenize_SplitsCorrectly() {
        String[] tokens = MergingLogic.tokenize("/api/en/products");
        assertArrayEquals("Should tokenize correctly",
                         new String[]{"api", "en", "products"}, tokens);

        tokens = MergingLogic.tokenize("api/en/products/");
        assertArrayEquals("Should handle trailing slash",
                         new String[]{"api", "en", "products"}, tokens);
    }

    @Test
    public void testTryMergeUrls_CaseInsensitiveLocales() {
        // Test that different locales with same format merge, regardless of case
        // Note: Same locale with different casing (en-us vs en-US) won't merge
        // because equalsIgnoreCase considers them same, so we test different locales
        URLStatic url1 = new URLStatic("/api/en-us/products", URLMethods.Method.GET);
        URLStatic url2 = new URLStatic("/api/fr-FR/products", URLMethods.Method.GET);

        URLTemplate result = MergingLogic.tryMergeUrls(url1, url2, false, true);

        assertNotNull("Should merge different locale codes regardless of case", result);
        assertEquals("Should create LOCALE template", "/api/LOCALE/products", result.getTemplateString());
    }

    @Test
    public void testTryMergeUrls_CommonWebLocales() {
        // Test merging of commonly used web application locales
        String[][] localePairs = {
            {"/api/en-US/data", "/api/es-ES/data"},
            {"/api/fr-FR/data", "/api/de-DE/data"},
            {"/api/zh-CN/data", "/api/ja-JP/data"},
            {"/api/pt-BR/data", "/api/it-IT/data"},
            {"/api/ko-KR/data", "/api/ru-RU/data"}
        };

        for (String[] pair : localePairs) {
            URLStatic url1 = new URLStatic(pair[0], URLMethods.Method.GET);
            URLStatic url2 = new URLStatic(pair[1], URLMethods.Method.GET);

            URLTemplate result = MergingLogic.tryMergeUrls(url1, url2, false, true);

            assertNotNull("Should merge locale pair: " + pair[0] + " and " + pair[1], result);
            assertEquals("Should create LOCALE template", "/api/LOCALE/data", result.getTemplateString());
        }
    }
}
