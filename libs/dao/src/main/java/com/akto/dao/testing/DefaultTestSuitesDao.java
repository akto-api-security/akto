package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.testing.DefaultTestSuites;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;

import java.util.*;

import static com.akto.dto.testing.DefaultTestSuites.owaspTop10List;

public class DefaultTestSuitesDao extends AccountsContextDao<DefaultTestSuites> {

    public static final DefaultTestSuitesDao instance = new DefaultTestSuitesDao();

    public static Map<String, Map<String, List<String>>> getDefaultTestSuitesMap(boolean isFirstTime, long lastUpdatedDefaultTestSuite, boolean addedNewCategory) {
        List<YamlTemplate> yamlTemplateList;
        if (!isFirstTime && !addedNewCategory) {
            yamlTemplateList = YamlTemplateDao.instance.findAll(Filters.gt(YamlTemplate.CREATED_AT, lastUpdatedDefaultTestSuite), Projections.include(Constants.ID, YamlTemplate.INFO, YamlTemplate.SETTINGS));
        } else {
            yamlTemplateList = YamlTemplateDao.instance.findAll(Filters.empty(), Projections.include(Constants.ID, YamlTemplate.INFO, YamlTemplate.SETTINGS));
        }

        Map<String, List<String>> owaspSuites = new HashMap<>();
        for(Map.Entry<String, List<String>> entry : owaspTop10List.entrySet()) {
            String key = entry.getKey();
            List<String> categories = entry.getValue();

            List<String> testSubCategories = new ArrayList<>();

            for(YamlTemplate yamlTemplate : yamlTemplateList) {
                if(categories.contains(yamlTemplate.getInfo().getCategory().getName())) {
                    testSubCategories.add(yamlTemplate.getId());
                }
            }

            owaspSuites.put(key, testSubCategories);
        }


        Map<String, List<String>> testingMethodsSuites = new HashMap<>();
        Map<String, List<String>> durationTestSuites = new HashMap<>();
        for(YamlTemplate yamlTemplate : yamlTemplateList) {
            if(yamlTemplate.getAttributes() != null) {
                if(yamlTemplate.getAttributes().getNature().name().equals(GlobalEnums.TemplateNature.INTRUSIVE.name())) {
                    testingMethodsSuites.putIfAbsent("Intrusive", new ArrayList<>());
                    testingMethodsSuites.get("Intrusive").add(yamlTemplate.getId());
                } else {
                    testingMethodsSuites.putIfAbsent("Non Intrusive", new ArrayList<>());
                    testingMethodsSuites.get("Non Intrusive").add(yamlTemplate.getId());
                }

                if(yamlTemplate.getAttributes().getDuration().name().equals(GlobalEnums.TemplateDuration.FAST.name())){
                    durationTestSuites.putIfAbsent("Fast", new ArrayList<>());
                    durationTestSuites.get("Fast").add(yamlTemplate.getId());
                } else {
                    durationTestSuites.putIfAbsent("Slow", new ArrayList<>());
                    durationTestSuites.get("Slow").add(yamlTemplate.getId());
                }
            }
        }


        Map<String, List<String>> severitySuites = new HashMap<>();
        for(YamlTemplate yamlTemplate : yamlTemplateList) {
            if(yamlTemplate.getInfo().getSeverity().equals(GlobalEnums.Severity.CRITICAL.name())) {
                severitySuites.putIfAbsent("Critical", new ArrayList<>());
                severitySuites.get("Critical").add(yamlTemplate.getId());
            } else if(yamlTemplate.getInfo().getSeverity().equals(GlobalEnums.Severity.HIGH.name())) {
                severitySuites.putIfAbsent("High", new ArrayList<>());
                severitySuites.get("High").add(yamlTemplate.getId());
            } else if(yamlTemplate.getInfo().getSeverity().equals(GlobalEnums.Severity.MEDIUM.name())) {
                severitySuites.putIfAbsent("Medium", new ArrayList<>());
                severitySuites.get("Medium").add(yamlTemplate.getId());
            } else if(yamlTemplate.getInfo().getSeverity().equals(GlobalEnums.Severity.LOW.name())) {
                severitySuites.putIfAbsent("Low", new ArrayList<>());
                severitySuites.get("Low").add(yamlTemplate.getId());
            }
        }

        // Add MCP Security suites
        Map<String, List<String>> mcpSecuritySuites = new HashMap<>();
        for(Map.Entry<String, List<String>> entry : DefaultTestSuites.mcpSecurityList.entrySet()) {
            String key = entry.getKey();
            List<String> categories = entry.getValue();

            List<String> testSubCategories = new ArrayList<>();

            for(YamlTemplate yamlTemplate : yamlTemplateList) {
                if(categories.contains(yamlTemplate.getInfo().getCategory().getName())) {
                    testSubCategories.add(yamlTemplate.getId());
                }
            }

            mcpSecuritySuites.put(key, testSubCategories);
        }

        // Add AI Agent Security suites
        Map<String, List<String>> aiAgentSecuritySuites = new HashMap<>();
        for(Map.Entry<String, List<String>> entry : DefaultTestSuites.aiAgentSecurityList.entrySet()) {
            String key = entry.getKey();
            List<String> categories = entry.getValue();

            List<String> testSubCategories = new ArrayList<>();

            for(YamlTemplate yamlTemplate : yamlTemplateList) {
                if(categories.contains(yamlTemplate.getInfo().getCategory().getName())) {
                    testSubCategories.add(yamlTemplate.getId());
                }
            }

            aiAgentSecuritySuites.put(key, testSubCategories);
        }

        Map<String, Map<String, List<String>>> defaultTestSuites = new HashMap<>();
        defaultTestSuites.put(DefaultTestSuites.DefaultSuitesType.OWASP.name(), owaspSuites);
        defaultTestSuites.put(DefaultTestSuites.DefaultSuitesType.TESTING_METHODS.name(), testingMethodsSuites);
        defaultTestSuites.put(DefaultTestSuites.DefaultSuitesType.SEVERITY.name(), severitySuites);
        defaultTestSuites.put(DefaultTestSuites.DefaultSuitesType.DURATION.name(), durationTestSuites);
        defaultTestSuites.put(DefaultTestSuites.DefaultSuitesType.MCP_SECURITY.name(), mcpSecuritySuites);
        defaultTestSuites.put(DefaultTestSuites.DefaultSuitesType.AI_AGENT_SECURITY.name(), aiAgentSecuritySuites);

        return defaultTestSuites;
    }

    public static void insertDefaultTestSuites(boolean isFirstTime) {
        long yamlTemplatesCount;
        long lastUpdatedDefaultTestSuite;
        if(!isFirstTime) {
            List<DefaultTestSuites> defaultTestSuites = DefaultTestSuitesDao.instance.findAll(Filters.empty(), 0, 1, Sorts.descending(DefaultTestSuites.CREATED_AT), null);
            if(!defaultTestSuites.isEmpty()) {
                lastUpdatedDefaultTestSuite = defaultTestSuites.get(0).getLastUpdated();
                yamlTemplatesCount = YamlTemplateDao.instance.count(Filters.gt(YamlTemplate.CREATED_AT, lastUpdatedDefaultTestSuite));
            } else {
                yamlTemplatesCount = 0;
                lastUpdatedDefaultTestSuite = 0;
            }
        } else {
            lastUpdatedDefaultTestSuite = 0;
            yamlTemplatesCount = YamlTemplateDao.instance.count(Filters.empty());
        }


        List<DefaultTestSuites> defaultTestSuites = DefaultTestSuitesDao.instance.findAll(Filters.empty());

        Set<String> allTestSuitesTemplates = new HashSet<>();
        for(DefaultTestSuites defaultTestSuite : defaultTestSuites) {
            allTestSuitesTemplates.addAll(defaultTestSuite.getSubCategoryList());
        }

        int countOfDefaultTestSuites = DefaultTestSuites.countOfDefaultTestSuites();
        boolean addedNewCategory = defaultTestSuites.size() != countOfDefaultTestSuites;

        if(yamlTemplatesCount == allTestSuitesTemplates.size() && addedNewCategory) {
            return;
        }

        Map<String, Map<String, List<String>>> defaultTestSuitesMap = getDefaultTestSuitesMap(isFirstTime, lastUpdatedDefaultTestSuite, addedNewCategory);

        for(DefaultTestSuites.DefaultSuitesType defaultSuitesType : DefaultTestSuites.DefaultSuitesType.values()) {
            Map<String, List<String>> defaultSuiteMap = defaultTestSuitesMap.get(defaultSuitesType.name());
            for (String key : defaultSuiteMap.keySet()) {
                DefaultTestSuitesDao.instance.updateOne(Filters.and(
                        Filters.eq(DefaultTestSuites.NAME, key),
                        Filters.eq(DefaultTestSuites.SUITE_TYPE, defaultSuitesType.name())
                    ),
                    Updates.combine(
                        Updates.setOnInsert(DefaultTestSuites.CREATED_AT, Context.now()),
                        Updates.set(DefaultTestSuites.LAST_UPDATED, Context.now()),
                        Updates.setOnInsert(DefaultTestSuites.CREATED_BY, "Akto"),
                        Updates.setOnInsert(DefaultTestSuites.SUITE_TYPE, defaultSuitesType.name()),
                        Updates.addEachToSet(DefaultTestSuites.SUB_CATEGORY_LIST, defaultSuiteMap.get(key))
                    )
                );
            }
        }
    }

    public void saveYamlTestTemplateInDefaultSuite(Info info, String author) {
        for(Map.Entry<String, List<String>> entry : owaspTop10List.entrySet()) {
            String key = entry.getKey();
            List<String> categories = entry.getValue();

            if(!categories.contains(info.getCategory().getName())) {
                continue;
            }

            for(DefaultTestSuites.DefaultSuitesType defaultSuitesType : DefaultTestSuites.DefaultSuitesType.values()) {
                if(defaultSuitesType.name().equals(DefaultTestSuites.DefaultSuitesType.TESTING_METHODS.name())) {
                    continue;
                }

                Bson keyFilter = Filters.eq(DefaultTestSuites.NAME, key);
                if(defaultSuitesType.name().equals(DefaultTestSuites.DefaultSuitesType.SEVERITY.name())) {
                    keyFilter = Filters.eq(DefaultTestSuites.NAME, StringUtils.capitalize(info.getSeverity().toLowerCase()));
                }

                DefaultTestSuitesDao.instance.updateOne(Filters.and(
                                keyFilter,
                                Filters.eq(DefaultTestSuites.SUITE_TYPE, defaultSuitesType.name())
                        ),
                        Updates.combine(
                                Updates.setOnInsert(DefaultTestSuites.CREATED_AT, Context.now()),
                                Updates.set(DefaultTestSuites.LAST_UPDATED, Context.now()),
                                Updates.setOnInsert(DefaultTestSuites.CREATED_BY, author),
                                Updates.setOnInsert(DefaultTestSuites.SUITE_TYPE, defaultSuitesType.name()),
                                Updates.addEachToSet(DefaultTestSuites.SUB_CATEGORY_LIST, Arrays.asList(info.getSubCategory()))
                        )
                );
            }
        }
    }

    @Override
    public String getCollName() {
        return "default_test_suites";
    }

    @Override
    public Class<DefaultTestSuites> getClassT() {
        return DefaultTestSuites.class;
    }
}
