package com.akto.action.testing;

import com.akto.action.UserAction;
import com.akto.dao.AuthMechanismsDao;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dto.testing.*;
import com.akto.dto.ApiInfo;
import com.akto.dto.type.URLMethods.Method;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;


public class AuthMechanismAction extends UserAction {

    private AuthParam.Location location;
    private String key;
    private String value;

    public String addAuthMechanism() {
        List<AuthParam> authParams = new ArrayList<>();
        if (location == null || key == null || value == null) {
            addActionError("Location, Key or Value can't be empty");
            return ERROR.toUpperCase();
        }
        AuthMechanismsDao.instance.deleteAll(new BasicDBObject());
        authParams.add(new HardcodedAuthParam(location, key, value));
        AuthMechanism authMechanism = new AuthMechanism(authParams);

        AuthMechanismsDao.instance.insertOne(authMechanism);
        return SUCCESS.toUpperCase();
    }

    List<TestingRunResult> testingRunResults;
    public String fetchTestingRunResults() {
        BasicDBObject query = new BasicDBObject("testingEndpoints.type", "COLLECTION_WISE");
        List<TestingRun> testingRuns = TestingRunDao.instance.findAll(query);
        Map<Integer, TestingRun> tests = new HashMap<>();
        Set<ObjectId> testRunIdSet = new HashSet<>();

        for(TestingRun test : testingRuns) {
            TestingEndpoints testingEndpoints = test.getTestingEndpoints();
            if (testingEndpoints instanceof CollectionWiseTestingEndpoints) {
                CollectionWiseTestingEndpoints collectionWiseEndpoints = (CollectionWiseTestingEndpoints) testingEndpoints;
                
                int key = collectionWiseEndpoints.getApiCollectionId();
                TestingRun testingRunForCollection = tests.get(key);
                if (testingRunForCollection == null) {
                    tests.put(key, test);
                    testRunIdSet.add(test.getId());
                } else if (testingRunForCollection.getScheduleTimestamp() > test.getScheduleTimestamp()) {
                    tests.put(key, test);
                    testRunIdSet.add(test.getId());
                    testRunIdSet.remove(testingRunForCollection.getId());
                }
            }
        }


        Bson filter = Filters.in("testRunId", Arrays.asList(testRunIdSet));

        this.testingRunResults = TestingRunResultDao.instance.findAll(filter);

        addDummyResults(testRunIdSet);

        for(TestingRunResult testingRunResult: this.testingRunResults) {
            Map<String, TestResult> testResultMap = testingRunResult.getResultMap();
            if (testResultMap != null) {
                for(TestResult testResult: testResultMap.values()) {
                    testResult.setMessage("");
                }
            }
        }

        return SUCCESS.toUpperCase();
    }

    public void addDummyResults(Set<ObjectId> testIds) {
        
        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(0, "/api/board", Method.POST);
        Map<String, TestResult> testResults = new HashMap<String, TestResult>();
        testResults.put("NoAuthTest", new TestResult("sadfadsfasf", true, new ArrayList<>()));
        testResults.put("DiffAuthTest", new TestResult("mnm", false, new ArrayList<>()));
        testResults.put("OtherAuthTest", new TestResult("sadfacvbcvbdsfasf", true, new ArrayList<>()));
        TestingRunResult testingRunResult = new TestingRunResult(testIds.iterator().next(), apiInfoKey, testResults);
        testingRunResult.setId(testIds.iterator().next());
        this.testingRunResults.add(testingRunResult);


        ApiInfo.ApiInfoKey apiInfoKey2 = new ApiInfo.ApiInfoKey(0, "/api/fetchDataFromMySQL", Method.POST);
        Map<String, TestResult> testResults2 = new HashMap<String, TestResult>();
        testResults2.put("NoAuthTest", new TestResult("ksdjhfksd", true, new ArrayList<>()));
        testResults2.put("DiffAuthTest", new TestResult("mnm", true, new ArrayList<>()));
        testResults2.put("OtherAuthTest", new TestResult("sadfacvbcvbdsfasf", true, new ArrayList<>()));
        testResults2.put("YetOtherAuthTest", new TestResult("sadfacvbcvbdsfasf", true, new ArrayList<>()));
        TestingRunResult testingRunResult2 = new TestingRunResult(testIds.iterator().next(), apiInfoKey2, testResults2);
        testingRunResult2.setId(testIds.iterator().next());
        this.testingRunResults.add(testingRunResult2);
    }



    public AuthParam.Location getLocation() {
        return this.location;
    }

    public String getKey() {
        return this.key;
    }

    public String getValue() {
        return this.value;
    }

    public List<TestingRunResult> getTestingRunResults() {
        return this.testingRunResults;
    }

    public void setTestingRunResults(List<TestingRunResult> testingRunResults) {
        this.testingRunResults = testingRunResults;
    }

    public void setLocation(AuthParam.Location location) {
        this.location = location;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
