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

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TestingRunPlayground {

    public static final String ID = "id";

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

    public static final String RECORDED_FLOW_OWNER_USER_ID = "recordedFlowOwnerUserId";

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
    private String miniTestingName;
    private TestingRunConfig testingRunConfig;
    private OriginalHttpRequest originalHttpRequest;
    private OriginalHttpResponse originalHttpResponse;
    @BsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter
    private String hexId;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private TestingRunPlaygroundType testingRunPlaygroundType;

    private AuthMechanism loginFlowAuthMechanism;

    private String loginFlowNodeId;

    private int loginFlowUserId;

    private boolean loginFlowSingleStepOnly;

    private LoginFlowResponse loginFlowResponse;

    private String recordedFlowContent;

    private String recordedFlowTokenFetchCommand;

    private String recordedFlowRoleName;

    private int recordedFlowOwnerUserId;

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

    public TestingRunPlayground() {
    }

    public String getHexId() {
        if (hexId == null && id != null) {
            return id.toHexString();
        }
        return hexId;
    }
}
