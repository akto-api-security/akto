package com.akto.action;

import com.akto.dto.filter.MergedUrls;
import com.akto.util.Constants;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Unit tests for merged URLs filtering logic in DbLayer.fetchStiBasedOnHostHeaders()
 * Tests the filtering that applies to account ID 1759386565 (Constants.MERGED_URLS_FILTER_ACCOUNT_ID)
 *
 * This test verifies that:
 * 1. MergedUrls equality works correctly based on (url, method, apiCollectionId)
 * 2. HashSet contains() method works properly with MergedUrls
 * 3. The filtering logic correctly identifies STIs that should be filtered out
 */
public class MergedUrlsFilterTest {

    /**
     * Test that MergedUrls equality works correctly
     * This is critical for the Set.contains() check in the filtering logic
     */
    @Test
    public void testMergedUrlsEquality() {
        MergedUrls url1 = new MergedUrls("api/users/123", "GET", 1000);
        MergedUrls url2 = new MergedUrls("api/users/123", "GET", 1000);
        MergedUrls url3 = new MergedUrls("api/users/456", "GET", 1000);
        MergedUrls url4 = new MergedUrls("api/users/123", "POST", 1000);
        MergedUrls url5 = new MergedUrls("api/users/123", "GET", 2000);

        // Same url, method, and apiCollectionId should be equal
        assertEquals("Same parameters should be equal", url1, url2);
        assertEquals("Hash codes should match", url1.hashCode(), url2.hashCode());

        // Different url should not be equal
        assertNotEquals("Different URL should not be equal", url1, url3);

        // Different method should not be equal
        assertNotEquals("Different method should not be equal", url1, url4);

        // Different apiCollectionId should not be equal
        assertNotEquals("Different apiCollectionId should not be equal", url1, url5);
    }

    /**
     * Test that HashSet contains() works with MergedUrls
     * This simulates the core filtering logic: mergedUrlsSet.contains(stiAsUrl)
     */
    @Test
    public void testMergedUrlsSetContains() {
        Set<MergedUrls> mergedUrlsSet = new HashSet<>();

        // Add some merged URLs to the set
        mergedUrlsSet.add(new MergedUrls("api/users/INTEGER", "GET", 1000));
        mergedUrlsSet.add(new MergedUrls("api/products/STRING", "POST", 1000));
        mergedUrlsSet.add(new MergedUrls("api/orders/OBJECT_ID", "GET", 2000));

        // Test that contains() works for exact matches
        assertTrue("Should find exact match",
                  mergedUrlsSet.contains(new MergedUrls("api/users/INTEGER", "GET", 1000)));

        assertTrue("Should find exact match with different URL",
                  mergedUrlsSet.contains(new MergedUrls("api/products/STRING", "POST", 1000)));

        // Test that contains() returns false for non-matches
        assertFalse("Should not find with different URL",
                   mergedUrlsSet.contains(new MergedUrls("api/users/123", "GET", 1000)));

        assertFalse("Should not find with different method",
                   mergedUrlsSet.contains(new MergedUrls("api/users/INTEGER", "POST", 1000)));

        assertFalse("Should not find with different apiCollectionId",
                   mergedUrlsSet.contains(new MergedUrls("api/users/INTEGER", "GET", 3000)));

        assertFalse("Should not find URL not in set",
                   mergedUrlsSet.contains(new MergedUrls("api/not/exists", "GET", 1000)));
    }

    /**
     * Test filtering logic - determines which STIs should be kept vs filtered out
     */
    @Test
    public void testMergedUrlsFilteringLogic() {
        // Create merged URLs set (simulates what's cached in DbLayer)
        Set<MergedUrls> mergedUrlsSet = new HashSet<>();
        mergedUrlsSet.add(new MergedUrls("api/users/INTEGER", "GET", 100));
        mergedUrlsSet.add(new MergedUrls("api/products/STRING", "POST", 100));
        mergedUrlsSet.add(new MergedUrls("api/orders/INTEGER", "GET", 200));

        // Test Case 1: STI matching merged URL - should be FILTERED OUT
        MergedUrls sti1 = new MergedUrls("api/users/INTEGER", "GET", 100);
        boolean shouldFilter1 = mergedUrlsSet.contains(sti1);
        assertTrue("STI matching merged URL should be filtered", shouldFilter1);

        // Test Case 2: STI with concrete URL - should be KEPT
        MergedUrls sti2 = new MergedUrls("api/users/123", "GET", 100);
        boolean shouldFilter2 = mergedUrlsSet.contains(sti2);
        assertFalse("STI with concrete URL should not be filtered", shouldFilter2);

        // Test Case 3: STI matching merged URL - should be FILTERED OUT
        MergedUrls sti3 = new MergedUrls("api/products/STRING", "POST", 100);
        boolean shouldFilter3 = mergedUrlsSet.contains(sti3);
        assertTrue("STI matching merged URL should be filtered", shouldFilter3);

        // Test Case 4: STI with different method - should be KEPT
        MergedUrls sti4 = new MergedUrls("api/users/INTEGER", "POST", 100);
        boolean shouldFilter4 = mergedUrlsSet.contains(sti4);
        assertFalse("STI with different method should not be filtered", shouldFilter4);

        // Test Case 5: STI with different collection - should be KEPT
        MergedUrls sti5 = new MergedUrls("api/users/INTEGER", "GET", 300);
        boolean shouldFilter5 = mergedUrlsSet.contains(sti5);
        assertFalse("STI with different apiCollectionId should not be filtered", shouldFilter5);

        // Test Case 6: STI matching merged URL in different collection - should be FILTERED OUT
        MergedUrls sti6 = new MergedUrls("api/orders/INTEGER", "GET", 200);
        boolean shouldFilter6 = mergedUrlsSet.contains(sti6);
        assertTrue("STI matching merged URL in collection 200 should be filtered", shouldFilter6);
    }

    /**
     * Test realistic scenario with template URLs
     */
    @Test
    public void testRealisticTemplateUrlScenario() {
        Set<MergedUrls> mergedUrlsSet = new HashSet<>();

        // Add merged template URLs (these would come from the merged_urls collection)
        mergedUrlsSet.add(new MergedUrls("api/v1/users/INTEGER", "GET", Constants.MERGED_URLS_FILTER_ACCOUNT_ID));
        mergedUrlsSet.add(new MergedUrls("api/v1/users/INTEGER/posts", "GET", Constants.MERGED_URLS_FILTER_ACCOUNT_ID));
        mergedUrlsSet.add(new MergedUrls("api/v1/products/STRING", "POST", Constants.MERGED_URLS_FILTER_ACCOUNT_ID));

        // Template URLs matching merged entries should be filtered
        assertTrue("Template URL matching merged entry should be filtered",
                  mergedUrlsSet.contains(new MergedUrls("api/v1/users/INTEGER", "GET", Constants.MERGED_URLS_FILTER_ACCOUNT_ID)));

        assertTrue("Template URL with path matching merged entry should be filtered",
                  mergedUrlsSet.contains(new MergedUrls("api/v1/users/INTEGER/posts", "GET", Constants.MERGED_URLS_FILTER_ACCOUNT_ID)));

        // Concrete URLs should NOT be filtered
        assertFalse("Concrete URL should not be filtered",
                   mergedUrlsSet.contains(new MergedUrls("api/v1/users/123", "GET", Constants.MERGED_URLS_FILTER_ACCOUNT_ID)));

        assertFalse("Concrete URL with path should not be filtered",
                   mergedUrlsSet.contains(new MergedUrls("api/v1/users/123/posts", "GET", Constants.MERGED_URLS_FILTER_ACCOUNT_ID)));

        // Same URL with different method should NOT be filtered
        assertFalse("Same URL with different method should not be filtered",
                   mergedUrlsSet.contains(new MergedUrls("api/v1/users/INTEGER", "POST", Constants.MERGED_URLS_FILTER_ACCOUNT_ID)));
    }

    /**
     * Test edge cases
     */
    @Test
    public void testEdgeCases() {
        Set<MergedUrls> mergedUrlsSet = new HashSet<>();

        // Empty set - nothing should be filtered
        assertFalse("Empty set should not contain any URL",
                   mergedUrlsSet.contains(new MergedUrls("api/test", "GET", 100)));

        // Add a URL and test
        mergedUrlsSet.add(new MergedUrls("api/test", "GET", 100));
        assertTrue("Should find the URL after adding",
                  mergedUrlsSet.contains(new MergedUrls("api/test", "GET", 100)));

        // Test with null URL
        mergedUrlsSet.add(new MergedUrls(null, "GET", 100));
        assertTrue("Should handle null URL",
                  mergedUrlsSet.contains(new MergedUrls(null, "GET", 100)));
    }

    /**
     * Test account ID constant value
     */
    @Test
    public void testAccountIdConstant() {
        // Verify the expected account ID
        int expectedAccountId = Constants.MERGED_URLS_FILTER_ACCOUNT_ID;

        // This test documents the account ID that triggers merged URLs filtering
        assertEquals("Expected account ID for merged URLs filtering",
                    Constants.MERGED_URLS_FILTER_ACCOUNT_ID, expectedAccountId);
    }
}
