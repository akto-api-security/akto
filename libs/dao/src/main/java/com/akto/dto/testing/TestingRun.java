package com.akto.dto.testing;

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
    private int periodInSeconds;

    public TestingRun() { }

    public TestingRun(int scheduleTimestamp, String userEmail, TestingEndpoints testingEndpoints, int testIdConfig, State state, int periodInSeconds) {
        this.scheduleTimestamp = scheduleTimestamp;
        this.endTimestamp = -1;
        this.pickedUpTimestamp = -1;
        this.userEmail = userEmail;
        this.testingEndpoints = testingEndpoints;
        this.testIdConfig = testIdConfig;
        this.state = state;
        this.periodInSeconds = periodInSeconds;
    }

    // if u r adding anything here make sure to add to stopAllTests() method too
    public enum State {
        SCHEDULED, RUNNING, STOPPED, COMPLETED
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
            "}";
    }

}
