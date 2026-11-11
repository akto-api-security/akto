package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.util.Constants;
import com.akto.util.enums.MongoDBEnums;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

public class TestingRunConfigDao extends AccountsContextDao<TestingRunConfig> {
    @Override
    public String getCollName() {
        return MongoDBEnums.Collection.TESTING_RUN_CONFIG.getCollectionName();
    }

    public static final TestingRunConfigDao instance = new TestingRunConfigDao();

    private TestingRunConfigDao() {}

    public void createIndicesIfAbsent() {
        createCollectionIfAbsent(getDBName(), getCollName(), new CreateCollectionOptions());
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{TestingRunConfig.PRE_REQUEST_SCRIPT_ID}, true);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{TestingRunConfig.POST_REQUEST_SCRIPT_ID}, true);
    }

    /** True if any config explicitly selects this script as its pre- or post-request script. */
    public boolean existsReferenceToScript(String scriptId) {
        if (scriptId == null || scriptId.isEmpty()) {
            return false;
        }
        return findOne(
            Filters.or(
                Filters.eq(TestingRunConfig.PRE_REQUEST_SCRIPT_ID, scriptId),
                Filters.eq(TestingRunConfig.POST_REQUEST_SCRIPT_ID, scriptId)
            ),
            Projections.include(Constants.ID)
        ) != null;
    }

    @Override
    public Class<TestingRunConfig> getClassT() {
        return TestingRunConfig.class;
    }
}
