package com.akto.dao.testing.config;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.testing.config.TestSuites;

public class TestSuiteDao extends AccountsContextDao<TestSuites>{

    public static final TestSuiteDao instance = new TestSuiteDao();

    @Override
    public String getCollName() {
        return "test_suite";
    }

    @Override
    public Class<TestSuites> getClassT() {
        return TestSuites.class;
    }

}