package com.akto.dao.testing.config;

import com.akto.dao.*;
import com.akto.dto.testing.config.TestScript;
import com.mongodb.client.model.Filters;

public class TestScriptsDao extends AccountsContextDao<TestScript> {

    public static final TestScriptsDao instance = new TestScriptsDao();

    private TestScriptsDao() {}

    public TestScript fetchTestScript(TestScript.Type type) {
        return TestScriptsDao.instance.findOne(Filters.eq(TestScript.TYPE, type.name()));
    }

    @Override
    public String getCollName() {
        return "test_collection_properties";
    }

    @Override
    public Class<TestScript> getClassT() {
        return TestScript.class;
    }
}
