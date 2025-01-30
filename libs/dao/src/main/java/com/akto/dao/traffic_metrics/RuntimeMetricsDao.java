package com.akto.dao.traffic_metrics;

import java.util.ArrayList;

import org.bson.conversions.Bson;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.traffic_metrics.RuntimeMetrics;
import com.akto.dto.type.URLMethods;
import com.akto.util.DbMode;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.WriteModel;

public class RuntimeMetricsDao extends AccountsContextDao<RuntimeMetrics> {
    
    public static final RuntimeMetricsDao instance = new RuntimeMetricsDao();
    public static final int maxDocuments = 100_000;
    public static final int sizeInBytes = 100_000_000;
    
    @Override
    public String getCollName() {
        return "runtime_metrics";
    }

    @Override
    public Class<RuntimeMetrics> getClassT() {
        return RuntimeMetrics.class;
    }

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
            db.createCollection(getCollName());
        }

        if (!exists) {
            if (DbMode.allowCappedCollections()) {
                db.createCollection(getCollName(), new CreateCollectionOptions().capped(true).maxDocuments(maxDocuments).sizeInBytes(sizeInBytes));
            } else {
                db.createCollection(getCollName());
            }
        }

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[] { "timestamp" }, true);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[] { "timestamp", "instanceId" }, true);
    }

    public static void bulkInsertMetrics(ArrayList<WriteModel<RuntimeMetrics>> bulkUpdates) {
        RuntimeMetricsDao.instance. getMCollection().bulkWrite(bulkUpdates);
    }

    public static Bson buildFilters(int startTs, int endTs) {
        return Filters.and(
                Filters.gte("timestamp", startTs),
                Filters.lte("timestamp", endTs)
        );
    }

    public static Bson buildFilters(int startTs, int endTs, String instanceId) {
        return Filters.and(
                Filters.gte("timestamp", startTs),
                Filters.lte("timestamp", endTs),
                Filters.eq("instanceId", instanceId)
        );
    }

}
