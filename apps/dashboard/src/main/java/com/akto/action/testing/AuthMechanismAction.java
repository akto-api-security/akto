package com.akto.action.testing;

import com.akto.DaoInit;
import com.akto.action.UserAction;
import com.akto.dao.AuthMechanismsDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.WorkflowTestResultsDao;
import com.akto.dto.testing.*;
import com.akto.dto.ApiInfo;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.URLMethods.Method;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;


public class AuthMechanismAction extends UserAction {

    private AuthParam.Location location;
    private String key;
    private String value;

    public String addAuthMechanism() {
        List<AuthParam> authParams = new ArrayList<>();
        if (location == null || key == null || value == null) {
            addActionError("Location, Key or Value can't be empty");
            return ERROR.toUpperCase();
        }
        AuthMechanismsDao.instance.deleteAll(new BasicDBObject());
        authParams.add(new HardcodedAuthParam(location, key, value));
        AuthMechanism authMechanism = new AuthMechanism(authParams);

        AuthMechanismsDao.instance.insertOne(authMechanism);
        return SUCCESS.toUpperCase();
    }

    List<TestingRunResult> testingRunResults;
    public String fetchTestingRunResults() {
        testingRunResults = TestingRunResultDao.instance.fetchLatestTestingRunResult();
        return SUCCESS.toUpperCase();
    }

    public String fetchRequestAndResponseForTest() {

        Set<ObjectId> testRunResultIds = new HashSet<>();

        for(TestingRunResult testingRunResult: this.testingRunResults) {
            testRunResultIds.add(new ObjectId(testingRunResult.getHexId()));
        }

        this.testingRunResults = TestingRunResultDao.instance.findAll(Filters.in("_id", testRunResultIds));


        return SUCCESS.toUpperCase();
    }


    private int workflowTestId;
    private WorkflowTestResult workflowTestResult;
    private TestingRun workflowTestingRun;
    public String fetchWorkflowResult() {
        workflowTestingRun = TestingRunDao.instance.findLatestOne(
                Filters.eq(TestingRun._TESTING_ENDPOINTS+"."+WorkflowTestingEndpoints._WORK_FLOW_TEST+"._id", workflowTestId)
        );

        if (workflowTestingRun == null) {
            return SUCCESS.toUpperCase();
        }

        workflowTestResult  = WorkflowTestResultsDao.instance.findOne(
                Filters.eq(WorkflowTestResult._TEST_RUN_ID, workflowTestingRun.getId())
        );

        return SUCCESS.toUpperCase();
    }

    public AuthParam.Location getLocation() {
        return this.location;
    }

    public String getKey() {
        return this.key;
    }

    public String getValue() {
        return this.value;
    }

    public List<TestingRunResult> getTestingRunResults() {
        return this.testingRunResults;
    }

    public void setTestingRunResults(List<TestingRunResult> testingRunResults) {
        this.testingRunResults = testingRunResults;
    }

    public void setLocation(AuthParam.Location location) {
        this.location = location;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void setWorkflowTestId(int workflowTestId) {
        this.workflowTestId = workflowTestId;
    }

    public WorkflowTestResult getWorkflowTestResult() {
        return workflowTestResult;
    }

    public TestingRun getWorkflowTestingRun() {
        return workflowTestingRun;
    }

}
