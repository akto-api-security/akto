package com.akto.dao.testing.config;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.testing.DefaultTestSuitesDao;
import com.akto.dto.testing.DefaultTestSuites;
import com.akto.dto.testing.config.TestSuites;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TestSuiteDao extends AccountsContextDao<TestSuites>{

    public static final TestSuiteDao instance = new TestSuiteDao();

    public static List<String> getAllTestSuitesSubCategories(List<ObjectId> testSuitesIds) {
        Set<String> testSubCategories = new HashSet<>();

        List<TestSuites> customTestSuites = TestSuiteDao.instance.findAll(Filters.in(Constants.ID, testSuitesIds), Projections.include(Constants.ID, TestSuites.SUB_CATEGORY_LIST));
        List<DefaultTestSuites> defaultTestSuites = DefaultTestSuitesDao.instance.findAll(Filters.in(Constants.ID, testSuitesIds), Projections.include(Constants.ID, TestSuites.SUB_CATEGORY_LIST));

        for(TestSuites testSuite : customTestSuites) {
            testSubCategories.addAll(testSuite.getSubCategoryList());
        }

        for(DefaultTestSuites defaultTestSuite : defaultTestSuites) {
            testSubCategories.addAll(defaultTestSuite.getSubCategoryList());
        }

        return new ArrayList<>(testSubCategories);
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