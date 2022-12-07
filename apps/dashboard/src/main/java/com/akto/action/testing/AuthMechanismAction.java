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

    //todo: rename requestData
    private ArrayList<RequestData> requestData;

    private String type;

    private AuthMechanism authMechanism;

    private ArrayList<AuthParamData> authParamData;

    public String addAuthMechanism() {
        List<AuthParam> authParams = new ArrayList<>();

        type = type != null ? type : LoginFlowEnums.AuthMechanismTypes.HARDCODED.toString();

        AuthMechanismsDao.instance.deleteAll(new BasicDBObject());

        if (type.equals(LoginFlowEnums.AuthMechanismTypes.HARDCODED.toString())) {
            if (authParamData.get(0).getKey() == null || authParamData.get(0).getValue() == null ||
                    authParamData.get(0).getWhere() == null) {
                addActionError("Key, Value, Location can't be empty");
                return ERROR.toUpperCase();
            }

            authParams.add(new HardcodedAuthParam(authParamData.get(0).getWhere(), authParamData.get(0).getKey(),
                    authParamData.get(0).getValue()));
        } else {

            for (AuthParamData param: authParamData) {
                if (param.getKey() == null || param.getValue() == null || param.getValueLocation() == null ||
                        param.getWhere() == null) {
                    addActionError("Key, Value, ValueLocation, Location can't be empty");
                    return ERROR.toUpperCase();
                }

                authParams.add(new LoginRequestAuthParam(param.getWhere(), param.getKey(), param.getValue(),
                        param.getValueLocation()));
            }
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

    public String getType() {
        return this.type;
    }


    public ArrayList<RequestData> getRequestData() {
        return this.requestData;
    }

    public AuthMechanism getAuthMechanism() {
        return this.authMechanism;
    }

    public ArrayList<AuthParamData> getAuthParamData() {
        return this.authParamData;
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

    public void setAuthParamData(ArrayList<AuthParamData> authParamData) {
        this.authParamData = authParamData;
    }

}
