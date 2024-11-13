package com.akto.dao;

import com.akto.dto.TestReport;

public class TestReportsDao extends CommonContextDao<TestReport> {

    public static TestReportsDao instance = new TestReportsDao();

    @Override
    public String getCollName() {
        return "test_reports";
    }

    @Override
    public Class<TestReport> getClassT() {
        return TestReport.class;
    }
}
