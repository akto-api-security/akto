package com.akto.action.testing;

import com.akto.action.UserAction;
import com.akto.dao.AuthMechanismsDao;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.WorkflowTestResultsDao;
import com.akto.dto.testing.*;
import com.akto.log.LoggerMaker;
import com.akto.testing.TestExecutor;
import com.akto.util.enums.LoginFlowEnums;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class AuthMechanismAction extends UserAction {

    //todo: rename requestData, also add len check
    private ArrayList<RequestData> requestData;

    private String type;

    private AuthMechanism authMechanism;

    private ArrayList<AuthParamData> authParamData;

    private String verificationCodeBody;

    private String uuid;

    private ArrayList<Object> responses;

    private static final LoggerMaker loggerMaker = new LoggerMaker(AuthMechanismAction.class);

    public String addAuthMechanism() {
        // todo: add more validations
        List<AuthParam> authParams = new ArrayList<>();

        type = type != null ? type : LoginFlowEnums.AuthMechanismTypes.HARDCODED.toString();

        AuthMechanismsDao.instance.deleteAll(new BasicDBObject());

        if (type.equals(LoginFlowEnums.AuthMechanismTypes.HARDCODED.toString())) {
            if (!authParamData.get(0).validate()) {
                addActionError("Key, Value, Location can't be empty");
                return ERROR.toUpperCase();
            }

            authParams.add(new HardcodedAuthParam(authParamData.get(0).getWhere(), authParamData.get(0).getKey(),
                    authParamData.get(0).getValue()));
        } else {

            for (AuthParamData param: authParamData) {
                if (!param.validate()) {
                    addActionError("Key, Value, Location can't be empty");
                    return ERROR.toUpperCase();
                }

                authParams.add(new LoginRequestAuthParam(param.getWhere(), param.getKey(), param.getValue()));
            }
        }
        AuthMechanism authMechanism = new AuthMechanism(authParams, requestData, type);

        AuthMechanismsDao.instance.insertOne(authMechanism);
        return SUCCESS.toUpperCase();
    }

    public String triggerLoginFlowSteps() {
        List<AuthParam> authParams = new ArrayList<>();

        if (type.equals(LoginFlowEnums.AuthMechanismTypes.HARDCODED.toString())) {
            addActionError("Invalid Type Value");
            return ERROR.toUpperCase();
        }

        for (AuthParamData param: authParamData) {
            if (!param.validate()) {
                addActionError("Key, Value, Location can't be empty");
                return ERROR.toUpperCase();
            }
            authParams.add(new LoginRequestAuthParam(param.getWhere(), param.getKey(), param.getValue()));
        }

        AuthMechanism authMechanism = new AuthMechanism(authParams, requestData, type);

        TestExecutor testExecutor = new TestExecutor();
        try {
            responses = testExecutor.executeLoginFlow(authMechanism, responses);
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

    // fix and use this for dynamic otp
    public String saveOtpData() {

        // fetch from url param
        Bson filters = Filters.eq("uuid", uuid);
        try {
            authMechanism = AuthMechanismsDao.instance.findOne(filters);
        } catch(Exception e) {
            loggerMaker.errorAndAddToDb("error extracting verification code for auth Id " + uuid);
            return ERROR.toUpperCase();
        }

        for (RequestData data : authMechanism.getRequestData()) {
            if (!(data.getType().equals("EMAIL_CODE_VERIFICATION") || data.getType().equals("MOBILE_CODE_VERIFICATION"))) {
                continue;
            }
            LoginVerificationCodeData verificationCodeData = data.getVerificationCodeData();

            String key = verificationCodeData.getKey();
            String body = data.getBody();
            String verificationCode;
            try {
                verificationCode = extractVerificationCode(verificationCodeBody, verificationCodeData.getRegexString());
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("error parsing regex string " + verificationCodeData.getRegexString() +
                        "for auth Id " + uuid);
                return ERROR.toUpperCase();
            }

            if (verificationCode == null) {
                loggerMaker.errorAndAddToDb("error extracting verification code for auth Id " + uuid);
                return ERROR.toUpperCase();
            }

            Gson gson = new Gson();
            Map<String, Object> json = gson.fromJson(body, Map.class);
            json.put(key, verificationCode);

            JSONObject jsonBody = new JSONObject();
            for (Map.Entry<String, Object> entry : json.entrySet()) {
                jsonBody.put(entry.getKey(), entry.getValue());
            }
            data.setBody(jsonBody.toString());
        }

        AuthMechanismsDao.instance.replaceOne(filters, authMechanism);
        return SUCCESS.toUpperCase();
    }

    private String extractVerificationCode(String text, String regex) {
        return "346";
//        System.out.println(regex);
//        System.out.println(regex.replace("\\", "\\\\"));
//        Pattern pattern = Pattern.compile(regex.replace("\\", "\\\\"));
//        Matcher matcher = pattern.matcher(text);
//        String verificationCode = null;
//        if (matcher.find()) {
//            verificationCode = matcher.group(1);
//        }
//        return verificationCode;
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

    public String getVerificationCodeBody() {
        return this.verificationCodeBody;
    }

    public String getUuid() {
        return this.uuid;
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

    public void setVerificationCodeBody(String verificationCodeBody) {
        this.verificationCodeBody = verificationCodeBody;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
}
