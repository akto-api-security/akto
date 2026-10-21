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
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.store.TestingUtil;

public class TestingConfigurations {

    /**
     * When set to a positive integer, replaces the test-run max concurrent requests
     * (including the default of 10). Use this to slow a run down locally.
     */
    public static final String MAX_CONCURRENT_REQUESTS_ENV = "MAX_CONCURRENT_REQUESTS";

    private static final LoggerMaker loggerMaker = new LoggerMaker(TestingConfigurations.class, LogDb.TESTING);
    private static final TestingConfigurations instance = new TestingConfigurations();

    private TestingUtil testingUtil;
    private TestingRunConfig testingRunConfig;
    private boolean debug;
    private int maxConcurrentRequest;
    private List<TestingRunResult> testingRunResultList;
    private TestingRunResultSummary rerunTestingRunResultSummary;
    Map<String, TestConfig> testConfigMap;
    private  Map<ApiInfoKey, RawApi> rawApiMap = new HashMap<>();
    private boolean doNotMarkIssuesAsFixed;
    private boolean runAutomatedTests;
    private String dashboardContext;

    private TestingConfigurations() {
    }

    public static TestingConfigurations getInstance() {
        return instance;
    }

    public synchronized void init(TestingUtil testingUtil, TestingRunConfig testingRunConfig, boolean debug, Map<String, TestConfig> testConfigMap, int maxConcurrentRequests, boolean doNotMarkIssuesAsFixed, boolean runAutomatedTests) {
        // Scope ApiInfoMetaCache to one testing run, not the whole (long-lived) JVM - see its own
        // class doc for why apiAccessTypes/collectionIds can't be cached across runs like
        // ApiCollectionMetaCache's tags can.
        com.akto.util.ApiInfoMetaCache.clear();
        this.testingUtil = testingUtil;
        this.testingRunConfig = testingRunConfig;
        this.debug = debug;
        this.testConfigMap = testConfigMap;
        this.maxConcurrentRequest = resolveMaxConcurrentRequests(maxConcurrentRequests);
        this.doNotMarkIssuesAsFixed = doNotMarkIssuesAsFixed;
        this.runAutomatedTests = runAutomatedTests;
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

    /**
     * Test config value, unless {@code MAX_CONCURRENT_REQUESTS} is a positive integer.
     * {@code -1} from the test run means the default of 10. The env override is used as-is,
     * so it can be lower than that default.
     */
    public static int resolveMaxConcurrentRequests(int configured) {
        int fromConfig = configured == -1 ? 10 : configured;
        Integer fromEnv = readPositiveEnvOverride();
        if (fromEnv != null) {
            loggerMaker.infoAndAddToDb("Using MAX_CONCURRENT_REQUESTS=" + fromEnv
                    + " instead of test config maxConcurrentRequests=" + configured, LogDb.TESTING);
            return fromEnv;
        }
        return fromConfig;
    }

    /**
     * Legacy in-process pool size. Without the env override this stays in [10, 100].
     * With the override, the floor of 10 is skipped so the pool can be smaller; the cap of 100 stays.
     */
    public static int resolveLegacyPoolSize(int configured) {
        Integer fromEnv = readPositiveEnvOverride();
        if (fromEnv != null) {
            int size = Math.min(100, fromEnv);
            loggerMaker.infoAndAddToDb("Using MAX_CONCURRENT_REQUESTS=" + fromEnv
                    + " (legacy pool size " + size + ") instead of test config maxConcurrentRequests=" + configured, LogDb.TESTING);
            return size;
        }
        return Math.min(100, Math.max(10, configured));
    }

    private static Integer readPositiveEnvOverride() {
        String raw = System.getenv(MAX_CONCURRENT_REQUESTS_ENV);
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value > 0) {
                return value;
            }
            loggerMaker.errorAndAddToDb("MAX_CONCURRENT_REQUESTS must be a positive integer, got " + value, LogDb.TESTING);
        } catch (NumberFormatException e) {
            loggerMaker.errorAndAddToDb("Invalid MAX_CONCURRENT_REQUESTS='" + raw + "'", LogDb.TESTING);
        }
        return null;
    }

    public int getMaxConcurrentRequest() {
        return maxConcurrentRequest;
    }

    public void setMaxConcurrentRequest(int maxConcurrentRequest) {
        this.maxConcurrentRequest = maxConcurrentRequest;
    }

    public Map<ApiInfoKey, RawApi> getRawApiMap() {
        return rawApiMap;
    }

    public void setRawApiMap(Map<ApiInfoKey, RawApi> rawApiMap) {
        this.rawApiMap = rawApiMap;
    }

    public TestingRunResultSummary getRerunTestingRunResultSummary() {
        return rerunTestingRunResultSummary;
    }

    public void setRerunTestingRunResultSummary(TestingRunResultSummary rerunTestingRunResultSummary) {
        this.rerunTestingRunResultSummary = rerunTestingRunResultSummary;
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



    public List<TestingRunResult> getTestingRunResultList() {
        return testingRunResultList;
    }

    public void setTestingRunResultList(List<TestingRunResult> testingRunResultList) {
        this.testingRunResultList = testingRunResultList;
    }

    public boolean getDoNotMarkIssuesAsFixed() {
        return doNotMarkIssuesAsFixed;
    }

    public void setDoNotMarkIssuesAsFixed(boolean doNotMarkIssuesAsFixed) {
        this.doNotMarkIssuesAsFixed = doNotMarkIssuesAsFixed;
    }

    public boolean isRunAutomatedTests() {
        return runAutomatedTests;
    }

    public void setRunAutomatedTests(boolean runAutomatedTests) {
        this.runAutomatedTests = runAutomatedTests;
    }

    public String getDashboardContext() {
        return dashboardContext;
    }

    public void setDashboardContext(String dashboardContext) {
        this.dashboardContext = dashboardContext;
    }

}