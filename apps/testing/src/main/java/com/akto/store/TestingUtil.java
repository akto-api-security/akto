package com.akto.store;

import com.akto.dto.ApiInfo;
import com.akto.dto.CustomAuthType;
import com.akto.dto.testing.TestRoles;

import java.util.List;
import java.util.Map;

public class TestingUtil {

    private SampleMessageStore sampleMessageStore;
    private List<TestRoles> testRoles;
    private String userEmail;

    private List<CustomAuthType> customAuthTypes;

    public TestingUtil(SampleMessageStore sampleMessageStore, List<TestRoles> testRoles,
                       String userEmail, List<CustomAuthType> customAuthTypes) {
        this.sampleMessageStore = sampleMessageStore;
        this.testRoles = testRoles;
        this.userEmail = userEmail;
        this.customAuthTypes = customAuthTypes;
    }

    public TestingUtil() {
    }

    public Map<ApiInfo.ApiInfoKey, List<String>> getSampleMessages() {
        return sampleMessageStore.getSampleDataMap();
    }
    
    public List<TestRoles> getTestRoles() {
        return testRoles;
    }

    public void setTestRoles(List<TestRoles> testRoles) {
        this.testRoles = testRoles;
    }

    public SampleMessageStore getSampleMessageStore() {
        return sampleMessageStore;
    }

    public void setSampleMessageStore(SampleMessageStore sampleMessageStore) {
        this.sampleMessageStore = sampleMessageStore;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public List<CustomAuthType> getCustomAuthTypes() {
        return customAuthTypes;
    }

    public void setCustomAuthTypes(List<CustomAuthType> customAuthTypes) {
        this.customAuthTypes = customAuthTypes;
    }
}
