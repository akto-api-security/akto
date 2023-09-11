package com.akto.dao.test_editor;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Projections;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class YamlTemplateDao extends AccountsContextDao<YamlTemplate> {

    public static final YamlTemplateDao instance = new YamlTemplateDao();

    public Map<String, TestConfig> fetchTestConfigMap(boolean includeYamlContent) {
        Map<String, TestConfig> testConfigMap = new HashMap<>();
        List<YamlTemplate> yamlTemplates = YamlTemplateDao.instance.findAll(new BasicDBObject());
        for (YamlTemplate yamlTemplate: yamlTemplates) {
            try {
                TestConfig testConfig = TestConfigYamlParser.parseTemplate(yamlTemplate.getContent());
                if (includeYamlContent) {
                    testConfig.setContent(yamlTemplate.getContent());
                    testConfig.setTemplateSource(yamlTemplate.getSource());
                    testConfig.setUpdateTs(yamlTemplate.getUpdatedAt());
                }
                testConfig.setInactive(yamlTemplate.getInactive());
                testConfigMap.put(testConfig.getId(), testConfig);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return testConfigMap;
    }

    public Map<String, Info> fetchTestInfoMap() {
        Map<String, Info> ret = new HashMap<>();
        List<YamlTemplate> yamlTemplates = YamlTemplateDao.instance.findAll(new BasicDBObject(), Projections.include("info"));
        for (YamlTemplate yamlTemplate: yamlTemplates) {
            ret.put(yamlTemplate.getId(), yamlTemplate.getInfo());
        }

        return ret;
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
