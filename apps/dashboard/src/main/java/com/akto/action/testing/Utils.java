package com.akto.action.testing;

import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;

import com.akto.dao.AccountSettingsDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.testing.GenericTestResult;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.TestingRunResultSummary;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;

public class Utils {

    public static Bson createFiltersForTestingReport(Map<String, List<String>> filterMap){
        List<Bson> filterList = new ArrayList<>();
        for(Map.Entry<String, List<String>> entry: filterMap.entrySet()) {
            String key = entry.getKey();
            List<String> value = entry.getValue();

            if (value.isEmpty()) continue;
            List<Integer> collectionIds = new ArrayList<>();
            if(key.equals(SingleTypeInfo._API_COLLECTION_ID)){
                for(String str: value){
                    collectionIds.add(Integer.parseInt(str));
                }
            }

            switch (key) {
                case SingleTypeInfo._METHOD:
                    filterList.add(Filters.in(TestingRunResult.API_INFO_KEY + "." + ApiInfoKey.METHOD, value));
                    break;
                case SingleTypeInfo._COLLECTION_IDS:
                case SingleTypeInfo._API_COLLECTION_ID:
                    filterList.add(Filters.in(TestingRunResult.API_INFO_KEY + "." + ApiInfoKey.API_COLLECTION_ID, collectionIds));
                    break;
                case "categoryFilter":
                    filterList.add(Filters.in(TestingRunResult.TEST_SUPER_TYPE, value));
                    break;
                case "severityStatus":
                    filterList.add(Filters.in(TestingRunResult.TEST_RESULTS + ".0." + GenericTestResult._CONFIDENCE, value));
                    break;
                case "testFilter":
                    filterList.add(Filters.in(TestingRunResult.TEST_SUB_TYPE, value));
                    break;
                case "apiNameFilter":
                    filterList.add(Filters.in(TestingRunResult.API_INFO_KEY + "." + ApiInfoKey.URL, value));
                default:
                    break;
            }
        }
        if(filterList.isEmpty()){
            return Filters.empty();
        }
        return Filters.and(filterList);
    }

    public static Map<String, String> mapIssueDescriptions(List<TestingRunIssues> issues,
        Map<TestingIssuesId, TestingRunResult> idToResultMap) {

        if (CollectionUtils.isEmpty(issues) || MapUtils.isEmpty(idToResultMap)) {
            return Collections.emptyMap();
        }

        Map<String, String> finalResult = new HashMap<>();
        for (TestingRunIssues issue : issues) {
            if (StringUtils.isNotBlank(issue.getDescription())) {
                TestingRunResult result = idToResultMap.get(issue.getId());
                if (result != null) {
                    finalResult.put(result.getHexId(), issue.getDescription());
                }
            }
        }
        return finalResult;
    }

    public static BasicDBObject buildIssueMetaDataMap(List<TestingRunIssues> issues, Map<TestingIssuesId, TestingRunResult> idToResultMap){
        BasicDBObject issueMetaDataMap = new BasicDBObject();
        Map<String, String> descriptionMap = new HashMap<>();
        Map<String, String> jiraIssueMap = new HashMap<>();
        Map<String, String> statusMap = new HashMap<>();
        for (TestingRunIssues issue : issues) {
            TestingRunResult result = idToResultMap.get(issue.getId());
            if (StringUtils.isNotBlank(issue.getDescription())) {
                descriptionMap.put(result.getHexId(), issue.getDescription());
            }
            if (StringUtils.isNotBlank(issue.getJiraIssueUrl())) {
                jiraIssueMap.put(result.getHexId(), issue.getJiraIssueUrl());
            }
            if(issue.getTestRunIssueStatus().name().equals("IGNORED")){
                statusMap.put(result.getHexId(), issue.getTestRunIssueStatus().name());
            }
            
        }
        issueMetaDataMap.put("descriptions", descriptionMap);
        issueMetaDataMap.put("jiraIssues", jiraIssueMap);
        issueMetaDataMap.put("statuses", statusMap);
        issueMetaDataMap.put("count", statusMap.size());
        return issueMetaDataMap;
    }

    public static void 
    recalculateTestingIssuesCount() {
        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter(), Projections.include(AccountSettings.LAST_UPDATED_TESTING_ISSUES_COUNT));
        int lastUpdatedTime = accountSettings.getLastUpdatedTestingIssuesCount();
        if (lastUpdatedTime == 0) {
            lastUpdatedTime = Context.now() - Constants.ONE_MONTH_TIMESTAMP;
        }

        // get the testing runs, which have scheduled time stamp greater than lastUpdatedTime and state is completed or scheduled
        List<TestingRun> testingRuns = TestingRunDao.instance.findAll(
            Filters.and(
                Filters.gt(TestingRun.SCHEDULE_TIMESTAMP, lastUpdatedTime),
                Filters.in(TestingRun.STATE, Arrays.asList(TestingRun.State.COMPLETED.toString(), TestingRun.State.SCHEDULED.toString()))
            ),  Projections.include(Constants.ID)
        );

        int accountId = Context.accountId.get();
        StartTestAction action = new StartTestAction();

        for(TestingRun testingRun : testingRuns) {
            List<TestingRunResultSummary> trrsList = TestingRunResultSummariesDao.instance.findAll(
                Filters.and(
                    Filters.eq(TestingRunResultSummary.TESTING_RUN_ID, testingRun.getId()),
                    Filters.gte(TestingRunResultSummary.START_TIMESTAMP, lastUpdatedTime)
                ),
                0,30 , Sorts.descending(TestingRunResultSummary.START_TIMESTAMP), Projections.include(Constants.ID)
            );

            for(TestingRunResultSummary trrs: trrsList){
                action.setTestingRunResultSummaryHexId(trrs.getHexId());
                action.handleRefreshTableCount();
            }
        }
        AccountSettingsDao.instance.updateOne(AccountSettingsDao.generateFilter(), Updates.set(AccountSettings.LAST_UPDATED_TESTING_ISSUES_COUNT, Context.now()));
    }

}
