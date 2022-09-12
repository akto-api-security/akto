package com.akto.action.testing;

import com.akto.action.UserAction;
import com.akto.dao.AuthMechanismsDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.TestingSchedulesDao;
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
import com.mongodb.client.model.Sorts;

import java.util.List;

public class StartTestAction extends UserAction {

    private TestingEndpoints.Type type;
    private int apiCollectionId;
    private List<ApiInfo.ApiInfoKey> apiInfoKeyList;
    private int testIdConfig;
    private int workflowTestId;

    private TestingRun createTestingRun(int scheduleTimestamp) {
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
            scheduleTimestamp, user.getLogin(), testingEndpoints, testIdConfig, TestingRun.State.SCHEDULED
        );

        return testingRun;   
    }

    public String startTest() {
        // 65 seconds added to give testing module time to get the latest sample messages (which runs every 60 secs)
        int scheduleTimestamp = Context.now() + 65;
        // But if workflow test then run immediately
        if (type.equals(Type.WORKFLOW)) scheduleTimestamp = Context.now();
        
        TestingRun testingRun = createTestingRun(scheduleTimestamp);

        if (testingRun == null) {
            return ERROR.toUpperCase();
        } else {
            TestingRunDao.instance.insertOne(testingRun);
        }
        
        this.retrieveAllCollectionTests();
        return SUCCESS.toUpperCase();
    }

    private List<TestingRun> testingRuns;
    private AuthMechanism authMechanism;

    public String retrieveAllCollectionTests() {
        this.authMechanism = AuthMechanismsDao.instance.findOne(new BasicDBObject());
        BasicDBObject query = new BasicDBObject("testingEndpoints.type", "COLLECTION_WISE");
        testingRuns = TestingRunDao.instance.findAll(query);
        testingRuns.sort((o1, o2) -> o2.getScheduleTimestamp() - o1.getScheduleTimestamp());
        this.testingSchedules = TestingSchedulesDao.instance.findAll(new BasicDBObject());
        return SUCCESS.toUpperCase();
    }

    public String stopTest() {
        BasicDBObject query = 
            new BasicDBObject("testingEndpoints.type", "COLLECTION_WISE")
            .append("testingEndpoints.apiCollectionId", apiCollectionId);

        List<TestingRun> testingRunsForCollection = TestingRunDao.instance.findAll(query, 0, 1, Sorts.descending("scheduleTimestamp"));

        if (!testingRunsForCollection.isEmpty()) {
            TestingRun testingRun = testingRunsForCollection.get(0);
            if(testingRun.getState() == TestingRun.State.RUNNING || testingRun.getState() == TestingRun.State.SCHEDULED) {
                ObjectId testRunObjectId = testingRun.getId();        
                Bson filter = Filters.eq("_id", testRunObjectId);
                Bson update = Updates.set(TestingRun.STATE, TestingRun.State.STOPPED);
                TestingRunDao.instance.updateOne(filter, update);
                this.retrieveAllCollectionTests();
                return SUCCESS.toUpperCase();        
            }
        }
        return ERROR.toUpperCase();
    }

    private String testRunId;
    public String replayTest() {
        User user = getSUser();
        ObjectId testRunObjectId;
        try {
            testRunObjectId = new ObjectId(testRunId);
        } catch (Exception e) {
            addActionError("Test doesn't exist");
            return ERROR.toUpperCase();
        }

        Bson filter = Filters.eq("_id", testRunObjectId);
        TestingRun testingRun = TestingRunDao.instance.findOne(filter);
        if (testingRun == null) {
            addActionError("Test doesn't exist");
            return ERROR.toUpperCase();
        }

        // 65 seconds added to give testing module time to get the latest sample messages (which runs every 60 secs)
        TestingRun newTestingRun = new TestingRun(
                Context.now()+65, user.getLogin(), testingRun.getTestingEndpoints(), testingRun.getTestIdConfig(), TestingRun.State.SCHEDULED
        );

        TestingRunDao.instance.insertOne(newTestingRun);

        return SUCCESS.toUpperCase();
    }

    int startTimestamp;
    boolean recurringDaily;
    List<TestingSchedule> testingSchedules = null;
    public String scheduleTest() {
        String author = getSUser().getLogin();
        TestingRun testingRun = createTestingRun(-1);
        if (testingRun == null) {
            return ERROR.toUpperCase();
        }
        
        int now = Context.now();
        TestingSchedule ts = new TestingSchedule(author, now, author, now, now, startTimestamp, recurringDaily, testingRun);
        TestingSchedulesDao.instance.insertOne(ts);
        this.testingSchedules = TestingSchedulesDao.instance.findAll(new BasicDBObject());
        return SUCCESS.toUpperCase();
    }

    public String stopSchedule() {
        TestingSchedulesDao.instance.deleteAll(Filters.eq("sampleTestingRun.testingEndpoints.apiCollectionId", apiCollectionId));
        this.testingSchedules = TestingSchedulesDao.instance.findAll(new BasicDBObject());
        return SUCCESS.toUpperCase();
    }

    public String fetchWorkflowTestingSchedule() {
        this.testingSchedules = TestingSchedulesDao.instance.findAll(Filters.eq("sampleTestingRun.testingEndpoints.workflowTest._id", workflowTestId));
        return SUCCESS.toUpperCase();
    }

    public String deleteScheduledWorkflowTests() {
        TestingSchedulesDao.instance.deleteAll(Filters.eq("sampleTestingRun.testingEndpoints.workflowTest._id", workflowTestId));
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

        // delete scheduled tests
        TestingSchedulesDao.instance.deleteAll(new BasicDBObject());

        testingSchedules = TestingSchedulesDao.instance.findAll(new BasicDBObject());
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

    public void setTestRunId(String testRunId) {
        this.testRunId = testRunId;
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

    public boolean getRecurringDaily() {
        return this.recurringDaily;
    }

    public void setRecurringDaily(boolean recurringDaily) {
        this.recurringDaily = recurringDaily;
    }

    public List<TestingSchedule> getTestingSchedules() {
        return this.testingSchedules;
    }

    public void setTestIdConfig(int testIdConfig) {
        this.testIdConfig = testIdConfig;
    }

    public void setWorkflowTestId(int workflowTestId) {
        this.workflowTestId = workflowTestId;
    }
}
