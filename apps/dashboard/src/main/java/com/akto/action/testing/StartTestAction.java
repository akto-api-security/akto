package com.akto.action.testing;

import com.akto.action.UserAction;
import com.akto.dao.AuthMechanismsDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.TestingSchedulesDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.User;
import com.akto.dto.testing.*;
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

    public String startTest() {
        User user = getSUser();
        int testIdConfig = 0;

        AuthMechanism authMechanism = AuthMechanismsDao.instance.findOne(new BasicDBObject());
        if (authMechanism == null) {
            addActionError("Please set authentication mechanism before you test any APIs");
            return ERROR.toUpperCase();
        }

        TestingEndpoints testingEndpoints;
        switch (type) {
            case CUSTOM:
                if (this.apiInfoKeyList == null || this.apiInfoKeyList.isEmpty())  {
                    addActionError("APIs list can't be empty");
                    return ERROR.toUpperCase();
                }
                testingEndpoints = new CustomTestingEndpoints(apiInfoKeyList);
                break;
            case COLLECTION_WISE:
                testingEndpoints = new CollectionWiseTestingEndpoints(apiCollectionId);
                break;
            default:
                addActionError("Invalid APIs type");
                return ERROR.toUpperCase();
        }

        TestingRun testingRun = new TestingRun(
                Context.now(), user.getLogin(), testingEndpoints, testIdConfig, TestingRun.State.SCHEDULED
        );

        TestingRunDao.instance.insertOne(testingRun);
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

        TestingRun newTestingRun = new TestingRun(
                Context.now(), user.getLogin(), testingRun.getTestingEndpoints(), testingRun.getTestIdConfig(), TestingRun.State.SCHEDULED
        );

        TestingRunDao.instance.insertOne(newTestingRun);

        return SUCCESS.toUpperCase();
    }

    int startTimestamp;
    boolean recurringDaily;
    List<TestingSchedule> testingSchedules = null;
    public String scheduleTest() {
        String author = getSUser().getLogin();
        int now = Context.now();
        TestingRun sampleTestingRun = new TestingRun(-1, author, new CollectionWiseTestingEndpoints(apiCollectionId), 0, TestingRun.State.SCHEDULED);
        TestingSchedule ts = new TestingSchedule(author, now, author, now, now, startTimestamp, recurringDaily, sampleTestingRun);
        TestingSchedulesDao.instance.insertOne(ts);
        this.testingSchedules = TestingSchedulesDao.instance.findAll(new BasicDBObject());
        return SUCCESS.toUpperCase();
    }

    public String stopSchedule() {
        TestingSchedulesDao.instance.deleteAll(Filters.eq("sampleTestingRun.testingEndpoints.apiCollectionId", apiCollectionId));
        this.testingSchedules = TestingSchedulesDao.instance.findAll(new BasicDBObject());
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


}
