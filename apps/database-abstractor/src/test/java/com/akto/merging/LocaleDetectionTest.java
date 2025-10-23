package com.akto.merging;

import com.akto.runtime.RuntimeUtil;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for locale code detection in URL merging
 */
public class LocaleDetectionTest {

    @Test
    public void testValidLocales() {
        // Test simple language codes
        assertTrue("en should be valid", RuntimeUtil.isValidLocaleToken("en"));
        assertTrue("ja should be valid", RuntimeUtil.isValidLocaleToken("ja"));
        assertTrue("de should be valid", RuntimeUtil.isValidLocaleToken("de"));
        assertTrue("fr should be valid", RuntimeUtil.isValidLocaleToken("fr"));
        assertTrue("es should be valid", RuntimeUtil.isValidLocaleToken("es"));
        assertTrue("pt should be valid", RuntimeUtil.isValidLocaleToken("pt"));
        assertTrue("zh should be valid", RuntimeUtil.isValidLocaleToken("zh"));
        assertTrue("ko should be valid", RuntimeUtil.isValidLocaleToken("ko"));
    }

    @Test
    public void testValidLocalesWithCountry() {
        // Test language + country codes (case variations)
        assertTrue("en-US should be valid", RuntimeUtil.isValidLocaleToken("en-US"));
        assertTrue("en-us should be valid", RuntimeUtil.isValidLocaleToken("en-us"));
        assertTrue("ja-JP should be valid", RuntimeUtil.isValidLocaleToken("ja-JP"));
        assertTrue("pt-BR should be valid", RuntimeUtil.isValidLocaleToken("pt-BR"));
        assertTrue("pt-br should be valid", RuntimeUtil.isValidLocaleToken("pt-br"));
        assertTrue("fr-FR should be valid", RuntimeUtil.isValidLocaleToken("fr-FR"));
        assertTrue("zh-CN should be valid", RuntimeUtil.isValidLocaleToken("zh-CN"));
        assertTrue("ko-KR should be valid", RuntimeUtil.isValidLocaleToken("ko-KR"));
        assertTrue("de-DE should be valid", RuntimeUtil.isValidLocaleToken("de-DE"));
        assertTrue("es-ES should be valid", RuntimeUtil.isValidLocaleToken("es-ES"));
    }

    @Test
    public void testInvalidLocales() {
        // Test invalid language codes
        assertFalse("foo should be invalid", RuntimeUtil.isValidLocaleToken("foo"));
        assertFalse("bar should be invalid", RuntimeUtil.isValidLocaleToken("bar"));
        assertFalse("xxx should be invalid", RuntimeUtil.isValidLocaleToken("xxx"));
        assertFalse("abc should be invalid", RuntimeUtil.isValidLocaleToken("abc"));

        // Test invalid country codes
        assertFalse("en-XX should be invalid", RuntimeUtil.isValidLocaleToken("en-XX"));
        assertFalse("zz-ZZ should be invalid", RuntimeUtil.isValidLocaleToken("zz-ZZ"));

        // Test invalid formats
        assertFalse("en-USA should be invalid", RuntimeUtil.isValidLocaleToken("en-USA"));
        assertFalse("123 should be invalid", RuntimeUtil.isValidLocaleToken("123"));

        // Test edge cases
        assertFalse("empty string should be invalid", RuntimeUtil.isValidLocaleToken(""));
        assertFalse("null should be invalid", RuntimeUtil.isValidLocaleToken(null));
        assertFalse("en- should be invalid", RuntimeUtil.isValidLocaleToken("en-"));
        assertFalse("-US should be invalid", RuntimeUtil.isValidLocaleToken("-US"));
        assertFalse("- should be invalid", RuntimeUtil.isValidLocaleToken("-"));
    }

    @Test
    public void testMixedValidAndInvalid() {
        // Valid locales should not be confused with similar invalid ones
        assertTrue("Valid: en", RuntimeUtil.isValidLocaleToken("en"));
        assertFalse("Invalid: e", RuntimeUtil.isValidLocaleToken("e"));

        assertTrue("Valid: en-US", RuntimeUtil.isValidLocaleToken("en-US"));
        assertFalse("Invalid: en-U", RuntimeUtil.isValidLocaleToken("en-U"));
        assertFalse("Invalid: e-US", RuntimeUtil.isValidLocaleToken("e-US"));
    }

    @Test
    public void testCommonLocales() {
        // Test most commonly used locales in web applications
        String[] commonLocales = {
            "en", "en-US", "en-GB",
            "es", "es-ES", "es-MX",
            "fr", "fr-FR", "fr-CA",
            "de", "de-DE",
            "it", "it-IT",
            "pt", "pt-BR", "pt-PT",
            "ja", "ja-JP",
            "zh", "zh-CN", "zh-TW",
            "ko", "ko-KR",
            "ru", "ru-RU",
            "ar", "ar-SA",
            "hi", "hi-IN"
        };

        for (String locale : commonLocales) {
            assertTrue("Common locale should be valid: " + locale,
                      RuntimeUtil.isValidLocaleToken(locale));
        }
    }
}
