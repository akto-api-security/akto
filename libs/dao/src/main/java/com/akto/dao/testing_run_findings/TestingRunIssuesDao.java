package com.akto.dao.testing_run_findings;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.util.enums.MongoDBEnums;

public class TestingRunIssuesDao extends AccountsContextDao<TestingRunIssues> {

    public static final TestingRunIssuesDao instance = new TestingRunIssuesDao();

    public void createIndicesIfAbsent() {

        boolean exists = false;
        for (String col : clients[0].getDatabase(Context.accountId.get() + "").listCollectionNames()) {
            if (getCollName().equalsIgnoreCase(col)) {
                exists = true;
                break;
            }
        }

        if (!exists) {
            clients[0].getDatabase(Context.accountId.get() + "").createCollection(getCollName());
        }

        String[] fieldNames = {TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestingRunIssues.CREATION_TIME};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

    }

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
