package com.akto.action.test_editor;

import com.akto.DaoInit;
import com.akto.action.UserAction;
import com.akto.action.testing_issues.IssuesAction;
import com.akto.dao.AuthMechanismsDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.TestConfigYamlParser;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.User;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.URLMethods;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;
import com.akto.testing.TestExecutor;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.types.ObjectId;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static com.akto.util.enums.GlobalEnums.YamlTemplateSource;

public class SaveTestEditorAction extends UserAction {

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
    public String fetchTestingRunResultFromTestingRun() {
        if (testingRunHexId == null) {
            addActionError("testingRunHexId is null");
            return ERROR.toUpperCase();
        }

        ObjectId testRunId = new ObjectId(testingRunHexId);

        this.testingRunResult = TestingRunResultDao.instance.findOne(Filters.eq(TestingRunResult.TEST_RUN_ID, testRunId));
        return SUCCESS.toUpperCase();
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
            YamlTemplateDao.instance.updateOne(
                    Filters.eq("_id", id),
                    Updates.combine(
                            Updates.setOnInsert(YamlTemplate.CREATED_AT, createdAt),
                            Updates.setOnInsert(YamlTemplate.AUTHOR, author),
                            Updates.set(YamlTemplate.UPDATED_AT, updatedAt),
                            Updates.set(YamlTemplate.CONTENT, content),
                            Updates.set(YamlTemplate.INFO, testConfig.getInfo()),
                            Updates.setOnInsert(YamlTemplate.SOURCE, YamlTemplateSource.CUSTOM)
                    )
            );
        } else {
            addActionError("Cannot save template, specify a different test id");
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

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

        if (apiInfoKey == null) {
            addActionError("apiInfoKey is null");
            return ERROR.toUpperCase();
        }

        if (sampleDataList == null || sampleDataList.isEmpty()) {
            addActionError("sampleDataList is empty");
            return ERROR.toUpperCase();
        }

        try {
            GlobalEnums.Severity.valueOf(testConfig.getInfo().getSeverity());
        } catch (Exception e) {
            addActionError("invalid severity, please choose from " + Arrays.toString(GlobalEnums.Severity.values()));
            return ERROR.toUpperCase();
        }

        ApiInfo.ApiInfoKey infoKey = new ApiInfo.ApiInfoKey(apiInfoKey.getInt(ApiInfo.ApiInfoKey.API_COLLECTION_ID),
                apiInfoKey.getString(ApiInfo.ApiInfoKey.URL),
                URLMethods.Method.valueOf(apiInfoKey.getString(ApiInfo.ApiInfoKey.METHOD)));

        AuthMechanism authMechanism = AuthMechanismsDao.instance.findOne(new BasicDBObject());
        Map<ApiInfo.ApiInfoKey, List<String>> sampleDataMap = new HashMap<>();
        sampleDataMap.put(infoKey, sampleDataList.get(0).getSamples());
        SampleMessageStore messageStore = SampleMessageStore.create(sampleDataMap);
        TestingUtil testingUtil = new TestingUtil(authMechanism, messageStore, null, null);
        testingRunResult = executor.runTestNew(infoKey, null, testingUtil, null, testConfig, null);
        if (testingRunResult == null) {
            testingRunResult = new TestingRunResult(
                    new ObjectId(), infoKey, testConfig.getInfo().getCategory().getName(), testConfig.getInfo().getSubCategory() ,Collections.singletonList(new TestResult(null, sampleDataList.get(0).getSamples().get(0),
                    Collections.singletonList("failed to execute test"),
                    0, false, TestResult.Confidence.HIGH, null)),
                    false,null,0,Context.now(),
                    Context.now(), new ObjectId()
            );
        }
        testingRunResult.setId(new ObjectId());
        if (testingRunResult.isVulnerable()) {
            TestingIssuesId issuesId = new TestingIssuesId(infoKey, GlobalEnums.TestErrorSource.TEST_EDITOR, testConfig.getId(), null);
            testingRunIssues = new TestingRunIssues(issuesId, GlobalEnums.Severity.valueOf(testConfig.getInfo().getSeverity()), GlobalEnums.TestRunIssueStatus.OPEN, Context.now(), Context.now(),null);
        }
        BasicDBObject infoObj = IssuesAction.createSubcategoriesInfoObj(testConfig);
        subCategoryMap = new HashMap<>();
        subCategoryMap.put(testConfig.getId(), infoObj);
        return SUCCESS.toUpperCase();
    }

    public static void showFile(File file, List<String> files) {
        if (!file.isDirectory()) {
            files.add(file.getAbsolutePath());
        }
    }

    public static void main(String[] args) throws Exception {
        DaoInit.init(new ConnectionString("mongodb://localhost:27017/admini"));
        Context.accountId.set(1_000_000);
        String folderPath = "/Users/shivamrawat/akto_code_openSource/akto/libs/dao/src/main/java/com/akto/dao/test_editor/inbuilt_test_yaml_files";
        Path dir = Paths.get(folderPath);
        List<String> files = new ArrayList<>();
        Files.walk(dir).forEach(path -> showFile(path.toFile(), files));
        for (String filePath : files) {
            System.out.println(filePath);
            List<String> lines = Files.readAllLines(Paths.get(filePath));
            String content  = String.join("\n", lines);
            SaveTestEditorAction saveTestEditorAction = new SaveTestEditorAction();
            saveTestEditorAction.setContent(content);
            Map<String,Object> session = new HashMap<>();
            User user = new User();
            user.setLogin("AKTO");
            session.put("user",user);
            saveTestEditorAction.setSession(session);
            String success = SUCCESS.toUpperCase();
            System.out.println(success);
        }
    }

    public void setContent(String content) {
        this.content = content;
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
}
