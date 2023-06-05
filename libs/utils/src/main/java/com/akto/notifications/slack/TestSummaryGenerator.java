package com.akto.notifications.slack;

import com.akto.DaoInit;
import com.akto.calendar.DateUtils;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dao.testing.WorkflowTestResultsDao;
import com.akto.dao.testing.WorkflowTestsDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.*;
import com.akto.util.enums.GlobalEnums;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.*;

import static com.akto.notifications.slack.DailyUpdate.*;

public class TestSummaryGenerator {

    private int accountId;


    public TestSummaryGenerator(int accountId) {
        this.accountId = accountId;
    }

    public String toJson(String dashboardLink) {

        GenerateResult generateResult = generate(Context.now() - 24*60*60);

        BasicDBList sectionsList = new BasicDBList();
        BasicDBObject ret = new BasicDBObject("blocks", sectionsList);

        sectionsList.add(createHeader("API Testing Summary For Today :testing: :"));

        String mainTestingLink = dashboardLink + "/dashboard/testing/active";

        int totalApis = 0;
        for (TestingRunResultSummary summary: generateResult.automatedTestingResultSummary) {
            totalApis += summary.getTotalApis();
        }

        BasicDBObject automatedTestStatsSection= createNumberSection(
                "Automated test runs",
                generateResult.automatedTestingResultSummary.size(),
                mainTestingLink,
                "Total APIs tested",
                totalApis,
                mainTestingLink
        );
        sectionsList.add(automatedTestStatsSection);

        List<LinkWithDescription> linkWithDescriptionList = new ArrayList<>();
        for (TestingRunIssues issue: generateResult.testingRunIssues) {
            TestingIssuesId id = issue.getId();
            String issueName = id.getTestSubCategory() == null ? id.getTestCategoryFromSourceConfig() : "Other";
            ApiInfo.ApiInfoKey apiInfoKey =  id.getApiInfoKey();
            String url = apiInfoKey.getUrl();
            String method = apiInfoKey.getMethod().name();
            linkWithDescriptionList.add(new LinkWithDescription(method + " " + url, dashboardLink+"/dashboard/issues", issueName));
        }

        if (!linkWithDescriptionList.isEmpty()) {
            sectionsList.add(createSimpleBlockText("*Issues found:* \n"));
            sectionsList.addAll(createLinksSection(linkWithDescriptionList));
        } else {
            sectionsList.add(createSimpleBlockText("_No issues found!!_:partying_face:"));
        }

        if (generateResult.workflowTestResults.isEmpty()) {
            return ret.toJson();
        }

        int workflowAPIs = 0;
        for (WorkflowTestResult workflowTestResult: generateResult.workflowTestResults) {
            workflowAPIs += workflowTestResult.getNodeResultMap().size();
        }

        BasicDBObject workflowTestStatsSection= createNumberSection(
                "Workflow test runs",
                generateResult.workflowTestResults.size(),
                mainTestingLink,
                "Total API requests made",
                workflowAPIs,
                mainTestingLink
        );
        sectionsList.add(workflowTestStatsSection);

        List<LinkWithDescription> vulnerableWorkflowTestResults = new ArrayList<>();
        List<LinkWithDescription> errorWorkflowTestResults = new ArrayList<>();
        for (WorkflowTestResult workflowTestResult: generateResult.workflowTestResults) {
            Map<String, WorkflowTestResult.NodeResult> nodeResultMap = workflowTestResult.getNodeResultMap();
            String vulnerable = null;
            String error = null;
            for (String key: nodeResultMap.keySet()) {
                WorkflowTestResult.NodeResult nodeResult = nodeResultMap.get(key);
                if (nodeResult.isVulnerable()) {
                    vulnerable = "Validation failed in: *" + key + "* node";
                }

                if (!nodeResult.getErrors().isEmpty()) {
                    error = "_" + nodeResult.getErrors().get(0) + "_" + " in *" + key + "* node";
                }
            }

            TestingRunResultSummary testingRunResultSummary = generateResult.testingRunResultSummaryMap.get(workflowTestResult.getTestingRunResultSummaryId());
            int duration = testingRunResultSummary.getEndTimestamp() - testingRunResultSummary.getStartTimestamp();
            String lastRunString = DateUtils.prettifyDelta(testingRunResultSummary.getStartTimestamp());

            String commonDescription = "Test started " + lastRunString + " and ran for "  + duration + " seconds. ";
            LinkWithDescription linkWithDescription = new LinkWithDescription(
                    workflowTestResult.getWorkflowTestId()+"",
                    dashboardLink + "/dashboard/testing/" + workflowTestResult.getTestRunId() +"/results" ,
                    null
            );
            if (vulnerable != null) {
                linkWithDescription.description = commonDescription + vulnerable;
                vulnerableWorkflowTestResults.add(linkWithDescription);
            }

            if (error != null) {
                linkWithDescription.description = commonDescription + error;
                errorWorkflowTestResults.add(linkWithDescription);
            }
        }

        if (!vulnerableWorkflowTestResults.isEmpty()) {
            sectionsList.add(createSimpleBlockText("*Vulnerable workflow tests:* \n"));
            sectionsList.addAll(createLinksSection(vulnerableWorkflowTestResults));
        } else {
            sectionsList.add(createSimpleBlockText("_No vulnerable workflow tests_:partying_face:"));
        }

        if (!errorWorkflowTestResults.isEmpty()) {
            sectionsList.add(createSimpleBlockText("*Error workflows tests:* \n"));
            sectionsList.addAll(createLinksSection(errorWorkflowTestResults));
        } else {
            sectionsList.add(createSimpleBlockText("_No error in workflow tests_:partying_face:"));
        }

        return ret.toJson();
    }


    public GenerateResult generate(int startTs) {
        Context.accountId.set(this.accountId);

        Bson testingRunBsonFilter = Filters.gte(TestingRunResultSummary.START_TIMESTAMP, startTs);
        List<TestingRunResultSummary> testingRunResultSummaryList = TestingRunResultSummariesDao.instance.findAll(testingRunBsonFilter);

        List<TestingRunResultSummary> automatedTestingResultSummary = new ArrayList<>();
        List<WorkflowTestResult> workflowTestResults = new ArrayList<>();

        List<ObjectId> workflowTestingResultSummaryIds = new ArrayList<>();

        Map<ObjectId, TestingRun> testingRunMap = new HashMap<>();
        Map<ObjectId, TestingRunResultSummary> testingRunResultSummaryMap = new HashMap<>();
        Set<ObjectId> automatedTestingRunSet = new HashSet<>();
        Set<ObjectId> workflowTestingRunSet = new HashSet<>();

        for (TestingRunResultSummary summary: testingRunResultSummaryList) {
            testingRunMap.put(summary.getTestingRunId(), null);
            testingRunResultSummaryMap.put(summary.getId(), summary);
        }

        List<TestingRun> testingRunList = TestingRunDao.instance.findAll(Filters.in("_id", testingRunMap.keySet()));
        for (TestingRun testingRun: testingRunList) {
            testingRunMap.put(testingRun.getId(), testingRun);
            if (testingRun.getTestIdConfig() == 1) {
                workflowTestingRunSet.add(testingRun.getId());
            } else {
                automatedTestingRunSet.add(testingRun.getId());
            }
        }

        for (TestingRunResultSummary summary: testingRunResultSummaryList) {
            if (automatedTestingRunSet.contains(summary.getTestingRunId())) {
                automatedTestingResultSummary.add(summary);
            } else if (workflowTestingRunSet.contains(summary.getTestingRunId())) {
                workflowTestingResultSummaryIds.add(summary.getId());
            }
        }

        workflowTestResults = WorkflowTestResultsDao.instance.findAll(Filters.in(WorkflowTestResult.TESTING_RUN_RESULT_SUMMARY_ID, workflowTestingResultSummaryIds));
        Set<Integer> workflowIds = new HashSet<>();
        for (WorkflowTestResult workflowTestResult: workflowTestResults) {
            workflowIds.add(workflowTestResult.getWorkflowTestId());
        }
        List<WorkflowTest> workflowTests = WorkflowTestsDao.instance.findAll(Filters.in("_id", workflowIds));
        Map<Integer, WorkflowTest> workflowTestsMap = new HashMap<>();
        for (WorkflowTest workflowTest :workflowTests) {
            workflowTestsMap.put(workflowTest.getId(), workflowTest);
        }

        List<TestingRunIssues> testingRunIssues = TestingRunIssuesDao.instance.findAll(Filters.and(
                Filters.gte(TestingRunIssues.LAST_SEEN, startTs),
                Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, GlobalEnums.TestRunIssueStatus.OPEN),
                Filters.ne("_id.testErrorSource", "TEST_EDITOR")));

        return new GenerateResult(workflowTestResults, automatedTestingResultSummary, testingRunMap,
                testingRunResultSummaryMap, testingRunIssues, workflowTestsMap);
    }



    private static class GenerateResult {
        List<WorkflowTestResult> workflowTestResults;
        List<TestingRunResultSummary> automatedTestingResultSummary;
        Map<ObjectId, TestingRun> testingRunMap;
        Map<ObjectId, TestingRunResultSummary> testingRunResultSummaryMap;
        List<TestingRunIssues> testingRunIssues;
        Map<Integer, WorkflowTest> workflowTestsMap;

        public GenerateResult(List<WorkflowTestResult> workflowTestResults,
                              List<TestingRunResultSummary> automatedTestingResultSummary,
                              Map<ObjectId, TestingRun> testingRunMap,
                              Map<ObjectId, TestingRunResultSummary> testingRunResultSummaryMap,
                              List<TestingRunIssues> testingRunIssues,
                              Map<Integer, WorkflowTest> workflowTestsMap) {
            this.workflowTestResults = workflowTestResults;
            this.automatedTestingResultSummary = automatedTestingResultSummary;
            this.testingRunMap = testingRunMap;
            this.testingRunResultSummaryMap = testingRunResultSummaryMap;
            this.testingRunIssues = testingRunIssues;
            this.workflowTestsMap = workflowTestsMap;
        }
    }

    public int getAccountId() {
        return accountId;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }
}
