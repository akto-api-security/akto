package com.akto.testing_utils;

import com.akto.dao.context.Context;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.TestingRunResult;
import com.akto.util.enums.GlobalEnums;
import com.mongodb.client.model.Filters;

import java.util.ArrayList;
import java.util.List;

public class TestingUtils {
    //Private constructor so that it's just a utility class
    private TestingUtils() {}

    private static boolean isExists (List<TestingIssuesId> idList, TestingIssuesId issueId) {
        final boolean[] found = {false};
        idList.forEach((issue) -> {
            if (issue.equals(issueId)) {
                found[0] = true;
            }
        });
        return found[0];
    }

    public static List<TestingIssuesId> listOfIssuesIdsFromTestingRunResults(List<TestingRunResult> testingRunResults) {

        List<TestingIssuesId> idList = new ArrayList<>();
        testingRunResults.forEach((runResult) -> {
            TestingIssuesId issueId = new TestingIssuesId(runResult.getApiInfoKey(),
                    GlobalEnums.TestErrorSource.AUTOMATED_TESTING,
                    GlobalEnums.TestCategory.getTestCategory(runResult.getTestSuperType()));
            if (!isExists(idList, issueId) && runResult.isVulnerable()) {
                idList.add(issueId);
            }
        });

        return idList;
    }

    public static List<TestingRunIssues> testingRunIssuesList(List<TestingIssuesId> issuesIds) {
        List<TestingRunIssues> testingRunIssuesList = new ArrayList<>(issuesIds.size());
        issuesIds.forEach((issueId) -> {
            TestingRunIssues testingRunIssue = new TestingRunIssues(issueId, issueId.getTestCategory().getSeverity(),
                    GlobalEnums.TestRunIssueStatus.OPEN,Context.now(),Context.now());
            testingRunIssuesList.add(testingRunIssue);
        });
        return testingRunIssuesList;
    }
}
