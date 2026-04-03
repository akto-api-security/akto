package com.akto.dto.test_editor;
import java.util.List;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.LoginFlowResponse;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.TestingRun.State;

import lombok.Getter;
import lombok.Setter;
public class TestingRunPlayground {

    public static final String TEST_TEMPLATE = "testTemplate";
    public static final String STATE = "state";
    public static final String SAMPLES = "samples";
    public static final String API_INFO_KEY = "apiInfoKey";
    public static final String CREATED_AT = "createdAt";
    public static final String TESTING_RUN_RESULT = "testingRunResult";
    public static final String TESTING_RUN_CONFIG = "testingRunConfig";
    public static final String LOGIN_FLOW_RESPONSE = "loginFlowResponse";
    public static final String LOGIN_FLOW_AUTH_MECHANISM = "loginFlowAuthMechanism";
    public static final String LOGIN_FLOW_NODE_ID = "loginFlowNodeId";
    public static final String LOGIN_FLOW_USER_ID = "loginFlowUserId";
    public static final String LOGIN_FLOW_SINGLE_STEP_ONLY = "loginFlowSingleStepOnly";

    public static final String RECORDED_FLOW_CONTENT = "recordedFlowContent";

    public static final String RECORDED_FLOW_TOKEN_FETCH_COMMAND = "recordedFlowTokenFetchCommand";

    public static final String RECORDED_FLOW_ROLE_NAME = "recordedFlowRoleName";

    public static final String TESTING_RUN_PLAYGROUND_TYPE = "testingRunPlaygroundType";

    public static final String RECORDED_FLOW_TOKEN_RESULT = "recordedFlowTokenResult";

    public static final String RECORDED_FLOW_ERROR_MESSAGE = "recordedFlowErrorMessage";

    private ObjectId id;
    private String testTemplate;
    private State state;
    private List<String> samples;
    private ApiInfoKey apiInfoKey;
    private int createdAt;
    private TestingRunResult testingRunResult;
    @Getter
    @Setter
    private TestingRunConfig testingRunConfig;
    private String miniTestingName;
    private OriginalHttpRequest originalHttpRequest;
    public static final String ORIGINAL_HTTP_RESPONSE = "originalHttpResponse";
    private OriginalHttpResponse originalHttpResponse;
    @BsonIgnore
    private String hexId;
    private TestingRunPlaygroundType testingRunPlaygroundType;

    private AuthMechanism loginFlowAuthMechanism;
    private String loginFlowNodeId;
    private int loginFlowUserId;
    private boolean loginFlowSingleStepOnly;
    private LoginFlowResponse loginFlowResponse;

    private String recordedFlowContent;

    private String recordedFlowTokenFetchCommand;

    private String recordedFlowRoleName;

    private String recordedFlowTokenResult;

    private String recordedFlowErrorMessage;

    public TestingRunPlaygroundType getTestingRunPlaygroundType() {
        if (testingRunPlaygroundType == null) {
            return TestingRunPlaygroundType.TEST_EDITOR_PLAYGROUND;
        }
        return testingRunPlaygroundType;
    }

    public void setTestingRunPlaygroundType(TestingRunPlaygroundType testingRunPlaygroundType) {
        this.testingRunPlaygroundType = testingRunPlaygroundType;
    }

    public enum TestingRunPlaygroundType {
        TEST_EDITOR_PLAYGROUND,
        POSTMAN_IMPORTS,
        LOGIN_FLOW_TEST,
        RECORDED_JSON_FLOW
    }

    public TestingRunPlayground(String testTemplate, State state, List<String> samples, ApiInfoKey apiInfoKey, int createdAt) {
        this.testTemplate = testTemplate;
        this.state = state;
        this.samples = samples;
        this.apiInfoKey = apiInfoKey;
        this.createdAt = createdAt;
    }

    public TestingRunPlayground(){
    }

    public int getCreatedAt() {
        return createdAt;
    }


    public void setCreatedAt(int createdAt) {
        this.createdAt = createdAt;
    }

    public ApiInfoKey getApiInfoKey() {
        return apiInfoKey;
    }
    public void setApiInfoKey(ApiInfoKey apiInfoKey) {
        this.apiInfoKey = apiInfoKey;
    }
    public ObjectId getId() {
        return id;
    }
    public void setId(ObjectId id) {
        this.id = id;
    }
    public List<String> getSamples() {
        return samples;
    }
    public void setSamples(List<String> samples) {
        this.samples = samples;
    }
    public State getState() {
        return state;
    }
    public void setState(State state) {
        this.state = state;
    }
    public String getTestTemplate() {
        return testTemplate;
    }
    public void setTestTemplate(String testTemplate) {
        this.testTemplate = testTemplate;
    }
    public String getHexId() {
        if (hexId == null) {
            return this.id.toHexString();
        }
        return this.hexId;
    }
    public TestingRunResult getTestingRunResult() {
        return testingRunResult;
    }
    public void setTestingRunResult(TestingRunResult testingRunResult) {
        this.testingRunResult = testingRunResult;
    }

    public String getMiniTestingName() {
        return miniTestingName;
    }

    public void setMiniTestingName(String miniTestingName) {
        this.miniTestingName = miniTestingName;
    }

    public OriginalHttpRequest getOriginalHttpRequest() {
        return originalHttpRequest;
    }

    public void setOriginalHttpRequest(OriginalHttpRequest originalHttpRequest) {
        this.originalHttpRequest = originalHttpRequest;
    }

    public OriginalHttpResponse getOriginalHttpResponse() {
        return originalHttpResponse;
    }

    public void setOriginalHttpResponse(OriginalHttpResponse originalHttpResponse) {
        this.originalHttpResponse = originalHttpResponse;
    }

    public AuthMechanism getLoginFlowAuthMechanism() {
        return loginFlowAuthMechanism;
    }

    public void setLoginFlowAuthMechanism(AuthMechanism loginFlowAuthMechanism) {
        this.loginFlowAuthMechanism = loginFlowAuthMechanism;
    }

    public String getLoginFlowNodeId() {
        return loginFlowNodeId;
    }

    public void setLoginFlowNodeId(String loginFlowNodeId) {
        this.loginFlowNodeId = loginFlowNodeId;
    }

    public int getLoginFlowUserId() {
        return loginFlowUserId;
    }

    public void setLoginFlowUserId(int loginFlowUserId) {
        this.loginFlowUserId = loginFlowUserId;
    }

    public boolean isLoginFlowSingleStepOnly() {
        return loginFlowSingleStepOnly;
    }

    public void setLoginFlowSingleStepOnly(boolean loginFlowSingleStepOnly) {
        this.loginFlowSingleStepOnly = loginFlowSingleStepOnly;
    }

    public LoginFlowResponse getLoginFlowResponse() {
        return loginFlowResponse;
    }

    public void setLoginFlowResponse(LoginFlowResponse loginFlowResponse) {
        this.loginFlowResponse = loginFlowResponse;
    }

    public String getRecordedFlowContent() {
        return recordedFlowContent;
    }

    public void setRecordedFlowContent(String recordedFlowContent) {
        this.recordedFlowContent = recordedFlowContent;
    }

    public String getRecordedFlowTokenFetchCommand() {
        return recordedFlowTokenFetchCommand;
    }

    public void setRecordedFlowTokenFetchCommand(String recordedFlowTokenFetchCommand) {
        this.recordedFlowTokenFetchCommand = recordedFlowTokenFetchCommand;
    }

    public String getRecordedFlowRoleName() {
        return recordedFlowRoleName;
    }

    public void setRecordedFlowRoleName(String recordedFlowRoleName) {
        this.recordedFlowRoleName = recordedFlowRoleName;
    }

    public String getRecordedFlowTokenResult() {
        return recordedFlowTokenResult;
    }

    public void setRecordedFlowTokenResult(String recordedFlowTokenResult) {
        this.recordedFlowTokenResult = recordedFlowTokenResult;
    }

    public String getRecordedFlowErrorMessage() {
        return recordedFlowErrorMessage;
    }

    public void setRecordedFlowErrorMessage(String recordedFlowErrorMessage) {
        this.recordedFlowErrorMessage = recordedFlowErrorMessage;
    }
}