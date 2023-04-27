package com.akto.testing.yaml_tests;

import com.akto.dto.ApiInfo;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.Auth;
import com.akto.dto.test_editor.ExecutionResult;
import com.akto.dto.test_editor.ExecutorNode;
import com.akto.dto.test_editor.FilterNode;
import com.akto.dto.testing.TestResult;

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

    public SecurityTestTemplate(ApiInfo.ApiInfoKey apiInfoKey, FilterNode filterNode, FilterNode validatorNode, ExecutorNode executorNode ,RawApi rawApi, Map<String, Object> varMap, Auth auth) {
        this.apiInfoKey = apiInfoKey;
        this.filterNode = filterNode;
        this.validatorNode = validatorNode;
        this.executorNode = executorNode;
        this.rawApi = rawApi;
        this.varMap = varMap;
        this.auth = auth;
    }

    public abstract boolean filter();

    public abstract List<ExecutionResult>  executor();

    public abstract List<TestResult> validator(List<ExecutionResult> attempts);

    public List<TestResult> run() {
        boolean valid = filter();
        if (!valid) return null;
        List<ExecutionResult> attempts = executor();
        return validator(attempts);
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
}
