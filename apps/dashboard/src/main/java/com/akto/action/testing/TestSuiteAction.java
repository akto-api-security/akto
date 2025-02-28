package com.akto.action.testing;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dao.testing.config.TestSuiteDao;
import com.akto.dto.User;
import com.akto.dto.testing.config.TestSuite;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.bson.conversions.Bson;

public class TestSuiteAction extends UserAction {
    private int testSuiteId;
    private String testSuiteName;
    private List<String> subCategoryList;
    private List<TestSuite> testSuiteList;

    public String createTestSuite() {
        if (this.testSuiteName == null || this.testSuiteName.trim().isEmpty()) {
            addActionError("Invalid test suite name");
            return ERROR.toUpperCase();
        }
        if (this.subCategoryList == null) {
            addActionError("Invalid sub category list");
            return ERROR.toUpperCase();
        }
        int id = UUID.randomUUID().hashCode() & 0xfffffff;
        long createdAt = System.currentTimeMillis() / 1000l;
        long lastUpdated = createdAt;

        User user = getSUser();
        if (user == null) {
            addActionError("User not authenticated.");
            return ERROR.toUpperCase();
        }

        TestSuiteDao.instance.insertOne(
                new TestSuite(id, this.testSuiteName, this.subCategoryList, user.getLogin(), lastUpdated, createdAt));

        this.testSuiteId = id;
        return SUCCESS.toUpperCase();
    }

    public String modifyTestSuite() {

        if (this.testSuiteId < 0) {
            addActionError("Invalid test suite id");
            return ERROR.toUpperCase();
        }
        if (this.testSuiteName == null || this.testSuiteName.trim().isEmpty()) {
            addActionError("Invalid test suite name");
            return ERROR.toUpperCase();
        }
        if (this.subCategoryList == null) {
            addActionError("Invalid sub category list");
            return ERROR.toUpperCase();
        }
        TestSuite existingTestSuite = TestSuiteDao.instance.findOne(Filters.eq(Constants.ID, this.testSuiteId));

        if (existingTestSuite == null) {
            addActionError("Test suite not found");
            return ERROR.toUpperCase();
        }

        List<Bson> updates = new ArrayList<>();
        if (!testSuiteName.isEmpty() && !testSuiteName.equals(existingTestSuite.getName())) {
            updates.add(Updates.set(TestSuite.FIELD_NAME, this.testSuiteName));
        }

        if (!Objects.equals(subCategoryList, existingTestSuite.getSubCategoryList())) {
            updates.add(Updates.set(TestSuite.FIELD_SUB_CATEGORY_LIST, this.subCategoryList));
        }

        if (!updates.isEmpty()) {
            updates.add(Updates.set(TestSuite.FIELD_LAST_UPDATED, (long) (System.currentTimeMillis() / 1000l)));
            TestSuiteDao.instance.updateOne(
                    Filters.eq(Constants.ID, this.testSuiteId),
                    Updates.combine(updates));
        }

        return SUCCESS.toUpperCase();
    }

    public String getAllTestSuites() {
        User user = getSUser();
        if (user == null) {
            addActionError("User not authenticated.");
            return ERROR.toUpperCase();
        }
        this.testSuiteList = TestSuiteDao.instance.findAll(
                Filters.eq(TestSuite.FIELD_CREATED_BY, user.getLogin()));
        return SUCCESS.toUpperCase();
    }

    public String deleteTestSuite() {
        if (this.testSuiteId < 0) {
            addActionError("Invalid test suite id");
            return ERROR.toUpperCase();
        }
    
        TestSuiteDao.instance.deleteAll(Filters.eq(Constants.ID, this.testSuiteId));
        return SUCCESS.toUpperCase();
    }
    

    public String getTestSuiteName() {
        return testSuiteName;
    }

    public void setTestSuiteName(String testSuiteName) {
        this.testSuiteName = testSuiteName;
    }

    public List<String> getSubCategoryList() {
        return subCategoryList;
    }

    public void setSubCategoryList(List<String> subCategoryList) {
        this.subCategoryList = subCategoryList;
    }

    public int getTestSuiteId() {
        return testSuiteId;
    }

    public void setTestSuiteId(int testSuiteId) {
        this.testSuiteId = testSuiteId;
    }

    public List<TestSuite> getTestSuiteList() {
        return testSuiteList;
    }

    public void setTestSuiteList(List<TestSuite> testSuites) {
        this.testSuiteList = testSuites;
    }

}