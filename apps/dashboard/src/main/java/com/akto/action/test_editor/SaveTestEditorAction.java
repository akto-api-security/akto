package com.akto.action.test_editor;

import com.akto.DaoInit;
import com.akto.action.UserAction;
import com.akto.action.testing.StartTestAction;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.TestConfigYamlParser;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.User;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.testing.TestingEndpoints;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

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
    private String templateSource;
    private ApiInfo.ApiInfoKey apiInfoKey;
    public String saveTestEditorFile() {
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

        int createdAt = Context.now();
        int updatedAt = Context.now();
        String author = getSUser().getLogin();


        //todo: @shivam modify this part when yaml template is bootstrapped via script in RuntimeInitializer
        YamlTemplateSource source = templateSource == null? YamlTemplateSource.AKTO_TEMPLATES : YamlTemplateSource.valueOf(templateSource);
        YamlTemplate template = YamlTemplateDao.instance.findOne(Filters.eq("_id", id));
        if (template == null || template.getSource() == YamlTemplateSource.CUSTOM || source == YamlTemplateSource.AKTO_TEMPLATES) {
            YamlTemplateDao.instance.updateOne(
                    Filters.eq("_id", id),
                    Updates.combine(
                            Updates.setOnInsert(YamlTemplate.CREATED_AT, createdAt),
                            Updates.setOnInsert(YamlTemplate.AUTHOR, author),
                            Updates.set(YamlTemplate.UPDATED_AT, updatedAt),
                            Updates.set(YamlTemplate.CONTENT, content),
                            Updates.set(YamlTemplate.INFO, testConfig.getInfo()),
                            Updates.set(YamlTemplate.SOURCE, source)
                    )
            );
        }

        StartTestAction testAction = new StartTestAction();
        testAction.setSession(getSession());
        testAction.setRecurringDaily(false);
        testAction.setApiInfoKeyList(Collections.singletonList(apiInfoKey));//default id
        testAction.setType(TestingEndpoints.Type.CUSTOM);
        List<String> idList = new ArrayList<>();
        idList.add(id);
        testAction.setSelectedTests(idList);
        testAction.startTest();
        this.setTestingRunHexId(testAction.getTestingRunHexId());
        return SUCCESS.toUpperCase();
    }

    public String saveTestEditorFile1() {
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

        String id = testConfig.getId();

        int createdAt = Context.now();
        int updatedAt = Context.now();
        String author = getSUser().getLogin();

        YamlTemplateDao.instance.updateOne(
                Filters.eq("_id", id),
                Updates.combine(
                        Updates.setOnInsert(YamlTemplate.CREATED_AT, createdAt),
                        Updates.setOnInsert(YamlTemplate.AUTHOR, author),
                        Updates.set(YamlTemplate.UPDATED_AT, updatedAt),
                        Updates.set(YamlTemplate.CONTENT, content),
                        Updates.set(YamlTemplate.INFO, testConfig.getInfo())
                )
        );

//        StartTestAction testAction = new StartTestAction();
//        testAction.setSession(getSession());
//        testAction.setRecurringDaily(false);
//        testAction.setApiCollectionId(0);//default id
//        testAction.setType(TestingEndpoints.Type.COLLECTION_WISE);
//        List<String> idList = new ArrayList<>();
//        idList.add(id);
//        testAction.setSelectedTests(idList);
//        testAction.startTest();
//        this.setTestingRunHexId(testAction.getTestingRunHexId());
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
            String success = saveTestEditorAction.saveTestEditorFile1();
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

    public String getTemplateSource() {
        return templateSource;
    }

    public void setTemplateSource(String templateSource) {
        this.templateSource = templateSource;
    }

    public ApiInfo.ApiInfoKey getApiInfoKey() {
        return apiInfoKey;
    }

    public void setApiInfoKey(ApiInfo.ApiInfoKey apiInfoKey) {
        this.apiInfoKey = apiInfoKey;
    }
}
