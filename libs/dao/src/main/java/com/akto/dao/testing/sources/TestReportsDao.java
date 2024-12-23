package com.akto.dao.testing.sources;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.testing.sources.TestReports;

public class TestReportsDao extends AccountsContextDao<TestReports> {

    public final static TestReportsDao instance = new TestReportsDao();

    private TestReportsDao() {}

    @Override
    public String getCollName() {
        return "test_reports";
    }

    @Override
    public Class<TestReports> getClassT() {
        return TestReports.class;
    }
}
