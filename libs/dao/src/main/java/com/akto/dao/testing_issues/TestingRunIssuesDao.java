package com.akto.dao.testing_issues;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.test_issues.TestingRunIssues;
import com.akto.util.enums.MongoDBEnums;

public class TestingRunIssuesDao extends AccountsContextDao<TestingRunIssues> {

    public static final TestingRunIssuesDao instance = new TestingRunIssuesDao();

    private TestingRunIssuesDao() {}
    @Override
    public String getCollName() {
        return MongoDBEnums.Collection.TESTING_RUN_ISSUES.getCollectionName();
    }

    @Override
    public Class<TestingRunIssues> getClassT() {
        return TestingRunIssues.class;
    }
}
