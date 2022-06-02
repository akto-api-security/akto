package com.akto.testing;

import com.akto.DaoInit;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunDao;
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

    public static void main(String[] args) throws InterruptedException {
        logger.info("Starting testing module....");

        DaoInit.init(new ConnectionString("mongodb://172.18.0.2:27017/admini"));
        Context.accountId.set(1_000_000);

        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                Context.accountId.set(1_000_000);
                SampleMessageStore.fetchSampleMessages();
            }
        }, 5, 5, TimeUnit.MINUTES);

        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                Context.accountId.set(1_000_000);
                AuthMechanismStore.fetchAuthMechanism();
            }
        }, 5, 5, TimeUnit.MINUTES);


        int delta = Context.now() - 60*60;

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
                // TODO:
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