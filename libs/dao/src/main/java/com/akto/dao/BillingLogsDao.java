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

public class BillingLogsDao extends AccountsContextDao<Log> {

    public static final BillingLogsDao instance = new BillingLogsDao();

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

        MongoCursor<Document> cursor = db.getCollection(getCollName()).listIndexes().cursor();
        List<Document> indices = new ArrayList<>();

        while (cursor.hasNext()) {
            indices.add(cursor.next());
        }

        if (indices.size() == 1) {
            instance.getMCollection().createIndex(Indexes.descending(Log.TIMESTAMP));
        }
    }

    @Override
    public String getCollName() {
        return "logs_billing";
    }

    @Override
    public Class<Log> getClassT() {
        return Log.class;
    }

}
