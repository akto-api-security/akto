package com.akto.dao.test_editor;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.util.AccountTask;
import com.akto.util.Constants;
import com.akto.util.Pair;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

import java.util.ArrayList;
import java.util.Collections;
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

    public void clearCommonWordListMapForAccount() {
        commonTemplateCache.remove(Context.accountId.get());
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
        Map<String, YamlTemplate> overrideMap = fetchSystemTemplateOverrideMap();

        while (localCounter < limit) {
            yamlTemplates = YamlTemplateDao.instance.findAll(Filters.and(filters), localSkip, localLimit, Sorts.ascending("_id"), proj);
            for (YamlTemplate yamlTemplate: yamlTemplates) {
                YamlTemplate effectiveTemplate = overrideMap.getOrDefault(yamlTemplate.getId(), yamlTemplate);
                try {
                    TestConfig testConfig = TestConfigYamlParser.parseTemplate(effectiveTemplate.getContent());
                    if (includeYamlContent) {
                        testConfig.setContent(effectiveTemplate.getContent());
                        testConfig.setTemplateSource(effectiveTemplate.getSource());
                        testConfig.setUpdateTs(effectiveTemplate.getUpdatedAt());
                    }
                    testConfig.setInactive(effectiveTemplate.getInactive());
                    testConfig.setAuthor(effectiveTemplate.getAuthor());
                    testConfig.setEstimatedTokens(effectiveTemplate.getEstimatedTokens());
                    if (testConfig.getWordlists() != null) {
                        testConfig.getWordlists().putAll(commonWordListMap);
                    } else {
                        testConfig.setWordlists(commonWordListMap);
                    }
                    testConfigMap.put(testConfig.getId(), testConfig);

                    if (testConfig.getInfo() != null && effectiveTemplate.getInfo() != null && effectiveTemplate.getInfo().getCompliance() != null) {
                        testConfig.getInfo().setCompliance(effectiveTemplate.getInfo().getCompliance());
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
        applySystemTemplateOverrides(yamlTemplates);
        for (YamlTemplate yamlTemplate : yamlTemplates) {
            ret.put(yamlTemplate.getId(), yamlTemplate.getInfo());
        }

        return ret;
    }

    public static boolean accountAllowsSystemTemplateOverrides() {
        Integer accountId = Context.accountId.get();
        if (accountId == null) {
            return false;
        }
        if (AccountTask.inactiveAccountsSet.contains(accountId)) {
            return false;
        }
        Account account = Context.getAccount();
        return account != null && !account.isInactive();
    }

    public YamlTemplate findEffectiveOne(String id, Bson projection) {
        Bson idFilter = Filters.eq(Constants.ID, id);
        if (accountAllowsSystemTemplateOverrides()) {
            YamlTemplate override = YamlTemplateOverrideDao.instance.findOne(idFilter, projection);
            if (override != null) {
                return override;
            }
        }
        return findOne(idFilter, projection);
    }

    public void applySystemTemplateOverrides(List<YamlTemplate> yamlTemplates) {
        if (yamlTemplates == null || yamlTemplates.isEmpty()) {
            return;
        }
        Map<String, YamlTemplate> overrideMap = fetchSystemTemplateOverrideMap();
        if (overrideMap.isEmpty()) {
            return;
        }
        for (int i = 0; i < yamlTemplates.size(); i++) {
            YamlTemplate override = overrideMap.get(yamlTemplates.get(i).getId());
            if (override != null) {
                yamlTemplates.set(i, override);
            }
        }
    }

    private Map<String, YamlTemplate> fetchSystemTemplateOverrideMap() {
        if (!accountAllowsSystemTemplateOverrides()) {
            return Collections.emptyMap();
        }
        List<YamlTemplate> overrides = YamlTemplateOverrideDao.instance.findAll(Filters.empty());
        if (overrides == null || overrides.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, YamlTemplate> overrideMap = new HashMap<>();
        for (YamlTemplate override : overrides) {
            overrideMap.put(override.getId(), override);
        }
        return overrideMap;
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
