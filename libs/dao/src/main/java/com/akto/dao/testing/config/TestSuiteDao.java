package com.akto.dao.testing.config;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.testing.config.TestSuite;

public class TestSuiteDao extends AccountsContextDao<TestSuite>{

    public static final TestSuiteDao instance = new TestSuiteDao();

    @Override
    public String getCollName() {
        return "test_suite";
    }

    @Override
    public Class<TestSuite> getClassT() {
        return TestSuite.class;
    }
    
}