package com.akto.runtime.policies;

import com.akto.dto.ApiInfo;
import com.akto.dto.type.URLMethods;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.util.HashMap;
import java.util.Map;

public class TestAktoPolicyWithoutDbCall {

    @Test
    public void testGetApiInfoMapKeyStrict() {
        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(0,"/api/books", URLMethods.Method.GET);
        Map<ApiInfo.ApiInfoKey, ApiInfo> apiInfoMap = new HashMap<>();
        ApiInfo.ApiInfoKey inMapKey1 = new ApiInfo.ApiInfoKey(0,"/api/books", URLMethods.Method.POST);
        ApiInfo.ApiInfoKey inMapKey2 = new ApiInfo.ApiInfoKey(0,"/api/books", URLMethods.Method.GET);
        apiInfoMap.put(inMapKey1, null);
        apiInfoMap.put(inMapKey2, null);
        ApiInfo.ApiInfoKey matchedKey = AktoPolicy.getApiInfoMapKey(apiInfoKey, apiInfoMap.keySet());
        Assertions.assertEquals(matchedKey, inMapKey2);
    }

    @Test
    public void testGetApiInfoMapKeyStrictFail() {
        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(0,"/api/books", URLMethods.Method.GET);
        Map<ApiInfo.ApiInfoKey, ApiInfo> apiInfoMap = new HashMap<>();
        ApiInfo.ApiInfoKey inMapKey1 = new ApiInfo.ApiInfoKey(0,"/api/books", URLMethods.Method.POST);
        ApiInfo.ApiInfoKey inMapKey2 = new ApiInfo.ApiInfoKey(1,"/api/books", URLMethods.Method.GET);
        ApiInfo.ApiInfoKey inMapKey3 = new ApiInfo.ApiInfoKey(0,"/api/books/3", URLMethods.Method.GET);
        apiInfoMap.put(inMapKey1, null);
        apiInfoMap.put(inMapKey2, null);
        apiInfoMap.put(inMapKey3, null);
        ApiInfo.ApiInfoKey matchedKey = AktoPolicy.getApiInfoMapKey(apiInfoKey, apiInfoMap.keySet() );
        Assertions.assertNull(matchedKey);
    }

    @Test
    public void testGetApiInfoMapKeyTemplateHappy() {
        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(0,"/api/books/3/cars/7/books", URLMethods.Method.GET);
        Map<ApiInfo.ApiInfoKey, ApiInfo> apiInfoMap = new HashMap<>();
        ApiInfo.ApiInfoKey inMapKey1 = new ApiInfo.ApiInfoKey(0,"/api/books/INTEGER/cars/INTEGER/books", URLMethods.Method.GET);
        apiInfoMap.put(inMapKey1, null);
        ApiInfo.ApiInfoKey matchedKey = AktoPolicy.getApiInfoMapKey(apiInfoKey, apiInfoMap.keySet() );
        Assertions.assertEquals(matchedKey, inMapKey1);
    }

    @Test
    public void testGetApiInfoMapKeyTemplateFail() {
        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(0,"/api/books/3/cars/7/books", URLMethods.Method.GET);
        Map<ApiInfo.ApiInfoKey, ApiInfo> apiInfoMap = new HashMap<>();
        ApiInfo.ApiInfoKey inMapKey1 = new ApiInfo.ApiInfoKey(0,"/api/books", URLMethods.Method.POST);
        ApiInfo.ApiInfoKey inMapKey2 = new ApiInfo.ApiInfoKey(1,"/api/books/INTEGER/cars/INTEGER/books", URLMethods.Method.GET);
        ApiInfo.ApiInfoKey inMapKey3 = new ApiInfo.ApiInfoKey(1,"/api/books/INTEGER/cars/INTEGER/books", URLMethods.Method.POST);
        apiInfoMap.put(inMapKey1, null);
        apiInfoMap.put(inMapKey2, null);
        apiInfoMap.put(inMapKey3, null);
        ApiInfo.ApiInfoKey matchedKey = AktoPolicy.getApiInfoMapKey(apiInfoKey, apiInfoMap.keySet() );
        Assertions.assertNull(matchedKey);
    }
}
