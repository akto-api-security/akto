package com.akto.testing_issues;

import com.akto.dao.context.Context;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.TestingRunResult;
import com.akto.testing.TestExecutor;
import com.akto.testing_utils.TestingUtils;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.akto.util.Constants.ID;
import static com.akto.util.enums.GlobalEnums.*;

public class TestingIssuesHandler {

    private static final Logger logger = LoggerFactory.getLogger(TestExecutor.class);
    private TestingIssuesHandler(){}
    //Update one Write models
    /*
     * Checks the status of issue from db,
     *
     * */

    private void writeUpdateQueryIntoWriteModel (List<WriteModel<TestingRunIssues>> writeModelList,
                                                       Map<TestingIssuesId, TestingRunResult> testingIssuesIdsMap,
                                                       List<TestingRunIssues> testingRunIssuesList) {
        int lastSeen = Context.now();

        testingRunIssuesList.forEach(testingRunIssues -> {
            TestingIssuesId issuesId = testingRunIssues.getId();

            TestingRunResult runResult = testingIssuesIdsMap.get(getIssuesIdFromMap(issuesId, testingIssuesIdsMap));
            TestRunIssueStatus status = testingRunIssues.getTestRunIssueStatus();
            Bson query = Filters.eq(ID, issuesId);
            Bson updateStatusFields;
            Bson updateSeverityField;
            if (runResult.isVulnerable()) {
                if (status == TestRunIssueStatus.IGNORED) {
                    updateStatusFields = Updates.set(TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestRunIssueStatus.IGNORED);
                } else {
                    updateStatusFields = Updates.set(TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestRunIssueStatus.OPEN);
                }
                updateSeverityField = Updates.set(TestingRunIssues.KEY_SEVERITY,
                        TestCategory.valueOf(runResult.getTestSuperType()).getSeverity());
            } else {
                updateStatusFields = Updates.set(TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestRunIssueStatus.FIXED);
                updateSeverityField = Updates.set(TestingRunIssues.KEY_SEVERITY,
                        Severity.LOW);
            }
            Bson updateFields = Updates.combine(
                    updateStatusFields,
                    updateSeverityField,
                    Updates.set(TestingRunIssues.LAST_SEEN,lastSeen)
            );

            writeModelList.add(new UpdateOneModel<>(query, updateFields));
        });

    }

    private TestingIssuesId getIssuesIdFromMap(TestingIssuesId issuesId,
                                               Map<TestingIssuesId, TestingRunResult> testingIssuesIdsMap) {
        final TestingIssuesId[] result = {null};
        testingIssuesIdsMap.keySet().forEach(testingIssuesId -> {
            if (testingIssuesId.equals(issuesId)) {
                result[0] = testingIssuesId;
            }
        });
        return result[0];
    }

    private void insertVulnerableTestsIntoIssuesCollection(List<WriteModel<TestingRunIssues>> writeModelList,
                                                           Map<TestingIssuesId, TestingRunResult> testingIssuesIdsMap,
                                                           List<TestingRunIssues> testingRunIssuesList) {
        int lastSeen = Context.now();
        testingIssuesIdsMap.forEach((testingIssuesId, runResult) -> {
            final boolean[] doesExists = {false};
            testingRunIssuesList.forEach(testingRunIssues -> {
                if (testingRunIssues.getId().equals(testingIssuesId)) {
                    doesExists[0] = true;
                }
            });
            if (!doesExists[0] && runResult.isVulnerable()) {
                writeModelList.add(new InsertOneModel<>(new TestingRunIssues(testingIssuesId,
                        TestCategory.valueOf(runResult.getTestSuperType()).getSeverity(),
                        TestRunIssueStatus.OPEN, lastSeen, lastSeen)));
            }
        });
    }

    public static void handleIssuesCreationFromTestingRunResults (List<TestingRunResult> testingRunResultList) {

        Map<TestingIssuesId, TestingRunResult> testingIssuesIdsMap = TestingUtils.
                listOfIssuesIdsFromTestingRunResults(testingRunResultList,true);

        Bson inQuery = Filters.in(ID,testingIssuesIdsMap.keySet().toArray());
        List<TestingRunIssues> testingRunIssuesList = TestingRunIssuesDao.instance.findAll(inQuery);

        List<WriteModel<TestingRunIssues>> writeModelList = new ArrayList<>();

        TestingIssuesHandler handler = new TestingIssuesHandler();

        handler.writeUpdateQueryIntoWriteModel(writeModelList,testingIssuesIdsMap,testingRunIssuesList);
        handler.insertVulnerableTestsIntoIssuesCollection(writeModelList, testingIssuesIdsMap, testingRunIssuesList);

        try {
            BulkWriteResult result = TestingRunIssuesDao.instance.bulkWrite(writeModelList);
            logger.info("Matched records : {}", result.getMatchedCount());
            logger.info("inserted counts : {}", result.getInsertedCount());
            logger.info("Modified counts : {}", result.getModifiedCount());
        }catch (Exception e) {
            logger.error("Error while inserting issues into db: {}",e.getMessage());
        }
    }
}
