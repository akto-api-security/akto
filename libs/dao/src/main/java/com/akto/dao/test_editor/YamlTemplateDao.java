package com.akto.dao.test_editor;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.context.Context;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.util.Pair;
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

    private static final int CACHE_CHECK = 15 * 60;
    private static Map<Integer, Pair<Integer, YamlTemplate>> commonTemplateCache = new HashMap<>();

    public Map<String, List<String>> fetchCommonWordListMap() {
        int accountId = Context.accountId.get();
        YamlTemplate commonTemplate = null;
        Pair<Integer, YamlTemplate> pair = commonTemplateCache.get(accountId);
        Map<String, List<String>> commonWordListMap = new HashMap<>();
        if (pair != null && pair.getFirst() + CACHE_CHECK > Context.now()) {
            commonTemplate = pair.getSecond();
        } else {
            commonTemplate = CommonTemplateDao.instance.findOne(Filters.empty());
            commonTemplateCache.put(accountId, new Pair<>(Context.now(), commonTemplate));
        }
        if (commonTemplate != null) {
            String content = commonTemplate.getContent();
            if (content != null && !content.isEmpty()) {
                commonWordListMap = TestConfigYamlParser.parseWordLists(content);
            }
        }
        return commonWordListMap;
    }

    public Map<String, TestConfig> fetchTestConfigMap(boolean includeYamlContent, boolean fetchOnlyActive, int skip, int limit, Bson customFilter) {
        Map<String, TestConfig> testConfigMap = new HashMap<>();
        List<Bson> filters = new ArrayList<>();
        filters.add(customFilter);
        if (fetchOnlyActive) {Bson filter = Filters.or(
                Filters.exists(YamlTemplate.INACTIVE, false),
                Filters.eq(YamlTemplate.INACTIVE, false)
        );
            filters.add(filter);
        } else {
            filters.add(new BasicDBObject());
        }
        Bson proj = includeYamlContent ? null : Projections.exclude("info");
        List<YamlTemplate> yamlTemplates;
        
        int localCounter = 0;
        int localSkip = skip;
        int localLimit = Math.min(100, limit);

        Map<String, List<String>> commonWordListMap = fetchCommonWordListMap();

        while (localCounter < limit) {
            yamlTemplates = YamlTemplateDao.instance.findAll(Filters.and(filters), localSkip, localLimit, Sorts.ascending("_id"), proj);
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

                    if (testConfig.getInfo() != null && yamlTemplate.getInfo() != null && yamlTemplate.getInfo().getCompliance() != null) {
                        testConfig.getInfo().setCompliance(yamlTemplate.getInfo().getCompliance());
                    }
                    
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            localCounter += yamlTemplates.size();
            if(yamlTemplates.size() == 0){
                break;
            }
            localSkip += localLimit;
        }

        return testConfigMap;
    }

    public Map<String, Info> fetchTestInfoMap() {
        return fetchTestInfoMap(new BasicDBObject());
    }

    public Map<String, Info> fetchTestInfoMap(Bson filter) {
        Map<String, Info> ret = new HashMap<>();
        List<YamlTemplate> yamlTemplates = YamlTemplateDao.instance.findAll(filter, Projections.include("info"));
        for (YamlTemplate yamlTemplate : yamlTemplates) {
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
