package com.akto.dao;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;
import org.junit.Test;

import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.TestResult.Confidence;
import com.akto.dto.type.URLMethods.Method;
import com.akto.utils.MongoBasedTest;
import com.mongodb.BasicDBObject;

public class TestTestingRunResultDao extends MongoBasedTest {
    @Test
    public void testNaN() {
        TestingRunResultDao.instance.getMCollection().drop();

        String message = "{}";

        double percentageMatch1a = 100.0/0;
        TestResult testResult1a = new TestResult(message, message,true, new ArrayList<>(), new ArrayList<>(), percentageMatch1a, Confidence.HIGH);

        double percentageMatch1b = 22.3;
        TestResult testResult1b = new TestResult(message, message,true, new ArrayList<>(), new ArrayList<>(), percentageMatch1b, Confidence.HIGH);
        Map<String, TestResult> testResultMap1 = new HashMap<>();

        testResultMap1.put("one", testResult1a);
        testResultMap1.put("two", testResult1b);

        TestingRunResult testingRunResult1 = new TestingRunResult(new ObjectId(), new ApiInfoKey(0, "url1", Method.GET), testResultMap1 );

        double percentageMatch2a = Double.NaN;
        TestResult testResult2a = new TestResult(message, message,true, new ArrayList<>(), new ArrayList<>(), percentageMatch2a, Confidence.HIGH);

        double percentageMatch2b = Double.POSITIVE_INFINITY;
        TestResult testResult2b = new TestResult(message, message,true, new ArrayList<>(), new ArrayList<>(), percentageMatch2b, Confidence.HIGH);
        Map<String, TestResult> testResultMap2 = new HashMap<>();

        testResultMap2.put("three", testResult2a);
        testResultMap2.put("four", testResult2b);

        TestingRunResult testingRunResult2 = new TestingRunResult(new ObjectId(), new ApiInfoKey(1, "url1", Method.GET), testResultMap2 );

        TestingRunResultDao.instance.insertMany(Arrays.asList(testingRunResult1, testingRunResult2));


        List<TestingRunResult> results = TestingRunResultDao.instance.findAll(new BasicDBObject());

        for (TestingRunResult testingRunResult: results) {
            for (String key: testingRunResult.getResultMap().keySet()) {
                TestResult testResult = testingRunResult.getResultMap().get(key);
                if (key.equals("two")) {
                    assertEquals(percentageMatch1b, testResult.getPercentageMatch(), 0.0);
                } else {
                    assertEquals(0.0, testResult.getPercentageMatch(), 0.0);
                }
            }
        }

    }
}