package com.akto.dto.testing.info;

import java.util.List;

import org.bson.types.ObjectId;

import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.testing.TestingRunResult;

public class SingleTestPayload {

    private ObjectId testingRunId;
    private ObjectId testingRunResultSummaryId;
    private ApiInfoKey apiInfoKey;
    private String subcategory;
    private List<TestingRunResult.TestLog> testLogs;
    private int accountId;

    public SingleTestPayload (ObjectId testingRunId, ObjectId testingRunResultSummaryId, ApiInfoKey apiInfoKey, String subcategory, List<TestingRunResult.TestLog> testLogs, int accountId){
        this.testingRunId = testingRunId;
        this.testingRunResultSummaryId = testingRunResultSummaryId;
        this.apiInfoKey = apiInfoKey;
        this.subcategory = subcategory;
        this.testLogs = testLogs;
        this.accountId = accountId;
    }

    public ObjectId getTestingRunId() {
        return testingRunId;
    }
    public void setTestingRunId(ObjectId testingRunId) {
        this.testingRunId = testingRunId;
    }

    public ObjectId getTestingRunResultSummaryId() {
        return testingRunResultSummaryId;
    }
    public void setTestingRunResultSummaryId(ObjectId testingRunResultSummaryId) {
        this.testingRunResultSummaryId = testingRunResultSummaryId;
    }

    public ApiInfoKey getApiInfoKey() {
        return apiInfoKey;
    }
    public void setApiInfoKey(ApiInfoKey apiInfoKey) {
        this.apiInfoKey = apiInfoKey;
    }

    public String getSubcategory() {
        return subcategory;
    }
    public void setSubcategory(String subcategory) {
        this.subcategory = subcategory;
    }

    public List<TestingRunResult.TestLog> getTestLogs() {
        return testLogs;
    }

    public void setTestLogs(List<TestingRunResult.TestLog> testLogs) {
        this.testLogs = testLogs;
    }

    public int getAccountId() {
        return accountId;
    }
    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }

    @Override 
    public String toString() {
        return "{" +
            "\"testingRunId\":\"" + (testingRunId != null ? testingRunId.toHexString() : null) + "\"," +
            "\"testingRunResultSummaryId\":\"" + (testingRunResultSummaryId != null ? testingRunResultSummaryId.toHexString() : null) + "\"," +
            "\"apiInfoKey\":" + (apiInfoKey != null ? "\"" + apiInfoKey.toString() + "\"" : null) + "," +
            "\"subcategory\":\"" + (subcategory != null ? subcategory : null) + "\"," +
            "\"testLogs\":" + (testLogs != null ? testLogs.toString() : null) + "," +
            "\"accountId\":" + accountId + "," +
        "}";
    }
}
