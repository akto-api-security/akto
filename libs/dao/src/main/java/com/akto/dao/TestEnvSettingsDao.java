package com.akto.dao;

import com.akto.dto.TestEnvSettings;

public class TestEnvSettingsDao extends AccountsContextDao<TestEnvSettings> {

    public static final TestEnvSettingsDao instance = new TestEnvSettingsDao();

    private TestEnvSettingsDao() {}

    @Override
    public String getCollName() {
        return "test_env_settings";
    }

    @Override
    public Class<TestEnvSettings> getClassT() {
        return TestEnvSettings.class;
    }

    public TestEnvSettings get(int id) {
        return findOne("_id", id);
    }
    
}
