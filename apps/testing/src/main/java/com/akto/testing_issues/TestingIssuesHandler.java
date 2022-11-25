package com.akto.testing_issues;

import com.akto.dao.context.Context;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.TestingRunResult;
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

    private static final Logger logger = LoggerFactory.getLogger(TestingIssuesHandler.class);
    //Update one Write models
    /*
     * Checks the status of issue from db,
     *
     * */

    private void writeUpdateQueryIntoWriteModel(List<WriteModel<TestingRunIssues>> writeModelList,
                                                Map<TestingIssuesId, TestingRunResult> testingIssuesIdsMap,
                                                List<TestingRunIssues> testingRunIssuesList) {
        int lastSeen = Context.now();

        testingRunIssuesList.forEach(testingRunIssues -> {
            TestingIssuesId issuesId = testingRunIssues.getId();

            TestingRunResult runResult = testingIssuesIdsMap.get(getIssuesIdFromMap(issuesId, testingIssuesIdsMap));
            if (runResult == null) {
                return;
            }
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
                        TestSubCategory.valueOf(runResult.getTestSubType()).getSuperCategory().getSeverity());
            } else {
                updateStatusFields = Updates.set(TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestRunIssueStatus.FIXED);
                updateSeverityField = Updates.set(TestingRunIssues.KEY_SEVERITY,
                        Severity.LOW);
            }
            Bson updateFields = Updates.combine(
                    updateStatusFields,
                    updateSeverityField,
                    Updates.set(TestingRunIssues.LAST_SEEN, lastSeen),
                    Updates.set(TestingRunIssues.LATEST_TESTING_RUN_SUMMARY_ID, runResult.getTestRunResultSummaryId())
            );
            logger.info("Updating the issue with id {}, with update parameters and result_summary_Id :{} ", issuesId
                    ,runResult.getTestRunResultSummaryId());

            writeModelList.add(new UpdateOneModel<>(query, updateFields));
        });

    }

    private TestingIssuesId getIssuesIdFromMap(TestingIssuesId issuesId,
                                               Map<TestingIssuesId, TestingRunResult> testingIssuesIdsMap) {
        for (TestingIssuesId testingIssuesId : testingIssuesIdsMap.keySet()) {
            if (testingIssuesId.equals(issuesId)) {
                return testingIssuesId;
            }
        }
        return new TestingIssuesId();
    }

    private void insertVulnerableTestsIntoIssuesCollection(List<WriteModel<TestingRunIssues>> writeModelList,
                                                           Map<TestingIssuesId, TestingRunResult> testingIssuesIdsMap,
                                                           List<TestingRunIssues> testingRunIssuesList) {
        int lastSeen = Context.now();
        testingIssuesIdsMap.forEach((testingIssuesId, runResult) -> {
            boolean doesExists = false;
            for (TestingRunIssues testingRunIssues : testingRunIssuesList) {
                if (testingRunIssues.getId().equals(testingIssuesId)) {
                    doesExists = true;
                    break;
                }
            }
            if (!doesExists && runResult.isVulnerable()) {
                writeModelList.add(new InsertOneModel<>(new TestingRunIssues(testingIssuesId,
                        TestSubCategory.getTestCategory(runResult.getTestSubType()).getSuperCategory().getSeverity(),
                        TestRunIssueStatus.OPEN, lastSeen, lastSeen, runResult.getTestRunResultSummaryId())));
                logger.info("Inserting the id {} , with summary Id as {}", testingIssuesId, runResult.getTestRunResultSummaryId());
            }
        });
    }

    public void handleIssuesCreationFromTestingRunResults(List<TestingRunResult> testingRunResultList) {

        Map<TestingIssuesId, TestingRunResult> testingIssuesIdsMap = TestingUtils.
                listOfIssuesIdsFromTestingRunResults(testingRunResultList, true);

        logger.info("Total issue id created from TestingRunResult map : {}", testingIssuesIdsMap.size());
        Bson inQuery = Filters.in(ID, testingIssuesIdsMap.keySet().toArray());
        List<TestingRunIssues> testingRunIssuesList = TestingRunIssuesDao.instance.findAll(inQuery);

        logger.info("Total list of issues from db : {}", testingRunIssuesList.size());
        List<WriteModel<TestingRunIssues>> writeModelList = new ArrayList<>();
        writeUpdateQueryIntoWriteModel(writeModelList, testingIssuesIdsMap, testingRunIssuesList);
        logger.info("Total write queries after the update iterations: {}", writeModelList.size());
        insertVulnerableTestsIntoIssuesCollection(writeModelList, testingIssuesIdsMap, testingRunIssuesList);
        logger.info("Total write queries after the insertion iterations: {}", writeModelList.size());
        try {
            BulkWriteResult result = TestingRunIssuesDao.instance.bulkWrite(writeModelList, new BulkWriteOptions().ordered(false));
            logger.info("Matched records : {}", result.getMatchedCount());
            logger.info("inserted counts : {}", result.getInsertedCount());
            logger.info("Modified counts : {}", result.getModifiedCount());
        } catch (Exception e) {
            logger.error("Error while inserting issues into db: {}", e.getMessage());
        }
    }
}
