package com.akto.dto.test_editor;

import java.util.List;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.TestingRun.State;

public class TestingRunPlayground {

    public static final String ID = "id";

    public static final String TEST_TEMPLATE = "testTemplate";

    public static final String STATE = "state";

    public static final String SAMPLES = "samples";

    public static final String API_INFO_KEY = "apiInfoKey";

    public static final String CREATED_AT = "createdAt";

    public static final String TESTING_RUN_RESULT = "testingRunResult";

    private ObjectId id;

    private String testTemplate;

    private State state;

    private List<String> samples;

    private ApiInfoKey apiInfoKey;

    private int createdAt;

    private TestingRunResult testingRunResult;
    private String miniTestingName;
    private OriginalHttpRequest originalHttpRequest;
    private OriginalHttpResponse originalHttpResponse;
    @BsonIgnore
    private String hexId;
    private TestingRunPlaygroundType testingRunPlaygroundType;

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
        POSTMAN_IMPORTS
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
}
