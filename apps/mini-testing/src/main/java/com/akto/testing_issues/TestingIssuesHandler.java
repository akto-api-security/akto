package com.akto.testing_issues;

import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.bulk_updates.BulkUpdates;
import com.akto.dto.bulk_updates.UpdatePayload;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.sources.TestSourceConfig;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.testing.TestExecutor;
import com.akto.util.enums.GlobalEnums;
import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.util.enums.GlobalEnums.TestRunIssueStatus;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.akto.util.Constants.ID;

public class TestingIssuesHandler {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TestingIssuesHandler.class);
    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    //Update one Write models
    /*
     * Checks the status of issue from db,
     *
     * */

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
                config = dataActor.findTestSourceConfig(subType);
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


    public static final String SET_OPERATION = "set";

    private void writeUpdateQueryIntoWriteModel(List<Object> writeModelList,
                                                Map<TestingIssuesId, TestingRunResult> testingIssuesIdsMap,
                                                List<TestingRunIssues> testingRunIssuesList,
                                                boolean doNotMarkIssuesAsFixed) {
        int lastSeen = Context.now();

        testingRunIssuesList.forEach(testingRunIssues -> {
            TestingIssuesId issuesId = testingRunIssues.getId();

            TestingRunResult runResult = testingIssuesIdsMap.get(getIssuesIdFromMap(issuesId, testingIssuesIdsMap));
            if (runResult == null) {
                return;
            }
            TestRunIssueStatus status = testingRunIssues.getTestRunIssueStatus();
            ArrayList<String> updates = new ArrayList<>();
            UpdatePayload updatePayload = new UpdatePayload();
            if (runResult.isVulnerable()) {
                if (status == TestRunIssueStatus.IGNORED) {
                    updatePayload = new UpdatePayload(TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestRunIssueStatus.IGNORED.name(), SET_OPERATION);
                    updates.add(updatePayload.toString());
                } else {
                    updatePayload = new UpdatePayload(TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestRunIssueStatus.OPEN.name(), SET_OPERATION);
                    updates.add(updatePayload.toString());
                }
            } else if (!doNotMarkIssuesAsFixed) {
                updatePayload = new UpdatePayload(TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestRunIssueStatus.FIXED.name(), SET_OPERATION);
                updates.add(updatePayload.toString());
            }
            // When flag is true we intentionally avoid touching the status for non-vulnerable results

            String severity = TestExecutor.getSeverityFromTestingRunResult(runResult).toString();
            updatePayload = new UpdatePayload(TestingRunIssues.KEY_SEVERITY, severity, SET_OPERATION);
            updates.add(updatePayload.toString());
            updatePayload = new UpdatePayload(TestingRunIssues.LAST_SEEN, lastSeen, SET_OPERATION);
            updates.add(updatePayload.toString());
            updatePayload = new UpdatePayload(TestingRunIssues.LATEST_TESTING_RUN_SUMMARY_HEX_ID, runResult.getTestRunResultSummaryId().toHexString(), SET_OPERATION);
            updates.add(updatePayload.toString());

            loggerMaker.infoAndAddToDb(String.format("Updating the issue with id %s, with update parameters and result_summary_Id :%s ", issuesId
                    ,runResult.getTestRunResultSummaryId()), LogDb.TESTING);

            Map<String, Object> filterMap = new HashMap<>();
            filterMap.put(ID, issuesId);
            writeModelList.add(new BulkUpdates(filterMap, updates));
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

    private void insertVulnerableTestsIntoIssuesCollection(List<Object> writeModelList,
                                                           Map<TestingIssuesId, TestingRunResult> testingIssuesIdsMap,
                                                           List<TestingRunIssues> testingRunIssuesList) {
        int lastSeen = Context.now();
        Map<String, Integer> countIssuesMap = new HashMap<>();
        countIssuesMap.put(Severity.CRITICAL.toString(), 0);
        countIssuesMap.put(Severity.HIGH.toString(), 0);    
        countIssuesMap.put(Severity.MEDIUM.toString(), 0);
        countIssuesMap.put(Severity.LOW.toString(), 0);

        ObjectId summaryId = null;

        for(TestingIssuesId testingIssuesId : testingIssuesIdsMap.keySet()) {
            TestingRunResult runResult = testingIssuesIdsMap.get(testingIssuesId);
            boolean doesExists = false;
            boolean shouldCountIssue = false;

            if (summaryId == null) {
                summaryId = runResult.getTestRunResultSummaryId();
            }

            for (TestingRunIssues testingRunIssues : testingRunIssuesList) {
                if (testingRunIssues.getId().equals(testingIssuesId)) {
                    doesExists = true;
                    if(testingRunIssues.getTestRunIssueStatus().equals(TestRunIssueStatus.OPEN)) {
                        shouldCountIssue = true;
                    }
                    break;
                }
            }
            if (runResult.isVulnerable()) {
                Severity severity = TestExecutor.getSeverityFromTestingRunResult(runResult);
                Map<String, Object> filterMap = new HashMap<>();
                filterMap.put(ID, testingIssuesId);
                ArrayList<String> updates = new ArrayList<>();
                UpdatePayload updatePayload = new UpdatePayload(TestingRunIssues.KEY_SEVERITY, severity.name(), SET_OPERATION);
                updates.add(updatePayload.toString());
                if(!doesExists || shouldCountIssue){
                    updatePayload = new UpdatePayload(TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestRunIssueStatus.OPEN.name(), SET_OPERATION);
                    int count = countIssuesMap.getOrDefault(severity.name(), 0);
                    countIssuesMap.put(severity.name(), count + 1);      
                }
                updates.add(updatePayload.toString());
                updatePayload = new UpdatePayload(TestingRunIssues.CREATION_TIME, lastSeen, SET_OPERATION);
                updates.add(updatePayload.toString());
                updatePayload = new UpdatePayload(TestingRunIssues.LAST_SEEN, lastSeen, SET_OPERATION);
                updates.add(updatePayload.toString());
                updatePayload = new UpdatePayload(TestingRunIssues.LAST_UPDATED, lastSeen, SET_OPERATION);
                updates.add(updatePayload.toString());
                updatePayload = new UpdatePayload(TestingRunIssues.UNREAD, true, SET_OPERATION);
                updates.add(updatePayload.toString());
                updatePayload = new UpdatePayload(TestingRunIssues.LATEST_TESTING_RUN_SUMMARY_HEX_ID, runResult.getTestRunResultSummaryId().toHexString(), SET_OPERATION);
                updates.add(updatePayload.toString());
                if (testingIssuesId != null && testingIssuesId.getApiInfoKey() != null) {
                    updatePayload = new UpdatePayload(TestingRunIssues.COLLECTION_IDS,
                            Arrays.asList(testingIssuesId.getApiInfoKey().getApiCollectionId()), SET_OPERATION);
                    updates.add(updatePayload.toString());
                }
                writeModelList.add(new BulkUpdates(filterMap, updates));
                loggerMaker.infoAndAddToDb(String.format("Inserting the id %s , with summary Id as %s", testingIssuesId, runResult.getTestRunResultSummaryId()), LogDb.TESTING);
            }
        }

        if(summaryId != null){
            // update testing run result summary here
            dataActor.updateIssueCountInSummary(summaryId.toHexString(), countIssuesMap, "increment");
            loggerMaker.infoAndAddToDb(String.format("Increasing the issues count map with summary id %s , HIGH: %d, MEDIUM: %d, LOW: %d", summaryId.toHexString(),countIssuesMap.get("HIGH"), countIssuesMap.get("MEDIUM"), countIssuesMap.get("LOW")), LogDb.TESTING);
        }
    }

    public void handleIssuesCreationFromTestingRunResults(List<TestingRunResult> testingRunResultList, boolean triggeredByTestEditor) {

        Map<TestingIssuesId, TestingRunResult> testingIssuesIdsMap = listOfIssuesIdsFromTestingRunResults(testingRunResultList, true, triggeredByTestEditor);
        
        List<TestingRunIssues> testingRunIssuesList = dataActor.fetchIssuesByIds(testingIssuesIdsMap.keySet());

        loggerMaker.infoAndAddToDb(String.format("Total list of issues from db : %s", testingRunIssuesList.size()), LogDb.TESTING);
        List<Object> writeModelList = new ArrayList<>();
        boolean doNotMarkIssuesAsFixedFlag = false;
        if (testingRunResultList != null && !testingRunResultList.isEmpty()) {
            TestingRunResult firstResult = testingRunResultList.get(0);
            if (firstResult != null && firstResult.getTestRunId() != null) {
                TestingRun testingRun = dataActor.findTestingRun(firstResult.getTestRunId().toHexString());
                if (testingRun != null) {
                    doNotMarkIssuesAsFixedFlag = testingRun.getDoNotMarkIssuesAsFixed();
                }
            }
        }

        writeUpdateQueryIntoWriteModel(writeModelList, testingIssuesIdsMap, testingRunIssuesList, doNotMarkIssuesAsFixedFlag);
        loggerMaker.infoAndAddToDb(String.format("Total write queries after the update iterations: %s", writeModelList.size()), LogDb.TESTING);
        insertVulnerableTestsIntoIssuesCollection(writeModelList, testingIssuesIdsMap, testingRunIssuesList);
        loggerMaker.infoAndAddToDb(String.format("Total write queries after the insertion iterations: %s", writeModelList.size()), LogDb.TESTING);
        try {
            if (writeModelList.size() > 0) {
                dataActor.bulkWriteTestingRunIssues(writeModelList);
            } else {
                loggerMaker.infoAndAddToDb("writeModelList is empty", LogDb.TESTING);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(String.format("Error while inserting issues into db: %s", e.toString()), LogDb.TESTING);
        }
    }
}
