package com.akto.dto.testing;

import com.akto.dto.ApiInfo;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import java.util.Map;

public class TestingRunResult {
    private ObjectId id;
    @BsonIgnore
    private String hexId;
    public static final String TEST_RUN_ID = "testRunId";
    private ObjectId testRunId;
    public static final String API_INFO_KEY = "apiInfoKey";
    private ApiInfo.ApiInfoKey apiInfoKey;
    private Map<String, TestResult> resultMap;

    // remove Map. Use "String" as test-supertype. 
    // introduce testSubtype
    // List<TestResult>
    // boolean isVulnerable
    // List<SingleTypeInfo>
    // int confidencePercentage
    private int startTimestamp;
    private int endTimestamp;
    private ObjectId testRunResultSummaryId;

    @BsonIgnore
    private ObjectId testRunResultSummaryHexId;

    public TestingRunResult() { }

    public TestingRunResult(ObjectId testRunId, ApiInfo.ApiInfoKey apiInfoKey, Map<String, TestResult> resultMap, ObjectId testRunResultSummaryId) {
        this.testRunId = testRunId;
        this.apiInfoKey = apiInfoKey;
        this.resultMap = resultMap;
        this.testRunResultSummaryId = testRunResultSummaryId;
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


    public String getHexId() {
        return this.hexId;
    }

    public void setHexId(String hexId) {
        this.hexId = hexId;
    }

    public int getStartTimestamp() {
        return this.startTimestamp;
    }

    public void setStartTimestamp(int startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public int getEndTimestamp() {
        return this.endTimestamp;
    }

    public void setEndTimestamp(int endTimestamp) {
        this.endTimestamp = endTimestamp;
    }

    public ObjectId getTestRunResultSummaryId() {
        return this.testRunResultSummaryId;
    }

    public void setTestRunResultSummaryId(ObjectId testRunResultSummaryId) {
        this.testRunResultSummaryId = testRunResultSummaryId;
    }

    public ObjectId getTestRunResultSummaryHexId() {
        return this.testRunResultSummaryHexId;
    }

    public void setTestRunResultSummaryHexId(ObjectId testRunResultSummaryHexId) {
        this.testRunResultSummaryHexId = testRunResultSummaryHexId;
    }

    @Override
    public String toString() {
        return "{" +
            " id='" + getId() + "'" +
            ", testRunId='" + getTestRunId() + "'" +
            ", apiInfoKey='" + getApiInfoKey() + "'" +
            ", resultMap='" + getResultMap() + "'" +
            ", startTimestamp='" + getStartTimestamp() + "'" +
            ", endTimestamp='" + getEndTimestamp() + "'" +
            ", testRunResultSummaryId='" + getTestRunResultSummaryId() + "'" +
            "}";
    }

}
