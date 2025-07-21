package com.akto.action.testing;

import com.akto.action.UserAction;
import com.akto.dao.AuthMechanismsDao;
import com.akto.dao.testing.TestRolesDao;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.WorkflowTestResultsDao;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.AuthParam;
import com.akto.dto.testing.AuthParamData;
import com.akto.dto.testing.HardcodedAuthParam;
import com.akto.dto.testing.LoginFlowParams;
import com.akto.dto.testing.LoginFlowResponse;
import com.akto.dto.testing.LoginRequestAuthParam;
import com.akto.dto.testing.RequestData;
import com.akto.dto.testing.SampleDataAuthParam;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.WorkflowTestResult;
import com.akto.dto.testing.WorkflowTestingEndpoints;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.testing.TestExecutor;
import com.akto.util.Constants;
import com.akto.util.enums.LoginFlowEnums;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import java.util.ArrayList;
import java.util.List;


public class AuthMechanismAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(AuthMechanismAction.class, LogDb.DASHBOARD);;

    //todo: rename requestData, also add len check
    private ArrayList<RequestData> requestData;

    private String type;

    private AuthMechanism authMechanism;

    private ArrayList<AuthParamData> authParamData;

    private String uuid;

    private ArrayList<Object> responses;

    private String nodeId;

    private BasicDBObject authMechanismDoc;


    public String addAuthMechanism() {
        // todo: add more validations
        List<AuthParam> authParams = new ArrayList<>();

        type = type != null ? type : LoginFlowEnums.AuthMechanismTypes.HARDCODED.toString();

        AuthMechanismsDao.instance.deleteAll(new BasicDBObject());

        if (type.equals(LoginFlowEnums.AuthMechanismTypes.HARDCODED.toString())) {
            if (authParamData != null) {
                if (!authParamData.get(0).validate()) {
                    addActionError("Key, Value, Location can't be empty");
                    return ERROR.toUpperCase();
                }
                authParams.add(new HardcodedAuthParam(authParamData.get(0).getWhere(), authParamData.get(0).getKey(),
                    authParamData.get(0).getValue(), true));
            }
            
        } else if (type.equals(LoginFlowEnums.AuthMechanismTypes.LOGIN_REQUEST.toString())) {

            for (AuthParamData param: authParamData) {
                if (!param.validate()) {
                    addActionError("Key, Value, Location can't be empty");
                    return ERROR.toUpperCase();
                }

                authParams.add(new LoginRequestAuthParam(param.getWhere(), param.getKey(), param.getValue(), param.getShowHeader()));
            }
        } else if (type.equals(LoginFlowEnums.AuthMechanismTypes.SAMPLE_DATA.toString())) {

            for (AuthParamData param: authParamData) {
                if (!param.validate()) {
                    addActionError("Key, Value, Location can't be empty");
                    return ERROR.toUpperCase();
                }

                authParams.add(new SampleDataAuthParam(param.getWhere(), param.getKey(), param.getValue(), param.getShowHeader()));
            }
        } 
        AuthMechanism authMechanism = new AuthMechanism(authParams, requestData, type, null);

        AuthMechanismsDao.instance.insertOne(authMechanism);
        return SUCCESS.toUpperCase();
    }

    public String triggerLoginFlowSteps() {
        List<AuthParam> authParams = new ArrayList<>();

        if (type.equals(LoginFlowEnums.AuthMechanismTypes.HARDCODED.toString()) ||
                type.equals(LoginFlowEnums.AuthMechanismTypes.TLS_AUTH.toString()) ||
                type.equals(LoginFlowEnums.AuthMechanismTypes.SAMPLE_DATA.toString())) {
            addActionError("Invalid Type Value");
            return ERROR.toUpperCase();
        }

        for (AuthParamData param: authParamData) {
            if (!param.validate()) {
                addActionError("Key, Value, Location can't be empty");
                return ERROR.toUpperCase();
            }
            authParams.add(new LoginRequestAuthParam(param.getWhere(), param.getKey(), param.getValue(), param.getShowHeader()));
        }

        AuthMechanism authMechanism = new AuthMechanism(authParams, requestData, type, null);

        TestExecutor testExecutor = new TestExecutor();
        try {
            LoginFlowResponse loginFlowResponse = testExecutor.executeLoginFlow(authMechanism, null, null);
            responses = loginFlowResponse.getResponses();
            if (!loginFlowResponse.getSuccess()) {
                throw new Exception(loginFlowResponse.getError());
            }
        } catch(Exception e) {
            addActionError(e.getMessage());
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    public String triggerSingleLoginFlowStep() {
        List<AuthParam> authParams = new ArrayList<>();

        if (type.equals(LoginFlowEnums.AuthMechanismTypes.HARDCODED.toString()) ||
                type.equals(LoginFlowEnums.AuthMechanismTypes.SAMPLE_DATA.toString()) ||
                type.equals(LoginFlowEnums.AuthMechanismTypes.TLS_AUTH.toString())) {
            addActionError("Invalid Type Value");
            return ERROR.toUpperCase();
        }

        AuthMechanism authMechanism = new AuthMechanism(authParams, requestData, type, null);

        TestExecutor testExecutor = new TestExecutor();
        try {
            LoginFlowParams loginFlowParams = new LoginFlowParams(getSUser().getId(), true, nodeId);
            LoginFlowResponse loginFlowResponse = testExecutor.executeLoginFlow(authMechanism, loginFlowParams, null);
            responses = loginFlowResponse.getResponses();
            if (!loginFlowResponse.getSuccess()) {
                throw new Exception(loginFlowResponse.getError());
            }
        } catch(Exception e) {
            addActionError(e.getMessage());
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    public String fetchAuthMechanismData() {

        authMechanism = TestRolesDao.instance.fetchAttackerToken(null);
        return SUCCESS.toUpperCase();
    }

    public String fetchAuthMechanismDataDoc() {
        authMechanismDoc = TestRolesDao.instance.fetchAttackerTokenDoc(0);
        authMechanismDoc.put(AuthMechanism.OBJECT_ID,authMechanismDoc.get(Constants.ID).toString());
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

    public String getUuid() {
        return this.uuid;
    }

    public String getNodeId() {
        return this.nodeId;
    }

    public ArrayList<Object> getResponses() {
        return this.responses;
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

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }
    
    public BasicDBObject getAuthMechanismDoc() {
        return authMechanismDoc;
    }

    public void setAuthMechanismDoc(BasicDBObject authMechanismDoc) {
        this.authMechanismDoc = authMechanismDoc;
    }
    
}
