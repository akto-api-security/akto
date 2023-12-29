package com.akto.dao.testing;

import java.util.List;
import java.util.Map;

import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.DeleteRunsInfo;
import com.akto.dto.testing.TestingRunResult;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public class DeleteRunsInfoDao extends AccountsContextDao<DeleteRunsInfo> {

    public static final DeleteRunsInfoDao instance = new DeleteRunsInfoDao();

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

    public void deleteTestRunsFromDb(DeleteRunsInfo deleteRunsInfo) {
        Map<String, Boolean> testingCollectionsDeleted = deleteRunsInfo.getTestingCollectionsDeleted();
        List<ObjectId> testRunIds = deleteRunsInfo.getTestRunIds();
        List<ObjectId> latestSummaryIds = deleteRunsInfo.getLatestTestingSummaryIds();
        List<Integer> testConfigIds = deleteRunsInfo.getTestConfigIds();

        testingCollectionsDeleted = deleteTestRunsCollections(testRunIds, latestSummaryIds, testConfigIds,
                testingCollectionsDeleted);

        Bson filter = Filters.in(DeleteRunsInfo.TEST_RUNS_IDS, testRunIds);
        deleteRunsInfo.setTestingCollectionsDeleted(testingCollectionsDeleted);
        if (DeleteRunsInfoDao.instance.isTestRunDeleted(deleteRunsInfo)) {
            DeleteRunsInfoDao.instance.getMCollection().deleteOne(filter);
        } else {
            DeleteRunsInfoDao.instance.updateOne(filter,
                    Updates.set(DeleteRunsInfo.TESTING_COLLECTIONS_DELETED, testingCollectionsDeleted));
        }
    }

    public boolean isTestRunDeleted(DeleteRunsInfo deleteRunsInfo) {
        Map<String,Boolean> testingCollectionsDeleted = deleteRunsInfo.getTestingCollectionsDeleted();
        return !(testingCollectionsDeleted.values().stream().anyMatch(value -> !value));
    }

    @Override
    public String getCollName() {
        return "delete_runs_info";
    }

    @Override
    public Class<DeleteRunsInfo> getClassT() {
        return DeleteRunsInfo.class;
    }
}
