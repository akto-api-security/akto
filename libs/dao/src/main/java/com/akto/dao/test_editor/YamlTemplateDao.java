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

import com.mongodb.client.model.Sorts;
import org.bson.conversions.Bson;

public class YamlTemplateDao extends AccountsContextDao<YamlTemplate> {

    public static final YamlTemplateDao instance = new YamlTemplateDao();

    public Map<String, TestConfig> fetchTestConfigMap(boolean includeYamlContent, boolean fetchOnlyActive, int skip, int limit, Bson customFilter) {
        Map<String, TestConfig> testConfigMap = new HashMap<>();
        List<Bson> filters = new ArrayList<>();
        filters.add(customFilter);
        if (fetchOnlyActive) {
            filters.add(Filters.exists(YamlTemplate.INACTIVE, false));
            filters.add(Filters.eq(YamlTemplate.INACTIVE, false));
        } else {
            filters.add(new BasicDBObject());
        }
        Bson proj = includeYamlContent ? null : Projections.exclude("info");
        List<YamlTemplate> yamlTemplates;
        
        int localCounter = 0;
        int localSkip = skip;
        int localLimit = Math.min(100, limit);

        while (localCounter < limit) {
            yamlTemplates = YamlTemplateDao.instance.findAll(Filters.or(filters), localSkip, localLimit, Sorts.ascending("_id"), proj);
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
            localCounter += yamlTemplates.size();
            localSkip += localLimit;
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

    public int getNewCustomTemplates(int timestamp){
        int countOfTemplates = (int) YamlTemplateDao.instance.count(
            Filters.and(
                Filters.gt(YamlTemplate.CREATED_AT, timestamp),
                Filters.ne(YamlTemplate.AUTHOR, "AKTO")
            )
        );
        return countOfTemplates;
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
