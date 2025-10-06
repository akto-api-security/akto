package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.Log;
import com.mongodb.client.MongoDatabase;

public class RuntimePersistentLogsDao extends AccountsContextDao<Log> {

    public static final RuntimePersistentLogsDao instance = new RuntimePersistentLogsDao();

    public void createIndicesIfAbsent() {
        boolean exists = false;
        String dbName = Context.accountId.get()+"";
        MongoDatabase db = clients[0].getDatabase(dbName);
        for (String col: db.listCollectionNames()){
            if (getCollName().equalsIgnoreCase(col)){
                exists = true;
                break;
            }
        };

        if (!exists) {
            // Intentionally create as uncapped to avoid eviction of important logs
            db.createCollection(getCollName());
        }

        String[] fieldNames = {Log.TIMESTAMP};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames,false);
    }

    @Override
    public String getCollName() {
        return "logs_runtime_persistent";
    }

    @Override
    public Class<Log> getClassT() {
        return Log.class;
    }
}


