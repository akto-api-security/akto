package com.akto.dao.test_editor;

import org.bson.conversions.Bson;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.test_editor.TestingRunPlayground;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

public class TestingRunPlaygroundDao extends AccountsContextDao<TestingRunPlayground> {
    public static final TestingRunPlaygroundDao instance = new TestingRunPlaygroundDao();

    public void createIndicesIfAbsent() {
        String dbName = Context.accountId.get() + "";
        createCollectionIfAbsent(dbName, getCollName(), new CreateCollectionOptions());

        String[] fieldNames = new String[] { TestingRunPlayground.CREATED_AT };
        Bson indexInfo = Indexes.ascending(fieldNames);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), indexInfo,
                new IndexOptions().expireAfter(300L, java.util.concurrent.TimeUnit.SECONDS));
    }

    @Override
    public String getCollName() {
        return "testing_run_playground";
    }

    @Override
    public Class<TestingRunPlayground> getClassT() {
        return TestingRunPlayground.class;
    }
}
