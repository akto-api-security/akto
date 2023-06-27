package com.akto.testing.yaml_tests;

import com.akto.dto.ApiInfo;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.Auth;
import com.akto.dto.test_editor.ExecutorNode;
import com.akto.dto.test_editor.FilterNode;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunConfig;

import java.util.Collections;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class SecurityTestTemplate {

    ApiInfo.ApiInfoKey apiInfoKey;
    FilterNode filterNode;
    FilterNode validatorNode;
    ExecutorNode executorNode;
    RawApi rawApi;
    Map<String, Object> varMap;
    Auth auth;
    AuthMechanism authMechanism;
    String logId;

    TestingRunConfig testingRunConfig;

    public SecurityTestTemplate(ApiInfo.ApiInfoKey apiInfoKey, FilterNode filterNode, FilterNode validatorNode, ExecutorNode executorNode ,RawApi rawApi, Map<String, Object> varMap, Auth auth, AuthMechanism authMechanism, String logId, TestingRunConfig testingRunConfig) {
        this.apiInfoKey = apiInfoKey;
        this.filterNode = filterNode;
        this.validatorNode = validatorNode;
        this.executorNode = executorNode;
        this.rawApi = rawApi;
        this.varMap = varMap;
        this.auth = auth;
        this.authMechanism = authMechanism;
        this.logId = logId;
        this.testingRunConfig = testingRunConfig;
    }

    public abstract boolean filter();

    public abstract boolean checkAuthBeforeExecution();

    public abstract List<TestResult>  executor();

    public List<TestResult> run() {
        boolean valid = filter();
        if (!valid) {
            List<TestResult> testResults = new ArrayList<>();
            testResults.add(new TestResult(null, rawApi.getOriginalMessage(), Collections.singletonList("Request API failed to satisfy api_selection_filters block, skipping execution"), 0, false, TestResult.Confidence.HIGH, null));
            return testResults;
        }
        valid = checkAuthBeforeExecution();
        if (!valid) {
            List<TestResult> testResults = new ArrayList<>();
            testResults.add(new TestResult(null, rawApi.getOriginalMessage(), Collections.singletonList("Request API failed authentication check, skipping execution"), 0, false, TestResult.Confidence.HIGH, null));
            return testResults;
        }
        List<TestResult> attempts = executor();
        return attempts;
    }

    public ApiInfo.ApiInfoKey getApiInfoKey() {
        return apiInfoKey;
    }

    public void setApiInfoKey(ApiInfo.ApiInfoKey apiInfoKey) {
        this.apiInfoKey = apiInfoKey;
    }

    public FilterNode getFilterNode() {
        return filterNode;
    }

    public void setFilterNode(FilterNode filterNode) {
        this.filterNode = filterNode;
    }

    public FilterNode getValidatorNode() {
        return validatorNode;
    }

    public void setValidatorNode(FilterNode validatorNode) {
        this.validatorNode = validatorNode;
    }

    public RawApi getRawApi() {
        return rawApi;
    }

    public void setRawApi(RawApi rawApi) {
        this.rawApi = rawApi;
    }

    public ExecutorNode getExecutorNode() {
        return executorNode;
    }

    public void setExecutorNode(ExecutorNode executorNode) {
        this.executorNode = executorNode;
    }

    public Map<String, Object> getVarMap() {
        return varMap;
    }

    public void setVarMap(Map<String, Object> varMap) {
        this.varMap = varMap;
    }

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    public AuthMechanism getAuthMechanism() {
        return authMechanism;
    }

    public void setAuthMechanism(AuthMechanism authMechanism) {
        this.authMechanism = authMechanism;
    }

    public String getLogId() {
        return logId;
    }

    public void setLogId(String logId) {
        this.logId = logId;
    }
    
}
