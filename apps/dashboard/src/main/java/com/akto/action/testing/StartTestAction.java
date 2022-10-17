package com.akto.action.testing;

import com.akto.action.UserAction;
import com.akto.dao.AuthMechanismsDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dao.testing.WorkflowTestsDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.User;
import com.akto.dto.testing.*;
import com.akto.dto.testing.TestingEndpoints.Type;
import com.akto.dto.testing.TestingRun.State;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.List;

public class StartTestAction extends UserAction {

    private TestingEndpoints.Type type;
    private int apiCollectionId;
    private List<ApiInfo.ApiInfoKey> apiInfoKeyList;
    private int testIdConfig;
    private int workflowTestId;
    private int startTimestamp;
    boolean recurringDaily;
    private List<TestingRun> testingRuns;
    private AuthMechanism authMechanism;
    private int endTimestamp;

    private TestingRun createTestingRun(int scheduleTimestamp, int periodInSeconds) {
        User user = getSUser();

        AuthMechanism authMechanism = AuthMechanismsDao.instance.findOne(new BasicDBObject());
        if (authMechanism == null && testIdConfig == 0) {
            addActionError("Please set authentication mechanism before you test any APIs");
            return null;
        }

        TestingEndpoints testingEndpoints;
        switch (type) {
            case CUSTOM:
                if (this.apiInfoKeyList == null || this.apiInfoKeyList.isEmpty())  {
                    addActionError("APIs list can't be empty");
                    return null;
                }
                testingEndpoints = new CustomTestingEndpoints(apiInfoKeyList);
                break;
            case COLLECTION_WISE:
                testingEndpoints = new CollectionWiseTestingEndpoints(apiCollectionId);
                break;
            case WORKFLOW:
                WorkflowTest workflowTest = WorkflowTestsDao.instance.findOne(Filters.eq("_id", this.workflowTestId));
                if (workflowTest == null) {
                    addActionError("Couldn't find workflow test");
                    return null;
                }
                testingEndpoints = new WorkflowTestingEndpoints(workflowTest);
                testIdConfig = 1;
                break;
            default:
                addActionError("Invalid APIs type");
                return null;
        }

        TestingRun testingRun = new TestingRun(
            scheduleTimestamp, user.getLogin(), testingEndpoints, testIdConfig, TestingRun.State.SCHEDULED, periodInSeconds
        );

        return testingRun;   
    }

    public String startTest() {
        // 65 seconds added to give testing module time to get the latest sample messages (which runs every 60 secs)
        // But if workflow test then run immediately
        int adder = type.equals(Type.WORKFLOW) ? 0 : 65;
        int scheduleTimestamp = this.startTimestamp == 0 ? (Context.now() + adder) : this.startTimestamp;
        
        TestingRun testingRun = createTestingRun(scheduleTimestamp, this.recurringDaily ? 86400 : 0);

        if (testingRun == null) {
            return ERROR.toUpperCase();
        } else {
            TestingRunDao.instance.insertOne(testingRun);
        }
        
        this.startTimestamp = 0;
        this.endTimestamp = 0;
        this.retrieveAllCollectionTests();
        return SUCCESS.toUpperCase();
    }

    public String retrieveAllCollectionTests() {
        if (this.startTimestamp == 0) {
            this.startTimestamp = Context.now();
        }

        if (this.endTimestamp == 0) {
            this.endTimestamp = Context.now() + 86400;
        }

        this.authMechanism = AuthMechanismsDao.instance.findOne(new BasicDBObject());

        Bson filterQ = Filters.and(
            Filters.lte(TestingRun.SCHEDULE_TIMESTAMP, this.endTimestamp),
            Filters.gte(TestingRun.SCHEDULE_TIMESTAMP, this.startTimestamp)
        );

        testingRuns = TestingRunDao.instance.findAll(filterQ);
        testingRuns.sort((o1, o2) -> o2.getScheduleTimestamp() - o1.getScheduleTimestamp());
        return SUCCESS.toUpperCase();
    }

    String testingRunHexId;
    List<TestingRunResultSummary> testingRunResultSummaries;
    public String fetchTestingRunResultSummaries() {
        ObjectId testingRunId = new ObjectId(testingRunHexId);

        Bson filterQ = Filters.and(
            Filters.eq(TestingRunResultSummary.TESTING_RUN_ID, testingRunId),
            Filters.gte(TestingRunResultSummary.START_TIMESTAMP, startTimestamp),
            Filters.lte(TestingRunResultSummary.END_TIMESTAMP, endTimestamp)
        );

        this.testingRunResultSummaries = TestingRunResultSummariesDao.instance.findAll(filterQ);

        return SUCCESS.toUpperCase();
    }

    String testingRunResultSummaryHexId;
    List<TestingRunResult> testingRunResults;
    public String fetchTestingRunResults() {
        ObjectId testingRunResultSummaryId = new ObjectId(testingRunResultSummaryHexId);
        
        this.testingRunResults = TestingRunResultDao.instance.findAll("testRunResultSummaryId", testingRunResultSummaryId);

        return SUCCESS.toUpperCase();
    }

    public String fetchWorkflowTestingRun() {
        Bson filterQ = Filters.and(
            Filters.eq("testingEndpoints.workflowTest._id", workflowTestId),
            Filters.eq("state", TestingRun.State.SCHEDULED)
        );
        this.testingRuns = TestingRunDao.instance.findAll(filterQ);
        return SUCCESS.toUpperCase();
    }

    public String deleteScheduledWorkflowTests() {
        Bson filter = Filters.and(
                Filters.or(
                        Filters.eq(TestingRun.STATE, State.SCHEDULED),
                        Filters.eq(TestingRun.STATE, State.RUNNING)
                ),
                Filters.eq("testingEndpoints.workflowTest._id", workflowTestId)
        );
        Bson update = Updates.set(TestingRun.STATE, State.STOPPED);

        TestingRunDao.instance.getMCollection().updateMany(filter, update);

        return SUCCESS.toUpperCase();
    }

    public String stopAllTests() {
        // stop all the scheduled and running tests
        Bson filter = Filters.or(
            Filters.eq(TestingRun.STATE, State.SCHEDULED),
            Filters.eq(TestingRun.STATE, State.RUNNING)
        );

        TestingRunDao.instance.getMCollection().updateMany(filter,Updates.set(TestingRun.STATE, State.STOPPED));
        testingRuns = TestingRunDao.instance.findAll(filter);

        return SUCCESS.toUpperCase();
    }

    public void setType(TestingEndpoints.Type type) {
        this.type = type;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public void setApiInfoKeyList(List<ApiInfo.ApiInfoKey> apiInfoKeyList) {
        this.apiInfoKeyList = apiInfoKeyList;
    }

    public List<TestingRun> getTestingRuns() {
        return testingRuns;
    }

    public AuthMechanism getAuthMechanism() {
        return this.authMechanism;
    }

    public void setAuthMechanism(AuthMechanism authMechanism) {
        this.authMechanism = authMechanism;
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

    public boolean getRecurringDaily() {
        return this.recurringDaily;
    }

    public void setRecurringDaily(boolean recurringDaily) {
        this.recurringDaily = recurringDaily;
    }

    public void setTestIdConfig(int testIdConfig) {
        this.testIdConfig = testIdConfig;
    }

    public void setWorkflowTestId(int workflowTestId) {
        this.workflowTestId = workflowTestId;
    }

    public void setTestingRunHexId(String testingRunHexId) {
        this.testingRunHexId = testingRunHexId;
    }

    public List<TestingRunResultSummary> getTestingRunResultSummaries() {
        return this.testingRunResultSummaries;
    }

    public void setTestingRunResultSummaryHexId(String testingRunResultSummaryHexId) {
        this.testingRunResultSummaryHexId = testingRunResultSummaryHexId;
    }

    public List<TestingRunResult> getTestingRunResults() {
        return this.testingRunResults;
    }
}
