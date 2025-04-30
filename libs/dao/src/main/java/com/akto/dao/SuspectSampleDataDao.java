package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.traffic.SuspectSampleData;
import com.akto.util.Constants;
import com.akto.util.DbMode;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;

public class SuspectSampleDataDao extends AccountsContextDao<SuspectSampleData> {
    public static final SuspectSampleDataDao instance = new SuspectSampleDataDao();

    public static final int maxDocuments = 100_000;
    public static final int sizeInBytes = 100_000_000;

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

        String[] fieldNames = { SuspectSampleData._DISCOVERED, SuspectSampleData.SOURCE_IPS, SuspectSampleData.MATCHING_URL, SuspectSampleData.API_COLLECTION_ID, Constants.ID };
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);
    }

    @Override
    public String getCollName() {
        return "suspect_sample_data";
    }

    @Override
    public Class<SuspectSampleData> getClassT() {
        return SuspectSampleData.class;
    }
}
