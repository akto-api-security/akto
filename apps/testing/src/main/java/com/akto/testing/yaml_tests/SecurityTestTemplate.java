package com.akto.testing.yaml_tests;

import com.akto.dto.ApiInfo;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.Auth;
import com.akto.dto.test_editor.ExecutorNode;
import com.akto.dto.test_editor.FilterNode;
import com.akto.dto.test_editor.Strategy;
import com.akto.dto.testing.*;
import com.akto.dto.testing.TestResult.TestError;
import com.akto.test_editor.execution.Memory;
import com.akto.test_editor.filter.data_operands_impl.ValidationResult;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.akto.dto.testing.TestResult.TestError.*;

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

    Memory memory;

    public SecurityTestTemplate(ApiInfo.ApiInfoKey apiInfoKey, FilterNode filterNode, FilterNode validatorNode, ExecutorNode executorNode ,RawApi rawApi, Map<String, Object> varMap, Auth auth, AuthMechanism authMechanism, String logId, TestingRunConfig testingRunConfig, Strategy strategy) {
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
    }

    private YamlTestResult getResultWithError(String errorMessage, boolean requiresConfig){
        return getResultWithError(Collections.singletonList(errorMessage), requiresConfig);
    }

    private YamlTestResult getResultWithError(List<String> errorMessages, boolean requiresConfig){
        List<GenericTestResult> testResults = new ArrayList<>();
        /*
         * Only skipped tests calling this function, 
         * thus no need to store original message here.
         */
        TestResult testResult = new TestResult(null, null, errorMessages, 0, false, TestResult.Confidence.HIGH, null);
        testResult.setRequiresConfig(requiresConfig);
        testResults.add(testResult);
        return new YamlTestResult(testResults, null);
    }

    public abstract ValidationResult filter();

    public abstract Set<String> requireConfig();

    public abstract boolean checkAuthBeforeExecution(boolean debug, List<TestingRunResult.TestLog> testLogs);

    public abstract YamlTestResult  executor(boolean debug, List<TestingRunResult.TestLog> testLogs);

    public abstract void triggerMetaInstructions(Strategy strategy, YamlTestResult attempts);

    public YamlTestResult run(boolean debug, List<TestingRunResult.TestLog> testLogs) {
        
        Set<String> missingConfigList = requireConfig();
        if(!missingConfigList.isEmpty()){
            String missingConfigs = "";
            for(String str: missingConfigList){
                missingConfigs += (str + " ");
            }
            return getResultWithError(missingConfigs + " " + ROLE_NOT_FOUND.getMessage(), true);
        }
        
        ValidationResult validationResult = filter();
        boolean valid = validationResult.getIsValid();
        if (!valid) {
            List<String> errorList = new ArrayList<>();
            errorList.add(SKIPPING_EXECUTION_BECAUSE_FILTERS.getMessage());
            errorList.add(validationResult.getValidationReason().replaceFirst("and:", "detailed reason for skipping execution:\n").replaceAll("\n\t","\n"));
            return getResultWithError(errorList, false);
        }
        valid = checkAuthBeforeExecution(debug, testLogs);
        if (!valid) {
            return getResultWithError(SKIPPING_EXECUTION_BECAUSE_AUTH.getMessage(), false);
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

}
