package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.testing.TestingRun;
import com.mongodb.client.model.CreateCollectionOptions;

public class TestingRunDao extends AccountsContextDao<TestingRun> {

    public static final TestingRunDao instance = new TestingRunDao();

    public void createIndicesIfAbsent() {
        String dbName = Context.accountId.get()+"";
        createCollectionIfAbsent(dbName, getCollName(), new CreateCollectionOptions());

        String[] fieldNames = {TestingRun.SCHEDULE_TIMESTAMP};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames,false);

        fieldNames = new String[]{TestingRun.END_TIMESTAMP};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames,false);

    }

    @Override
    public String getCollName() {
        return "testing_run";
    }

    @Override
    public Class<TestingRun> getClassT() {
        return TestingRun.class;
    }
}
