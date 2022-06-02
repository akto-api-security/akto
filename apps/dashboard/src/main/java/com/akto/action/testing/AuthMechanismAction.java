package com.akto.action.testing;

import com.akto.action.UserAction;
import com.akto.dao.AuthMechanismsDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dto.testing.*;
import com.akto.dto.ApiInfo;
import com.akto.dto.traffic.SampleData;
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

    public String fetchRequestAndResponseForTest() {

        Set<ObjectId> testRunResultIds = new HashSet<>();

        for(TestingRunResult testingRunResult: this.testingRunResults) {
            testRunResultIds.add(testingRunResult.getId());
        }

        this.testingRunResults = TestingRunResultDao.instance.findAll(Filters.in("_id", testRunResultIds));

        this.addDummyResults(testRunResultIds);

        return SUCCESS.toUpperCase();
    }

    public void addDummyResults(Set<ObjectId> testIds) {
        
        SampleData sampleData = new SampleData();
        sampleData.setSamples(Arrays.asList( "{\"method\":\"GET\",\"requestPayload\":\"{}\",\"responsePayload\":\"{\\\"id\\\":2,\\\"name\\\":\\\"purplefieldstester1\\\",\\\"photoUrls\\\":[\\\"http://purplefieldstestimage1\\\",\\\"http://purplefieldstestimage2\\\"],\\\"tags\\\":[],\\\"status\\\":\\\"available\\\"}\",\"ip\":\"null\",\"source\":\"HAR\",\"type\":\"HTTP/2\",\"akto_vxlan_id\":\"0\",\"path\":\"https://petstore.swagger.io/v2/pet/2\",\"requestHeaders\":\"{\\\"TE\\\":\\\"trailers\\\",\\\"Accept\\\":\\\"application/json\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\\\",\\\"Referer\\\":\\\"https://petstore.swagger.io/\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"petstore.swagger.io\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.5\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\"}\",\"responseHeaders\":\"{\\\"date\\\":\\\"Mon, 03 Jan 2022 07:16:48 GMT\\\",\\\"access-control-allow-origin\\\":\\\"*\\\",\\\"server\\\":\\\"Jetty(9.2.9.v20150224)\\\",\\\"access-control-allow-headers\\\":\\\"Content-Type, api_key, Authorization\\\",\\\"X-Firefox-Spdy\\\":\\\"h2\\\",\\\"content-type\\\":\\\"application/json\\\",\\\"access-control-allow-methods\\\":\\\"GET, POST, DELETE, PUT\\\"}\",\"time\":\"1641194208\",\"contentType\":\"application/json\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}"));

        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(0, "/api/board", Method.POST);
        Map<String, TestResult> testResults = new HashMap<String, TestResult>();
        testResults.put("NoAuthTest", new TestResult(sampleData.getSamples().get(0), true, new ArrayList<>()));
        testResults.put("DiffAuthTest", new TestResult(sampleData.getSamples().get(0), false, new ArrayList<>()));
        testResults.put("OtherAuthTest", new TestResult(sampleData.getSamples().get(0), true, new ArrayList<>()));
        TestingRunResult testingRunResult = new TestingRunResult(testIds.iterator().next(), apiInfoKey, testResults);
        testingRunResult.setId(testIds.iterator().next());
        this.testingRunResults.add(testingRunResult);


        ApiInfo.ApiInfoKey apiInfoKey2 = new ApiInfo.ApiInfoKey(0, "/api/fetchDataFromMySQL", Method.POST);
        Map<String, TestResult> testResults2 = new HashMap<String, TestResult>();
        testResults2.put("NoAuthTest", new TestResult(sampleData.getSamples().get(0), true, new ArrayList<>()));
        testResults2.put("DiffAuthTest", new TestResult(sampleData.getSamples().get(0), true, new ArrayList<>()));
        testResults2.put("OtherAuthTest", new TestResult(sampleData.getSamples().get(0), true, new ArrayList<>()));
        testResults2.put("YetOtherAuthTest", new TestResult(sampleData.getSamples().get(0), true, new ArrayList<>()));
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
