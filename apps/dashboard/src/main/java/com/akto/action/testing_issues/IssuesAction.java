package com.akto.action.testing_issues;

import com.akto.action.UserAction;
import com.akto.dao.HistoricalDataDao;
import com.akto.dao.RBACDao;
import com.akto.action.testing.StartTestAction;
import com.akto.dao.context.Context;
import com.akto.dao.demo.VulnerableRequestForTemplateDao;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dao.testing.VulnerableTestingRunResultDao;
import com.akto.dao.testing.sources.TestReportsDao;
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
import com.akto.dto.User;
import com.akto.dto.testing.sources.TestReports;
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
import com.akto.utils.ApiInfoKeyResult;
import com.akto.utils.TestTemplateUtils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.*;
import com.mongodb.client.result.InsertOneResult;
import com.opensymphony.xwork2.Action;

import lombok.Getter;
import lombok.Setter;

import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.akto.util.Constants.ID;
import static com.akto.util.Constants.ONE_DAY_TIMESTAMP;
import com.akto.dao.ApiInfoDao;
import com.akto.dto.ApiInfo.ApiInfoKey;

public class IssuesAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(IssuesAction.class, LogDb.DASHBOARD);

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
    private List<TestRunIssueStatus> filterStatus;
    private List<Integer> filterCollectionsId;
    private List<Severity> filterSeverity;
    private List<String> filterCompliance;
    private List<String> filterSubCategory;
    private List<TestingRunIssues> similarlyAffectedIssues;
    private boolean activeCollections;

    private int startEpoch;
    long endTimeStamp;
    private Map<Integer,Map<String,Integer>> severityInfo = new HashMap<>();

    private Map<String, String> issuesDescriptionMap;

    @Getter
    int buaCategoryCount;

    int URL_METHOD_PAIR_THRESHOLD = 1;
    
    @Setter
    private boolean showTestSubCategories;
    @Setter
    private boolean showApiInfo;
    @Getter
    private List<ApiInfo> buaCategoryApiInfo = new ArrayList<>();
    @Setter
    String categoryType;
    @Getter
    int endpointsCount;

    public boolean isShowApiInfo() {
        return showApiInfo;
    }

    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();


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

        if (startEpoch != 0) {
            filters = Filters.and(filters, Filters.gte(TestingRunIssues.CREATION_TIME, startEpoch));
        }
        
        if(endTimeStamp != 0){
            filters = Filters.and(filters, Filters.lt(TestingRunIssues.CREATION_TIME, endTimeStamp));
        }

        if(activeCollections){
            Set<Integer> deactivatedCollections = UsageMetricCalculator.getDeactivated();
            filters = Filters.and(filters, Filters.nin(TestingRunIssues.ID_API_COLLECTION_ID, deactivatedCollections));
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

        Set<Integer> deactivatedCollections = UsageMetricCalculator.getDeactivated();
        Bson notIncludedCollections = Filters.nin(ID + "." + TestingIssuesId.API_KEY_INFO + "." + ApiInfo.ApiInfoKey.API_COLLECTION_ID, deactivatedCollections);

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
                Filters.in(TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestRunIssueStatus.OPEN.name()),
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
        GroupByTimeRange.groupByAllRange(daysBetween, pipeline, TestingRunIssues.CREATION_TIME, "totalIssues", 30, null);
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

    private List<TestingRunIssues> removedRunResultsIssuesList;
    public String fetchVulnerableTestingRunResultsFromIssues() {
        Bson filters = createFilters(true);
        try {
            List<TestingRunIssues> issues = new ArrayList<>();
            if(issuesIds != null && !issuesIds.isEmpty()){
                issues =  TestingRunIssuesDao.instance.findAll(Filters.in(Constants.ID, issuesIds));
            }else{
                issues =  TestingRunIssuesDao.instance.findAll(filters, skip, 50, null);
            }
            List<Bson> andFilters = new ArrayList<>();
            List<Bson> filtersForNewCollection = new ArrayList<>();

            Map<String,Boolean> summaryIdVsIsNew = new HashMap<>();

            for (TestingRunIssues issue : issues) {

                ObjectId currentSummaryId = issue.getLatestTestingRunSummaryId();

                Bson baseFilter = Filters.and(
                    Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, currentSummaryId),
                    Filters.eq(TestingRunResult.TEST_SUB_TYPE, issue.getId().getTestSubCategory()),
                    Filters.eq(TestingRunResult.API_INFO_KEY, issue.getId().getApiInfoKey())
                );

                Boolean val = summaryIdVsIsNew.get(currentSummaryId.toHexString());
                if(val == null){
                    val = VulnerableTestingRunResultDao.instance.isStoredInVulnerableCollection(currentSummaryId, true);
                    summaryIdVsIsNew.put(currentSummaryId.toHexString(), val);
                }

                if(!val){
                    andFilters.add(
                        Filters.and(baseFilter,Filters.eq(TestingRunResult.VULNERABLE, true))
                    );
                }else{
                    filtersForNewCollection.add(baseFilter);
                }
            }
            this.testingRunResults = new ArrayList<>();
            if (issues.isEmpty()) {
                // this.sampleDataVsCurlMap = new HashMap<>();
                return SUCCESS.toUpperCase();
            }

            Map<String, TestingRunIssues> testingRunIssuesMap = new HashMap<>();
            for(TestingRunIssues issue: issues) {
                String testSubCategory = issue.getId().getTestSubCategory();
                String key = issue.getId().getApiInfoKey().toString() + "_" + testSubCategory;
                testingRunIssuesMap.put(key, issue);
            }
            if(!andFilters.isEmpty()){
                Bson orFilters = Filters.or(andFilters);
                this.testingRunResults = TestingRunResultDao.instance.findAll(orFilters);
            }

            if (!filtersForNewCollection.isEmpty()) {
                this.testingRunResults.addAll(
                    VulnerableTestingRunResultDao.instance.findAll(Filters.or(filtersForNewCollection))
                );
            }

            Map<String, String> sampleDataVsCurlMap = new HashMap<>();
            // todo: fix
            for (TestingRunResult runResult: this.testingRunResults) {
                List<GenericTestResult> testResults = new ArrayList<>();
                // WorkflowTest workflowTest = runResult.getWorkflowTest();
                for (GenericTestResult tr : runResult.getTestResults()) {
                    if (tr.isVulnerable()) {
                        if (tr instanceof TestResult) {
                            TestResult testResult = (TestResult) tr;
                            testResults.add(testResult);
                            // sampleDataVsCurlMap.put(testResult.getMessage(), ExportSampleDataAction.getCurl(testResult.getMessage()));
                            // sampleDataVsCurlMap.put(testResult.getOriginalMessage(), ExportSampleDataAction.getCurl(testResult.getOriginalMessage()));
                        } else if (tr instanceof MultiExecTestResult){
                            MultiExecTestResult testResult = (MultiExecTestResult) tr;
                            testResults.add(testResult);
                            Map<String, WorkflowTestResult.NodeResult> nodeResultMap = testResult.getNodeResultMap();
                            for (String order : nodeResultMap.keySet()) {
                                WorkflowTestResult.NodeResult nodeResult = nodeResultMap.get(order);
                                String nodeResultLastMessage = StartTestAction.getNodeResultLastMessage(nodeResult.getMessage());
                                if (nodeResultLastMessage != null) {
                                    nodeResult.setMessage(nodeResultLastMessage);
                                    // sampleDataVsCurlMap.put(nodeResultLastMessage,
                                    //         ExportSampleDataAction.getCurl(nodeResultLastMessage));
                                }
                            }
                        }
                    }
                    // if (workflowTest != null) {
                    //     Map<String, WorkflowNodeDetails> nodeDetailsMap = workflowTest.getMapNodeIdToWorkflowNodeDetails();
                    //     for (String nodeName: nodeDetailsMap.keySet()) {
                    //         if (nodeDetailsMap.get(nodeName) instanceof YamlNodeDetails) {
                    //             YamlNodeDetails details = (YamlNodeDetails) nodeDetailsMap.get(nodeName);
                    //             sampleDataVsCurlMap.put(details.getOriginalMessage(),
                    //                     ExportSampleDataAction.getCurl(details.getOriginalMessage()));
                    //         }

                    //     }
                    // }
                }
                runResult.setTestResults(testResults);


                String filterKey = runResult.getApiInfoKey().toString() + "_" + runResult.getTestSubType();
                testingRunIssuesMap.remove(filterKey);
            }

            removedRunResultsIssuesList = new ArrayList<>();
            removedRunResultsIssuesList.addAll(testingRunIssuesMap.values());
            // this.sampleDataVsCurlMap = sampleDataVsCurlMap;
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
        testingRunResult = VulnerableTestingRunResultDao.instance.findOneWithComparison(filterForRunResult, null);
        if (issue.isUnread() && (currentUserRole.equals(Role.ADMIN) || currentUserRole.equals(Role.MEMBER))) {
            logger.debug("Issue id from db to be marked as read " + issueId);
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

        ComplianceMapping complianceMapping = info.getCompliance();

        if (complianceMapping == null) {
            complianceMapping = new ComplianceMapping(new HashMap<>(), "", "", 0);
        }

        infoObj.put("issueDescription", info.getDescription());
        infoObj.put("issueDetails", info.getDetails());
        infoObj.put("issueImpact", info.getImpact());
        infoObj.put("issueTags", info.getTags());
        infoObj.put("compliance", complianceMapping);
        infoObj.put("testName", info.getName());
        infoObj.put("references", info.getReferences());
        infoObj.put("cwe", info.getCwe());
        infoObj.put("cve", info.getCve());
        infoObj.put("name", testConfig.getId());
        infoObj.put("_name", testConfig.getId());
        infoObj.put("content", testConfig.getContent());
        infoObj.put("templateSource", testConfig.getTemplateSource());
        infoObj.put("attributes", testConfig.getAttributes());

        String remediationContent = info.getRemediation();

        if (!StringUtils.isEmpty(remediationContent)) {
            infoObj.put("remediation", remediationContent);
        }
        
        
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
        categories = TestTemplateUtils.getAllTestCategoriesWithinContext(Context.contextSource.get());
        // Bson filters = Filters.in(
        //         "info.category", Arrays.asList(categories)
        // );
        switch (mode) {
            case "runTests":
                break;
            case "testEditor":
                includeYamlContent = true;
                break;
            default:
                includeYamlContent = true;
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
                logger.errorAndAddToDb(e, err, LogDb.DASHBOARD);
            }
        }

        return SUCCESS.toUpperCase();
    }

    public String updateIssueStatus () {
        if (issueId == null || statusToBeUpdated == null || ignoreReason == null) {
            throw new IllegalStateException();
        }

        logger.debug("Issue id from db to be updated " + issueId);
        logger.debug("status id from db to be updated " + statusToBeUpdated);
        logger.debug("status reason from db to be updated " + ignoreReason);
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

    private Map<String,String> testingRunResultHexIdsMap;

    public String bulkUpdateIssueStatus () {
        if (issueIdArray == null || statusToBeUpdated == null || ignoreReason == null) {
            throw new IllegalStateException();
        }

        logger.debug("Issue id from db to be updated " + issueIdArray);
        logger.debug("status id from db to be updated " + statusToBeUpdated);
        logger.debug("status reason from db to be updated " + ignoreReason);
        User user = getSUser();
        String userLogin = user != null ? user.getLogin() : "System";
        Bson update = Updates.combine(Updates.set(TestingRunIssues.TEST_RUN_ISSUES_STATUS, statusToBeUpdated),
            Updates.set(TestingRunIssues.LAST_UPDATED, Context.now()),
            Updates.set(TestingRunIssues.LAST_UPDATED_BY, userLogin));

        if (statusToBeUpdated == TestRunIssueStatus.IGNORED) { //Changing status to ignored
            update = Updates.combine(update, Updates.set(TestingRunIssues.IGNORE_REASON, ignoreReason));
        } else {
            update = Updates.combine(update, Updates.unset(TestingRunIssues.IGNORE_REASON));
        }
        if(!StringUtils.isEmpty(this.description)) {
            update = Updates.combine(update, Updates.set(TestingRunIssues.DESCRIPTION, this.description));
        }
        TestingRunIssuesDao.instance.updateMany(Filters.in(ID, issueIdArray), update);

        int accountId = Context.accountId.get();
        executorService.schedule( new Runnable() {
            public void run() {
                Context.accountId.set(accountId);
                try {

                    final Map<String, Integer> countIssuesMap = new HashMap<>();
                    countIssuesMap.put(Severity.HIGH.toString(), 0);
                    countIssuesMap.put(Severity.MEDIUM.toString(), 0);
                    countIssuesMap.put(Severity.LOW.toString(), 0);

                    // update summaries accordingly with issues ignored
                    // currently we change the summaries from result page only
                    // so only 1 result comes at a time
                    // Map<String,String> testingRunResultHexIdsMap has only 1 result.
                    
                    Map<ObjectId,String> mapSummaryToResultId = VulnerableTestingRunResultDao.instance.mapSummaryIdToTestingResultHexId(testingRunResultHexIdsMap.keySet());
                    if(mapSummaryToResultId.isEmpty()){
                        mapSummaryToResultId = TestingRunResultDao.instance.mapSummaryIdToTestingResultHexId(testingRunResultHexIdsMap.keySet());
                    }
                    Map<ObjectId,Map<String,Integer>> summaryWiseCountMap = new HashMap<>();

                    for(ObjectId summaryId: mapSummaryToResultId.keySet()){
                        String resultHexId = mapSummaryToResultId.get(summaryId);
                        Map<String, Integer> countMap = summaryWiseCountMap.getOrDefault(summaryId, countIssuesMap);
                        String severity = testingRunResultHexIdsMap.get(resultHexId);
                        int initialCount = countMap.getOrDefault(severity, 0);
                        countMap.put(severity, initialCount + 1);
                        summaryWiseCountMap.put(summaryId, countMap);
                    }
                    if(!summaryWiseCountMap.isEmpty()){
                        TestingRunResultSummariesDao.instance.bulkUpdateTestingRunResultSummariesCount(summaryWiseCountMap);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0 , TimeUnit.SECONDS);
        

        return SUCCESS.toUpperCase();
    }

    String latestTestingRunSummaryId;
    List<String> issueStatusQuery;
    List<TestingRunResult> testingRunResultList;
    private Map<String, List<String>> filters;
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
                TestingRunResultSummary.END_TIMESTAMP,
                TestingRunResultSummary.TESTING_RUN_ID);

        testingRunResultSummary = TestingRunResultSummariesDao.instance
                .findOne(Filters.eq(TestingRunResultSummary.ID, testingRunSummaryObj), projection);
                
        if (testingRunResultSummary != null && testingRunResultSummary.getTestingRunId() != null) {
            testingRunResultSummary.setTestingRunHexId(
                    testingRunResultSummary.getTestingRunId().toHexString());
        }

        return SUCCESS.toUpperCase();
    }

    private Map<String, List<String>> reportFilterList;
    private String generatedReportId;
    private List<TestingIssuesId> issuesIdsForReport;
    private BasicDBObject response;

    public String generateTestReport () {
        try {
            TestReports testReport = new TestReports(reportFilterList, Context.now(), "", this.issuesIdsForReport);
            InsertOneResult insertTResult = TestReportsDao.instance.insertOne(testReport);
            this.generatedReportId = insertTResult.getInsertedId().toString();
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            e.printStackTrace();
            addActionError("Error in generating pdf report");
            return ERROR.toUpperCase();
        }
    }

    public String getReportFilters () {
        if(this.generatedReportId == null){
            addActionError("Report id cannot be null");
            return ERROR.toUpperCase();
        }
        response = new BasicDBObject();
        ObjectId reportId = new ObjectId(this.generatedReportId);
        TestReports reportDoc = TestReportsDao.instance.findOne(Filters.eq(Constants.ID, reportId));
        response.put(TestReports.FILTERS_FOR_REPORT, reportDoc.getFiltersForReport());
        response.put(TestReports.ISSUE_IDS_FOR_REPORT, reportDoc.getIssuesIdsForReport());
        return SUCCESS.toUpperCase();
    }

    public String fetchSeverityInfoForIssues() {
        Bson filter = createFilters(true);

        if (issuesIds != null && !issuesIds.isEmpty()) {
            filter = Filters.and(filter, Filters.in(Constants.ID, issuesIds));
        }

        Set<Integer> deactivatedCollections = UsageMetricCalculator.getDeactivated();
        filter = Filters.and(
            filter,
            Filters.nin(TestingRunIssues.ID_API_COLLECTION_ID, deactivatedCollections)
        );

        BasicDBObject groupedId = new BasicDBObject(SingleTypeInfo._API_COLLECTION_ID, "$" + TestingRunIssues.ID_API_COLLECTION_ID)
                .append(TestingRunIssues.KEY_SEVERITY, "$" + TestingRunIssues.KEY_SEVERITY);
        this.severityInfo = TestingRunIssuesDao.instance.getSeveritiesMapForCollections(filter, false, groupedId);
        return Action.SUCCESS.toUpperCase();
    }
    String description;
    public String updateIssueDescription() {
        if(issueId == null){
            addActionError("Issue id cannot be null");
            return ERROR.toUpperCase();
        }
        if(description == null){
            addActionError("Description cannot be null");
            return ERROR.toUpperCase();
        }
        
        TestingRunIssuesDao.instance.updateOneNoUpsert(Filters.eq(Constants.ID, issueId), Updates.set(TestingRunIssues.DESCRIPTION, description));
        return SUCCESS.toUpperCase();
    }

    public String fetchBUACategoryCount() {
        ApiInfoKeyResult result = com.akto.utils.Utils.fetchUniqueApiInfoKeys(
                TestingRunIssuesDao.instance.getRawCollection(),
                createFilters(true),
                "_id.apiInfoKey",
                this.showApiInfo
        );
        this.buaCategoryCount = result.count;
        if (this.showApiInfo) {
            this.buaCategoryApiInfo = result.apiInfoList;
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchUrlsByIssues() {
        List<Bson> pipeline = new ArrayList<>();

        Bson filterQ = UsageMetricCalculator.excludeDemosAndDeactivated(TestingRunIssues.ID_API_COLLECTION_ID);
        pipeline.add(
                Aggregates.match(Filters.and(
                        Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, GlobalEnums.TestRunIssueStatus.OPEN),
                        filterQ
                ))
        );

        try {
            List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            if(collectionIds != null) {
                pipeline.add(Aggregates.match(Filters.in(SingleTypeInfo._COLLECTION_IDS, collectionIds)));
            }
        } catch(Exception e){
        }

        // Group by testSubCategory and collect unique URL+Method combinations
        BasicDBObject groupId = new BasicDBObject("testSubCategory", "$_id." + TestingIssuesId.TEST_SUB_CATEGORY);
        pipeline.add(Aggregates.group(groupId,
                Accumulators.addToSet("apiInfoKeySet", new BasicDBObject("url", "$_id." + TestingIssuesId.API_KEY_INFO + "." + ApiInfo.ApiInfoKey.URL)
                        .append("method", "$_id." + TestingIssuesId.API_KEY_INFO + "." + ApiInfo.ApiInfoKey.METHOD)
                        .append("apiCollectionId", "$_id." + TestingIssuesId.API_KEY_INFO + "." + ApiInfo.ApiInfoKey.API_COLLECTION_ID))
        ));

        // Filter to only include groups with more than the threshold of unique URL+Method combinations
        pipeline.add(Aggregates.match(Filters.expr(
                new BasicDBObject("$gt", Arrays.asList(
                        new BasicDBObject("$size", "$apiInfoKeySet"),
                        URL_METHOD_PAIR_THRESHOLD
                ))
        )));

        if (!showTestSubCategories) {
            try {
                long totalCount = TestingRunIssuesDao.instance.getMCollection()
                        .aggregate(pipeline, BasicDBObject.class)
                        .into(new ArrayList<>())
                        .size();

                this.response = new BasicDBObject();
                this.response.put("totalCount", (int) totalCount);
                return SUCCESS.toUpperCase();
            } catch (Exception e) {
                addActionError("Error counting URLs by test subcategory");
                return ERROR.toUpperCase();
            }
        }

        pipeline.add(Aggregates.project(Projections.fields(
                Projections.include("testSubCategory", "apiInfoKeySet"),
                Projections.computed("apiInfoKeySetCount", new BasicDBObject("$size", "$apiInfoKeySet"))
        )));

        // Sort by testSubCategory
        pipeline.add(Aggregates.sort(Sorts.ascending("testSubCategory")));

        try {
            MongoCursor<BasicDBObject> cursor = TestingRunIssuesDao.instance.getMCollection()
                    .aggregate(pipeline, BasicDBObject.class)
                    .cursor();

            List<BasicDBObject> result = new ArrayList<>();
            while (cursor.hasNext()) {
                BasicDBObject doc = cursor.next();
                result.add(doc);
            }

            this.response = new BasicDBObject();
            this.response.put("testSubCategories", result);
            this.response.put("totalCount", result.size());

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            addActionError("Error fetching URLs by test subcategory");
            return ERROR.toUpperCase();
        }
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

    public List<TestingRunResult> getTestingRunResultList() {
        return testingRunResultList;
    }

    public void setFilters(Map<String, List<String>> filters) {
        this.filters = filters;
    }

    public void setReportFilterList(Map<String, List<String>> reportFilterList) {
        this.reportFilterList = reportFilterList;
    }

    public String getGeneratedReportId() {
        return generatedReportId;
    }

    public void setGeneratedReportId(String generatedReportId) {
        this.generatedReportId = generatedReportId;
    }
    public void setIssuesIdsForReport(List<TestingIssuesId> issuesIdsForReport) {
        this.issuesIdsForReport = issuesIdsForReport;
    }

    public BasicDBObject getResponse() {
        return response;
    }

    public Map<Integer, Map<String, Integer>> getSeverityInfo() {
        return severityInfo;
    }

    public void setSeverityInfo(Map<Integer, Map<String, Integer>> severityInfo) {
        this.severityInfo = severityInfo;
    }

    public void setTestingRunResultHexIdsMap(Map<String, String> testingRunResultHexIdsMap) {
        this.testingRunResultHexIdsMap = testingRunResultHexIdsMap;
    }

    public List<TestingRunIssues> getRemovedRunResultsIssuesList() {
        return removedRunResultsIssuesList;
    }

    public void setActiveCollections(boolean activeCollections) {
        this.activeCollections = activeCollections;
    }

    public List<String> getFilterCompliance() {
        return filterCompliance;
    }

    public void setFilterCompliance(List<String> filterCompliance) {
        this.filterCompliance = filterCompliance;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public String getDescription() {
        return description;
    }

    public Map<String, String> getIssuesDescriptionMap() {
        return issuesDescriptionMap;
    }

    public void setIssuesDescriptionMap(Map<String, String> issuesDescriptionMap) {
        this.issuesDescriptionMap = issuesDescriptionMap;
    }
}
