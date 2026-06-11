package com.akto.hybrid_runtime.policies;

import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfoCatalog;
import com.akto.dto.PolicyCatalog;
import com.akto.dto.billing.SyncLimit;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLStatic;
import com.akto.dto.type.URLTemplate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UsageBillingLimitTest {

    private static final int COLLECTION_ID = 42;

    private AktoPolicyNew buildPolicyWithStrictUrl(String url, URLMethods.Method method) {
        AktoPolicyNew policy = new AktoPolicyNew();
        ApiInfo apiInfo = new ApiInfo(COLLECTION_ID, url, method);
        PolicyCatalog pc = new PolicyCatalog(apiInfo, new HashMap<>());
        pc.setSeenEarlier(true);

        Map<URLStatic, PolicyCatalog> strict = new HashMap<>();
        strict.put(new URLStatic(url, method), pc);
        ApiInfoCatalog catalog = new ApiInfoCatalog(strict, new HashMap<>(), new ArrayList<>());

        Map<Integer, ApiInfoCatalog> catalogMap = new HashMap<>();
        catalogMap.put(COLLECTION_ID, catalog);
        policy.apiInfoCatalogMap = catalogMap;
        return policy;
    }

    /**
     * When usageLimit=5 and currentUsage=5, usageLeft=0.
     * The first new URL decrements usageLeft to -1 → updateUsageLeftAndCheckSkip returns true (skip).
     * markSkipped() is called → api_info write is suppressed.
     */
    @Test
    public void testApiInfoNotWrittenWhenLimitExhausted() {
        // usageLimit=5, currentUsage=5 → usageLeft=0, checkLimit=true
        SyncLimit syncLimit = new SyncLimit(true, 0);

        String url = "/api/users";
        URLMethods.Method method = URLMethods.Method.GET;
        AktoPolicyNew policy = buildPolicyWithStrictUrl(url, method);

        Assertions.assertEquals(1, AktoPolicyNew.getUpdates(policy.apiInfoCatalogMap).size(),
            "URL seen this cycle should be pending write before limit check");

        // Mirrors APICatalogSync limit-gate logic
        boolean shouldSkip = syncLimit.updateUsageLeftAndCheckSkip();
        Assertions.assertTrue(shouldSkip, "SyncLimit(checkLimit=true, usageLeft=0) must signal skip on first new URL");

        if (shouldSkip) {
            policy.markSkipped(COLLECTION_ID, url, method);
        }

        List<ApiInfo> updates = AktoPolicyNew.getUpdates(policy.apiInfoCatalogMap);
        Assertions.assertEquals(0, updates.size(), "api_info must not be written when STI write is blocked by limit");
    }

    /**
     * When usageLimit=100 and currentUsage=0, usageLeft=100, checkLimit=true.
     * updateUsageLeftAndCheckSkip returns false → markSkipped is NOT called → api_info write proceeds.
     */
    @Test
    public void testApiInfoWrittenWhenLimitNotExhausted() {
        // usageLimit=100, currentUsage=0 → usageLeft=100, checkLimit=true
        SyncLimit syncLimit = new SyncLimit(true, 100);

        String url = "/api/products";
        URLMethods.Method method = URLMethods.Method.POST;
        AktoPolicyNew policy = buildPolicyWithStrictUrl(url, method);

        boolean shouldSkip = syncLimit.updateUsageLeftAndCheckSkip();
        Assertions.assertFalse(shouldSkip, "SyncLimit with 100 usage left must not signal skip");

        if (shouldSkip) {
            policy.markSkipped(COLLECTION_ID, url, method);
        }

        List<ApiInfo> updates = AktoPolicyNew.getUpdates(policy.apiInfoCatalogMap);
        Assertions.assertEquals(1, updates.size(), "api_info must be written when limit is not exhausted");
        Assertions.assertEquals(url, updates.get(0).getId().getUrl());
    }

    /**
     * checkLimit=false (unlimited plan) → updateUsageLeftAndCheckSkip always returns false.
     * markSkipped is never called → api_info write proceeds normally.
     */
    @Test
    public void testApiInfoWrittenWhenCheckLimitFalse() {
        SyncLimit syncLimit = new SyncLimit(false, 0);

        String url = "/api/orders";
        URLMethods.Method method = URLMethods.Method.GET;
        AktoPolicyNew policy = buildPolicyWithStrictUrl(url, method);

        boolean shouldSkip = syncLimit.updateUsageLeftAndCheckSkip();
        Assertions.assertFalse(shouldSkip, "checkLimit=false must never signal skip regardless of usageLeft");

        List<ApiInfo> updates = AktoPolicyNew.getUpdates(policy.apiInfoCatalogMap);
        Assertions.assertEquals(1, updates.size(), "api_info must be written on unlimited plan");
    }

    /**
     * markSkipped on a URL that was never registered in apiInfoCatalogMap must not throw.
     */
    @Test
    public void testMarkSkippedOnMissingCollectionDoesNotThrow() {
        SyncLimit syncLimit = new SyncLimit(true, 0);
        AktoPolicyNew policy = new AktoPolicyNew();
        policy.apiInfoCatalogMap = new HashMap<>();

        syncLimit.updateUsageLeftAndCheckSkip();
        policy.markSkipped(999, "/no/collection", URLMethods.Method.GET);
    }

    /**
     * markSkipped for URL-A must not affect URL-B in the same collection.
     */
    @Test
    public void testMarkSkippedDoesNotAffectOtherUrls() {
        // Only 1 usage left → first new URL is allowed, second is skipped
        SyncLimit syncLimit = new SyncLimit(true, 1);

        AktoPolicyNew policy = new AktoPolicyNew();
        Map<URLStatic, PolicyCatalog> strict = new HashMap<>();

        for (String url : new String[]{"/api/a", "/api/b"}) {
            ApiInfo apiInfo = new ApiInfo(COLLECTION_ID, url, URLMethods.Method.GET);
            PolicyCatalog pc = new PolicyCatalog(apiInfo, new HashMap<>());
            pc.setSeenEarlier(true);
            strict.put(new URLStatic(url, URLMethods.Method.GET), pc);
        }

        ApiInfoCatalog catalog = new ApiInfoCatalog(strict, new HashMap<>(), new ArrayList<>());
        Map<Integer, ApiInfoCatalog> catalogMap = new HashMap<>();
        catalogMap.put(COLLECTION_ID, catalog);
        policy.apiInfoCatalogMap = catalogMap;

        // /api/a → usageLeft goes 1→0, returns false (keep)
        boolean skipA = syncLimit.updateUsageLeftAndCheckSkip();
        Assertions.assertFalse(skipA);
        if (skipA) policy.markSkipped(COLLECTION_ID, "/api/a", URLMethods.Method.GET);

        // /api/b → usageLeft goes 0→-1, returns true (skip)
        boolean skipB = syncLimit.updateUsageLeftAndCheckSkip();
        Assertions.assertTrue(skipB);
        if (skipB) policy.markSkipped(COLLECTION_ID, "/api/b", URLMethods.Method.GET);

        List<ApiInfo> updates = AktoPolicyNew.getUpdates(policy.apiInfoCatalogMap);
        Assertions.assertEquals(1, updates.size(), "only the URL within limit should have api_info written");
        Assertions.assertEquals("/api/a", updates.get(0).getId().getUrl());
    }

    /**
     * Template URL: limit exhausted → markSkipped via concrete URL match → api_info suppressed.
     */
    @Test
    public void testApiInfoNotWrittenForTemplateUrlWhenLimitExhausted() {
        SyncLimit syncLimit = new SyncLimit(true, 0);

        AktoPolicyNew policy = new AktoPolicyNew();
        URLMethods.Method method = URLMethods.Method.GET;

        URLTemplate urlTemplate = new URLTemplate(
            new String[]{"api", "users", null},
            new SingleTypeInfo.SuperType[]{null, null, SingleTypeInfo.SuperType.INTEGER},
            method
        );

        ApiInfo apiInfo = new ApiInfo(COLLECTION_ID, urlTemplate.getTemplateString(), method);
        PolicyCatalog pc = new PolicyCatalog(apiInfo, new HashMap<>());
        pc.setSeenEarlier(true);

        Map<URLTemplate, PolicyCatalog> templates = new HashMap<>();
        templates.put(urlTemplate, pc);
        ApiInfoCatalog catalog = new ApiInfoCatalog(new HashMap<>(), templates, new ArrayList<>());

        Map<Integer, ApiInfoCatalog> catalogMap = new HashMap<>();
        catalogMap.put(COLLECTION_ID, catalog);
        policy.apiInfoCatalogMap = catalogMap;

        Assertions.assertEquals(1, AktoPolicyNew.getUpdates(policy.apiInfoCatalogMap).size());

        boolean shouldSkip = syncLimit.updateUsageLeftAndCheckSkip();
        Assertions.assertTrue(shouldSkip);

        if (shouldSkip) {
            // APICatalogSync passes getTemplateString() when removing template URLs
            policy.markSkipped(COLLECTION_ID, urlTemplate.getTemplateString(), method);
        }

        Assertions.assertEquals(0, AktoPolicyNew.getUpdates(policy.apiInfoCatalogMap).size(),
            "template URL api_info must be suppressed when limit is exhausted");
    }
}
