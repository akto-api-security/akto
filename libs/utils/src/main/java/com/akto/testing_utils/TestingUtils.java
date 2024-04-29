package com.akto.testing_utils;

import com.akto.dao.testing.sources.TestSourceConfigsDao;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.sources.TestSourceConfig;
import com.akto.util.enums.GlobalEnums;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestingUtils {
    //Private constructor so that it's just a utility class
    private TestingUtils() {
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
