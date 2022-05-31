package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.testing.TestingRun;

public class TestingRunDao extends AccountsContextDao<TestingRun> {

    public static final TestingRunDao instance = new TestingRunDao();

    @Override
    public String getCollName() {
        return "testing_run";
    }

    @Override
    public Class<TestingRun> getClassT() {
        return TestingRun.class;
    }
}
