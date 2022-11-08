package com.akto.dao.testing_run_findings;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.test_run_findings.TestingRunIssues;
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
