package com.akto.rules;

import com.akto.MongoBasedTest;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.dto.testing.*;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.store.SampleMessageStore;
import com.akto.testing.ApiExecutor;
import com.akto.types.CappedSet;
import com.akto.util.JSONUtils;
import com.akto.util.modifier.NoneAlgoJWTModifier;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class TestTestPlugin extends MongoBasedTest {

    @Test
    public void testIsStatusGood() {
        boolean result = TestPlugin.isStatusGood(200);
        assertTrue(result);
        result = TestPlugin.isStatusGood(300);
        assertFalse(result);
        result = TestPlugin.isStatusGood(299);
        assertTrue(result);
        result = TestPlugin.isStatusGood(100);
        assertFalse(result);
    }

    @Test
    public void testCompareWithOriginalResponse() {
//        {"name": "Ankush", "age": 100, "friends": [{"name": "Avneesh", "stud": true}, {"name": "ankita", "stud": true}], "jobs": ["MS", "CT"]}
        String originalPayload = "{\"name\": \"Ankush\", \"age\": 100, \"friends\": [{\"name\": \"Avneesh\", \"stud\": true}, {\"name\": \"ankita\", \"stud\": true}], \"jobs\": [\"MS\", \"CT\"]}";
        String currentPayload = "{\"name\": \"Vian\", \"age\": 1, \"friends\": [{\"name\": \"Avneesh\", \"stud\": true}, {\"name\": \"Ankita\", \"stud\": true}, {\"name\": \"Ankush\", \"stud\": true}], \"jobs\": []}";
        double val = TestPlugin.compareWithOriginalResponse(originalPayload, currentPayload, new HashMap<>());
        assertEquals(val,20.0, 0.0);

        // {"nestedObject": {"keyA":{"keyB":"B", "keyC": ["A", "B"]}}}
        originalPayload = "{\"nestedObject\": {\"keyA\":{\"keyB\":\"B\", \"keyC\": [\"A\", \"B\"]}}}";
        currentPayload = "{\"nestedObject\": {\"keyA\":{\"keyB\":\"B\", \"keyC\": [\"A\"]}}}";
        val = TestPlugin.compareWithOriginalResponse(originalPayload, currentPayload, new HashMap<>());
        assertEquals(val,50.0, 0.0);

        // [{"name": "A", "age": 10},{"name": "B", "age": 10},{"name": "C", "age": 10}]
        originalPayload = "[{\"name\": \"A\", \"age\": 10},{\"name\": \"B\", \"age\": 10},{\"name\": \"C\", \"age\": 10}]";
        currentPayload = "[{\"name\": \"B\", \"age\": 10},{\"name\": \"B\", \"age\": 10},{\"name\": \"C\", \"age\": 10}]";
        val = TestPlugin.compareWithOriginalResponse(originalPayload, currentPayload, new HashMap<>());
        assertEquals(val,50.0, 0.0);

        originalPayload = "{}";
        currentPayload = "{}";
        val = TestPlugin.compareWithOriginalResponse(originalPayload, currentPayload, new HashMap<>());
        assertEquals(val,100.0, 100.0);

        originalPayload = "{\"user\":{}}";
        currentPayload = "{\"user\":{}}";
        val = TestPlugin.compareWithOriginalResponse(originalPayload, currentPayload, new HashMap<>());
        assertEquals(val,100.0, 100.0);
    }

    @Test
    public void testCompareWithExcludedKeys() {
//        {"name": "Ankush", "age": 100, "friends": [{"name": "Avneesh", "stud": true}, {"name": "ankita", "stud": true}], "jobs": ["MS", "CT"]}
        String originalPayload = "{\"name\": \"Ankush\", \"age\": 100, \"friends\": [{\"name\": \"Avneesh\", \"stud\": true}, {\"name\": \"ankita\", \"stud\": true}], \"jobs\": [\"MS\", \"CT\"]}";
        String currentPayload = "{\"name\": \"Vian\", \"age\": 1, \"friends\": [{\"name\": \"Avneesh\", \"stud\": true}, {\"name\": \"Ankita\", \"stud\": true}, {\"name\": \"Ankush\", \"stud\": true}], \"jobs\": []}";
        Map<String, Boolean> excludedKeys = new HashMap<>();
        excludedKeys.put("friends#$#name", true);
        excludedKeys.put("name", true);
        double val = TestPlugin.compareWithOriginalResponse(originalPayload, currentPayload, excludedKeys);
        assertEquals(val,33.3, 0.04);
    }

    @Test
    public void testGetExcludedKeys() {
        ArrayList<OriginalHttpResponse> replayedResponses = new ArrayList<>();
        ArrayList<Map<String, Set<String>>> replayedResponseMap = new ArrayList<>();
        String originalPayload1 = "{\"name\": \"Ankush\", \"age\": 100, \"friends\": [{\"name\": \"Avneesh\", \"stud\": true}, {\"name\": \"ankita\", \"stud\": true}], \"jobs\": [\"MS\", \"CT\"]}";
        String originalPayload2 = "{\"name\": \"Ankush\", \"age\": 101, \"friends\": [{\"name\": \"Avneesh\", \"stud\": false}, {\"name\": \"ankita\", \"stud\": true}]}";

        Map<String, Set<String>> originalResponseParamMap1 = new HashMap<>();
        Map<String, Set<String>> originalResponseParamMap2 = new HashMap<>();
        try {
            TestPlugin.extractAllValuesFromPayload(originalPayload1, originalResponseParamMap1);
            TestPlugin.extractAllValuesFromPayload(originalPayload2, originalResponseParamMap2);
        } catch(Exception e) {
            fail("Unexpected Exception " + e.getMessage());
        }
        replayedResponseMap.add(originalResponseParamMap1);
        replayedResponseMap.add(originalResponseParamMap2);
        replayedResponses.add(new OriginalHttpResponse(originalPayload1, new HashMap<>(), 200));
        replayedResponses.add(new OriginalHttpResponse(originalPayload2, new HashMap<>(), 200));
        SampleRequestReplayResponse sampleReplayResp = new SampleRequestReplayResponse();
        sampleReplayResp.setReplayedResponseMap(replayedResponseMap);
        sampleReplayResp.setReplayedResponses(replayedResponses);
        Map<String, Boolean> comparisonExcludedKeys = TestPlugin.getComparisonExcludedKeys(sampleReplayResp, sampleReplayResp.getReplayedResponseMap());
        assertEquals(comparisonExcludedKeys.size(), 2);
    }

    @Test
    public void testGetExcludedKeysOrderChanged() {
        ArrayList<OriginalHttpResponse> replayedResponses = new ArrayList<>();
        ArrayList<Map<String, Set<String>>> replayedResponseMap = new ArrayList<>();
        String originalPayload1 = "{\"name\": \"Ankush\", \"age\": 100, \"friends\": [{\"name\": \"ankita\", \"stud\": true}, {\"name\": \"Avneesh\", \"stud\": true}], \"jobs\": [\"MS\", \"CT\"]}";
        String originalPayload2 = "{\"name\": \"Ankush\", \"age\": 101, \"friends\": [{\"name\": \"Avneesh\", \"stud\": false}, {\"name\": \"ankita\", \"stud\": true}]}";

        Map<String, Set<String>> originalResponseParamMap1 = new HashMap<>();
        Map<String, Set<String>> originalResponseParamMap2 = new HashMap<>();
        try {
            TestPlugin.extractAllValuesFromPayload(originalPayload1, originalResponseParamMap1);
            TestPlugin.extractAllValuesFromPayload(originalPayload2, originalResponseParamMap2);
        } catch(Exception e) {
            fail("Unexpected Exception " + e.getMessage());
        }
        replayedResponseMap.add(originalResponseParamMap1);
        replayedResponseMap.add(originalResponseParamMap2);
        replayedResponses.add(new OriginalHttpResponse(originalPayload1, new HashMap<>(), 200));
        replayedResponses.add(new OriginalHttpResponse(originalPayload2, new HashMap<>(), 200));
        SampleRequestReplayResponse sampleReplayResp = new SampleRequestReplayResponse();
        sampleReplayResp.setReplayedResponseMap(replayedResponseMap);
        sampleReplayResp.setReplayedResponses(replayedResponses);
        Map<String, Boolean> comparisonExcludedKeys = TestPlugin.getComparisonExcludedKeys(sampleReplayResp, sampleReplayResp.getReplayedResponseMap());
        assertEquals(comparisonExcludedKeys.size(), 2);
    }

    // @Test
    // public void testContainsPrivateResource() {
    //     Map<String, SingleTypeInfo> singleTypeInfoMap = new HashMap<>();
    //     BOLATest bolaTest = new BOLATest();

    //     // FIRST (Contains only private resources)
    //     ApiInfo.ApiInfoKey apiInfoKey1 = new ApiInfo.ApiInfoKey(123, "/api/books", URLMethods.Method.GET);

    //     insertIntoStiMap(apiInfoKey1,"param1", SingleTypeInfo.EMAIL, false, true, singleTypeInfoMap);
    //     insertIntoStiMap(apiInfoKey1,"param2", SingleTypeInfo.GENERIC, false, true, singleTypeInfoMap);
    //     insertIntoStiMap(apiInfoKey1,"param3", SingleTypeInfo.GENERIC, false, true, singleTypeInfoMap);

    //     String payload1 = "{\"param1\": \"avneesh@akto.io\", \"param2\": \"ankush\"}";
    //     OriginalHttpRequest originalHttpRequest1 = new OriginalHttpRequest("/api/books", "param3=ankita", apiInfoKey1.getMethod().name(), payload1, new HashMap<>(), "");
    //     TestPlugin.ContainsPrivateResourceResult result1 = bolaTest.containsPrivateResource(originalHttpRequest1, apiInfoKey1, singleTypeInfoMap);
    //     assertEquals(3, result1.singleTypeInfos.size());
    //     assertEquals(3, result1.findPrivateOnes().size());
    //     assertTrue(result1.isPrivate);

    //     // SECOND (Contains 2 public resources)
    //     ApiInfo.ApiInfoKey apiInfoKey2 = new ApiInfo.ApiInfoKey(123, "api/INTEGER/cars/STRING", URLMethods.Method.GET);

    //     insertIntoStiMap(apiInfoKey2,"1", SingleTypeInfo.INTEGER_32, true, true, singleTypeInfoMap);
    //     insertIntoStiMap(apiInfoKey2,"3", SingleTypeInfo.GENERIC, true,false, singleTypeInfoMap);
    //     insertIntoStiMap(apiInfoKey2,"param1", SingleTypeInfo.GENERIC, false, true, singleTypeInfoMap);
    //     insertIntoStiMap(apiInfoKey2,"param2", SingleTypeInfo.GENERIC, false,false, singleTypeInfoMap);
    //     String payload2 = "{\"param1\": \"Ronaldo\", \"param2\": \"Messi\"}";
    //     OriginalHttpRequest originalHttpRequest2 = new OriginalHttpRequest("/api/INTEGER/cars/STRING", null ,apiInfoKey2.getMethod().name(), payload2, new HashMap<>(), "");
    //     TestPlugin.ContainsPrivateResourceResult result2 = bolaTest.containsPrivateResource(originalHttpRequest2, apiInfoKey2, singleTypeInfoMap);
    //     assertEquals(4, result2.singleTypeInfos.size());
    //     assertFalse(result2.isPrivate);
    //     assertEquals(2, result2.findPrivateOnes().size());

    //     // THIRD (All missing) [We give missing STI benefit of doubt and consider it to be private]
    //     ApiInfo.ApiInfoKey apiInfoKey3 = new ApiInfo.ApiInfoKey(123, "/api/bus", URLMethods.Method.GET);

    //     String payload3 = "{\"param1\": \"Ronaldo\", \"param2\": \"Messi\"}";
    //     OriginalHttpRequest originalHttpRequest3 = new OriginalHttpRequest("/api/bus", null, apiInfoKey3.method.name(), payload3, new HashMap<>(), "");
    //     TestPlugin.ContainsPrivateResourceResult result3 = bolaTest.containsPrivateResource(originalHttpRequest3, apiInfoKey3, singleTypeInfoMap);
    //     assertEquals(0, result3.singleTypeInfos.size());
    //     assertTrue(result3.isPrivate);
    //     assertEquals(0, result3.findPrivateOnes().size());

    //     // FOURTH (Empty payload)
    //     ApiInfo.ApiInfoKey apiInfoKey4 = new ApiInfo.ApiInfoKey(123, "/api/toys", URLMethods.Method.GET);

    //     String payload4 = "{}";
    //     OriginalHttpRequest originalHttpRequest4 = new OriginalHttpRequest("/api/toys",null, apiInfoKey4.getMethod().name(), payload4, new HashMap<>(), "");
    //     TestPlugin.ContainsPrivateResourceResult result4 = bolaTest.containsPrivateResource(originalHttpRequest4, apiInfoKey4, singleTypeInfoMap);
    //     assertEquals(0, result4.singleTypeInfos.size());
    //     assertFalse(result4.isPrivate);
    //     assertEquals(0, result4.findPrivateOnes().size());

    // }

    private HttpRequestParams buildHttpReq(String url, String method, int apiCollectionId, String payload) {
        return new HttpRequestParams(
                method, url, "", new HashMap<>(), payload, apiCollectionId
        );
    }

    private void insertIntoStiMap(ApiInfo.ApiInfoKey apiInfoKey, String param, SingleTypeInfo.SubType subType,
                                  boolean isUrlParam, boolean isPrivate, SampleMessageStore sampleMessageStore)  {
        int apiCollectionId = apiInfoKey.getApiCollectionId();
        String url = apiInfoKey.getUrl();
        String method = apiInfoKey.getMethod().name();

        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(
                url, method,-1, false, param, subType, apiCollectionId, isUrlParam
        );

        SingleTypeInfo singleTypeInfo = new SingleTypeInfo(
                paramId,new HashSet<>(), new HashSet<>(), 0, Context.now(), 0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE
        );

        singleTypeInfo.setPublicCount(10);
        if (isPrivate) {
            singleTypeInfo.setUniqueCount(1000000);
        } else {
            singleTypeInfo.setUniqueCount(10);
        }

        sampleMessageStore.getSingleTypeInfos().put(singleTypeInfo.composeKeyWithCustomSubType(SingleTypeInfo.GENERIC), singleTypeInfo);
    }

    @Test
    public void testModifyJwtHeaderToNoneAlgo() {

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("origin", Collections.singletonList("https://www.akto.io"));
        headers.put("random", Collections.singletonList("avneesh_is_studddd"));

        // no change to headers since it doesn't find any JWT token
        Map<String, List<String>> result = JSONUtils.modifyHeaderValues(headers, new NoneAlgoJWTModifier("none"));
        assertNull(result);
        assertEquals(2, headers.size());
        assertEquals("https://www.akto.io", headers.get("origin").get(0));
        assertEquals("avneesh_is_studddd", headers.get("random").get(0));

        headers.put("access-token", Collections.singletonList("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"));
        result = JSONUtils.modifyHeaderValues(headers, new NoneAlgoJWTModifier("none"));
        assertNotNull(result);
        assertEquals("eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.", result.get("access-token").get(0));
    }

    @Test
    public void testFindUndocumentedMethods() {
        Map<ApiInfo.ApiInfoKey, List<String>> sampleMessages = new HashMap<>();
        sampleMessages.put(new ApiInfo.ApiInfoKey(0, "/api/books", URLMethods.Method.GET), Collections.singletonList(""));
        sampleMessages.put(new ApiInfo.ApiInfoKey(0, "/api/books", URLMethods.Method.POST), Collections.singletonList(""));
        sampleMessages.put(new ApiInfo.ApiInfoKey(1, "/api/books", URLMethods.Method.PUT), Collections.singletonList(""));
        sampleMessages.put(new ApiInfo.ApiInfoKey(0, "/api/books/INTEGER", URLMethods.Method.GET), Collections.singletonList(""));
        sampleMessages.put(new ApiInfo.ApiInfoKey(0, "/api/books/INTEGER", URLMethods.Method.POST), Collections.singletonList(""));
        sampleMessages.put(new ApiInfo.ApiInfoKey(1, "/api/books/INTEGER", URLMethods.Method.PUT), Collections.singletonList(""));

        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(0, "/api/books", URLMethods.Method.GET);
        List<URLMethods.Method> undocumentedMethods = TestPlugin.findUndocumentedMethods(sampleMessages, apiInfoKey);
        assertEquals(3, undocumentedMethods.size());

        apiInfoKey = new ApiInfo.ApiInfoKey(0, "/api/books/1", URLMethods.Method.GET);
        undocumentedMethods = TestPlugin.findUndocumentedMethods(sampleMessages, apiInfoKey);
        assertEquals(3, undocumentedMethods.size());

    }

    @Test
    public void testOverrideAppUrl() {

        String url = "http://google.com/some/path/here/?param1=1&param2=2";
        String newHost = "https://twitter.com:80";

        System.out.println(ApiExecutor.replaceHostFromConfig(url, new TestingRunConfig(0, null, null, null, newHost, null)));

    }

}
