package com.akto.action.testing;

import com.akto.action.ExportSampleDataAction;
import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.sources.TestSourceConfigsDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dao.testing.*;
import com.akto.dto.ApiInfo;
import com.akto.dto.User;
import com.akto.dto.ApiToken.Utility;
import com.akto.dto.CollectionConditions.TestConfigsAdvancedSettings;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.*;
import com.akto.dto.testing.TestingRun.State;
import com.akto.dto.testing.TestingRun.TestingRunType;
import com.akto.dto.testing.TestResult.TestError;
import com.akto.dto.testing.info.CurrentTestsStatus;
import com.akto.dto.testing.info.CurrentTestsStatus.StatusForIndividualTest;
import com.akto.dto.testing.sources.TestSourceConfig;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums;
import com.akto.util.enums.GlobalEnums.TestErrorSource;
import com.akto.utils.DeleteTestRunUtils;
import com.akto.utils.Utils;
import com.google.gson.Gson;
import com.mongodb.client.model.*;
import com.mongodb.client.result.InsertOneResult;
import com.opensymphony.xwork2.Action;

import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StartTestAction extends UserAction {

    private TestingEndpoints.Type type;
    private int apiCollectionId;
    private List<ApiInfo.ApiInfoKey> apiInfoKeyList;
    private int testIdConfig;
    private int workflowTestId;
    private int startTimestamp;
    private int testRunTime;
    private int maxConcurrentRequests;
    boolean recurringDaily;
    private List<TestingRun> testingRuns;
    private AuthMechanism authMechanism;
    private int endTimestamp;
    private String testName;
    private Map<String, String> metadata;
    private String triggeredBy;
    private boolean isTestRunByTestEditor;
    private Map<ObjectId, TestingRunResultSummary> latestTestingRunResultSummaries;
    private Map<String, String> sampleDataVsCurlMap;
    private String overriddenTestAppUrl;
    private static final LoggerMaker loggerMaker = new LoggerMaker(StartTestAction.class, LogDb.DASHBOARD);
    private TestingRunType testingRunType;
    private String searchString;
    private boolean continuousTesting;
    private int testingRunConfigId;

    private Map<String,Long> allTestsCountMap = new HashMap<>();
    private Map<String,Integer> issuesSummaryInfoMap = new HashMap<>();

    private String testRoleId;
    private boolean cleanUpTestingResources;

    private static final Gson gson = new Gson();

    private static List<ObjectId> getTestingRunListFromSummary(Bson filters){
        Bson projections = Projections.fields(
                Projections.excludeId(),
                Projections.include(TestingRunResultSummary.TESTING_RUN_ID));
        return TestingRunResultSummariesDao.instance.findAll(
                filters, projections).stream().map(summary -> summary.getTestingRunId())
                .collect(Collectors.toList());
    }

    public List<ObjectId> getCicdTests(){
        return getTestingRunListFromSummary(Filters.exists(TestingRunResultSummary.METADATA_STRING));
    }

    private static List<ObjectId> getTestsWithSeverity(List<String> severities) {

        List<Bson> severityFilters = new ArrayList<>();
        for (String severity : severities) {
            severityFilters.add(Filters.gt(TestingRunResultSummary.COUNT_ISSUES + "." + severity, 0));
        }

        return getTestingRunListFromSummary(Filters.or(severityFilters));
    }

    private CallSource source;
    private boolean sendSlackAlert = false;

    private TestingRun createTestingRun(int scheduleTimestamp, int periodInSeconds) {
        User user = getSUser();

        if (!StringUtils.isEmpty(this.overriddenTestAppUrl)) {
            boolean isValidUrl = Utils.isValidURL(this.overriddenTestAppUrl);

            if (!isValidUrl) {
                addActionError("The override url is invalid. Please check the url again.");
                return null;
            }
        }

        AuthMechanism authMechanism = TestRolesDao.instance.fetchAttackerToken(0, null);
        if (authMechanism == null && testIdConfig == 0) {
            addActionError("Please set authentication mechanism before you test any APIs");
            return null;
        }

        TestingEndpoints testingEndpoints;
        switch (type) {
            case CUSTOM:
                if (this.apiInfoKeyList == null || this.apiInfoKeyList.isEmpty()) {
                    addActionError("APIs list can't be empty");
                    return null;
                }
                testingEndpoints = new CustomTestingEndpoints(apiInfoKeyList);
                break;
            case COLLECTION_WISE:
                testingEndpoints = new CollectionWiseTestingEndpoints(apiCollectionId);
                break;
            case WORKFLOW:
                WorkflowTest workflowTest = WorkflowTestsDao.instance.findOne(Filters.eq("_id", this.workflowTestId));
                if (workflowTest == null) {
                    addActionError("Couldn't find workflow test");
                    return null;
                }
                testingEndpoints = new WorkflowTestingEndpoints(workflowTest);
                testIdConfig = 1;
                break;
            default:
                addActionError("Invalid APIs type");
                return null;
        }
        if (this.selectedTests != null) {
            int id = UUID.randomUUID().hashCode() & 0xfffffff;
            TestingRunConfig testingRunConfig = new TestingRunConfig(id, null, this.selectedTests, authMechanism.getId(), this.overriddenTestAppUrl, this.testRoleId, this.cleanUpTestingResources);
            // add advanced setting here
            if(this.testConfigsAdvancedSettings != null && !this.testConfigsAdvancedSettings.isEmpty()){
                testingRunConfig.setConfigsAdvancedSettings(this.testConfigsAdvancedSettings);
            }
            this.testIdConfig = testingRunConfig.getId();
            TestingRunConfigDao.instance.insertOne(testingRunConfig);
        }

        return new TestingRun(scheduleTimestamp, user.getLogin(),
                testingEndpoints, testIdConfig, State.SCHEDULED, periodInSeconds, testName, this.testRunTime,
                this.maxConcurrentRequests, this.sendSlackAlert);
    }

    private List<String> selectedTests;
    private List<TestConfigsAdvancedSettings> testConfigsAdvancedSettings;

    public String startTest() {

        if (this.startTimestamp != 0 && this.startTimestamp + 86400 < Context.now()) {
            addActionError("Cannot schedule a test run in the past.");
            return ERROR.toUpperCase();
        }

        int scheduleTimestamp = this.startTimestamp == 0 ? Context.now() : this.startTimestamp;
        handleCallFromAktoGpt();

        TestingRun localTestingRun = null;
        if (this.testingRunHexId != null) {
            try {
                ObjectId testingId = new ObjectId(this.testingRunHexId);
                localTestingRun = TestingRunDao.instance.findOne(Constants.ID, testingId);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e,
                        "ERROR in converting testingRunHexId to objectId: " + this.testingRunHexId + " " + e.toString(),
                        LogDb.DASHBOARD);
            }
        }
        if (localTestingRun == null) {
            try {
                localTestingRun = createTestingRun(scheduleTimestamp, this.recurringDaily ? 86400 : 0);
                // pass boolean from ui, which will tell if testing is coniinuous on new endpoints
                if (this.continuousTesting) {
                    localTestingRun.setPeriodInSeconds(-1);
                }
                if (triggeredBy != null && !triggeredBy.isEmpty()) {
                    localTestingRun.setTriggeredBy(triggeredBy);
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Unable to create test run - " + e.toString(), LogDb.DASHBOARD);
            }

            if (localTestingRun == null) {
                return ERROR.toUpperCase();
            } else {
                TestingRunDao.instance.insertOne(localTestingRun);
                testingRunHexId = localTestingRun.getId().toHexString();
            }
            this.testIdConfig = 0;
        } else {
            if(this.metadata == null || this.metadata.isEmpty()){
                TestingRunDao.instance.updateOne(
                    Filters.eq(Constants.ID, localTestingRun.getId()),
                    Updates.combine(
                            Updates.set(TestingRun.STATE, TestingRun.State.SCHEDULED),
                            Updates.set(TestingRun.SCHEDULE_TIMESTAMP, scheduleTimestamp)));
            }
            if (this.overriddenTestAppUrl != null || this.selectedTests != null) {
                int id = UUID.randomUUID().hashCode() & 0xfffffff ;
                TestingRunConfig testingRunConfig = new TestingRunConfig(id, null, this.selectedTests, null, this.overriddenTestAppUrl, this.testRoleId);
                this.testIdConfig = testingRunConfig.getId();
                TestingRunConfigDao.instance.insertOne(testingRunConfig);
            }

        }

        Map<String, Object> session = getSession();
        String utility = (String) session.get("utility");

        if (utility != null
                && (Utility.CICD.toString().equals(utility) || Utility.EXTERNAL_API.toString().equals(utility))) {
            int testsCounts = this.selectedTests != null ? this.selectedTests.size() : 0;
            TestingRunResultSummary summary = new TestingRunResultSummary(scheduleTimestamp, 0, new HashMap<>(),
                    0, localTestingRun.getId(), localTestingRun.getId().toHexString(), 0, this.testIdConfig,testsCounts );
            summary.setState(TestingRun.State.SCHEDULED);
            if (metadata != null) {
                loggerMaker.infoAndAddToDb("CICD test triggered at " + Context.now(), LogDb.DASHBOARD);
                summary.setMetadata(metadata);
            }
            InsertOneResult result = TestingRunResultSummariesDao.instance.insertOne(summary);
            this.testingRunResultSummaryHexId = result.getInsertedId().asObjectId().getValue().toHexString();

        }

        this.startTimestamp = 0;
        this.endTimestamp = 0;
        this.retrieveAllCollectionTests();
        return SUCCESS.toUpperCase();
    }

    private void handleCallFromAktoGpt() {
        if (this.source == null) {
            loggerMaker.infoAndAddToDb("Call from testing UI, skipping", LoggerMaker.LogDb.DASHBOARD);
            return;
        }
        if (this.source.isCallFromAktoGpt() && !this.selectedTests.isEmpty()) {
            loggerMaker.infoAndAddToDb("Call from Akto GPT, " + this.selectedTests, LoggerMaker.LogDb.DASHBOARD);
            Map<String, Info> testInfoMap = YamlTemplateDao.instance.fetchTestInfoMap();
            List<String> tests = new ArrayList<>();
            for (String selectedTest : this.selectedTests) {
                List<String> testSubCategories = new ArrayList<>();
                for (Info testInfo : testInfoMap.values()) {
                    if (selectedTest.equalsIgnoreCase(testInfo.getCategory().getName())) {
                        testSubCategories.add(testInfo.getSubCategory());
                    }
                }
                if (testSubCategories.isEmpty()) {
                    loggerMaker.errorAndAddToDb("Test not found for " + selectedTest, LoggerMaker.LogDb.DASHBOARD);
                } else {
                    loggerMaker.infoAndAddToDb(
                            String.format("Category: %s, tests: %s", selectedTest, testSubCategories),
                            LoggerMaker.LogDb.DASHBOARD);
                    tests.addAll(testSubCategories);
                }
            }
            if (!tests.isEmpty()) {
                this.selectedTests = tests;
                loggerMaker.infoAndAddToDb("Tests found for " + this.selectedTests, LoggerMaker.LogDb.DASHBOARD);
            } else {
                loggerMaker.errorAndAddToDb("No tests found for " + this.selectedTests, LoggerMaker.LogDb.DASHBOARD);
            }
        }
    }

    private String sortKey;
    private int sortOrder;
    private int limit;
    private int skip;
    private Map<String, List> filters;
    private long testingRunsCount;

    private ArrayList<Bson> prepareFilters(int startTimestamp, int endTimestamp) {
        ArrayList<Bson> filterList = new ArrayList<>();

        filterList.add(Filters.lte(TestingRun.SCHEDULE_TIMESTAMP, endTimestamp));
        filterList.add(Filters.gte(TestingRun.SCHEDULE_TIMESTAMP, startTimestamp));
        filterList.add(Filters.ne(TestingRun.TRIGGERED_BY, "test_editor"));

        if (filters == null) {
            return filterList;
        }

        try {
            for (Map.Entry<String, List> entry : filters.entrySet()) {
                String key = entry.getKey();
                List value = entry.getValue();

                if (value == null || value.isEmpty())
                    continue;

                switch (key) {
                    case "severity":
                        List<String> severities = Utils.castList(String.class, value);
                        filterList.add(Filters.in(Constants.ID, getTestsWithSeverity(severities)));
                        break;

                    default:
                        break;
                }

            }
        } catch (Exception e) {
            return filterList;
        }

        return filterList;
    }

    private Bson prepareSort() {
        List<String> sortFields = new ArrayList<>();
        if (sortKey == null || "".equals(sortKey)) {
            sortKey = TestingRun.SCHEDULE_TIMESTAMP;
        }
        sortFields.add(sortKey);

        return sortOrder == 1 ? Sorts.ascending(sortFields) : Sorts.descending(sortFields);
    }

    private Bson getTestingRunTypeFilter(TestingRunType testingRunType){
        if(testingRunType == null){
            return Filters.empty();
        }
        switch (testingRunType) {
            case CI_CD:
                return Filters.in(Constants.ID, getCicdTests());
            case ONE_TIME:
                return Filters.and(
                    Filters.nin(Constants.ID, getCicdTests()),
                    Filters.eq(TestingRun.PERIOD_IN_SECONDS,0
                ));
            case RECURRING:
                return Filters.and(
                    Filters.nin(Constants.ID, getCicdTests()),
                    Filters.ne(TestingRun.PERIOD_IN_SECONDS,0),
                    Filters.ne(TestingRun.PERIOD_IN_SECONDS, -1));
            case CONTINUOUS_TESTING:
                return Filters.and(
                    Filters.nin(Constants.ID, getCicdTests()),
                    Filters.eq(TestingRun.PERIOD_IN_SECONDS,-1
                ));
            default:
                return Filters.empty();
        }
    }

    private Bson getSearchFilters(){
        if(this.searchString == null || this.searchString.length() < 3){
            return Filters.empty();
        }
        String escapedPrefix = Pattern.quote(this.searchString);
        String regexPattern = ".*" + escapedPrefix + ".*";
        Bson filter = Filters.or(
            Filters.regex(TestingRun.NAME, regexPattern, "i")
        );
        return filter;
    }

    private ArrayList<Bson> getTableFilters(){
        ArrayList<Bson> filterList = new ArrayList<>();
        if(this.filters == null){
            return filterList;
        }
        List<Integer> apiCollectionIds = (List<Integer>) this.filters.getOrDefault("apiCollectionId", new ArrayList<>());
        if(!apiCollectionIds.isEmpty()){
            filterList.add(
                Filters.or(
                    Filters.in(TestingRun._API_COLLECTION_ID, apiCollectionIds),
                    Filters.in(TestingRun._API_COLLECTION_ID_IN_LIST, apiCollectionIds)
                )
            );
        }

        return filterList;
    }

    public String retrieveAllCollectionTests() {

        this.authMechanism = TestRolesDao.instance.fetchAttackerToken(0, null);

        ArrayList<Bson> testingRunFilters = new ArrayList<>();
        Bson testingRunTypeFilter = getTestingRunTypeFilter(testingRunType);
        testingRunFilters.add(testingRunTypeFilter);
        testingRunFilters.addAll(prepareFilters(startTimestamp, endTimestamp));
        testingRunFilters.add(getSearchFilters());
        testingRunFilters.addAll(getTableFilters());

        if(skip < 0){
            skip *= -1;
        }

        if(limit < 0){
            limit *= -1;
        }

        int pageLimit = Math.min(limit == 0 ? 50 : limit, 200);

        testingRuns = TestingRunDao.instance.findAll(
                Filters.and(testingRunFilters), skip, pageLimit,
                prepareSort());

        List<ObjectId> testingRunHexIds = new ArrayList<>();
        testingRuns.forEach(
                localTestingRun->  {
                    testingRunHexIds.add(localTestingRun.getId());
                });

        latestTestingRunResultSummaries = TestingRunResultSummariesDao.instance
                .fetchLatestTestingRunResultSummaries(testingRunHexIds);

        testingRunsCount = TestingRunDao.instance.getMCollection().countDocuments(Filters.and(testingRunFilters));

        return SUCCESS.toUpperCase();
    }

    String testingRunHexId;
    List<TestingRunResultSummary> testingRunResultSummaries;
    final int limitForTestingRunResultSummary = 20;
    private TestingRun testingRun;
    private WorkflowTest workflowTest;

    public String fetchTestingRunResultSummaries() {
        ObjectId testingRunId;
        try {
            testingRunId = new ObjectId(testingRunHexId);
        } catch (Exception e) {
            addActionError("Invalid test id");
            return ERROR.toUpperCase();
        }

        List<Bson> filterQ = new ArrayList<>();
        filterQ.add(Filters.eq(TestingRunResultSummary.TESTING_RUN_ID, testingRunId));

        if (this.startTimestamp != 0) {
            filterQ.add(Filters.gte(TestingRunResultSummary.START_TIMESTAMP, this.startTimestamp));
        }

        if (this.endTimestamp != 0) {
            filterQ.add(Filters.lte(TestingRunResultSummary.START_TIMESTAMP, this.endTimestamp));
        }

        Bson sort = Sorts.descending(TestingRunResultSummary.START_TIMESTAMP);

        this.testingRunResultSummaries = TestingRunResultSummariesDao.instance.findAll(Filters.and(filterQ), 0,
                limitForTestingRunResultSummary, sort);
        this.testingRun = TestingRunDao.instance.findOne(Filters.eq("_id", testingRunId));

        long cicdCount =  TestingRunDao.instance.getMCollection().countDocuments(
            Filters.and(
                Filters.eq(Constants.ID, testingRunId),
                getTestingRunTypeFilter(TestingRunType.CI_CD)
            )
        );

        this.testingRunType = TestingRunType.ONE_TIME;
        if(cicdCount > 0){
            this.testingRunType = TestingRunType.CI_CD;
        }
        else if(this.testingRun.getPeriodInSeconds() > 0){
            this.testingRunType = TestingRunType.RECURRING;
        }

        if (this.testingRun != null && this.testingRun.getTestIdConfig() == 1) {
            WorkflowTestingEndpoints workflowTestingEndpoints = (WorkflowTestingEndpoints) testingRun
                    .getTestingEndpoints();
            this.workflowTest = WorkflowTestsDao.instance
                    .findOne(Filters.eq("_id", workflowTestingEndpoints.getWorkflowTest().getId()));
        }

        TestingRunConfig runConfig = TestingRunConfigDao.instance.findOne(
            Filters.eq("_id", this.testingRun.getTestIdConfig()), Projections.exclude("collectionWiseApiInfoKey", "testSubCategoryList")
        );

        this.testingRun.setTestingRunConfig(runConfig);

        return SUCCESS.toUpperCase();
    }

    public String fetchTestingRunResultSummary() {
        this.testingRunResultSummaries = new ArrayList<>();
        this.testingRunResultSummaries.add(
                TestingRunResultSummariesDao.instance.findOne("_id", new ObjectId(this.testingRunResultSummaryHexId)));

        if (this.testingRunResultSummaries.size() == 0) {
            addActionError("No test summaries found");
            return ERROR.toUpperCase();
        } else {
            return SUCCESS.toUpperCase();
        }
    }

    private static Bson vulnerableFilter = Filters.and(
        Filters.eq(TestingRunResult.VULNERABLE, true),
        Filters.in(TestingRunResult.IS_IGNORED_RESULT, Arrays.asList(null, false))
    );

    private List<Bson> prepareTestRunResultsFilters(ObjectId testingRunResultSummaryId, QueryMode queryMode) {
        List<Bson> filterList = new ArrayList<>();
        filterList.add(Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, testingRunResultSummaryId));

        if(reportFilterList != null) {
            Bson filtersForTestingRunResults = com.akto.action.testing.Utils.createFiltersForTestingReport(reportFilterList);
            if (!filtersForTestingRunResults.equals(Filters.empty())) {
                filterList.add(filtersForTestingRunResults);
            }
        }

        if(queryMode == null) {
            if(fetchOnlyVulnerable) {
                filterList.add(vulnerableFilter);
            }
        } else {
            switch (queryMode) {
                case VULNERABLE:
                    filterList.add(vulnerableFilter);
                    break;
                case SKIPPED_EXEC_API_REQUEST_FAILED:
                    filterList.add(Filters.eq(TestingRunResult.VULNERABLE, false));
                    filterList.add(Filters.in(TestingRunResultDao.ERRORS_KEY, TestResult.API_CALL_FAILED_ERROR_STRING, TestResult.API_CALL_FAILED_ERROR_STRING_UNREACHABLE));
                    break;
                case SKIPPED_EXEC:
                    filterList.add(Filters.eq(TestingRunResult.VULNERABLE, false));
                    filterList.add(Filters.in(TestingRunResultDao.ERRORS_KEY, TestResult.TestError.getErrorsToSkipTests()));
                    break;
                case SECURED:
                    filterList.add(Filters.eq(TestingRunResult.VULNERABLE, false));
                    List<String> errorsToSkipTest = TestResult.TestError.getErrorsToSkipTests();
                    errorsToSkipTest.add(TestResult.API_CALL_FAILED_ERROR_STRING);
                    errorsToSkipTest.add(TestResult.API_CALL_FAILED_ERROR_STRING_UNREACHABLE);
                    filterList.add(
                            Filters.or(
                                    Filters.exists(WorkflowTestingEndpoints._WORK_FLOW_TEST),
                                    Filters.and(
                                            Filters.nin(TestingRunResultDao.ERRORS_KEY, errorsToSkipTest),
                                            Filters.ne(TestingRunResult.REQUIRES_CONFIG, true)
                                    )
                            )
                    );
                    break;
                case SKIPPED_EXEC_NEED_CONFIG:
                    filterList.add(Filters.eq(TestingRunResult.REQUIRES_CONFIG, true));
                    break;
            }
        }

        if(filterList.isEmpty()) {
            filterList.add(Filters.empty());
        }

        return filterList;
    }

    public static Bson prepareTestingRunResultCustomSorting(String sortKey, int sortOrder) {
        Bson sortStage = null;
        if (TestingRunIssues.KEY_SEVERITY.equals(sortKey)) {
            sortStage = (sortOrder == 1) ?
                    Aggregates.sort(Sorts.ascending("severityValue", TestingRunResult.END_TIMESTAMP)) :
                    Aggregates.sort(Sorts.descending("severityValue", TestingRunResult.END_TIMESTAMP));
        } else if ("time".equals(sortKey)) {
            sortStage = (sortOrder == 1) ?
                    Aggregates.sort(Sorts.ascending(TestingRunResult.END_TIMESTAMP)) :
                    Aggregates.sort(Sorts.descending(TestingRunResult.END_TIMESTAMP));
        }

        return sortStage;
    }

    Map<String, Integer> testCountMap;
    public String fetchTestRunResultsCount() {
        ObjectId testingRunResultSummaryId;
        try {
            testingRunResultSummaryId = new ObjectId(testingRunResultSummaryHexId);
        } catch (Exception e) {
            addActionError("Invalid test summary id");
            return ERROR.toUpperCase();
        }

        testCountMap = new HashMap<>();
        int timeNow = Context.now();
        for(QueryMode qm : QueryMode.values()) {
            if(qm.equals(QueryMode.ALL) || qm.equals(QueryMode.SKIPPED_EXEC_NO_ACTION)) {
                continue;
            }

            timeNow = Context.now();
            int count = (int) TestingRunResultDao.instance.count(Filters.and(
                    prepareTestRunResultsFilters(testingRunResultSummaryId, qm)
            ));
            loggerMaker.infoAndAddToDb("[" + (Context.now() - timeNow) + "] Fetched total count of testingRunResults for: " + qm.name(), LogDb.DASHBOARD);
            testCountMap.put(qm.toString(), count);
        }

        testCountMap.put(QueryMode.VULNERABLE.name(), testCountMap.getOrDefault(QueryMode.VULNERABLE.name(), 0));
        return SUCCESS.toUpperCase();
    }

    String testingRunResultSummaryHexId;
    List<TestingRunResult> testingRunResults;
    private boolean fetchOnlyVulnerable;
    public enum QueryMode {
        VULNERABLE, SECURED, SKIPPED_EXEC_NEED_CONFIG, SKIPPED_EXEC_NO_ACTION, SKIPPED_EXEC, ALL, SKIPPED_EXEC_API_REQUEST_FAILED;
    }
    private QueryMode queryMode;

    private Map<TestError, String> errorEnums = new HashMap<>();
    List<TestingRunIssues> issueslist;

    public String fetchTestingRunResults() {
        ObjectId testingRunResultSummaryId;
        try {
            testingRunResultSummaryId = new ObjectId(testingRunResultSummaryHexId);
        } catch (Exception e) {
            addActionError("Invalid test summary id");
            return ERROR.toUpperCase();
        }


        if (testingRunResultSummaryHexId != null) loggerMaker.infoAndAddToDb("fetchTestingRunResults called for hexId=" + testingRunResultSummaryHexId);
        if (queryMode != null) loggerMaker.infoAndAddToDb("fetchTestingRunResults called for queryMode="+queryMode);

        int timeNow = Context.now();
        Bson ignoredIssuesFilters = Filters.and(
                Filters.in(TestingRunIssues.TEST_RUN_ISSUES_STATUS, "IGNORED"),
                Filters.in(TestingRunIssues.LATEST_TESTING_RUN_SUMMARY_ID, testingRunResultSummaryId)
        );
        issueslist = TestingRunIssuesDao.instance.findAll(ignoredIssuesFilters, Projections.include("_id"));
        loggerMaker.infoAndAddToDb("[" + (Context.now() - timeNow) + "] Fetched testing run issues of size: " + issueslist.size(), LogDb.DASHBOARD);

        List<Bson> testingRunResultFilters = prepareTestRunResultsFilters(testingRunResultSummaryId, queryMode);

        if(queryMode == QueryMode.SKIPPED_EXEC || queryMode == QueryMode.SKIPPED_EXEC_NEED_CONFIG){
            TestError[] testErrors = TestResult.TestError.values();
            for(TestError testError: testErrors){
                this.errorEnums.put(testError, testError.getMessage());
            }
        }
        if(queryMode == QueryMode.SKIPPED_EXEC_API_REQUEST_FAILED){
            this.errorEnums.put(TestError.NO_API_REQUEST, TestError.NO_API_REQUEST.getMessage());
        }

        try {
            int pageLimit = limit <= 0 ? 150 : limit;
            Bson sortStage = prepareTestingRunResultCustomSorting(sortKey, sortOrder);

            timeNow = Context.now();
            Bson filters = testingRunResultFilters.isEmpty() ? Filters.empty() : Filters.and(testingRunResultFilters);
            this.testingRunResults = TestingRunResultDao.instance
                    .fetchLatestTestingRunResultWithCustomAggregations(filters, pageLimit, skip, sortStage);
            loggerMaker.infoAndAddToDb("[" + (Context.now() - timeNow) + "] Fetched testing run results of size: " + testingRunResults.size(), LogDb.DASHBOARD);

            timeNow = Context.now();
            removeTestingRunResultsByIssues(testingRunResults, issueslist, false);
            loggerMaker.infoAndAddToDb("[" + (Context.now() - timeNow) + "] Removed ignored issues from testing run results. Current size of testing run results: " + testingRunResults.size(), LogDb.DASHBOARD);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error in fetchLatestTestingRunResult: " + e);
        }

        timeNow = Context.now();
        loggerMaker.infoAndAddToDb("fetchTestingRunResults completed in: " + (Context.now() - timeNow), LogDb.DASHBOARD);

        return SUCCESS.toUpperCase();
    }

    public static void removeTestingRunResultsByIssues(List<TestingRunResult> testingRunResults,
                                                       List<TestingRunIssues> testingRunIssues,
                                                       boolean retainByIssues) {
        long startTime = System.currentTimeMillis();

        Set<String> issuesSet = new HashSet<>();
        for (TestingRunIssues issue : testingRunIssues) {
            String apiInfoKeyString = issue.getId().getApiInfoKey().toString();
            String key = apiInfoKeyString + "|" + issue.getId().getTestSubCategory();
            issuesSet.add(key);
        }
        loggerMaker.infoAndAddToDb("Total issues to be removed from TestingRunResults list: " + issuesSet.size(), LogDb.DASHBOARD);

        Iterator<TestingRunResult> resultIterator = testingRunResults.iterator();

        while (resultIterator.hasNext()) {
            TestingRunResult result = resultIterator.next();
            String apiInfoKeyString = result.getApiInfoKey().toString();
            String resultKey = apiInfoKeyString + "|" + result.getTestSubType();

            boolean matchFound = issuesSet.contains(resultKey);

            if (retainByIssues && !matchFound) {
                resultIterator.remove();
            } else if (!retainByIssues && matchFound) {
                resultIterator.remove();
            }
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        loggerMaker.infoAndAddToDb("[" + Instant.now() + "] Removed elements from TestingRunResults list in " + duration + " ms", LogDb.DASHBOARD);
    }

    private Map<String, List<String>> reportFilterList;

    public String fetchVulnerableTestRunResults() {
        ObjectId testingRunResultSummaryId;
        try {
            testingRunResultSummaryId = new ObjectId(testingRunResultSummaryHexId);
            Bson filterForReport = com.akto.action.testing.Utils.createFiltersForTestingReport(reportFilterList);
            Bson filters = Filters.and(
                    Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, testingRunResultSummaryId),
                    Filters.eq(TestingRunResult.VULNERABLE, true),
                    filterForReport
            );

            List<TestingRunResult> testingRunResultList = TestingRunResultDao.instance.findAll(filters, skip, 50, null);
            // Map<String, String> sampleDataVsCurlMap = new HashMap<>();
            // for (TestingRunResult runResult: testingRunResultList) {
            //     WorkflowTest workflowTest = runResult.getWorkflowTest();
            //     for (GenericTestResult tr : runResult.getTestResults()) {
            //         if (tr.isVulnerable()) {
            //             if (tr instanceof TestResult) {
            //                 TestResult testResult = (TestResult) tr;
            //                 // sampleDataVsCurlMap.put(testResult.getMessage(),
            //                 //         ExportSampleDataAction.getCurl(testResult.getMessage()));
            //                 // sampleDataVsCurlMap.put(testResult.getOriginalMessage(),
            //                 //         ExportSampleDataAction.getCurl(testResult.getOriginalMessage()));
            //             } else if (tr instanceof MultiExecTestResult){
            //                 MultiExecTestResult testResult = (MultiExecTestResult) tr;
            //                 Map<String, WorkflowTestResult.NodeResult> nodeResultMap = testResult.getNodeResultMap();
            //                 for (String order : nodeResultMap.keySet()) {
            //                     WorkflowTestResult.NodeResult nodeResult = nodeResultMap.get(order);
            //                     String nodeResultLastMessage = getNodeResultLastMessage(nodeResult.getMessage());
            //                     if (nodeResultLastMessage != null) {
            //                         nodeResult.setMessage(nodeResultLastMessage);
            //                         sampleDataVsCurlMap.put(nodeResultLastMessage,
            //                                 ExportSampleDataAction.getCurl(nodeResultLastMessage));
            //                     }
            //                 }
            //             }
            //         }
            //     }
            //     if (workflowTest != null) {
            //         Map<String, WorkflowNodeDetails> nodeDetailsMap = workflowTest.getMapNodeIdToWorkflowNodeDetails();
            //         for (String nodeName: nodeDetailsMap.keySet()) {
            //             if (nodeDetailsMap.get(nodeName) instanceof YamlNodeDetails) {
            //                 YamlNodeDetails details = (YamlNodeDetails) nodeDetailsMap.get(nodeName);
            //                 sampleDataVsCurlMap.put(details.getOriginalMessage(),
            //                         ExportSampleDataAction.getCurl(details.getOriginalMessage()));
            //             }

            //         }
            //     }
            // }
            this.testingRunResults = testingRunResultList;
            // this.sampleDataVsCurlMap = sampleDataVsCurlMap;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while executing test run summary" + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Invalid test summary id");
            return ERROR.toUpperCase();
        }

        return SUCCESS.toUpperCase();
    }

    public static String getNodeResultLastMessage(String message) {
        if (StringUtils.isEmpty(message) || "[]".equals(message)) {
            return null;
        }
        List listOfMessage = gson.fromJson(message, List.class);
        Object vulnerableMessage = listOfMessage.get(listOfMessage.size() - 1);
        return gson.toJson(vulnerableMessage);
    }

    private String testingRunResultHexId;
    private TestingRunResult testingRunResult;

    public String fetchTestRunResultDetails() {
        ObjectId testingRunResultId = new ObjectId(testingRunResultHexId);
        this.testingRunResult = TestingRunResultDao.instance.findOne("_id", testingRunResultId);
        List<GenericTestResult> runResults = new ArrayList<>();

        for (GenericTestResult testResult: this.testingRunResult.getTestResults()) {
            if (testResult instanceof TestResult) {
                runResults.add(testResult);
            } else {
                MultiExecTestResult multiTestRes = (MultiExecTestResult) testResult;
                runResults.addAll(multiTestRes.convertToExistingTestResult(this.testingRunResult));
            }
        }

        this.testingRunResult.setTestResults(runResults);
        return SUCCESS.toUpperCase();
    }

    private TestingRunIssues runIssues;

    public String fetchIssueFromTestRunResultDetails() {
        ObjectId testingRunResultId = new ObjectId(testingRunResultHexId);
        TestingRunResult result = TestingRunResultDao.instance.findOne(Constants.ID, testingRunResultId);
        try {
            if (result.isVulnerable()) {
                // name = category
                String category = result.getTestSubType();
                TestSourceConfig config = null;
                if (category.startsWith("http")) {
                    config = TestSourceConfigsDao.instance.getTestSourceConfig(result.getTestSubType());
                }
                TestingIssuesId issuesId = new TestingIssuesId(result.getApiInfoKey(),
                        TestErrorSource.AUTOMATED_TESTING,
                        category, config != null ? config.getId() : null);
                if (isTestRunByTestEditor) {
                    issuesId.setTestErrorSource(TestErrorSource.TEST_EDITOR);
                }
                runIssues = TestingRunIssuesDao.instance.findOne(Filters.eq(Constants.ID, issuesId));
            }
        } catch (Exception ignore) {
        }

        return SUCCESS.toUpperCase();
    }

    public String fetchWorkflowTestingRun() {
        Bson filterQ = Filters.and(
                Filters.eq("testingEndpoints.workflowTest._id", workflowTestId),
                Filters.eq("state", TestingRun.State.SCHEDULED));
        this.testingRuns = TestingRunDao.instance.findAll(filterQ);
        return SUCCESS.toUpperCase();
    }

    public String deleteScheduledWorkflowTests() {
        Bson filter = Filters.and(
                Filters.or(
                        Filters.eq(TestingRun.STATE, State.SCHEDULED),
                        Filters.eq(TestingRun.STATE, State.RUNNING)),
                Filters.eq("testingEndpoints.workflowTest._id", workflowTestId));
        Bson update = Updates.set(TestingRun.STATE, State.STOPPED);

        TestingRunDao.instance.getMCollection().updateMany(filter, update);

        return SUCCESS.toUpperCase();
    }

    public String stopAllTests() {
        // stop all the scheduled and running tests
        Bson filter = Filters.or(
                Filters.eq(TestingRun.STATE, State.SCHEDULED),
                Filters.eq(TestingRun.STATE, State.RUNNING));

        TestingRunDao.instance.getMCollection().updateMany(filter, Updates.set(TestingRun.STATE, State.STOPPED));
        testingRuns = TestingRunDao.instance.findAll(filter);

        return SUCCESS.toUpperCase();
    }

    public String stopTest() {
        Bson filter = Filters.or(
                Filters.eq(TestingRun.STATE, State.SCHEDULED),
                Filters.eq(TestingRun.STATE, State.RUNNING));
        if (this.testingRunHexId != null) {
            try {
                ObjectId testingId = new ObjectId(this.testingRunHexId);
                TestingRunDao.instance.updateOne(
                        Filters.and(filter, Filters.eq(Constants.ID, testingId)),
                        Updates.set(TestingRun.STATE, State.STOPPED));
                Bson testingSummaryFilter = Filters.and(
                    Filters.eq(TestingRunResultSummary.TESTING_RUN_ID,testingId),
                    filter
                );
                TestingRunResultSummariesDao.instance.updateManyNoUpsert(testingSummaryFilter, Updates.set(TestingRunResultSummary.STATE, State.STOPPED));
                return SUCCESS.toUpperCase();
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "ERROR: Stop test failed - " + e.toString(), LogDb.DASHBOARD);
            }
        }

        addActionError("Unable to stop test run");
        return ERROR.toLowerCase();
    }

    Map<String, Set<String>> metadataFilters;

    public String fetchMetadataFilters() {

        List<String> filterFields = new ArrayList<>(Arrays.asList("branch", "repository"));
        metadataFilters = TestingRunResultSummariesDao.instance.fetchMetadataFilters(filterFields);

        return SUCCESS.toUpperCase();
    }

    // this gives the count for test runs types{ All, CI/CD, One-time, Scheduled}
    // needed for new ui as the table was server table.
    public String computeAllTestsCountMap(){
        Map<String,Long> result = new HashMap<>();
        ArrayList<Bson> filters = new ArrayList<>();
        filters.addAll(prepareFilters(startTimestamp, endTimestamp));
        
        long totalCount = TestingRunDao.instance.getMCollection().countDocuments(Filters.and(filters));
        
        ArrayList<Bson> filterForCicd = new ArrayList<>(filters); // Create a copy of filters
        filterForCicd.add(getTestingRunTypeFilter(TestingRunType.CI_CD));
        long cicdCount = TestingRunDao.instance.getMCollection().countDocuments(Filters.and(filterForCicd));

        filters.add(getTestingRunTypeFilter(TestingRunType.ONE_TIME));

        long oneTimeCount = TestingRunDao.instance.getMCollection().countDocuments(Filters.and(filters));

        ArrayList<Bson> continuousTestsFilter = new ArrayList<>(); // Create a copy of filters
        continuousTestsFilter.add(getTestingRunTypeFilter(TestingRunType.CONTINUOUS_TESTING));
        continuousTestsFilter.add(Filters.gte(TestingRun.SCHEDULE_TIMESTAMP, startTimestamp));

        long continuousTestsCount = TestingRunDao.instance.getMCollection().countDocuments(Filters.and(continuousTestsFilter));

        System.out.println("cont count" + continuousTestsCount);

        long scheduleCount = totalCount - oneTimeCount - cicdCount - continuousTestsCount;

        
        result.put("allTestRuns", totalCount);
        result.put("oneTime", oneTimeCount);
        result.put("scheduled", scheduleCount);
        result.put("cicd", cicdCount);
        result.put("continuous", continuousTestsCount);

        this.allTestsCountMap = result;
        return SUCCESS.toUpperCase();
    }

    // this gives the count of total vulnerabilites and map of count of subcategories for which issues are generated.
    public String getIssueSummaryInfo(){

        if(this.endTimestamp == 0){
            this.endTimestamp = Context.now();
        }
        // issues default for 2 months
        if(this.startTimestamp == 0){
            this.startTimestamp = Context.now() - (2 * Constants.ONE_MONTH_TIMESTAMP);
        }

        Map<String,Integer> totalSubcategoriesCountMap = TestingRunIssuesDao.instance.getTotalSubcategoriesCountMap(this.startTimestamp,this.endTimestamp);
        this.issuesSummaryInfoMap = totalSubcategoriesCountMap;

        return SUCCESS.toUpperCase();
    }

    private List<String> testRunIds;
    private List<String> latestSummaryIds;

    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    private static void executeDelete(DeleteTestRuns DeleteTestRuns){
        DeleteTestRunsDao.instance.insertOne(DeleteTestRuns);
        int accountId = Context.accountId.get();
        Runnable r = () -> {
            Context.accountId.set(accountId);
            DeleteTestRunUtils.deleteTestRunsFromDb(DeleteTestRuns);
        };
        executorService.submit(r);
    }

    public String deleteTestRunsAction() {
        try {
            List<ObjectId> testRunIdsCopy = new ArrayList<>();
            for (String id : testRunIds) {
                ObjectId objectId = new ObjectId(id);
                testRunIdsCopy.add(objectId);
            }

            List<Integer> testConfigIds = TestingRunDao.instance.getTestConfigIdsToDelete(testRunIdsCopy);
            List<ObjectId> latestSummaryIds = TestingRunDao.instance.getSummaryIdsFromRunIds(testRunIdsCopy);

            DeleteTestRuns DeleteTestRuns = new DeleteTestRuns(testRunIdsCopy, Context.now(), new HashMap<>(),
                    testConfigIds, latestSummaryIds);
            executeDelete(DeleteTestRuns);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    public String deleteTestDataFromSummaryId(){
        try {
            List<ObjectId> summaryIdsCopy = new ArrayList<>();
            for (String id : latestSummaryIds) {
                ObjectId objectId = new ObjectId(id);
                summaryIdsCopy.add(objectId);
            }

            DeleteTestRuns DeleteTestRuns = new DeleteTestRuns(new ArrayList<>(), Context.now(), new HashMap<>(),
                    new ArrayList<>(), summaryIdsCopy);
            executeDelete(DeleteTestRuns);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    private boolean testRunsByUser;

    private boolean getUserTestingRuns(){
        Bson filter = Filters.ne(TestingRun.NAME, "Onboarding demo test");
        return TestingRunDao.instance.getMCollection().find(filter).limit(1).first() != null;
    }

    public String getUsageTestRuns(){
        try {
            this.testRunsByUser = getUserTestingRuns();
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Action.ERROR.toUpperCase();
    }

    private CurrentTestsStatus currentTestsStatus;

    public String getCurrentTestStateStatus(){
        // polling api 
        try {
            List<TestingRunResultSummary> runningTrrs = TestingRunResultSummariesDao.instance.getCurrentRunningTestsSummaries();
            int totalRunningTests = 0;
            int totalInitiatedTests = 0;
            List<StatusForIndividualTest> currentTestsRunningList = new ArrayList<>();
            for(TestingRunResultSummary summary: runningTrrs){
                int countInitiatedTestsForSummary = (summary.getTestInitiatedCount() * summary.getTotalApis()) ;
                totalInitiatedTests += countInitiatedTestsForSummary;

                int currentTestsStoredForSummary = summary.getTestResultsCount();
                totalRunningTests += currentTestsStoredForSummary;
                currentTestsRunningList.add(new StatusForIndividualTest(summary.getTestingRunId().toHexString(), totalInitiatedTests, totalRunningTests));
            }
            int totalTestsQueued = (int) TestingRunDao.instance.count(
                Filters.eq(TestingRun.STATE, State.SCHEDULED)
            );

            this.currentTestsStatus = new CurrentTestsStatus(totalTestsQueued, totalRunningTests, totalInitiatedTests, currentTestsRunningList);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error in getting status for tests", LogDb.DASHBOARD);
            return Action.ERROR.toUpperCase();
        }

        return Action.SUCCESS.toUpperCase();
    }

    public String modifyTestingRunConfig(){
        TestingRunConfigDao.instance.updateOne(
            Filters.eq(Constants.ID, this.testingRunConfigId),
            Updates.set("configsAdvancedSettings", this.testConfigsAdvancedSettings)
        );
        return SUCCESS.toUpperCase();
    }

    public String handleRefreshTableCount(){
        if(this.testingRunResultSummaryHexId == null || this.testingRunResultSummaryHexId.isEmpty()){
            addActionError("Invalid summary id");
            return ERROR.toUpperCase();
        }
        int accountId = Context.accountId.get();
        executorService.schedule( new Runnable() {
            public void run() {
                Context.accountId.set(accountId);
                try {
                    ObjectId summaryObjectId = new ObjectId(testingRunResultSummaryHexId);
                    List<TestingRunResult> testingRunResults = TestingRunResultDao.instance.findAll(
                        Filters.and(
                            Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, summaryObjectId),
                            vulnerableFilter
                        ), 
                        Projections.include(TestingRunResult.API_INFO_KEY, TestingRunResult.TEST_SUB_TYPE)
                    );

                    if(testingRunResults.isEmpty()){
                        return;
                    }
            
                    Set<TestingIssuesId> issuesIds = new HashSet<>();
                    Map<TestingIssuesId, ObjectId> mapIssueToResultId = new HashMap<>();
                    Set<ObjectId> ignoredResults = new HashSet<>();
                    for(TestingRunResult runResult: testingRunResults){
                        TestingIssuesId issuesId = new TestingIssuesId(runResult.getApiInfoKey(), TestErrorSource.AUTOMATED_TESTING , runResult.getTestSubType());
                        issuesIds.add(issuesId);
                        mapIssueToResultId.put(issuesId, runResult.getId());
                        ignoredResults.add(runResult.getId());
                    }
            
                    List<TestingRunIssues> issues = TestingRunIssuesDao.instance.findAll(
                        Filters.and(
                            Filters.in(Constants.ID, issuesIds),
                            Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, GlobalEnums.TestRunIssueStatus.OPEN)
                        ), Projections.include(TestingRunIssues.KEY_SEVERITY)
                    );
            
                    Map<String, Integer> totalCountIssues = new HashMap<>();
                    totalCountIssues.put("HIGH", 0);
                    totalCountIssues.put("MEDIUM", 0);
                    totalCountIssues.put("LOW", 0);
            
                    for(TestingRunIssues runIssue: issues){
                        int initCount = totalCountIssues.getOrDefault(runIssue.getSeverity().name(), 0);
                        totalCountIssues.put(runIssue.getSeverity().name(), initCount + 1);
                        if(mapIssueToResultId.containsKey(runIssue.getId())){
                            ObjectId resId = mapIssueToResultId.get(runIssue.getId());
                            ignoredResults.remove(resId);
                        }
                    }
            
                    // update testing run result summary
                    TestingRunResultSummariesDao.instance.updateOne(
                        Filters.eq(Constants.ID, summaryObjectId),
                        Updates.set(TestingRunResultSummary.COUNT_ISSUES, totalCountIssues)
                    );
            
                    // update testing run results, by setting them isIgnored true
                    TestingRunResultDao.instance.updateMany(
                        Filters.in(Constants.ID, ignoredResults),
                        Updates.set(TestingRunResult.IS_IGNORED_RESULT, true)
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0 , TimeUnit.SECONDS);

        return SUCCESS.toUpperCase();
    }


    public void setType(TestingEndpoints.Type type) {
        this.type = type;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public void setApiInfoKeyList(List<ApiInfo.ApiInfoKey> apiInfoKeyList) {
        this.apiInfoKeyList = apiInfoKeyList;
    }

    public List<TestingRun> getTestingRuns() {
        return testingRuns;
    }

    public AuthMechanism getAuthMechanism() {
        return this.authMechanism;
    }

    public void setAuthMechanism(AuthMechanism authMechanism) {
        this.authMechanism = authMechanism;
    }

    public int getStartTimestamp() {
        return this.startTimestamp;
    }

    public void setStartTimestamp(int startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public int getEndTimestamp() {
        return this.endTimestamp;
    }

    public void setEndTimestamp(int endTimestamp) {
        this.endTimestamp = endTimestamp;
    }

    public boolean getRecurringDaily() {
        return this.recurringDaily;
    }

    public void setRecurringDaily(boolean recurringDaily) {
        this.recurringDaily = recurringDaily;
    }

    public void setTestIdConfig(int testIdConfig) {
        this.testIdConfig = testIdConfig;
    }

    public void setWorkflowTestId(int workflowTestId) {
        this.workflowTestId = workflowTestId;
    }

    public void setTestingRunHexId(String testingRunHexId) {
        this.testingRunHexId = testingRunHexId;
    }

    public String getTestingRunHexId() {
        return testingRunHexId;
    }

    public List<TestingRunResultSummary> getTestingRunResultSummaries() {
        return this.testingRunResultSummaries;
    }

    public String getTestingRunResultSummaryHexId() {
        return this.testingRunResultSummaryHexId;
    }

    public void setTestingRunResultSummaryHexId(String testingRunResultSummaryHexId) {
        this.testingRunResultSummaryHexId = testingRunResultSummaryHexId;
    }

    public List<TestingRunResult> getTestingRunResults() {
        return this.testingRunResults;
    }

    public void setTestingRunResultHexId(String testingRunResultHexId) {
        this.testingRunResultHexId = testingRunResultHexId;
    }

    public TestingRunResult getTestingRunResult() {
        return testingRunResult;
    }

    public TestingRun getTestingRun() {
        return testingRun;
    }

    public WorkflowTest getWorkflowTest() {
        return workflowTest;
    }

    public TestingRunIssues getRunIssues() {
        return runIssues;
    }

    public void setRunIssues(TestingRunIssues runIssues) {
        this.runIssues = runIssues;
    }

    public List<String> getSelectedTests() {
        return selectedTests;
    }

    public void setSelectedTests(List<String> selectedTests) {
        this.selectedTests = selectedTests;
    }

    public String getTestName() {
        return this.testName;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }

    public int getTestRunTime() {
        return testRunTime;
    }

    public void setTestRunTime(int testRunTime) {
        this.testRunTime = testRunTime;
    }

    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }

    public void setMaxConcurrentRequests(int maxConcurrentRequests) {
        this.maxConcurrentRequests = maxConcurrentRequests;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public CallSource getSource() {
        return this.source;
    }

    public void setSource(CallSource source) {
        this.source = source;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public void setTriggeredBy(String triggeredBy) {
        this.triggeredBy = triggeredBy;
    }

    public boolean isTestRunByTestEditor() {
        return isTestRunByTestEditor;
    }

    public void setIsTestRunByTestEditor(boolean testRunByTestEditor) {
        isTestRunByTestEditor = testRunByTestEditor;
    }

    public void setOverriddenTestAppUrl(String overriddenTestAppUrl) {
        this.overriddenTestAppUrl = overriddenTestAppUrl;
    }

    public String getOverriddenTestAppUrl() {
        return overriddenTestAppUrl;
    }

    public String getSortKey() {
        return sortKey;
    }

    public void setSortKey(String sortKey) {
        this.sortKey = sortKey;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getSkip() {
        return skip;
    }

    public void setSkip(int skip) {
        this.skip = skip;
    }

    public Map<String, List> getFilters() {
        return filters;
    }

    public void setFilters(Map<String, List> filters) {
        this.filters = filters;
    }

    public long getTestingRunsCount() {
        return testingRunsCount;
    }

    public void setTestingRunsCount(long testingRunsCount) {
        this.testingRunsCount = testingRunsCount;
    }

    public Map<ObjectId, TestingRunResultSummary> getLatestTestingRunResultSummaries() {
        return latestTestingRunResultSummaries;
    }

    public void setLatestTestingRunResultSummaries(
            Map<ObjectId, TestingRunResultSummary> latestTestingRunResultSummaries) {
        this.latestTestingRunResultSummaries = latestTestingRunResultSummaries;
    }

    public Map<String, String> getSampleDataVsCurlMap() {
        return sampleDataVsCurlMap;
    }

    public void setSampleDataVsCurlMap(Map<String, String> sampleDataVsCurlMap) {
        this.sampleDataVsCurlMap = sampleDataVsCurlMap;
    }

    public void setFetchOnlyVulnerable(boolean fetchOnlyVulnerable) {
        this.fetchOnlyVulnerable = fetchOnlyVulnerable;
    }

    public void setQueryMode(QueryMode queryMode) {
        this.queryMode = queryMode;
    }

    public Map<String, Set<String>> getMetadataFilters() {
        return metadataFilters;
    }

    public TestingRunType getTestingRunType() {
        return testingRunType;
    }

    public void setTestingRunType(TestingRunType testingRunType) {
        this.testingRunType = testingRunType;
    }
    
    public Map<String, Integer> getIssuesSummaryInfoMap() {
        return issuesSummaryInfoMap;
    }

    public Map<String, Long> getAllTestsCountMap() {
        return allTestsCountMap;
    }

    public List<String> getTestRunIds() {
        return testRunIds;
    }

    public void setTestRunIds(List<String> testRunIds) {
        this.testRunIds = testRunIds;
    }

    public List<String> getLatestSummaryIds() {
        return latestSummaryIds;
    }

    public void setLatestSummaryIds(List<String> latestSummaryIds) {
        this.latestSummaryIds = latestSummaryIds;
    }

    public boolean getTestRunsByUser() {
        return testRunsByUser;
    }

    public void setSearchString(String searchString) {
        this.searchString = searchString;
    }


    public enum CallSource {
        TESTING_UI,
        AKTO_GPT;
        public static CallSource getCallSource(String source) {
            if (source == null) {
                return TESTING_UI;
            }
            for (CallSource callSource : CallSource.values()) {
                if (callSource.name().equalsIgnoreCase(source)) {
                    return callSource;
                }
            }
            return null;
        }

        public boolean isCallFromAktoGpt() {
            return AKTO_GPT.equals(this);
        }
    }

    public String getTestRoleId() {
        return testRoleId;
    }

    public void setTestRoleId(String testRoleId) {
        this.testRoleId = testRoleId;
    }

    public CurrentTestsStatus getCurrentTestsStatus() {
        return currentTestsStatus;
    }

    public Map<TestError, String> getErrorEnums() {
        return errorEnums;
    }

    public boolean getContinuousTesting() {
        return continuousTesting;
    }

    public void setContinuousTesting(boolean continuousTesting) {
        this.continuousTesting = continuousTesting;
    }

    public void setSendSlackAlert(boolean sendSlackAlert) {
        this.sendSlackAlert = sendSlackAlert;
    }

    public void setTestConfigsAdvancedSettings(List<TestConfigsAdvancedSettings> testConfigsAdvancedSettings) {
        this.testConfigsAdvancedSettings = testConfigsAdvancedSettings;
    }

    public void setTestingRunConfigId(int testingRunConfigId) {
        this.testingRunConfigId = testingRunConfigId;
    }

    public Map<String, Integer> getTestCountMap() {
        return testCountMap;
    }

    public void setReportFilterList(Map<String, List<String>> reportFilterList) {
        this.reportFilterList = reportFilterList;
    }

    public List<TestingRunIssues> getIssueslist() {
        return issueslist;
    }
    
    public boolean getCleanUpTestingResources() {
        return cleanUpTestingResources;
    }

    public void setCleanUpTestingResources(boolean cleanUpTestingResources) {
        this.cleanUpTestingResources = cleanUpTestingResources;
    }
}
