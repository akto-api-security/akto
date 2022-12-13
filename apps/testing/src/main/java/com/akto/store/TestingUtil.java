package com.akto.store;

import com.akto.dto.ApiInfo;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestRoles;
import com.akto.dto.type.SingleTypeInfo;

import java.util.List;
import java.util.Map;

public class TestingUtil {

    private AuthMechanism authMechanism;
    private java.util.Map<ApiInfo.ApiInfoKey, List<String>> sampleMessages;
    private Map<String, SingleTypeInfo> singleTypeInfoMap;

    private List<TestRoles> testRoles;

    public TestingUtil(AuthMechanism authMechanism, Map<ApiInfo.ApiInfoKey, List<String>> sampleMessages,
                       Map<String, SingleTypeInfo> singleTypeInfoMap, List<TestRoles> testRoles) {
        this.authMechanism = authMechanism;
        this.sampleMessages = sampleMessages;
        this.singleTypeInfoMap = singleTypeInfoMap;
        this.testRoles = testRoles;
    }

    public TestingUtil() {
    }

    public AuthMechanism getAuthMechanism() {
        return authMechanism;
    }

    public void setAuthMechanism(AuthMechanism authMechanism) {
        this.authMechanism = authMechanism;
    }

    public Map<ApiInfo.ApiInfoKey, List<String>> getSampleMessages() {
        return sampleMessages;
    }

    public void setSampleMessages(Map<ApiInfo.ApiInfoKey, List<String>> sampleMessages) {
        this.sampleMessages = sampleMessages;
    }

    public Map<String, SingleTypeInfo> getSingleTypeInfoMap() {
        return singleTypeInfoMap;
    }

    public void setSingleTypeInfoMap(Map<String, SingleTypeInfo> singleTypeInfoMap) {
        this.singleTypeInfoMap = singleTypeInfoMap;
    }

    public List<TestRoles> getTestRoles() {
        return testRoles;
    }

    public void setTestRoles(List<TestRoles> testRoles) {
        this.testRoles = testRoles;
    }
}
