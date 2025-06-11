package com.akto.testing.kafka_utils;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.TestingRunResultSummary;
import com.akto.store.TestingUtil;

public class TestingConfigurations {

    private static final TestingConfigurations instance = new TestingConfigurations();

    private TestingUtil testingUtil;
    private TestingRunConfig testingRunConfig;
    private boolean debug;
    private int maxConcurrentRequest;
    private List<TestingRunResult> testingRunResultList;
    private TestingRunResultSummary rerunTestingRunResultSummary;
    Map<String, TestConfig> testConfigMap;
    private  Map<ApiInfoKey, RawApi> rawApiMap = new HashMap<>();

    private TestingConfigurations() {
    }

    public static TestingConfigurations getInstance() {
        return instance;
    }

    public synchronized void init(TestingUtil testingUtil, TestingRunConfig testingRunConfig, boolean debug, Map<String, TestConfig> testConfigMap, int maxConcurrentRequests) {
        this.testingUtil = testingUtil;
        this.testingRunConfig = testingRunConfig;
        this.debug = debug;
        this.testConfigMap = testConfigMap;
        this.maxConcurrentRequest = maxConcurrentRequests == -1 ? 10 : maxConcurrentRequests;
    }

    public boolean isDebug() {
        return debug;
    }

    public TestingRunConfig getTestingRunConfig() {
        return testingRunConfig;
    }

    public TestingUtil getTestingUtil() {
        return testingUtil;
    }

    public Map<String, TestConfig> getTestConfigMap() {
        return testConfigMap;
    }

    public int getMaxConcurrentRequest() {
        return maxConcurrentRequest;
    }

    public void setMaxConcurrentRequest(int maxConcurrentRequest) {
        this.maxConcurrentRequest = maxConcurrentRequest;
    }

    public List<TestingRunResult> getTestingRunResultList() {
        return testingRunResultList;
    }

    public void setTestingRunResultList(List<TestingRunResult> testingRunResultList) {
        this.testingRunResultList = testingRunResultList;
    }

    public TestingRunResult getTestingRunResultForApiKeyInfo(ApiInfo.ApiInfoKey apiInfoKey, String testSubCategory) {
        if (testingRunResultList == null || apiInfoKey == null || testSubCategory == null) {
            return null;
        }

        return testingRunResultList.stream()
                .filter(result -> apiInfoKey.equals(result.getApiInfoKey()) && testSubCategory.equals(result.getTestSubType()))
                .findFirst()
                .orElse(null);
    }

    public TestingRunResultSummary getRerunTestingRunResultSummary() {
        return rerunTestingRunResultSummary;
    }

    public void setRerunTestingRunResultSummary(TestingRunResultSummary rerunTestingRunResultSummary) {
        this.rerunTestingRunResultSummary = rerunTestingRunResultSummary;
    }

    public void insertRawApi(ApiInfoKey apiInfoKey, RawApi rawApi) {
        if (rawApi == null || apiInfoKey == null) return;
        instance.rawApiMap.put(apiInfoKey, rawApi);
    }

    public RawApi getRawApi(ApiInfoKey apiInfoKey) {
        if (apiInfoKey == null) return null;
        return instance.rawApiMap.get(apiInfoKey);
    }
}
