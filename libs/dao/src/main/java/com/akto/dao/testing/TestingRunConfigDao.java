package com.akto.dao.testing;

import com.mongodb.client.model.CreateCollectionOptions;
import com.akto.dao.MCollection;
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

    public void createIndicesIfAbsent() {
        createCollectionIfAbsent(getDBName(), getCollName(), new CreateCollectionOptions());
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{TestingRunConfig.PRE_REQUEST_SCRIPT_ID}, true);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{TestingRunConfig.POST_REQUEST_SCRIPT_ID}, true);
    }
}
