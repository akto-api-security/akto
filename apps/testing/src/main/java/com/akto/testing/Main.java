package com.akto.testing;

import static com.akto.testing.Utils.isTestingRunForDemoCollection;
import static com.akto.testing.Utils.readJsonContentFromFile;

import com.akto.DaoInit;
import com.akto.billing.UsageMetricUtils;
import com.akto.crons.GetRunningTestsStatus;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.MCollection;
import com.akto.dao.SetupDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.TestingInstanceHeartBeatDao;
import com.akto.dao.context.Context;
import com.akto.dao.notifications.CustomWebhooksDao;
import com.akto.dao.testing.TestingRunConfigDao;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dao.testing.VulnerableTestingRunResultDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.Account;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.Setup;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.SyncLimit;
import com.akto.dto.notifications.CustomWebhook;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.CollectionWiseTestingEndpoints;
import com.akto.dto.testing.CustomTestingEndpoints;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.dto.testing.TestingEndpoints.Operator;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRun.State;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.TestingRunResultSummary;
import com.akto.dto.testing.rate_limit.ApiRateLimit;
import com.akto.dto.testing.rate_limit.GlobalApiRateLimit;
import com.akto.dto.testing.rate_limit.RateLimitHandler;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.usage.MetricTypes;
import com.akto.github.GithubUtils;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.mixpanel.AktoMixpanel;
import com.akto.notifications.data.TestingAlertData;
import com.akto.notifications.email.SendgridEmail;
import com.akto.notifications.slack.APITestStatusAlert;
import com.akto.notifications.slack.CustomTextAlert;
import com.akto.notifications.slack.NewIssuesModel;
import com.akto.notifications.slack.SlackAlerts;
import com.akto.notifications.slack.SlackSender;
import com.akto.notifications.teams.TeamsSender;
import com.akto.rules.RequiredConfigs;
import com.akto.task.Cluster;
import com.akto.test_editor.execution.Executor;
import com.akto.testing.kafka_utils.ConsumerUtil;
import com.akto.testing.kafka_utils.Producer;
import com.akto.testing.kafka_utils.TestingConfigurations;
import com.akto.util.AccountTask;
import com.akto.util.Constants;
import com.akto.util.DashboardMode;
import com.akto.util.DbMode;
import com.akto.util.EmailAccountName;
import com.akto.util.Util;
import com.akto.util.enums.GlobalEnums;
import com.akto.util.enums.GlobalEnums.Severity;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.sendgrid.helpers.mail.Mail;
import com.slack.api.Slack;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.json.JSONObject;
import org.springframework.util.StringUtils;

public class Main {
    private static final LoggerMaker loggerMaker = new LoggerMaker(Main.class, LogDb.TESTING);
    private static final String testingInstanceId = UUID.randomUUID().toString();

    public static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    public static final ScheduledExecutorService deleteScheduler = Executors.newScheduledThreadPool(1);

    public static final ScheduledExecutorService schedulerAccessMatrix = Executors.newScheduledThreadPool(2);

    public static boolean SKIP_SSRF_CHECK = ("true".equalsIgnoreCase(System.getenv("SKIP_SSRF_CHECK")) || !DashboardMode.isSaasDeployment());
    public static final boolean IS_SAAS = "true".equalsIgnoreCase(System.getenv("IS_SAAS"));
    public static final String AKTO_SLACK_WEBHOOK = System.getenv("AKTO_SLACK_WEBHOOK");
    public static final Slack SLACK_INSTANCE = Slack.getInstance();

    private static Map<String, Integer> emptyCountIssuesMap = new HashMap<>();

    static {
        emptyCountIssuesMap.put(Severity.HIGH.toString(), 0);
        emptyCountIssuesMap.put(Severity.MEDIUM.toString(), 0);
        emptyCountIssuesMap.put(Severity.LOW.toString(), 0);
    }

    public static void sendSlackAlertForFailedTest(int accountId, String customMessage){
        if(StringUtils.hasLength(AKTO_SLACK_WEBHOOK)){
            try {
                String slackMessage = "Test failed for accountId: " + accountId + "\n with reason and details: " + customMessage;
                CustomTextAlert customTextAlert = new CustomTextAlert(slackMessage);
                SLACK_INSTANCE.send(AKTO_SLACK_WEBHOOK, customTextAlert.toJson());
            } catch (Exception e) {
                loggerMaker.error("Error sending slack alert for failed test", e);
            }
        }
    }

    private static TestingRunResultSummary createTRRSummaryIfAbsent(TestingRun testingRun, int start){
        ObjectId testingRunId = new ObjectId(testingRun.getHexId());

        return TestingRunResultSummariesDao.instance.getMCollection().withWriteConcern(WriteConcern.W1).findOneAndUpdate(
                Filters.and(
                        Filters.eq(TestingRunResultSummary.TESTING_RUN_ID, testingRunId),
                        Filters.eq(TestingRunResultSummary.STATE,TestingRun.State.SCHEDULED)
                ),
                Updates.combine(
                        Updates.set(TestingRunResultSummary.STATE, TestingRun.State.RUNNING),
                        Updates.setOnInsert(TestingRunResultSummary.START_TIMESTAMP, start),
                        Updates.set(TestingRunResultSummary.TEST_RESULTS_COUNT, 0),
                        Updates.set(TestingRunResultSummary.COUNT_ISSUES, emptyCountIssuesMap),
                        Updates.set(TestingRunResultSummary.IS_NEW_TESTING_RUN_RESULT_SUMMARY, true)
                ),
                new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
        );
    }


    private static void setupRateLimitWatcher () {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTaskForNonHybridAccounts(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        AccountSettings settings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
                        if (settings == null) {
                            return;
                        }
                        int globalRateLimit = settings.getGlobalRateLimit();
                        int accountId = t.getId();
                        Map<ApiRateLimit, Integer> rateLimitMap =  RateLimitHandler.getInstance(accountId).getRateLimitsMap();
                        rateLimitMap.clear();
                        rateLimitMap.put(new GlobalApiRateLimit(globalRateLimit), globalRateLimit);
                    }
                }, "rate-limit-scheduler");
            }
        }, 0, 1, TimeUnit.MINUTES);
    }

    private static void triggerHeartbeatCron() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                UpdateOptions updateOptions = new UpdateOptions();
                updateOptions.upsert(true);
                Bson updates = Updates.combine(
                    Updates.setOnInsert("instanceId", testingInstanceId),
                    Updates.set("ts", Context.now())
                );
                TestingInstanceHeartBeatDao.instance.getMCollection().
                updateOne(Filters.eq("instanceId", testingInstanceId), updates, updateOptions);

            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    public static Set<Integer> extractApiCollectionIds(List<ApiInfo.ApiInfoKey> apiInfoKeyList) {
        Set<Integer> ret = new HashSet<>();
        for(ApiInfo.ApiInfoKey apiInfoKey: apiInfoKeyList) {
            ret.add(apiInfoKey.getApiCollectionId());
        }

        return ret;
    }
    private static final int LAST_TEST_RUN_EXECUTION_DELTA = 10 * 60;
    private static final int DEFAULT_DELTA_IGNORE_TIME = 2*60*60;
    private static final int MAX_RETRIES_FOR_FAILED_SUMMARIES = 3;

    private static TestingRun findPendingTestingRun(int userDeltaTime) {
        int deltaPeriod = userDeltaTime == 0 ? DEFAULT_DELTA_IGNORE_TIME : userDeltaTime;
        int delta = Context.now() - deltaPeriod;

        Bson filter1 = Filters.and(Filters.eq(TestingRun.STATE, TestingRun.State.SCHEDULED),
                Filters.lte(TestingRun.SCHEDULE_TIMESTAMP, Context.now())
        );
        Bson filter2 = Filters.and(
                Filters.eq(TestingRun.STATE, TestingRun.State.RUNNING),
                Filters.lte(TestingRun.PICKED_UP_TIMESTAMP, delta)
        );

        Bson update = Updates.combine(
                Updates.set(TestingRun.PICKED_UP_TIMESTAMP, Context.now()),
                Updates.set(TestingRun.STATE, TestingRun.State.RUNNING)
        );

        // returns the previous state of testing run before the update
        TestingRun testingRun = TestingRunDao.instance.getMCollection().withWriteConcern(WriteConcern.W1).findOneAndUpdate(
                Filters.or(filter1), update);
        if (testingRun == null) {
            testingRun = TestingRunDao.instance.getMCollection().withWriteConcern(WriteConcern.W1).findOneAndUpdate(
                    Filters.or(filter2), update);
        }
        return testingRun;
    }

    private static TestingRunResultSummary findPendingTestingRunResultSummary(int userDeltaTime) {

        // if you change this delta, update this delta in method getCurrentRunningTestsSummaries
        int now = Context.now();
        int deltaPeriod = userDeltaTime == 0 ? DEFAULT_DELTA_IGNORE_TIME : userDeltaTime;
        int delta = now - deltaPeriod;

        Bson filter1 = Filters.and(
            Filters.eq(TestingRun.STATE, TestingRun.State.SCHEDULED),
            Filters.lte(TestingRunResultSummary.START_TIMESTAMP, now),
            Filters.gt(TestingRunResultSummary.START_TIMESTAMP, delta)
        );

        Bson filter2 = Filters.and(
            Filters.eq(TestingRun.STATE, TestingRun.State.RUNNING),
            Filters.lte(TestingRunResultSummary.START_TIMESTAMP, now - 20*60),
            Filters.gt(TestingRunResultSummary.START_TIMESTAMP, delta)
        );

        Bson update = Updates.set(TestingRun.STATE, TestingRun.State.RUNNING);

        // for ci-cd tests, we need to check filter1, filter2 separately, coz if 2 machines are running , and first
        // one is executing, and at t + 22 minutes and less than delta, the other machine will pick up the running summary instead of the scheduled one if present
        // because findOneAndUpdate will return the first one {sorted by default id} that matches the filter
        TestingRunResultSummary trrs = TestingRunResultSummariesDao.instance.getMCollection().withWriteConcern(WriteConcern.W1).findOneAndUpdate(filter1, update);
        if(trrs == null) {
            trrs = TestingRunResultSummariesDao.instance.getMCollection().withWriteConcern(WriteConcern.W1).findOneAndUpdate(filter2, update);
        }

        return trrs;
    }

    private static void setTestingRunConfig(TestingRun testingRun, TestingRunResultSummary trrs) {
        long timestamp = testingRun.getId().getTimestamp();
        long seconds = Context.now() - timestamp;
        loggerMaker.debugAndAddToDb("Found one + " + testingRun.getId().toHexString() + " created: " + seconds + " seconds ago", LogDb.TESTING);

        TestingRunConfig configFromTrrs = null;
        TestingRunConfig baseConfig = null;

        if (trrs != null && trrs.getTestIdConfig() > 1) {
            configFromTrrs = TestingRunConfigDao.instance.findOne(Constants.ID, trrs.getTestIdConfig());
            loggerMaker.debugAndAddToDb("Found testing run trrs config with id :" + configFromTrrs.getId(), LogDb.TESTING);
        }

        if (testingRun.getTestIdConfig() > 1) {
            int counter = 0;
            do {
                baseConfig = TestingRunConfigDao.instance.findOne(Constants.ID, testingRun.getTestIdConfig());
                if (baseConfig == null) {
                    loggerMaker.errorAndAddToDb("in loop Couldn't find testing run base config:" + testingRun.getTestIdConfig(), LogDb.TESTING);
                } else {
                    loggerMaker.debugAndAddToDb("in loop Found testing run base config with id :" + baseConfig.getId(), LogDb.TESTING);
                }

                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {

                }
                counter++;
            } while (baseConfig == null && counter <= 5);

            if (baseConfig == null) {
                loggerMaker.errorAndAddToDb("Couldn't find testing run base config:" + testingRun.getTestIdConfig(), LogDb.TESTING);
            } else {
                loggerMaker.debugAndAddToDb("Found testing run base config with id :" + baseConfig.getId(), LogDb.TESTING);
            }
        }

        if (configFromTrrs == null) {
            testingRun.setTestingRunConfig(baseConfig);
        } else {
            configFromTrrs.rebaseOn(baseConfig);
            testingRun.setTestingRunConfig(configFromTrrs);
        }
        if(testingRun.getTestingRunConfig() != null){
            loggerMaker.debug(testingRun.getTestingRunConfig().toString());
        }else{
            loggerMaker.debug("Testing run config is null.");
        }
    }

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

            TestingRunResultSummary testingRunResultSummary = TestingRunResultSummariesDao.instance.findOne(Filters.eq(Constants.ID, new ObjectId(testingRunSummaryId)), Projections.include(TestingRunResultSummary.STATE));
            if(testingRunResultSummary == null || testingRunResultSummary.getState() == null ||  testingRunResultSummary.getState() != State.RUNNING){
                return null;
            }

            TestingRunResultSummary latestSummary =  TestingRunResultSummariesDao.instance.findLatestOne(Filters.eq(TestingRunResultSummary.TESTING_RUN_ID, new ObjectId(testingRunId)));
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
    
    // Runnable task for monitoring memory
    static class MemoryMonitorTask implements Runnable {
        @Override
        public void run() {
            Runtime runtime = Runtime.getRuntime();
    
            // Loop to print memory usage every 1 second
            while (true) {
                // Calculate memory statistics
                long totalMemory = runtime.totalMemory();
                long freeMemory = runtime.freeMemory();
                long usedMemory = totalMemory - freeMemory;
    
                // Print memory statistics
                System.out.print("Used Memory: " + (usedMemory / 1024 / 1024) + " MB ");
                System.out.print("Free Memory: " + (freeMemory / 1024 / 1024) + " MB ");
                System.out.print("Total Memory: " + (totalMemory / 1024 / 1024) + " MB ");
                System.out.print("Available Memory: " + ((runtime.maxMemory() - usedMemory) / 1024 / 1024) + " MB ");
                System.out.println("-------------------------");
    
                // Pause for 1 second
                try {
                    Thread.sleep(15000);
                } catch (InterruptedException e) {
                    System.err.println("Memory monitor thread interrupted: " + e.getMessage());
                    break; // Exit the loop if thread is interrupted
                }
            }
        }
    }

    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    //returns true if test is not supposed to run
    private static boolean handleRerunTestingRunResult(TestingRunResultSummary originalSummary) {
        TestingConfigurations config = TestingConfigurations.getInstance();
        config.setRerunTestingRunResultSummary(null);

        if (originalSummary == null || originalSummary.getOriginalTestingRunResultSummaryId() == null) {
            config.setTestingRunResultList(null);
            return false;
        }

        ObjectId summaryId = originalSummary.getOriginalTestingRunResultSummaryId();
        List<TestingRunResult> testingRunResultList = TestingRunResultDao.instance.findAll(
                Filters.and(
                        Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, summaryId),
                        Filters.eq(TestingRunResult.RERUN, true)
                ),
                Projections.include(
                        TestingRunResult.TEST_RUN_ID,
                        TestingRunResult.API_INFO_KEY,
                        TestingRunResult.TEST_SUB_TYPE,
                        TestingRunResult.VULNERABLE,
                        TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID
                )
        );

        if (testingRunResultList == null) {
            TestingRunResultSummariesDao.instance.deleteAll(Filters.eq(TestingRunResultSummariesDao.ID, originalSummary.getId()));
            loggerMaker.debugAndAddToDb("Deleting TRRS for rerun case, no testing run result found, TRRS_ID: " + originalSummary.getId().toHexString());
            return true;
        }

        //Updating start time stamp as current time stamp in case of rerun
        TestingRunResultSummariesDao.instance.updateOneNoUpsert(Filters.eq(TestingRunResultSummary.ID, summaryId),
                Updates.set(TestingRunResultSummary.START_TIMESTAMP, Context.now()));

        config.setRerunTestingRunResultSummary(originalSummary);
        config.setTestingRunResultList(testingRunResultList);
        return false;
    }

    public static void main(String[] args) throws InterruptedException {
        String mongoURI = System.getenv("AKTO_MONGO_CONN");
        ReadPreference readPreference = ReadPreference.primary();
        WriteConcern writeConcern = WriteConcern.W1;
        DaoInit.init(new ConnectionString(mongoURI), readPreference, writeConcern);

        boolean connectedToMongo = false;
        do {
            connectedToMongo = MCollection.checkConnection();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } while (!connectedToMongo);

        setupRateLimitWatcher();

        triggerHeartbeatCron();
        
        executorService.scheduleAtFixedRate(new Main.MemoryMonitorTask(), 0, 1, TimeUnit.SECONDS);

        if (!SKIP_SSRF_CHECK) {
            Setup setup = SetupDao.instance.findOne(new BasicDBObject());
            String dashboardMode = setup.getDashboardMode();
            if (dashboardMode != null) {
                boolean isSaas = dashboardMode.equalsIgnoreCase(DashboardMode.SAAS.name());
                if (!isSaas) SKIP_SSRF_CHECK = true;
            }
        }

        loggerMaker.debugAndAddToDb("Starting.......", LogDb.TESTING);

        Producer testingProducer = new Producer();
        ConsumerUtil testingConsumer = new ConsumerUtil();
        TestCompletion testCompletion = new TestCompletion();
        if(Constants.IS_NEW_TESTING_ENABLED){
            testingConsumer.initializeConsumer();
        }

        // read from files here and then see if we want to init the Producer and run the consumer
        // if producer is running, then we can skip the check and let the default testing pick up the job

        BasicDBObject currentTestInfo = null;
        if(Constants.IS_NEW_TESTING_ENABLED){
            currentTestInfo = checkIfAlreadyTestIsRunningOnMachine();
        }

        if(currentTestInfo != null){
            try {
                int accountId = Context.accountId.get();
                loggerMaker.debugAndAddToDb("Tests were already running on this machine, thus resuming the test for account: "+ accountId, LogDb.TESTING);
                FeatureAccess featureAccess = UsageMetricUtils.getFeatureAccess(accountId, MetricTypes.TEST_RUNS);
                

                String testingRunId = currentTestInfo.getString("testingRunId");
                String testingRunSummaryId = currentTestInfo.getString("summaryId");
                //check if currently running testrun is part of rerun
                ObjectId summaryId = new ObjectId(testingRunSummaryId);
                TestingRunResultSummary rerunTestingRunResultSummary = TestingRunResultSummariesDao.instance.findOne(TestingRunResultSummary.ORIGINAL_TESTING_RUN_SUMMARY_ID, summaryId);
                //fill testingRunResult in TestingConfigurations
                if(!handleRerunTestingRunResult(rerunTestingRunResultSummary)) {
                    TestingRun testingRun = TestingRunDao.instance.findOne(Filters.eq(Constants.ID, new ObjectId(testingRunId)));
                    TestingRunConfig baseConfig = TestingRunConfigDao.instance.findOne(Constants.ID, testingRun.getTestIdConfig());
                    testingRun.setTestingRunConfig(baseConfig);
                    testingProducer.initProducer(testingRun, summaryId, featureAccess.fetchSyncLimit(), true);
                    int maxRunTime = testingRun.getTestRunTime() <= 0 ? 30*60 : testingRun.getTestRunTime();
                    testingConsumer.init(maxRunTime);

                    // mark the test completed here
                    testCompletion.markTestAsCompleteAndRunFunctions(testingRun, summaryId);
                }

                deleteScheduler.execute(() -> {
                    Context.accountId.set(accountId);
                    try {
                        deleteNonVulnerableResults();

                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e, "Error in deleting testing run results");
                    }
                });
            } catch (Exception e) {
                loggerMaker.error("Error in running failed tests from file.", e);
            }
        }


        schedulerAccessMatrix.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTaskForNonHybridAccounts(account -> {
                    AccessMatrixAnalyzer matrixAnalyzer = new AccessMatrixAnalyzer();
                    try {
                        matrixAnalyzer.run();
                    } catch (Exception e) {
                        loggerMaker.debugAndAddToDb("could not run matrixAnalyzer: " + e.getMessage(), LogDb.TESTING);
                    }
                },"matrix-analyser-task");
            }
        }, 0, 1, TimeUnit.MINUTES);
        GetRunningTestsStatus.getRunningTests().getStatusOfRunningTests();

        loggerMaker.debugAndAddToDb("sun.arch.data.model: " +  System.getProperty("sun.arch.data.model"), LogDb.TESTING);
        loggerMaker.debugAndAddToDb("os.arch: " + System.getProperty("os.arch"), LogDb.TESTING);
        loggerMaker.debugAndAddToDb("os.version: " + System.getProperty("os.version"), LogDb.TESTING);

        // create /testing-info folder in the memory from here
        if(Constants.IS_NEW_TESTING_ENABLED){
            boolean val = Utils.createFolder(Constants.TESTING_STATE_FOLDER_PATH);
            loggerMaker.debug("Testing info folder status: " + val);
        }

        SingleTypeInfo.init();
        while (true) {
            AccountTask.instance.executeTaskForNonHybridAccounts(account -> {
                int accountId = account.getId();
                AccountSettings accountSettings = AccountSettingsDao.instance.findOne(
                    Filters.eq(Constants.ID, accountId), Projections.include(AccountSettings.DELTA_IGNORE_TIME_FOR_SCHEDULED_SUMMARIES)
                );

                TestingInstanceHeartBeatDao.instance.setTestingRunId(testingInstanceId, "");

                int start = Context.now();
                int defaultTime = DEFAULT_DELTA_IGNORE_TIME;
                if(accountSettings != null){
                    defaultTime =  accountSettings.getTimeForScheduledSummaries();
                }
                TestingConfigurations config = TestingConfigurations.getInstance();
                TestingRunResultSummary trrs = findPendingTestingRunResultSummary(defaultTime);
                boolean isSummaryRunning = trrs != null && trrs.getState().equals(State.RUNNING);
                boolean isTestingRunResultRerunCase = trrs != null && trrs.getOriginalTestingRunResultSummaryId() != null;
                TestingRun testingRun;
                ObjectId summaryId = null;
                if (trrs == null) {
                    testingRun = findPendingTestingRun(defaultTime);
                } else {
                    // For rerun case, use the original summary ID to maintain connection to original test
                    summaryId = isTestingRunResultRerunCase ? trrs.getOriginalTestingRunResultSummaryId() : trrs.getId();
                    loggerMaker.debugAndAddToDb("Found trrs " + trrs.getHexId() + (isTestingRunResultRerunCase ? " (rerun case) " : " ") + "for account: " + accountId);
                    testingRun = TestingRunDao.instance.findOne("_id", trrs.getTestingRunId());
                }
                if (testingRun == null) {
                    return;
                }

                if (handleRerunTestingRunResult(trrs)) {
                    return;
                }

                if (!TestingInstanceHeartBeatDao.instance.isTestEligibleForInstance(testingRun.getHexId())) {
                    return;
                }
                loggerMaker.info("Testing run eligible for instance: " + testingRun.getHexId() + " for account: " + accountId + " at:" + Context.now() + " on: " + testingInstanceId);
                TestingInstanceHeartBeatDao.instance.setTestingRunId(testingInstanceId, testingRun.getHexId());

                if (testingRun.getState().equals(State.STOPPED)) {
                    loggerMaker.debugAndAddToDb("Testing run stopped");
                    if (trrs != null) {
                        loggerMaker.debugAndAddToDb("Stopping TRRS: " + trrs.getId());

                        // get count issues here
                        if (isTestingRunResultRerunCase) {
                            // For TRR-rerun case, delete the rerun summary and clean up configurations
                            TestingRunResultSummariesDao.instance.deleteAll(Filters.eq(TestingRunResultSummariesDao.ID, trrs.getId()));
                            config.setTestingRunResultList(null);
                            config.setRerunTestingRunResultSummary(null);
                            loggerMaker.debugAndAddToDb("Deleted for TestingRunResult rerun case for stopped testrun TRRS: " + trrs.getId());
                        } else {
                            Map<String,Integer> finalCountMap = Utils.finalCountIssuesMap(trrs.getId());
                            loggerMaker.debugAndAddToDb("Final count map calculated is " + finalCountMap.toString());
                            TestingRunResultSummariesDao.instance.updateOneNoUpsert(
                                    Filters.eq(Constants.ID, trrs.getId()),
                                    Updates.combine(
                                            Updates.set(TestingRunResultSummary.STATE, State.STOPPED),
                                            Updates.set(TestingRunResultSummary.COUNT_ISSUES, finalCountMap)
                                    )
                            );
                            loggerMaker.debugAndAddToDb("Stopped TRRS: " + trrs.getId());
                        }
                    }
                    return;
                }

                FeatureAccess featureAccess = UsageMetricUtils.getFeatureAccess(accountId, MetricTypes.TEST_RUNS);

                loggerMaker.debugAndAddToDb("Starting test for accountID: " + accountId);

                boolean isTestingRunRunning = testingRun.getState().equals(State.RUNNING);

                if (featureAccess.checkInvalidAccess()) {
                    loggerMaker.debugAndAddToDb("Test runs overage detected for account: " + accountId + ". Failing test run at " + start, LogDb.TESTING);
                    TestingRunDao.instance.getMCollection().withWriteConcern(writeConcern).findOneAndUpdate(
                            Filters.eq(Constants.ID, testingRun.getId()),
                            Updates.set(TestingRun.STATE, TestingRun.State.FAILED));

                    sendSlackAlertForFailedTest(accountId, "Overrage detected, TRR_ID: " + testingRun.getHexId() + " TRRS_ID: " + summaryId.toHexString());

                    if (isTestingRunResultRerunCase) {
                        // For TRR-rerun case, delete the rerun summary and clean up configurations
                        TestingRunResultSummariesDao.instance.deleteAll(Filters.eq(TestingRunResultSummariesDao.ID, trrs.getId()));
                        config.setTestingRunResultList(null);
                        config.setRerunTestingRunResultSummary(null);
                        loggerMaker.debugAndAddToDb("Deleted for TestingRunResult rerun case for failed testrun TRRS: " + trrs.getId());
                    } else {
                        TestingRunResultSummariesDao.instance.getMCollection().withWriteConcern(writeConcern).findOneAndUpdate(
                                Filters.eq(Constants.ID, summaryId),
                                Updates.set(TestingRun.STATE, TestingRun.State.FAILED));
                    }
                    return;
                }

                SyncLimit syncLimit = featureAccess.fetchSyncLimit();
                /*
                 * Since the role cache is static
                 * so to prevent it from being shared across accounts.
                 */
                Executor.clearRoleCache();

                try {
                    fillTestingEndpoints(testingRun);
                    // continuous testing condition
                    if (testingRun.getPeriodInSeconds() == -1) {
                        CustomTestingEndpoints eps = (CustomTestingEndpoints) testingRun.getTestingEndpoints();
                        if (eps.getApisList().size() == 0) {
                            Bson completedUpdate = Updates.combine(
                                Updates.set(TestingRun.STATE, TestingRun.State.SCHEDULED),
                                Updates.set(TestingRun.END_TIMESTAMP, Context.now()),
                                Updates.set(TestingRun.SCHEDULE_TIMESTAMP, Context.now() + 5 * 60)
                            );
                            TestingRunDao.instance.getMCollection().withWriteConcern(writeConcern).findOneAndUpdate(
                                Filters.eq("_id", testingRun.getId()),  completedUpdate
                            );
                            return;
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
                            Map<ObjectId, TestingRunResultSummary> objectIdTestingRunResultSummaryMap = TestingRunResultSummariesDao.instance.fetchLatestTestingRunResultSummaries(Collections.singletonList(testingRun.getId()));
                            testingRunResultSummary = objectIdTestingRunResultSummaryMap.get(testingRun.getId());
                        }                   

                        if (testingRunResultSummary != null) {
                            int maxRunTime = testingRun.getTestRunTime() <= 0 ? 30*60 : testingRun.getTestRunTime(); 
                            Bson filterCountFailed = Filters.and(
                                Filters.gte(TestingRunResultSummary.START_TIMESTAMP, (Context.now() - ((MAX_RETRIES_FOR_FAILED_SUMMARIES + 1) * maxRunTime))),
                                Filters.eq(TestingRunResultSummary.TESTING_RUN_ID, testingRun.getId()),
                                Filters.eq(TestingRunResultSummary.STATE, State.FAILED)
                            );

                            // For rerun case, we need to check the original test results
                            List<TestingRunResult> testingRunResults;
                            if (isTestingRunResultRerunCase) {
                                testingRunResults = Utils.fetchLatestTestingRunResult(
                                    Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, testingRunResultSummary.getOriginalTestingRunResultSummaryId())
                                );
                            } else {
                                testingRunResults = Utils.fetchLatestTestingRunResult(
                                    Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, testingRunResultSummary.getId())
                                );
                            }

                            if (testingRunResults != null && !testingRunResults.isEmpty()) {
                                TestingRunResult testingRunResult = testingRunResults.get(0);
                                if (Context.now() - testingRunResult.getEndTimestamp() < LAST_TEST_RUN_EXECUTION_DELTA) {
                                    loggerMaker.debugAndAddToDb("Skipping test run as it was executed recently, TRR_ID:"
                                            + testingRunResult.getHexId() + ", TRRS_ID:" + testingRunResultSummary.getHexId() 
                                            + (isTestingRunResultRerunCase ? " (rerun case) " : " ")
                                            + " TR_ID:" + testingRun.getHexId(), LogDb.TESTING);
                                    return;
                                } else {
                                    loggerMaker.infoAndAddToDb("Test run was executed long ago, TRR_ID:"
                                            + testingRunResult.getHexId() + ", TRRS_ID:" + testingRunResultSummary.getHexId() 
                                            + (isTestingRunResultRerunCase ? " (rerun case) " : " ")
                                            + " TR_ID:" + testingRun.getHexId(), LogDb.TESTING);

                                    Map<String,Integer> finalCountMap = Utils.finalCountIssuesMap(isTestingRunResultRerunCase?
                                            testingRunResultSummary.getOriginalTestingRunResultSummaryId(): testingRunResultSummary.getId());
                                    if (isTestingRunResultRerunCase) {
                                        //If stuck for a long time, delete the rerun test summary and update issue count for original summary
                                        TestingRunResultSummariesDao.instance.updateOneNoUpsert(
                                                Filters.eq(TestingRunResultSummary.ID, testingRunResultSummary.getOriginalTestingRunResultSummaryId()),
                                                Updates.set(TestingRunResultSummary.COUNT_ISSUES, finalCountMap)
                                        );

                                        TestingRunResultSummariesDao.instance.deleteAll(Filters.eq(TestingRunResultSummariesDao.ID, testingRunResultSummary.getId()));
                                        config.setTestingRunResultList(null);
                                        config.setRerunTestingRunResultSummary(null);
                                        loggerMaker.debugAndAddToDb("Deleted for TestingRunResult rerun case for failed testrun TRRS: " + testingRunResultSummary.getId());
                                        return;
                                    }

                                    int countFailedSummaries = (int) TestingRunResultSummariesDao.instance.count(filterCountFailed);
                                    loggerMaker.debugAndAddToDb("Final count map calculated is " + finalCountMap.toString());
                                    Bson updateForSummary = Updates.combine(
                                        Updates.set(TestingRunResultSummary.STATE, State.FAILED),
                                        Updates.set(TestingRunResultSummary.COUNT_ISSUES, finalCountMap)
                                    );
                                    String customMessage = "Test stuck for a long time, TRR_ID: " + testingRun.getHexId() + " TRRS_ID: " + testingRunResultSummary.getHexId();
                                    if(countFailedSummaries >= (MAX_RETRIES_FOR_FAILED_SUMMARIES - 1)){
                                        updateForSummary = Updates.combine(
                                            Updates.set(TestingRunResultSummary.STATE, State.COMPLETED),
                                            Updates.set(TestingRunResultSummary.COUNT_ISSUES, finalCountMap),
                                            Updates.set(TestingRunResultSummary.END_TIMESTAMP, Context.now())
                                        );
                                        loggerMaker.infoAndAddToDb("Max retries level reached for TRR_ID: " + testingRun.getHexId(), LogDb.TESTING);
                                        maxRetriesReached = true;
                                        customMessage += " Max retries level reached";
                                    }else{
                                        customMessage += " Max retries level not reached, count failed summaries: " + countFailedSummaries;
                                    }

                                    TestingRunResultSummary summary = TestingRunResultSummariesDao.instance.updateOneNoUpsert(
                                            Filters.and(
                                                    Filters.eq(TestingRunResultSummary.ID, testingRunResultSummary.getId()),
                                                    Filters.eq(TestingRunResultSummary.STATE, State.RUNNING)
                                            ),
                                            updateForSummary
                                    );
                                    sendSlackAlertForFailedTest(accountId, customMessage);
                                    if (summary == null) {
                                        loggerMaker.debugAndAddToDb("Skipping because some other thread picked it up, TRRS_ID:" + testingRunResultSummary.getHexId() + " TR_ID:" + testingRun.getHexId(), LogDb.TESTING);
                                        return;
                                    }

                                    TestingRunResultSummary runResultSummary = TestingRunResultSummariesDao.instance.findOne(
                                        Filters.eq(TestingRunResultSummary.ID, testingRunResultSummary.getId())
                                    );
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
                                    TestingRunResultSummariesDao.instance.deleteAll(Filters.eq(TestingRunResultSummariesDao.ID, testingRunResultSummary.getId()));
                                    config.setTestingRunResultList(null);
                                    config.setRerunTestingRunResultSummary(null);
                                    loggerMaker.debugAndAddToDb("Deleted for TestingRunResult rerun case for failed testrun TRRS: " + testingRunResultSummary.getId());
                                    return;
                                }

                                int countFailedSummaries = (int) TestingRunResultSummariesDao.instance.count(filterCountFailed);
                                Bson updateForSummary = Updates.set(TestingRunResultSummary.STATE, State.FAILED);
                                String customMessage = "No executions made for this test, will need to restart it, TRR_ID: " + testingRun.getHexId() + " TRRS_ID: " + testingRunResultSummary.getHexId();

                                if(countFailedSummaries >= (MAX_RETRIES_FOR_FAILED_SUMMARIES - 1)){
                                    updateForSummary = Updates.combine(
                                        Updates.set(TestingRunResultSummary.STATE, State.COMPLETED),
                                        Updates.set(TestingRunResultSummary.END_TIMESTAMP, Context.now())
                                    );
                                    loggerMaker.infoAndAddToDb("Max retries level reached for TRR_ID: " + testingRun.getHexId(), LogDb.TESTING);
                                    maxRetriesReached = true;
                                    customMessage += " Max retries level reached";
                                }else{
                                    customMessage += " Max retries level not reached, count failed summaries: " + countFailedSummaries;
                                }
                                TestingRunResultSummary summary = TestingRunResultSummariesDao.instance.updateOneNoUpsert(
                                    Filters.and(
                                        Filters.eq(Constants.ID, testingRunResultSummary.getId()),
                                        Filters.eq(TestingRunResultSummary.STATE, State.RUNNING)
                                    ), updateForSummary
                                );
                                sendSlackAlertForFailedTest(accountId, customMessage);
                                if (summary == null) {
                                    loggerMaker.debugAndAddToDb("Skipping because some other thread picked it up, TRRS_ID:" + testingRunResultSummary.getHexId() + " TR_ID:" + testingRun.getHexId(), LogDb.TESTING);
                                    return;
                                }
                            }

                            // insert new summary based on old summary
                            // add max retries here and then mark last summary as completed when results > 0
                            if(maxRetriesReached){
                                loggerMaker.debugAndAddToDb("Exiting out as maxRetries have been reached for testingRun: " + testingRun.getHexId(), LogDb.TESTING);
                            }else{
                                if (summaryId != null) {
                                    trrs.setId(new ObjectId());
                                    trrs.setStartTimestamp(start);
                                    trrs.setState(State.RUNNING);
                                    trrs.setTestResultsCount(0);
                                    trrs.setCountIssues(emptyCountIssuesMap);
                                    trrs.setNewTestingSummary(true);
                                    TestingRunResultSummariesDao.instance.insertOne(trrs);
                                    summaryId = trrs.getId();
                                } else {
                                    trrs = createTRRSummaryIfAbsent(testingRun, start);
                                    summaryId = trrs.getId();
                                }
                            }
                        } else {
                            loggerMaker.debugAndAddToDb("No summary found. Let's run it as usual");
                        }
                    }

                    if (summaryId == null) {
                        trrs = createTRRSummaryIfAbsent(testingRun, start);
                        summaryId = trrs.getId();
                    }

                    if (trrs.getState() == State.SCHEDULED) {
                        if (trrs.getMetadata()!= null && trrs.getMetadata().containsKey("pull_request_id") && trrs.getMetadata().containsKey("commit_sha_head") ) {
                            //case of github status push
                            GithubUtils.publishGithubStatus(trrs);

                        }
                    }
                    RequiredConfigs.initiate();
                    int maxRunTime = testingRun.getTestRunTime() <= 0 ? 30*60 : testingRun.getTestRunTime();
                    
                    if(!maxRetriesReached){
                        // init producer and the consumer here
                        // producer for testing is currently calls init functions from test-executor
                        if(Constants.IS_NEW_TESTING_ENABLED){
                            testingProducer.initProducer(testingRun, summaryId, syncLimit, false);
                            testingConsumer.init(maxRunTime);  
                        }else{
                            TestExecutor testExecutor = new TestExecutor();
                            testExecutor.init(testingRun, summaryId, syncLimit, false);
                        }                   
                    }
                    
            } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Error in init " + e);
                }
                testCompletion.markTestAsCompleteAndRunFunctions(testingRun, summaryId);
                /*
                 * In case the testing run results start overflowing
                 * due to being a capped collection,
                 * we will start deleting non-vulnerable results,
                 * since we cap on size as well as number of documents.
                 */
                deleteScheduler.execute(() -> {
                    Context.accountId.set(accountId);
                    try {
                        deleteNonVulnerableResults();
                        // TODO: fix clean testing job (CleanTestingJob) for more scale and add here.

                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e, "Error in deleting testing run results");
                    }
                });
                

            }, "testing");
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

        List<ApiInfo.ApiInfoKey> endpoints = SingleTypeInfoDao.fetchLatestEndpointsForTesting(st, et, apiCollectionId);
        CustomTestingEndpoints newEps = new CustomTestingEndpoints(endpoints, Operator.AND);
        tr.setTestingEndpoints(newEps);
    }

    public static void raiseMixpanelEvent(ObjectId summaryId, TestingRun testingRun, int accountId) {
        TestingRunResultSummary testingRunResultSummary = TestingRunResultSummariesDao.instance.findOne
                (
                        Filters.eq(TestingRunResultSummary.ID, summaryId)
                );
        int totalApis = testingRunResultSummary.getTotalApis();

        String testType = "ONE_TIME";
        if(testingRun.getPeriodInSeconds()>0)
        {
            testType = "SCHEDULED";
        }
        if (testingRunResultSummary.getMetadata() != null) {
            testType = "CI_CD";
        }

        Setup setup = SetupDao.instance.findOne(new BasicDBObject());

        String dashboardMode = "saas";
        if (setup != null) {
            dashboardMode = setup.getDashboardMode();
        }

        String userEmail = testingRun.getUserEmail();
        String distinct_id = userEmail + "_" + dashboardMode.toUpperCase();

        EmailAccountName emailAccountName = new EmailAccountName(userEmail);
        String accountName = emailAccountName.getAccountName();

        JSONObject props = new JSONObject();
        props.put("Email ID", userEmail);
        props.put("Dashboard Mode", dashboardMode);
        props.put("Account Name", accountName);
        props.put("Test type", testType);
        props.put("Total APIs tested", totalApis);

        if (testingRun.getTestIdConfig() > 1) {
            TestingRunConfig testingRunConfig = TestingRunConfigDao.instance.findOne(Constants.ID, testingRun.getTestIdConfig());
            if (testingRunConfig != null && testingRunConfig.getTestSubCategoryList() != null) {
                props.put("Total Tests", testingRunConfig.getTestSubCategoryList().size());
                props.put("Tests Ran", testingRunConfig.getTestSubCategoryList());
            }
        }

        Bson filters = Filters.and(
            Filters.eq("latestTestingRunSummaryId", summaryId),
            Filters.eq("testRunIssueStatus", "OPEN")
        );
        List<TestingRunIssues> testingRunIssuesList = TestingRunIssuesDao.instance.findAll(filters);

        Map<String, Integer> apisAffectedCount = new HashMap<>();
        int newIssues = 0;
        Map<String, Integer> severityCount = new HashMap<>();
        for (TestingRunIssues testingRunIssues: testingRunIssuesList) {
            String key = testingRunIssues.getSeverity().toString();
            if (!severityCount.containsKey(key)) {
                severityCount.put(key, 0);
            }

            int issuesSeverityCount = severityCount.get(key);
            severityCount.put(key, issuesSeverityCount+1);

            String testSubCategory = testingRunIssues.getId().getTestSubCategory();
            int totalApisAffected = apisAffectedCount.getOrDefault(testSubCategory, 0)+1;

            apisAffectedCount.put(
                    testSubCategory,
                    totalApisAffected
            );

            if(testingRunIssues.getCreationTime() > testingRunResultSummary.getStartTimestamp()) {
                newIssues++;
            }
        }

        testingRunIssuesList.sort(Comparator.comparing(TestingRunIssues::getSeverity));

        List<NewIssuesModel> newIssuesModelList = new ArrayList<>();
        for(TestingRunIssues testingRunIssues : testingRunIssuesList) {
            if(testingRunIssues.getCreationTime() > testingRunResultSummary.getStartTimestamp()) {
                String testRunResultId;
                if(newIssuesModelList.size() <= 5) {
                    Bson filterForRunResult = Filters.and(
                            Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, testingRunIssues.getLatestTestingRunSummaryId()),
                            Filters.eq(TestingRunResult.VULNERABLE, true),
                            Filters.eq(TestingRunResult.TEST_SUB_TYPE, testingRunIssues.getId().getTestSubCategory()),
                            Filters.eq(TestingRunResult.API_INFO_KEY, testingRunIssues.getId().getApiInfoKey())
                    );
                    TestingRunResult testingRunResult = VulnerableTestingRunResultDao.instance.findOneWithComparison(filterForRunResult, Projections.include("_id"));
                    testRunResultId = testingRunResult.getHexId();
                } else testRunResultId = "";

                String issueCategory = testingRunIssues.getId().getTestSubCategory();
                newIssuesModelList.add(new NewIssuesModel(
                        issueCategory,
                        testRunResultId,
                        apisAffectedCount.get(issueCategory),
                        testingRunIssues.getCreationTime()
                ));
            }
        }

        props.put("Vulnerabilities Found", testingRunIssuesList.size());

        Iterator<Map.Entry<String, Integer>> iterator = severityCount.entrySet().iterator();
        while(iterator.hasNext()) {
            Map.Entry<String, Integer> entry = iterator.next();
            props.put(entry.getKey() + " Vulnerabilities", entry.getValue());
        }

        long nextTestRun = testingRun.getPeriodInSeconds() == 0 ? 0 : ((long) testingRun.getScheduleTimestamp() + (long) testingRun.getPeriodInSeconds());

        String collection = null;
        TestingEndpoints testingEndpoints = testingRun.getTestingEndpoints();
        if(testingEndpoints.getType().equals(TestingEndpoints.Type.COLLECTION_WISE)) {
            CollectionWiseTestingEndpoints collectionWiseTestingEndpoints = (CollectionWiseTestingEndpoints) testingEndpoints;
            int apiCollectionId = collectionWiseTestingEndpoints.getApiCollectionId();
            ApiCollection apiCollection = ApiCollectionsDao.instance.getMeta(apiCollectionId);
            collection = apiCollection.getName();
        }

        long currentTime = Context.now();
        long startTimestamp = testingRunResultSummary.getStartTimestamp();
        long scanTimeInSeconds = Math.abs(currentTime - startTimestamp);

        TestingAlertData alertData = new TestingAlertData(
                testingRun.getName(),
                severityCount.getOrDefault(GlobalEnums.Severity.CRITICAL.name(), 0),
                severityCount.getOrDefault(GlobalEnums.Severity.HIGH.name(), 0),
                severityCount.getOrDefault(GlobalEnums.Severity.MEDIUM.name(), 0),
                severityCount.getOrDefault(GlobalEnums.Severity.LOW.name(), 0),
                testingRunIssuesList.size(),
                newIssues,
                totalApis,
                collection,
                scanTimeInSeconds,
                testType,
                nextTestRun,
                newIssuesModelList,
                testingRun.getHexId(),
                summaryId.toHexString()
        );

        SlackAlerts apiTestStatusAlert = new APITestStatusAlert(alertData);

        if (testingRun.getSendSlackAlert()) {
            SlackSender.sendAlert(accountId, apiTestStatusAlert, testingRun.getSelectedSlackChannelId());
        }

        if(testingRun.getSendMsTeamsAlert() ){
            TeamsSender.sendAlert(accountId, alertData);
        }

        // check for webhooks here 
        CustomWebhook customWebhook = CustomWebhooksDao.instance.findOne(
            Filters.eq(CustomWebhook.WEBHOOK_TYPE, CustomWebhook.WebhookType.GMAIL.toString())
        );

        if(customWebhook != null && customWebhook.getActiveStatus().equals(CustomWebhook.ActiveStatus.ACTIVE)) {
            try {
                Mail mail = SendgridEmail.getInstance().buildTestingRunResultsEmail(alertData, customWebhook.getUrl(),customWebhook.getDashboardUrl() + "/dashboard/testing/"
                + alertData.getViewOnAktoURL() + "#vulnerable" , customWebhook.getQueryParams());
                loggerMaker.infoAndAddToDb("Sending Gmail alert for TestingRunResultSummary: " + summaryId + " to: " + customWebhook.getUrl());
                SendgridEmail.getInstance().send(mail);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error sending Gmail alert for TestingRunResultSummary: " + summaryId);
            }
        }

        AktoMixpanel aktoMixpanel = new AktoMixpanel();
        aktoMixpanel.sendEvent(distinct_id, "Test executed", props);
    }

    private static void deleteNonVulnerableResults() {
        if (shouldDeleteNonVulnerableResults()) {
            /*
             * Since this is a heavy job,
             * running it for only one account on one instance at a time.
             */
            boolean dibs = Cluster.callDibs(Cluster.DELETE_TESTING_RUN_RESULTS, 60 * 60, 60);
            if (!dibs) {
                return;
            }
            loggerMaker.debugAndAddToDb(
                    String.format("%s dibs acquired, starting", Cluster.DELETE_TESTING_RUN_RESULTS));

            deleteNonVulnerableResultsUtil();
            loggerMaker.debugAndAddToDb(
                    "deleteNonVulnerableResults Completed deleting non-vulnerable TestingRunResults");
        }
    }

    private static boolean shouldDeleteNonVulnerableResults() {
        long count = -1;
        long size = -1;

        if (DbMode.allowCappedCollections()) {
            Document stats = TestingRunResultDao.instance.getCollectionStats();
            count = Util.getLongValue(stats.get(MCollection._COUNT));
            size = Util.getLongValue(stats.get(MCollection._SIZE));
        } else {
            /*
             * db.runCommand not supported for DBs excluding mongoDB
             */
            count = TestingRunResultDao.instance.getMCollection().estimatedDocumentCount();
        }

        loggerMaker.debugAndAddToDb(String.format(
                "shouldDeleteNonVulnerableResults non-vulnerable TestingRunResults, current stats: size: %s count: %s",
                size, count));

        // Deleting only in case of capped collection limits about to breach
        if ((size >= (TestingRunResultDao.CLEAN_THRESHOLD * TestingRunResultDao.sizeInBytes) / 100) ||
                (count >= (TestingRunResultDao.CLEAN_THRESHOLD * TestingRunResultDao.maxDocuments) / 100)) {

            /*
             * We delete non-vulnerable results,
             * per summary, starting from the oldest one.
             */
            return true;
        }
        return false;
    }

    private static void deleteNonVulnerableResultsUtil() {

        final int LIMIT = 1000;
        final int BATCH_LIMIT = 5;
        int skip = 0;

        do {

            // oldest first.
            List<TestingRunResultSummary> summaries = TestingRunResultSummariesDao.instance.findAll(
                    Filters.empty(), skip, LIMIT, Sorts.ascending(TestingRunResultSummary.END_TIMESTAMP),
                    Projections.exclude(TestingRunResultSummary.METADATA_STRING));

            if (summaries == null || summaries.isEmpty()) {
                break;
            }

            loggerMaker.debugAndAddToDb(String.format(
                    "deleteNonVulnerableResultsUtil Fetched %s summaries for deleting TestingRunResults",
                    summaries.size()));
            List<ObjectId> ids = new ArrayList<>();

            boolean done = false;
            for (TestingRunResultSummary summary : summaries) {
                ids.add(summary.getId());
                if (ids.size() >= BATCH_LIMIT) {
                    if (actuallyDeleteTestingRunResults(ids)) {
                        done = true;
                        break;
                    }
                    ids = new ArrayList<>();
                }
            }

            if (!done && ids.size() > 0) {
                if (actuallyDeleteTestingRunResults(ids)) {
                    break;
                }
            }

            skip += LIMIT;
        } while (true);

        runCompactIfPossible();
    }

    private static void runCompactIfPossible() {
        if (!DbMode.allowCappedCollections()) {
            /*
             * db.runCommand not supported for DBs excluding mongoDB
             */
            return;
        }

        loggerMaker.debugAndAddToDb("deleteNonVulnerableResultsUtil Running compact to reclaim space");
        Document compactResult = TestingRunResultDao.instance.compactCollection();
        loggerMaker.debugAndAddToDb(
                String.format("deleteNonVulnerableResultsUtil compact result: %s", compactResult.toString()));
    }

    private static boolean actuallyDeleteTestingRunResults(List<ObjectId> ids) {

        if (ids == null || ids.isEmpty()) {
            return false;
        }

        DeleteResult res = TestingRunResultDao.instance.deleteAll(
                Filters.and(
                        Filters.in(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, ids),
                        Filters.eq(TestingRunResult.VULNERABLE, false)));

        loggerMaker.debugAndAddToDb(String.format(
                "actuallyDeleteTestingRunResults Deleted %s TestingRunResults",
                res.getDeletedCount()));
        if (deletePurposeSuccessful()) {
            return true;
        }
        return false;
    }

    private static boolean deletePurposeSuccessful() {
        long count = -1;
        long size = -1;

        if (DbMode.allowCappedCollections()) {
            Document stats = TestingRunResultDao.instance.getCollectionStats();
            count = Util.getLongValue(stats.get(MCollection._COUNT));
            size = Util.getLongValue(stats.get(MCollection._SIZE));
        } else {
            /*
             * db.runCommand not supported for DBs excluding mongoDB
             */
            count = TestingRunResultDao.instance.getMCollection().estimatedDocumentCount();
        }

        loggerMaker.debugAndAddToDb(String.format(
                "actuallyDeleteTestingRunResults stats: size: %s count: %s",
                size, count));

        if ((size != -1 &&
                (size <= (TestingRunResultDao.DESIRED_THRESHOLD * TestingRunResultDao.sizeInBytes) / 100)) ||
                (count != -1 &&
                        (count <= (TestingRunResultDao.DESIRED_THRESHOLD * TestingRunResultDao.maxDocuments) / 100))) {

            return true;
        }
        return false;
    }

}