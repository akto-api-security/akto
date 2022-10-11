package com.akto.dto.testing;

import com.akto.dto.ApiInfo;

import com.akto.dto.type.SingleTypeInfo;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.Map;

public class TestingRunResult {
    private ObjectId id;
    @BsonIgnore
    private String hexId;
    public static final String TEST_RUN_ID = "testRunId";
    private ObjectId testRunId;
    public static final String API_INFO_KEY = "apiInfoKey";
    private ApiInfo.ApiInfoKey apiInfoKey;
    private String testSuperType;
    private String testSubType;
    private List<TestResult> testResults;
    private boolean isVulnerable;
    private List<SingleTypeInfo> singleTypeInfos;
    private int confidencePercentage;

    private int startTimestamp;
    private int endTimestamp;
    private ObjectId testRunResultSummaryId;

    @BsonIgnore
    private ObjectId testRunResultSummaryHexId;

    public TestingRunResult() { }

    public TestingRunResult(ObjectId testRunId, ApiInfo.ApiInfoKey apiInfoKey, String testSuperType, String testSubType,
                            List<TestResult> testResults, boolean isVulnerable, List<SingleTypeInfo> singleTypeInfos,
                            int confidencePercentage, int startTimestamp, int endTimestamp, ObjectId testRunResultSummaryId) {
        this.testRunId = testRunId;
        this.apiInfoKey = apiInfoKey;
        this.testSuperType = testSuperType;
        this.testSubType = testSubType;
        this.testResults = testResults;
        this.isVulnerable = isVulnerable;
        this.singleTypeInfos = singleTypeInfos;
        this.confidencePercentage = confidencePercentage;
        this.startTimestamp = startTimestamp;
        this.endTimestamp = endTimestamp;
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

    public String getTestSuperType() {
        return testSuperType;
    }

    public void setTestSuperType(String testSuperType) {
        this.testSuperType = testSuperType;
    }

    public String getTestSubType() {
        return testSubType;
    }

    public void setTestSubType(String testSubType) {
        this.testSubType = testSubType;
    }

    public List<TestResult> getTestResults() {
        return testResults;
    }

    public void setTestResults(List<TestResult> testResults) {
        this.testResults = testResults;
    }

    public boolean isVulnerable() {
        return isVulnerable;
    }

    public void setVulnerable(boolean vulnerable) {
        isVulnerable = vulnerable;
    }

    public List<SingleTypeInfo> getSingleTypeInfos() {
        return singleTypeInfos;
    }

    public void setSingleTypeInfos(List<SingleTypeInfo> singleTypeInfos) {
        this.singleTypeInfos = singleTypeInfos;
    }

    public int getConfidencePercentage() {
        return confidencePercentage;
    }

    public void setConfidencePercentage(int confidencePercentage) {
        this.confidencePercentage = confidencePercentage;
    }

    @Override
    public String toString() {
        return "TestingRunResult{" +
                "id=" + id +
                ", testRunId=" + testRunId +
                ", apiInfoKey=" + apiInfoKey +
                ", testSuperType='" + testSuperType + '\'' +
                ", testSubType='" + testSubType + '\'' +
                ", isVulnerable=" + isVulnerable +
                ", confidencePercentage=" + confidencePercentage +
                ", startTimestamp=" + startTimestamp +
                ", endTimestamp=" + endTimestamp +
                ", testRunResultSummaryId=" + testRunResultSummaryId +
                '}';
    }
}
