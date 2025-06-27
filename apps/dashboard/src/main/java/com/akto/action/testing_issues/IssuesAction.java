package com.akto.action.testing_issues;

import com.akto.action.ExportSampleDataAction;
import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dao.demo.VulnerableRequestForTemplateDao;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.sources.TestSourceConfigsDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.demo.VulnerableRequestForTemplate;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.GenericTestResult;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.sources.TestSourceConfig;
import com.akto.dao.common.LoggerMaker;
import com.akto.dao.common.LoggerMaker.LogDb;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.util.enums.GlobalEnums;
import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.util.enums.GlobalEnums.TestCategory;
import com.akto.util.enums.GlobalEnums.TestRunIssueStatus;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.akto.util.Constants.ID;

public class IssuesAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(IssuesAction.class);
    private static final Logger logger = LoggerFactory.getLogger(IssuesAction.class);
    private List<TestingRunIssues> issues;
    private TestingIssuesId issueId;
    private List<TestingIssuesId> issueIdArray;
    private TestingRunResult testingRunResult;
    private List<TestingRunResult> testingRunResults;
    private Map<String, String> sampleDataVsCurlMap;
    private TestRunIssueStatus statusToBeUpdated;
    private String ignoreReason;
    private int skip;
    private int limit;
    private long totalIssuesCount;
    private List<TestRunIssueStatus> filterStatus;
    private List<Integer> filterCollectionsId;
    private List<Severity> filterSeverity;
    private List<String> filterSubCategory;
    private List<TestingRunIssues> similarlyAffectedIssues;
    private int startEpoch;
    private Bson createFilters () {
        Bson filters = Filters.empty();
        if (filterStatus != null && !filterStatus.isEmpty()) {
            filters = Filters.and(filters, Filters.in(TestingRunIssues.TEST_RUN_ISSUES_STATUS, filterStatus));
        }
        if (filterCollectionsId != null && !filterCollectionsId.isEmpty()) {
            filters = Filters.and(filters, Filters.in(SingleTypeInfo._COLLECTION_IDS, filterCollectionsId));
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

        Bson combinedFilters = Filters.and(filters, Filters.ne("_id.testErrorSource", "TEST_EDITOR"));
        
        return combinedFilters;
    }

    public String fetchAffectedEndpoints() {
        Bson sort = Sorts.orderBy(Sorts.descending(TestingRunIssues.TEST_RUN_ISSUES_STATUS),
                Sorts.descending(TestingRunIssues.CREATION_TIME));
        
        String subCategory = issueId.getTestSubCategory();
        List<TestSourceConfig> sourceConfigs = TestSourceConfigsDao.instance.findAll(Filters.empty());
        List<String> sourceConfigIds = new ArrayList<>();
        for (TestSourceConfig sourceConfig : sourceConfigs) {
            sourceConfigIds.add(sourceConfig.getId());
        }
        Bson filters = Filters.and(
                Filters.or(
                        Filters.eq(ID + "." + TestingIssuesId.TEST_SUB_CATEGORY, subCategory),
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
            if (runIssue.getId().getTestSubCategory().startsWith("http")) {//TestSourceConfig case
                TestSourceConfig config = TestSourceConfigsDao.instance.getTestSourceConfig(runIssue.getId().getTestCategoryFromSourceConfig());
                runIssue.getId().setTestSourceConfig(config);
            }
        }
        return SUCCESS.toUpperCase();
    }

    public String fetchVulnerableTestingRunResultsFromIssues() {
        Bson filters = createFilters();
        try {
            List<TestingRunIssues> issues =  TestingRunIssuesDao.instance.findAll(filters, skip, 50, null);
            this.totalIssuesCount = issues.size();
            List<Bson> andFilters = new ArrayList<>();
            for (TestingRunIssues issue : issues) {
                andFilters.add(Filters.and(
                        Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, issue.getLatestTestingRunSummaryId()),
                        Filters.eq(TestingRunResult.TEST_SUB_TYPE, issue.getId().getTestSubCategory()),
                        Filters.eq(TestingRunResult.API_INFO_KEY, issue.getId().getApiInfoKey()),
                        Filters.eq(TestingRunResult.VULNERABLE, true)
                ));
            }
            if (issues.isEmpty()) {
                this.testingRunResults = new ArrayList<>();
                this.sampleDataVsCurlMap = new HashMap<>();
                return SUCCESS.toUpperCase();
            }
            Bson orFilters = Filters.or(andFilters);
            this.testingRunResults = TestingRunResultDao.instance.findAll(orFilters);
            Map<String, String> sampleDataVsCurlMap = new HashMap<>();
            // todo: fix
            for (TestingRunResult runResult: this.testingRunResults) {
                List<GenericTestResult> testResults = new ArrayList<>();
                for (GenericTestResult tr : runResult.getTestResults()) {
                    TestResult testResult = (TestResult) tr;
                    if (testResult.isVulnerable()) {
                        testResults.add(testResult);
                        sampleDataVsCurlMap.put(testResult.getMessage(), ExportSampleDataAction.getCurl(testResult.getMessage()));
                        sampleDataVsCurlMap.put(testResult.getOriginalMessage(), ExportSampleDataAction.getCurl(testResult.getOriginalMessage()));
                    }
                }
                runResult.setTestResults(testResults);
            }
            this.sampleDataVsCurlMap = sampleDataVsCurlMap;
        } catch (Exception e) {
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }
    public String fetchTestingRunResult() {
        if (issueId == null) {
            throw new IllegalStateException();
        }
        TestingRunIssues issue = TestingRunIssuesDao.instance.findOne(Filters.eq(ID, issueId));
        String testSubType = null;
        // ?? enum stored in db
        String subCategory = issue.getId().getTestSubCategory();
        if (subCategory.startsWith("http")) {
            testSubType = issue.getId().getTestCategoryFromSourceConfig();
        } else {
            testSubType = issue.getId().getTestSubCategory();
        }
        Bson filterForRunResult = Filters.and(
                Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, issue.getLatestTestingRunSummaryId()),
                Filters.eq(TestingRunResult.TEST_SUB_TYPE, testSubType),
                Filters.eq(TestingRunResult.API_INFO_KEY, issue.getId().getApiInfoKey())
        );
        testingRunResult = TestingRunResultDao.instance.findOne(filterForRunResult);
        if (issue.isUnread()) {
            logger.info("Issue id from db to be marked as read " + issueId);
            Bson update = Updates.combine(Updates.set(TestingRunIssues.UNREAD, false),
                    Updates.set(TestingRunIssues.LAST_UPDATED, Context.now()));
            TestingRunIssues updatedIssue = TestingRunIssuesDao.instance.updateOneNoUpsert(Filters.eq(ID, issueId), update);
            issueId = updatedIssue.getId();
        }
        return SUCCESS.toUpperCase();
    }

    private ArrayList<BasicDBObject> subCategories;
    private List<VulnerableRequestForTemplate> vulnerableRequests;
    private TestCategory[] categories;
    private List<TestSourceConfig> testSourceConfigs;

    public static BasicDBObject createSubcategoriesInfoObj(TestConfig testConfig) {
        Info info = testConfig.getInfo();
        if (info.getName().equals("FUZZING")) {
            return null;
        }
        BasicDBObject infoObj = new BasicDBObject();
        BasicDBObject superCategory = new BasicDBObject();
        BasicDBObject severity = new BasicDBObject();
        infoObj.put("issueDescription", info.getDescription());
        infoObj.put("issueDetails", info.getDetails());
        infoObj.put("issueImpact", info.getImpact());
        infoObj.put("issueTags", info.getTags());
        infoObj.put("testName", info.getName());
        infoObj.put("references", info.getReferences());
        infoObj.put("cwe", info.getCwe());
        infoObj.put("cve", info.getCve());
        infoObj.put("name", testConfig.getId());
        infoObj.put("_name", testConfig.getId());
        infoObj.put("content", testConfig.getContent());
        infoObj.put("templateSource", testConfig.getTemplateSource());
        infoObj.put("updatedTs", testConfig.getUpdateTs());
        infoObj.put("author", testConfig.getAuthor());

        superCategory.put("displayName", info.getCategory().getDisplayName());
        superCategory.put("name", info.getCategory().getName());
        superCategory.put("shortName", info.getCategory().getShortName());

        severity.put("_name",info.getSeverity());
        superCategory.put("severity", severity);
        infoObj.put("superCategory", superCategory);
        infoObj.put(YamlTemplate.INACTIVE, testConfig.getInactive());
        return infoObj;
    }

    private boolean fetchOnlyActive;
    private String mode;

    public String fetchAllSubCategories() {

        boolean includeYamlContent = false;

        switch (mode) {
            case "runTests":
                categories = GlobalEnums.TestCategory.values();
                break;
            case "testEditor":
                includeYamlContent = true;
                vulnerableRequests = VulnerableRequestForTemplateDao.instance.findAll(Filters.empty());
                break;
            default:
                includeYamlContent = true;
                categories = GlobalEnums.TestCategory.values();
                vulnerableRequests = VulnerableRequestForTemplateDao.instance.findAll(Filters.empty());
                testSourceConfigs = TestSourceConfigsDao.instance.findAll(Filters.empty());
        }

        Map<String, TestConfig> testConfigMap = YamlTemplateDao.instance.fetchTestConfigMap(includeYamlContent,
                fetchOnlyActive);
        subCategories = new ArrayList<>();
        for (Map.Entry<String, TestConfig> entry : testConfigMap.entrySet()) {
            try {
                BasicDBObject infoObj = createSubcategoriesInfoObj(entry.getValue());
                if (infoObj != null) {
                    subCategories.add(infoObj);
                }
            } catch (Exception e) {
                String err = "Error while fetching subcategories for " + entry.getKey();
                loggerMaker.errorAndAddToDb(e, err, LogDb.DASHBOARD);
            }
        }

        return SUCCESS.toUpperCase();
    }


    public String updateIssueStatus () {
        if (issueId == null || statusToBeUpdated == null || ignoreReason == null) {
            throw new IllegalStateException();
        }

        logger.info("Issue id from db to be updated " + issueId);
        logger.info("status id from db to be updated " + statusToBeUpdated);
        logger.info("status reason from db to be updated " + ignoreReason);
        Bson update = Updates.combine(Updates.set(TestingRunIssues.TEST_RUN_ISSUES_STATUS, statusToBeUpdated),
                                        Updates.set(TestingRunIssues.LAST_UPDATED, Context.now()));

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

        logger.info("Issue id from db to be updated " + issueIdArray);
        logger.info("status id from db to be updated " + statusToBeUpdated);
        logger.info("status reason from db to be updated " + ignoreReason);
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

    public List<String> getFilterSubCategory() {
        return filterSubCategory;
    }

    public void setFilterSubCategory(List<String> filterSubCategory) {
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

    public ArrayList<BasicDBObject> getSubCategories() {
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

    public List<VulnerableRequestForTemplate> getVulnerableRequests() {
        return vulnerableRequests;
    }

    public void setVulnerableRequests(List<VulnerableRequestForTemplate> vulnerableRequests) {
        this.vulnerableRequests = vulnerableRequests;
    }

    public boolean getFetchOnlyActive() {
        return fetchOnlyActive;
    }

    public void setFetchOnlyActive(boolean fetchOnlyActive) {
        this.fetchOnlyActive = fetchOnlyActive;
    }

    public List<TestingRunResult> getTestingRunResults() {
        return testingRunResults;
    }

    public void setTestingRunResults(List<TestingRunResult> testingRunResults) {
        this.testingRunResults = testingRunResults;
    }

    public Map<String, String> getSampleDataVsCurlMap() {
        return sampleDataVsCurlMap;
    }

    public void setSampleDataVsCurlMap(Map<String, String> sampleDataVsCurlMap) {
        this.sampleDataVsCurlMap = sampleDataVsCurlMap;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }
}
