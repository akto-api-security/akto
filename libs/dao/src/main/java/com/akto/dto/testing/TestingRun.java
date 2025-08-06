package com.akto.dto.testing;

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
    public static final String TEST_RUNTIME = "testRunTime";
    private int testRunTime;
    public static final String MAX_CONCURRENT_REQUEST = "maxConcurrentRequests";
    private int maxConcurrentRequests;
    private String triggeredBy;
    public static final String TRIGGERED_BY = "triggeredBy";

    public static final String _API_COLLECTION_ID = "testingEndpoints.apiCollectionId";
    public static final String _API_COLLECTION_ID_WORK_FLOW = "testingEndpoints.workflowTest.apiCollectionId";
    public static final String _API_COLLECTION_ID_IN_LIST = "testingEndpoints.apisList.apiCollectionId";

    public static final String IS_NEW_TESTING_RUN = "isNewTestRun";
    private boolean isNewTestRun = true;

    @BsonIgnore
    private String hexId;
    @BsonIgnore
    private TestingRunConfig testingRunConfig;

    public static final String NAME = "name";
    private String name;

    public static final String MINI_TESTING_SERVICE_NAME = "miniTestingServiceName";
    public static final String SELECTED_SLACK_CHANNEL_ID = "selectedSlackChannelId";
    private String miniTestingServiceName;
    private int selectedSlackChannelId;

    public enum TestingRunType{
        ONE_TIME, RECURRING, CI_CD, CONTINUOUS_TESTING
    }

    public static final String SEND_SLACK_ALERT = "sendSlackAlert";
    private boolean sendSlackAlert = false;

    public static final String SEND_MS_TEAMS_ALERT = "sendMsTeamsAlert";
    private boolean sendMsTeamsAlert = false;
    public static final String SEND_PENDING_TESTS_WEBHOOK_TIMESTAMP = "sendPendingTestsWebhookTimestamp";
    private int sendPendingTestsWebhookTimestamp;

    public TestingRun() { }

    public TestingRun(int scheduleTimestamp, String userEmail, TestingEndpoints testingEndpoints, int testIdConfig, State state, int periodInSeconds, String name, String triggeredBy, boolean sendSlackAlert) {
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
        this.sendSlackAlert = sendSlackAlert;
        this.isNewTestRun = true;
    }

    public TestingRun(int scheduleTimestamp, String userEmail, TestingEndpoints testingEndpoints, int testIdConfig, State state, int periodInSeconds, String name, int testRunTime, int maxConcurrentRequests, boolean sendSlackAlert, String miniTestingServiceName, int selectedSlackChannelId) {
        this(scheduleTimestamp, userEmail,testingEndpoints,testIdConfig, state, periodInSeconds, name, testRunTime, maxConcurrentRequests, sendSlackAlert, false, miniTestingServiceName, selectedSlackChannelId);
    }

    public TestingRun(int scheduleTimestamp, String userEmail, TestingEndpoints testingEndpoints, int testIdConfig,
        State state, int periodInSeconds, String name, int testRunTime, int maxConcurrentRequests,
        boolean sendSlackAlert, boolean sendMsTeamsAlert, String miniTestingServiceName, int selectedSlackChannelId) {
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
        this.sendSlackAlert = sendSlackAlert;
        this.miniTestingServiceName = miniTestingServiceName;
        this.isNewTestRun = true;
        this.sendMsTeamsAlert = sendMsTeamsAlert;
        this.selectedSlackChannelId = selectedSlackChannelId;
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

    public boolean isSendSlackAlert() {
        return sendSlackAlert;
    }

    public boolean getSendSlackAlert() {
        return sendSlackAlert;
    }

    public void setSendSlackAlert(boolean sendSlackAlert) {
        this.sendSlackAlert = sendSlackAlert;
    }

    public boolean getSendMsTeamsAlert() {
        return sendMsTeamsAlert;
    }

    public void setSendMsTeamsAlert(boolean sendMsTeamsAlert) {
        this.sendMsTeamsAlert = sendMsTeamsAlert;
    }

    public String getMiniTestingServiceName() {
        return miniTestingServiceName;
    }

    public void setMiniTestingServiceName(String miniTestingServiceName) {
        this.miniTestingServiceName = miniTestingServiceName;
    }

    public int getSelectedSlackChannelId() {
        return selectedSlackChannelId;
    }

    public void setSelectedSlackChannelId(int selectedSlackChannelId) {
        this.selectedSlackChannelId = selectedSlackChannelId;
    }

    public int getSendPendingTestsWebhookTimestamp() {
        return sendPendingTestsWebhookTimestamp;
    }

    public void setSendPendingTestsWebhookTimestamp(int sendPendingTestsWebhookTimestamp) {
        this.sendPendingTestsWebhookTimestamp = sendPendingTestsWebhookTimestamp;
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

    public boolean isNewTestRun() {
        return isNewTestRun;
    }

    public void setNewTestRun(boolean isNewTestRun) {
        this.isNewTestRun = isNewTestRun;
    }

}
