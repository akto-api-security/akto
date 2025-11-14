package com.akto.action.test_editor;

import com.akto.action.UserAction;
import com.akto.action.testing_issues.IssuesAction;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.AccountsDao;
import com.akto.dao.CustomAuthTypeDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dao.test_editor.CommonTemplateDao;
import com.akto.dao.test_editor.TestConfigYamlParser;
import com.akto.dao.test_editor.TestingRunPlaygroundDao;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.test_editor.info.InfoParser;
import com.akto.dao.testing.AgentConversationResultDao;
import com.akto.dao.testing.DefaultTestSuitesDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dto.Account;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiInfo;
import com.akto.dto.CustomAuthType;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.test_editor.Category;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.test_editor.TestLibrary;
import com.akto.dto.test_editor.TestingRunPlayground;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.AgentConversationResult;
import com.akto.dto.testing.GenericTestResult;
import com.akto.dto.testing.MultiExecTestResult;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.TestingRun.State;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.URLMethods;
import com.akto.listener.InitializerListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.rules.RequiredConfigs;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;
import com.akto.test_editor.TestingUtilsSingleton;
import com.akto.test_editor.execution.VariableResolver;
import com.akto.testing.TestExecutor;
import com.akto.testing.Utils;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums;
import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.utils.GithubSync;
import com.akto.utils.TrafficFilterUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.*;
import com.mongodb.client.result.InsertOneResult;

import lombok.Getter;

import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.io.File;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.akto.util.enums.GlobalEnums.YamlTemplateSource;

public class SaveTestEditorAction extends UserAction {

    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private static final LoggerMaker logger = new LoggerMaker(SaveTestEditorAction.class, LogDb.DASHBOARD);;

    @Override
    public String execute() throws Exception {
        return super.execute();
    }

    private String content;
    private String testingRunHexId;
    private BasicDBObject apiInfoKey;
    private TestingRunResult testingRunResult;
    private String originalTestId;
    private String finalTestId;
    private List<SampleData> sampleDataList;
    private TestingRunIssues testingRunIssues;
    private Map<String, BasicDBObject> subCategoryMap;
    private boolean inactive;
    private String repositoryUrl;
    private HashMap<String, Integer> testCountMap;
    private String testingRunPlaygroundHexId;
    private State testingRunPlaygroundStatus;

    public String fetchTestingRunResultFromTestingRun() {
        if (testingRunHexId == null) {
            addActionError("testingRunHexId is null");
            return ERROR.toUpperCase();
        }

        ObjectId testRunId = new ObjectId(testingRunHexId);

        this.testingRunResult = TestingRunResultDao.instance.findOne(Filters.eq(TestingRunResult.TEST_RUN_ID, testRunId));
        return SUCCESS.toUpperCase();
    }

    private String getInfoKeyMissing(Info info){
        if (info.getName() == null){
            return "name";
        }
        if(info.getDescription() == null){
            return "description";
        }
        if(info.getDetails() == null){
            return "details";
        }
        if(info.getCategory() == null){
            return "category";
        }
        if(info.getSeverity() == null){
            return "severity";
        }
        if(info.getSubCategory() == null){
            return "subcategory";
        }

        return "";
    }

    public String saveTestEditorFile() {
        TestConfig testConfig;
        try {
            ObjectMapper mapper = new ObjectMapper(YAMLFactory.builder()
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
            .disable(YAMLGenerator.Feature.SPLIT_LINES)
            .build());
            mapper.findAndRegisterModules();
            Map<String, Object> config = mapper.readValue(content, Map.class);
            Object info = config.get("info");
            if (info == null) {
                addActionError("Error in template: info key absent");
                return ERROR.toUpperCase();
            }

            // adding all necessary fields check for info in editor
            InfoParser parser = new InfoParser();
            Info convertedInfo = parser.parse(info);
            
            String keyMissingInInfo = getInfoKeyMissing(convertedInfo);
            if(keyMissingInInfo.length() > 0){
                addActionError("Error in template: " + keyMissingInInfo + " key absent");
                return ERROR.toUpperCase();
            }

            Category category = convertedInfo.getCategory();
            if (category.getName() == null || category.getDisplayName() == null || category.getShortName() == null) {
                return ERROR.toUpperCase();
            }

            Map<String, Object> infoMap = (Map<String, Object>) info;

            finalTestId = config.getOrDefault("id", "").toString();            
            String finalTestName = infoMap.getOrDefault("name", "").toString();
            
            int epoch = Context.now();

            if (finalTestId.length()==0) {
                finalTestId = "CUSTOM_"+epoch;
            }

            if (finalTestName.length()==0) {
                finalTestName="Custom " + epoch;
            }

            YamlTemplate templateWithSameName = YamlTemplateDao.instance.findOne(Filters.eq("info.name", finalTestName));

            if (finalTestId.equals(originalTestId)) {
                YamlTemplate origYamlTemplate = YamlTemplateDao.instance.findOne(Filters.eq(Constants.ID, originalTestId));
                if (origYamlTemplate != null && origYamlTemplate.getSource() == YamlTemplateSource.CUSTOM) {

                    if (templateWithSameName != null && !templateWithSameName.getId().equals(originalTestId)) {
                        finalTestName += " Custom " + epoch;
                    }

                    // update the content in the original template
                } else {
                    finalTestId = finalTestId + "_CUSTOM_" + epoch;

                    if (templateWithSameName != null) {
                        finalTestName = finalTestName + " Custom " + epoch;
                    }

                    // insert new template
                }
            } else {
                YamlTemplate templateWithSameId = YamlTemplateDao.instance.findOne(Filters.eq(Constants.ID, finalTestId));
                if (templateWithSameId != null) {
                    finalTestId = finalTestId + "_CUSTOM_" + epoch;
                }

                if (templateWithSameName != null) {
                    finalTestName = finalTestName + " Custom " + epoch;
                }

                // insert new template
            }

            config.replace("id", finalTestId);
            infoMap.put("name", finalTestName);
            
            this.content = mapper.writeValueAsString(config);
            testConfig = TestConfigYamlParser.parseTemplate(content);
        } catch (Exception e) {
            e.printStackTrace();
            addActionError(e.getMessage());
            return ERROR.toUpperCase();
        }

        String id = testConfig.getId();

        int createdAt = Context.now();
        int updatedAt = Context.now();
        String author = getSUser().getLogin();


        YamlTemplate template = YamlTemplateDao.instance.findOne(Filters.eq("_id", id));        
        if (template == null || template.getSource() == YamlTemplateSource.CUSTOM) {

            List<Bson> updates = new ArrayList<>(
                    Arrays.asList(
                            Updates.setOnInsert(YamlTemplate.CREATED_AT, createdAt),
                            Updates.setOnInsert(YamlTemplate.AUTHOR, author),
                            Updates.set(YamlTemplate.UPDATED_AT, updatedAt),
                            Updates.set(YamlTemplate.CONTENT, content),
                            Updates.set(YamlTemplate.INFO, testConfig.getInfo()),
                            Updates.setOnInsert(YamlTemplate.SOURCE, YamlTemplateSource.CUSTOM)));
            
            try {
                // If the field does not exist in the template then we will not overwrite the existing value
                Object inactiveObject = TestConfigYamlParser.getFieldIfExists(content, YamlTemplate.INACTIVE);
                if (inactiveObject != null && inactiveObject instanceof Boolean) {
                    boolean inactive = (boolean) inactiveObject;
                    updates.add(Updates.set(YamlTemplate.INACTIVE, inactive));
                }
            } catch (Exception e) {
            }

            YamlTemplateDao.instance.updateOne(
                    Filters.eq(Constants.ID, id),
                    Updates.combine(updates));

            DefaultTestSuitesDao.instance.saveYamlTestTemplateInDefaultSuite(testConfig.getInfo(), author);

        } else {
            addActionError("Cannot save template, specify a different test id");
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    @Getter
    List<AgentConversationResult> agentConversationResults;

    public String runTestForGivenTemplate() {
        TestExecutor executor = new TestExecutor();
        TestConfig testConfig;
        try {
            testConfig = TestConfigYamlParser.parseTemplate(content);
        } catch (Exception e) {
            e.printStackTrace();
            addActionError(e.getMessage());
            return ERROR.toUpperCase();
        }

        if (testConfig == null) {
            addActionError("testConfig is null");
            return ERROR.toUpperCase();
        }

        Map<String, List<String>> commonWordListMap = YamlTemplateDao.instance.fetchCommonWordListMap();
        if (testConfig.getWordlists() != null) {
            testConfig.getWordlists().putAll(commonWordListMap);
        } else {
            testConfig.setWordlists(commonWordListMap);
        }

        if (apiInfoKey == null) {
            addActionError("apiInfoKey is null");
            return ERROR.toUpperCase();
        }

        if (sampleDataList == null || sampleDataList.isEmpty()) {
            addActionError("sampleDataList is empty");
            return ERROR.toUpperCase();
        }

        TestingUtilsSingleton.init();

        Account account = AccountsDao.instance.findOne(Filters.eq(Constants.ID, Context.accountId.get()));
        ApiInfo.ApiInfoKey infoKey = new ApiInfo.ApiInfoKey(apiInfoKey.getInt(ApiInfo.ApiInfoKey.API_COLLECTION_ID),
                apiInfoKey.getString(ApiInfo.ApiInfoKey.URL),
                URLMethods.Method.valueOf(apiInfoKey.getString(ApiInfo.ApiInfoKey.METHOD)));

        if (account.getHybridTestingEnabled()) {
            ModuleInfo moduleInfo = ModuleInfoDao.instance.getMCollection().find(Filters.eq(ModuleInfo.MODULE_TYPE, ModuleInfo.ModuleType.MINI_TESTING)).sort(Sorts.descending(ModuleInfo.LAST_HEARTBEAT_RECEIVED)).limit(1).first();
            if (moduleInfo != null) {
                String version = moduleInfo.getCurrentVersion().split(" - ")[0];
                if (Utils.compareVersions("1.44.9", version) <= 0) {//latest version
                    TestingRunPlayground testingRunPlayground = new TestingRunPlayground();
                    testingRunPlayground.setTestTemplate(content);
                    testingRunPlayground.setState(State.SCHEDULED);
                    testingRunPlayground.setSamples(sampleDataList.get(0).getSamples());
                    testingRunPlayground.setApiInfoKey(infoKey);
                    testingRunPlayground.setCreatedAt(Context.now());

                    InsertOneResult insertOne = TestingRunPlaygroundDao.instance.insertOne(testingRunPlayground);
                    if (insertOne.wasAcknowledged()) {
                        testingRunPlaygroundHexId = Objects.requireNonNull(insertOne.getInsertedId()).asObjectId().getValue().toHexString();
                        return SUCCESS.toUpperCase();
                    } else {
                        addActionError("Failed to create TestingRunPlayground");
                        return ERROR.toUpperCase();
                    }
                }
            }
        }

        try {
            if (!TestConfig.DYNAMIC_SEVERITY.equals(testConfig.getInfo().getSeverity())) {
                GlobalEnums.Severity.valueOf(testConfig.getInfo().getSeverity());
            }
        } catch (Exception e) {
            addActionError("invalid severity, please choose from " + Arrays.toString(GlobalEnums.Severity.values()));
            return ERROR.toUpperCase();
        }

        // initiating map creation for storing required
        RequiredConfigs.initiate();
        Map<ApiInfo.ApiInfoKey, List<String>> sampleDataMap = new HashMap<>();
        Map<ApiInfo.ApiInfoKey, List<String>> newSampleDataMap = new HashMap<>();
        
        Bson filters = Filters.and(
            Filters.eq("_id.apiCollectionId", infoKey.getApiCollectionId()),
            Filters.eq("_id.method", infoKey.getMethod()),
            Filters.in("_id.url", infoKey.getUrl())
        );
        SampleData sd = SampleDataDao.instance.findOne(filters);
        if (sd != null && sd.getSamples().size() > 0) {
            sd.getSamples().remove(0);
            newSampleDataMap.put(infoKey, sd.getSamples());
        }
        sampleDataMap.put(infoKey, sampleDataList.get(0).getSamples());
        Map<String, List<String>> wordListsMap = testConfig.getWordlists();
        if (wordListsMap == null) {
            wordListsMap = new HashMap<String, List<String>>();
        }

        wordListsMap = VariableResolver.resolveWordList(wordListsMap, infoKey, newSampleDataMap);

        SampleMessageStore messageStore = SampleMessageStore.create(sampleDataMap);
        List<CustomAuthType> customAuthTypes = CustomAuthTypeDao.instance.findAll(CustomAuthType.ACTIVE,true);
        TestingUtil testingUtil = new TestingUtil(messageStore, null, null, customAuthTypes);
        List<TestingRunResult.TestLog> testLogs = new ArrayList<>();
        int lastSampleIndex = sampleDataList.get(0).getSamples().size() - 1;
        
        TestingRunConfig testingRunConfig = new TestingRunConfig();
        List<String> samples = testingUtil.getSampleMessages().get(infoKey);
        TestingRunResult testingRunResult = Utils.generateFailedRunResultForMessage(null, infoKey, testConfig.getInfo().getCategory().getName(), testConfig.getInfo().getSubCategory(), null,samples , null);
        if(testingRunResult == null){
            String sample = samples.get(samples.size() - 1);
            testingRunResult = executor.runTestNew(infoKey, null, testingUtil, null, testConfig, testingRunConfig, true, testLogs, sample);
            String conversationId = null;
            if(testingRunResult != null){
                if(testingRunResult.getTestResults().get(0) instanceof TestResult){
                    TestResult testResult = (TestResult) testingRunResult.getTestResults().get(0);
                    conversationId = testResult.getConversationId();
                }
                if(!StringUtils.isEmpty(conversationId)){
                    agentConversationResults = AgentConversationResultDao.instance.findAll(Filters.eq("conversationId", conversationId));
                }
            }
        }
        if (testingRunResult == null) {
            testingRunResult = new TestingRunResult(
                    new ObjectId(), infoKey, testConfig.getInfo().getCategory().getName(), testConfig.getInfo().getSubCategory() ,Collections.singletonList(new TestResult(null, sampleDataList.get(0).getSamples().get(lastSampleIndex),
                    Collections.singletonList("failed to execute test"),
                    0, false, TestResult.Confidence.HIGH, null)),
                    false,null,0,Context.now(),
                    Context.now(), new ObjectId(), null, testLogs
            );
        }
        generateTestingRunResultAndIssue(testConfig, infoKey, testingRunResult);

        return SUCCESS.toUpperCase();
    }

    public String fetchTestingRunPlaygroundStatus() {
        if (testingRunPlaygroundHexId == null || testingRunPlaygroundHexId.trim().isEmpty()) {
            addActionError("Testing run id cannot be empty");
            return ERROR.toUpperCase();
        }
        TestingRunPlayground testingRunPlayGround = null;
        try {
            ObjectId testRunId = new ObjectId(testingRunPlaygroundHexId);
            testingRunPlayGround = (TestingRunPlaygroundDao.instance.findOne(Filters.eq(Constants.ID, testRunId)));
        } catch (Exception e) {
            addActionError("Invalid test run id");
            return ERROR.toUpperCase();
        }
        
        if (testingRunPlayGround == null) {
            addActionError("testingRunPlayGround not found");
            return ERROR.toUpperCase();
        }
        this.testingRunPlaygroundStatus = testingRunPlayGround.getState();
        List<String> samples = testingRunPlayGround.getSamples();
        ApiInfo.ApiInfoKey infoKey = testingRunPlayGround.getApiInfoKey();TestConfig testConfig;
        try {
            testConfig = TestConfigYamlParser.parseTemplate(testingRunPlayGround.getTestTemplate());
        } catch (Exception e) {
            e.printStackTrace();
            addActionError(e.getMessage());
            return ERROR.toUpperCase();
        }
        int lastSampleIndex = samples.size()-1;
        List<TestingRunResult.TestLog> testLogs = new ArrayList<>();
        TestingRunResult failedResult = new TestingRunResult(
                new ObjectId(), infoKey, testConfig.getInfo().getCategory().getName(), testConfig.getInfo().getSubCategory() ,Collections.singletonList(new TestResult(null, samples.get(lastSampleIndex),
                Collections.singletonList("failed to execute test"),
                0, false, TestResult.Confidence.HIGH, null)),
                false,null,0,Context.now(),
                Context.now(), new ObjectId(), null, testLogs
        );
        failedResult.setId(failedResult.getTestRunId());

        if(testingRunPlayGround.getState().equals(State.SCHEDULED)){
            this.testingRunResult = failedResult;
        }else {
            if(testingRunPlayGround.getTestingRunResult() != null) {
                this.testingRunResult = testingRunPlayGround.getTestingRunResult();
                generateTestingRunResultAndIssue(testConfig, infoKey, testingRunResult);
            } else {
                this.testingRunResult = failedResult;
            }
        }
        return SUCCESS.toUpperCase();
    }

    public void generateTestingRunResultAndIssue(TestConfig testConfig, ApiInfo.ApiInfoKey infoKey, TestingRunResult testingRunResult) {
        testingRunResult.setId(new ObjectId());
        if (testingRunResult.isVulnerable()) {
            TestingIssuesId issuesId = new TestingIssuesId(infoKey, GlobalEnums.TestErrorSource.TEST_EDITOR, testConfig.getId(), null);
            Severity severity = TestExecutor.getSeverityFromTestingRunResult(testingRunResult);
            this.testingRunIssues = new TestingRunIssues(issuesId, severity, GlobalEnums.TestRunIssueStatus.OPEN, Context.now(), Context.now(),null, null, Context.now());
        }
        BasicDBObject infoObj = IssuesAction.createSubcategoriesInfoObj(testConfig);
        subCategoryMap = new HashMap<>();
        subCategoryMap.put(testConfig.getId(), infoObj);

        List<GenericTestResult> runResults = new ArrayList<>();
        this.testingRunResult = testingRunResult;

        for (GenericTestResult testResult: this.testingRunResult.getTestResults()) {
            if (testResult instanceof TestResult) {
                runResults.add(testResult);
            } else {
                MultiExecTestResult multiTestRes = (MultiExecTestResult) testResult;
                runResults.addAll(multiTestRes.convertToExistingTestResult(this.testingRunResult));
            }
        }

        this.testingRunResult.setTestResults(runResults);
    }

    public static void showFile(File file, List<String> files) {
        if (!file.isDirectory()) {
            files.add(file.getAbsolutePath());
        }
    }

    public String setTestInactive() {

        if (originalTestId == null) {
            addActionError("TestId cannot be null");
            return ERROR.toUpperCase();
        }

        YamlTemplate template = YamlTemplateDao.instance.updateOne(
                Filters.eq(Constants.ID, originalTestId),
                Updates.combine(
                    Updates.set(YamlTemplate.INACTIVE, inactive),
                    Updates.set(YamlTemplate.UPDATED_AT, Context.now())
                ));

        if (template == null) {
            addActionError("Template not found");
            return ERROR.toUpperCase();
        }

        return SUCCESS.toUpperCase();
    }

    private void updateTemplates(int accountId, String author, String repositoryUrl) {
    executorService.schedule(new Runnable() {
            public void run() {
                Context.accountId.set(accountId);
                try {
                    GithubSync githubSync = new GithubSync();
                    byte[] repoZip = githubSync.syncRepo(repositoryUrl);
                    logger.debugAndAddToDb(String.format("Adding test templates from %s for account: %d", repositoryUrl, accountId), LogDb.DASHBOARD);
                    InitializerListener.processTemplateFilesZip(repoZip, author, YamlTemplateSource.CUSTOM.toString(), repositoryUrl);
                } catch (Exception e) {
                    logger.errorAndAddToDb(String.format("Error while adding test editor templates from %s for account %d, Error: %s", repositoryUrl, accountId, e.getMessage()), LogDb.DASHBOARD);
                }
            }
        }, 0, TimeUnit.SECONDS);
    }

    private boolean checkEmptyRepoString() {
        return repositoryUrl == null && !repositoryUrl.isEmpty() ;
    }

    public String syncCustomLibrary(){

        if (checkEmptyRepoString()) {
            addActionError("Repository url cannot be empty");
            return ERROR.toUpperCase();
        }

        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());

        List<TestLibrary> testLibraries = accountSettings.getTestLibraries();
        TestLibrary testLibrary = null;

        if(testLibraries != null){
            testLibrary = testLibraries.stream()
                    .filter(testLibrary1 -> testLibrary1.getRepositoryUrl().equals(repositoryUrl))
                    .findFirst().orElse(null);
        }

        if(testLibrary == null){
            addActionError("Test library not found");
            return ERROR.toUpperCase();
        }

        int accountId = Context.accountId.get();
        String author = testLibrary.getAuthor();

        updateTemplates(accountId, author, repositoryUrl);

        return SUCCESS.toUpperCase();
    }


    public String addTestLibrary(){

        if (checkEmptyRepoString()) {
            addActionError("Repository url cannot be empty");
            return ERROR.toUpperCase();
        }

        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());

        List<TestLibrary> testLibraries = accountSettings.getTestLibraries();

        if (testLibraries != null &&
                testLibraries.stream()
                        .anyMatch(testLibrary -> testLibrary.getRepositoryUrl().equals(repositoryUrl))) {
            addActionError("Test library already exists");
            return ERROR.toUpperCase();
        }

        int accountId = Context.accountId.get();
        String author = getSUser().getLogin();

        TestLibrary testLibrary = new TestLibrary(repositoryUrl, author, Context.now());

        AccountSettingsDao.instance.updateOne(
                AccountSettingsDao.generateFilter(), 
                Updates.addToSet(AccountSettings.TEST_LIBRARIES, testLibrary));

        updateTemplates(accountId, author, repositoryUrl);

        return SUCCESS.toUpperCase();
    }

    public String removeTestLibrary(){

        if (checkEmptyRepoString()) {
            addActionError("Repository url cannot be empty");
            return ERROR.toUpperCase();
        }

        String author = getSUser().getLogin();
        
        BasicDBObject repoPullObj = new BasicDBObject();
        repoPullObj.put(TestLibrary.REPOSITORY_URL, repositoryUrl);

        AccountSettingsDao.instance.updateOne(
                AccountSettingsDao.generateFilter(), 
                Updates.pull(AccountSettings.TEST_LIBRARIES, repoPullObj ));
        
        YamlTemplateDao.instance.deleteAll(
                Filters.and(
                    Filters.eq(YamlTemplate.AUTHOR, author),
                    Filters.eq(YamlTemplate.SOURCE, YamlTemplateSource.CUSTOM),
                    Filters.eq(YamlTemplate.REPOSITORY_URL, repositoryUrl)));

        return SUCCESS.toUpperCase();
    }

    public String fetchCustomTestsCount(){

        List<YamlTemplate> templates = YamlTemplateDao.instance.findAll(
            Filters.exists(YamlTemplate.REPOSITORY_URL),
            Projections.include(YamlTemplate.REPOSITORY_URL));
        
        if(testCountMap == null){
            testCountMap = new HashMap<>();
        }

        for(YamlTemplate template : templates){
            String repo = template.getRepositoryUrl();
            if(testCountMap.containsKey(repo)){
                testCountMap.put(repo, testCountMap.get(repo) + 1);
            }else{
                testCountMap.put(repo, 1);
            }
        }
        return SUCCESS.toUpperCase();
    }

    public String fetchTestContent(){
        if (originalTestId == null || originalTestId.trim().isEmpty()) {
            addActionError("TestId cannot be null or empty");
            return ERROR.toUpperCase();
        }

        YamlTemplate template =  YamlTemplateDao.instance.findOne(Filters.eq(Constants.ID, originalTestId), Projections.include(YamlTemplate.CONTENT));
        if (template == null) {
            addActionError("test not found");
            return ERROR.toUpperCase();
        }

        this.content = template.getContent();
        return SUCCESS.toUpperCase();
    }

    public String fetchCommonTestTemplate() {
        YamlTemplate template = CommonTemplateDao.instance.findOne(Filters.empty());
        if (template != null) {
            this.content = template.getContent();
        }
        return SUCCESS.toUpperCase();
    }

    public static final String COMMON_TEST_TEMPLATE = "common-test-template";

    public String saveCommonTestTemplate() {
        try {
            Map<String, List<String>> commonWordListMap = new HashMap<>();
            if (content != null && !content.isEmpty()) {
                commonWordListMap = TestConfigYamlParser.parseWordLists(content);
            }

            // TODO: add checks to remove extra content apart from wordLists

            if(commonWordListMap == null || commonWordListMap.isEmpty()) {
                addActionError("wordLists cannot be empty");
                return ERROR.toUpperCase();
            }

            String userName = "system";
            if (getSUser() != null && !getSUser().getLogin().isEmpty()) {
                userName = getSUser().getLogin();
            }
            List<Bson> updates = TrafficFilterUtil.getDbUpdateForTemplate(this.content, userName);
            // this has upsert true, so it will create a new document if it does not exist
            CommonTemplateDao.instance.updateOne(
                    Filters.eq(Constants.ID, COMMON_TEST_TEMPLATE),
                    Updates.combine(updates));

        } catch (Exception e) {
            e.printStackTrace();
            addActionError("Error while saving custom test template: " + e.getMessage());
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public String getTestingRunHexId() {
        return testingRunHexId;
    }

    public void setTestingRunHexId(String testingRunHexId) {
        this.testingRunHexId = testingRunHexId;
    }

    public BasicDBObject getApiInfoKey() {
        return apiInfoKey;
    }

    public void setApiInfoKey(BasicDBObject apiInfoKey) {
        this.apiInfoKey = apiInfoKey;
    }

    public TestingRunResult getTestingRunResult() {
        return testingRunResult;
    }

    public void setTestingRunResult(TestingRunResult testingRunResult) {
        this.testingRunResult = testingRunResult;
    }

    public String getOriginalTestId() {
        return originalTestId;
    }

    public void setOriginalTestId(String originalTestId) {
        this.originalTestId = originalTestId;
    }

    public String getFinalTestId() {
        return finalTestId;
    }

    public void setFinalTestId(String finalTestId) {
        this.finalTestId = finalTestId;
    }

    public List<SampleData> getSampleDataList() {
        return sampleDataList;
    }

    public void setSampleDataList(List<SampleData> sampleDataList) {
        this.sampleDataList = sampleDataList;
    }

    public TestingRunIssues getTestingRunIssues() {
        return testingRunIssues;
    }

    public void setTestingRunIssues(TestingRunIssues testingRunIssues) {
        this.testingRunIssues = testingRunIssues;
    }

    public Map<String, BasicDBObject> getSubCategoryMap() {
        return subCategoryMap;
    }

    public void setSubCategoryMap(Map<String, BasicDBObject> subCategoryMap) {
        this.subCategoryMap = subCategoryMap;
    }

    public boolean getInactive() {
        return inactive;
    }

    public void setInactive(boolean inactive) {
        this.inactive = inactive;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public HashMap<String, Integer> getTestCountMap() {
        return testCountMap;
    }

    public void setTestCountMap(HashMap<String, Integer> testCountMap) {
        this.testCountMap = testCountMap;
    }

    public void setTestingRunPlaygroundHexId(String testingRunPlayGroundHexId) {
        this.testingRunPlaygroundHexId = testingRunPlayGroundHexId;
    }

    public State getTestingRunPlaygroundStatus() {
        return testingRunPlaygroundStatus;
    }

    public void setTestingRunPlaygroundStatus(State testingRunPlaygroundStatus) {
        this.testingRunPlaygroundStatus = testingRunPlaygroundStatus;
    }

    public String getTestingRunPlaygroundHexId() {
        return testingRunPlaygroundHexId;
    }

}
