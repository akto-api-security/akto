package com.akto.dao.endpoint_shield;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.endpoint_shield.FileInspectionResult;
import com.mongodb.client.MongoDatabase;

public class FileInspectionResultDao extends AccountsContextDao<FileInspectionResult> {

    public static final FileInspectionResultDao instance = new FileInspectionResultDao();
    public static final String COLLECTION_NAME = "file_inspection_results";

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
            db.createCollection(getCollName());
        }

        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{FileInspectionResult.RULE_ID, FileInspectionResult.EXECUTED_AT}, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{FileInspectionResult.DEVICE_ID, FileInspectionResult.EXECUTED_AT}, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{FileInspectionResult.DEVICE_ID, FileInspectionResult.RULE_ID}, true);
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
