package com.akto.runtime.policies;

import com.akto.dto.ApiInfo;
import com.akto.dto.type.URLMethods;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.util.HashMap;
import java.util.Map;

public class TestAktoPolicyWithoutDbCall {
//
//    @Test
//    public void testGetApiInfoMapKeyStrict() {
//        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(0,"/api/books", URLMethods.Method.GET);
//        Map<String, Map<ApiInfo.ApiInfoKey, ApiInfo>> apiInfoMap = new HashMap<>();
//        apiInfoMap.put(AktoPolicy.STRICT, new HashMap<>());
//        apiInfoMap.put(AktoPolicy.TEMPLATE, new HashMap<>());
//
//        ApiInfo.ApiInfoKey inMapKey1 = new ApiInfo.ApiInfoKey(0,"/api/books", URLMethods.Method.POST);
//        ApiInfo.ApiInfoKey inMapKey2 = new ApiInfo.ApiInfoKey(0,"/api/books", URLMethods.Method.GET);
//        apiInfoMap.get(AktoPolicy.STRICT).put(inMapKey1, null);
//        apiInfoMap.get(AktoPolicy.STRICT).put(inMapKey2, null);
//
//        AktoPolicy aktoPolicy = new AktoPolicy(false);
//        aktoPolicy.setApiInfoMap(apiInfoMap);
//        ApiInfo apiInfo = aktoPolicy.getApiInfoFromMap(apiInfoKey);
//        Assertions.assertEquals(apiInfo.getId(), inMapKey2);
//    }
//
//    @Test
//    public void testGetApiInfoMapKeyStrictFail() {
//        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(0,"/api/books", URLMethods.Method.GET);
//        Map<String, Map<ApiInfo.ApiInfoKey, ApiInfo>> apiInfoMap = new HashMap<>();
//        apiInfoMap.put(AktoPolicy.STRICT, new HashMap<>());
//        apiInfoMap.put(AktoPolicy.TEMPLATE, new HashMap<>());
//
//        ApiInfo.ApiInfoKey inMapKey1 = new ApiInfo.ApiInfoKey(0,"/api/books", URLMethods.Method.POST);
//        ApiInfo.ApiInfoKey inMapKey2 = new ApiInfo.ApiInfoKey(1,"/api/books", URLMethods.Method.GET);
//        ApiInfo.ApiInfoKey inMapKey3 = new ApiInfo.ApiInfoKey(0,"/api/books/3", URLMethods.Method.GET);
//        apiInfoMap.get(AktoPolicy.STRICT).put(inMapKey1, null);
//        apiInfoMap.get(AktoPolicy.STRICT).put(inMapKey2, null);
//        apiInfoMap.get(AktoPolicy.STRICT).put(inMapKey3, null);
//        AktoPolicy aktoPolicy = new AktoPolicy(false);
//        aktoPolicy.setApiInfoMap(apiInfoMap);
//        ApiInfo apiInfo = aktoPolicy.getApiInfoFromMap(apiInfoKey);
//        Assertions.assertNull(apiInfo);
//    }
//
//    @Test
//    public void testGetApiInfoMapKeyTemplateHappy() {
//        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(0,"/api/books/3/cars/7/books", URLMethods.Method.GET);
//        Map<String, Map<ApiInfo.ApiInfoKey, ApiInfo>> apiInfoMap = new HashMap<>();
//        apiInfoMap.put(AktoPolicy.STRICT, new HashMap<>());
//        apiInfoMap.put(AktoPolicy.TEMPLATE, new HashMap<>());
//
//        ApiInfo.ApiInfoKey inMapKey1 = new ApiInfo.ApiInfoKey(0,"/api/books/INTEGER/cars/INTEGER/books", URLMethods.Method.GET);
//        apiInfoMap.get(AktoPolicy.TEMPLATE).put(inMapKey1, null);
//        AktoPolicy aktoPolicy = new AktoPolicy(false);
//        aktoPolicy.setApiInfoMap(apiInfoMap);
//        ApiInfo apiInfo = aktoPolicy.getApiInfoFromMap(apiInfoKey);
//        Assertions.assertEquals(apiInfo.getId(), inMapKey1);
//    }
//
//    @Test
//    public void testGetApiInfoMapKeyTemplateFail() {
//        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(0,"/api/books/3/cars/7/books", URLMethods.Method.GET);
//        Map<String, Map<ApiInfo.ApiInfoKey, ApiInfo>> apiInfoMap = new HashMap<>();
//        apiInfoMap.put(AktoPolicy.STRICT, new HashMap<>());
//        apiInfoMap.put(AktoPolicy.TEMPLATE, new HashMap<>());
//
//        ApiInfo.ApiInfoKey inMapKey1 = new ApiInfo.ApiInfoKey(0,"/api/books", URLMethods.Method.POST);
//        ApiInfo.ApiInfoKey inMapKey2 = new ApiInfo.ApiInfoKey(1,"/api/books/INTEGER/cars/INTEGER/books", URLMethods.Method.GET);
//        ApiInfo.ApiInfoKey inMapKey3 = new ApiInfo.ApiInfoKey(1,"/api/books/INTEGER/cars/INTEGER/books", URLMethods.Method.POST);
//        apiInfoMap.get(AktoPolicy.STRICT).put(inMapKey1, null);
//        apiInfoMap.get(AktoPolicy.TEMPLATE).put(inMapKey2, null);
//        apiInfoMap.get(AktoPolicy.TEMPLATE).put(inMapKey3, null);
//        AktoPolicy aktoPolicy = new AktoPolicy(false);
//        aktoPolicy.setApiInfoMap(apiInfoMap);
//        ApiInfo apiInfo = aktoPolicy.getApiInfoFromMap(apiInfoKey);
//        Assertions.assertNull(apiInfo);
//    }
//
//    @Test
//    public void testGetApiInfoMapKeyTemplateWithLeadingSlashHappy() {
//        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(0,"api/books/3/cars/7/books", URLMethods.Method.GET);
//        Map<String, Map<ApiInfo.ApiInfoKey, ApiInfo>> apiInfoMap = new HashMap<>();
//        apiInfoMap.put(AktoPolicy.STRICT, new HashMap<>());
//        apiInfoMap.put(AktoPolicy.TEMPLATE, new HashMap<>());
//
//        ApiInfo.ApiInfoKey inMapKey1 = new ApiInfo.ApiInfoKey(0,"/api/books/INTEGER/cars/INTEGER/books", URLMethods.Method.GET);
//        apiInfoMap.get(AktoPolicy.TEMPLATE).put(inMapKey1, null);
//        AktoPolicy aktoPolicy = new AktoPolicy(false);
//        aktoPolicy.setApiInfoMap(apiInfoMap);
//        ApiInfo apiInfo = aktoPolicy.getApiInfoFromMap(apiInfoKey);
//        Assertions.assertEquals(apiInfo.getId(), inMapKey1);
//    }
}
