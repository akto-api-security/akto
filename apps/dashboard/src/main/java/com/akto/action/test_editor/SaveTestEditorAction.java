package com.akto.action.test_editor;

import com.akto.DaoInit;
import com.akto.action.UserAction;
import com.akto.action.testing_issues.IssuesAction;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.AuthMechanismsDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.TestConfigYamlParser;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.User;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.URLMethods;
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

    private static ObjectMapper mapper = new ObjectMapper();
    private static Gson gson = new Gson();
    private String content;
    private String testingRunHexId;
    private BasicDBObject apiInfoKey;
    private TestingRunResult testingRunResult;
    private String originalTestId;
    private String finalTestId;
    private List<SampleData> sampleDataList;
    private TestingRunIssues testingRunIssues;
    private Map<String, BasicDBObject> subCategoryMap;
    /*
    *   Request and Response Sample Data
    *
    * Request and response folling burp's format
    *
    * First line METHOD URL PROTOCOL
    * Second line host
    * Third line headers
    * Fourth line empty
    * Fifth line body
    *
    * Response format is
    * First line PROTOCOL STATUS_CODE STATUS_MESSAGE
    * Second line headers
    * Third line empty
    * Fourth line body
    *
    * */

    /*
    * {
    "sampleDataList": [
        {
            "id": {
                "apiCollectionId": 1681381098,
                "bucketEndEpoch": 0,
                "bucketStartEpoch": 0,
                "method": "GET",
                "responseCode": -1,
                "url": "https:\/\/juiceshop.akto.io\/rest\/captcha\/"
            },
            "samples": [
                "{\"method\":\"GET\",\"requestPayload\":\"\",\"responsePayload\":\"{\\\"captchaId\\\":0,\\\"captcha\\\":\\\"10-10*9\\\",\\\"answer\\\":\\\"-80\\\"}\",\"ip\":\"null\",\"source\":\"HAR\",\"type\":\"HTTP\/1.1\",\"akto_vxlan_id\":\"1681381098\",\"path\":\"https:\/\/juiceshop.akto.io\/rest\/captcha\/\",\"requestHeaders\":\"{\\\"Cookie\\\":\\\"language=en; welcomebanner_status=dismiss; cookieconsent_status=dismiss; continueCode=v7BmaPZbQ7NroLqvm1YzMVnwOBAVkTefndgpE5jkJlXey43R68K2D9xWNQgq; token=eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJzdGF0dXMiOiJzdWNjZXNzIiwiZGF0YSI6eyJpZCI6MjEsInVzZXJuYW1lIjoidmljdGltIiwiZW1haWwiOiJ2aWN0aW1AZ21haWwuY29tIiwicGFzc3dvcmQiOiJhNjJlN2JlMGE1NjQwODFiNmE5Zjc1MzA4MjA4YzQzMyIsInJvbGUiOiJjdXN0b21lciIsImRlbHV4ZVRva2VuIjoiIiwibGFzdExvZ2luSXAiOiIiLCJwcm9maWxlSW1hZ2UiOiJhc3NldHMvcHVibGljL2ltYWdlcy91cGxvYWRzL2RlZmF1bHQuc3ZnIiwidG90cFNlY3JldCI6IiIsImlzQWN0aXZlIjp0cnVlLCJjcmVhdGVkQXQiOiIyMDIzLTAzLTA5IDE0OjQ2OjI5LjI2OSArMDA6MDAiLCJ1cGRhdGVkQXQiOiIyMDIzLTAzLTA5IDE0OjQ2OjI5LjI2OSArMDA6MDAiLCJkZWxldGVkQXQiOm51bGx9LCJpYXQiOjE2NzgzNzM0MzAsImV4cCI6MTY3ODM5MTQzMH0.JYBu5fv--c9xic_A3yLhvcy2p5o6YjvsVSDnDJ8f5x5cFq5MBfm-Q3a9PrkzFk37QI9nkAsCHXp7lOOdI72sUjHyqZiBu3PT7XzOQmkf8G3D0QZn51oX-bzCEDKbprFoBi5a14duxQvuGhHakoK1La9x8Dgz0SQikeAEsDH6xzo\\\",\\\"Accept\\\":\\\"application\/json, text\/plain, *\/*\\\",\\\"User-Agent\\\":\\\"Mozilla\/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit\/537.36 (KHTML, like Gecko) Chrome\/110.0.5481.178 Safari\/537.36\\\",\\\"Referer\\\":\\\"https:\/\/juiceshop.akto.io\/\\\",\\\"Connection\\\":\\\"close\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Host\\\":\\\"juiceshop.akto.io\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"Authorization\\\":\\\"Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJzdGF0dXMiOiJzdWNjZXNzIiwiZGF0YSI6eyJpZCI6MjEsInVzZXJuYW1lIjoidmljdGltIiwiZW1haWwiOiJ2aWN0aW1AZ21haWwuY29tIiwicGFzc3dvcmQiOiJhNjJlN2JlMGE1NjQwODFiNmE5Zjc1MzA4MjA4YzQzMyIsInJvbGUiOiJjdXN0b21lciIsImRlbHV4ZVRva2VuIjoiIiwibGFzdExvZ2luSXAiOiIiLCJwcm9maWxlSW1hZ2UiOiJhc3NldHMvcHVibGljL2ltYWdlcy91cGxvYWRzL2RlZmF1bHQuc3ZnIiwidG90cFNlY3JldCI6IiIsImlzQWN0aXZlIjp0cnVlLCJjcmVhdGVkQXQiOiIyMDIzLTAzLTEwIDA1OjM5OjE4LjI5OSArMDA6MDAiLCJ1cGRhdGVkQXQiOiIyMDIzLTAzLTEwIDA1OjM5OjE4LjI5OSArMDA6MDAiLCJkZWxldGVkQXQiOm51bGx9LCJpYXQiOjE2Nzg0MjY4NjUsImV4cCI6MTk5Mzc4Njg2NX0.bUvn24at2rOcuht5hto8QHl7pXdanuLKQDBxqH2MWG2-mMEI8LgWm1R9HhUD209dHL93Ks52KijKJFOlF_5Z3-v47jY-Rf73wcA_Le69-n7EudWwrc_X6EGpNiqovVYm31RZQnU2Q_H-PtzpnzNIOnfE6z_p023acrke-cZkKss\\\",\\\"sec-ch-ua\\\":\\\"\\\\\\\"Not A(Brand\\\\\\\";v=\\\\\\\"24\\\\\\\", \\\\\\\"Chromium\\\\\\\";v=\\\\\\\"110\\\\\\\"\\\",\\\"sec-ch-ua-mobile\\\":\\\"?0\\\",\\\"sec-ch-ua-platform\\\":\\\"\\\\\\\"macOS\\\\\\\"\\\",\\\"Accept-Language\\\":\\\"en-GB,en-US;q=0.9,en;q=0.8\\\"}\",\"responseHeaders\":\"{\\\"X-Frame-Options\\\":\\\"SAMEORIGIN\\\",\\\"X-Recruiting\\\":\\\"\/#\/jobs\\\",\\\"Access-Control-Allow-Origin\\\":\\\"*\\\",\\\"ETag\\\":\\\"W\/\\\\\\\"32-vqgZNWxKAaJR+BtU04LY7YgnXPg\\\\\\\"\\\",\\\"X-Content-Type-Options\\\":\\\"nosniff\\\",\\\"Connection\\\":\\\"close\\\",\\\"Feature-Policy\\\":\\\"payment 'self'\\\",\\\"Vary\\\":\\\"Accept-Encoding\\\",\\\"Content-Length\\\":\\\"50\\\",\\\"Date\\\":\\\"Thu, 09 Mar 2023 14:51:03 GMT\\\",\\\"Content-Type\\\":\\\"application\/json; charset=utf-8\\\"}\",\"time\":\"1678373463\",\"contentType\":\"application\/json; charset=utf-8\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}",
                "{\"method\":\"GET\",\"requestPayload\":\"\",\"responsePayload\":\"{\\\"captchaId\\\":1,\\\"captcha\\\":\\\"6*4+7\\\",\\\"answer\\\":\\\"31\\\"}\",\"ip\":\"null\",\"source\":\"HAR\",\"type\":\"HTTP\/1.1\",\"akto_vxlan_id\":\"1681381098\",\"path\":\"https:\/\/juiceshop.akto.io\/rest\/captcha\/\",\"requestHeaders\":\"{\\\"Cookie\\\":\\\"language=en; welcomebanner_status=dismiss; cookieconsent_status=dismiss; continueCode=v7BmaPZbQ7NroLqvm1YzMVnwOBAVkTefndgpE5jkJlXey43R68K2D9xWNQgq; token=eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJzdGF0dXMiOiJzdWNjZXNzIiwiZGF0YSI6eyJpZCI6MjEsInVzZXJuYW1lIjoidmljdGltIiwiZW1haWwiOiJ2aWN0aW1AZ21haWwuY29tIiwicGFzc3dvcmQiOiJhNjJlN2JlMGE1NjQwODFiNmE5Zjc1MzA4MjA4YzQzMyIsInJvbGUiOiJjdXN0b21lciIsImRlbHV4ZVRva2VuIjoiIiwibGFzdExvZ2luSXAiOiIiLCJwcm9maWxlSW1hZ2UiOiJhc3NldHMvcHVibGljL2ltYWdlcy91cGxvYWRzL2RlZmF1bHQuc3ZnIiwidG90cFNlY3JldCI6IiIsImlzQWN0aXZlIjp0cnVlLCJjcmVhdGVkQXQiOiIyMDIzLTAzLTA5IDE0OjQ2OjI5LjI2OSArMDA6MDAiLCJ1cGRhdGVkQXQiOiIyMDIzLTAzLTA5IDE0OjQ2OjI5LjI2OSArMDA6MDAiLCJkZWxldGVkQXQiOm51bGx9LCJpYXQiOjE2NzgzNzM0MzAsImV4cCI6MTY3ODM5MTQzMH0.JYBu5fv--c9xic_A3yLhvcy2p5o6YjvsVSDnDJ8f5x5cFq5MBfm-Q3a9PrkzFk37QI9nkAsCHXp7lOOdI72sUjHyqZiBu3PT7XzOQmkf8G3D0QZn51oX-bzCEDKbprFoBi5a14duxQvuGhHakoK1La9x8Dgz0SQikeAEsDH6xzo\\\",\\\"Accept\\\":\\\"application\/json, text\/plain, *\/*\\\",\\\"User-Agent\\\":\\\"Mozilla\/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit\/537.36 (KHTML, like Gecko) Chrome\/110.0.5481.178 Safari\/537.36\\\",\\\"Referer\\\":\\\"https:\/\/juiceshop.akto.io\/\\\",\\\"Connection\\\":\\\"close\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Host\\\":\\\"juiceshop.akto.io\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"Authorization\\\":\\\"Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJzdGF0dXMiOiJzdWNjZXNzIiwiZGF0YSI6eyJpZCI6MjEsInVzZXJuYW1lIjoidmljdGltIiwiZW1haWwiOiJ2aWN0aW1AZ21haWwuY29tIiwicGFzc3dvcmQiOiJhNjJlN2JlMGE1NjQwODFiNmE5Zjc1MzA4MjA4YzQzMyIsInJvbGUiOiJjdXN0b21lciIsImRlbHV4ZVRva2VuIjoiIiwibGFzdExvZ2luSXAiOiIiLCJwcm9maWxlSW1hZ2UiOiJhc3NldHMvcHVibGljL2ltYWdlcy91cGxvYWRzL2RlZmF1bHQuc3ZnIiwidG90cFNlY3JldCI6IiIsImlzQWN0aXZlIjp0cnVlLCJjcmVhdGVkQXQiOiIyMDIzLTAzLTEwIDA1OjM5OjE4LjI5OSArMDA6MDAiLCJ1cGRhdGVkQXQiOiIyMDIzLTAzLTEwIDA1OjM5OjE4LjI5OSArMDA6MDAiLCJkZWxldGVkQXQiOm51bGx9LCJpYXQiOjE2Nzg0MjY4NjUsImV4cCI6MTk5Mzc4Njg2NX0.bUvn24at2rOcuht5hto8QHl7pXdanuLKQDBxqH2MWG2-mMEI8LgWm1R9HhUD209dHL93Ks52KijKJFOlF_5Z3-v47jY-Rf73wcA_Le69-n7EudWwrc_X6EGpNiqovVYm31RZQnU2Q_H-PtzpnzNIOnfE6z_p023acrke-cZkKss\\\",\\\"If-None-Match\\\":\\\"W\/\\\\\\\"32-vqgZNWxKAaJR+BtU04LY7YgnXPg\\\\\\\"\\\",\\\"sec-ch-ua\\\":\\\"\\\\\\\"Not A(Brand\\\\\\\";v=\\\\\\\"24\\\\\\\", \\\\\\\"Chromium\\\\\\\";v=\\\\\\\"110\\\\\\\"\\\",\\\"sec-ch-ua-mobile\\\":\\\"?0\\\",\\\"sec-ch-ua-platform\\\":\\\"\\\\\\\"macOS\\\\\\\"\\\",\\\"Accept-Language\\\":\\\"en-GB,en-US;q=0.9,en;q=0.8\\\"}\",\"responseHeaders\":\"{\\\"X-Frame-Options\\\":\\\"SAMEORIGIN\\\",\\\"X-Recruiting\\\":\\\"\/#\/jobs\\\",\\\"Access-Control-Allow-Origin\\\":\\\"*\\\",\\\"ETag\\\":\\\"W\/\\\\\\\"2f-zwDMoWnwfWUSM4x77RNppD4xc\/Q\\\\\\\"\\\",\\\"X-Content-Type-Options\\\":\\\"nosniff\\\",\\\"Connection\\\":\\\"close\\\",\\\"Feature-Policy\\\":\\\"payment 'self'\\\",\\\"Vary\\\":\\\"Accept-Encoding\\\",\\\"Content-Length\\\":\\\"47\\\",\\\"Date\\\":\\\"Thu, 09 Mar 2023 14:51:14 GMT\\\",\\\"Content-Type\\\":\\\"application\/json; charset=utf-8\\\"}\",\"time\":\"1678373474\",\"contentType\":\"application\/json; charset=utf-8\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}"
            ]
        }
    ],
    "sensitiveSampleData": {},
    "traffic": {}
}
    *
    * */

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
        TestingUtil testingUtil = new TestingUtil(authMechanism, sampleDataMap, null, null);
        testingRunResult = executor.runTestNew(infoKey, null, testingUtil, null, testConfig);
        if (testingRunResult == null) {
            testingRunResult = new TestingRunResult(
                    new ObjectId(), infoKey, testConfig.getInfo().getCategory().getName(), testConfig.getInfo().getSubCategory() ,Collections.singletonList(new TestResult(null, sampleDataList.get(0).getSamples().get(0),
                    Collections.singletonList("Request API failed to satisfy api_selection_filters block, skipping execution"),
                    0, false, TestResult.Confidence.HIGH, null)),
                    false,null,0,Context.now(),
                    Context.now(), new ObjectId()
            );
        }
        testingRunResult.setId(new ObjectId());
        if (testingRunResult.isVulnerable()) {
            TestingIssuesId issuesId = new TestingIssuesId(infoKey, GlobalEnums.TestErrorSource.TEST_EDITOR, testConfig.getInfo().getSubCategory(), null);
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
