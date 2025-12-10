package com.akto.dto.testing;

import java.util.Map;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

public class TestingRunResultSummary {
    
    public static final String ID = "_id";
    public static final String START_TIMESTAMP = "startTimestamp";    
    public static final String END_TIMESTAMP = "endTimestamp";    
    public static final String COUNT_ISSUES = "countIssues";    
    public static final String TOTAL_APIS = "totalApis";    
    public static final String TESTING_RUN_ID = "testingRunId";    
    public static final String STATE = "state";    
    public static final String TEST_RESULTS_COUNT = "testResultsCount";
    public static final String METADATA_STRING = "metadata";
    public static final String TESTS_INITIATED_COUNT = "testInitiatedCount";

    private ObjectId id;
    private int startTimestamp;
    private int endTimestamp;
    private Map<String, Integer> countIssues;
    private int totalApis;
    private ObjectId testingRunId;
    @BsonIgnore
    private String testingRunHexId;

    private TestingRun.State state; 
    private int testResultsCount;
    private int testInitiatedCount;

    private int testIdConfig;
    /*
    * originalTestingRunResultSummaryId this will be used to trigger running testingRunResults
    *
    * */
    private ObjectId originalTestingRunResultSummaryId;
    public static final String ORIGINAL_TESTING_RUN_SUMMARY_ID = "originalTestingRunResultSummaryId";
    public static final String IS_NEW_TESTING_RUN_RESULT_SUMMARY = "newTestingSummary";
    public static final String IS_NEW_TESTING_RUN_RESULT_SUMMARY_OLD = "isNewTestingSummary";
    private boolean newTestingSummary = true;

    @BsonIgnore
    private String hexId;

    private Map<String,String> metadata;

    public TestingRunResultSummary() {
    }

    public TestingRunResultSummary(int startTimestamp, int endTimestamp, Map<String,Integer> countIssues, int totalApis,
                                   ObjectId testingRunId, String testingRunHexId, int testResultsCount, int testIdConfig, int testInitiatedCount) {
        this.startTimestamp = startTimestamp;
        this.endTimestamp = endTimestamp;
        this.countIssues = countIssues;
        this.totalApis = totalApis;
        this.testingRunId = testingRunId;
        this.testingRunHexId = testingRunHexId;
        this.state = TestingRun.State.RUNNING;
        this.testResultsCount = testResultsCount;
        this.testIdConfig = testIdConfig;
        this.testInitiatedCount = testInitiatedCount;
        this.newTestingSummary = true;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
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

    public Map<String,Integer> getCountIssues() {
        return this.countIssues;
    }

    public void setCountIssues(Map<String,Integer> countIssues) {
        this.countIssues = countIssues;
    }

    public int getTotalApis() {
        return this.totalApis;
    }

    public void setTotalApis(int totalApis) {
        this.totalApis = totalApis;
    }

    public ObjectId getTestingRunId() {
        return this.testingRunId;
    }

    public void setTestingRunId(ObjectId testingRunId) {
        this.testingRunId = testingRunId;
    }

    public String getTestingRunHexId() {
        return this.testingRunHexId;
    }

    public void setTestingRunHexId(String testingRunHexId) {
        this.testingRunHexId = testingRunHexId;
    }

    public TestingRun.State getState() {
        return this.state;
    }

    public void setState(TestingRun.State state) {
        this.state = state;
    }

    public String getHexId() {
        return this.id.toHexString();
    }

    public int getTestResultsCount() {
        return testResultsCount;
    }

    public void setTestResultsCount(int testResultsCount) {
        this.testResultsCount = testResultsCount;
    }


    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public int getTestIdConfig() {
        return testIdConfig;
    }

    public void setTestIdConfig(int testIdConfig) {
        this.testIdConfig = testIdConfig;
    }

    public int getTestInitiatedCount() {
        return testInitiatedCount;
    }

    public void setTestInitiatedCount(int testInitiatedCount) {
        this.testInitiatedCount = testInitiatedCount;
    }

    @Override
    public String toString() {
        return "{" +
            " startTimestamp='" + getStartTimestamp() + "'" +
            ", endTimestamp='" + getEndTimestamp() + "'" +
            ", countIssues='" + getCountIssues() + "'" +
            ", totalApis='" + getTotalApis() + "'" +
            ", testIdConfig='" + getTestIdConfig() + "'" +
            ", testingRunId='" + getTestingRunId() + "'" +
            ", testingRunHexId='" + getTestingRunHexId() + "'" +
            ", state='" + getState() + "'" +
            ", metadata='" + getMetadata().toString() + "'" +
            ", testInitiatedCount='" + getTestInitiatedCount() + "'" +
            "}";
    }

    public boolean getNewTestingSummary() {
        return newTestingSummary;
    }

    public void setNewTestingSummary(boolean newTestingSummary) {
        this.newTestingSummary = newTestingSummary;
    }

    public ObjectId getOriginalTestingRunResultSummaryId() {
        return originalTestingRunResultSummaryId;
    }

    public void setOriginalTestingRunResultSummaryId(ObjectId originalTestingRunResultSummaryId) {
        this.originalTestingRunResultSummaryId = originalTestingRunResultSummaryId;
    }
}
