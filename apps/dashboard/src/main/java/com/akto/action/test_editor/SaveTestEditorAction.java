package com.akto.action.test_editor;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.TestConfigYamlParser;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public class SaveTestEditorAction extends UserAction {

    @Override
    public String execute() throws Exception {
        return super.execute();
    }

    String content;
    public String saveTestEditorFile() {
        TestConfig testConfig = null;
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

        return SUCCESS.toUpperCase();
    }

    public void setContent(String content) {
        this.content = content;
    }
}
