package com.akto.dto.testing.info;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;

import com.akto.dao.context.Context;
import com.akto.dto.testing.GenericTestResult;
import com.akto.dto.testing.MultiExecTestResult;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;

public class SingleTestResultPayload {
    private TestingRunResult testingRunResult;
    private int accountId;

    private final static ObjectMapper mapper = new ObjectMapper();

    public SingleTestResultPayload(TestingRunResult testingRunResult, int accountId) {
        this.testingRunResult = testingRunResult;
        this.accountId = accountId;
    }

    public SingleTestResultPayload() {
    }


    public TestingRunResult getTestingRunResult() {
        return testingRunResult;
    }
    public void setTestingRunResult(TestingRunResult testingRunResult) {
        this.testingRunResult = testingRunResult;
    }
    public int getAccountId() {
        return accountId;
    }
    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }

    @Override 
    public String toString() {
        this.getTestingRunResult().setId(null);
        this.getTestingRunResult().setHexId("");
        this.getTestingRunResult().setTestRunHexId(this.getTestingRunResult().getTestRunId().toHexString());
        this.getTestingRunResult().setTestRunResultSummaryHexId(this.getTestingRunResult().getTestRunResultSummaryId().toHexString());
        this.getTestingRunResult().setTestRunResultSummaryId(null);
        this.getTestingRunResult().setTestRunId(null);
        try {
            return mapper.writeValueAsString(this);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
        
    }

    public static SingleTestResultPayload getTestingRunResultFromMessage(String message){
        try {
            BasicDBObject object = mapper.readValue(message, BasicDBObject.class);
            Object testingRunResultObj = object.get("testingRunResult");
            int accountId = object.getInt("accountId");
            if (testingRunResultObj instanceof Map) {
                BasicDBObject testingRunResult = new BasicDBObject((Map<String, Object>) testingRunResultObj);
                object.put("testingRunResult", testingRunResult);
            }
            BasicDBObject bdObject = (BasicDBObject) object.get("testingRunResult");
            List<GenericTestResult> testResults = new ArrayList<>();
            if(bdObject.get("singleTestResults") !=null ){
                List<TestResult> trList = (List<TestResult>) bdObject.get("singleTestResults");
                testResults = new ArrayList<>(trList);
                bdObject.put("singleTestResults", null);
            }else if(bdObject.get("multiExecTestResults") != null){
                List<MultiExecTestResult> trList = (List<MultiExecTestResult>) bdObject.get("multiExecTestResults");
                testResults = new ArrayList<>(trList);
                bdObject.put("multiExecTestResults", null);
            }
            
            TestingRunResult testingRunResult = mapper.convertValue(bdObject, TestingRunResult.class);


            if (testingRunResult.getTestRunHexId() != null) {
                ObjectId id = new ObjectId(testingRunResult.getTestRunHexId());
                testingRunResult.setTestRunId(id);
            }

            
            if (testingRunResult.getTestRunResultSummaryHexId() != null) {
                ObjectId id = new ObjectId(testingRunResult.getTestRunResultSummaryHexId());
                testingRunResult.setTestRunResultSummaryId(id);
            }
            if(!testResults.isEmpty()){
                testingRunResult.setTestResults(testResults);
            }
            return new SingleTestResultPayload(testingRunResult, accountId);
           
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String getMessageString(TestingRunResult runResult){
        GenericTestResult testRes = runResult.getTestResults().get(0);
        if (testRes instanceof TestResult) {
            List<TestResult> list = new ArrayList<>();
            for(GenericTestResult testResult: runResult.getTestResults()){
                list.add((TestResult) testResult);
            }
            runResult.setSingleTestResults(list);
        } else {
            List<MultiExecTestResult> list = new ArrayList<>();
            for(GenericTestResult testResult: runResult.getTestResults()){
                list.add((MultiExecTestResult) testResult);
            }
            runResult.setMultiExecTestResults(list);
        }
        runResult.setTestResults(null);

        SingleTestResultPayload singleTestResultPayload = new SingleTestResultPayload(runResult,Context.accountId.get());
        String message = singleTestResultPayload.toString();
        return message;
    }
}
