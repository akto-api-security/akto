package com.akto.testing;

import com.akto.DaoInit;
import com.akto.billing.UsageMetricUtils;
import com.akto.crons.GetRunningTestsStatus;
import com.akto.dao.*;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunConfigDao;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.database_abstractor_authenticator.JwtAuthenticator;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.SyncLimit;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.*;
import com.akto.dto.billing.Organization;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.*;
import com.akto.dto.testing.TestingEndpoints.Operator;
import com.akto.dto.testing.TestingRun.State;
import com.akto.dto.testing.rate_limit.ApiRateLimit;
import com.akto.dto.testing.rate_limit.GlobalApiRateLimit;
import com.akto.dto.testing.rate_limit.RateLimitHandler;
import com.akto.dto.usage.MetricTypes;
import com.akto.github.GithubUtils;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.mixpanel.AktoMixpanel;
import com.akto.usage.UsageMetricHandler;
import com.akto.notifications.slack.APITestStatusAlert;
import com.akto.notifications.slack.NewIssuesModel;
import com.akto.notifications.slack.SlackAlerts;
import com.akto.notifications.slack.SlackSender;
import com.akto.rules.RequiredConfigs;
import com.akto.util.AccountTask;
import com.akto.util.Constants;
import com.akto.util.DashboardMode;
import com.akto.util.EmailAccountName;
import com.akto.util.enums.GlobalEnums;
import com.akto.util.enums.GlobalEnums.Severity;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class Main {
    private static final LoggerMaker loggerMaker = new LoggerMaker(Main.class);

    private static final Logger logger = LoggerFactory.getLogger(Main.class);


    public static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public static final ScheduledExecutorService testTelemetryScheduler = Executors.newScheduledThreadPool(2);

    public static final ScheduledExecutorService schedulerAccessMatrix = Executors.newScheduledThreadPool(2);

    public static boolean SKIP_SSRF_CHECK = ("true".equalsIgnoreCase(System.getenv("SKIP_SSRF_CHECK")) || !DashboardMode.isSaasDeployment());
    public static final boolean IS_SAAS = "true".equalsIgnoreCase(System.getenv("IS_SAAS"));

    private static Map<String, Integer> emptyCountIssuesMap = new HashMap<>();

    static {
        emptyCountIssuesMap.put(Severity.HIGH.toString(), 0);
        emptyCountIssuesMap.put(Severity.MEDIUM.toString(), 0);
        emptyCountIssuesMap.put(Severity.LOW.toString(), 0);
    }

    private static TestingRunResultSummary createTRRSummaryIfAbsent(TestingRun testingRun, int start){
        ObjectId testingRunId = new ObjectId(testingRun.getHexId());

        return TestingRunResultSummariesDao.instance.getMCollection().findOneAndUpdate(
                Filters.and(
                        Filters.eq(TestingRunResultSummary.TESTING_RUN_ID, testingRunId),
                        Filters.eq(TestingRunResultSummary.STATE,TestingRun.State.SCHEDULED)
                ),
                Updates.combine(
                        Updates.set(TestingRunResultSummary.STATE, TestingRun.State.RUNNING),
                        Updates.setOnInsert(TestingRunResultSummary.START_TIMESTAMP, start),
                        Updates.set(TestingRunResultSummary.TEST_RESULTS_COUNT, 0),
                        Updates.set(TestingRunResultSummary.COUNT_ISSUES, emptyCountIssuesMap)
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

        // if you change this delta, update this delta in method getCurrentRunningTestsSummaries
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
        if(testingRun.getTestingRunConfig() != null){
            logger.info(testingRun.getTestingRunConfig().toString());
        }else{
            logger.info("Testing run config is null.");
        }
    }

    public static void main(String[] args) throws InterruptedException {
        String mongoURI = System.getenv("AKTO_MONGO_CONN");
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

        if (!SKIP_SSRF_CHECK) {
            Setup setup = SetupDao.instance.findOne(new BasicDBObject());
            String dashboardMode = setup.getDashboardMode();
            if (dashboardMode != null) {
                boolean isSaas = dashboardMode.equalsIgnoreCase(DashboardMode.SAAS.name());
                if (!isSaas) SKIP_SSRF_CHECK = true;
            }
        }

        loggerMaker.infoAndAddToDb("Starting.......", LogDb.TESTING);

        schedulerAccessMatrix.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTaskForNonHybridAccounts(account -> {
                    AccessMatrixAnalyzer matrixAnalyzer = new AccessMatrixAnalyzer();
                    try {
                        matrixAnalyzer.run();
                    } catch (Exception e) {
                        loggerMaker.infoAndAddToDb("could not run matrixAnalyzer: " + e.getMessage(), LogDb.TESTING);
                    }
                },"matrix-analyser-task");
            }
        }, 0, 1, TimeUnit.MINUTES);
        GetRunningTestsStatus.getRunningTests().getStatusOfRunningTests();

        loggerMaker.infoAndAddToDb("sun.arch.data.model: " +  System.getProperty("sun.arch.data.model"), LogDb.TESTING);
        loggerMaker.infoAndAddToDb("os.arch: " + System.getProperty("os.arch"), LogDb.TESTING);
        loggerMaker.infoAndAddToDb("os.version: " + System.getProperty("os.version"), LogDb.TESTING);

        while (true) {
            AccountTask.instance.executeTaskForNonHybridAccounts(account -> {
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

                FeatureAccess featureAccess = UsageMetricUtils.getFeatureAccess(accountId, MetricTypes.TEST_RUNS);

                loggerMaker.infoAndAddToDb("Starting test for accountID: " + accountId);

                boolean isTestingRunRunning = testingRun.getState().equals(State.RUNNING);

                if (featureAccess.checkInvalidAccess()) {
                    loggerMaker.infoAndAddToDb("Test runs overage detected for account: " + accountId + ". Failing test run at " + start, LogDb.TESTING);
                    TestingRunDao.instance.getMCollection().findOneAndUpdate(
                            Filters.eq(Constants.ID, testingRun.getId()),
                            Updates.set(TestingRun.STATE, TestingRun.State.FAILED));

                    TestingRunResultSummariesDao.instance.getMCollection().findOneAndUpdate(
                            Filters.eq(Constants.ID, summaryId),
                            Updates.set(TestingRun.STATE, TestingRun.State.FAILED));
                    return;
                }

                SyncLimit syncLimit = featureAccess.fetchSyncLimit();
                // saving the initial usageLeft, to calc delta later.
                int usageLeft = syncLimit.getUsageLeft();

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
                            TestingRunDao.instance.getMCollection().findOneAndUpdate(
                                Filters.eq("_id", testingRun.getId()),  completedUpdate
                            );
                            return;
                        }
                    }
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
                                trrs.setTestResultsCount(0);
                                trrs.setCountIssues(emptyCountIssuesMap);
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
                    RequiredConfigs.initiate();
                    testExecutor.init(testingRun, summaryId, syncLimit);
                    raiseMixpanelEvent(summaryId, testingRun, accountId);
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
                } else if (testingRun.getPeriodInSeconds() == -1) {
                    completedUpdate = Updates.combine(
                            Updates.set(TestingRun.STATE, TestingRun.State.SCHEDULED),
                            Updates.set(TestingRun.END_TIMESTAMP, Context.now()),
                            Updates.set(TestingRun.SCHEDULE_TIMESTAMP, testingRun.getScheduleTimestamp() + 5 * 60)
                    );
                }

                if(GetRunningTestsStatus.getRunningTests().isTestRunning(testingRun.getId())){
                    loggerMaker.infoAndAddToDb("Updating status of running test to Completed.");
                    TestingRunDao.instance.getMCollection().findOneAndUpdate(
                            Filters.eq("_id", testingRun.getId()),  completedUpdate
                    );
                }

                if(summaryId != null && testingRun.getTestIdConfig() != 1){
                    TestExecutor.updateTestSummary(summaryId);
                }

                loggerMaker.infoAndAddToDb("Tests completed in " + (Context.now() - start) + " seconds for account: " + accountId, LogDb.TESTING);

                Organization organization = OrganizationsDao.instance.findOne(
                        Filters.in(Organization.ACCOUNTS, Context.accountId.get()));

                if(organization != null && organization.getTestTelemetryEnabled()){
                    loggerMaker.infoAndAddToDb("Test telemetry enabled for account: " + accountId + ", sending results", LogDb.TESTING);
                    ObjectId finalSummaryId = summaryId;
                    testTelemetryScheduler.execute(() -> {
                        Context.accountId.set(accountId);
                        try {
                            com.akto.onprem.Constants.sendTestResults(finalSummaryId, organization);
                            loggerMaker.infoAndAddToDb("Test telemetry sent for account: " + accountId, LogDb.TESTING);
                        } catch (Exception e) {
                            loggerMaker.errorAndAddToDb(e, "Error in sending test telemetry for account: " + accountId);
                        }
                    });
                } else {
                    loggerMaker.infoAndAddToDb("Test telemetry disabled for account: " + accountId, LogDb.TESTING);
                }

                // update usage after test is completed.
                 int deltaUsage = 0;
                 if(syncLimit.checkLimit){
                     deltaUsage = usageLeft - syncLimit.getUsageLeft();
                 }
 
                 UsageMetricHandler.calcAndFetchFeatureAccessUsingDeltaUsage(MetricTypes.TEST_RUNS, accountId, deltaUsage);

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

    private static void raiseMixpanelEvent(ObjectId summaryId, TestingRun testingRun, int accountId) {
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
                            Filters.eq(TestingRunResult.TEST_SUB_TYPE, testingRunIssues.getId().getTestSubCategory()),
                            Filters.eq(TestingRunResult.API_INFO_KEY, testingRunIssues.getId().getApiInfoKey())
                    );
                    TestingRunResult testingRunResult = TestingRunResultDao.instance.findOne(filterForRunResult, Projections.include("_id"));
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

        SlackAlerts apiTestStatusAlert = new APITestStatusAlert(
                testingRun.getName(),
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
        if (testingRun.getSendSlackAlert()) {
            SlackSender.sendAlert(accountId, apiTestStatusAlert);
        }

        AktoMixpanel aktoMixpanel = new AktoMixpanel();
        aktoMixpanel.sendEvent(distinct_id, "Test executed", props);
    }
}