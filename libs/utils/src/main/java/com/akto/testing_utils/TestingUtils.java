package com.akto.testing_utils;

import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dao.testing.VulnerableTestingRunResultDao;
import com.akto.dao.testing.sources.TestSourceConfigsDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.TestingRunResultSummary;
import com.akto.dto.testing.sources.TestSourceConfig;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums;
import com.akto.util.enums.GlobalEnums.TestErrorSource;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TestingUtils {
    //Private constructor so that it's just a utility class
    private TestingUtils() {
    }

    private static TestingIssuesId getTestingIssueIdFromRunResult(TestingRunResult runResult) {
        return new TestingIssuesId(runResult.getApiInfoKey(), TestErrorSource.AUTOMATED_TESTING,
            runResult.getTestSubType());
    }

    // Recounts open issues for a single summary and updates its countIssues + isIgnoredResult flags.
    // Shared by StartTestAction.handleRefreshTableCount (manual refresh) and
    // IssuesAction.bulkUpdateIssueStatus (ignore/reopen from the issues page).
    public static void recountAndUpdateSummaryCount(ObjectId summaryObjectId){
        boolean isStoredInVulnerableCollection = VulnerableTestingRunResultDao.instance.isStoredInVulnerableCollection(summaryObjectId, true);
       
        List<TestingRunResult> testingRunResults = VulnerableTestingRunResultDao.instance.findAll(
            Filters.and(
                Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, summaryObjectId),
                Filters.eq(TestingRunResult.VULNERABLE, true)
            ),
            Projections.include(TestingRunResult.API_INFO_KEY, TestingRunResult.TEST_SUB_TYPE),
            isStoredInVulnerableCollection
        );

        if(testingRunResults.isEmpty()){
            return;
        }

        Set<TestingIssuesId> issuesIds = new HashSet<>();
        Map<TestingIssuesId, ObjectId> mapIssueToResultId = new HashMap<>();
        Set<ObjectId> ignoredResults = new HashSet<>();
        for(TestingRunResult runResult: testingRunResults){
            TestingIssuesId issuesId = getTestingIssueIdFromRunResult(runResult);
            issuesIds.add(issuesId);
            mapIssueToResultId.put(issuesId, runResult.getId());
            ignoredResults.add(runResult.getId());
        }

        List<TestingRunIssues> issues = TestingRunIssuesDao.instance.findAll(
            Filters.and(
                Filters.in(Constants.ID, issuesIds),
                Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, GlobalEnums.TestRunIssueStatus.OPEN)
            ), Projections.include(TestingRunIssues.KEY_SEVERITY)
        );

        Map<String, Integer> totalCountIssues = new HashMap<>();
        totalCountIssues.put("CRITICAL", 0);
        totalCountIssues.put("HIGH", 0);
        totalCountIssues.put("MEDIUM", 0);
        totalCountIssues.put("LOW", 0);

        // Results whose issue is OPEN must NOT be flagged ignored (handles reopen restoring the count).
        Set<ObjectId> openResults = new HashSet<>();
        for(TestingRunIssues runIssue: issues){
            int initCount = totalCountIssues.getOrDefault(runIssue.getSeverity().name(), 0);
            totalCountIssues.put(runIssue.getSeverity().name(), initCount + 1);
            if(mapIssueToResultId.containsKey(runIssue.getId())){
                ObjectId resId = mapIssueToResultId.get(runIssue.getId());
                ignoredResults.remove(resId);
                openResults.add(resId);
            }
        }

        // update testing run result summary
        TestingRunResultSummariesDao.instance.updateOne(
            Filters.eq(Constants.ID, summaryObjectId),
            Updates.set(TestingRunResultSummary.COUNT_ISSUES, totalCountIssues)
        );

        // flag resolved results ignored, and clear the flag on results whose issue is open again (reopen)
        if(isStoredInVulnerableCollection){
            VulnerableTestingRunResultDao.instance.updateMany(
                Filters.in(Constants.ID, ignoredResults),
                Updates.set(TestingRunResult.IS_IGNORED_RESULT, true)
            );
            VulnerableTestingRunResultDao.instance.updateMany(
                Filters.in(Constants.ID, openResults),
                Updates.set(TestingRunResult.IS_IGNORED_RESULT, false)
            );
        }else{
            TestingRunResultDao.instance.updateMany(
                Filters.in(Constants.ID, ignoredResults),
                Updates.set(TestingRunResult.IS_IGNORED_RESULT, true)
            );
            TestingRunResultDao.instance.updateMany(
                Filters.in(Constants.ID, openResults),
                Updates.set(TestingRunResult.IS_IGNORED_RESULT, false)
            );
        }
    }



    private static boolean doesExists(List<TestingIssuesId> idList, TestingIssuesId issueId) {
        for (TestingIssuesId issue : idList) {
            if (issue.equals(issueId)) {
                return true;
            }
        }
        return false;
    }

    public static Map<TestingIssuesId, TestingRunResult> listOfIssuesIdsFromTestingRunResults(List<TestingRunResult> testingRunResults,
                                                                                              boolean isAutomatedTesting, boolean triggeredByTestEditor) {

        HashMap<TestingIssuesId, TestingRunResult> mapOfIssueIdsvsTestingRunResult = new HashMap<>();
        List<TestingIssuesId> idList = new ArrayList<>();
        testingRunResults.forEach(runResult -> {
            String subType = runResult.getTestSubType();
            TestSourceConfig config = null;
            // name = subtype
            String subCategory = subType;
            if (subCategory.startsWith("http")) {//Issue came from custom template
                config = TestSourceConfigsDao.instance.getTestSourceConfig(subType);
            }

            GlobalEnums.TestErrorSource testErrorSource;

            if (triggeredByTestEditor) {
                testErrorSource = GlobalEnums.TestErrorSource.TEST_EDITOR;
            } else {
                testErrorSource = isAutomatedTesting ?
                GlobalEnums.TestErrorSource.AUTOMATED_TESTING : GlobalEnums.TestErrorSource.RUNTIME;
            }

            TestingIssuesId issueId = new TestingIssuesId(runResult.getApiInfoKey(), testErrorSource,
                    subCategory, config != null ?config.getId() : null);
            if (!doesExists(idList, issueId)) {
                idList.add(issueId);
                mapOfIssueIdsvsTestingRunResult.put(issueId, runResult);
            }
        });
        return mapOfIssueIdsvsTestingRunResult;
    }
}
