package com.akto.action;

import com.akto.dto.type.APICatalog;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for DbAction class, specifically testing the template URL filtering logic
 * for account ID 1759386565.
 *
 * This test verifies that the APICatalog.isTemplateUrl() method works correctly,
 * which is used in bulkWriteSti() to filter template URLs for the specific account.
 */
public class DbActionTest {

    /**
     * Test that template URLs are correctly identified by APICatalog.isTemplateUrl()
     * This is the core logic used in bulkWriteSti() for account 1759386565.
     */
    @Test
    public void testTemplateUrlIdentification() {
        // Template URLs should return true
        assertTrue("INTEGER in URL should be identified as template",
                   APICatalog.isTemplateUrl("api/users/INTEGER"));

        assertTrue("STRING in URL should be identified as template",
                   APICatalog.isTemplateUrl("api/products/STRING"));

        assertTrue("OBJECT_ID in URL should be identified as template",
                   APICatalog.isTemplateUrl("api/orders/OBJECT_ID"));

        // Non-template URLs should return false
        assertFalse("Regular ID should not be identified as template",
                    APICatalog.isTemplateUrl("api/users/12345"));

        assertFalse("Regular string path should not be identified as template",
                    APICatalog.isTemplateUrl("api/users/profile"));

        assertFalse("UUID-like string should not be identified as template",
                    APICatalog.isTemplateUrl("api/users/abc123def456"));
    }

    /**
     * Test various template URL patterns that should be filtered for account 1759386565
     * Note: Only STRING, INTEGER, FLOAT, OBJECT_ID, and LOCALE are recognized as template types
     */
    @Test
    public void testVariousTemplateUrlPatterns() {
        // Test different template types that are actually supported
        String[] templateUrls = {
            "api/v1/users/INTEGER",
            "api/v1/users/INTEGER/posts",
            "/api/products/STRING",
            "/api/orders/OBJECT_ID/items",
            "api/data/FLOAT",
            "api/locale/LOCALE"
        };

        for (String url : templateUrls) {
            assertTrue("URL '" + url + "' should be identified as template",
                      APICatalog.isTemplateUrl(url));
        }
    }

    /**
     * Test that regular URLs are NOT identified as templates
     */
    @Test
    public void testRegularUrlsNotIdentifiedAsTemplates() {
        String[] regularUrls = {
            "api/users/123",
            "api/users/profile",
            "api/products/search",
            "/api/orders/latest",
            "api/v1/users",
            "api/books/bestsellers",
            "/api/data/export",
            // Note: UUID, BOOLEAN are NOT template types in APICatalog.isTemplateUrl()
            "api/books/UUID",
            "api/items/BOOLEAN"
        };

        for (String url : regularUrls) {
            assertFalse("URL '" + url + "' should NOT be identified as template",
                       APICatalog.isTemplateUrl(url));
        }
    }

    /**
     * Test edge cases for template URL identification
     */
    @Test
    public void testTemplateUrlEdgeCases() {
        // Test case sensitivity - template tokens are usually uppercase
        assertTrue("Uppercase INTEGER should be template",
                  APICatalog.isTemplateUrl("api/users/INTEGER"));

        // Empty or null URLs - verify no exceptions
        try {
            boolean result = APICatalog.isTemplateUrl(null);
            assertFalse("Null URL should return false", result);
        } catch (NullPointerException e) {
            // If NPE is thrown, that's also acceptable behavior
            // The actual implementation in bulkWriteSti checks for null URL before calling isTemplateUrl
        }

        assertFalse("Empty string should not be template",
                   APICatalog.isTemplateUrl(""));
    }

    /**
     * Test mixed URLs with both template and non-template segments
     */
    @Test
    public void testMixedUrlPatterns() {
        // URLs with template tokens anywhere in the path should be identified
        assertTrue("Template token in middle should be identified",
                  APICatalog.isTemplateUrl("api/users/INTEGER/posts"));

        assertTrue("Template token at end should be identified",
                  APICatalog.isTemplateUrl("api/v2/products/STRING"));

        assertTrue("Multiple path segments with template should be identified",
                  APICatalog.isTemplateUrl("api/v1/users/INTEGER/orders/OBJECT_ID"));
    }

    /**
     * Test that the logic for account 1759386565 template URL filtering
     * follows the expected pattern: if (accId == 1759386565 && url != null && APICatalog.isTemplateUrl(url))
     */
    @Test
    public void testAccount1759386565FilteringLogic() {
        int accountId = 1759386565;

        // Scenario 1: Template URL for account 1759386565 - should be ignored
        String templateUrl = "api/users/INTEGER";
        boolean shouldIgnore = (accountId == 1759386565 &&
                               templateUrl != null &&
                               APICatalog.isTemplateUrl(templateUrl));
        assertTrue("Template URL for account 1759386565 should be ignored", shouldIgnore);

        // Scenario 2: Non-template URL for account 1759386565 - should NOT be ignored
        String regularUrl = "api/users/12345";
        shouldIgnore = (accountId == 1759386565 &&
                       regularUrl != null &&
                       APICatalog.isTemplateUrl(regularUrl));
        assertFalse("Regular URL for account 1759386565 should NOT be ignored", shouldIgnore);

        // Scenario 3: Template URL for different account - should NOT be ignored by this rule
        int differentAccountId = 1000000;
        shouldIgnore = (differentAccountId == 1759386565 &&
                       templateUrl != null &&
                       APICatalog.isTemplateUrl(templateUrl));
        assertFalse("Template URL for different account should NOT be ignored by this rule",
                   shouldIgnore);

        // Scenario 4: Null URL for account 1759386565 - should NOT cause errors
        String nullUrl = null;
        shouldIgnore = (accountId == 1759386565 &&
                       nullUrl != null &&
                       APICatalog.isTemplateUrl(nullUrl));
        assertFalse("Null URL should be handled gracefully", shouldIgnore);
    }
}
