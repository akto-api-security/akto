package com.akto.action.testing;

import com.akto.action.UserAction;
import com.akto.dao.RBACDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.DefaultTestSuitesDao;
import com.akto.dao.testing.config.TestSuiteDao;
import com.akto.dto.RBAC;
import com.akto.dto.User;
import com.akto.dto.testing.DefaultTestSuites;
import com.akto.dto.testing.config.TestSuites;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.util.StringUtils;

public class TestSuiteAction extends UserAction {
    private String testSuiteHexId;
    private String testSuiteName;
    private List<String> subCategoryList;
    private List<TestSuites> testSuiteList;

    private String generateUniqueTestSuiteName(String baseName) {
        String copiedString = new String(baseName);
        baseName = baseName.replaceAll("\\s*\\(\\d+\\)$", "");
        Pattern regexPattern = Pattern.compile("^" + Pattern.quote(baseName) + "(?: \\((\\d+)\\))?$",
                Pattern.CASE_INSENSITIVE);

        List<String> existingNames = TestSuiteDao.instance.findAll(Filters.regex("name", regexPattern))
                .stream()
                .map(TestSuites::getName)
                .collect(Collectors.toList());

        int maxCount = -1;
        for (String name : existingNames) {

            Matcher matcher = regexPattern.matcher(name);
            if (matcher.matches()) {
                if (matcher.group(1) != null) {
                    int currentCount = Integer.parseInt(matcher.group(1));
                    maxCount = Math.max(maxCount, currentCount);
                } else {
                    maxCount = Math.max(maxCount, 0);
                }
            }
        }
        return maxCount == -1 ? copiedString : baseName + " (" + (maxCount + 1) + ")";
    }

    public String createTestSuite() {
        if (this.testSuiteName == null || this.testSuiteName.trim().isEmpty()) {
            addActionError("Invalid test suite name");
            return ERROR.toUpperCase();
        }

        String newTestSuiteName = generateUniqueTestSuiteName(this.testSuiteName);

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
                new TestSuites(newTestSuiteName, this.subCategoryList, user.getLogin(), timeNow, timeNow));

        return SUCCESS.toUpperCase();
    }

    public String modifyTestSuite() {

        if (!StringUtils.hasText(this.testSuiteHexId)) {
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
            updates.add(Updates.set(TestSuites.NAME, this.testSuiteName));
        }

        if (this.subCategoryList != null) {
            updates.add(Updates.set(TestSuites.SUB_CATEGORY_LIST, this.subCategoryList));
        }
        updates.add(Updates.set(TestSuites.LAST_UPDATED, (long) (System.currentTimeMillis() / 1000l)));
        TestSuiteDao.instance.updateOne(
                Filters.eq(Constants.ID, testSuiteId),
                Updates.combine(updates));

        return SUCCESS.toUpperCase();
    }

    private List<DefaultTestSuites> defaultTestSuites;
    public String getAllTestSuites() {
        this.testSuiteList = TestSuiteDao.instance.findAll(Filters.empty());
        this.defaultTestSuites = DefaultTestSuitesDao.instance.findAll(Filters.empty());
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

    public List<DefaultTestSuites> getDefaultTestSuites() {
        return defaultTestSuites;
    }

    public void setDefaultTestSuites(List<DefaultTestSuites> defaultTestSuites) {
        this.defaultTestSuites = defaultTestSuites;
    }
}