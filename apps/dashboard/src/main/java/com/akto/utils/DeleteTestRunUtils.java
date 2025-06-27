package com.akto.utils;

import java.util.List;
import java.util.Map;

import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.akto.dao.context.Context;
import com.akto.dao.testing.DeleteTestRunsDao;
import com.akto.dao.testing.TestingRunConfigDao;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.DeleteTestRuns;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.usage.MetricTypes;
import com.akto.usage.UsageMetricHandler;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;

public class DeleteTestRunUtils {

    private static Map<String, Boolean> deleteTestRunsCollections(List<ObjectId> testRunIds,
            List<ObjectId> latestSummaryIds, List<Integer> testConfigIds,
            Map<String, Boolean> testingCollectionsDeleted) {
                
        if (!testingCollectionsDeleted.getOrDefault("testing_run_result", false)) {
            DeleteResult testingRunResultDeleteResult = TestingRunResultDao.instance.deleteAll(Filters.in(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, latestSummaryIds));

            /*
             * This count may not be accurate, since this may include deactivated/demo
             * collection or old tests which are not being counted in usage.
             * Any inaccuracies here, would be fixed in the next calcUsage cycle (4 hrs)
             */
            int deltaUsage = -1 * (int)testingRunResultDeleteResult.getDeletedCount();
            UsageMetricHandler.calcAndFetchFeatureAccessUsingDeltaUsage(MetricTypes.TEST_RUNS, Context.accountId.get(), deltaUsage);

            testingCollectionsDeleted.put("testing_run_result", true);
        }

        if (!testingCollectionsDeleted.getOrDefault("testing_run_issues", false)) {
            TestingRunIssuesDao.instance
                    .deleteAll(Filters.in(TestingRunIssues.LATEST_TESTING_RUN_SUMMARY_ID, latestSummaryIds));
            testingCollectionsDeleted.put("testing_run_issues", true);
        }

        if (!testingCollectionsDeleted.getOrDefault("testing_run_result_summaries", false)) {
            TestingRunResultSummariesDao.instance
                    .deleteAll(Filters.in("_id", latestSummaryIds));
            testingCollectionsDeleted.put("testing_run_result_summaries", true);
        }

        if (!testingCollectionsDeleted.getOrDefault("testing_run_config", false)) {
            TestingRunConfigDao.instance.deleteAll(Filters.in("_id", testConfigIds));
            testingCollectionsDeleted.put("testing_run_config", true);
        }

        if (!testingCollectionsDeleted.getOrDefault("testing_run", false)) {
            TestingRunDao.instance.deleteAll(Filters.in("_id", testRunIds));
            testingCollectionsDeleted.put("testing_run", true);
        }

        return testingCollectionsDeleted;
    }

    public static void deleteTestRunsFromDb(DeleteTestRuns deleteTestRuns) {
        Map<String, Boolean> testingCollectionsDeleted = deleteTestRuns.getTestingCollectionsDeleted();
        List<ObjectId> testRunIds = deleteTestRuns.getTestRunIds();
        List<ObjectId> latestSummaryIds = deleteTestRuns.getLatestTestingSummaryIds();
        List<Integer> testConfigIds = deleteTestRuns.getTestConfigIds();

        testingCollectionsDeleted = deleteTestRunsCollections(testRunIds, latestSummaryIds, testConfigIds,
                testingCollectionsDeleted);

        Bson filter = Filters.in(DeleteTestRuns.TEST_RUNS_IDS, testRunIds);
        deleteTestRuns.setTestingCollectionsDeleted(testingCollectionsDeleted);
        if (isTestRunDeleted(deleteTestRuns)) {
            DeleteTestRunsDao.instance.getMCollection().deleteOne(filter);
        } else {
            DeleteTestRunsDao.instance.updateOne(filter,
                    Updates.set(DeleteTestRuns.TESTING_COLLECTIONS_DELETED, testingCollectionsDeleted));
        }
    }

    public static boolean isTestRunDeleted(DeleteTestRuns deleteTestRuns) {
        Map<String,Boolean> testingCollectionsDeleted = deleteTestRuns.getTestingCollectionsDeleted();
        return !(testingCollectionsDeleted.values().stream().anyMatch(value -> !value));
    }
    
}
