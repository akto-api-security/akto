package com.akto.dto.testing;

import com.akto.dto.ApiInfo;
import org.bson.types.ObjectId;

import java.util.Map;

public class TestingRunResult {
    private ObjectId id;
    public static final String TEST_RUN_ID = "testRunId";
    private ObjectId testRunId;
    public static final String API_INFO_KEY = "apiInfoKey";
    private ApiInfo.ApiInfoKey apiInfoKey;
    private Map<String, TestResult> resultMap;

    public TestingRunResult() { }

    public TestingRunResult(ObjectId testRunId, ApiInfo.ApiInfoKey apiInfoKey, Map<String, TestResult> resultMap) {
        this.testRunId = testRunId;
        this.apiInfoKey = apiInfoKey;
        this.resultMap = resultMap;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public ApiInfo.ApiInfoKey getApiInfoKey() {
        return apiInfoKey;
    }

    public void setApiInfoKey(ApiInfo.ApiInfoKey apiInfoKey) {
        this.apiInfoKey = apiInfoKey;
    }

    public ObjectId getTestRunId() {
        return testRunId;
    }

    public void setTestRunId(ObjectId testRunId) {
        this.testRunId = testRunId;
    }

    public Map<String, TestResult> getResultMap() {
        return resultMap;
    }

    public void setResultMap(Map<String, TestResult> resultMap) {
        this.resultMap = resultMap;
    }


    @Override
    public String toString() {
        return "{" +
            " id='" + getId() + "'" +
            ", testRunId='" + getTestRunId() + "'" +
            ", apiInfoKey='" + getApiInfoKey() + "'" +
            ", resultMap='" + getResultMap() + "'" +
            "}";
    }

}
