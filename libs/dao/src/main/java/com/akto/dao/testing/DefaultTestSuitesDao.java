package com.akto.dao.testing;

import com.akto.dao.AccountSettingsDao;
import com.akto.dao.AccountsContextDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dto.AccountSettings;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.testing.DefaultTestSuites;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

import static com.akto.dto.testing.DefaultTestSuites.owaspTop10List;

public class DefaultTestSuitesDao extends AccountsContextDao<DefaultTestSuites> {

    public static final DefaultTestSuitesDao instance = new DefaultTestSuitesDao();

    private static String getCategoryName(YamlTemplate yamlTemplate) {
        if (yamlTemplate.getInfo() == null || yamlTemplate.getInfo().getCategory() == null) {
            return null;
        }
        return yamlTemplate.getInfo().getCategory().getName();
    }

    private static Map<String, List<String>> buildSuitesByCategory(List<YamlTemplate> yamlTemplateList, Map<String, List<String>> suiteToCategories) {
        Map<String, List<String>> suites = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : suiteToCategories.entrySet()) {
            List<String> testSubCategories = new ArrayList<>();
            for (YamlTemplate yamlTemplate : yamlTemplateList) {
                if (entry.getValue().contains(getCategoryName(yamlTemplate))) {
                    testSubCategories.add(yamlTemplate.getId());
                }
            }
            suites.put(entry.getKey(), testSubCategories);
        }
        return suites;
    }

    public static Map<String, Map<String, List<String>>> getDefaultTestSuitesMap(long lastUpdatedDefaultTestSuite) {
        List<YamlTemplate> yamlTemplateList =  YamlTemplateDao.instance.findAll(Filters.gte(YamlTemplate.CREATED_AT, lastUpdatedDefaultTestSuite), Projections.include(Constants.ID, YamlTemplate.INFO, YamlTemplate.SETTINGS));;
        Map<String, List<String>> owaspSuites = buildSuitesByCategory(yamlTemplateList, owaspTop10List);


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
        Map<String, List<String>> mcpSecuritySuites = buildSuitesByCategory(yamlTemplateList, DefaultTestSuites.mcpSecurityList);

        // Add Attack Strategy suites - grouped by the agentic OWASP top 10 (2026) categories
        Map<String, List<String>> attackStrategySuites = buildSuitesByCategory(yamlTemplateList, DefaultTestSuites.attackStrategyList);

        // Add Attack Base Technique suites - derived from the base technique that info.name ends with
        Set<String> attackStrategyCategories = new HashSet<>();
        for (List<String> categories : DefaultTestSuites.attackStrategyList.values()) {
            attackStrategyCategories.addAll(categories);
        }

        Map<String, List<String>> attackBaseTechniqueSuites = new HashMap<>();
        for (String baseTechnique : DefaultTestSuites.attackBaseTechniqueList) {
            attackBaseTechniqueSuites.put(baseTechnique, new ArrayList<>());
        }
        attackBaseTechniqueSuites.put(DefaultTestSuites.OTHERS_SUITE, new ArrayList<>());

        for (YamlTemplate yamlTemplate : yamlTemplateList) {
            if (!attackStrategyCategories.contains(getCategoryName(yamlTemplate))) {
                continue;
            }
            String baseTechnique = DefaultTestSuites.resolveAttackBaseTechnique(yamlTemplate.getInfo().getName());
            attackBaseTechniqueSuites.computeIfAbsent(baseTechnique, k -> new ArrayList<>()).add(yamlTemplate.getId());
        }

        Map<String, Map<String, List<String>>> defaultTestSuites = new HashMap<>();
        defaultTestSuites.put(DefaultTestSuites.DefaultSuitesType.OWASP.name(), owaspSuites);
        defaultTestSuites.put(DefaultTestSuites.DefaultSuitesType.TESTING_METHODS.name(), testingMethodsSuites);
        defaultTestSuites.put(DefaultTestSuites.DefaultSuitesType.SEVERITY.name(), severitySuites);
        defaultTestSuites.put(DefaultTestSuites.DefaultSuitesType.DURATION.name(), durationTestSuites);
        defaultTestSuites.put(DefaultTestSuites.DefaultSuitesType.MCP_SECURITY.name(), mcpSecuritySuites);
        defaultTestSuites.put(DefaultTestSuites.DefaultSuitesType.ATTACK_BASE_TECHNIQUE.name(), attackBaseTechniqueSuites);
        defaultTestSuites.put(DefaultTestSuites.DefaultSuitesType.ATTACK_STRATEGY.name(), attackStrategySuites);

        return defaultTestSuites;
    }
    public static void insertDefaultTestSuites() {

        AccountSettings as = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        int diff = Context.now() - as.getDefaultSuitesLastUpdatedAt();
        if(diff <= Constants.ONE_DAY_TIMESTAMP){
            return;
        }

        if(as.getDefaultSuitesLastUpdatedAt() == 0){
            DefaultTestSuitesDao.instance.deleteAll(Filters.empty());
        }

        Map<String, Map<String, List<String>>> defaultTestSuitesMap = getDefaultTestSuitesMap(as.getDefaultSuitesLastUpdatedAt());

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
        AccountSettingsDao.instance.updateOne(AccountSettingsDao.generateFilter(), Updates.set("defaultSuitesLastUpdatedAt", Context.now()));
    }

    public void saveYamlTestTemplateInDefaultSuite(Info info, String author) {
        for (Map.Entry<String, List<String>> entry : owaspTop10List.entrySet()) {
            String key = entry.getKey();
            List<String> categories = entry.getValue();

            if (!categories.contains(info.getCategory().getName())) {
                continue;
            }

            DefaultTestSuitesDao.instance.updateOne(
                    Filters.and(
                            Filters.eq(DefaultTestSuites.NAME, key),
                            Filters.eq(DefaultTestSuites.SUITE_TYPE, DefaultTestSuites.DefaultSuitesType.OWASP.name())
                    ),
                    Updates.combine(
                            Updates.setOnInsert(DefaultTestSuites.CREATED_AT, Context.now()),
                            Updates.set(DefaultTestSuites.LAST_UPDATED, Context.now()),
                            Updates.setOnInsert(DefaultTestSuites.CREATED_BY, author),
                            Updates.setOnInsert(DefaultTestSuites.SUITE_TYPE, DefaultTestSuites.DefaultSuitesType.OWASP.name()),
                            Updates.addEachToSet(DefaultTestSuites.SUB_CATEGORY_LIST, Arrays.asList(info.getSubCategory()))
                    )
            );

            String severityName = StringUtils.capitalize(info.getSeverity().toLowerCase());
            DefaultTestSuitesDao.instance.updateOne(
                    Filters.and(
                            Filters.eq(DefaultTestSuites.NAME, severityName),
                            Filters.eq(DefaultTestSuites.SUITE_TYPE, DefaultTestSuites.DefaultSuitesType.SEVERITY.name())
                    ),
                    Updates.combine(
                            Updates.setOnInsert(DefaultTestSuites.CREATED_AT, Context.now()),
                            Updates.set(DefaultTestSuites.LAST_UPDATED, Context.now()),
                            Updates.setOnInsert(DefaultTestSuites.CREATED_BY, author),
                            Updates.setOnInsert(DefaultTestSuites.SUITE_TYPE, DefaultTestSuites.DefaultSuitesType.SEVERITY.name()),
                            Updates.addEachToSet(DefaultTestSuites.SUB_CATEGORY_LIST, Arrays.asList(info.getSubCategory()))
                    )
            );
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
