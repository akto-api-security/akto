package com.akto.dao.metrics;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.Log;
import com.akto.dto.metrics.MetricData;
import com.akto.util.DbMode;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class MetricDataDao extends AccountsContextDao<MetricData> {
    public static final MetricDataDao instance = new MetricDataDao();

    public static final int maxDocuments = 100_000;
    public static final int sizeInBytes = 100_000_000;
    
    // Track last cleanup time per account
    private static final ConcurrentHashMap<Integer, Integer> lastCleanupTimeMap = new ConcurrentHashMap<>();
    private static final int CLEANUP_INTERVAL = 3600; // 1 hour in seconds
    private static final int METRIC_RETENTION_PERIOD = 7 * 24 * 3600; // 7 days in seconds

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
        return "metrics_data";
    }

    @Override
    public Class<MetricData> getClassT() {
        return MetricData.class;
    }

    /**
     * Delete old metrics data that is older than the retention period
     * @return number of documents deleted
     */
    public long deleteOldMetrics() {
        int currentTime = Context.now();
        int cutoffTime = currentTime - METRIC_RETENTION_PERIOD;
        
        Bson filter = Filters.lt(Log.TIMESTAMP, cutoffTime);
        return getMCollection().deleteMany(filter).getDeletedCount();
    }

    /**
     * Check if cleanup should be performed for the current account
     * @return true if cleanup should be performed
     */
    public boolean shouldPerformCleanup() {
        int accountId = Context.accountId.get();
        int currentTime = Context.now();
        
        Integer lastCleanupTime = lastCleanupTimeMap.get(accountId);
        if (lastCleanupTime == null || (currentTime - lastCleanupTime) >= CLEANUP_INTERVAL) {
            lastCleanupTimeMap.put(accountId, currentTime);
            return true;
        }
        return false;
    }
}