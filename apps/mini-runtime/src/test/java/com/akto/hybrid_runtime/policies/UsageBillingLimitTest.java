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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UsageBillingLimitTest {

    private static final int COLLECTION_ID = 42;

    private AktoPolicyNew buildPolicyWithStrictUrl(String url, URLMethods.Method method) throws Exception {
        AktoPolicyNew policy = new AktoPolicyNew();
        ApiInfo apiInfo = new ApiInfo(COLLECTION_ID, url, method);
        PolicyCatalog pc = new PolicyCatalog(apiInfo, new HashMap<>());
        pc.setSeenEarlier(true);

        Map<URLStatic, PolicyCatalog> strict = new HashMap<>();
        strict.put(new URLStatic(url, method), pc);
        ApiInfoCatalog catalog = new ApiInfoCatalog(strict, new HashMap<>(), new ArrayList<>());

        Map<Integer, ApiInfoCatalog> catalogMap = new HashMap<>();
        catalogMap.put(COLLECTION_ID, catalog);
        setApiInfoCatalogMap(policy, catalogMap);
        return policy;
    }

    private static void setApiInfoCatalogMap(AktoPolicyNew policy, Map<Integer, ApiInfoCatalog> map) throws Exception {
        Field f = AktoPolicyNew.class.getDeclaredField("apiInfoCatalogMap");
        f.setAccessible(true);
        f.set(policy, map);
    }

    private static Map<Integer, ApiInfoCatalog> getApiInfoCatalogMap(AktoPolicyNew policy) throws Exception {
        Field f = AktoPolicyNew.class.getDeclaredField("apiInfoCatalogMap");
        f.setAccessible(true);
        return (Map<Integer, ApiInfoCatalog>) f.get(policy);
    }

    @Test
    public void testApiInfoNotWrittenWhenLimitExhausted() throws Exception {
        SyncLimit syncLimit = new SyncLimit(true, 0);

        String url = "/api/users";
        URLMethods.Method method = URLMethods.Method.GET;
        AktoPolicyNew policy = buildPolicyWithStrictUrl(url, method);

        Assertions.assertEquals(1, AktoPolicyNew.getUpdates(getApiInfoCatalogMap(policy)).size(),
            "URL seen this cycle should be pending write before limit check");

        boolean shouldSkip = syncLimit.updateUsageLeftAndCheckSkip();
        Assertions.assertTrue(shouldSkip);

        if (shouldSkip) {
            policy.removeApiInfo(COLLECTION_ID, url, method);
        }

        Assertions.assertEquals(0, AktoPolicyNew.getUpdates(getApiInfoCatalogMap(policy)).size(),
            "api_info must not be written when STI write is blocked by limit");
    }

    @Test
    public void testApiInfoWrittenWhenLimitNotExhausted() throws Exception {
        SyncLimit syncLimit = new SyncLimit(true, 100);

        String url = "/api/products";
        URLMethods.Method method = URLMethods.Method.POST;
        AktoPolicyNew policy = buildPolicyWithStrictUrl(url, method);

        boolean shouldSkip = syncLimit.updateUsageLeftAndCheckSkip();
        Assertions.assertFalse(shouldSkip);

        if (shouldSkip) {
            policy.removeApiInfo(COLLECTION_ID, url, method);
        }

        List<ApiInfo> updates = AktoPolicyNew.getUpdates(getApiInfoCatalogMap(policy));
        Assertions.assertEquals(1, updates.size(), "api_info must be written when limit is not exhausted");
        Assertions.assertEquals(url, updates.get(0).getId().getUrl());
    }

    @Test
    public void testApiInfoWrittenWhenCheckLimitFalse() throws Exception {
        SyncLimit syncLimit = new SyncLimit(false, 0);

        String url = "/api/orders";
        URLMethods.Method method = URLMethods.Method.GET;
        AktoPolicyNew policy = buildPolicyWithStrictUrl(url, method);

        boolean shouldSkip = syncLimit.updateUsageLeftAndCheckSkip();
        Assertions.assertFalse(shouldSkip);

        Assertions.assertEquals(1, AktoPolicyNew.getUpdates(getApiInfoCatalogMap(policy)).size(),
            "api_info must be written on unlimited plan");
    }

    @Test
    public void testMarkSkippedOnMissingCollectionDoesNotThrow() throws Exception {
        SyncLimit syncLimit = new SyncLimit(true, 0);
        AktoPolicyNew policy = new AktoPolicyNew();
        setApiInfoCatalogMap(policy, new HashMap<>());

        syncLimit.updateUsageLeftAndCheckSkip();
        policy.removeApiInfo(999, "/no/collection", URLMethods.Method.GET);
    }

    @Test
    public void testMarkSkippedDoesNotAffectOtherUrls() throws Exception {
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
        setApiInfoCatalogMap(policy, catalogMap);

        boolean skipA = syncLimit.updateUsageLeftAndCheckSkip();
        Assertions.assertFalse(skipA);
        if (skipA) policy.removeApiInfo(COLLECTION_ID, "/api/a", URLMethods.Method.GET);

        boolean skipB = syncLimit.updateUsageLeftAndCheckSkip();
        Assertions.assertTrue(skipB);
        if (skipB) policy.removeApiInfo(COLLECTION_ID, "/api/b", URLMethods.Method.GET);

        List<ApiInfo> updates = AktoPolicyNew.getUpdates(getApiInfoCatalogMap(policy));
        Assertions.assertEquals(1, updates.size(), "only the URL within limit should have api_info written");
        Assertions.assertEquals("/api/a", updates.get(0).getId().getUrl());
    }

    @Test
    public void testApiInfoNotWrittenForTemplateUrlWhenLimitExhausted() throws Exception {
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
        setApiInfoCatalogMap(policy, catalogMap);

        Assertions.assertEquals(1, AktoPolicyNew.getUpdates(getApiInfoCatalogMap(policy)).size());

        boolean shouldSkip = syncLimit.updateUsageLeftAndCheckSkip();
        Assertions.assertTrue(shouldSkip);

        if (shouldSkip) {
            policy.removeApiInfo(COLLECTION_ID, urlTemplate.getTemplateString(), method);
        }

        Assertions.assertEquals(0, AktoPolicyNew.getUpdates(getApiInfoCatalogMap(policy)).size(),
            "template URL api_info must be suppressed when limit is exhausted");
    }
}
