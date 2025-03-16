package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
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

        boolean exists = false;
        for (String col: clients[0].getDatabase(Context.accountId.get()+"").listCollectionNames()){
            if (getCollName().equalsIgnoreCase(col)){
                exists = true;
                break;
            }
        };

        if (!exists) {
            clients[0].getDatabase(Context.accountId.get()+"").createCollection(getCollName());
        }

        String[] fieldNames = {TestingRunConfig.TEST_ROLE_ID};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames,true);
    }
}
