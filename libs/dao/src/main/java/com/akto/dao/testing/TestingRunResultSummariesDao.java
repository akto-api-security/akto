package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.testing.TestingRunResultSummary;

public class TestingRunResultSummariesDao extends AccountsContextDao<TestingRunResultSummary> {

    public static final TestingRunResultSummariesDao instance = new TestingRunResultSummariesDao();

    private TestingRunResultSummariesDao() {}

    @Override
    public String getCollName() {
        return "testing_run_result_summaries";
    }

    @Override
    public Class<TestingRunResultSummary> getClassT() {
        return TestingRunResultSummary.class;
    }
}
