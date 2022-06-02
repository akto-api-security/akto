package com.akto.action.testing;

import com.akto.action.UserAction;
import com.akto.dao.AuthMechanismsDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.User;
import com.akto.dto.testing.*;
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

    @Override
    public String execute() {
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

        return SUCCESS;
    }

    private List<TestingRun> testingRuns;
    public String retrieveAllTests() {
        testingRuns = TestingRunDao.instance.findAll(new BasicDBObject());
        testingRuns.sort((o1, o2) -> o2.getScheduleTimestamp() - o1.getScheduleTimestamp());
        return SUCCESS.toUpperCase();
    }


    private String testRunId;
    public String stopTest() {
        ObjectId testRunObjectId;
        try {
            testRunObjectId = new ObjectId(testRunId);
        } catch (Exception e) {
            addActionError("Test doesn't exist");
            return ERROR.toUpperCase();
        }

        Bson filter = Filters.eq("_id", testRunObjectId);
        Bson update = Updates.set(TestingRun.STATE, TestingRun.State.STOPPED);
        TestingRunDao.instance.updateOne(filter, update);
        return SUCCESS.toUpperCase();
    }

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
}
