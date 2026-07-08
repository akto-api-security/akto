package com.akto.dao.testing.config;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.DefaultTestSuitesDao;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.testing.DefaultTestSuites;
import com.akto.dto.testing.config.TestSuites;
import com.akto.util.Constants;
import com.akto.util.TestCategoryContextUtils;
import com.akto.util.enums.GlobalEnums;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.util.enums.GlobalEnums.TestCategory;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TestSuiteDao extends AccountsContextDao<TestSuites>{

    public static final TestSuiteDao instance = new TestSuiteDao();

    public static List<String> getAllTestSuitesSubCategories(List<ObjectId> testSuitesIds, CONTEXT_SOURCE contextSource) {
        Set<String> testSubCategories = new HashSet<>();

        List<TestSuites> customTestSuites = TestSuiteDao.instance.findAll(
                Filters.in(Constants.ID, testSuitesIds),
                Projections.include(Constants.ID, TestSuites.FIELD_SUB_CATEGORY_LIST));
        List<DefaultTestSuites> defaultTestSuites = DefaultTestSuitesDao.instance.findAll(
                Filters.in(Constants.ID, testSuitesIds),
                Projections.include(Constants.ID, TestSuites.FIELD_SUB_CATEGORY_LIST, DefaultTestSuites.SUITE_TYPE, TestSuites.FIELD_NAME));

        for (TestSuites testSuite : customTestSuites) {
            testSubCategories.addAll(filterSubCategoriesForSuite(
                    testSuite.getSubCategoryList(), contextSource, null, null));
        }

        for (DefaultTestSuites defaultTestSuite : defaultTestSuites) {
            testSubCategories.addAll(filterSubCategoriesForSuite(
                    defaultTestSuite.getSubCategoryList(),
                    contextSource,
                    defaultTestSuite.getSuiteType(),
                    defaultTestSuite.getName()));
        }

        return new ArrayList<>(testSubCategories);
    }

    public static List<String> filterSubCategoriesByContext(List<String> subCategories, CONTEXT_SOURCE contextSource) {
        return filterSubCategoriesForSuite(subCategories, contextSource, null, null);
    }

    private static boolean isTagBasedSuiteType(DefaultTestSuites.DefaultSuitesType suiteType) {
        return suiteType == DefaultTestSuites.DefaultSuitesType.SEVERITY
                || suiteType == DefaultTestSuites.DefaultSuitesType.DURATION
                || suiteType == DefaultTestSuites.DefaultSuitesType.TESTING_METHODS;
    }

    public static List<String> filterSubCategoriesForSuite(
            List<String> subCategories,
            CONTEXT_SOURCE contextSource,
            DefaultTestSuites.DefaultSuitesType suiteType,
            String suiteName) {
        if (subCategories == null || subCategories.isEmpty()) {
            return Collections.emptyList();
        }

        if (isTagBasedSuiteType(suiteType) && tagFilterForSuite(suiteType, suiteName) == null) {
            return Collections.emptyList();
        }

        TestCategory[] categories = TestCategoryContextUtils.getAllTestCategoriesWithinContext(contextSource);
        if (categories.length == 0) {
            return Collections.emptyList();
        }

        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.in(Constants.ID, subCategories));
        filters.add(Filters.or(buildCategoryFilters(categories)));
        filters.add(Filters.or(
                Filters.exists(YamlTemplate.INACTIVE, false),
                Filters.eq(YamlTemplate.INACTIVE, false)
        ));

        Bson tagFilter = tagFilterForSuite(suiteType, suiteName);
        if (tagFilter != null) {
            filters.add(tagFilter);
        }

        List<YamlTemplate> yamlTemplates = YamlTemplateDao.instance.findAll(
                Filters.and(filters),
                Projections.include(Constants.ID)
        );

        return yamlTemplates.stream().map(YamlTemplate::getId).collect(Collectors.toList());
    }

    private static List<Bson> buildCategoryFilters(TestCategory[] categories) {
        List<Bson> categoryFilters = new ArrayList<>();
        for (TestCategory category : categories) {
            categoryFilters.add(Filters.eq(YamlTemplate.INFO + ".category.name", category.getName()));
        }
        return categoryFilters;
    }

    private static Bson tagFilterForSuite(DefaultTestSuites.DefaultSuitesType suiteType, String suiteName) {
        if (suiteType == null || suiteName == null) {
            return null;
        }
        switch (suiteType) {
            case SEVERITY:
                String severity = severityForSuiteName(suiteName);
                return severity != null ? Filters.eq(YamlTemplate.INFO + ".severity", severity) : null;
            case DURATION:
                String duration = durationForSuiteName(suiteName);
                return duration != null ? Filters.eq(YamlTemplate.SETTINGS + ".duration", duration) : null;
            case TESTING_METHODS:
                String nature = natureForSuiteName(suiteName);
                return nature != null ? Filters.eq(YamlTemplate.SETTINGS + ".nature", nature) : null;
            default:
                return null;
        }
    }

    private static String severityForSuiteName(String suiteName) {
        switch (suiteName) {
            case "Critical":
                return GlobalEnums.Severity.CRITICAL.name();
            case "High":
                return GlobalEnums.Severity.HIGH.name();
            case "Medium":
                return GlobalEnums.Severity.MEDIUM.name();
            case "Low":
                return GlobalEnums.Severity.LOW.name();
            default:
                return null;
        }
    }

    private static String durationForSuiteName(String suiteName) {
        switch (suiteName) {
            case "Fast":
                return GlobalEnums.TemplateDuration.FAST.name();
            case "Slow":
                return GlobalEnums.TemplateDuration.SLOW.name();
            default:
                return null;
        }
    }

    private static String natureForSuiteName(String suiteName) {
        switch (suiteName) {
            case "Intrusive":
                return GlobalEnums.TemplateNature.INTRUSIVE.name();
            case "Non Intrusive":
                return GlobalEnums.TemplateNature.NON_INTRUSIVE.name();
            default:
                return null;
        }
    }

    @Override
    public String getCollName() {
        return "test_suite";
    }

    @Override
    public Class<TestSuites> getClassT() {
        return TestSuites.class;
    }

}
