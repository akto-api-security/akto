package com.akto.action.testing;

import com.akto.action.UserAction;
import com.akto.dao.AuthMechanismsDao;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.WorkflowTestResultsDao;
import com.akto.dto.testing.*;
import com.akto.testing.TestExecutor;
import com.akto.util.enums.LoginFlowEnums;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;


public class AuthMechanismAction extends UserAction {

    private AuthParam.Location location;
    private String key;
    private String value;

    private ArrayList<RequestData> requestData;

    private String authTokenPath;

    private String type;

    private AuthMechanism authMechanism;

    public String addAuthMechanism() {
        List<AuthParam> authParams = new ArrayList<>();
        if (location == null || key == null || value == null) {
            addActionError("Location, Key or Value can't be empty");
            return ERROR.toUpperCase();
        }
        AuthMechanismsDao.instance.deleteAll(new BasicDBObject());
        type = type != null ? type : LoginFlowEnums.AuthMechanismTypes.HARDCODED.toString();

        if (type.equals(LoginFlowEnums.AuthMechanismTypes.HARDCODED.toString())) {
            authParams.add(new HardcodedAuthParam(location, key, value));
        } else {
            authParams.add(new LoginRequestAuthParam(location, key, value, authTokenPath));
        }
        AuthMechanism authMechanism = new AuthMechanism(authParams, requestData, type);

        AuthMechanismsDao.instance.insertOne(authMechanism);
        return SUCCESS.toUpperCase();
    }

    public String triggerLoginFlowSteps() {
        List<AuthParam> authParams = new ArrayList<>();
        if (location == null || key == null || requestData == null || authTokenPath == null) {
            addActionError("Location, Key, requestData, authTokenPath can't be empty");
            return ERROR.toUpperCase();
        }
        type = type != null ? type : LoginFlowEnums.AuthMechanismTypes.HARDCODED.toString();

        if (type.equals(LoginFlowEnums.AuthMechanismTypes.HARDCODED.toString())) {
            authParams.add(new HardcodedAuthParam(location, key, value));
        } else {
            authParams.add(new LoginRequestAuthParam(location, key, value, authTokenPath));
        }
        AuthMechanism authMechanism = new AuthMechanism(authParams, requestData, type);

        TestExecutor testExecutor = new TestExecutor();
        try {
            testExecutor.executeLoginFlow(authMechanism);
        } catch(Exception e) {
            addActionError(e.getMessage());
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    public String fetchAuthMechanismData() {

        authMechanism = AuthMechanismsDao.instance.findOne(new BasicDBObject());
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

    public String getType() {
        return this.type;
    }

    public String getAuthTokenPath() {
        return this.authTokenPath;
    }

    public ArrayList<RequestData> getRequestData() {
        return this.requestData;
    }

    public AuthMechanism getAuthMechanism() {
        return this.authMechanism;
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

    public void setType(String type) {
        this.type = type;
    }

    public void setRequestData(ArrayList<RequestData> requestData) {
        this.requestData = requestData;
    }

    public void setAuthTokenPath(String authTokenPath) {
        this.authTokenPath = authTokenPath;
    }
}
