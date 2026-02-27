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

public class MetricDataDao extends AccountsContextDao<MetricData> {
    public static final MetricDataDao instance = new MetricDataDao();

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
        return "metrics_data";
    }

    @Override
    public Class<MetricData> getClassT() {
        return MetricData.class;
    }

    public List<MetricData> getMetricsForTimeRange(long startTime, long endTime) {
        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.gte("timestamp", startTime));
        filters.add(Filters.lte("timestamp", endTime));

        MongoCursor<MetricData> cursor = getMCollection().find(Filters.and(filters))
                .sort(Sorts.ascending("timestamp"))
                .iterator();

        List<MetricData> metrics = new ArrayList<>();
        while (cursor.hasNext()) {
            metrics.add(cursor.next());
        }
        cursor.close();
        return metrics;
    }

    public List<MetricData> getMetricsForModule(long startTime, long endTime, String moduleType) {
        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.gte("timestamp", startTime));
        filters.add(Filters.lte("timestamp", endTime));
        filters.add(Filters.eq("moduleType", moduleType));

        return instance.findAll(Filters.and(filters), 0, 100_000, Sorts.ascending("timestamp"));
    }

    public List<MetricData> getMetricsForModule(long startTime, long endTime, String moduleType, String instanceId) {
        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.gte("timestamp", startTime));
        filters.add(Filters.lte("timestamp", endTime));
        filters.add(Filters.eq("moduleType", moduleType));

        if (instanceId != null && !instanceId.isEmpty()) {
            filters.add(Filters.eq("instanceId", instanceId));
        }

        return instance.findAll(Filters.and(filters), 0, 100_000, Sorts.ascending("timestamp"));
    }
}