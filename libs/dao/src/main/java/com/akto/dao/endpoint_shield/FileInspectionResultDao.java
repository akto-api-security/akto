package com.akto.dao.endpoint_shield;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.endpoint_shield.FileInspectionResult;
import com.akto.util.DbMode;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;

public class FileInspectionResultDao extends AccountsContextDao<FileInspectionResult> {

    public static final FileInspectionResultDao instance = new FileInspectionResultDao();
    public static final String COLLECTION_NAME = "file_inspection_results";

    public static final int maxDocuments = 200_000;
    public static final long sizeInBytes = 200_000_000L;

    public void createIndicesIfAbsent() {
        boolean exists = false;
        String dbName = Context.accountId.get() + "";
        MongoDatabase db = clients[0].getDatabase(dbName);
        for (String col : db.listCollectionNames()) {
            if (getCollName().equalsIgnoreCase(col)) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            if (DbMode.allowCappedCollections()) {
                db.createCollection(getCollName(),
                    new CreateCollectionOptions().capped(true).maxDocuments(maxDocuments).sizeInBytes(sizeInBytes));
            } else {
                db.createCollection(getCollName());
            }
        }

        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{FileInspectionResult.RULE_ID, FileInspectionResult.EXECUTED_AT}, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{FileInspectionResult.DEVICE_ID, FileInspectionResult.EXECUTED_AT}, false);
    }

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<FileInspectionResult> getClassT() {
        return FileInspectionResult.class;
    }
}
