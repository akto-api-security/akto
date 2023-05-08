package com.akto.action.test_editor;

import com.akto.action.UserAction;
import com.akto.action.testing.StartTestAction;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.TestConfigYamlParser;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.testing.TestingEndpoints;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import java.util.ArrayList;
import java.util.List;

public class SaveTestEditorAction extends UserAction {

    @Override
    public String execute() throws Exception {
        return super.execute();
    }

    private String content;
    private String testingRunHexId;
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

        StartTestAction testAction = new StartTestAction();
        testAction.setSession(getSession());
        testAction.setRecurringDaily(false);
        testAction.setApiCollectionId(0);//default id
        testAction.setType(TestingEndpoints.Type.COLLECTION_WISE);
        List<String> idList = new ArrayList<>();
        idList.add(id);
        testAction.setSelectedTests(idList);
        testAction.startTest();
        this.setTestingRunHexId(testAction.getTestingRunHexId());
        return SUCCESS.toUpperCase();
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
}
