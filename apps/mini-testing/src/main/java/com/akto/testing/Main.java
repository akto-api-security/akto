package com.akto.testing;

import com.akto.RuntimeMode;
import com.akto.agent.AgentClient;
import com.akto.agent.AgenticUtils;
import com.akto.billing.UsageMetricUtils;
import com.akto.crons.GetRunningTestsStatus;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.TestConfigYamlParser;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.*;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.Organization;
import com.akto.dto.billing.SyncLimit;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.test_editor.TestingRunPlayground;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.testing.*;
import com.akto.dto.testing.TestingEndpoints.Operator;
import com.akto.dto.testing.TestingRun.State;
import com.akto.dto.testing.rate_limit.ApiRateLimit;
import com.akto.dto.testing.rate_limit.GlobalApiRateLimit;
import com.akto.dto.testing.rate_limit.RateLimitHandler;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.usage.MetricTypes;
import com.akto.github.GithubUtils;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.metrics.AllMetrics;
import com.akto.metrics.ModuleInfoWorker;
import com.akto.test_editor.execution.Executor;
import com.akto.testing.kafka_utils.ConsumerUtil;
import com.akto.testing.kafka_utils.Producer;
import com.akto.testing.kafka_utils.TestingConfigurations;
import com.akto.usage.OrgUtils;
import com.akto.util.Constants;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;
import com.akto.util.DashboardMode;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import static com.akto.testing.Utils.readJsonContentFromFile;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {
    private static final LoggerMaker loggerMaker = new LoggerMaker(Main.class, LogDb.TESTING);

    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    public static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static final ScheduledExecutorService testTelemetryScheduler = Executors.newScheduledThreadPool(2);

    public static final ScheduledExecutorService schedulerAccessMatrix = Executors.newScheduledThreadPool(2);

    public static boolean SKIP_SSRF_CHECK = ("true".equalsIgnoreCase(System.getenv("SKIP_SSRF_CHECK")) || !DashboardMode.isSaasDeployment());
    public static final boolean IS_SAAS = "true".equalsIgnoreCase(System.getenv("IS_SAAS"));
    
    private static final String customMiniTestingServiceName;
    static {
        customMiniTestingServiceName = System.getenv("MINI_TESTING_NAME") == null? "Default_" + UUID.randomUUID().toString().substring(0, 4) : System.getenv("MINI_TESTING_NAME");
    }

    private static void setupRateLimitWatcher (AccountSettings settings) {
        
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                if (settings == null) {
                    return;
                }
                int globalRateLimit = settings.getGlobalRateLimit();
                int accountId = settings.getId();
                Map<ApiRateLimit, Integer> rateLimitMap =  RateLimitHandler.getInstance(accountId).getRateLimitsMap();
                rateLimitMap.clear();
                rateLimitMap.put(new GlobalApiRateLimit(globalRateLimit), globalRateLimit);
            }
        }, 0, 1, TimeUnit.MINUTES);
    }

    public static void checkForPlaygroundTest(AccountSettings accountSettings){
        scheduler.scheduleWithFixedDelay(new Runnable() {
            public void run() {
                Context.accountId.set(accountSettings.getId());
                int timestamp = Context.now()-5*60;
                TestingRunPlayground testingRunPlayground =  dataActor.getCurrentTestingRunDetailsFromEditor(timestamp); // fetch from Db
                
                if (testingRunPlayground == null) {
                    return;
                }

                switch (testingRunPlayground.getTestingRunPlaygroundType()) {
                    case TEST_EDITOR_PLAYGROUND:
                        handleTestEditorPlayground(testingRunPlayground);
                        break;
                    case POSTMAN_IMPORTS:
                        handlePostmanImports(testingRunPlayground);
                        break;
                }
            }
        }, 0, 2, TimeUnit.SECONDS);
    }

    private static void handleTestEditorPlayground(TestingRunPlayground testingRunPlayground) {
        TestExecutor executor = new TestExecutor();
        TestConfig testConfig = null;
        try {
            testConfig = TestConfigYamlParser.parseTemplate(testingRunPlayground.getTestTemplate());
        } catch (Exception e) {
            return;
        }
        ApiInfo.ApiInfoKey infoKey = testingRunPlayground.getApiInfoKey();

        List<String> sampleData = testingRunPlayground.getSamples(); // get sample data from DB
        AgenticUtils.checkAndInitializeAgent(Collections.singleton(infoKey.getApiCollectionId()), true, testConfig.getApiSelectionFilters().getNode());

        List<TestingRunResult.TestLog> testLogs = new ArrayList<>();
        Map<ApiInfo.ApiInfoKey, List<String>> sampleDataMap = new HashMap<>();
        sampleDataMap.put(infoKey, sampleData); // get sample list from DB
        SampleMessageStore messageStore = SampleMessageStore.create(sampleDataMap);

        List<CustomAuthType> customAuthTypes = dataActor.fetchCustomAuthTypes();

        TestingUtil testingUtil = new TestingUtil(messageStore, null, null, customAuthTypes);
        String message = messageStore.getSampleDataMap().get(infoKey).get(messageStore.getSampleDataMap().get(infoKey).size() - 1);
        TestingRunResult testingRunResult = executor.runTestNew(infoKey, null, testingUtil, null, testConfig, null, true, testLogs, message);
        testingRunResult.setId(testingRunPlayground.getId());
        testingRunResult.setTestRunId(testingRunPlayground.getId());
        testingRunResult.setTestRunResultSummaryId(testingRunPlayground.getId());

        GenericTestResult testRes = testingRunResult.getTestResults().get(0);
        if (testRes instanceof TestResult) {
            List<TestResult> list = new ArrayList<>();
            for(GenericTestResult testResult: testingRunResult.getTestResults()){
                list.add((TestResult) testResult);
            }
            testingRunResult.setSingleTestResults(list);
        } else {
            List<MultiExecTestResult> list = new ArrayList<>();
            for(GenericTestResult testResult: testingRunResult.getTestResults()){
                list.add((MultiExecTestResult) testResult);
            }
            testingRunResult.setMultiExecTestResults(list);
        }
        testingRunResult.setTestResults(null);
        testingRunResult.setTestLogs(null);
        testingRunPlayground.setTestingRunResult(testingRunResult);
        // update testingRunPlayground in DB
        dataActor.updateTestingRunPlayground(testingRunPlayground);
    }

    private static void handlePostmanImports(TestingRunPlayground testingRunPlayground) {
        // For Postman imports, we use the original request/response directly
        loggerMaker.infoAndAddToDb("Running requests for testingRunPlayground, starting: " + testingRunPlayground.getId());
        OriginalHttpRequest originalRequest = testingRunPlayground.getOriginalHttpRequest();
        if (originalRequest == null) {
            return;
        }
        loggerMaker.infoAndAddToDb("Running requests for testingRunPlayground, found req: " + testingRunPlayground.getId());
        OriginalHttpResponse res;
        TestingRunConfig testingRunConfig = new TestingRunConfig();
        try {
            res = ApiExecutor.sendRequest(originalRequest, true, testingRunConfig, false, new ArrayList<>());
        } catch (Exception e) {
            res = new OriginalHttpResponse();
        }
        testingRunPlayground.setOriginalHttpResponse(res);
        loggerMaker.infoAndAddToDb("Running requests for testingRunPlayground, updating res: " + testingRunPlayground.getId());
        // update testingRunPlayground in DB
        dataActor.updateTestingRunPlayground(testingRunPlayground);
    }

    public static Set<Integer> extractApiCollectionIds(List<ApiInfo.ApiInfoKey> apiInfoKeyList) {
        Set<Integer> ret = new HashSet<>();
        for(ApiInfo.ApiInfoKey apiInfoKey: apiInfoKeyList) {
            ret.add(apiInfoKey.getApiCollectionId());
        }

        return ret;
    }
    private static final int LAST_TEST_RUN_EXECUTION_DELTA = 5 * 60;
    private static final int MAX_RETRIES_FOR_FAILED_SUMMARIES = 3;

    private static BasicDBObject checkIfAlreadyTestIsRunningOnMachine(){
        // this will return true if consumer is running and this the latest summary of the testing run
        // and also the summary should be in running state
        try {
            BasicDBObject currentTestInfo = readJsonContentFromFile(Constants.TESTING_STATE_FOLDER_PATH, Constants.TESTING_STATE_FILE_NAME, BasicDBObject.class);
            if(currentTestInfo == null){
                return null;
            }
            if(!currentTestInfo.getBoolean("CONSUMER_RUNNING", false)){
                return null;
            }
            String testingRunId = currentTestInfo.getString("testingRunId");
            String testingRunSummaryId = currentTestInfo.getString("summaryId");

            int accountID = currentTestInfo.getInt("accountId");
            Context.accountId.set(accountID);

            TestingRunResultSummary testingRunResultSummary = dataActor.fetchTestingRunResultSummary(testingRunSummaryId);
            if(testingRunResultSummary == null || testingRunResultSummary.getState() == null ||  testingRunResultSummary.getState() != State.RUNNING){
                return null;
            }
            Bson filterQ = Filters.eq(TestingRunResultSummary.TESTING_RUN_ID, new ObjectId(testingRunId));
            TestingRunResultSummary latestSummary = dataActor.findLatestTestingRunResultSummary(filterQ);
            if(latestSummary.getHexId().equals(testingRunSummaryId)){
                return currentTestInfo;
            }else{
                return null;
            }
        } catch (Exception e) {
            loggerMaker.error("Error in reading the testing state file: " + e.getMessage());
            return null;
        }
    }

    private static void setTestingRunConfig(TestingRun testingRun, TestingRunResultSummary trrs) {
        long timestamp = testingRun.getId().getTimestamp();
        long seconds = Context.now() - timestamp;
        loggerMaker.infoAndAddToDb("Found one + " + testingRun.getId().toHexString() + " created: " + seconds + " seconds ago", LogDb.TESTING);

        TestingRunConfig configFromTrrs = null;
        TestingRunConfig baseConfig = null;

        if (trrs != null && trrs.getTestIdConfig() > 1) {
            loggerMaker.infoAndAddToDb("evaluating first trrs condition");
            configFromTrrs = dataActor.findTestingRunConfig(trrs.getTestIdConfig());
            loggerMaker.infoAndAddToDb("Found testing run trrs config with id :" + configFromTrrs.getId(), LogDb.TESTING);
        }

        if (testingRun.getTestIdConfig() > 1) {
            loggerMaker.infoAndAddToDb("evaluating second trrs condition");
            int maxRetries = 5;
            for (int i = 0; i < maxRetries; i++) {
                loggerMaker.infoAndAddToDb("fetching baseconfig in second trrs condition");
                baseConfig = dataActor.findTestingRunConfig(testingRun.getTestIdConfig());
                if (baseConfig != null) {
                    break;
                }
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                }
            }

            loggerMaker.infoAndAddToDb("Found testing run base config with id :" + baseConfig.getId(), LogDb.TESTING);
        }

        if (configFromTrrs == null) {
            testingRun.setTestingRunConfig(baseConfig);
        } else {
            configFromTrrs.rebaseOn(baseConfig);
            testingRun.setTestingRunConfig(configFromTrrs);
        }
        if(testingRun.getTestingRunConfig() != null){
            loggerMaker.info(testingRun.getTestingRunConfig().toString());
        }else{
            loggerMaker.info("Testing run config is null.");
        }
    }

    private static boolean matrixAnalyzerRunning = false;

    private static void singleTypeInfoInit(int accountId) {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                List<CustomDataType> customDataTypes = dataActor.fetchCustomDataTypes();
                loggerMaker.infoAndAddToDb("customDataType size: " + customDataTypes.size());
                List<AktoDataType> aktoDataTypes = dataActor.fetchAktoDataTypes();
                List<CustomAuthType> customAuthTypes = dataActor.fetchCustomAuthTypes();
                SingleTypeInfo.fetchCustomDataTypes(accountId, customDataTypes, aktoDataTypes);
                SingleTypeInfo.fetchCustomAuthTypes(accountId, customAuthTypes);
            }
        }, 0, 5, TimeUnit.MINUTES);
    }

    //returns true if test is not supposed to run
    private static boolean handleRerunTestingRunResult(TestingRunResultSummary originalSummary) {
        TestingConfigurations config = TestingConfigurations.getInstance();
        config.setRerunTestingRunResultSummary(null);

        if (originalSummary == null || originalSummary.getOriginalTestingRunResultSummaryId() == null) {
            config.setTestingRunResultList(null);
            return false;
        }

        String summaryIdHexId = originalSummary.getOriginalTestingRunResultSummaryHexId();
        List<TestingRunResult> testingRunResultList = dataActor.fetchRerunTestingRunResult(summaryIdHexId);
        if (testingRunResultList == null) {
            dataActor.deleteTestRunResultSummary(originalSummary.getId().toHexString());
            loggerMaker.infoAndAddToDb("Deleting TRRS for rerun case, no testing run result found, TRRS_ID: " + originalSummary.getId().toHexString(), LogDb.TESTING);
            return true;
        }

        //Updating start time stamp as current time stamp in case of rerun
        dataActor.updateStartTsTestRunResultSummary(summaryIdHexId);
        config.setRerunTestingRunResultSummary(originalSummary);
        config.setTestingRunResultList(testingRunResultList);
        return false;
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        try {
            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    if (PrometheusMetricsHandler.isModuleBusy()) {
                        loggerMaker.errorAndAddToDb("Module found busy while shutdown hook was triggered for mini-testing: " + customMiniTestingServiceName);
                    }
                    loggerMaker.infoAndAddToDb("Shutdown hook triggered for mini-testing: " + customMiniTestingServiceName);
                    shutdown();
                }
            });
            runModule();
        } finally {
            if (PrometheusMetricsHandler.isModuleBusy()) {
                loggerMaker.errorAndAddToDb("Module found busy while shutting down mini-testing: " + customMiniTestingServiceName);
            }
            loggerMaker.infoAndAddToDb("Shutting down mini-testing: " + customMiniTestingServiceName);
            shutdown();
        }
    }

    private static void shutdown() {
        try {
            PrometheusMetricsHandler.shutdownServer();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Exception while performing shutdown tasks");
        }

        loggerMaker.infoAndAddToDb("Invoking System.exit(0) for mini-testing: " + customMiniTestingServiceName);
        System.exit(0);
    }

    private static void runModule() throws InterruptedException, IOException {
        PrometheusMetricsHandler.init();
        AccountSettings accountSettings = dataActor.fetchAccountSettings();
        dataActor.modifyHybridTestingSetting(RuntimeMode.isHybridDeployment());
        setupRateLimitWatcher(accountSettings);
        checkForPlaygroundTest(accountSettings);

        if (!SKIP_SSRF_CHECK) {
            Setup setup = dataActor.fetchSetup();
            String dashboardMode = setup.getDashboardMode();
            if (dashboardMode != null) {
                boolean isSaas = dashboardMode.equalsIgnoreCase(DashboardMode.SAAS.name());
                if (!isSaas) SKIP_SSRF_CHECK = true;
            }
        }

        Producer testingProducer = new Producer();
        ConsumerUtil testingConsumer = new ConsumerUtil();
        TestCompletion testCompletion = new TestCompletion();
        ModuleInfoWorker.init(ModuleInfo.ModuleType.MINI_TESTING, dataActor, customMiniTestingServiceName);
        LoggerMaker.setModuleId(customMiniTestingServiceName);
        loggerMaker.infoAndAddToDb("Starting.......", LogDb.TESTING);

        if(Constants.IS_NEW_TESTING_ENABLED){
            boolean val = Utils.createFolder(Constants.TESTING_STATE_FOLDER_PATH);
            loggerMaker.info("Testing info folder status: " + val);
        }

        schedulerAccessMatrix.scheduleAtFixedRate(new Runnable() {
            public void run() {
                if (matrixAnalyzerRunning) {
                    return;
                }
                Context.accountId.set(accountSettings.getId());
                AccessMatrixAnalyzer matrixAnalyzer = new AccessMatrixAnalyzer();
                try {
                    matrixAnalyzerRunning = true;
                    matrixAnalyzer.run();
                } catch (Exception e) {
                    loggerMaker.infoAndAddToDb("could not run matrixAnalyzer: " + e.getMessage(), LogDb.TESTING);
                } finally {
                    matrixAnalyzerRunning = false;
                }
            }
        }, 0, 1, TimeUnit.MINUTES);

        loggerMaker.infoAndAddToDb("sun.arch.data.model: " +  System.getProperty("sun.arch.data.model"), LogDb.TESTING);
        loggerMaker.infoAndAddToDb("os.arch: " + System.getProperty("os.arch"), LogDb.TESTING);
        loggerMaker.infoAndAddToDb("os.version: " + System.getProperty("os.version"), LogDb.TESTING);
        
        Map<Integer, Integer> logSentMap = new HashMap<>();

        int accountId = accountSettings.getId();
        Context.accountId.set(accountId);
        DataActor.actualAccountId = accountId;
        GetRunningTestsStatus.getRunningTests().getStatusOfRunningTests();

          BasicDBObject currentTestInfo = null;
        if(Constants.IS_NEW_TESTING_ENABLED){
            currentTestInfo = checkIfAlreadyTestIsRunningOnMachine();
        }

        if(currentTestInfo != null){
            try {
                loggerMaker.infoAndAddToDb("Tests were already running on this machine, thus resuming the test for account: "+ accountId, LogDb.TESTING);
                Organization organization = OrgUtils.getOrganizationCached(accountId);
                FeatureAccess featureAccess = UsageMetricUtils.getFeatureAccess(organization, MetricTypes.TEST_RUNS);
                SyncLimit syncLimit = featureAccess.fetchSyncLimit();
                String testingRunSummaryId = currentTestInfo.getString("summaryId");
                //check if currently running testrun is part of rerun
                ObjectId summaryId = new ObjectId(testingRunSummaryId);

                TestingRunResultSummary rerunTestingRunResultSummary = dataActor.fetchRerunTestingRunResultSummary(testingRunSummaryId);
                //fill testingRunResult in TestingConfigurations
                if(!handleRerunTestingRunResult(rerunTestingRunResultSummary)) {
                    TestingRun testingRun = dataActor.findTestingRun(testingRunSummaryId);
                    TestingRunConfig baseConfig = dataActor.findTestingRunConfig(testingRun.getTestIdConfig());
                    testingRun.setTestingRunConfig(baseConfig);
                    testingProducer.initProducer(testingRun, summaryId, true, syncLimit);
                    int maxRunTime = testingRun.getTestRunTime() <= 0 ? 30*60 : testingRun.getTestRunTime();
                    testingConsumer.init(maxRunTime);

                    // mark the test completed here
                    testCompletion.markTestAsCompleteAndRunFunctions(testingRun, summaryId, System.currentTimeMillis());

                    // if (StringUtils.hasLength(AKTO_SLACK_WEBHOOK) ) {
                    //     try {
                    //         CustomTextAlert customTextAlert = new CustomTextAlert("Test completed for accountId=" + accountId + " testingRun=" + testingRun.getHexId() + " summaryId=" + summaryId.toHexString() + " : @Arjun you are up now. Make your time worth it. :)");
                    //         SLACK_INSTANCE.send(AKTO_SLACK_WEBHOOK, customTextAlert.toJson());
                    //     } catch (Exception e) {
                    //         loggerMaker.error("Error sending slack alert for completion of test", e);
                    //     }

                    // }

                    // deleteScheduler.execute(() -> {
                    //     Context.accountId.set(accountId);
                    //     try {
                    //         deleteNonVulnerableResults();

                    //     } catch (Exception e) {
                    //         loggerMaker.errorAndAddToDb(e, "Error in deleting testing run results");
                    //     }
                    // });
                }
            } catch (Exception e) {
                loggerMaker.error("Error in running failed tests from file.", e);
            }
        }

        singleTypeInfoInit(accountId);

        while (true) {
            PrometheusMetricsHandler.markModuleIdle();
            int start = Context.now();
            long startDetailed = System.currentTimeMillis();
            int delta = start - 20*60;
            if (accountSettings.getTimeForScheduledSummaries() > 0) {
                delta = start - accountSettings.getTimeForScheduledSummaries();
            }

            TestingConfigurations config = TestingConfigurations.getInstance();
            TestingRunResultSummary trrs = dataActor.findPendingTestingRunResultSummary(start, delta, customMiniTestingServiceName);
            boolean isSummaryRunning = trrs != null && trrs.getState().equals(State.RUNNING);
            boolean isTestingRunResultRerunCase = trrs != null && trrs.getOriginalTestingRunResultSummaryId() != null;
            if (isTestingRunResultRerunCase) {
                trrs.setOriginalTestingRunResultSummaryId(new ObjectId(trrs.getOriginalTestingRunResultSummaryHexId()));
            }
            TestingRun testingRun;
            ObjectId summaryId = null;
            if (trrs == null) {
                delta = Context.now() - 20*60;
                testingRun = dataActor.findPendingTestingRun(delta, customMiniTestingServiceName);
            } else {
                summaryId = isTestingRunResultRerunCase ? trrs.getOriginalTestingRunResultSummaryId() : trrs.getId();
                loggerMaker.infoAndAddToDb("Found trrs " + trrs.getHexId() + (isTestingRunResultRerunCase ? " (rerun case) " : " ") + "for account: " + accountId);
                testingRun = dataActor.findTestingRun(trrs.getTestingRunId().toHexString());
            }

            if (testingRun == null ||
                    (testingRun.getMiniTestingServiceName() != null &&
                            !testingRun.getMiniTestingServiceName().equalsIgnoreCase(customMiniTestingServiceName))) {
                Thread.sleep(1000);
                continue;
            }

            if (handleRerunTestingRunResult(trrs)) {
                continue;
            }

            if (testingRun.getState().equals(State.STOPPED)) {
                loggerMaker.infoAndAddToDb("Testing run stopped");
                if (trrs != null) {
                    if (isTestingRunResultRerunCase) {
                        // For TRR-rerun case, delete the rerun summary and clean up configurations
                        dataActor.deleteTestRunResultSummary(trrs.getId().toHexString());
                        config.setTestingRunResultList(null);
                        config.setRerunTestingRunResultSummary(null);
                        loggerMaker.infoAndAddToDb("Deleted for TestingRunResult rerun case for stopped testrun TRRS: " + trrs.getId());
                    } else {
                        loggerMaker.infoAndAddToDb("Stopping TRRS: " + trrs.getId());
                        dataActor.updateTestRunResultSummaryNoUpsert(trrs.getId().toHexString());
                        loggerMaker.infoAndAddToDb("Stopped TRRS: " + trrs.getId());
                    }
                }
                continue;
            }

            loggerMaker.infoAndAddToDb("Starting test for accountID: " + accountId);

            boolean isTestingRunRunning = testingRun.getState().equals(State.RUNNING);

            if (UsageMetricUtils.checkTestRunsOverage(accountId)) {
                if (isTestingRunResultRerunCase) {
                    // For TRR-rerun case, delete the rerun summary and clean up configurations
                    dataActor.deleteTestRunResultSummary(trrs.getId().toHexString());
                    config.setTestingRunResultList(null);
                    config.setRerunTestingRunResultSummary(null);
                    loggerMaker.infoAndAddToDb("Deleted for TestingRunResult rerun case for failed testrun TRRS: " + trrs.getId());
                } else {
                    int lastSent = logSentMap.getOrDefault(accountId, 0);
                    if (start - lastSent > LoggerMaker.LOG_SAVE_INTERVAL) {
                        logSentMap.put(accountId, start);
                        loggerMaker.infoAndAddToDb("Test runs overage detected for account: " + accountId
                                + " . Failing test run : " + start, LogDb.TESTING);
                    }
                    dataActor.updateTestingRun(testingRun.getId().toHexString());
                    dataActor.updateTestRunResultSummary(summaryId.toHexString());
                }
                continue;
            }

            try {
                fillTestingEndpoints(testingRun);
                // continuous testing condition
                if (testingRun.getPeriodInSeconds() == -1) {
                    CustomTestingEndpoints eps = (CustomTestingEndpoints) testingRun.getTestingEndpoints();
                    if (eps == null || eps.getApisList().size() == 0) {
                        dataActor.updateTestingRunAndMarkCompleted(testingRun.getId().toHexString(), Context.now() + 5 * 60);
                        continue;
                    }
                }
                setTestingRunConfig(testingRun, trrs);

                boolean maxRetriesReached = false;

                if (isSummaryRunning || isTestingRunRunning) {
                    loggerMaker.infoAndAddToDb("TRRS or TR is in running state, checking if it should run it or not");
                    TestingRunResultSummary testingRunResultSummary;
                    if (trrs != null) {
                        testingRunResultSummary = trrs;
                    } else {
                        Map<ObjectId, TestingRunResultSummary> objectIdTestingRunResultSummaryMap = dataActor.fetchTestingRunResultSummaryMap(testingRun.getId().toHexString());
                        testingRunResultSummary = objectIdTestingRunResultSummaryMap.get(testingRun.getId());
                    }
                    // For rerun case, we need to check the original test results
                    List<TestingRunResult> testingRunResults;
                    if (testingRunResultSummary != null) {
                        if (isTestingRunResultRerunCase) {
                            testingRunResults = dataActor.fetchLatestTestingRunResult(testingRunResultSummary.getOriginalTestingRunResultSummaryId().toHexString());
                        } else {
                            testingRunResults = dataActor.fetchLatestTestingRunResult(testingRunResultSummary.getId().toHexString());
                        }

                        if (testingRunResults != null && !testingRunResults.isEmpty()) {
                            TestingRunResult testingRunResult = testingRunResults.get(0);
                            if (Context.now() - testingRunResult.getEndTimestamp() < LAST_TEST_RUN_EXECUTION_DELTA) {
                                loggerMaker.infoAndAddToDb("Skipping test run as it was executed recently, TRR_ID:"
                                        + testingRunResult.getHexId() + ", TRRS_ID:" + testingRunResultSummary.getHexId()
                                        + (isTestingRunResultRerunCase ? " (rerun case) " : " ")
                                        + " TR_ID:" + testingRun.getHexId(), LogDb.TESTING);
                                continue;
                            } else {
                                loggerMaker.infoAndAddToDb("Test run was executed long ago, TRR_ID:"
                                        + testingRunResult.getHexId() + ", TRRS_ID:" + testingRunResultSummary.getHexId()
                                        + (isTestingRunResultRerunCase ? " (rerun case) " : " ")
                                        + " TR_ID:" + testingRun.getHexId(), LogDb.TESTING);
                                int maxRunTime = testingRun.getTestRunTime() <= 0 ? 30*60 : testingRun.getTestRunTime();
                                Bson filterQ = Filters.and(
                                    Filters.gte(TestingRunResultSummary.START_TIMESTAMP, (Context.now() - ((MAX_RETRIES_FOR_FAILED_SUMMARIES + 1) * maxRunTime))),
                                    Filters.eq(TestingRunResultSummary.TESTING_RUN_ID, testingRun.getId()),
                                    Filters.eq(TestingRunResultSummary.STATE, State.FAILED)
                                );

                                int countFailedSummaries = (int) dataActor.countTestingRunResultSummaries(filterQ);
                                TestingRunResultSummary runResultSummary = dataActor.fetchTestingRunResultSummary(testingRunResultSummary.getId().toHexString());
                                TestingRunResultSummary summary;
                                if(countFailedSummaries >= (MAX_RETRIES_FOR_FAILED_SUMMARIES - 1)){
                                    summary = dataActor.updateIssueCountInSummary(testingRunResultSummary.getId().toHexString(), runResultSummary.getCountIssues());
                                    loggerMaker.infoAndAddToDb("Max retries level reached for TRR_ID: " + testingRun.getHexId(), LogDb.TESTING);
                                    maxRetriesReached = true;
                                }else{
                                    summary = dataActor.markTestRunResultSummaryFailed(testingRunResultSummary.getId().toHexString());
                                }
    
                                runResultSummary = dataActor.fetchTestingRunResultSummary(testingRunResultSummary.getId().toHexString());
                                if (summary == null) {
                                    loggerMaker.infoAndAddToDb("Skipping because some other thread picked it up, TRRS_ID:" + testingRunResultSummary.getHexId() + " TR_ID:" + testingRun.getHexId(), LogDb.TESTING);
                                    continue;
                                }
                                GithubUtils.publishGithubComments(runResultSummary);
                            }
                        } else {
                            loggerMaker.infoAndAddToDb("No executions made for this test, will need to restart it, TRRS_ID:"
                                    + testingRunResultSummary.getHexId()
                                    + (isTestingRunResultRerunCase ? " (rerun case) " : " ")
                                    + " TR_ID:" + testingRun.getHexId(), LogDb.TESTING);
                            //won't reach here for testing run result rerun case, as there will be a minimum of 1 TRR
                            //for safety delete run result.
                            if (isTestingRunResultRerunCase) {
                                dataActor.deleteTestRunResultSummary(testingRunResultSummary.getId().toHexString());
                                config.setTestingRunResultList(null);
                                config.setRerunTestingRunResultSummary(null);
                                loggerMaker.infoAndAddToDb("Deleted for TestingRunResult rerun case for failed testrun TRRS: " + testingRunResultSummary.getId(), LogDb.TESTING);
                                continue;
                            }
                            TestingRunResultSummary summary = dataActor.markTestRunResultSummaryFailed(testingRunResultSummary.getId().toHexString());
                            if (summary == null) {
                                loggerMaker.infoAndAddToDb("Skipping because some other thread picked it up, TRRS_ID:" + testingRunResultSummary.getHexId() + " TR_ID:" + testingRun.getHexId(), LogDb.TESTING);
                                continue;
                            }
                        }

                        // insert new summary based on old summary
                        if(maxRetriesReached){
                            loggerMaker.infoAndAddToDb("Exiting out as maxRetries have been reached for testingRun: " + testingRun.getHexId(), LogDb.TESTING);
                        }else{
                            if (summaryId != null) {
                                trrs.setId(new ObjectId());
                                trrs.setStartTimestamp(start);
                                trrs.setState(State.RUNNING);
                                dataActor.insertTestingRunResultSummary(trrs);
                                summaryId = trrs.getId();
                            } else {
                                trrs = dataActor.createTRRSummaryIfAbsent(testingRun.getHexId(), start);
                                summaryId = trrs.getId();
                            }
                        }
                    } else {
                        loggerMaker.infoAndAddToDb("No summary found. Let's run it as usual");
                    }
                }

                if (summaryId == null) {
                    trrs = dataActor.createTRRSummaryIfAbsent(testingRun.getHexId(), start);
                    summaryId = trrs.getId();
                }

                TestExecutor testExecutor = new TestExecutor();
                if (trrs.getState() == State.SCHEDULED) {
                    if (trrs.getMetadata()!= null && trrs.getMetadata().containsKey("pull_request_id") && trrs.getMetadata().containsKey("commit_sha_head") ) {
                        //case of github status push
                        GithubUtils.publishGithubStatus(trrs);

                    }
                }

                Organization organization = OrgUtils.getOrganizationCached(accountId);
                FeatureAccess featureAccess = UsageMetricUtils.getFeatureAccess(organization, MetricTypes.TEST_RUNS);
                SyncLimit syncLimit = featureAccess.fetchSyncLimit();
                Executor.clearRoleCache();

                if(!maxRetriesReached){
                    if(Constants.IS_NEW_TESTING_ENABLED){
                        int maxRunTime = testingRun.getTestRunTime() <= 0 ? 30*60 : testingRun.getTestRunTime();
                        testingProducer.initProducer(testingRun, summaryId, false, syncLimit);
                        testingConsumer.init(maxRunTime);
                    }else{
                        testExecutor.init(testingRun, summaryId, syncLimit, false);
                    }
                    AllMetrics.instance.setTestingRunCount(1);
                }
                // raiseMixpanelEvent(summaryId, testingRun, accountId);
            } catch (Exception e) {
                e.printStackTrace();
                loggerMaker.errorAndAddToDb(e, "Error in init " + e);
            }

            testCompletion.markTestAsCompleteAndRunFunctions(testingRun, summaryId, startDetailed);

            Thread.sleep(1000);
        }
    }

    private static void fillTestingEndpoints(TestingRun tr) {
        if (tr.getPeriodInSeconds() != -1) {
            return;
        }

        int apiCollectionId;
        if (tr.getTestingEndpoints() instanceof CollectionWiseTestingEndpoints) {
            CollectionWiseTestingEndpoints eps = (CollectionWiseTestingEndpoints) tr.getTestingEndpoints();
            apiCollectionId = eps.getApiCollectionId();
        } else if (tr.getTestingEndpoints() instanceof CustomTestingEndpoints) {
            CustomTestingEndpoints eps = (CustomTestingEndpoints) tr.getTestingEndpoints();
            apiCollectionId = eps.getApisList().get(0).getApiCollectionId();
        } else {
            return;
        }

        int st = tr.getEndTimestamp();
        int et = 0;
        if (st == -1) {
            st = 0;
            et = Context.now() + 20 * 60;
        } else {
            et = st + 20 * 60;
        }

        List<ApiInfo.ApiInfoKey> endpoints = dataActor.fetchLatestEndpointsForTesting(st, et, apiCollectionId);
        CustomTestingEndpoints newEps = new CustomTestingEndpoints(endpoints, Operator.AND);
        tr.setTestingEndpoints(newEps);
    }
}