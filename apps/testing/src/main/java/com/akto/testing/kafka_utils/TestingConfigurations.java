package com.akto.testing.kafka_utils;


import java.util.List;
import java.util.Map;

import com.akto.dto.ApiInfo;
import com.akto.dto.RawApi;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.TestingRunResultSummary;
import com.akto.store.TestingUtil;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TestingConfigurations {

    private static final TestingConfigurations instance = new TestingConfigurations();

    private TestingUtil testingUtil;
    private TestingRunConfig testingRunConfig;
    private boolean debug;
    private int maxConcurrentRequest;
    private List<TestingRunResult> testingRunResultList;
    private TestingRunResultSummary rerunTestingRunResultSummary;
    Map<String, TestConfig> testConfigMap;

    Map<ApiInfoKey, RawApi> rawApiMap;
    Map<String, String> originalRequestPayloadMap;

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

    public TestingRunResult getTestingRunResultForApiKeyInfo(ApiInfo.ApiInfoKey apiInfoKey, String testSubCategory) {
        if (testingRunResultList == null || apiInfoKey == null || testSubCategory == null) {
            return null;
        }

        return testingRunResultList.stream()
                .filter(result -> apiInfoKey.equals(result.getApiInfoKey()) && testSubCategory.equals(result.getTestSubType()))
                .findFirst()
                .orElse(null);
    }
}
