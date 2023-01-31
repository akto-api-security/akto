package com.akto.dao;

import com.akto.dto.TestRun;

public class TestRunDao extends AccountsContextDao<TestRun> {

    public static final TestRunDao instance = new TestRunDao();

    private TestRunDao() {}

    @Override
    public String getCollName() {
        return "test_run";
    }

    @Override
    public Class<TestRun> getClassT() {
        return TestRun.class;
    }

    public int createRun(TestRun testRun) {
        return insertOne(testRun).getInsertedId().asInt32().getValue();
    }    
}
