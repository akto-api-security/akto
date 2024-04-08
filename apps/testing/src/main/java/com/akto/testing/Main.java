package com.akto.testing;

import com.akto.DaoInit;
import com.akto.billing.UsageMetricUtils;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.AccountsDao;
import com.akto.dao.SetupDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunConfigDao;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.Account;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiInfo;
import com.akto.dto.Setup;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRun.State;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.TestingRunResultSummary;
import com.akto.dto.testing.rate_limit.ApiRateLimit;
import com.akto.dto.testing.rate_limit.GlobalApiRateLimit;
import com.akto.dto.testing.rate_limit.RateLimitHandler;
import com.akto.github.GithubUtils;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.mixpanel.AktoMixpanel;
import com.akto.util.AccountTask;
import com.akto.util.Constants;
import com.akto.util.EmailAccountName;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class Main {
    private static final LoggerMaker loggerMaker = new LoggerMaker(Main.class);

    private static final Logger logger = LoggerFactory.getLogger(Main.class);


    public static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    public static final ScheduledExecutorService schedulerAccessMatrix = Executors.newScheduledThreadPool(2);

    public static final boolean SKIP_SSRF_CHECK = "true".equalsIgnoreCase(System.getenv("SKIP_SSRF_CHECK"));
    public static final boolean IS_SAAS = "true".equalsIgnoreCase(System.getenv("IS_SAAS"));

    private static TestingRunResultSummary createTRRSummaryIfAbsent(TestingRun testingRun, int start){
        ObjectId testingRunId = new ObjectId(testingRun.getHexId());

        return TestingRunResultSummariesDao.instance.getMCollection().findOneAndUpdate(
                Filters.and(
                        Filters.eq(TestingRunResultSummary.TESTING_RUN_ID, testingRunId),
                        Filters.eq(TestingRunResultSummary.STATE,TestingRun.State.SCHEDULED)
                ),
                Updates.combine(
                        Updates.set(TestingRunResultSummary.STATE, TestingRun.State.RUNNING),
                        Updates.setOnInsert(TestingRunResultSummary.START_TIMESTAMP, start)
                ),
                new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
        );
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

    private static TestingRun findPendingTestingRun() {
        int delta = Context.now() - 20*60;

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
        return TestingRunDao.instance.getMCollection().findOneAndUpdate(
                Filters.or(filter1,filter2), update);
    }

    private static TestingRunResultSummary findPendingTestingRunResultSummary() {
        int now = Context.now();
        int delta = now - 20*60;

        Bson filter1 = Filters.and(
            Filters.eq(TestingRun.STATE, TestingRun.State.SCHEDULED),
            Filters.lte(TestingRunResultSummary.START_TIMESTAMP, now),
            Filters.gt(TestingRunResultSummary.START_TIMESTAMP, delta)
        );

        Bson filter2 = Filters.and(
            Filters.eq(TestingRun.STATE, TestingRun.State.RUNNING),
            Filters.lte(TestingRunResultSummary.START_TIMESTAMP, now - 5*60),
            Filters.gt(TestingRunResultSummary.START_TIMESTAMP, delta)
        );

        Bson update = Updates.set(TestingRun.STATE, TestingRun.State.RUNNING);

        TestingRunResultSummary trrs = TestingRunResultSummariesDao.instance.getMCollection().findOneAndUpdate(Filters.or(filter1,filter2), update);

        return trrs;
    }

    private static void setTestingRunConfig(TestingRun testingRun, TestingRunResultSummary trrs) {
        long timestamp = testingRun.getId().getTimestamp();
        long seconds = Context.now() - timestamp;
        loggerMaker.infoAndAddToDb("Found one + " + testingRun.getId().toHexString() + " created: " + seconds + " seconds ago", LogDb.TESTING);

        TestingRunConfig configFromTrrs = null;
        TestingRunConfig baseConfig = null;

        if (trrs != null && trrs.getTestIdConfig() > 1) {
            configFromTrrs = TestingRunConfigDao.instance.findOne(Constants.ID, trrs.getTestIdConfig());
            loggerMaker.infoAndAddToDb("Found testing run trrs config with id :" + configFromTrrs.getId(), LogDb.TESTING);
        }

        if (testingRun.getTestIdConfig() > 1) {
            baseConfig = TestingRunConfigDao.instance.findOne(Constants.ID, testingRun.getTestIdConfig());
            loggerMaker.infoAndAddToDb("Found testing run base config with id :" + baseConfig.getId(), LogDb.TESTING);
        }

        if (configFromTrrs == null) {
            testingRun.setTestingRunConfig(baseConfig);
        } else {
            configFromTrrs.rebaseOn(baseConfig);
            testingRun.setTestingRunConfig(configFromTrrs);
        }

        logger.info(testingRun.getTestingRunConfig().toString());
    }

    public static void main(String[] args) throws InterruptedException {
        String mongoURI = System.getenv("AKTO_MONGO_CONN");;
        DaoInit.init(new ConnectionString(mongoURI));

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
        
        Map<Integer, Integer> logSentMap = new HashMap<>();

        while (true) {
            AccountTask.instance.executeTask(account -> {
                int accountId = account.getId();

                int start = Context.now();

                TestingRunResultSummary trrs = findPendingTestingRunResultSummary();
                boolean isSummaryRunning = trrs != null && trrs.getState().equals(State.RUNNING);
                TestingRun testingRun;
                ObjectId summaryId = null;
                if (trrs == null) {
                    testingRun = findPendingTestingRun();
                } else {
                    summaryId = trrs.getId();
                    loggerMaker.infoAndAddToDb("Found trrs " + trrs.getHexId() +  " for account: " + accountId);
                    testingRun = TestingRunDao.instance.findOne("_id", trrs.getTestingRunId());
                }

                if (testingRun == null) {
                    return;
                }

                if (testingRun.getState().equals(State.STOPPED)) {
                    loggerMaker.infoAndAddToDb("Testing run stopped");
                    if (trrs != null) {
                        loggerMaker.infoAndAddToDb("Stopping TRRS: " + trrs.getId());
                        TestingRunResultSummariesDao.instance.updateOneNoUpsert(
                                Filters.eq(Constants.ID, trrs.getId()),
                                Updates.set(TestingRunResultSummary.STATE, State.STOPPED)
                        );
                        loggerMaker.infoAndAddToDb("Stopped TRRS: " + trrs.getId());
                    }
                    return;
                }

                loggerMaker.infoAndAddToDb("Starting test for accountID: " + accountId);

                boolean isTestingRunRunning = testingRun.getState().equals(State.RUNNING);

                if (UsageMetricUtils.checkTestRunsOverage(accountId)) {
                    int lastSent = logSentMap.getOrDefault(accountId, 0);
                    if (start - lastSent > LoggerMaker.LOG_SAVE_INTERVAL) {
                        logSentMap.put(accountId, start);
                        loggerMaker.infoAndAddToDb("Test runs overage detected for account: " + accountId
                                + " . Failing test run : " + start, LogDb.TESTING);
                    }
                    TestingRunDao.instance.getMCollection().findOneAndUpdate(
                            Filters.eq(Constants.ID, testingRun.getId()),
                            Updates.set(TestingRun.STATE, TestingRun.State.FAILED));

                    TestingRunResultSummariesDao.instance.getMCollection().findOneAndUpdate(
                            Filters.eq(Constants.ID, summaryId),
                            Updates.set(TestingRun.STATE, TestingRun.State.FAILED));

                    return;
                }

                try {
                    setTestingRunConfig(testingRun, trrs);

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
                            List<TestingRunResult> testingRunResults = TestingRunResultDao.instance.fetchLatestTestingRunResult(Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, testingRunResultSummary.getId()), 1);
                            if (testingRunResults != null && !testingRunResults.isEmpty()) {
                                TestingRunResult testingRunResult = testingRunResults.get(0);
                                if (Context.now() - testingRunResult.getEndTimestamp() < LAST_TEST_RUN_EXECUTION_DELTA) {
                                    loggerMaker.infoAndAddToDb("Skipping test run as it was executed recently, TRR_ID:"
                                            + testingRunResult.getHexId() + ", TRRS_ID:" + testingRunResultSummary.getHexId() + " TR_ID:" + testingRun.getHexId(), LogDb.TESTING);
                                    return;
                                } else {
                                    loggerMaker.infoAndAddToDb("Test run was executed long ago, TRR_ID:"
                                            + testingRunResult.getHexId() + ", TRRS_ID:" + testingRunResultSummary.getHexId() + " TR_ID:" + testingRun.getHexId(), LogDb.TESTING);
                                    TestingRunResultSummary summary = TestingRunResultSummariesDao.instance.updateOneNoUpsert(
                                            Filters.and(
                                                    Filters.eq(TestingRunResultSummary.ID, testingRunResultSummary.getId()),
                                                    Filters.eq(TestingRunResultSummary.STATE, State.RUNNING)
                                            ),
                                            Updates.set(TestingRunResultSummary.STATE, State.FAILED)
                                    );
                                    if (summary == null) {
                                        loggerMaker.infoAndAddToDb("Skipping because some other thread picked it up, TRRS_ID:" + testingRunResultSummary.getHexId() + " TR_ID:" + testingRun.getHexId(), LogDb.TESTING);
                                        return;
                                    }

                                    TestingRunResultSummary runResultSummary = TestingRunResultSummariesDao.instance.findOne(Filters.eq(TestingRunResultSummary.ID, testingRunResultSummary.getId()));
                                    GithubUtils.publishGithubComments(runResultSummary);
                                }
                            } else {
                                loggerMaker.infoAndAddToDb("No executions made for this test, will need to restart it, TRRS_ID:" + testingRunResultSummary.getHexId() + " TR_ID:" + testingRun.getHexId(), LogDb.TESTING);
                                TestingRunResultSummary summary = TestingRunResultSummariesDao.instance.updateOneNoUpsert(
                                        Filters.and(
                                                Filters.eq(TestingRunResultSummary.ID, testingRunResultSummary.getId()),
                                                Filters.eq(TestingRunResultSummary.STATE, State.RUNNING)
                                        ),
                                        Updates.set(TestingRunResultSummary.STATE, State.FAILED)
                                );
                                if (summary == null) {
                                    loggerMaker.infoAndAddToDb("Skipping because some other thread picked it up, TRRS_ID:" + testingRunResultSummary.getHexId() + " TR_ID:" + testingRun.getHexId(), LogDb.TESTING);
                                    return;
                                }
                            }

                            // insert new summary based on old summary
                            if (summaryId != null) {
                                trrs.setId(new ObjectId());
                                trrs.setStartTimestamp(start);
                                trrs.setState(State.RUNNING);
                                TestingRunResultSummariesDao.instance.insertOne(trrs);
                                summaryId = trrs.getId();
                            } else {
                                trrs = createTRRSummaryIfAbsent(testingRun, start);
                                summaryId = trrs.getId();
                            }
                        } else {
                            loggerMaker.infoAndAddToDb("No summary found. Let's run it as usual");
                        }
                    }

                    if (summaryId == null) {
                        trrs = createTRRSummaryIfAbsent(testingRun, start);
                        summaryId = trrs.getId();
                    }

                    TestExecutor testExecutor = new TestExecutor();
                    if (trrs.getState() == State.SCHEDULED) {
                        if (trrs.getMetadata()!= null && trrs.getMetadata().containsKey("pull_request_id") && trrs.getMetadata().containsKey("commit_sha_head") ) {
                            //case of github status push
                            GithubUtils.publishGithubStatus(trrs);

                        }
                    }
                    testExecutor.init(testingRun, summaryId);
                    raiseMixpanelEvent(summaryId, testingRun);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Error in init " + e);
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

                loggerMaker.infoAndAddToDb("Tests completed in " + (Context.now() - start) + " seconds for account: " + accountId, LogDb.TESTING);
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

        Bson filters = Filters.and(
            Filters.eq("latestTestingRunSummaryId", summaryId),
            Filters.eq("testRunIssueStatus", "OPEN")
        );
        List<TestingRunIssues> testingRunIssuesList = TestingRunIssuesDao.instance.findAll(filters);

        Map<String, Integer> severityCount = new HashMap<>();
        for (TestingRunIssues testingRunIssues: testingRunIssuesList) {
            String key = testingRunIssues.getSeverity().toString();
            if (!severityCount.containsKey(key)) {
                severityCount.put(key, 0);
            }
            int val = severityCount.get(key);
            severityCount.put(key, val+1);
        }

        props.put("Vulnerabilities Found", testingRunIssuesList.size());

        Iterator<Map.Entry<String, Integer>> iterator = severityCount.entrySet().iterator();
        while(iterator.hasNext()) {
            Map.Entry<String, Integer> entry = iterator.next();
            props.put(entry.getKey() + " Vulnerabilities", entry.getValue());
        }

        AktoMixpanel aktoMixpanel = new AktoMixpanel();
        aktoMixpanel.sendEvent(distinct_id, "Test executed", props);
    }
}