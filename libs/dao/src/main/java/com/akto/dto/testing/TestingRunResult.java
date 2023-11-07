package com.akto.dto.testing;

import com.akto.dto.ApiInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.util.ColorConstants;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.Map;

public class TestingRunResult implements Comparable<TestingRunResult> {
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
    public static final String VULNERABLE = "vulnerable";
    private boolean vulnerable;
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
                            List<TestResult> testResults, boolean vulnerable, List<SingleTypeInfo> singleTypeInfos,
                            int confidencePercentage, int startTimestamp, int endTimestamp, ObjectId testRunResultSummaryId) {
        this.testRunId = testRunId;
        this.apiInfoKey = apiInfoKey;
        this.testSuperType = testSuperType;
        this.testSubType = testSubType;
        this.testResults = testResults;
        this.vulnerable = vulnerable;
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
        return vulnerable;
    }

    public void setVulnerable(boolean vulnerable) {
        this.vulnerable = vulnerable;
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
                ", isVulnerable=" + vulnerable +
                ", confidencePercentage=" + confidencePercentage +
                ", startTimestamp=" + startTimestamp +
                ", endTimestamp=" + endTimestamp +
                ", testRunResultSummaryId=" + testRunResultSummaryId +
                '}';
    }

    public String toConsoleString(String severity) {

        return 
         ColorConstants.BLUE + "API: " + apiInfoKey.getUrl() + " " + apiInfoKey.getMethod().toString() + "\n" +
         ColorConstants.PURPLE + "Test: " + testSuperType + " " + testSubType + " " +
         (vulnerable ? ColorConstants.RED : ColorConstants.GREEN) + "Vulnerable: " + vulnerable + 
         (vulnerable ? ColorConstants.CYAN + " Severity : " + severity : "") + 
         "\n" + ColorConstants.RESET;
    }

    public String toOutputString(String severity){
        StringBuilder bld = new StringBuilder();

        bld.append("API: " + apiInfoKey.getUrl() + " " + apiInfoKey.getMethod().toString() + "\n");
        bld.append("Test: " + testSuperType + " " + testSubType + " Vulnerable: " + vulnerable +
        (vulnerable ? " Severity : " + severity : "") + "\n");
        for (TestResult testResult : testResults) {
            Gson gson = new Gson();
            Map<String, Object> json = gson.fromJson(testResult.getOriginalMessage(), new TypeToken<Map<String, Object>>(){}.getType());
            try {
                bld.append("Original request : " + json.get("requestHeaders") + "\n" + json.get("requestPayload") + "\n");
                bld.append("Original response: " + json.get("responseHeaders") + "\n" + json.get("responsePayload") + "\n");
            } catch (Exception e){
                bld.append("Original data not found\n");
            }
            try {
                json = gson.fromJson(testResult.getMessage(), new TypeToken<Map<String, Object>>(){}.getType());
                bld.append("\nAttempted request : " + json.get("request") + "\n");
                bld.append("\nAttempted response: " + json.get("response") + "\n");
            } catch (Exception e) {
                int c = 1;
                bld.append("Attempted errors : \n");
                for (String error : testResult.getErrors()) {
                    bld.append("Error " + c + ": " + error + "\n");
                    c++;
                }
            }
        }
        bld.append("\n");

        return bld.toString();
    }

    @Override
    public int compareTo(TestingRunResult o) {

        TestingRunResult that = o;

        if (this.isVulnerable() != that.isVulnerable()) {
            return this.isVulnerable() ? -1 : 1;
        }

        if (!this.getTestSubType().equalsIgnoreCase(that.getTestSubType())) {
            return this.getTestSubType().compareToIgnoreCase(that.getTestSubType());
        }

        if (!this.getApiInfoKey().getUrl().equalsIgnoreCase(that.getApiInfoKey().getUrl())) {
            return this.getApiInfoKey().getUrl().compareToIgnoreCase(that.getApiInfoKey().getUrl());
        }

        return 0;
    }

}
