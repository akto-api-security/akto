package com.akto.rules;

import com.akto.MongoBasedTest;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.type.URLMethods;
import com.mongodb.BasicDBObject;
import org.bson.types.ObjectId;
import org.junit.Test;

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
}
