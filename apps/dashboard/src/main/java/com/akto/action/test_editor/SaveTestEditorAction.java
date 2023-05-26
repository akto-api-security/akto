package com.akto.action.test_editor;

import com.akto.DaoInit;
import com.akto.action.UserAction;
import com.akto.action.testing.StartTestAction;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.TestConfigYamlParser;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.User;
import com.akto.dto.test_editor.Category;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.type.URLMethods;
import com.akto.util.enums.GlobalEnums;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;
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
    private GlobalEnums.TestCategory testCategory;
    private String testId;
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
            testConfig = TestConfigYamlParser.parseTemplate(content);
            testConfig.setId(testId);
            Category category = new Category(testCategory.getName(), testCategory.getDisplayName(), testCategory.getShortName());
            testConfig.getInfo().setCategory(category);
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
            addActionError("Cannot update akto templates");
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    public String runTestForGivenTemplate() {
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

        String id = testConfig.getId();
        YamlTemplate template = YamlTemplateDao.instance.findOne(Filters.eq("_id", id));
        if (template == null) {
            addActionError("template does not exists");
            return ERROR.toUpperCase();
        }

//        int createdAt = Context.now();
//        int updatedAt = Context.now();
//        String author = getSUser().getLogin();
//
//
//        //todo: @shivam modify this part when yaml template is bootstrapped via script in RuntimeInitializer
//        YamlTemplateSource source = templateSource == null? YamlTemplateSource.AKTO_TEMPLATES : YamlTemplateSource.valueOf(templateSource);
//        if (template == null || template.getSource() == YamlTemplateSource.CUSTOM || source == YamlTemplateSource.AKTO_TEMPLATES) {
//            YamlTemplateDao.instance.updateOne(
//                    Filters.eq("_id", id),
//                    Updates.combine(
//                            Updates.setOnInsert(YamlTemplate.CREATED_AT, createdAt),
//                            Updates.setOnInsert(YamlTemplate.AUTHOR, author),
//                            Updates.set(YamlTemplate.UPDATED_AT, updatedAt),
//                            Updates.set(YamlTemplate.CONTENT, content),
//                            Updates.set(YamlTemplate.INFO, testConfig.getInfo()),
//                            Updates.set(YamlTemplate.SOURCE, source)
//                    )
//            );
//        }

        ApiInfo.ApiInfoKey infoKey = new ApiInfo.ApiInfoKey(apiInfoKey.getInt(ApiInfo.ApiInfoKey.API_COLLECTION_ID),
                apiInfoKey.getString(ApiInfo.ApiInfoKey.URL),
                URLMethods.Method.valueOf(apiInfoKey.getString(ApiInfo.ApiInfoKey.METHOD)));
        StartTestAction testAction = new StartTestAction();
        testAction.setSession(getSession());
        testAction.setRecurringDaily(false);
        testAction.setApiInfoKeyList(Collections.singletonList(infoKey));//default id
        testAction.setType(TestingEndpoints.Type.CUSTOM);
        List<String> idList = new ArrayList<>();
        idList.add(id);
        testAction.setSelectedTests(idList);
        testAction.startTest();
        this.setTestingRunHexId(testAction.getTestingRunHexId());
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

    public GlobalEnums.TestCategory getTestCategory() {
        return testCategory;
    }

    public void setTestCategory(GlobalEnums.TestCategory testCategory) {
        this.testCategory = testCategory;
    }

    public String getTestId() {
        return testId;
    }

    public void setTestId(String testId) {
        this.testId = testId;
    }
}
