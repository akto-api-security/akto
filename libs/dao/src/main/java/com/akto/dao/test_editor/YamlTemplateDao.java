package com.akto.dao.test_editor;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.conversions.Bson;

public class YamlTemplateDao extends AccountsContextDao<YamlTemplate> {

    public static final YamlTemplateDao instance = new YamlTemplateDao();

    public Map<String, List<String>> fetchCommonWordListMap(YamlTemplate commonTemplate) {
        Map<String, List<String>> commonWordListMap = new HashMap<>();
        if (commonTemplate != null) {
            String content = commonTemplate.getContent();
            if (content != null && !content.isEmpty()) {
                commonWordListMap = TestConfigYamlParser.parseWordLists(content);
            }
        }
        return commonWordListMap;
    }

    public Map<String, TestConfig> fetchTestConfigMap(boolean includeYamlContent, boolean fetchOnlyActive) {
        Map<String, TestConfig> testConfigMap = new HashMap<>();
        List<Bson> filters = new ArrayList<>();
        if (fetchOnlyActive) {
            filters.add(Filters.exists(YamlTemplate.INACTIVE, false));
            filters.add(Filters.eq(YamlTemplate.INACTIVE, false));
        } else {
            filters.add(new BasicDBObject());
        }
        List<YamlTemplate> yamlTemplates = YamlTemplateDao.instance.findAll(Filters.or(filters));
        for (YamlTemplate yamlTemplate: yamlTemplates) {
            try {
                TestConfig testConfig = TestConfigYamlParser.parseTemplate(yamlTemplate.getContent());
                if (includeYamlContent) {
                    testConfig.setContent(yamlTemplate.getContent());
                    testConfig.setTemplateSource(yamlTemplate.getSource());
                    testConfig.setUpdateTs(yamlTemplate.getUpdatedAt());
                }
                testConfig.setInactive(yamlTemplate.getInactive());
                testConfig.setAuthor(yamlTemplate.getAuthor());
                testConfigMap.put(testConfig.getId(), testConfig);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return testConfigMap;
    }
    public Map<String, TestConfig> fetchTestConfigMap(boolean includeYamlContent, boolean fetchOnlyActive, List<YamlTemplate> yamlTemplates) {
        return fetchTestConfigMap(includeYamlContent, fetchOnlyActive, yamlTemplates, null);
    }

    public Map<String, TestConfig> fetchTestConfigMap(boolean includeYamlContent, boolean fetchOnlyActive, List<YamlTemplate> yamlTemplates, YamlTemplate commonTemplate) {
        Map<String, TestConfig> testConfigMap = new HashMap<>();
        Map<String, List<String>> commonWordListMap = fetchCommonWordListMap(commonTemplate);
        for (YamlTemplate yamlTemplate: yamlTemplates) {
            try {
                TestConfig testConfig = TestConfigYamlParser.parseTemplate(yamlTemplate.getContent());
                if (includeYamlContent) {
                    testConfig.setContent(yamlTemplate.getContent());
                    testConfig.setTemplateSource(yamlTemplate.getSource());
                    testConfig.setUpdateTs(yamlTemplate.getUpdatedAt());
                }
                testConfig.setInactive(yamlTemplate.getInactive());
                testConfig.setAuthor(yamlTemplate.getAuthor());
                if (testConfig.getWordlists() != null) {
                    testConfig.getWordlists().putAll(commonWordListMap);
                } else {
                    testConfig.setWordlists(commonWordListMap);
                }
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
