package com.akto.testing.yaml_tests;

import com.akto.dto.ApiInfo;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.*;
import com.akto.dto.testing.*;
import com.akto.dto.testing.TestResult.TestError;
import com.akto.test_editor.execution.Memory;

import java.util.*;

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
    Strategy strategy;

    Map<String, ApiInfo.ApiInfoKey> apiNameToApiInfoKey = new HashMap<>();

    Map<String, ConfigParserResult> workFlowSelectionFilters;

    Memory memory;

    public SecurityTestTemplate(ApiInfo.ApiInfoKey apiInfoKey, FilterNode filterNode, FilterNode validatorNode, ExecutorNode executorNode ,RawApi rawApi, Map<String, Object> varMap, Auth auth, AuthMechanism authMechanism, String logId, TestingRunConfig testingRunConfig, Strategy strategy, Map<String, ConfigParserResult> workFlowSelectionFilters) {
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
        this.strategy = strategy;
        this.workFlowSelectionFilters = workFlowSelectionFilters;
    }

    public abstract boolean filter();

    public abstract boolean workflowFilter();

    public abstract boolean checkAuthBeforeExecution(boolean debug, List<TestingRunResult.TestLog> testLogs);

    public abstract YamlTestResult  executor(boolean debug, List<TestingRunResult.TestLog> testLogs);

    public abstract void triggerMetaInstructions(Strategy strategy, YamlTestResult attempts);

    public YamlTestResult run(boolean debug, List<TestingRunResult.TestLog> testLogs) {

        boolean valid = filter();
        if (!valid) {
            List<GenericTestResult> testResults = new ArrayList<>();
            testResults.add(new TestResult(null, rawApi.getOriginalMessage(), Collections.singletonList("Request API failed to satisfy api_selection_filters block, skipping execution"), 0, false, TestResult.Confidence.HIGH, null));
            return new YamlTestResult(testResults, null);
        }
        valid = checkAuthBeforeExecution(debug, testLogs);
        if (!valid) {
            List<GenericTestResult> testResults = new ArrayList<>();
            testResults.add(new TestResult(null, rawApi.getOriginalMessage(), Collections.singletonList("Request API failed authentication check, skipping execution"), 0, false, TestResult.Confidence.HIGH, null));
            return new YamlTestResult(testResults, null);
        }

        boolean workflowFound = workflowFilter();
        if (!workflowFound) {
            List<GenericTestResult> testResults = new ArrayList<>();
            testResults.add(new TestResult(null, rawApi.getOriginalMessage(), Collections.singletonList("Request API failed to satisfy workflow_selection_filters block, skipping execution"), 0, false, TestResult.Confidence.HIGH, null));
            return new YamlTestResult(testResults, null);
        }

        YamlTestResult attempts = executor(debug, testLogs);
        if(attempts == null || attempts.getTestResults().isEmpty()){
            List<GenericTestResult> res = new ArrayList<>();
            res.add(new TestResult(null, rawApi.getOriginalMessage(), Collections.singletonList(TestError.EXECUTION_FAILED.getMessage()), 0, false, TestResult.Confidence.HIGH, null));
            attempts.setTestResults(res);
        }
        triggerMetaInstructions(strategy, attempts);
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

    public Map<String, ApiInfo.ApiInfoKey> getApiNameToApiInfoKey() {
        return apiNameToApiInfoKey;
    }

    public void setApiNameToApiInfoKey(Map<String, ApiInfo.ApiInfoKey> apiNameToApiInfoKey) {
        this.apiNameToApiInfoKey = apiNameToApiInfoKey;
    }

    public Map<String, ConfigParserResult> getWorkFlowSelectionFilters() {
        return workFlowSelectionFilters;
    }

    public void setWorkFlowSelectionFilters(Map<String, ConfigParserResult> workFlowSelectionFilters) {
        this.workFlowSelectionFilters = workFlowSelectionFilters;
    }
}
