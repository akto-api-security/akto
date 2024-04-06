package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.Log;
import com.akto.util.DbMode;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class AnalyserLogsDao extends AccountsContextDao<Log> {

    public static final AnalyserLogsDao instance = new AnalyserLogsDao();

    public static final int maxDocuments = 100_000;
    public static final int sizeInBytes = 100_000_000;
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
            if (DbMode.allowCappedCollections()) {
                db.createCollection(getCollName(), new CreateCollectionOptions().capped(true).maxDocuments(maxDocuments).sizeInBytes(sizeInBytes));
            } else {
                db.createCollection(getCollName());
            }
        }

        String[] fieldNames = {Log.TIMESTAMP};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames,false);
    }

    @Override
    public String getCollName() {
        return "logs_analyser";
    }

    @Override
    public Class<Log> getClassT() {
        return Log.class;
    }

}
