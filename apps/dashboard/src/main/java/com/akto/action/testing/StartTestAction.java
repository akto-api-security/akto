package com.akto.action.testing;

import com.akto.action.UserAction;
import com.akto.billing.UsageMetricUtils;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dao.notifications.SlackWebhooksDao;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.*;
import com.akto.utils.CategoryWiseStatsUtils;
import com.akto.dao.testing.sources.TestSourceConfigsDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiToken.Utility;
import com.akto.dto.CollectionConditions.TestConfigsAdvancedSettings;
import com.akto.dto.User;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.notifications.SlackWebhook;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.*;
import com.akto.dto.testing.TestResult.TestError;
import com.akto.dto.testing.TestingRun.State;
import com.akto.dto.testing.TestingRun.TestingRunType;
import com.akto.dto.testing.config.EditableTestingRunConfig;
import com.akto.dto.testing.info.CurrentTestsStatus;
import com.akto.dto.testing.info.CurrentTestsStatus.StatusForIndividualTest;
import com.akto.dto.testing.sources.TestSourceConfig;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.notifications.slack.CustomTextAlert;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.Constants;
import com.akto.util.GroupByTimeRange;
import com.akto.util.enums.GlobalEnums;
import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.util.enums.GlobalEnums.TestErrorSource;
import com.akto.utils.ApiInfoKeyResult;
import com.akto.util.enums.GlobalEnums.TestRunIssueStatus;
import com.akto.utils.DeleteTestRunUtils;
import com.akto.utils.Utils;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.*;
import com.mongodb.client.result.InsertOneResult;
import com.opensymphony.xwork2.Action;
import com.slack.api.Slack;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.akto.action.testing.Utils.buildIssueMetaDataMap;

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
    boolean recurringWeekly;
    boolean recurringMonthly;
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
    private List<String> testSuiteIds;

    private Map<String,Long> allTestsCountMap = new HashMap<>();
    private Map<String,Integer> issuesSummaryInfoMap = new HashMap<>();
    
    @Getter
    private List<Map<String, Object>> categoryWiseScores = new ArrayList<>();
    
    private String dashboardCategory;
    private String dataSource = "redteaming"; // Default to redteaming
    
    public void setDashboardCategory(String dashboardCategory) {
        this.dashboardCategory = dashboardCategory;
    }
    
    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }

    private String testRoleId;
    private boolean cleanUpTestingResources;

    private AutoTicketingDetails autoTicketingDetails;

    private Map<String, String> issuesDescriptionMap;

    private static final Gson gson = new Gson();

    @Getter
    int misConfiguredTestsCount;

    Set<Integer> deactivatedCollections = UsageMetricCalculator.getDeactivated();

    @Setter
    private boolean showApiInfo;
    @Getter
    private List<ApiInfo> misConfiguredTestsApiInfo = new ArrayList<>();

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
    private boolean sendMsTeamsAlert = false;

    private TestingRun createTestingRun(int scheduleTimestamp, int periodInSeconds, String miniTestingServiceName, int selectedSlackChannelId) {
        User user = getSUser();

        if (!StringUtils.isEmpty(this.overriddenTestAppUrl)) {
            boolean isValidUrl = Utils.isValidURL(this.overriddenTestAppUrl);

            if (!isValidUrl) {
                addActionError("The override url is invalid. Please check the url again.");
                return null;
            }
        }

        AuthMechanism authMechanism = TestRolesDao.instance.fetchAttackerToken(null);
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
            TestingRunConfig testingRunConfig = new TestingRunConfig(id, null, this.selectedTests,
                authMechanism.getId(), this.overriddenTestAppUrl, this.testRoleId, this.cleanUpTestingResources,
                this.autoTicketingDetails);
            // add advanced setting here
            if(this.testConfigsAdvancedSettings != null && !this.testConfigsAdvancedSettings.isEmpty()){
                testingRunConfig.setConfigsAdvancedSettings(this.testConfigsAdvancedSettings);
            }
            List<String> testSuiteIdsObj = new ArrayList<>(testSuiteIds);
            testingRunConfig.setTestSuiteIds(testSuiteIdsObj);
            this.testIdConfig = testingRunConfig.getId();
            TestingRunConfigDao.instance.insertOne(testingRunConfig);
        }

        return new TestingRun(scheduleTimestamp, user.getLogin(),
                testingEndpoints, testIdConfig, State.SCHEDULED, periodInSeconds, testName, this.testRunTime,
                this.maxConcurrentRequests, this.sendSlackAlert, this.sendMsTeamsAlert, miniTestingServiceName,selectedSlackChannelId);
    }

    String selectedMiniTestingServiceName;
    int selectedSlackWebhook;
    private List<String> selectedTests;
    private List<TestConfigsAdvancedSettings> testConfigsAdvancedSettings;

    private List<String> selectedTestRunResultHexIds;

    private int getPeriodInSeconds(boolean recurringDaily, boolean recurringWeekly, boolean recurringMonthly) {
        if(recurringDaily){
            return Constants.ONE_DAY_TIMESTAMP;
        } else if(recurringWeekly){
            return 7 * Constants.ONE_DAY_TIMESTAMP;
        } else if(recurringMonthly){
            return Constants.ONE_MONTH_TIMESTAMP;
        } else {
            return 0;
        }
    }

    private static final Slack SLACK_INSTANCE = Slack.getInstance();

    public String startTest() {

        if (this.startTimestamp != 0 && this.startTimestamp + 86400 < Context.now()) {
            addActionError("Cannot schedule a test run in the past.");
            return ERROR.toUpperCase();
        }

        if (!validateAutoTicketingDetails(this.autoTicketingDetails)) {
            return Action.ERROR.toUpperCase();
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
                localTestingRun = createTestingRun(scheduleTimestamp, getPeriodInSeconds(recurringDaily, recurringWeekly, recurringMonthly), selectedMiniTestingServiceName, selectedSlackWebhook);
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
                if (selectedTestRunResultHexIds == null || selectedTestRunResultHexIds.isEmpty()) {
                    TestingRunDao.instance.updateOne(
                            Filters.eq(Constants.ID, localTestingRun.getId()),
                            Updates.combine(
                                    Updates.set(TestingRun.STATE, TestingRun.State.SCHEDULED),
                                    Updates.set(TestingRun.SCHEDULE_TIMESTAMP, scheduleTimestamp)));

                } else {

                    if (this.testingRunResultSummaryHexId != null) {
                        List<ObjectId> testingRunResultIds = new ArrayList<>();
                        for (String testingRunResultHexId : selectedTestRunResultHexIds) {
                            testingRunResultIds.add(new ObjectId(testingRunResultHexId));
                        }

                        TestingRunResultDao.instance.updateManyNoUpsert(Filters.in(TestingRunResultDao.ID, testingRunResultIds),
                                Updates.set(TestingRunResult.RERUN,true));

                        TestingRunResultSummary summary = new TestingRunResultSummary(Context.now(), 0, new HashMap<>(),
                                0, localTestingRun.getId(), localTestingRun.getId().toHexString(), 0, localTestingRun.getTestIdConfig(),0 );
                        summary.setState(TestingRun.State.SCHEDULED);
                        summary.setOriginalTestingRunResultSummaryId(new ObjectId(testingRunResultSummaryHexId));
                        loggerMaker.debugAndAddToDb("Rerun test triggered at " + Context.now(), LogDb.DASHBOARD);

                        InsertOneResult result = TestingRunResultSummariesDao.instance.insertOne(summary);
                        this.testingRunResultSummaryHexId = result.getInsertedId().asObjectId().getValue().toHexString();
                    }
                }

            } else {
                // CI-CD test.
                /*
                 * If test is already running or scheduled, do nothing.
                 * If test is stopped/failed, mark it as completed.
                 */
                if(localTestingRun.getState().equals(TestingRun.State.FAILED) || localTestingRun.getState().equals(TestingRun.State.STOPPED)){
                    TestingRunDao.instance.updateOne(
                    Filters.eq(Constants.ID, localTestingRun.getId()),
                    Updates.combine(
                            Updates.set(TestingRun.STATE, TestingRun.State.COMPLETED),
                            Updates.set(TestingRun.END_TIMESTAMP, Context.now())));
                }

            }

            if (this.overriddenTestAppUrl != null || this.selectedTests != null) {
                int id = UUID.randomUUID().hashCode() & 0xfffffff;
                TestingRunConfig testingRunConfig = new TestingRunConfig(id, null, this.selectedTests, null, this.overriddenTestAppUrl, this.testRoleId);

                List<String> testSuiteIdsObj = new ArrayList<>(testSuiteIds);
                testingRunConfig.setTestSuiteIds(testSuiteIdsObj);
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
                loggerMaker.debugAndAddToDb("CICD test triggered at " + Context.now(), LogDb.DASHBOARD);
                summary.setMetadata(metadata);
            }
            InsertOneResult result = TestingRunResultSummariesDao.instance.insertOne(summary);
            this.testingRunResultSummaryHexId = result.getInsertedId().asObjectId().getValue().toHexString();

        }

        try {
            // if (DashboardMode.isSaasDeployment() && !com.akto.testing.Utils.isTestingRunForDemoCollection(localTestingRun)) {
            //     int accountId = Context.accountId.get();
            //     User user = AccountAction.addUserToExistingAccount("arjun@akto.io", accountId);
            //     if (user != null) {

            //         RBACDao.instance.updateOneNoUpsert(
            //             Filters.and(
            //                 Filters.eq(RBAC.USER_ID, user.getId()),
            //                 Filters.eq(RBAC.ACCOUNT_ID, accountId)
            //             ),
            //             Updates.combine(
            //                 Updates.set(RBAC.USER_ID, user.getId()),
            //                 Updates.set(RBAC.ACCOUNT_ID, accountId),
            //                 Updates.set(RBAC.ROLE, Role.DEVELOPER)
            //             )
            //         );
            //     }
            // }
        } catch (Exception e) {
            e.printStackTrace();
            loggerMaker.errorAndAddToDb(e, "error in adding user startTest " + e.getMessage());
        }

        this.startTimestamp = 0;
        this.endTimestamp = 0;
        this.retrieveAllCollectionTests();
        int accountId = Context.accountId.get();
        String testingRunHexIdCopy = this.testingRunHexId;
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                Context.accountId.set(1000000);
                SlackWebhook slackWebhook = SlackWebhooksDao.instance.findOne(Filters.empty());
                if(accountId == 1723492815 && slackWebhook != null){
                    try {
                        CustomTextAlert customTextAlert = new CustomTextAlert("Tests being triggered for account: " + accountId + " runId=" + testingRunHexIdCopy);
                        SLACK_INSTANCE.send(slackWebhook.getWebhook(), customTextAlert.toJson());
                    } catch (Exception e) {
                        // TODO: handle exception
                    }
                    
                }
            }
        });
        return SUCCESS.toUpperCase();
    }

    private void handleCallFromAktoGpt() {
        if (this.source == null) {
            loggerMaker.debugAndAddToDb("Call from testing UI, skipping", LoggerMaker.LogDb.DASHBOARD);
            return;
        }
        if (this.source.isCallFromAktoGpt() && !this.selectedTests.isEmpty()) {
            loggerMaker.debugAndAddToDb("Call from Akto GPT, " + this.selectedTests, LoggerMaker.LogDb.DASHBOARD);
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
                    loggerMaker.debugAndAddToDb(
                            String.format("Category: %s, tests: %s", selectedTest, testSubCategories),
                            LoggerMaker.LogDb.DASHBOARD);
                    tests.addAll(testSubCategories);
                }
            }
            if (!tests.isEmpty()) {
                this.selectedTests = tests;
                loggerMaker.debugAndAddToDb("Tests found for " + this.selectedTests, LoggerMaker.LogDb.DASHBOARD);
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

        this.authMechanism = TestRolesDao.instance.fetchAttackerToken(null);

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

        testingRunsCount = TestingRunDao.instance.count(Filters.and(testingRunFilters));

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

        long cicdCount =  TestingRunDao.instance.count(
            Filters.and(
                Filters.eq(Constants.ID, testingRunId),
                getTestingRunTypeFilter(TestingRunType.CI_CD)
            )
        );

        this.testingRunType = TestingRunType.ONE_TIME;
        if (cicdCount > 0) {
            this.testingRunType = TestingRunType.CI_CD;
        } else if (this.testingRun.getPeriodInSeconds() > 0) {
            this.testingRunType = TestingRunType.RECURRING;
        } else if (this.testingRun.getPeriodInSeconds() == -1) {
            this.testingRunType = TestingRunType.CONTINUOUS_TESTING;
        }

        if (this.testingRun != null && this.testingRun.getTestIdConfig() == 1) {
            WorkflowTestingEndpoints workflowTestingEndpoints = (WorkflowTestingEndpoints) testingRun
                    .getTestingEndpoints();
            this.workflowTest = WorkflowTestsDao.instance
                    .findOne(Filters.eq("_id", workflowTestingEndpoints.getWorkflowTest().getId()));
        }

        TestingRunConfig runConfig = TestingRunConfigDao.instance.findOne(
            Filters.eq("_id", this.testingRun.getTestIdConfig()), Projections.exclude("collectionWiseApiInfoKey")
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
        Filters.or(
            Filters.exists(TestingRunResult.IS_IGNORED_RESULT, false),
            Filters.eq(TestingRunResult.IS_IGNORED_RESULT, false)
        )
        
    );

    private List<Bson> prepareTestRunResultsFilters(ObjectId testingRunResultSummaryId, QueryMode queryMode, boolean ignoreVulnerableInResults) {
        List<Bson> filterList = new ArrayList<>();
        filterList.add(Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, testingRunResultSummaryId));

        if(reportFilterList != null) {
            Bson filtersForTestingRunResults = com.akto.action.testing.Utils.createFiltersForTestingReport(reportFilterList);
            if (!filtersForTestingRunResults.equals(Filters.empty())) {
                filterList.add(filtersForTestingRunResults);
            }
        }

        Bson vulnerableTestFilter = ignoreVulnerableInResults ? vulnerableFilter : Filters.eq(TestingRunResult.VULNERABLE, true);

        if(queryMode == null) {
            if(fetchOnlyVulnerable) {
                filterList.add(vulnerableTestFilter);
            }
        } else {
            switch (queryMode) {
                case VULNERABLE:
                    filterList.add(vulnerableTestFilter);
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
                default:
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
                    Aggregates.sort(Sorts.ascending("severityValue")) :
                    Aggregates.sort(Sorts.descending("severityValue"));
        } else if ("time".equals(sortKey)) {
            sortStage = (sortOrder == 1) ?
                    Aggregates.sort(Sorts.ascending(TestingRunResult.END_TIMESTAMP)) :
                    Aggregates.sort(Sorts.descending(TestingRunResult.END_TIMESTAMP));
        }

        return sortStage;
    }

    private Bson prepareIgnoredFilterListFromResults(List<Bson> filterList) {
        List<TestingRunResult> testingRunResults = VulnerableTestingRunResultDao.instance
                    .findAll(Filters.and(filterList), Projections.include(TestingRunResult.API_INFO_KEY, TestingRunResult.TEST_SUB_TYPE));
        List<TestingIssuesId> issueIdsList = testingRunResults.stream()
                .map(testingRunResult -> getTestingIssueIdFromRunResult(testingRunResult))
                .distinct()
                .collect(Collectors.toList());
        return Filters.and(Filters.in(Constants.ID, issueIdsList), Filters.in(TestingRunIssues.TEST_RUN_ISSUES_STATUS, Arrays.asList(TestRunIssueStatus.IGNORED, TestRunIssueStatus.FIXED)));
    }

    private Map<String, Integer> getCountMapForQueryMode(ObjectId testingRunResultSummaryId, QueryMode queryMode, int accountId){
        Context.accountId.set(accountId);
        Map<String, Integer> resultantMap = new HashMap<>();

        List<Bson> filterList =  prepareTestRunResultsFilters(testingRunResultSummaryId, queryMode, false);
        int count = 0;
        if(queryMode.equals(QueryMode.IGNORED_ISSUES)) {
            // get all vulnerable results with only apiInfoKey, testSubType
            // find ignored issues count from those id, return count
            count = (int) TestingRunIssuesDao.instance.count(prepareIgnoredFilterListFromResults(filterList));
        }else{
            count = VulnerableTestingRunResultDao.instance.countFromDb(Filters.and(filterList), queryMode.equals(QueryMode.VULNERABLE));
        }

        resultantMap.put(queryMode.toString(), count);

        return resultantMap;
    }

    private final ExecutorService multiExecService = Executors.newFixedThreadPool(6);

    Map<String, Integer> testCountMap;
    public String fetchTestRunResultsCount() {
        ObjectId testingRunResultSummaryId;
        try {
            testingRunResultSummaryId = new ObjectId(testingRunResultSummaryHexId);
        } catch (Exception e) {
            addActionError("Invalid test summary id");
            return ERROR.toUpperCase();
        }


        int accountId = Context.accountId.get();

        testCountMap = new HashMap<>();
        List<Callable<Map<String, Integer>>> jobs = new ArrayList<>();

        for(QueryMode qm : QueryMode.values()) {
            if(!(qm.equals(QueryMode.SECURED) || qm.equals(QueryMode.SKIPPED_EXEC_NO_ACTION))){
                jobs.add(() -> getCountMapForQueryMode(testingRunResultSummaryId, qm, accountId));
            }
        }

        try {
            List<Future<Map<String, Integer>>> futures = new ArrayList<>();
            for (Callable<Map<String, Integer>> job : jobs) {
                futures.add(multiExecService.submit(job));
            }

            for (Future<Map<String, Integer>> future : futures) {
                try {
                    Map<String, Integer> queryCountMap = future.get();
                    for(String key: queryCountMap.keySet()){
                        testCountMap.put(key, queryCountMap.getOrDefault(key, 0));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }

        } catch (Exception e) {
            e.printStackTrace();
            return ERROR.toUpperCase();
        }

        return SUCCESS.toUpperCase();
    }

    String testingRunResultSummaryHexId;
    List<TestingRunResult> testingRunResults;
    private boolean fetchOnlyVulnerable;

    public List<String> getSelectedTestRunResultHexIds() {
        return selectedTestRunResultHexIds;
    }

    public void setSelectedTestRunResultHexIds(List<String> selectedTestRunResultHexIds) {
        this.selectedTestRunResultHexIds = selectedTestRunResultHexIds;
    }

    public enum QueryMode {
        VULNERABLE, SECURED, SKIPPED_EXEC_NEED_CONFIG, SKIPPED_EXEC_NO_ACTION, SKIPPED_EXEC, ALL, SKIPPED_EXEC_API_REQUEST_FAILED, IGNORED_ISSUES;
    }
    private QueryMode queryMode;

    private Map<TestError, String> errorEnums = new HashMap<>();

    @Getter
    private Map<String, String> jiraIssuesMapForResults;

    public String fetchTestingRunResults() {
        ObjectId testingRunResultSummaryId;
        try {
            testingRunResultSummaryId = new ObjectId(testingRunResultSummaryHexId);
        } catch (Exception e) {
            addActionError("Invalid test summary id");
            return ERROR.toUpperCase();
        }

        if (testingRunResultSummaryHexId != null) loggerMaker.debugAndAddToDb("fetchTestingRunResults called for hexId=" + testingRunResultSummaryHexId);
        if (queryMode != null) loggerMaker.debugAndAddToDb("fetchTestingRunResults called for queryMode="+queryMode);

        int timeNow = Context.now();
        List<Bson> testingRunResultFilters = prepareTestRunResultsFilters(testingRunResultSummaryId, queryMode, false);

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
            int pageLimit = queryMode.equals(QueryMode.IGNORED_ISSUES) ? 1000 : limit <= 0 ? 150 : limit;
            Bson sortStage = prepareTestingRunResultCustomSorting(sortKey, sortOrder);

            timeNow = Context.now();
            Bson filters = testingRunResultFilters.isEmpty() ? Filters.empty() : Filters.and(testingRunResultFilters);
            this.testingRunResults = VulnerableTestingRunResultDao.instance
                    .fetchLatestTestingRunResultWithCustomAggregations(filters, pageLimit, skip, sortStage, testingRunResultSummaryId, queryMode.equals(QueryMode.VULNERABLE) || queryMode.equals(QueryMode.IGNORED_ISSUES));
            loggerMaker.debugAndAddToDb("[" + (Context.now() - timeNow) + "] Fetched testing run results of size: " + testingRunResults.size(), LogDb.DASHBOARD);
            timeNow = Context.now();
            if(queryMode.equals(QueryMode.VULNERABLE) || queryMode.equals(QueryMode.IGNORED_ISSUES)) {
                List<TestRunIssueStatus> ignoreStatuses = queryMode.equals(QueryMode.IGNORED_ISSUES) ? Arrays.asList(TestRunIssueStatus.OPEN) : Arrays.asList(TestRunIssueStatus.IGNORED, TestRunIssueStatus.FIXED);
                BasicDBObject issueMetaDataMap = prepareIssueMetaDataMap(testingRunResults, ignoreStatuses);
                removeTestingRunResultsByIssues(testingRunResults, (Map<String, String>) issueMetaDataMap.get("statuses"));
                this.issuesDescriptionMap = (Map<String, String>) issueMetaDataMap.get("descriptions");
                this.jiraIssuesMapForResults = (Map<String, String>) issueMetaDataMap.get("jiraIssues");
            }
            loggerMaker.debugAndAddToDb("[" + (Context.now() - timeNow) + "] Removed ignored issues from testing run results. Current size of testing run results: " + testingRunResults.size(), LogDb.DASHBOARD);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error in fetchLatestTestingRunResult: " + e);
        }

        timeNow = Context.now();
        loggerMaker.debugAndAddToDb("fetchTestingRunResults completed in: " + (Context.now() - timeNow), LogDb.DASHBOARD);

        return SUCCESS.toUpperCase();
    }

    private BasicDBObject prepareIssueMetaDataMap(List<TestingRunResult> testingRunResults,List<TestRunIssueStatus> ignoreStatuses) {
        BasicDBObject issueMetaDataMap = new BasicDBObject();
        try {
            if (testingRunResults == null || testingRunResults.isEmpty()) {
                return issueMetaDataMap;
            }

            Map<TestingIssuesId, TestingRunResult> idToResultMap = new HashMap<>();
            for (TestingRunResult result : testingRunResults) {
                TestingIssuesId id = getTestingIssueIdFromRunResult(result);
                idToResultMap.put(id, result);
            }

            List<TestingRunIssues> issues = TestingRunIssuesDao.instance.findAll(
                Filters.and(
                    Filters.in(Constants.ID, idToResultMap.keySet())
                ),
                Projections.include(TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestingRunIssues.DESCRIPTION, TestingRunIssues.JIRA_ISSUE_URL)
            );

            return buildIssueMetaDataMap(issues, idToResultMap, ignoreStatuses);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in preparing issue description map: " + e.getMessage());
            return issueMetaDataMap;
        }
    }

    public static void removeTestingRunResultsByIssues(List<TestingRunResult> testingRunResults, Map<String, String> ignoredResults) {
        Iterator<TestingRunResult> resultIterator = testingRunResults.iterator();
        while (resultIterator.hasNext()) {
            TestingRunResult result = resultIterator.next();
            String resultHexId = result.getHexId();
            if (ignoredResults.containsKey(resultHexId)) {
                resultIterator.remove();
            }
        }
    }

    private Map<String, List<String>> reportFilterList;

    public String fetchVulnerableTestRunResults() {
        ObjectId testingRunResultSummaryId;
        try {
            testingRunResultSummaryId = new ObjectId(testingRunResultSummaryHexId);
            Bson filterForReport = com.akto.action.testing.Utils.createFiltersForTestingReport(reportFilterList);
            boolean isStoredInVulnerableCollection = VulnerableTestingRunResultDao.instance.isStoredInVulnerableCollection(testingRunResultSummaryId, true);
            Bson filters = Filters.and(
                    Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, testingRunResultSummaryId),
                    Filters.eq(TestingRunResult.VULNERABLE, true),
                    filterForReport
            );
            List<TestingRunResult> testingRunResultList = new ArrayList<>();
            if(isStoredInVulnerableCollection){
                filters = Filters.and(
                    Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, testingRunResultSummaryId),
                    filterForReport
                );
                testingRunResultList = VulnerableTestingRunResultDao.instance.findAll(filters, skip, 50, null);
            }else{
                testingRunResultList = TestingRunResultDao.instance.findAll(filters, skip, 50, null);
            }



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
        this.testingRunResult = VulnerableTestingRunResultDao.instance.findOneWithComparison(Filters.eq(Constants.ID, testingRunResultId), null);
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
        TestingRunResult result = VulnerableTestingRunResultDao.instance.findOneWithComparison(Filters.eq(Constants.ID, testingRunResultId), null);
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
                TestingRunDao.instance.updateOneNoUpsert(
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
        filters.addAll(getTableFilters());

        long totalCount = TestingRunDao.instance.count(Filters.and(filters));

        ArrayList<Bson> filterForCicd = new ArrayList<>(filters); // Create a copy of filters
        filterForCicd.add(getTestingRunTypeFilter(TestingRunType.CI_CD));
        long cicdCount = TestingRunDao.instance.count(Filters.and(filterForCicd));

        filters.add(getTestingRunTypeFilter(TestingRunType.ONE_TIME));

        long oneTimeCount = TestingRunDao.instance.count(Filters.and(filters));

        ArrayList<Bson> continuousTestsFilter = new ArrayList<>(); // Create a copy of filters
        continuousTestsFilter.add(getTestingRunTypeFilter(TestingRunType.CONTINUOUS_TESTING));
        continuousTestsFilter.add(Filters.gte(TestingRun.SCHEDULE_TIMESTAMP, startTimestamp));
        continuousTestsFilter.addAll(getTableFilters());

        long continuousTestsCount = TestingRunDao.instance.count(Filters.and(continuousTestsFilter));

        long scheduleCount = totalCount - oneTimeCount - cicdCount - continuousTestsCount;


        result.put("allTestRuns", totalCount);
        result.put("oneTime", oneTimeCount);
        result.put("scheduled", scheduleCount);
        result.put("cicd", cicdCount);
        result.put("continuous", continuousTestsCount);

        this.allTestsCountMap = result;
        return SUCCESS.toUpperCase();
    }

    public String fetchCategoryWiseScores() {
        try {
            // Determine data source type
            CategoryWiseStatsUtils.DataSource sourceType;
            switch (dataSource.toLowerCase()) {
                case "threat_detection":
                    sourceType = CategoryWiseStatsUtils.DataSource.THREAT_DETECTION;
                    break;
                case "guardrails":
                    sourceType = CategoryWiseStatsUtils.DataSource.GUARDRAILS;
                    break;
                case "redteaming":
                default:
                    sourceType = CategoryWiseStatsUtils.DataSource.REDTEAMING;
                    break;
            }
            
            // Use generic utility for aggregation - categories are handled internally
            this.categoryWiseScores = CategoryWiseStatsUtils.getCategoryWiseScores(
                sourceType,
                startTimestamp, 
                endTimestamp, 
                dashboardCategory
            );
            
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error fetching category wise scores: " + e.getMessage());
            addActionError("Error fetching category wise scores");
            return ERROR.toUpperCase();
        }
        
        return SUCCESS.toUpperCase();
    }


    // this gives the count of total vulnerabilites and map of count of subcategories for which issues are generated.
    public String getIssueSummaryInfo(){

        if(this.endTimestamp == 0){
            this.endTimestamp = Context.now();
        }

        Set<Integer> demoCollections = new HashSet<>();
        demoCollections.addAll(deactivatedCollections);
//        demoCollections.add(RuntimeListener.LLM_API_COLLECTION_ID);
//        demoCollections.add(RuntimeListener.VULNERABLE_API_COLLECTION_ID);
//
//        ApiCollection juiceshopCollection = ApiCollectionsDao.instance.findByName("juice_shop_demo");
//        if (juiceshopCollection != null) demoCollections.add(juiceshopCollection.getId());

        Map<String,Integer> totalSubcategoriesCountMap = TestingRunIssuesDao.instance.getTotalSubcategoriesCountMap(this.startTimestamp,this.endTimestamp, demoCollections);
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
        Bson filter = Filters.ne(TestingRun.NAME, Constants.ONBOARDING_DEMO_TEST);
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

    private EditableTestingRunConfig editableTestingRunConfig;

    public String modifyTestingRunConfig(){
        if (editableTestingRunConfig == null) {
            addActionError("Invalid editableTestingRunConfig");
            return Action.ERROR.toUpperCase();
        }

        try {
            if (this.testingRunConfigId == 0) {
                addActionError("Invalid testing run config id");
                return Action.ERROR.toUpperCase();
            } else {

                TestingRunConfig existingTestingRunConfig = TestingRunConfigDao.instance.findOne(Filters.eq(Constants.ID, this.testingRunConfigId));
                if (existingTestingRunConfig == null) {
                    addActionError("Testing run config object not found for ID: " + this.testingRunConfigId);
                    return Action.ERROR.toUpperCase();
                }

                List<Bson> updates = new ArrayList<>();

                if (editableTestingRunConfig.getConfigsAdvancedSettings() != null && !editableTestingRunConfig.getConfigsAdvancedSettings().equals(existingTestingRunConfig.getConfigsAdvancedSettings())) {
                    updates.add(Updates.set(TestingRunConfig.TEST_CONFIGS_ADVANCED_SETTINGS, editableTestingRunConfig.getConfigsAdvancedSettings()));
                }

                if(editableTestingRunConfig.getTestSuiteIds() != null && !editableTestingRunConfig.getTestSuiteIds().equals(existingTestingRunConfig.getTestSuiteIds()) && !editableTestingRunConfig.getTestSuiteIds().isEmpty()){
                    updates.add(Updates.set(TestingRunConfig.TEST_SUITE_IDS, editableTestingRunConfig.getTestSuiteIds()));
                }

                if (editableTestingRunConfig.getTestSubCategoryList() != null && !editableTestingRunConfig.getTestSubCategoryList().equals(existingTestingRunConfig.getTestSubCategoryList()) && !editableTestingRunConfig.getTestSubCategoryList().isEmpty()) {
                    updates.add(Updates.set(TestingRunConfig.TEST_SUBCATEGORY_LIST, editableTestingRunConfig.getTestSubCategoryList()));
                }

                if (editableTestingRunConfig.getTestRoleId() != null && !editableTestingRunConfig.getTestRoleId().equals(existingTestingRunConfig.getTestRoleId())) {
                    updates.add(Updates.set(TestingRunConfig.TEST_ROLE_ID, editableTestingRunConfig.getTestRoleId()));
                }

                if (editableTestingRunConfig.getOverriddenTestAppUrl() != null && !editableTestingRunConfig.getOverriddenTestAppUrl().equals(existingTestingRunConfig.getOverriddenTestAppUrl())) {
                    updates.add(Updates.set(TestingRunConfig.OVERRIDDEN_TEST_APP_URL, editableTestingRunConfig.getOverriddenTestAppUrl()));
                }
                if (editableTestingRunConfig.getAutoTicketingDetails() != null && validateAutoTicketingDetails(
                    editableTestingRunConfig.getAutoTicketingDetails())) {
                    updates.add(Updates.set(TestingRunConfig.AUTO_TICKETING_DETAILS,
                        editableTestingRunConfig.getAutoTicketingDetails()));
                }

                if (!updates.isEmpty()) {
                    TestingRunConfigDao.instance.updateOne(
                        Filters.eq(Constants.ID, this.testingRunConfigId),
                        Updates.combine(updates)
                    );
                }
            }

            if (editableTestingRunConfig.getTestingRunHexId() != null) {

                TestingRun existingTestingRun = TestingRunDao.instance.findOne(Filters.eq(Constants.ID, new ObjectId(editableTestingRunConfig.getTestingRunHexId())));

                if (existingTestingRun != null) {
                    List<Bson> updates = new ArrayList<>();

                    if (editableTestingRunConfig.getTestRunTime() > 0
                            && editableTestingRunConfig.getTestRunTime() <= 6 * 60 * 60
                            && editableTestingRunConfig.getTestRunTime() != existingTestingRun.getTestRunTime()) {
                        updates.add(Updates.set(TestingRun.TEST_RUNTIME, editableTestingRunConfig.getTestRunTime()));
                    }

                    if (editableTestingRunConfig.getMaxConcurrentRequests() > 0
                            && editableTestingRunConfig.getMaxConcurrentRequests() <= 500 && editableTestingRunConfig
                                    .getMaxConcurrentRequests() != existingTestingRun.getMaxConcurrentRequests()) {
                        updates.add(Updates.set(TestingRun.MAX_CONCURRENT_REQUEST,
                                editableTestingRunConfig.getMaxConcurrentRequests()));
                    }

                    if (existingTestingRun.getSendSlackAlert() != editableTestingRunConfig.getSendSlackAlert()) {
                        updates.add(
                                Updates.set(TestingRun.SEND_SLACK_ALERT, editableTestingRunConfig.getSendSlackAlert()));
                    }

                    if(editableTestingRunConfig.getSelectedSlackChannelId() != 0 && editableTestingRunConfig.getSelectedSlackChannelId() != existingTestingRun.getSelectedSlackChannelId()){
                        updates.add(Updates.set(TestingRun.SELECTED_SLACK_CHANNEL_ID, editableTestingRunConfig.getSelectedSlackChannelId()));
                    }

                    if (existingTestingRun.getSendMsTeamsAlert() != editableTestingRunConfig.getSendMsTeamsAlert()) {
                        updates.add(
                                Updates.set(TestingRun.SEND_MS_TEAMS_ALERT,
                                        editableTestingRunConfig.getSendMsTeamsAlert()));
                    }

                    int periodInSeconds = getPeriodInSeconds(editableTestingRunConfig.getRecurringDaily(), editableTestingRunConfig.getRecurringWeekly(), editableTestingRunConfig.getRecurringMonthly());
                    if (editableTestingRunConfig.getContinuousTesting()) {
                        periodInSeconds = -1;
                    }
                    if (existingTestingRun.getPeriodInSeconds() != periodInSeconds && periodInSeconds != 0) {
                        updates.add(Updates.set(TestingRun.PERIOD_IN_SECONDS, periodInSeconds));
                    }
                    if((editableTestingRunConfig.getScheduleTimestamp() - Context.now()) >= -60){
                        // 60 because maximum request time is 60 seconds and by default we want scheduled time stamp to be greater than current time.
                        updates.add(
                            Updates.combine(
                                Updates.set(TestingRun.SCHEDULE_TIMESTAMP, editableTestingRunConfig.getScheduleTimestamp()),
                                Updates.set(TestingRun.STATE, State.SCHEDULED)
                            )
                        );
                    }

                    if(editableTestingRunConfig.getMiniTestingServiceName() != null && !editableTestingRunConfig.getMiniTestingServiceName().isEmpty()){
                        updates.add(Updates.set(TestingRun.MINI_TESTING_SERVICE_NAME, editableTestingRunConfig.getMiniTestingServiceName()));
                    }

                    updates.add(Updates.set(TestingRun.SELECTED_SLACK_CHANNEL_ID, editableTestingRunConfig.getSelectedSlackChannelId()));

                    if (!updates.isEmpty()) {
                        TestingRunDao.instance.updateOne(
                            Filters.eq(Constants.ID,new ObjectId(editableTestingRunConfig.getTestingRunHexId())),
                            Updates.combine(updates)
                        );
                    }
                }

            }


        } catch (Exception e) {
            addActionError("Unable to modify testing run config and testing run");
            return Action.ERROR.toUpperCase();
        }
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
                    boolean isStoredInVulnerableCollection = VulnerableTestingRunResultDao.instance.isStoredInVulnerableCollection(summaryObjectId, true);
                    List<TestingRunResult> testingRunResults = VulnerableTestingRunResultDao.instance.findAll(
                        Filters.and(
                            Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, summaryObjectId),
                            vulnerableFilter
                        ),
                        Projections.include(TestingRunResult.API_INFO_KEY, TestingRunResult.TEST_SUB_TYPE),
                        isStoredInVulnerableCollection
                    );

                    if(testingRunResults.isEmpty()){
                        return;
                    }

                    Set<TestingIssuesId> issuesIds = new HashSet<>();
                    Map<TestingIssuesId, ObjectId> mapIssueToResultId = new HashMap<>();
                    Set<ObjectId> ignoredResults = new HashSet<>();
                    for(TestingRunResult runResult: testingRunResults){
                        TestingIssuesId issuesId = getTestingIssueIdFromRunResult(runResult);
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
                    totalCountIssues.put("CRITICAL", 0);
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
                    if(isStoredInVulnerableCollection){
                        VulnerableTestingRunResultDao.instance.updateMany(
                            Filters.in(Constants.ID, ignoredResults),
                            Updates.set(TestingRunResult.IS_IGNORED_RESULT, true)
                        );
                    }else{
                        TestingRunResultDao.instance.updateMany(
                            Filters.in(Constants.ID, ignoredResults),
                            Updates.set(TestingRunResult.IS_IGNORED_RESULT, true)
                        );
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0 , TimeUnit.SECONDS);

        return SUCCESS.toUpperCase();
    }

    private boolean validateAutoTicketingDetails(AutoTicketingDetails autoTicketingDetails) {
        if (autoTicketingDetails != null && autoTicketingDetails.isShouldCreateTickets()) {

            FeatureAccess featureAccess = UsageMetricUtils.getFeatureAccessSaas(Context.accountId.get(),
                "JIRA_INTEGRATION");
            if (!featureAccess.getIsGranted()) {
                addActionError("Auto Create Tickets plan is not activated for this account.");
                return false;
            }

            if (autoTicketingDetails.getProjectId() == null) {
                addActionError("Project Id is required.");
                return false;
            }

            if (autoTicketingDetails.getSeverities() == null
                || autoTicketingDetails.getSeverities().isEmpty()) {
                addActionError("Severities cannot be empty.");
                return false;
            }

            try {
                for (String s : autoTicketingDetails.getSeverities()) {
                    Severity.valueOf(s);
                }
            } catch (IllegalArgumentException e) {
                addActionError("Invalid parameter: severities.");
                return false;
            }
        }
        return true;
    }

    private Set<String> miniTestingServiceNames;
    public String fetchMiniTestingServiceNames() {
        List<ModuleInfo> moduleInfos = ModuleInfoDao.instance.findAll(Filters.and(
                        Filters.eq(ModuleInfo.MODULE_TYPE, ModuleInfo.ModuleType.MINI_TESTING),
                        Filters.gt(ModuleInfo.LAST_HEARTBEAT_RECEIVED, Context.now() - 20 * 60)));
        if (this.miniTestingServiceNames == null) {
            this.miniTestingServiceNames = new HashSet<>();
        }
        for (ModuleInfo moduleInfo : moduleInfos) {
            if (moduleInfo.getName() != null) {
                this.miniTestingServiceNames.add(moduleInfo.getName());
            }
        }
        return SUCCESS.toUpperCase();
    }

    private TestingIssuesId getTestingIssueIdFromRunResult(TestingRunResult runResult) {
        return new TestingIssuesId(runResult.getApiInfoKey(), TestErrorSource.AUTOMATED_TESTING,
            runResult.getTestSubType());
    }

    public String fetchMisConfiguredTestsCount() {
       ApiInfoKeyResult result = Utils.fetchUniqueApiInfoKeys(
                TestingRunResultDao.instance.getRawCollection(),
                Filters.eq(TestingRunResult.REQUIRES_CONFIG, true),
                "apiInfoKey",
                this.showApiInfo
        );
        this.misConfiguredTestsCount = result.count;
        if (this.showApiInfo) {
            this.misConfiguredTestsApiInfo = result.apiInfoList;
        }
        return Action.SUCCESS.toUpperCase();
    }

    @Getter
    Map<String, Integer> allTestsCountsRanges;

    public String fetchTestingRunsRanges(){
        allTestsCountsRanges = new HashMap<>();
        try {
            List<Bson> pipeLine = new ArrayList<>();
            pipeLine.add(
                Aggregates.match(Filters.and(Filters.eq(TestingRunResultSummary.STATE, State.COMPLETED.toString()), Filters.gt(TestingRunResultSummary.START_TIMESTAMP, 0)))
            );
            pipeLine.add(Aggregates.sort(
                Sorts.descending(TestingRunResultSummary.START_TIMESTAMP)
            ));

            GroupByTimeRange.groupByWeek(pipeLine, TestingRunResultSummary.START_TIMESTAMP, "totalTests", new BasicDBObject());
            MongoCursor<BasicDBObject> cursor = TestingRunResultSummariesDao.instance.getMCollection().aggregate(pipeLine, BasicDBObject.class).cursor();
            while (cursor.hasNext()) {
                BasicDBObject document = cursor.next();
                if(document.isEmpty()) continue;
                BasicDBObject id = (BasicDBObject) document.get("_id");
                String key = id.getInt("year") + "_" + id.getInt("weekOfYear");
                allTestsCountsRanges.put(key, document.getInt("totalTests"));
            }
            cursor.close();
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        }
        

        return SUCCESS.toUpperCase();
    }

    @Getter
    List<AgentConversationResult> conversationsList;
    @Setter
    String conversationId;

    public String fetchConversationsFromConversationId() {
        if(this.conversationId == null || this.conversationId.isEmpty()){
            addActionError("Conversation id is required");
            return ERROR.toUpperCase();
        }
        this.conversationsList = AgentConversationResultDao.instance.findAll(Filters.eq("conversationId", this.conversationId));
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

    public void setEditableTestingRunConfig(EditableTestingRunConfig editableTestingRunConfig) {
        this.editableTestingRunConfig = editableTestingRunConfig;
    }

    public Map<String, Integer> getTestCountMap() {
        return testCountMap;
    }

    public void setReportFilterList(Map<String, List<String>> reportFilterList) {
        this.reportFilterList = reportFilterList;
    }

    public boolean getCleanUpTestingResources() {
        return cleanUpTestingResources;
    }

    public void setCleanUpTestingResources(boolean cleanUpTestingResources) {
        this.cleanUpTestingResources = cleanUpTestingResources;
    }

    public boolean getSendMsTeamsAlert() {
        return sendMsTeamsAlert;
    }

    public void setSendMsTeamsAlert(boolean sendMsTeamsAlert) {
        this.sendMsTeamsAlert = sendMsTeamsAlert;
    }

    public void setRecurringWeekly(boolean recurringWeekly) {
        this.recurringWeekly = recurringWeekly;
    }

    public void setRecurringMonthly(boolean recurringMonthly) {
        this.recurringMonthly = recurringMonthly;
    }

    public void setTestSuiteIds(List<String> testSuiteIds) {
        this.testSuiteIds = testSuiteIds;
    }

    public void setAutoTicketingDetails(AutoTicketingDetails autoTicketingDetails) {
        this.autoTicketingDetails = autoTicketingDetails;
    }

    public Set<String> getMiniTestingServiceNames() {
        return miniTestingServiceNames;
    }

    public void setMiniTestingServiceNames(Set<String> miniTestingServiceNames) {
        this.miniTestingServiceNames = miniTestingServiceNames;
    }

    public void setSelectedMiniTestingServiceName(String selectedMiniTestingServiceName) {
        this.selectedMiniTestingServiceName = selectedMiniTestingServiceName;
    }

    public Map<String, String> getIssuesDescriptionMap() {
        return issuesDescriptionMap;
    }

    public void setIssuesDescriptionMap(
        Map<String, String> issuesDescriptionMap) {
        this.issuesDescriptionMap = issuesDescriptionMap;
    }

    public int getSelectedSlackWebhook() {
        return selectedSlackWebhook;
    }

    public void setSelectedSlackWebhook(int selectedSlackWebhook) {
        this.selectedSlackWebhook = selectedSlackWebhook;

    }
}
