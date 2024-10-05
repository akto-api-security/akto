package com.akto.utils.jobs;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dto.Account;
import com.akto.dto.testing.MultiExecTestResult;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.WorkflowTest;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.AccountTask;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;

public class CleanTestingJob {
    private static final LoggerMaker loggerMaker = new LoggerMaker(CleanTestingJob.class, LogDb.DASHBOARD);
    private static final Logger logger = LoggerFactory.getLogger(CleanTestingJob.class);

    final static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    final static int DATA_STORAGE_TIME = 2 * 30 * 24 * 60 * 60; // two months.

    public static void cleanTestingJobRunner() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                int now = Context.now();
                logger.info("Starting cleanTestingJob for all accounts at " + now);

                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        try {
                            cleanTestingJob();
                        } catch (Exception e) {
                            loggerMaker.errorAndAddToDb(e, "Error in cleanTestingJob " + e.getMessage());
                        }
                    }
                }, "clean-testing-job");
                int now2 = Context.now();
                int diffNow = now2 - now;
                logger.info(String.format("Completed cleanTestingJob for all accounts at %d , time taken : %d", now2,
                        diffNow));
            }
        }, 0, 5, TimeUnit.HOURS);
    }

    private static void cleanTestingJob() {

        int now = Context.now();
        int oldTime = now - DATA_STORAGE_TIME;
        Bson baseFilter = Filters.and(
                Filters.lt(TestingRunResult.END_TIMESTAMP, oldTime),
                Filters.eq(TestingRunResult.VULNERABLE, false));
        String updatedData = "";

        Bson filter = Filters.and(
                baseFilter,
                Filters.exists(TestingRunResult.TEST_RESULTS + "." + TestResult._MESSAGE));
        UpdateResult result = TestingRunResultDao.instance.updateManyNoUpsert(filter,
                // normal tests
                Updates.set(TestingRunResult.TEST_RESULTS + ".$[]." + TestResult._MESSAGE, updatedData));
        loggerMaker.infoAndAddToDb(String.format("Result cleanTestingJob message: matched: %d modified: %d",
                result.getMatchedCount(), result.getModifiedCount()));

        filter = Filters.and(
                baseFilter,
                Filters.exists(TestingRunResult.TEST_RESULTS + "." + TestResult.ORIGINAL_MESSAGE));
        result = TestingRunResultDao.instance.updateManyNoUpsert(filter,
                // normal tests
                Updates.set(TestingRunResult.TEST_RESULTS + ".$[]." + TestResult.ORIGINAL_MESSAGE, updatedData));
        loggerMaker.infoAndAddToDb(String.format("Result cleanTestingJob originalMessage: matched: %d modified: %d",
                result.getMatchedCount(), result.getModifiedCount()));

        filter = Filters.and(
                baseFilter,
                Filters.exists(TestingRunResult.TEST_RESULTS + "." + MultiExecTestResult.NODE_RESULT_MAP));
        result = TestingRunResultDao.instance.updateManyNoUpsert(filter,
                // multi-exec tests
                Updates.unset(TestingRunResult.TEST_RESULTS + ".$[]." + MultiExecTestResult.NODE_RESULT_MAP));
        loggerMaker.infoAndAddToDb(String.format("Result cleanTestingJob multi-exec nodeResultMap: matched: %d modified: %d",
                result.getMatchedCount(), result.getModifiedCount()));

        filter = Filters.and(
                baseFilter,
                Filters.exists(TestingRunResult.WORKFLOW_TEST + "."
                        + WorkflowTest.MAP_NODE_ID_TO_WORKFLOW_NODE_DETAILS));
        result = TestingRunResultDao.instance.updateManyNoUpsert(filter,
                // workflow tests
                Updates.unset(TestingRunResult.WORKFLOW_TEST + "."
                        + WorkflowTest.MAP_NODE_ID_TO_WORKFLOW_NODE_DETAILS));

        loggerMaker.infoAndAddToDb(String.format("Result cleanTestingJob workflowTest mapNodeIdToWorkflowNodeDetails: matched: %d modified: %d",
                result.getMatchedCount(), result.getModifiedCount()));
    }
}
