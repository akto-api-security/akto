package com.akto.testing;

import com.akto.DaoInit;
import com.akto.dao.context.Context;
import com.akto.dto.testing.*;
import com.akto.store.AuthMechanismStore;
import com.akto.store.SampleMessageStore;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    public static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public static void invokeScheduledTests() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                Context.accountId.set(1_000_000);
                int now = Context.now();
                for(TestingSchedule ts: TestingSchedulesDao.instance.findAll(Filters.lte(TestingSchedule.START_TIMESTAMP, now))) {
                    TestingRun sampleTestingRun = ts.getSampleTestingRun();
                    sampleTestingRun.setScheduleTimestamp(now); //insert in DB
                    TestingRunDao.instance.insert(sampleTestingRun);
                    int nextTs = ts.getStartTimestamp() + 86400; // update in DB
                    TestingSchedulesDao.instance.updateOne(Filters.eq("_id", ts.getId()), Updates.set(TestingSchedule.START_TIMESTAMP, nextTs));
                }
            }
        }, 0, 5, TimeUnit.MINUTES);
    }

    public static void main(String[] args) throws InterruptedException {
        logger.info("Starting testing module....");
        String mongoURI = System.getenv("AKTO_MONGO_CONN");;
        DaoInit.init(new ConnectionString(mongoURI));
        Context.accountId.set(1_000_000);

        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                Context.accountId.set(1_000_000);
                SampleMessageStore.fetchSampleMessages();
                AuthMechanismStore.fetchAuthMechanism();
            }
        }, 5, 5, TimeUnit.MINUTES);

        invokeScheduledTests();

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

        SampleMessageStore.fetchSampleMessages();
        AuthMechanismStore.fetchAuthMechanism();

        logger.info("Starting.......");

        StatusCodeAnalyser.run();

        while (true) {
            int start = Context.now();

            TestingRun testingRun = TestingRunDao.instance.getMCollection().findOneAndUpdate(
                    Filters.or(filter1,filter2), update
            );

            // TODO: find a better solution than wait
            if (testingRun == null) {
                try {
                    Thread.sleep(60 * 1000L);
                    continue;
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            logger.info("Found one + " + testingRun.getId().toHexString());

            try {
                TestExecutor.init(testingRun);
            } catch (Exception e) {
                e.printStackTrace();
            }

            Bson completedUpdate = Updates.combine(
                    Updates.set(TestingRun.STATE, TestingRun.State.COMPLETED),
                    Updates.set(TestingRun.END_TIMESTAMP, Context.now())
            );

            TestingRunDao.instance.getMCollection().findOneAndUpdate(
                    Filters.eq("_id", testingRun.getId()),  completedUpdate
            );

            logger.info("Tests completed in " + (Context.now() - start) + " seconds");
        }
    }
}