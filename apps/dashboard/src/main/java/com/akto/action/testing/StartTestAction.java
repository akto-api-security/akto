package com.akto.action.testing;

import com.akto.DaoInit;
import com.akto.action.ExportSampleDataAction;
import com.akto.action.UserAction;
import com.akto.dao.AuthMechanismsDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dao.testing.WorkflowTestsDao;
import com.akto.dao.testing.sources.TestSourceConfigsDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dao.testing.*;
import com.akto.dto.ApiInfo;
import com.akto.dto.User;
import com.akto.dto.ApiToken.Utility;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.*;
import com.akto.dto.testing.TestingRun.State;
import com.akto.dto.testing.sources.TestSourceConfig;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.TestErrorSource;
import com.akto.utils.Utils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private Map<String,String> metadata;
    private boolean fetchCicd;
    private String triggeredBy;
    private boolean isTestRunByTestEditor;
    private Map<ObjectId,TestingRunResultSummary> latestTestingRunResultSummaries;
    private Map<String, String> sampleDataVsCurlMap;
    private String overriddenTestAppUrl;
    private static final LoggerMaker loggerMaker = new LoggerMaker(StartTestAction.class);
    private boolean fetchAllTestRuns;

    private Map<String,Long> allTestsCountMap = new HashMap<>();
    private Map<String, Map<String,Integer>> issuesSummaryInfoMap = new HashMap<>();

    private static List<ObjectId> getTestingRunListFromSummary(Bson filters){
        Bson projections = Projections.fields(
                Projections.excludeId(),
                Projections.include(TestingRunResultSummary.TESTING_RUN_ID));
        return TestingRunResultSummariesDao.instance.findAll(
                filters, projections).stream().map(summary -> summary.getTestingRunId())
                .collect(Collectors.toList());
    }

    private static List<ObjectId> getCicdTests(){
        return getTestingRunListFromSummary(Filters.exists(TestingRunResultSummary.METADATA_STRING));
    }

    private static List<ObjectId> getTestsWithSeverity(List<String> severities){

        List<Bson> severityFilters = new ArrayList<>();
        for(String severity : severities){
            severityFilters.add(Filters.gt(TestingRunResultSummary.COUNT_ISSUES + "." + severity, 0));
        }

        return getTestingRunListFromSummary(Filters.or(severityFilters));
    }

    private CallSource source;

    private TestingRun createTestingRun(int scheduleTimestamp, int periodInSeconds) {
        User user = getSUser();

        if (!StringUtils.isEmpty(this.overriddenTestAppUrl)) {
            boolean isValidUrl = Utils.isValidURL(this.overriddenTestAppUrl);

            if (!isValidUrl) {
                addActionError("The override url is invalid. Please check the url again.");
                return null;
            }
        }

        AuthMechanism authMechanism = AuthMechanismsDao.instance.findOne(new BasicDBObject());
        if (authMechanism == null && testIdConfig == 0) {
            addActionError("Please set authentication mechanism before you test any APIs");
            return null;
        }

        TestingEndpoints testingEndpoints;
        switch (type) {
            case CUSTOM:
                if (this.apiInfoKeyList == null || this.apiInfoKeyList.isEmpty())  {
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
            TestingRunConfig testingRunConfig = new TestingRunConfig(Context.now(), null, this.selectedTests,authMechanism.getId(), this.overriddenTestAppUrl);
            this.testIdConfig = testingRunConfig.getId();
            TestingRunConfigDao.instance.insertOne(testingRunConfig);
        }

        return new TestingRun(scheduleTimestamp, user.getLogin(),
                testingEndpoints, testIdConfig, State.SCHEDULED, periodInSeconds, testName, this.testRunTime, this.maxConcurrentRequests);
    }
    private List<String> selectedTests;

    public String startTest() {

        if(this.startTimestamp != 0 && this.startTimestamp + 86400 < Context.now()) {
            addActionError("Cannot schedule a test run in the past.");
            return ERROR.toUpperCase();
        }

        int scheduleTimestamp = this.startTimestamp == 0 ? Context.now()  : this.startTimestamp;
        handleCallFromAktoGpt();

        TestingRun localTestingRun = null;
        if(this.testingRunHexId!=null){
            try{
                ObjectId testingId = new ObjectId(this.testingRunHexId);
                localTestingRun = TestingRunDao.instance.findOne(Constants.ID,testingId);
            } catch (Exception e){
                e.printStackTrace();
                loggerMaker.errorAndAddToDb(e.toString(), LogDb.DASHBOARD);
            }
        }
        if(localTestingRun==null){
            try {
                localTestingRun = createTestingRun(scheduleTimestamp, this.recurringDaily ? 86400 : 0);
                if (triggeredBy.length() > 0) {
                    localTestingRun.setTriggeredBy(triggeredBy);
                }
            } catch (Exception e){
                loggerMaker.errorAndAddToDb(e.toString(), LogDb.DASHBOARD);
            }

            if (localTestingRun == null) {
                return ERROR.toUpperCase();
            } else {
                TestingRunDao.instance.insertOne(localTestingRun);
                testingRunHexId = localTestingRun.getId().toHexString();
            }
        } else {
            TestingRunDao.instance.updateOne(
                Filters.eq(Constants.ID,localTestingRun.getId()),
                Updates.combine(
                    Updates.set(TestingRun.STATE,TestingRun.State.SCHEDULED),
                    Updates.set(TestingRun.SCHEDULE_TIMESTAMP,scheduleTimestamp)
                ));
        }

        Map<String, Object> session = getSession();
        String utility = (String) session.get("utility");

        if(utility!=null && ( Utility.CICD.toString().equals(utility) || Utility.EXTERNAL_API.toString().equals(utility))){
            TestingRunResultSummary summary = new TestingRunResultSummary(scheduleTimestamp, 0, new HashMap<>(),
            0, localTestingRun.getId(), localTestingRun.getId().toHexString(), 0);
            summary.setState(TestingRun.State.SCHEDULED);
            if(metadata!=null){
                loggerMaker.infoAndAddToDb("CICD test triggered at " + Context.now(), LogDb.DASHBOARD);
                summary.setMetadata(metadata);
            }
            TestingRunResultSummariesDao.instance.insertOne(summary);
        }
        
        this.startTimestamp = 0;
        this.endTimestamp = 0;
        this.retrieveAllCollectionTests();
        return SUCCESS.toUpperCase();
    }

    private void handleCallFromAktoGpt() {
        if(this.source == null){
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
                    loggerMaker.infoAndAddToDb(String.format("Category: %s, tests: %s", selectedTest, testSubCategories), LoggerMaker.LogDb.DASHBOARD);
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

    private ArrayList<Bson> prepareFilters() {
        ArrayList<Bson> filterList = new ArrayList<>();

        if(filters==null){
            return filterList;
        }

        try {
            for (Map.Entry<String, List> entry : filters.entrySet()) {
                String key = entry.getKey();
                List value = entry.getValue();

                if (value == null || value.isEmpty())
                    continue;

                switch(key){

                    case "endTimestamp":
                        List<Long> ll = Utils.castList(Long.class, value);
                        filterList.add(Filters.gte(key, ll.get(0)));
                        filterList.add(Filters.lte(key, ll.get(1)));
                    break;
                    
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
        if(sortKey==null || "".equals(sortKey)){
            sortKey = TestingRun.SCHEDULE_TIMESTAMP;
        }
        sortFields.add(sortKey);

        return sortOrder == 1 ? Sorts.ascending(sortFields) : Sorts.descending(sortFields);
    }

    public String retrieveAllCollectionTests() {
        if (this.startTimestamp == 0) {
            this.startTimestamp = Context.now();
        }

        if (this.endTimestamp == 0) {
            this.endTimestamp = Context.now() + 86400;
        }

        this.authMechanism = AuthMechanismsDao.instance.findOne(new BasicDBObject());

        ArrayList<Bson> testingRunFilters = new ArrayList<>();

        if(fetchCicd){
            // filters for test runs to be only CI/CD pipeline
            testingRunFilters.add(Filters.in(Constants.ID, getCicdTests()));
        } else if(fetchAllTestRuns){
            // get All test runs which are not run by test editor
            testingRunFilters.add(Filters.ne("triggeredBy", "test_editor"));
        } else {
            // the left test are the scheduled one
            Collections.addAll(testingRunFilters, 
                Filters.lte(TestingRun.SCHEDULE_TIMESTAMP, this.endTimestamp),
                Filters.gte(TestingRun.SCHEDULE_TIMESTAMP, this.startTimestamp),
                Filters.nin(Constants.ID,getCicdTests()),
                Filters.ne("triggeredBy", "test_editor")
            );
        }

        testingRunFilters.addAll(prepareFilters());

        int pageLimit = Math.min(limit == 0 ? 50 : limit, 10_000);

        testingRuns = TestingRunDao.instance.findAll(
                Filters.and(testingRunFilters), skip, pageLimit,
                prepareSort());

        List<ObjectId> testingRunHexIds = new ArrayList<>();
        testingRuns.forEach(
            localTestingRun -> {
                testingRunHexIds.add(localTestingRun.getId());
            }
        );

        latestTestingRunResultSummaries = TestingRunResultSummariesDao.instance.fetchLatestTestingRunResultSummaries(testingRunHexIds);

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

        if(this.startTimestamp!=0){
            filterQ.add(Filters.gte(TestingRunResultSummary.START_TIMESTAMP, this.startTimestamp));
        }

        if(this.endTimestamp!=0){
            filterQ.add(Filters.lte(TestingRunResultSummary.START_TIMESTAMP, this.endTimestamp));
        }

        Bson sort = Sorts.descending(TestingRunResultSummary.START_TIMESTAMP) ;

        this.testingRunResultSummaries = TestingRunResultSummariesDao.instance.findAll(Filters.and(filterQ), 0, limitForTestingRunResultSummary , sort);
        this.testingRun = TestingRunDao.instance.findOne(Filters.eq("_id", testingRunId));

        if (this.testingRun!=null && this.testingRun.getTestIdConfig() == 1) {
            WorkflowTestingEndpoints workflowTestingEndpoints = (WorkflowTestingEndpoints) testingRun.getTestingEndpoints();
            this.workflowTest = WorkflowTestsDao.instance.findOne(Filters.eq("_id", workflowTestingEndpoints.getWorkflowTest().getId()));
        }

        return SUCCESS.toUpperCase();
    }

    String testingRunResultSummaryHexId;
    List<TestingRunResult> testingRunResults;
    private boolean fetchOnlyVulnerable;

    public String fetchTestingRunResults() {
        ObjectId testingRunResultSummaryId;
        try {
            testingRunResultSummaryId= new ObjectId(testingRunResultSummaryHexId);
        } catch (Exception e) {
            addActionError("Invalid test summary id");
            return ERROR.toUpperCase();
        }
        
        List<Bson> testingRunResultFilters = new ArrayList<>();

        if(fetchOnlyVulnerable){
            testingRunResultFilters.add(Filters.eq(TestingRunResult.VULNERABLE, true));
        }

        testingRunResultFilters.add(Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, testingRunResultSummaryId));

        this.testingRunResults = TestingRunResultDao.instance.fetchLatestTestingRunResult(Filters.and(testingRunResultFilters));

        return SUCCESS.toUpperCase();
    }

    public String fetchVulnerableTestRunResults() {
        ObjectId testingRunResultSummaryId;
        try {
            testingRunResultSummaryId= new ObjectId(testingRunResultSummaryHexId);
            Bson filters = Filters.and(
                    Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, testingRunResultSummaryId),
                    Filters.eq(TestingRunResult.VULNERABLE, true)
            );
            List<TestingRunResult> testingRunResultList = TestingRunResultDao.instance.findAll(filters, skip, 50, null);
            Map<String, String> sampleDataVsCurlMap = new HashMap<>();
            for (TestingRunResult runResult: testingRunResultList) {
                List<TestResult> testResults = new ArrayList<>();
                for (TestResult testResult : runResult.getTestResults()) {
                    if (testResult.isVulnerable()) {
                        testResults.add(testResult);
                        sampleDataVsCurlMap.put(testResult.getMessage(), ExportSampleDataAction.getCurl(testResult.getMessage()));
                        sampleDataVsCurlMap.put(testResult.getOriginalMessage(), ExportSampleDataAction.getCurl(testResult.getOriginalMessage()));
                    }
                }
                runResult.setTestResults(testResults);
            }
            this.testingRunResults = testingRunResultList;
            this.sampleDataVsCurlMap = sampleDataVsCurlMap;
        } catch (Exception e) {
            addActionError("Invalid test summary id");
            return ERROR.toUpperCase();
        }


        return SUCCESS.toUpperCase();
    }

    private String testingRunResultHexId;
    private TestingRunResult testingRunResult;
    public String fetchTestRunResultDetails() {
        ObjectId testingRunResultId = new ObjectId(testingRunResultHexId);
        this.testingRunResult = TestingRunResultDao.instance.findOne("_id", testingRunResultId);
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
                // string comparison (nuclei test)
                if (category.startsWith("http")) {
                    config = TestSourceConfigsDao.instance.getTestSourceConfig(result.getTestSubType());
                }
                TestingIssuesId issuesId = new TestingIssuesId(result.getApiInfoKey(), TestErrorSource.AUTOMATED_TESTING,
                        category, config != null ? config.getId() : null);
                if (isTestRunByTestEditor) {
                    issuesId.setTestErrorSource(TestErrorSource.TEST_EDITOR);
                }
                runIssues = TestingRunIssuesDao.instance.findOne(Filters.eq(Constants.ID, issuesId));
            }
        }catch (Exception ignore) {}

        return SUCCESS.toUpperCase();
    }

    public String fetchWorkflowTestingRun() {
        Bson filterQ = Filters.and(
            Filters.eq("testingEndpoints.workflowTest._id", workflowTestId),
            Filters.eq("state", TestingRun.State.SCHEDULED)
        );
        this.testingRuns = TestingRunDao.instance.findAll(filterQ);
        return SUCCESS.toUpperCase();
    }

    public String deleteScheduledWorkflowTests() {
        Bson filter = Filters.and(
                Filters.or(
                        Filters.eq(TestingRun.STATE, State.SCHEDULED),
                        Filters.eq(TestingRun.STATE, State.RUNNING)
                ),
                Filters.eq("testingEndpoints.workflowTest._id", workflowTestId)
        );
        Bson update = Updates.set(TestingRun.STATE, State.STOPPED);

        TestingRunDao.instance.getMCollection().updateMany(filter, update);

        return SUCCESS.toUpperCase();
    }

    public String stopAllTests() {
        // stop all the scheduled and running tests
        Bson filter = Filters.or(
            Filters.eq(TestingRun.STATE, State.SCHEDULED),
            Filters.eq(TestingRun.STATE, State.RUNNING)
        );

        TestingRunDao.instance.getMCollection().updateMany(filter,Updates.set(TestingRun.STATE, State.STOPPED));
        testingRuns = TestingRunDao.instance.findAll(filter);

        return SUCCESS.toUpperCase();
    }

    public String stopTest() {
        // stop only scheduled and running tests
        Bson filter = Filters.or(
                Filters.eq(TestingRun.STATE, State.SCHEDULED),
                Filters.eq(TestingRun.STATE, State.RUNNING));
        if (this.testingRunHexId != null) {
            try {
                ObjectId testingId = new ObjectId(this.testingRunHexId);
                TestingRunDao.instance.updateOne(
                        Filters.and(filter, Filters.eq(Constants.ID, testingId)),
                        Updates.set(TestingRun.STATE, State.STOPPED));
                return SUCCESS.toUpperCase();
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e.toString(), LogDb.DASHBOARD);
            }
        }

        addActionError("Unable to stop test run");
        return ERROR.toLowerCase();
    }

    Map<String, Set<String>> metadataFilters; 

    public String fetchMetadataFilters(){

        List<String> filterFields = new ArrayList<>(Arrays.asList("branch", "repository"));
        metadataFilters = TestingRunResultSummariesDao.instance.fetchMetadataFilters(filterFields);
        
        return SUCCESS.toUpperCase();
    }

    // this gives the count for test runs types{ All, CI/CD, One-time, Scheduled}
    // needed for new ui as the table was server table.
    public String computeAllTestsCountMap(){
        Map<String,Long> result = new HashMap<>();

        long totalCount = TestingRunDao.instance.getMCollection().countDocuments();
        List<Bson> filtersForCiCd = new ArrayList<>();
        filtersForCiCd.add(Filters.in(Constants.ID, getCicdTests()));
        long cicdCount = TestingRunDao.instance.getMCollection().countDocuments(Filters.and(filtersForCiCd));

        int startTime = Context.now();
        int endTime = Context.now() + 86400;
        List<Bson> filtersForSchedule = new ArrayList<>();
        Collections.addAll(filtersForSchedule,
                Filters.lte(TestingRun.SCHEDULE_TIMESTAMP, endTime),
                Filters.gte(TestingRun.SCHEDULE_TIMESTAMP, startTime),
                Filters.nin(Constants.ID,getCicdTests())
        );
        long scheduleCount =  TestingRunDao.instance.getMCollection().countDocuments(Filters.and(filtersForSchedule));

        long oneTimeCount = totalCount - cicdCount - scheduleCount;
        result.put("allTestRuns", totalCount);
        result.put("oneTime", oneTimeCount);
        result.put("scheduled", scheduleCount);
        result.put("cicd", cicdCount);

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

        Map<String,Integer> totalSeveritiesCountMap = TestingRunIssuesDao.instance.getTotalSeveritiesCountMap(this.startTimestamp,this.endTimestamp);
        Map<String,Integer> totalSubcategoriesCountMap = TestingRunIssuesDao.instance.getTotalSubcategoriesCountMap(this.startTimestamp,this.endTimestamp);

        Map<String,Map<String,Integer>> result = new HashMap<>();
        result.put("subcategoryInfo", totalSubcategoriesCountMap);
        result.put("severityInfo", totalSeveritiesCountMap);

        this.issuesSummaryInfoMap = result;

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

    public boolean isFetchCicd() {
        return fetchCicd;
    }

    public void setFetchCicd(boolean fetchCicd) {
        this.fetchCicd = fetchCicd;
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

    public Map<String, Set<String>> getMetadataFilters() {
        return metadataFilters;
    }

    public boolean isFetchAllTestRuns() {
        return fetchAllTestRuns;
    }

    public void setFetchAllTestRuns(boolean fetchAllTestRuns) {
        this.fetchAllTestRuns = fetchAllTestRuns;
    }
    
    public Map<String, Map<String, Integer>> getIssuesSummaryInfoMap() {
        return issuesSummaryInfoMap;
    }

    public Map<String, Long> getAllTestsCountMap() {
        return allTestsCountMap;
    }

    public enum CallSource{
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
        public boolean isCallFromAktoGpt(){
            return AKTO_GPT.equals(this);
        }
    }
}
