package com.akto.dao.testing.sources;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.testing.sources.TestSourceConfig;

public class TestSourceConfigsDao extends AccountsContextDao<TestSourceConfig> {

    public static final TestSourceConfigsDao instance = new TestSourceConfigsDao();

    private TestSourceConfigsDao() {}

    @Override
    public String getCollName() {
        
        return "test_source_configs";
    }

    @Override
    public Class<TestSourceConfig> getClassT() {
        return TestSourceConfig.class;
    }
        
    
}
