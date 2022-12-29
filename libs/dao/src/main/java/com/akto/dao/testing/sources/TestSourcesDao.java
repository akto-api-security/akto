package com.akto.dao.testing.sources;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.testing.sources.TestSource;

public class TestSourcesDao extends AccountsContextDao<TestSource> {

    public static final TestSourcesDao instance = new TestSourcesDao();

    private TestSourcesDao() {}

    @Override
    public String getCollName() {
        
        return "test_sources";
    }

    @Override
    public Class<TestSource> getClassT() {
        return TestSource.class;
    }
    
}
