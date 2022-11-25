package com.akto.rules;

import com.akto.MongoBasedTest;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dto.*;
import com.akto.dto.testing.*;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.store.SampleMessageStore;
import com.akto.types.CappedSet;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import org.bson.types.ObjectId;
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
        double val = TestPlugin.compareWithOriginalResponse(originalPayload, currentPayload);
        assertEquals(val,20.0, 0.0);

        // {"nestedObject": {"keyA":{"keyB":"B", "keyC": ["A", "B"]}}}
        originalPayload = "{\"nestedObject\": {\"keyA\":{\"keyB\":\"B\", \"keyC\": [\"A\", \"B\"]}}}";
        currentPayload = "{\"nestedObject\": {\"keyA\":{\"keyB\":\"B\", \"keyC\": [\"A\"]}}}";
        val = TestPlugin.compareWithOriginalResponse(originalPayload, currentPayload);
        assertEquals(val,50.0, 0.0);

        // [{"name": "A", "age": 10},{"name": "B", "age": 10},{"name": "C", "age": 10}]
        originalPayload = "[{\"name\": \"A\", \"age\": 10},{\"name\": \"B\", \"age\": 10},{\"name\": \"C\", \"age\": 10}]";
        currentPayload = "[{\"name\": \"B\", \"age\": 10},{\"name\": \"B\", \"age\": 10},{\"name\": \"C\", \"age\": 10}]";
        val = TestPlugin.compareWithOriginalResponse(originalPayload, currentPayload);
        assertEquals(val,50.0, 0.0);

        originalPayload = "{}";
        currentPayload = "{}";
        val = TestPlugin.compareWithOriginalResponse(originalPayload, currentPayload);
        assertEquals(val,100.0, 100.0);

        originalPayload = "{\"user\":{}}";
        currentPayload = "{\"user\":{}}";
        val = TestPlugin.compareWithOriginalResponse(originalPayload, currentPayload);
        assertEquals(val,100.0, 100.0);
    }

    @Test
    public void testContainsPrivateResource() {
        Map<String, SingleTypeInfo> singleTypeInfoMap = new HashMap<>();
        BOLATest bolaTest = new BOLATest();

        // FIRST (Contains only private resources)
        ApiInfo.ApiInfoKey apiInfoKey1 = new ApiInfo.ApiInfoKey(123, "/api/books", URLMethods.Method.GET);

        insertIntoStiMap(apiInfoKey1,"param1", SingleTypeInfo.EMAIL, false, true, singleTypeInfoMap);
        insertIntoStiMap(apiInfoKey1,"param2", SingleTypeInfo.GENERIC, false, true, singleTypeInfoMap);
        insertIntoStiMap(apiInfoKey1,"param3", SingleTypeInfo.GENERIC, false, true, singleTypeInfoMap);

        String payload1 = "{\"param1\": \"avneesh@akto.io\", \"param2\": \"ankush\"}";
        OriginalHttpRequest originalHttpRequest1 = new OriginalHttpRequest("/api/books", "param3=ankita", apiInfoKey1.getMethod().name(), payload1, new HashMap<>(), "");
        TestPlugin.ContainsPrivateResourceResult result1 = bolaTest.containsPrivateResource(originalHttpRequest1, apiInfoKey1, singleTypeInfoMap);
        assertEquals(3, result1.singleTypeInfos.size());
        assertEquals(3, result1.findPrivateOnes().size());
        assertTrue(result1.isPrivate);

        // SECOND (Contains 2 public resources)
        ApiInfo.ApiInfoKey apiInfoKey2 = new ApiInfo.ApiInfoKey(123, "api/INTEGER/cars/STRING", URLMethods.Method.GET);

        insertIntoStiMap(apiInfoKey2,"1", SingleTypeInfo.INTEGER_32, true, true, singleTypeInfoMap);
        insertIntoStiMap(apiInfoKey2,"3", SingleTypeInfo.GENERIC, true,false, singleTypeInfoMap);
        insertIntoStiMap(apiInfoKey2,"param1", SingleTypeInfo.GENERIC, false, true, singleTypeInfoMap);
        insertIntoStiMap(apiInfoKey2,"param2", SingleTypeInfo.GENERIC, false,false, singleTypeInfoMap);
        String payload2 = "{\"param1\": \"Ronaldo\", \"param2\": \"Messi\"}";
        OriginalHttpRequest originalHttpRequest2 = new OriginalHttpRequest("/api/INTEGER/cars/STRING", null ,apiInfoKey2.getMethod().name(), payload2, new HashMap<>(), "");
        TestPlugin.ContainsPrivateResourceResult result2 = bolaTest.containsPrivateResource(originalHttpRequest2, apiInfoKey2, singleTypeInfoMap);
        assertEquals(4, result2.singleTypeInfos.size());
        assertFalse(result2.isPrivate);
        assertEquals(2, result2.findPrivateOnes().size());

        // THIRD (All missing) [We give missing STI benefit of doubt and consider it to be private]
        ApiInfo.ApiInfoKey apiInfoKey3 = new ApiInfo.ApiInfoKey(123, "/api/bus", URLMethods.Method.GET);

        String payload3 = "{\"param1\": \"Ronaldo\", \"param2\": \"Messi\"}";
        OriginalHttpRequest originalHttpRequest3 = new OriginalHttpRequest("/api/bus", null, apiInfoKey3.method.name(), payload3, new HashMap<>(), "");
        TestPlugin.ContainsPrivateResourceResult result3 = bolaTest.containsPrivateResource(originalHttpRequest3, apiInfoKey3, singleTypeInfoMap);
        assertEquals(0, result3.singleTypeInfos.size());
        assertTrue(result3.isPrivate);
        assertEquals(0, result3.findPrivateOnes().size());

        // FOURTH (Empty payload)
        ApiInfo.ApiInfoKey apiInfoKey4 = new ApiInfo.ApiInfoKey(123, "/api/toys", URLMethods.Method.GET);

        String payload4 = "{}";
        OriginalHttpRequest originalHttpRequest4 = new OriginalHttpRequest("/api/toys",null, apiInfoKey4.getMethod().name(), payload4, new HashMap<>(), "");
        TestPlugin.ContainsPrivateResourceResult result4 = bolaTest.containsPrivateResource(originalHttpRequest4, apiInfoKey4, singleTypeInfoMap);
        assertEquals(0, result4.singleTypeInfos.size());
        assertFalse(result4.isPrivate);
        assertEquals(0, result4.findPrivateOnes().size());

    }

    private HttpRequestParams buildHttpReq(String url, String method, int apiCollectionId, String payload) {
        return new HttpRequestParams(
                method, url, "", new HashMap<>(), payload, apiCollectionId
        );
    }

    private void insertIntoStiMap(ApiInfo.ApiInfoKey apiInfoKey, String param, SingleTypeInfo.SubType subType,
                                  boolean isUrlParam, boolean isPrivate, Map<String, SingleTypeInfo> singleTypeInfoMap)  {
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

        singleTypeInfoMap.put(singleTypeInfo.composeKeyWithCustomSubType(SingleTypeInfo.GENERIC), singleTypeInfo);
    }


    @Test
    public void testDecrementUrlVersion() {
        String result = TestPlugin.decrementUrlVersion("/api/v2/books/v3n0m/", 1, 1);
        assertEquals("/api/v1/books/v3n0m/", result);

        result = TestPlugin.decrementUrlVersion("/api/v22/books/", 2, 1);
        assertEquals("/api/v20/books/", result);

        result = TestPlugin.decrementUrlVersion("/api/v22/books", -1, 1);
        assertEquals("/api/v23/books", result);

        result = TestPlugin.decrementUrlVersion("/api/v1/books", 1, 1);
        assertNull(result);

    }


}
