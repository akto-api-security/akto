package com.akto.dao.testing.config;

import com.akto.dao.*;
import com.akto.dto.testing.config.TestScript;

public class TestScriptsDao extends AccountsContextDao<TestScript> {

    public static final TestScriptsDao instance = new TestScriptsDao();

    private TestScriptsDao() {}

    @Override
    public String getCollName() {
        return "test_collection_properties";
    }

    @Override
    public Class<TestScript> getClassT() {
        return TestScript.class;
    }
}
