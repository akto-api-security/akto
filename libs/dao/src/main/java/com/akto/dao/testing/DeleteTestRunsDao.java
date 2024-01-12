package com.akto.dao.testing;

import java.util.List;
import java.util.Map;

import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.DeleteTestRuns;
import com.akto.dto.testing.TestingRunResult;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public class DeleteTestRunsDao extends AccountsContextDao<DeleteTestRuns> {

    public static final DeleteTestRunsDao instance = new DeleteTestRunsDao();

    private static Map<String, Boolean> deleteTestRunsCollections(List<ObjectId> testRunIds,
            List<ObjectId> latestSummaryIds, List<Integer> testConfigIds,
            Map<String, Boolean> testingCollectionsDeleted) {
                
        if (!testingCollectionsDeleted.getOrDefault("testing_run_result", false)) {
            TestingRunResultDao.instance.deleteAll(Filters.in(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, latestSummaryIds));
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

    public void deleteTestRunsFromDb(DeleteTestRuns deleteTestRuns) {
        Map<String, Boolean> testingCollectionsDeleted = deleteTestRuns.getTestingCollectionsDeleted();
        List<ObjectId> testRunIds = deleteTestRuns.getTestRunIds();
        List<ObjectId> latestSummaryIds = deleteTestRuns.getLatestTestingSummaryIds();
        List<Integer> testConfigIds = deleteTestRuns.getTestConfigIds();

        testingCollectionsDeleted = deleteTestRunsCollections(testRunIds, latestSummaryIds, testConfigIds,
                testingCollectionsDeleted);

        Bson filter = Filters.in(DeleteTestRuns.TEST_RUNS_IDS, testRunIds);
        deleteTestRuns.setTestingCollectionsDeleted(testingCollectionsDeleted);
        if (DeleteTestRunsDao.instance.isTestRunDeleted(deleteTestRuns)) {
            DeleteTestRunsDao.instance.getMCollection().deleteOne(filter);
        } else {
            DeleteTestRunsDao.instance.updateOne(filter,
                    Updates.set(DeleteTestRuns.TESTING_COLLECTIONS_DELETED, testingCollectionsDeleted));
        }
    }

    public boolean isTestRunDeleted(DeleteTestRuns deleteTestRuns) {
        Map<String,Boolean> testingCollectionsDeleted = deleteTestRuns.getTestingCollectionsDeleted();
        return !(testingCollectionsDeleted.values().stream().anyMatch(value -> !value));
    }

    @Override
    public String getCollName() {
        return "delete_test_runs_info";
    }

    @Override
    public Class<DeleteTestRuns> getClassT() {
        return DeleteTestRuns.class;
    }
}
