package com.akto.dao.testing;

import com.akto.DaoInit;
import com.akto.dao.CommonContextDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.testing.DefaultTestSuites;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

import java.util.*;

import static com.akto.dto.testing.DefaultTestSuites.owaspTop10List;

public class DefaultTestSuitesDao extends CommonContextDao<DefaultTestSuites> {

    public static final DefaultTestSuitesDao instance = new DefaultTestSuitesDao();

    public static void main(String[] args) {
        Context.accountId.set(1000000);
        DaoInit.init(new ConnectionString("mongodb://localhost:27017/admini"));

        long documentCount = DefaultTestSuitesDao.instance.estimatedDocumentCount();
        if(documentCount == 0) {
            insertDefaultTestSuites();
        }
    }

    public static Map<String, Map<String, List<String>>> getDefaultTestSuitesMap() {
        return getDefaultTestSuitesMap(null);
    }

    public static Map<String, Map<String, List<String>>> getDefaultTestSuitesMap(List<YamlTemplate> yamlTemplateList) {
        if(yamlTemplateList == null || yamlTemplateList.isEmpty()) {
            yamlTemplateList = YamlTemplateDao.instance.findAll(Filters.ne(YamlTemplate.INACTIVE, true), Projections.include(Constants.ID, YamlTemplate.INFO, YamlTemplate.SETTINGS));
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
        for(YamlTemplate yamlTemplate : yamlTemplateList) {
            if(yamlTemplate.getAttributes() != null) {
                if(yamlTemplate.getAttributes().getNature().name().equals(GlobalEnums.TemplateNature.INTRUSIVE.name())) {
                    testingMethodsSuites.putIfAbsent("Intrusive", new ArrayList<>());
                    testingMethodsSuites.get("Intrusive").add(yamlTemplate.getId());
                } else {
                    testingMethodsSuites.putIfAbsent("Non Intrusive", new ArrayList<>());
                    testingMethodsSuites.get("Non Intrusive").add(yamlTemplate.getId());
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

        Map<String, Map<String, List<String>>> defaultTestSuites = new HashMap<>();
        defaultTestSuites.put(DefaultTestSuites.DefaultSuitesType.OWASP.name(), owaspSuites);
        defaultTestSuites.put(DefaultTestSuites.DefaultSuitesType.TESTING_METHODS.name(), testingMethodsSuites);
        defaultTestSuites.put(DefaultTestSuites.DefaultSuitesType.SEVERITY.name(), severitySuites);

        return defaultTestSuites;
    }

    public static void insertDefaultTestSuites() {
        insertDefaultTestSuites(null);
    }

    public static void insertDefaultTestSuites(Map<String, Map<String, List<String>>> defaultTestSuitesMap) {
        if(defaultTestSuitesMap == null || defaultTestSuitesMap.isEmpty()) {
            defaultTestSuitesMap = getDefaultTestSuitesMap();
        }

        for(DefaultTestSuites.DefaultSuitesType defaultSuitesType : DefaultTestSuites.DefaultSuitesType.values()) {
            Map<String, List<String>> defaultSuiteMap = defaultTestSuitesMap.get(defaultSuitesType.name());
            for(String key : defaultSuiteMap.keySet()) {
                DefaultTestSuites defaultTestSuites = new DefaultTestSuites();

                defaultTestSuites.setCreatedAt(Context.now());
                defaultTestSuites.setCreatedBy("Akto");
                defaultTestSuites.setLastUpdated(Context.now());
                defaultTestSuites.setName(key);
                defaultTestSuites.setSubCategoryList(defaultSuiteMap.get(key));
                defaultTestSuites.setSuiteType(defaultSuitesType);

                DefaultTestSuitesDao.instance.insertOne(defaultTestSuites);
            }
        }
    }

    public static void updateDefaultTestSuites() {
        long now = Context.now();
        long sevenDaysAgo = now - 7 * 24 * 60 * 60;

        long yamlTemplatesCount = YamlTemplateDao.instance.count(Filters.ne(YamlTemplate.INACTIVE, true));

        List<YamlTemplate> yamlTemplateList = YamlTemplateDao.instance.findAll(Filters.and(
                Filters.ne(YamlTemplate.INACTIVE, true),
                Filters.gte(YamlTemplate.CREATED_AT, sevenDaysAgo)
        ), Projections.include(Constants.ID, YamlTemplate.CREATED_AT, YamlTemplate.INFO, YamlTemplate.SETTINGS));

        List<DefaultTestSuites> defaultTestSuites = DefaultTestSuitesDao.instance.findAll(Filters.empty());

        Set<String> allTestSuitesTemplates = new HashSet<>();
        for(DefaultTestSuites defaultTestSuite : defaultTestSuites) {
            allTestSuitesTemplates.addAll(defaultTestSuite.getSubCategoryList());
        }

        if(yamlTemplatesCount == allTestSuitesTemplates.size()) {
            return;
        }

        List<YamlTemplate> newTemplates = new ArrayList<>();
        for(YamlTemplate yamlTemplate : yamlTemplateList) {
            if(!allTestSuitesTemplates.contains(yamlTemplate.getId())) {
                newTemplates.add(yamlTemplate);
            }
        }

        Map<String, Map<String, List<String>>> defaultTestSuitesMap = getDefaultTestSuitesMap(newTemplates);
        insertDefaultTestSuites(defaultTestSuitesMap);
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
