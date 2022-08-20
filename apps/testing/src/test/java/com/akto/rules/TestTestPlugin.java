package com.akto.rules;

import com.akto.MongoBasedTest;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.store.SampleMessageStore;
import com.akto.types.CappedSet;
import com.mongodb.BasicDBObject;
import org.bson.types.ObjectId;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

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
    }

    @Test
    public void testAddWithoutRequestError() {
        TestingRunResultDao.instance.getMCollection().drop();
        TestPlugin bolaTest = new BOLATest();
        TestPlugin noAuthTest = new NoAuthTest();

        ApiInfo.ApiInfoKey apiInfoKey1 = new ApiInfo.ApiInfoKey(0,"url1", URLMethods.Method.GET);
        ObjectId testRunId1 = new ObjectId();
        bolaTest.addWithoutRequestError(apiInfoKey1, testRunId1, TestResult.TestError.API_REQUEST_FAILED);
        noAuthTest.addWithoutRequestError(apiInfoKey1, testRunId1, TestResult.TestError.NO_AUTH_MECHANISM);

        ApiInfo.ApiInfoKey apiInfoKey2 = new ApiInfo.ApiInfoKey(0,"url1", URLMethods.Method.POST);
        ObjectId testRunId2 = new ObjectId();
        bolaTest.addWithoutRequestError(apiInfoKey2, testRunId2, TestResult.TestError.NO_PATH);

        ObjectId testRunId3 = new ObjectId();
        noAuthTest.addWithoutRequestError(apiInfoKey1, testRunId3, TestResult.TestError.NO_PATH);

        List<TestingRunResult> testingRunResultList = TestingRunResultDao.instance.findAll(new BasicDBObject());
        assertEquals(testingRunResultList.size(),3);

        TestingRunResult testingRunResult1 = TestingRunResultDao.instance.findOne(TestingRunResultDao.generateFilter(testRunId1,apiInfoKey1));
        assertEquals(testingRunResult1.getResultMap().size(),2);
        assertFalse(testingRunResult1.getResultMap().get(bolaTest.testName()).isVulnerable());
        assertEquals(testingRunResult1.getResultMap().get(bolaTest.testName()).getErrors().get(0), TestResult.TestError.API_REQUEST_FAILED);
        assertEquals(testingRunResult1.getResultMap().get(noAuthTest.testName()).getErrors().get(0), TestResult.TestError.NO_AUTH_MECHANISM);

        TestingRunResult testingRunResult2 = TestingRunResultDao.instance.findOne(TestingRunResultDao.generateFilter(testRunId2,apiInfoKey2));
        assertEquals(testingRunResult2.getResultMap().size(),1);
        assertEquals(testingRunResult2.getResultMap().get(bolaTest.testName()).getErrors().get(0), TestResult.TestError.NO_PATH);
        assertNull(testingRunResult2.getResultMap().get(noAuthTest.testName()));

        TestingRunResult testingRunResult3 = TestingRunResultDao.instance.findOne(TestingRunResultDao.generateFilter(testRunId3,apiInfoKey1));
        assertEquals(testingRunResult3.getResultMap().size(),1);
        assertEquals(testingRunResult3.getResultMap().get(noAuthTest.testName()).getErrors().get(0), TestResult.TestError.NO_PATH);
        assertNull(testingRunResult3.getResultMap().get(bolaTest.testName()));
    }

    @Test
    public void testContainsPrivateResource() {
        SampleMessageStore.singleTypeInfos = new HashMap<>();
        BOLATest bolaTest = new BOLATest();

        // FIRST (Contains only private resources)
        ApiInfo.ApiInfoKey apiInfoKey1 = new ApiInfo.ApiInfoKey(123, "/api/books", URLMethods.Method.GET);

        insertIntoStiMap(apiInfoKey1,"param1", SingleTypeInfo.EMAIL, false, true);
        insertIntoStiMap(apiInfoKey1,"param2", SingleTypeInfo.GENERIC, false, true);
        insertIntoStiMap(apiInfoKey1,"param3", SingleTypeInfo.GENERIC, false, true);

        String payload1 = "{\"param1\": \"avneesh@akto.io\", \"param2\": \"ankush\"}";
        HttpRequestParams httpRequestParams1 = buildHttpReq("/api/books?param3=ankita", apiInfoKey1.getMethod().name(), apiInfoKey1.getApiCollectionId(), payload1);
        TestPlugin.ContainsPrivateResourceResult result1 = bolaTest.containsPrivateResource(httpRequestParams1, apiInfoKey1);
        assertEquals(3, result1.singleTypeInfos.size());
        assertEquals(3, result1.findPrivateOnes().size());
        assertTrue(result1.isPrivate);

        // SECOND (Contains 2 public resources)
        ApiInfo.ApiInfoKey apiInfoKey2 = new ApiInfo.ApiInfoKey(123, "api/INTEGER/cars/STRING", URLMethods.Method.GET);

        insertIntoStiMap(apiInfoKey2,"1", SingleTypeInfo.INTEGER_32, true, true);
        insertIntoStiMap(apiInfoKey2,"3", SingleTypeInfo.GENERIC, true,false);
        insertIntoStiMap(apiInfoKey2,"param1", SingleTypeInfo.GENERIC, false, true);
        insertIntoStiMap(apiInfoKey2,"param2", SingleTypeInfo.GENERIC, false,false);
        String payload2 = "{\"param1\": \"Ronaldo\", \"param2\": \"Messi\"}";
        HttpRequestParams httpRequestParams2 = buildHttpReq("/api/INTEGER/cars/STRING", apiInfoKey2.getMethod().name(), apiInfoKey2.getApiCollectionId(), payload2);
        TestPlugin.ContainsPrivateResourceResult result2 = bolaTest.containsPrivateResource(httpRequestParams2, apiInfoKey2);
        assertEquals(4, result2.singleTypeInfos.size());
        assertFalse(result2.isPrivate);
        assertEquals(2, result2.findPrivateOnes().size());

        // THIRD (All missing) [We give missing STI benefit of doubt and consider it to be private]
        ApiInfo.ApiInfoKey apiInfoKey3 = new ApiInfo.ApiInfoKey(123, "/api/bus", URLMethods.Method.GET);

        String payload3 = "{\"param1\": \"Ronaldo\", \"param2\": \"Messi\"}";
        HttpRequestParams httpRequestParams3 = buildHttpReq("/api/bus", apiInfoKey3.getMethod().name(), apiInfoKey3.getApiCollectionId(), payload3);

        TestPlugin.ContainsPrivateResourceResult result3 = bolaTest.containsPrivateResource(httpRequestParams3, apiInfoKey3);
        assertEquals(0, result3.singleTypeInfos.size());
        assertTrue(result3.isPrivate);
        assertEquals(0, result3.findPrivateOnes().size());

        // FOURTH (Empty payload)
        ApiInfo.ApiInfoKey apiInfoKey4 = new ApiInfo.ApiInfoKey(123, "/api/toys", URLMethods.Method.GET);

        String payload4 = "{}";
        HttpRequestParams httpRequestParams4 = buildHttpReq("/api/toys", apiInfoKey4.getMethod().name(), apiInfoKey4.getApiCollectionId(), payload4);

        TestPlugin.ContainsPrivateResourceResult result4 = bolaTest.containsPrivateResource(httpRequestParams4, apiInfoKey4);
        assertEquals(0, result4.singleTypeInfos.size());
        assertFalse(result4.isPrivate);
        assertEquals(0, result4.findPrivateOnes().size());

    }

    private HttpRequestParams buildHttpReq(String url, String method, int apiCollectionId, String payload) {
        return new HttpRequestParams(
                method, url, "", new HashMap<>(), payload, apiCollectionId
        );
    }

    private void insertIntoStiMap(ApiInfo.ApiInfoKey apiInfoKey, String param, SingleTypeInfo.SubType subType, boolean isUrlParam, boolean isPrivate)  {
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
            singleTypeInfo.setUniqueCount(5);
        }

        SampleMessageStore.singleTypeInfos.put(singleTypeInfo.composeKeyWithCustomSubType(SingleTypeInfo.GENERIC), singleTypeInfo);
    }
}
