package com.akto.testing;

import com.akto.DaoInit;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.AccountsDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunConfigDao;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dto.Account;
import com.akto.dto.AccountSettings;
import com.akto.dto.testing.TestingRun;
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
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
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

    public static final boolean SKIP_SSRF_CHECK = "true".equalsIgnoreCase(System.getenv("SKIP_SSRF_CHECK"));

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
                    ObjectId summaryId = createTRRSummaryIfAbsent(testingRun, start);
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

        String dashboardMode = "saas";

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


        AktoMixpanel aktoMixpanel = new AktoMixpanel();
        aktoMixpanel.sendEvent(distinct_id, "Test executed", props);
    }
}