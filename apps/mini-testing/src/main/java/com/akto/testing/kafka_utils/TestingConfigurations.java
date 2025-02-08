package com.akto.testing.kafka_utils;


import java.util.Map;

import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.store.TestingUtil;

public class TestingConfigurations {

    private static final TestingConfigurations instance = new TestingConfigurations();

    private TestingUtil testingUtil;
    private TestingRunConfig testingRunConfig;
    private boolean debug;
    Map<String, TestConfig> testConfigMap;

    private TestingConfigurations() {
    }

    public static TestingConfigurations getInstance() {
        return instance;
    }

    public synchronized void init(TestingUtil testingUtil, TestingRunConfig testingRunConfig, boolean debug, Map<String, TestConfig> testConfigMap) {
        this.testingUtil = testingUtil;
        this.testingRunConfig = testingRunConfig;
        this.debug = debug;
        this.testConfigMap = testConfigMap;
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
    
}
