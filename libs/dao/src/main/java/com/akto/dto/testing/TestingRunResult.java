package com.akto.dto.testing;

import com.akto.dto.ApiInfo;
import com.akto.dto.type.SingleTypeInfo;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import java.util.List;

public class TestingRunResult {
    private ObjectId id;
    @BsonIgnore
    private String hexId;

    public static final String TEST_RUN_ID = "testRunId";
    private ObjectId testRunId;
    public static final String API_INFO_KEY = "apiInfoKey";
    private ApiInfo.ApiInfoKey apiInfoKey;
    public static final String TEST_SUPER_TYPE = "testSuperType";
    private String testSuperType;
    public static final String TEST_SUB_TYPE = "testSubType";
    private String testSubType;
    public static final String TEST_RESULTS = "testResults";
    private List<TestResult> testResults;
    public static final String IS_VULNERABLE = "isVulnerable";
    private boolean isVulnerable;
    public static final String SINGLE_TYPE_INFOS = "singleTypeInfos";
    private List<SingleTypeInfo> singleTypeInfos;
    public static final String CONFIDENCE_PERCENTAGE = "confidencePercentage";
    private int confidencePercentage;

    public static final String START_TIMESTAMP = "startTimestamp";
    private int startTimestamp;
    public static final String END_TIMESTAMP = "endTimestamp";
    private int endTimestamp;
    public static final String TEST_RUN_RESULT_SUMMARY_ID = "testRunResultSummaryId";
    private ObjectId testRunResultSummaryId;

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
        if (hexId == null) return this.id.toHexString();
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
