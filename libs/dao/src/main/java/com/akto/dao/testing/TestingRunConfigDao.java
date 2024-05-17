package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.util.enums.MongoDBEnums;

public class TestingRunConfigDao extends AccountsContextDao<TestingRunConfig> {
    @Override
    public String getCollName() {
        return MongoDBEnums.Collection.TESTING_RUN_CONFIG.getCollectionName();
    }

    public static final TestingRunConfigDao instance = new TestingRunConfigDao();

    private TestingRunConfigDao() {}
    
    @Override
    public Class<TestingRunConfig> getClassT() {
        return TestingRunConfig.class;
    }
}
