package com.akto.dao.test_editor;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.mongodb.BasicDBObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class YamlTemplateDao extends AccountsContextDao<YamlTemplate> {

    public static final YamlTemplateDao instance = new YamlTemplateDao();

    public Map<String, TestConfig> fetchTestConfigMap() {
        Map<String, TestConfig> testConfigMap = new HashMap<>();
        List<YamlTemplate> yamlTemplates = YamlTemplateDao.instance.findAll(new BasicDBObject());
        for (YamlTemplate yamlTemplate: yamlTemplates) {
            try {
                TestConfig testConfig = TestConfigYamlParser.parseTemplate(yamlTemplate.getContent());
                testConfigMap.put(testConfig.getId(), testConfig);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return testConfigMap;
    }

    @Override
    public String getCollName() {
        return "yaml_templates";
    }

    @Override
    public Class<YamlTemplate> getClassT() {
        return YamlTemplate.class;
    }
}
