package com.akto.action.testing_issues;

import com.akto.action.UserAction;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.sources.TestSourceConfigsDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.sources.TestSourceConfig;
import com.akto.util.enums.GlobalEnums;
import com.akto.util.enums.GlobalEnums.TestSubCategory;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bouncycastle.util.test.Test;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;

import static com.akto.util.Constants.ID;
import static com.akto.util.enums.GlobalEnums.*;

public class IssuesAction extends UserAction {
    private List<TestingRunIssues> issues;
    private TestingIssuesId issueId;
    private List<TestingIssuesId> issueIdArray;
    private TestingRunResult testingRunResult;
    private TestRunIssueStatus statusToBeUpdated;
    private String ignoreReason;
    private int skip;
    private int limit;
    private long totalIssuesCount;
    private List<TestRunIssueStatus> filterStatus;
    private List<Integer> filterCollectionsId;
    private List<Severity> filterSeverity;
    private List<TestSubCategory> filterSubCategory;
    private List<TestingRunIssues> similarlyAffectedIssues;
    private int startEpoch;
    private Bson createFilters () {
        Bson filters = Filters.empty();
        if (filterStatus != null && !filterStatus.isEmpty()) {
            filters = Filters.and(filters, Filters.in(TestingRunIssues.TEST_RUN_ISSUES_STATUS, filterStatus));
        }
        if (filterCollectionsId != null && !filterCollectionsId.isEmpty()) {
            filters = Filters.and(filters, Filters.in(ID + "."
                    + TestingIssuesId.API_KEY_INFO + "."
                    + ApiInfo.ApiInfoKey.API_COLLECTION_ID, filterCollectionsId));
        }
        if (filterSeverity != null && !filterSeverity.isEmpty()) {
            filters = Filters.and(filters, Filters.in(TestingRunIssues.KEY_SEVERITY, filterSeverity));
        }
        if (filterSubCategory != null && !filterSubCategory.isEmpty()) {
            filters = Filters.and(filters, Filters.in(ID + "."
                    + TestingIssuesId.TEST_SUB_CATEGORY, filterSubCategory));
        }
        if (startEpoch != 0) {
            filters = Filters.and(filters, Filters.gte(TestingRunIssues.CREATION_TIME, startEpoch));
        }
        return filters;
    }

    public String fetchAffectedEndpoints() {
        Bson sort = Sorts.orderBy(Sorts.descending(TestingRunIssues.TEST_RUN_ISSUES_STATUS),
                Sorts.descending(TestingRunIssues.CREATION_TIME));
        TestSubCategory subCategory = issueId.getTestSubCategory();
        TestCategory superCategory;
        if (subCategory.equals(GlobalEnums.TestSubCategory.CUSTOM_IAM)) {
            superCategory = TestSourceConfigsDao.instance.getTestSourceConfig(issueId.getTestCategoryFromSourceConfig()).getCategory();
        } else {
            superCategory = issueId.getTestSubCategory().getSuperCategory();
        }

        List<TestSubCategory> subCategoryList = new ArrayList<>();
        for (TestSubCategory sctg : TestSubCategory.getValuesArray()) {
            if (sctg.getSuperCategory() == superCategory) {
                subCategoryList.add(sctg);
            }
        }
        List<TestSourceConfig> sourceConfigs = TestSourceConfigsDao.instance.findAll(Filters.empty());
        List<String> sourceConfigIds = new ArrayList<>();
        for (TestSourceConfig sourceConfig : sourceConfigs) {
            sourceConfigIds.add(sourceConfig.getId());
        }
        Bson filters = Filters.and(
                Filters.or(
                        Filters.in(ID + "." + TestingIssuesId.TEST_SUB_CATEGORY, subCategoryList),
                        Filters.in(ID + "." + TestingIssuesId.TEST_CATEGORY_FROM_SOURCE_CONFIG, sourceConfigIds)
                ), Filters.ne(ID, issueId));
        similarlyAffectedIssues = TestingRunIssuesDao.instance.findAll(filters, 0,3, sort);
        return SUCCESS.toUpperCase();
    }

    public String fetchAllIssues() {
        Bson sort = Sorts.orderBy(Sorts.descending(TestingRunIssues.TEST_RUN_ISSUES_STATUS),
                Sorts.descending(TestingRunIssues.CREATION_TIME));
        Bson filters = createFilters();
        totalIssuesCount = TestingRunIssuesDao.instance.getMCollection().countDocuments(filters);
        issues = TestingRunIssuesDao.instance.findAll(filters, skip,limit, sort);

        for (TestingRunIssues runIssue : issues) {
            if (runIssue.getId().getTestSubCategory().equals(GlobalEnums.TestSubCategory.CUSTOM_IAM)) {//TestSourceConfig case
                TestSourceConfig config = TestSourceConfigsDao.instance.getTestSourceConfig(runIssue.getId().getTestCategoryFromSourceConfig());
                runIssue.getId().setTestSourceConfig(config);
            }
        }
        return SUCCESS.toUpperCase();
    }
    public String fetchTestingRunResult() {
        if (issueId == null) {
            throw new IllegalStateException();
        }
        TestingRunIssues issue = TestingRunIssuesDao.instance.findOne(Filters.eq(ID, issueId));
        String testSubType = null;
        TestSubCategory subCategory = issue.getId().getTestSubCategory();
        if (subCategory.equals(GlobalEnums.TestSubCategory.CUSTOM_IAM)) {
            testSubType = issue.getId().getTestCategoryFromSourceConfig();
        } else {
            testSubType = issue.getId().getTestSubCategory().getName();
        }
        Bson filterForRunResult = Filters.and(
                Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, issue.getLatestTestingRunSummaryId()),
                Filters.eq(TestingRunResult.TEST_SUB_TYPE, testSubType),
                Filters.eq(TestingRunResult.API_INFO_KEY, issue.getId().getApiInfoKey())
        );
        testingRunResult = TestingRunResultDao.instance.findOne(filterForRunResult);
        return SUCCESS.toUpperCase();
    }

    private TestSubCategory[] subCategories;
    private TestCategory[] categories;
    private List<TestSourceConfig> testSourceConfigs;
    public String fetchAllSubCategories() {
        this.subCategories = GlobalEnums.TestSubCategory.getValuesArray();
        this.categories = GlobalEnums.TestCategory.values();
        this.testSourceConfigs = TestSourceConfigsDao.instance.findAll(Filters.empty());
        return SUCCESS.toUpperCase();
    }


    public String updateIssueStatus () {
        if (issueId == null || statusToBeUpdated == null || ignoreReason == null) {
            throw new IllegalStateException();
        }

        System.out.println("Issue id from db to be updated " + issueId);
        System.out.println("status id from db to be updated " + statusToBeUpdated);
        System.out.println("status reason from db to be updated " + ignoreReason);
        Bson update = Updates.set(TestingRunIssues.TEST_RUN_ISSUES_STATUS, statusToBeUpdated);

        if (statusToBeUpdated == TestRunIssueStatus.IGNORED) { //Changing status to ignored
            update = Updates.combine(update, Updates.set(TestingRunIssues.IGNORE_REASON, ignoreReason));
        } else {
            update = Updates.combine(update, Updates.unset(TestingRunIssues.IGNORE_REASON));
        }
        TestingRunIssues updatedIssue = TestingRunIssuesDao.instance.updateOne(Filters.eq(ID, issueId), update);
        issueId = updatedIssue.getId();
        ignoreReason = updatedIssue.getIgnoreReason();
        statusToBeUpdated = updatedIssue.getTestRunIssueStatus();
        return SUCCESS.toUpperCase();
    }

    public String bulkUpdateIssueStatus () {
        if (issueIdArray == null || statusToBeUpdated == null || ignoreReason == null) {
            throw new IllegalStateException();
        }

        System.out.println("Issue id from db to be updated " + issueIdArray);
        System.out.println("status id from db to be updated " + statusToBeUpdated);
        System.out.println("status reason from db to be updated " + ignoreReason);
        Bson update = Updates.set(TestingRunIssues.TEST_RUN_ISSUES_STATUS, statusToBeUpdated);

        if (statusToBeUpdated == TestRunIssueStatus.IGNORED) { //Changing status to ignored
            update = Updates.combine(update, Updates.set(TestingRunIssues.IGNORE_REASON, ignoreReason));
        } else {
            update = Updates.combine(update, Updates.unset(TestingRunIssues.IGNORE_REASON));
        }
        TestingRunIssuesDao.instance.updateMany(Filters.in(ID, issueIdArray), update);
        return SUCCESS.toUpperCase();
    }
    public List<TestingRunIssues> getIssues() {
        return issues;
    }

    public void setIssues(List<TestingRunIssues> issues) {
        this.issues = issues;
    }

    public TestingIssuesId getIssueId() {
        return issueId;
    }

    public void setIssueId(TestingIssuesId issueId) {
        this.issueId = issueId;
    }

    public TestRunIssueStatus getStatusToBeUpdated() {
        return statusToBeUpdated;
    }

    public void setStatusToBeUpdated(TestRunIssueStatus statusToBeUpdated) {
        this.statusToBeUpdated = statusToBeUpdated;
    }

    public String getIgnoreReason() {
        return ignoreReason;
    }

    public void setIgnoreReason(String ignoreReason) {
        this.ignoreReason = ignoreReason;
    }

    public int getSkip() {
        return skip;
    }

    public void setSkip(int skip) {
        this.skip = skip;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public long getTotalIssuesCount() {
        return totalIssuesCount;
    }

    public void setTotalIssuesCount(long totalIssuesCount) {
        this.totalIssuesCount = totalIssuesCount;
    }

    public List<TestRunIssueStatus> getFilterStatus() {
        return filterStatus;
    }

    public void setFilterStatus(List<TestRunIssueStatus> filterStatus) {
        this.filterStatus = filterStatus;
    }

    public List<Integer> getFilterCollectionsId() {
        return filterCollectionsId;
    }

    public void setFilterCollectionsId(List<Integer> filterCollectionsId) {
        this.filterCollectionsId = filterCollectionsId;
    }

    public List<Severity> getFilterSeverity() {
        return filterSeverity;
    }

    public void setFilterSeverity(List<Severity> filterSeverity) {
        this.filterSeverity = filterSeverity;
    }

    public List<TestSubCategory> getFilterSubCategory() {
        return filterSubCategory;
    }

    public void setFilterSubCategory(List<TestSubCategory> filterSubCategory) {
        this.filterSubCategory = filterSubCategory;
    }

    public int getStartEpoch() {
        return startEpoch;
    }

    public void setStartEpoch(int startEpoch) {
        this.startEpoch = startEpoch;
    }

    public List<TestingIssuesId> getIssueIdArray() {
        return issueIdArray;
    }

    public void setIssueIdArray(List<TestingIssuesId> issueIdArray) {
        this.issueIdArray = issueIdArray;
    }

    public TestingRunResult getTestingRunResult() {
        return testingRunResult;
    }

    public void setTestingRunResult(TestingRunResult testingRunResult) {
        this.testingRunResult = testingRunResult;
    }

    public TestSubCategory[] getSubCategories() {
        return this.subCategories;
    }

    public List<TestingRunIssues> getSimilarlyAffectedIssues() {
        return similarlyAffectedIssues;
    }

    public void setSimilarlyAffectedIssues(List<TestingRunIssues> similarlyAffectedIssues) {
        this.similarlyAffectedIssues = similarlyAffectedIssues;
    }

    public TestCategory[] getCategories() {
        return this.categories;
    }

    public void setCategories(TestCategory[] categories) {
        this.categories = categories;
    }

    public List<TestSourceConfig> getTestSourceConfigs() {
        return testSourceConfigs;
    }

    public void setTestSourceConfigs(List<TestSourceConfig> testSourceConfigs) {
        this.testSourceConfigs = testSourceConfigs;
    }
}
