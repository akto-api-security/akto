package com.akto.testing;

import com.akto.DaoInit;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.AccountsDao;
import com.akto.dao.SetupDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunConfigDao;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dto.Account;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiInfo;
import com.akto.dto.Setup;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRun.State;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.TestingRunResultSummary;
import com.akto.dto.testing.rate_limit.ApiRateLimit;
import com.akto.dto.testing.rate_limit.GlobalApiRateLimit;
import com.akto.dto.testing.rate_limit.RateLimitHandler;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.mixpanel.AktoMixpanel;
import com.akto.util.AccountTask;
import com.akto.util.Constants;
import com.akto.util.EmailAccountName;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class Main {
    private static final LoggerMaker loggerMaker = new LoggerMaker(Main.class);

    public static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    public static final ScheduledExecutorService schedulerAccessMatrix = Executors.newScheduledThreadPool(2);

    public static final boolean SKIP_SSRF_CHECK = "true".equalsIgnoreCase(System.getenv("SKIP_SSRF_CHECK"));
    public static final boolean IS_SAAS = "true".equalsIgnoreCase(System.getenv("IS_SAAS"));

    private static ObjectId createTRRSummaryIfAbsent(TestingRun testingRun, int start){
        ObjectId summaryId = new ObjectId();
        try {
            ObjectId testingRunId = new ObjectId(testingRun.getHexId());
            TestingRunResultSummary testingRunResultSummary = TestingRunResultSummariesDao.instance.findOne(
                Filters.and(
                    Filters.eq(TestingRunResultSummary.TESTING_RUN_ID, testingRunId),
                    Filters.eq(TestingRunResultSummary.STATE,TestingRun.State.SCHEDULED)
                )
            );
            summaryId = testingRunResultSummary.getId();
            TestingRunResultSummariesDao.instance.updateOne(
                    Filters.eq(TestingRunResultSummary.ID, summaryId),
                    Updates.set(TestingRunResultSummary.STATE, TestingRun.State.RUNNING));
        } catch (Exception e){
            TestingRunResultSummary summary = new TestingRunResultSummary(start, 0, new HashMap<>(),
            0, testingRun.getId(), testingRun.getId().toHexString(), 0);

            summaryId = TestingRunResultSummariesDao.instance.insertOne(summary).getInsertedId().asObjectId().getValue();
        }
        return summaryId;
    }


    private static void setupRateLimitWatcher () {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
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

    public static Set<Integer> extractApiCollectionIds(List<ApiInfo.ApiInfoKey> apiInfoKeyList) {
        Set<Integer> ret = new HashSet<>();
        for(ApiInfo.ApiInfoKey apiInfoKey: apiInfoKeyList) {
            ret.add(apiInfoKey.getApiCollectionId());
        }

        return ret;
    }
    private static final int LAST_TEST_RUN_EXECUTION_DELTA = 5 * 60;

    public static void main(String[] args) throws InterruptedException {
        String mongoURI = System.getenv("AKTO_MONGO_CONN");;
        DaoInit.init(new ConnectionString(mongoURI));

        boolean connectedToMongo = false;
        do {
            try {
                AccountsDao.instance.getStats();
                connectedToMongo = true;
            } catch (Exception ignored) {
            } finally {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        } while (!connectedToMongo);

        setupRateLimitWatcher();

        loggerMaker.infoAndAddToDb("Starting.......", LogDb.TESTING);

        schedulerAccessMatrix.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(account -> {
                    AccessMatrixAnalyzer matrixAnalyzer = new AccessMatrixAnalyzer();
                    try {
                        matrixAnalyzer.run();
                    } catch (Exception e) {
                        loggerMaker.infoAndAddToDb("could not run matrixAnalyzer: " + e.getMessage(), LogDb.TESTING);
                    }
                },"matrix-analyser-task");
            }
        }, 0, 1, TimeUnit.MINUTES);



        loggerMaker.infoAndAddToDb("sun.arch.data.model: " +  System.getProperty("sun.arch.data.model"), LogDb.TESTING);
        loggerMaker.infoAndAddToDb("os.arch: " + System.getProperty("os.arch"), LogDb.TESTING);
        loggerMaker.infoAndAddToDb("os.version: " + System.getProperty("os.version"), LogDb.TESTING);

        while (true) {
            AccountTask.instance.executeTask(account -> {
                int delta = Context.now() - 20*60;

                Bson filter1 = Filters.and(Filters.eq(TestingRun.STATE, TestingRun.State.SCHEDULED),
                        Filters.lte(TestingRun.SCHEDULE_TIMESTAMP, Context.now())
                );
                Bson filter2 = Filters.and(
                        Filters.eq(TestingRun.STATE, TestingRun.State.RUNNING),
                        Filters.lte(TestingRun.SCHEDULE_TIMESTAMP, delta)
                );

                Bson update = Updates.combine(
                        Updates.set(TestingRun.PICKED_UP_TIMESTAMP, Context.now()),
                        Updates.set(TestingRun.STATE, TestingRun.State.RUNNING)
                );

                int start = Context.now();

                TestingRun testingRun = TestingRunDao.instance.getMCollection().findOneAndUpdate(
                        Filters.or(filter1,filter2), update);

                if (testingRun == null) {
                    return;
                }


                ObjectId summaryId = null;
                try {
                    long timestamp = testingRun.getId().getTimestamp();
                    long seconds = Context.now() - timestamp;
                    loggerMaker.infoAndAddToDb("Found one + " + testingRun.getId().toHexString() + " created: " + seconds + " seconds ago", LogDb.TESTING);
                    if (testingRun.getTestIdConfig() > 1) {
                        TestingRunConfig testingRunConfig = TestingRunConfigDao.instance.findOne(Constants.ID, testingRun.getTestIdConfig());
                        if (testingRunConfig != null) {
                            loggerMaker.infoAndAddToDb("Found testing run config with id :" + testingRunConfig.getId(), LogDb.TESTING);
                            testingRun.setTestingRunConfig(testingRunConfig);
                        }else {
                            loggerMaker.errorAndAddToDb("Couldn't find testing run config id for " + testingRun.getTestIdConfig(), LogDb.TESTING);
                        }
                    }
                    if(testingRun.getState().equals(TestingRun.State.RUNNING)){
                        Map<ObjectId, TestingRunResultSummary> objectIdTestingRunResultSummaryMap = TestingRunResultSummariesDao.instance.fetchLatestTestingRunResultSummaries(Collections.singletonList(testingRun.getId()));
                        TestingRunResultSummary testingRunResultSummary = objectIdTestingRunResultSummaryMap.get(testingRun.getId());
                        List<TestingRunResult> testingRunResults = TestingRunResultDao.instance.fetchLatestTestingRunResult(Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, testingRunResultSummary.getId()), 1);
                        if(testingRunResults != null && !testingRunResults.isEmpty()){
                            TestingRunResult testingRunResult = testingRunResults.get(0);
                            if(Context.now() - testingRunResult.getEndTimestamp() < LAST_TEST_RUN_EXECUTION_DELTA){
                                loggerMaker.infoAndAddToDb("Skipping test run as it was executed recently, TRR_ID:"
                                        + testingRunResult.getHexId() + ", TRRS_ID:" + testingRunResultSummary.getHexId() + " TR_ID:" + testingRun.getHexId(), LogDb.TESTING);
                                return;
                            } else {
                                loggerMaker.infoAndAddToDb("Test run was executed long ago, TRR_ID:"
                                        + testingRunResult.getHexId() + ", TRRS_ID:" + testingRunResultSummary.getHexId() + " TR_ID:" + testingRun.getHexId(), LogDb.TESTING);
                                TestingRunResultSummariesDao.instance.updateOne(Filters.eq(TestingRunResultSummary.ID, testingRunResultSummary.getId()), Updates.set(TestingRunResultSummary.STATE, TestingRun.State.FAILED));
                            }
                        } else {
                            loggerMaker.infoAndAddToDb("No executions made for this test, will need to restart it, TRRS_ID:" + testingRunResultSummary.getHexId() + " TR_ID:" + testingRun.getHexId(), LogDb.TESTING);
                            TestingRunResultSummariesDao.instance.updateOne(Filters.eq(TestingRunResultSummary.ID, testingRunResultSummary.getId()), Updates.set(TestingRunResultSummary.STATE, TestingRun.State.FAILED));
                        }
                    }
                    summaryId = createTRRSummaryIfAbsent(testingRun, start);
                    TestExecutor testExecutor = new TestExecutor();
                    testExecutor.init(testingRun, summaryId);
                    raiseMixpanelEvent(summaryId, testingRun);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb("Error in init " + e, LogDb.TESTING);
                }
                Bson completedUpdate = Updates.combine(
                        Updates.set(TestingRun.STATE, TestingRun.State.COMPLETED),
                        Updates.set(TestingRun.END_TIMESTAMP, Context.now())
                );

                if (testingRun.getPeriodInSeconds() > 0 ) {
                    completedUpdate = Updates.combine(
                            Updates.set(TestingRun.STATE, TestingRun.State.SCHEDULED),
                            Updates.set(TestingRun.END_TIMESTAMP, Context.now()),
                            Updates.set(TestingRun.SCHEDULE_TIMESTAMP, testingRun.getScheduleTimestamp() + testingRun.getPeriodInSeconds())
                    );
                }

                TestingRunDao.instance.getMCollection().findOneAndUpdate(
                        Filters.eq("_id", testingRun.getId()),  completedUpdate
                );

                if(summaryId != null && testingRun.getTestIdConfig() != 1){
                    TestExecutor.updateTestSummary(summaryId);
                }

                loggerMaker.infoAndAddToDb("Tests completed in " + (Context.now() - start) + " seconds", LogDb.TESTING);
            }, "testing");
            Thread.sleep(1000);
        }
    }

    private static void raiseMixpanelEvent(ObjectId summaryId, TestingRun testingRun) {
        TestingRunResultSummary testingRunResultSummary = TestingRunResultSummariesDao.instance.findOne
                (
                        Filters.eq(TestingRunResultSummary.ID, summaryId)
                );
        int totalApis = testingRunResultSummary.getTotalApis();

        String testType = "ONE_TIME";
        if(testingRun.getPeriodInSeconds()>0)
        {
            testType = "SCHEDULED DAILY";
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

        List<TestingRunResult> testingRunResults = TestingRunResultDao.instance.findAll(Filters.eq("testRunId", testingRun.getId()), Projections.include("vulnerable", "testResults"));
        int vulnerableCount = 0;
        Map<String, Integer> severityCount = new HashMap<>();
        for (TestingRunResult result: testingRunResults) {
            if (result.isVulnerable()) {
                vulnerableCount++;
                List<TestResult> testResults = result.getTestResults();
                if (testResults != null && testResults.size() > 0) {
                    String key = testResults.get(0).getConfidence().toString();
                    if (!severityCount.containsKey(key)) {
                        severityCount.put(key, 0);
                    }
                    int val = severityCount.get(key);
                    severityCount.put(key, val+1);
                }
            }
        }

        props.put("Vulnerabilities Found", vulnerableCount);
        Iterator<Map.Entry<String, Integer>> iterator = severityCount.entrySet().iterator();
        while(iterator.hasNext()) {
            Map.Entry<String, Integer> entry = iterator.next();
            props.put(entry.getKey() + " Vulnerabilities", entry.getValue());
        }

        AktoMixpanel aktoMixpanel = new AktoMixpanel();
        aktoMixpanel.sendEvent(distinct_id, "Test executed", props);
    }
}