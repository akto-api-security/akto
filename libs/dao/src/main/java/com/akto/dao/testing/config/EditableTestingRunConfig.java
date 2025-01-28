package com.akto.dao.testing.config;

import java.util.List;

import com.akto.dto.CollectionConditions.TestConfigsAdvancedSettings;

public class EditableTestingRunConfig {
    private List<String> subCategoriesList;
    private List<TestConfigsAdvancedSettings> testConfigsAdvancedSettings;
    private String overriddenTestAppUrl;
    private String testRoleId;
    private int maxConcurrentRequests;
    private String testingRunHexId;
    private int testRunTime;

    public EditableTestingRunConfig() {

    }

    public List<String> getSubCategoriesList() {
        return subCategoriesList;
    }

    public int getTestRunTime() {
        return testRunTime;
    }

    public void setTestRunTime(int testRunTime) {
        this.testRunTime = testRunTime;
    }

    public void setSubCategoriesList(List<String> subCategoriesList) {
        this.subCategoriesList = subCategoriesList;
    }

    public List<TestConfigsAdvancedSettings> getTestConfigsAdvancedSettings() {
        return testConfigsAdvancedSettings;
    }

    public void setTestConfigsAdvancedSettings(List<TestConfigsAdvancedSettings> testConfigsAdvancedSettings) {
        this.testConfigsAdvancedSettings = testConfigsAdvancedSettings;
    }

    public String getOverriddenTestAppUrl() {
        return overriddenTestAppUrl;
    }

    public void setOverriddenTestAppUrl(String overriddenTestAppUrl) {
        this.overriddenTestAppUrl = overriddenTestAppUrl;
    }

    public String getTestRoleId() {
        return testRoleId;
    }

    public void setTestRoleId(String testRoleId) {
        this.testRoleId = testRoleId;
    }

    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }

    public void setMaxConcurrentRequests(int maxConcurrentRequests) {
        this.maxConcurrentRequests = maxConcurrentRequests;
    }

    public String getTestingRunHexId() {
        return testingRunHexId;
    }

    public void setTestingRunHexId(String testingRunHexId) {
        this.testingRunHexId = testingRunHexId;
    }
}

