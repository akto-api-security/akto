package com.akto.testing_issues;

import static com.akto.util.Constants.ID;

import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dao.testing.sources.TestSourceConfigsDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.sources.TestSourceConfig;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.testing.TestExecutor;
import com.akto.testing_utils.TestingUtils;
import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.util.enums.GlobalEnums.TestRunIssueStatus;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.BsonDocument;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

public class TestingIssuesHandler {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TestingIssuesHandler.class);

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
            if (status == TestRunIssueStatus.IGNORED) {
                updateStatusFields = new BsonDocument();
            } else if (runResult.isVulnerable()) {
                updateStatusFields = Updates.set(TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestRunIssueStatus.OPEN);
            } else {
                updateStatusFields = Updates.set(TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestRunIssueStatus.FIXED);
            }

            // name = cateogry
            String subCategory = runResult.getTestSubType();

            if (subCategory.startsWith("http")) {//TestSourceConfig case
                TestSourceConfig config = TestSourceConfigsDao.instance.getTestSourceConfig(runResult.getTestSubType());
                updateSeverityField = Updates.set(TestingRunIssues.KEY_SEVERITY, config.getSeverity());
            } else {//TestSubCategory case
                String severity = TestExecutor.getSeverityFromTestingRunResult(runResult).toString();
                updateSeverityField = Updates.set(TestingRunIssues.KEY_SEVERITY,
                    severity); // todo: take value from yaml
            }

            Bson updateFields = Updates.combine(
                    updateStatusFields,
                    updateSeverityField,
                    Updates.set(TestingRunIssues.LAST_SEEN, lastSeen),
                    Updates.set(TestingRunIssues.LATEST_TESTING_RUN_SUMMARY_ID, runResult.getTestRunResultSummaryId())
            );
            loggerMaker.debugAndAddToDb(String.format("Updating the issue with id %s, with update parameters and result_summary_Id :%s ", issuesId
                    ,runResult.getTestRunResultSummaryId()), LogDb.TESTING);

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
        ObjectId summaryId = null;

        Map<String, Integer> countIssuesMap = new HashMap<>();
        countIssuesMap.put(Severity.CRITICAL.toString(), 0);
        countIssuesMap.put(Severity.HIGH.toString(), 0);
        countIssuesMap.put(Severity.MEDIUM.toString(), 0);
        countIssuesMap.put(Severity.LOW.toString(), 0);

        for(TestingIssuesId testingIssuesId : testingIssuesIdsMap.keySet()) {
            TestingRunResult runResult = testingIssuesIdsMap.get(testingIssuesId);
            boolean doesExists = false;
            if (summaryId == null) {
                summaryId = runResult.getTestRunResultSummaryId();
            }

            if(!runResult.isVulnerable()){
                continue; // skip if not vulnerable
            }

            boolean shouldCountIssue = false;
            Severity severity = TestExecutor.getSeverityFromTestingRunResult(runResult);

            for (TestingRunIssues testingRunIssues : testingRunIssuesList) {
                if (testingRunIssues.getId().equals(testingIssuesId)) {
                    doesExists = true;
                    if(testingRunIssues.getTestRunIssueStatus().equals(TestRunIssueStatus.OPEN)) {
                        shouldCountIssue = true;
                    }
                    break;
                }
            }

            if(shouldCountIssue || !doesExists) {
                int count = countIssuesMap.getOrDefault(severity.toString(), 0);
                countIssuesMap.put(severity.toString(), count + 1);
            }

            if (!doesExists) {
                // name = category
                String subCategory = runResult.getTestSubType();
                if (subCategory.startsWith("http")) {
                    TestSourceConfig config = TestSourceConfigsDao.instance.getTestSourceConfig(runResult.getTestSubType());
                    writeModelList.add(new InsertOneModel<>(new TestingRunIssues(testingIssuesId,
                            config.getSeverity(),
                            TestRunIssueStatus.OPEN, lastSeen, lastSeen, runResult.getTestRunResultSummaryId(), null, lastSeen, true)));
                }else {
                    
                    writeModelList.add(new InsertOneModel<>(new TestingRunIssues(testingIssuesId,
                            severity,
                            TestRunIssueStatus.OPEN, lastSeen, lastSeen, runResult.getTestRunResultSummaryId(),null, lastSeen, true))); // todo: take value from yaml
                }
                loggerMaker.debugAndAddToDb(String.format("Inserting the id %s , with summary Id as %s", testingIssuesId, runResult.getTestRunResultSummaryId()), LogDb.TESTING);
            }
        };

        if(summaryId != null){
            TestingRunResultSummariesDao.instance.updateOneNoUpsert(
                Filters.eq("_id", summaryId),
                Updates.combine(
                    Updates.inc("countIssues.CRITICAL", countIssuesMap.get("CRITICAL")),
                    Updates.inc("countIssues.HIGH", countIssuesMap.get("HIGH")),
                    Updates.inc("countIssues.MEDIUM", countIssuesMap.get("MEDIUM")),
                    Updates.inc("countIssues.LOW", countIssuesMap.get("LOW"))
                )
            );
        }

    }

    public void handleIssuesCreationFromTestingRunResults(List<TestingRunResult> testingRunResultList, boolean triggeredByTestEditor) {

        // makes the map of issueId to result {no db call}
        Map<TestingIssuesId, TestingRunResult> testingIssuesIdsMap = TestingUtils.
                listOfIssuesIdsFromTestingRunResults(testingRunResultList, true, triggeredByTestEditor);

        Bson inQuery = Filters.in(ID, testingIssuesIdsMap.keySet().toArray());
        List<TestingRunIssues> testingRunIssuesList = TestingRunIssuesDao.instance.findAll(inQuery);

        List<WriteModel<TestingRunIssues>> writeModelList = new ArrayList<>();
        // this will create only the updates {status and summaryId change} for existing issues only
        writeUpdateQueryIntoWriteModel(writeModelList, testingIssuesIdsMap, testingRunIssuesList);


        insertVulnerableTestsIntoIssuesCollection(writeModelList, testingIssuesIdsMap, testingRunIssuesList);
        try {
            if (writeModelList.size() > 0) {
                TestingRunIssuesDao.instance.bulkWrite(writeModelList, new BulkWriteOptions().ordered(false));
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(String.format("Error while inserting issues into db: %s", e.toString()), LogDb.TESTING);
        }
    }
}
