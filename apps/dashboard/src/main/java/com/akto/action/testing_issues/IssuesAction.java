package com.akto.action.testing_issues;

import com.akto.action.ExportSampleDataAction;
import com.akto.action.UserAction;
import com.akto.dao.HistoricalDataDao;
import com.akto.dao.RBACDao;
import com.akto.action.testing.StartTestAction;
import com.akto.dao.context.Context;
import com.akto.dao.demo.VulnerableRequestForTemplateDao;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dao.testing.sources.TestSourceConfigsDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.HistoricalData;
import com.akto.dto.RBAC.Role;
import com.akto.dto.demo.VulnerableRequestForTemplate;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.*;
import com.akto.dto.testing.sources.TestSourceConfig;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.Constants;
import com.akto.util.GroupByTimeRange;
import com.akto.util.enums.GlobalEnums;
import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.util.enums.GlobalEnums.TestCategory;
import com.akto.util.enums.GlobalEnums.TestRunIssueStatus;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.*;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.akto.util.Constants.ID;
import static com.akto.util.Constants.ONE_DAY_TIMESTAMP;

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
    long endTimeStamp;
    private Bson createFilters (boolean useFilterStatus) {
        Bson filters = Filters.empty();
        if (useFilterStatus && filterStatus != null && !filterStatus.isEmpty()) {
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
        if (startEpoch != 0 && endTimeStamp != 0) {
            filters = Filters.and(filters, Filters.gte(TestingRunIssues.CREATION_TIME, startEpoch));
            filters = Filters.and(filters, Filters.lt(TestingRunIssues.CREATION_TIME, endTimeStamp));
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

    long openIssuesCount;
    long fixedIssuesCount;
    long ignoredIssuesCount;
    String sortKey;
    int sortOrder;
    public String fetchAllIssues() {
        Bson filters = createFilters(true);

        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(filters));
        try {
            List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            if(collectionIds != null) {
                pipeline.add(Aggregates.match(Filters.in(SingleTypeInfo._COLLECTION_IDS, collectionIds)));
            }
        } catch(Exception e){
        }
        if (TestingRunIssues.KEY_SEVERITY.equals(sortKey)) {
            Bson addSeverityValueStage = Aggregates.addFields(
                    new Field<>("severityValue", new BasicDBObject("$switch",
                            new BasicDBObject("branches", Arrays.asList(
                                    new BasicDBObject("case", new BasicDBObject("$eq", Arrays.asList("$severity", Severity.CRITICAL.name()))).append("then", 4),
                                    new BasicDBObject("case", new BasicDBObject("$eq", Arrays.asList("$severity", Severity.HIGH.name()))).append("then", 3),
                                    new BasicDBObject("case", new BasicDBObject("$eq", Arrays.asList("$severity", Severity.MEDIUM.name()))).append("then", 2),
                                    new BasicDBObject("case", new BasicDBObject("$eq", Arrays.asList("$severity", Severity.LOW.name()))).append("then", 1)
                            )).append("default", 0)
                    ))
            );
            pipeline.add(addSeverityValueStage);

            Bson sortStage = (sortOrder == 1) ?
                    Aggregates.sort(Sorts.ascending("severityValue", TestingRunIssues.CREATION_TIME)) :
                    Aggregates.sort(Sorts.descending("severityValue", TestingRunIssues.CREATION_TIME));
            pipeline.add(sortStage);

        } else if (TestingRunIssues.CREATION_TIME.equals(sortKey)) {
            Bson sortStage = (sortOrder == 1) ?
                    Aggregates.sort(Sorts.ascending(TestingRunIssues.CREATION_TIME)) :
                    Aggregates.sort(Sorts.descending(TestingRunIssues.CREATION_TIME));
            pipeline.add(sortStage);
        }

        pipeline.add(Aggregates.skip(skip));
        pipeline.add(Aggregates.limit(limit));

        issues = TestingRunIssuesDao.instance.getMCollection()
                .aggregate(pipeline, TestingRunIssues.class)
                .into(new ArrayList<>());

        Bson countingFilters = createFilters(false);
        openIssuesCount = TestingRunIssuesDao.instance.count(Filters.and(countingFilters, Filters.in(TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestRunIssueStatus.OPEN.name())));
        fixedIssuesCount = TestingRunIssuesDao.instance.count(Filters.and(countingFilters, Filters.in(TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestRunIssueStatus.FIXED.name())));
        ignoredIssuesCount = TestingRunIssuesDao.instance.count(Filters.and(countingFilters, Filters.in(TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestRunIssueStatus.IGNORED.name())));

        for (TestingRunIssues runIssue : issues) {
            if (runIssue.getId().getTestSubCategory().startsWith("http")) {
                TestSourceConfig config = TestSourceConfigsDao.instance.getTestSourceConfig(runIssue.getId().getTestCategoryFromSourceConfig());
                runIssue.getId().setTestSourceConfig(config);
            }
        }

        return SUCCESS.toUpperCase();
    }

    List<Integer> totalIssuesCountDayWise;
    List<Integer> openIssuesCountDayWise;
    List<Integer> criticalIssuesCountDayWise;
    public String findTotalIssuesByDay() {
        long daysBetween = (endTimeStamp - startEpoch) / ONE_DAY_TIMESTAMP;
        List<Bson> pipeline = new ArrayList<>();

        Bson notIncludedCollections = UsageMetricCalculator.excludeDemosAndDeactivated("_id." + TestingIssuesId.API_KEY_INFO + "." + ApiInfo.ApiInfoKey.API_COLLECTION_ID);

        Bson filters = Filters.and(
                notIncludedCollections,
                Filters.gte(TestingRunIssues.CREATION_TIME, startEpoch),
                Filters.lte(TestingRunIssues.CREATION_TIME, endTimeStamp)
        );

        Bson totalIssuesMatchStage = Aggregates.match(filters);
        Bson openIssuesMatchStage = Aggregates.match(Filters.and(
                filters,
                Filters.in(TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestRunIssueStatus.OPEN.name())
        ));
        Bson criticalIssuesMatchStage = Aggregates.match(Filters.and(
                filters,
                Filters.in(TestingRunIssues.KEY_SEVERITY, Severity.CRITICAL.name(), Severity.HIGH.name())
        ));

        pipeline.add(totalIssuesMatchStage);
        List<Integer> collectionIds = null;
        try {
            collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            if(collectionIds != null) {
                pipeline.add(Aggregates.match(Filters.in(SingleTypeInfo._COLLECTION_IDS, collectionIds)));
            }
        } catch(Exception e){
        }
        totalIssuesCountDayWise = new ArrayList<>();
        filterIssuesDataByTimeRange(daysBetween, pipeline, totalIssuesCountDayWise);
        pipeline.clear();

        pipeline.add(openIssuesMatchStage);
        if(collectionIds != null) {
            pipeline.add(Aggregates.match(Filters.in(SingleTypeInfo._COLLECTION_IDS, collectionIds)));
        }
        openIssuesCountDayWise = new ArrayList<>();
        filterIssuesDataByTimeRange(daysBetween, pipeline, openIssuesCountDayWise);
        pipeline.clear();

        pipeline.add(criticalIssuesMatchStage);
        if(collectionIds != null) {
            pipeline.add(Aggregates.match(Filters.in(SingleTypeInfo._COLLECTION_IDS, collectionIds)));
        }
        criticalIssuesCountDayWise = new ArrayList<>();
        filterIssuesDataByTimeRange(daysBetween, pipeline, criticalIssuesCountDayWise);
        pipeline.clear();

        return SUCCESS.toUpperCase();
    }

    private void filterIssuesDataByTimeRange(long daysBetween, List<Bson> pipeline, List<Integer> issuesList) {
        GroupByTimeRange.groupByAllRange(daysBetween, pipeline, TestingRunIssues.CREATION_TIME, "totalIssues");
        MongoCursor<BasicDBObject> cursor = TestingRunIssuesDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        while (cursor.hasNext()) {
            BasicDBObject document = cursor.next();
            if(document.isEmpty()) continue;
            issuesList.add(document.getInt("totalIssues"));
        }
        cursor.close();
    }

    List<HistoricalData> historicalData;
    public String fetchTestCoverageData() {
        long daysBetween = (endTimeStamp - startEpoch) / ONE_DAY_TIMESTAMP;

        List<Bson> pipeline = new ArrayList<>();

        Bson notIncludedCollections = UsageMetricCalculator.excludeDemosAndDeactivated(HistoricalData.API_COLLECTION_ID);
        Bson filter = Filters.and(
                notIncludedCollections,
                Filters.gte("time", startEpoch),
                Filters.lte("time", endTimeStamp)
        );
        pipeline.add(Aggregates.match(filter));

        historicalData = new ArrayList<>();

        if(daysBetween > 30 && daysBetween <= 210) {
            addGroupAndProjectStages(pipeline, "week");
        } else if(daysBetween > 210) {
            addGroupAndProjectStages(pipeline, "month");
        }

        MongoCursor<HistoricalData> cursor = HistoricalDataDao.instance.getMCollection().aggregate(pipeline, HistoricalData.class).cursor();
        while(cursor.hasNext()) {
            historicalData.add(cursor.next());
        }
        cursor.close();

        return SUCCESS.toUpperCase();
    }

    private void addGroupAndProjectStages(List<Bson> pipeline, String dateUnit) {
        Bson groupStage = Aggregates.group(
                new Document(dateUnit, new Document("$" + dateUnit, new Document("$toDate", new Document("$multiply", Arrays.asList("$time", 1000))))),
                Accumulators.avg("avgTotalApis", "$totalApis"),
                Accumulators.avg("avgApisTested", "$apisTested")
        );

        Bson projectStage = Aggregates.project(new Document(dateUnit, "$" + dateUnit)
                .append("totalApis", new Document("$round", "$avgTotalApis"))
                .append("apisTested", new Document("$round", "$avgApisTested"))
        );

        pipeline.add(groupStage);
        pipeline.add(projectStage);
    }

    public String fetchVulnerableTestingRunResultsFromIssues() {
        Bson filters = createFilters(true);
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
                WorkflowTest workflowTest = runResult.getWorkflowTest();
                for (GenericTestResult tr : runResult.getTestResults()) {
                    if (tr.isVulnerable()) {
                        if (tr instanceof TestResult) {
                            TestResult testResult = (TestResult) tr;
                            testResults.add(testResult);
                            sampleDataVsCurlMap.put(testResult.getMessage(), ExportSampleDataAction.getCurl(testResult.getMessage()));
                            sampleDataVsCurlMap.put(testResult.getOriginalMessage(), ExportSampleDataAction.getCurl(testResult.getOriginalMessage()));
                        } else if (tr instanceof MultiExecTestResult){
                            MultiExecTestResult testResult = (MultiExecTestResult) tr;
                            Map<String, WorkflowTestResult.NodeResult> nodeResultMap = testResult.getNodeResultMap();
                            for (String order : nodeResultMap.keySet()) {
                                WorkflowTestResult.NodeResult nodeResult = nodeResultMap.get(order);
                                String nodeResultLastMessage = StartTestAction.getNodeResultLastMessage(nodeResult.getMessage());
                                if (nodeResultLastMessage != null) {
                                    nodeResult.setMessage(nodeResultLastMessage);
                                    sampleDataVsCurlMap.put(nodeResultLastMessage,
                                            ExportSampleDataAction.getCurl(nodeResultLastMessage));
                                }
                            }
                        }
                    }
                    if (workflowTest != null) {
                        Map<String, WorkflowNodeDetails> nodeDetailsMap = workflowTest.getMapNodeIdToWorkflowNodeDetails();
                        for (String nodeName: nodeDetailsMap.keySet()) {
                            if (nodeDetailsMap.get(nodeName) instanceof YamlNodeDetails) {
                                YamlNodeDetails details = (YamlNodeDetails) nodeDetailsMap.get(nodeName);
                                sampleDataVsCurlMap.put(details.getOriginalMessage(),
                                        ExportSampleDataAction.getCurl(details.getOriginalMessage()));
                            }

                        }
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

        Role currentUserRole = RBACDao.getCurrentRoleForUser(getSUser().getId(), Context.accountId.get());

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
        if (issue.isUnread() && (currentUserRole.equals(Role.ADMIN) || currentUserRole.equals(Role.MEMBER))) {
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

    public String fetchVulnerableRequests() {
        vulnerableRequests = VulnerableRequestForTemplateDao.instance.findAll(Filters.empty(), skip, limit, Sorts.ascending("_id"));
        return SUCCESS.toUpperCase();
    }

    public String fetchAllSubCategories() {
        boolean includeYamlContent = false;

        switch (mode) {
            case "runTests":
                categories = GlobalEnums.TestCategory.values();
                break;
            case "testEditor":
                includeYamlContent = true;
                break;
            default:
                includeYamlContent = true;
                categories = GlobalEnums.TestCategory.values();
                testSourceConfigs = TestSourceConfigsDao.instance.findAll(Filters.empty());
        }

        Map<String, TestConfig> testConfigMap = YamlTemplateDao.instance.fetchTestConfigMap(includeYamlContent,
                fetchOnlyActive, skip, limit, Filters.empty());
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
        Bson update = Updates.combine(Updates.set(TestingRunIssues.TEST_RUN_ISSUES_STATUS, statusToBeUpdated),
                Updates.set(TestingRunIssues.LAST_UPDATED, Context.now()));

        if (statusToBeUpdated == TestRunIssueStatus.IGNORED) { //Changing status to ignored
            update = Updates.combine(update, Updates.set(TestingRunIssues.IGNORE_REASON, ignoreReason));
        } else {
            update = Updates.combine(update, Updates.unset(TestingRunIssues.IGNORE_REASON));
        }
        TestingRunIssuesDao.instance.updateMany(Filters.in(ID, issueIdArray), update);
        return SUCCESS.toUpperCase();
    }

    String latestTestingRunSummaryId;
    List<String> issueStatusQuery;
    public String fetchIssuesByStatusAndSummaryId() {
        Bson filters = Filters.and(
                Filters.in(TestingRunIssues.TEST_RUN_ISSUES_STATUS, issueStatusQuery),
                Filters.in(TestingRunIssues.LATEST_TESTING_RUN_SUMMARY_ID, new ObjectId(latestTestingRunSummaryId))
        );
        issues = TestingRunIssuesDao.instance.findAll(filters);
        return SUCCESS.toUpperCase();
    }

    List<TestingIssuesId> issuesIds;

    public String fetchIssuesFromResultIds(){
        issues = TestingRunIssuesDao.instance.findAll(
            Filters.and(
                Filters.in(Constants.ID, issuesIds),
                Filters.in(TestingRunIssues.TEST_RUN_ISSUES_STATUS, issueStatusQuery)
            ), Projections.include("_id", TestingRunIssues.TEST_RUN_ISSUES_STATUS)
        );
        return SUCCESS.toUpperCase();
    }

    String testingRunSummaryId;
    private TestingRunResultSummary testingRunResultSummary;
    public String fetchTestingRunResultsSummary() {
        ObjectId testingRunSummaryObj;
        try {
            testingRunSummaryObj = new ObjectId(testingRunSummaryId);
        } catch (Exception e) {
            addActionError("Invalid testing run summary id");
            return ERROR.toUpperCase();
        }

        Bson projection = Projections.include(
                TestingRunResultSummary.STATE,
                TestingRunResultSummary.START_TIMESTAMP,
                TestingRunResultSummary.END_TIMESTAMP
        );

        testingRunResultSummary = TestingRunResultSummariesDao.instance.findOne(Filters.eq(TestingRunResultSummary.ID, testingRunSummaryObj), projection);

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

    public void setStartEpoch(int startEpoch) {
        this.startEpoch = startEpoch;
    }

    public void setEndTimeStamp(long endTimeStamp) {
        this.endTimeStamp = endTimeStamp;
    }

    public List<Integer> getTotalIssuesCountDayWise() {
        return totalIssuesCountDayWise;
    }

    public List<Integer> getOpenIssuesCountDayWise() {
        return openIssuesCountDayWise;
    }

    public List<Integer> getCriticalIssuesCountDayWise() {
        return criticalIssuesCountDayWise;
    }

    public List<HistoricalData> getHistoricalData() {
        return historicalData;
    }
    public long getOpenIssuesCount() {
        return openIssuesCount;
    }

    public long getFixedIssuesCount() {
        return fixedIssuesCount;
    }

    public long getIgnoredIssuesCount() {
        return ignoredIssuesCount;
    }

    public void setSortKey(String sortKey) {
        this.sortKey = sortKey;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void setLatestTestingRunSummaryId(String latestTestingRunSummaryId) {
        this.latestTestingRunSummaryId = latestTestingRunSummaryId;
    }

    public void setIssueStatusQuery(List<String> issueStatusQuery) {
        this.issueStatusQuery = issueStatusQuery;
    }

    public void setIssuesIds(List<TestingIssuesId> issuesIds) {
        this.issuesIds = issuesIds;
    }

    public void setTestingRunSummaryId(String testingRunSummaryId) {
        this.testingRunSummaryId = testingRunSummaryId;
    }

    public TestingRunResultSummary getTestingRunResultSummary() {
        return testingRunResultSummary;
    }
}
