package com.akto.testing;

import com.akto.billing.UsageMetricUtils;
import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.data_actor.DbLayer;
import com.akto.dto.*;
import com.akto.dto.billing.Organization;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.*;
import com.akto.dto.testing.TestingEndpoints.Operator;
import com.akto.dto.testing.TestingRun.State;
import com.akto.dto.testing.rate_limit.ApiRateLimit;
import com.akto.dto.testing.rate_limit.GlobalApiRateLimit;
import com.akto.dto.testing.rate_limit.RateLimitHandler;
import com.akto.github.GithubUtils;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.metrics.AllMetrics;
import com.akto.mixpanel.AktoMixpanel;
import com.akto.notifications.slack.APITestStatusAlert;
import com.akto.notifications.slack.NewIssuesModel;
import com.akto.notifications.slack.SlackAlerts;
import com.akto.notifications.slack.SlackSender;
import com.akto.util.DashboardMode;
import com.akto.util.EmailAccountName;
import com.akto.util.enums.GlobalEnums;
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

public class Main {
    private static final LoggerMaker loggerMaker = new LoggerMaker(Main.class);

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    public static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public static final ScheduledExecutorService testTelemetryScheduler = Executors.newScheduledThreadPool(2);

    public static final ScheduledExecutorService schedulerAccessMatrix = Executors.newScheduledThreadPool(2);

    public static boolean SKIP_SSRF_CHECK = ("true".equalsIgnoreCase(System.getenv("SKIP_SSRF_CHECK")) || !DashboardMode.isSaasDeployment());
    public static final boolean IS_SAAS = "true".equalsIgnoreCase(System.getenv("IS_SAAS"));

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

    public static Set<Integer> extractApiCollectionIds(List<ApiInfo.ApiInfoKey> apiInfoKeyList) {
        Set<Integer> ret = new HashSet<>();
        for(ApiInfo.ApiInfoKey apiInfoKey: apiInfoKeyList) {
            ret.add(apiInfoKey.getApiCollectionId());
        }

        return ret;
    }
    private static final int LAST_TEST_RUN_EXECUTION_DELTA = 5 * 60;

    private static void setTestingRunConfig(TestingRun testingRun, TestingRunResultSummary trrs) {
        long timestamp = testingRun.getId().getTimestamp();
        long seconds = Context.now() - timestamp;
        loggerMaker.infoAndAddToDb("Found one + " + testingRun.getId().toHexString() + " created: " + seconds + " seconds ago", LogDb.TESTING);

        TestingRunConfig configFromTrrs = null;
        TestingRunConfig baseConfig = null;

        if (trrs != null && trrs.getTestIdConfig() > 1) {
            configFromTrrs = dataActor.findTestingRunConfig(trrs.getTestIdConfig());
            loggerMaker.infoAndAddToDb("Found testing run trrs config with id :" + configFromTrrs.getId(), LogDb.TESTING);
        }

        if (testingRun.getTestIdConfig() > 1) {
            baseConfig = dataActor.findTestingRunConfig(testingRun.getTestIdConfig());
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
        AccountSettings accountSettings = dataActor.fetchAccountSettings();
        setupRateLimitWatcher(accountSettings);

        if (!SKIP_SSRF_CHECK) {
            Setup setup = dataActor.fetchSetup();
            String dashboardMode = setup.getDashboardMode();
            if (dashboardMode != null) {
                boolean isSaas = dashboardMode.equalsIgnoreCase(DashboardMode.SAAS.name());
                if (!isSaas) SKIP_SSRF_CHECK = true;
            }
        }

        loggerMaker.infoAndAddToDb("Starting.......", LogDb.TESTING);

        schedulerAccessMatrix.scheduleAtFixedRate(new Runnable() {
            public void run() {
                Context.accountId.set(accountSettings.getId());
                AccessMatrixAnalyzer matrixAnalyzer = new AccessMatrixAnalyzer();
                try {
                    matrixAnalyzer.run();
                } catch (Exception e) {
                    loggerMaker.infoAndAddToDb("could not run matrixAnalyzer: " + e.getMessage(), LogDb.TESTING);
                }
            }
        }, 0, 1, TimeUnit.MINUTES);



        loggerMaker.infoAndAddToDb("sun.arch.data.model: " +  System.getProperty("sun.arch.data.model"), LogDb.TESTING);
        loggerMaker.infoAndAddToDb("os.arch: " + System.getProperty("os.arch"), LogDb.TESTING);
        loggerMaker.infoAndAddToDb("os.version: " + System.getProperty("os.version"), LogDb.TESTING);
        
        Map<Integer, Integer> logSentMap = new HashMap<>();

        Context.accountId.set(accountSettings.getId());

        while (true) {
            int accountId = accountSettings.getId();
            int start = Context.now();
            long startDetailed = System.currentTimeMillis();
            int delta = start - 20*60;

            TestingRunResultSummary trrs = dataActor.findPendingTestingRunResultSummary(start, delta);
            boolean isSummaryRunning = trrs != null && trrs.getState().equals(State.RUNNING);
            TestingRun testingRun;
            ObjectId summaryId = null;
            if (trrs == null) {
                delta = Context.now() - 20*60;
                testingRun = dataActor.findPendingTestingRun(delta, null);
            } else {
                summaryId = trrs.getId();
                loggerMaker.infoAndAddToDb("Found trrs " + trrs.getHexId() +  " for account: " + accountId);
                testingRun = dataActor.findTestingRun(trrs.getTestingRunId().toHexString());
            }

            if (testingRun == null) {
                Thread.sleep(1000);
                continue;
            }

            if (testingRun.getState().equals(State.STOPPED)) {
                loggerMaker.infoAndAddToDb("Testing run stopped");
                if (trrs != null) {
                    loggerMaker.infoAndAddToDb("Stopping TRRS: " + trrs.getId());
                    dataActor.updateTestRunResultSummaryNoUpsert(trrs.getId().toHexString());
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
                dataActor.updateTestingRun(testingRun.getId().toHexString(), testingRun.getPeriodInSeconds(),testingRun.getScheduleTimestamp());
                dataActor.updateTestRunResultSummary(summaryId.toHexString());
                return;
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

                if (isSummaryRunning || isTestingRunRunning) {
                    loggerMaker.infoAndAddToDb("TRRS or TR is in running state, checking if it should run it or not");
                    TestingRunResultSummary testingRunResultSummary;
                    if (trrs != null) {
                        testingRunResultSummary = trrs;
                    } else {
                        Map<ObjectId, TestingRunResultSummary> objectIdTestingRunResultSummaryMap = dataActor.fetchTestingRunResultSummaryMap(testingRun.getId().toHexString());
                        testingRunResultSummary = objectIdTestingRunResultSummaryMap.get(testingRun.getId());
                    }

                    if (testingRunResultSummary != null) {
                        List<TestingRunResult> testingRunResults = dataActor.fetchLatestTestingRunResult(testingRunResultSummary.getId().toHexString());
                        if (testingRunResults != null && !testingRunResults.isEmpty()) {
                            TestingRunResult testingRunResult = testingRunResults.get(0);
                            if (Context.now() - testingRunResult.getEndTimestamp() < LAST_TEST_RUN_EXECUTION_DELTA) {
                                loggerMaker.infoAndAddToDb("Skipping test run as it was executed recently, TRR_ID:"
                                        + testingRunResult.getHexId() + ", TRRS_ID:" + testingRunResultSummary.getHexId() + " TR_ID:" + testingRun.getHexId(), LogDb.TESTING);
                                return;
                            } else {
                                loggerMaker.infoAndAddToDb("Test run was executed long ago, TRR_ID:"
                                        + testingRunResult.getHexId() + ", TRRS_ID:" + testingRunResultSummary.getHexId() + " TR_ID:" + testingRun.getHexId(), LogDb.TESTING);
                                TestingRunResultSummary summary = dataActor.markTestRunResultSummaryFailed(testingRunResultSummary.getId().toHexString());
                                if (summary == null) {
                                    loggerMaker.infoAndAddToDb("Skipping because some other thread picked it up, TRRS_ID:" + testingRunResultSummary.getHexId() + " TR_ID:" + testingRun.getHexId(), LogDb.TESTING);
                                    return;
                                }

                                TestingRunResultSummary runResultSummary = dataActor.fetchTestingRunResultSummary(testingRunResultSummary.getId().toHexString());
                                GithubUtils.publishGithubComments(runResultSummary);
                            }
                        } else {
                            loggerMaker.infoAndAddToDb("No executions made for this test, will need to restart it, TRRS_ID:" + testingRunResultSummary.getHexId() + " TR_ID:" + testingRun.getHexId(), LogDb.TESTING);
                            TestingRunResultSummary summary = dataActor.markTestRunResultSummaryFailed(testingRunResultSummary.getId().toHexString());
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
                            dataActor.insertTestingRunResultSummary(trrs);
                            summaryId = trrs.getId();
                        } else {
                            trrs = dataActor.createTRRSummaryIfAbsent(testingRun.getHexId(), start);
                            summaryId = trrs.getId();
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
                testExecutor.init(testingRun, summaryId);
                AllMetrics.instance.setTestingRunCount(1);
                //raiseMixpanelEvent(summaryId, testingRun, accountId);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error in init " + e);
            }
            int scheduleTs = 0;

            if (testingRun.getPeriodInSeconds() > 0 ) {
                scheduleTs = testingRun.getScheduleTimestamp() + testingRun.getPeriodInSeconds();
            } else if (testingRun.getPeriodInSeconds() == -1) {
                scheduleTs = testingRun.getScheduleTimestamp() + 5 * 60;
            }

            dataActor.updateTestingRunAndMarkCompleted(testingRun.getId().toHexString(), scheduleTs);

            if(summaryId != null && testingRun.getTestIdConfig() != 1){
                TestExecutor.updateTestSummary(summaryId);
            }

            loggerMaker.infoAndAddToDb("Tests completed in " + (Context.now() - start) + " seconds for account: " + accountId, LogDb.TESTING);
            AllMetrics.instance.setTestingRunLatency(System.currentTimeMillis() - startDetailed);

            Organization organization = dataActor.fetchOrganization(accountId);

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

    private static void raiseMixpanelEvent(ObjectId summaryId, TestingRun testingRun, int accountId) {
        TestingRunResultSummary testingRunResultSummary = dataActor.fetchTestingRunResultSummary(summaryId.toHexString());
        int totalApis = testingRunResultSummary.getTotalApis();

        String testType = "ONE_TIME";
        if(testingRun.getPeriodInSeconds()>0)
        {
            testType = "SCHEDULED DAILY";
        }
        if (testingRunResultSummary.getMetadata() != null) {
            testType = "CI_CD";
        }

        Setup setup = dataActor.fetchSetup();

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
            TestingRunConfig testingRunConfig = dataActor.findTestingRunConfig(testingRun.getTestIdConfig());
            if (testingRunConfig != null && testingRunConfig.getTestSubCategoryList() != null) {
                props.put("Total Tests", testingRunConfig.getTestSubCategoryList().size());
                props.put("Tests Ran", testingRunConfig.getTestSubCategoryList());
            }
        }

        Bson filters = Filters.and(
            Filters.eq("latestTestingRunSummaryId", summaryId),
            Filters.eq("testRunIssueStatus", "OPEN")
        );
        List<TestingRunIssues> testingRunIssuesList = dataActor.fetchOpenIssues(summaryId.toHexString());

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
                    TestingRunResult testingRunResult = dataActor.fetchTestingRunResults(filterForRunResult);
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
            ApiCollection apiCollection = dataActor.fetchApiCollectionMeta(apiCollectionId);
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
        SlackSender.sendAlert(accountId, apiTestStatusAlert);

        AktoMixpanel aktoMixpanel = new AktoMixpanel();
        aktoMixpanel.sendEvent(distinct_id, "Test executed", props);
    }
}