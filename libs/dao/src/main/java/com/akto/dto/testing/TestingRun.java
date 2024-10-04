package com.akto.dto.testing;

import com.akto.dto.ApiCollection;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

public class TestingRun {

    private ObjectId id;
    public static final String SCHEDULE_TIMESTAMP = "scheduleTimestamp";
    private int scheduleTimestamp;
    public static final String PICKED_UP_TIMESTAMP = "pickedUpTimestamp";
    private int pickedUpTimestamp;
    public static final String END_TIMESTAMP = "endTimestamp";
    private int endTimestamp;
    public static final String STATE = "state";
    private State state;
    private String userEmail;
    public static final String _TESTING_ENDPOINTS = "testingEndpoints";
    private TestingEndpoints testingEndpoints;
    private int testIdConfig;
    public static final String PERIOD_IN_SECONDS = "periodInSeconds";
    private int periodInSeconds;
    private int testRunTime;
    private int maxConcurrentRequests;
    private String triggeredBy;
    public static final String TRIGGERED_BY = "triggeredBy";

    public static final String _API_COLLECTION_ID = "testingEndpoints.apiCollectionId";
    public static final String _API_COLLECTION_ID_IN_LIST = "testingEndpoints.apisList.apiCollectionId";

    @BsonIgnore
    private String hexId;
    @BsonIgnore
    private TestingRunConfig testingRunConfig;

    public static final String NAME = "name";
    private String name;

    public enum TestingRunType{
        ONE_TIME, RECURRING, CI_CD, CONTINUOUS_TESTING
    }

    public TestingRun() { }

    public TestingRun(int scheduleTimestamp, String userEmail, TestingEndpoints testingEndpoints, int testIdConfig, State state, int periodInSeconds, String name, String triggeredBy) {
        this.scheduleTimestamp = scheduleTimestamp;
        this.testRunTime = -1;
        this.maxConcurrentRequests = -1;
        this.endTimestamp = -1;
        this.pickedUpTimestamp = -1;
        this.userEmail = userEmail;
        this.testingEndpoints = testingEndpoints;
        this.testIdConfig = testIdConfig;
        this.state = state;
        this.periodInSeconds = periodInSeconds;
        this.name = name;
        this.triggeredBy = triggeredBy;
    }
    public TestingRun(int scheduleTimestamp, String userEmail, TestingEndpoints testingEndpoints, int testIdConfig, State state, int periodInSeconds, String name, int testRunTime, int maxConcurrentRequests) {
        this.scheduleTimestamp = scheduleTimestamp;
        this.testRunTime = testRunTime;
        this.maxConcurrentRequests = maxConcurrentRequests;
        this.endTimestamp = -1;
        this.pickedUpTimestamp = -1;
        this.userEmail = userEmail;
        this.testingEndpoints = testingEndpoints;
        this.testIdConfig = testIdConfig;
        this.state = state;
        this.periodInSeconds = periodInSeconds;
        this.name = name;
    }

    public TestingRunConfig getTestingRunConfig() {
        return testingRunConfig;
    }

    public void setTestingRunConfig(TestingRunConfig testingRunConfig) {
        this.testingRunConfig = testingRunConfig;
    }

    public int getTestRunTime() {
        return testRunTime;
    }

    public void setTestRunTime(int testRunTime) {
        this.testRunTime = testRunTime;
    }

    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }

    public void setMaxConcurrentRequests(int maxConcurrentRequests) {
        this.maxConcurrentRequests = maxConcurrentRequests;
    }

    // if u r adding anything here make sure to add to stopAllTests() method too
    public enum State {
        SCHEDULED, RUNNING, STOPPED, COMPLETED, FAILED
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public int getScheduleTimestamp() {
        return scheduleTimestamp;
    }

    public void setScheduleTimestamp(int scheduleTimestamp) {
        this.scheduleTimestamp = scheduleTimestamp;
    }

    public int getEndTimestamp() {
        return endTimestamp;
    }

    public void setEndTimestamp(int endTimestamp) {
        this.endTimestamp = endTimestamp;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public TestingEndpoints getTestingEndpoints() {
        return testingEndpoints;
    }

    public void setTestingEndpoints(TestingEndpoints testingEndpoints) {
        this.testingEndpoints = testingEndpoints;
    }

    public int getTestIdConfig() {
        return testIdConfig;
    }

    public void setTestIdConfig(int testIdConfig) {
        this.testIdConfig = testIdConfig;
    }

    public int getPickedUpTimestamp() {
        return pickedUpTimestamp;
    }

    public void setPickedUpTimestamp(int pickedUpTimestamp) {
        this.pickedUpTimestamp = pickedUpTimestamp;
    }

    public int getPeriodInSeconds() {
        return this.periodInSeconds;
    }

    public void setPeriodInSeconds(int periodInSeconds) {
        this.periodInSeconds = periodInSeconds;
    }

    public State getState() {
        return this.state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public String getHexId() {
        return this.id.toHexString();
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public void setTriggeredBy(String triggeredBy) {
        this.triggeredBy = triggeredBy;
    }

    @Override
    public String toString() {
        return "{" +
            " id='" + getId() + "'" +
            ", scheduleTimestamp='" + getScheduleTimestamp() + "'" +
            ", pickedUpTimestamp='" + getPickedUpTimestamp() + "'" +
            ", endTimestamp='" + getEndTimestamp() + "'" +
            ", state='" + getState() + "'" +
            ", userEmail='" + getUserEmail() + "'" +
            ", testingEndpoints='" + getTestingEndpoints() + "'" +
            ", testIdConfig='" + getTestIdConfig() + "'" +
            ", periodInSeconds='" + getPeriodInSeconds() + "'" +
            ", name='" + getName() + "'" +
            "}";
    }

    public static String findTestType(TestingRun testingRun, TestingRunResultSummary trrs) {
        String testType = "ONE_TIME";
        if(testingRun.getPeriodInSeconds()>0) testType = "SCHEDULED DAILY";
        if (trrs.getMetadata() != null) testType = "CI_CD";
        return testType;
    }



}
