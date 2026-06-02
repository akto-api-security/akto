package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.Log;
import com.akto.util.DbMode;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;

public class GuardrailsServiceLogsDao extends LogsDao {

    public static final int maxDocuments = 100_000;
    public static final int sizeInBytes = 100_000_000;

    public static final GuardrailsServiceLogsDao instance = new GuardrailsServiceLogsDao();

    public void createIndicesIfAbsent() {
        String dbName = Context.accountId.get() + "";
        if (clients == null || clients.length == 0 || clients[0] == null) {
            return;
        }
        MongoDatabase db = clients[0].getDatabase(dbName);
        if (db == null) {
            return;
        }

        try {
            if (DbMode.allowCappedCollections()) {
                db.createCollection(
                    getCollName(),
                    new CreateCollectionOptions()
                        .capped(true)
                        .maxDocuments(maxDocuments)
                        .sizeInBytes(sizeInBytes)
                );
            } else {
                db.createCollection(getCollName());
            }
        } catch (com.mongodb.MongoCommandException e) {
            // NamespaceExists = collection already exists
            if (e.getErrorCode() != 48) {
                throw e;
            }
        }

        String[] fieldNames = {Log.TIMESTAMP};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);
    }

    @Override
    public String getCollName() {
        return "logs_guardrails_service";
    }

    @Override
    public Class<Log> getClassT() {
        return Log.class;
    }
}
