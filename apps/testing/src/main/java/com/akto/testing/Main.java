package com.akto.testing;

import com.akto.DaoInit;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.AccountsDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.TestConfigYamlParser;
import com.akto.dao.testing.TestingRunConfigDao;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dto.Account;
import com.akto.dto.AccountSettings;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.TestingRunResultSummary;
import com.akto.dto.testing.rate_limit.ApiRateLimit;
import com.akto.dto.testing.rate_limit.GlobalApiRateLimit;
import com.akto.dto.testing.rate_limit.RateLimitHandler;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.store.AuthMechanismStore;
import com.akto.store.SampleMessageStore;
import com.akto.util.AccountTask;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;

public class Main {
    private static final LoggerMaker loggerMaker = new LoggerMaker(Main.class);

    public static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

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

                Bson filter1 = Filters.eq(TestingRun.STATE, TestingRun.State.SCHEDULED);
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

                loggerMaker.infoAndAddToDb("Found one + " + testingRun.getId().toHexString(), LogDb.TESTING);

                try {
                    AccountSettings accountSettings = AccountSettingsDao.instance.findOne(new BasicDBObject());
                    boolean runStatusCodeAnalyser = accountSettings == null ||
                            accountSettings.getSetupType() != AccountSettings.SetupType.PROD;

                    SampleMessageStore sampleMessageStore = SampleMessageStore.create();
                    AuthMechanismStore authMechanismStore = AuthMechanismStore.create();


                    TestExecutor testExecutor = new TestExecutor();
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
                    if (runStatusCodeAnalyser) {
                        StatusCodeAnalyser.run(sampleMessageStore.getSampleDataMap(),sampleMessageStore, authMechanismStore, testingRun.getTestingRunConfig());
                    }

            ObjectId summaryId = createTRRSummaryIfAbsent(testingRun, start);
            loggerMaker.infoAndAddToDb("Using testing run summary: " + summaryId, LogDb.TESTING);

                    testExecutor.init(testingRun, sampleMessageStore, authMechanismStore, summaryId);
                } catch (Exception e) {
                    e.printStackTrace();
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
}