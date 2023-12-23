package com.akto.dto.testing.NodeDetails;

import java.util.List;
import java.util.Map;

import com.akto.dto.CustomAuthType;
import com.akto.dto.RawApi;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.test_editor.ExecutorNode;
import com.akto.dto.test_editor.FilterNode;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.testing.AuthMechanism;

public class YamlNodeDetails extends NodeDetails {
    
    String testId;

    FilterNode validatorNode;

    ExecutorNode executorNode;

    Map<String, Object> varMap;

    List<CustomAuthType> customAuthTypes;

    AuthMechanism authMechanism;
    
    RawApi rawApi;

    ApiInfoKey apiInfoKey;

    public YamlNodeDetails(String testId, FilterNode validatorNode, ExecutorNode executorNode,
            Map<String, Object> varMap, List<CustomAuthType> customAuthTypes, AuthMechanism authMechanism, 
            RawApi rawApi, ApiInfoKey apiInfoKey) {
        this.testId = testId;
        this.validatorNode = validatorNode;
        this.executorNode = executorNode;
        this.varMap = varMap;
        this.customAuthTypes = customAuthTypes;
        this.authMechanism = authMechanism;
        this.rawApi = rawApi;
        this.apiInfoKey = apiInfoKey;
    }

    public YamlNodeDetails() {
    }

    public String getTestId() {
        return testId;
    }

    public void setTestId(String testId) {
        this.testId = testId;
    }

    public FilterNode getValidatorNode() {
        return validatorNode;
    }

    public void setValidatorNode(FilterNode validatorNode) {
        this.validatorNode = validatorNode;
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

    public List<CustomAuthType> getCustomAuthTypes() {
        return customAuthTypes;
    }

    public void setCustomAuthTypes(List<CustomAuthType> customAuthTypes) {
        this.customAuthTypes = customAuthTypes;
    }

    public AuthMechanism getAuthMechanism() {
        return authMechanism;
    }

    public void setAuthMechanism(AuthMechanism authMechanism) {
        this.authMechanism = authMechanism;
    }

    public RawApi getRawApi() {
        return rawApi;
    }

    public void setRawApi(RawApi rawApi) {
        this.rawApi = rawApi;
    }

    public ApiInfoKey getApiInfoKey() {
        return apiInfoKey;
    }

    public void setApiInfoKey(ApiInfoKey apiInfoKey) {
        this.apiInfoKey = apiInfoKey;
    }

    public String validate() {
        return null;        
    }
    
}
