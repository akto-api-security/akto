package com.akto.action.testing;

import com.akto.action.UserAction;
import com.akto.dao.RBACDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.config.TestSuiteDao;
import com.akto.dto.RBAC;
import com.akto.dto.User;
import com.akto.dto.testing.config.TestSuites;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import java.util.ArrayList;
import java.util.List;

import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.util.StringUtils;

public class TestSuiteAction extends UserAction {
    private String testSuiteHexId;
    private String testSuiteName;
    private List<String> subCategoryList;
    private List<TestSuites> testSuiteList;

    public String createTestSuite() {
        if (this.testSuiteName == null || this.testSuiteName.trim().isEmpty()) {
            addActionError("Invalid test suite name");
            return ERROR.toUpperCase();
        }

        int existingCount = (int) TestSuiteDao.instance.count(Filters.eq(TestSuites.FIELD_NAME, this.testSuiteName));
        if (existingCount > 0) {
            addActionError("Test suite with same name already exists");
            return ERROR.toUpperCase();
        }

        if (this.subCategoryList == null) {
            addActionError("Invalid sub category list");
            return ERROR.toUpperCase();
        }

        User user = getSUser();
        if (user == null) {
            addActionError("User not authenticated.");
            return ERROR.toUpperCase();
        }
        int timeNow = Context.now();

        TestSuiteDao.instance.insertOne(
                new TestSuites(this.testSuiteName, this.subCategoryList, user.getLogin(), timeNow, timeNow));

        return SUCCESS.toUpperCase();
    }

    public String modifyTestSuite() {

        if (StringUtils.hasText(this.testSuiteHexId)) {
            addActionError("Invalid test suite id");
            return ERROR.toUpperCase();
        }

        ObjectId testSuiteId = new ObjectId(this.testSuiteHexId);

        TestSuites existingTestSuite = TestSuiteDao.instance.findOne(Filters.eq(Constants.ID, testSuiteId));
        if (existingTestSuite == null) {
            addActionError("Test suite not found");
            return ERROR.toUpperCase();
        }

        if(!(getSUser().getLogin().equals(existingTestSuite.getCreatedBy()) || RBACDao.getCurrentRoleForUser(Context.userId.get(), Context.accountId.get()).equals(RBAC.Role.ADMIN))) {
            addActionError("User not authorized to modify test suite");
            return ERROR.toUpperCase();
        }
        
        List<Bson> updates = new ArrayList<>();
        if (StringUtils.hasText(this.testSuiteName)) {
            updates.add(Updates.set(TestSuites.FIELD_NAME, this.testSuiteName));
        }

        if (this.subCategoryList != null && !this.subCategoryList.isEmpty()) {
            updates.add(Updates.set(TestSuites.FIELD_SUB_CATEGORY_LIST, this.subCategoryList));
        }
        updates.add(Updates.set(TestSuites.FIELD_LAST_UPDATED, (long) (System.currentTimeMillis() / 1000l)));
        TestSuiteDao.instance.updateOne(
                Filters.eq(Constants.ID, testSuiteId),
                Updates.combine(updates));

        return SUCCESS.toUpperCase();
    }

    public String getAllTestSuites() {
        this.testSuiteList = TestSuiteDao.instance.findAll(Filters.empty());
        return SUCCESS.toUpperCase();
    }

    public String deleteTestSuite() {
        ObjectId testSuiteId = null;
        try {
            testSuiteId = new ObjectId(this.testSuiteHexId);
        } catch (Exception e) {
            addActionError("Invalid test suite id");
            return ERROR.toUpperCase();
        }
    
        TestSuiteDao.instance.deleteAll(Filters.eq(Constants.ID, testSuiteId));
        return SUCCESS.toUpperCase();
    }

    public void setTestSuiteName(String testSuiteName) {
        this.testSuiteName = testSuiteName;
    }

    public void setTestSuiteHexId(String testSuiteHexId) {
        this.testSuiteHexId = testSuiteHexId;
    }

    public void setSubCategoryList(List<String> subCategoryList) {
        this.subCategoryList = subCategoryList;
    }

    public List<TestSuites> getTestSuiteList() {
        return testSuiteList;
    }

    public void setTestSuiteList(List<TestSuites> testSuites) {
        this.testSuiteList = testSuites;
    }

}