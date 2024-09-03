package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.traffic.SusSampleData;
import com.akto.util.Constants;
import com.akto.util.DbMode;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;

public class SusSampleDataDao extends AccountsContextDao<SusSampleData> {
    public static final SusSampleDataDao instance = new SusSampleDataDao();

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

        String[] fieldNames = { SusSampleData._DISCOVERED, SusSampleData.SOURCE_IPS, SusSampleData.MATCHING_URL, SusSampleData.API_COLLECTION_ID, Constants.ID };
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);
    }

    @Override
    public String getCollName() {
        return "sus_sample_data";
    }

    @Override
    public Class<SusSampleData> getClassT() {
        return SusSampleData.class;
    }
}
