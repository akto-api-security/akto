package com.akto.dao.traffic_metrics;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.traffic_metrics.TrafficMetrics;
import com.akto.util.DbMode;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;

import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrafficMetricsDao extends AccountsContextDao<TrafficMetrics> {

    public static final TrafficMetricsDao instance = new TrafficMetricsDao();
    public static final String ID = "_id.";

    public static final int maxDocuments = 30_000;
    public static final int sizeInBytes = 100_000_000;


    public List<Bson> basicFilters(List<TrafficMetrics.Name> names, int startTimestamp, int endTimestamp) {
        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.in(ID + TrafficMetrics.Key.NAME, names));
        filters.add(Filters.gte(ID + TrafficMetrics.Key.BUCKET_START_EPOCH, startTimestamp));
        filters.add(Filters.lte(ID + TrafficMetrics.Key.BUCKET_END_EPOCH, endTimestamp));

        return filters;
    }

    public static Bson filtersForUpdate(TrafficMetrics.Key key) {
        return Filters.and(
                Filters.eq(ID + TrafficMetrics.Key.NAME, key.getName()),
                Filters.eq(ID + TrafficMetrics.Key.BUCKET_START_EPOCH, key.getBucketStartEpoch()),
                Filters.eq(ID + TrafficMetrics.Key.BUCKET_END_EPOCH, key.getBucketEndEpoch()),
                Filters.eq(ID + TrafficMetrics.Key.IP, key.getIp()),
                Filters.eq(ID + TrafficMetrics.Key.HOST,  key.getHost()),
                Filters.eq(ID + TrafficMetrics.Key.VXLAN_ID,  key.getVxlanID())
        );
    }

    public static Map<String, Object> getFiltersMap(TrafficMetrics.Key key) {
        Map<String, Object> filterMap = new HashMap<>();
        filterMap.put(ID + TrafficMetrics.Key.NAME, key.getName());
        filterMap.put(ID + TrafficMetrics.Key.BUCKET_START_EPOCH, key.getBucketStartEpoch());
        filterMap.put(ID + TrafficMetrics.Key.BUCKET_END_EPOCH, key.getBucketEndEpoch());
        filterMap.put(ID + TrafficMetrics.Key.IP, key.getIp());
        filterMap.put(ID + TrafficMetrics.Key.HOST, key.getHost());
        filterMap.put(ID + TrafficMetrics.Key.VXLAN_ID, key.getVxlanID());
        return filterMap;
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
            if (DbMode.allowCappedCollections()) {
                clients[0].getDatabase(Context.accountId.get()+"").createCollection(getCollName(), new CreateCollectionOptions().capped(true).maxDocuments(maxDocuments).sizeInBytes(sizeInBytes));
            } else {
                db.createCollection(getCollName());
            }
        }

        String[] fieldNames = {
                ID+ TrafficMetrics.Key.NAME,
                ID+ TrafficMetrics.Key.BUCKET_START_EPOCH,
                ID+ TrafficMetrics.Key.BUCKET_END_EPOCH,
                ID+ TrafficMetrics.Key.IP,
                ID+ TrafficMetrics.Key.HOST,
                ID+ TrafficMetrics.Key.VXLAN_ID,
        };
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

    }

    @Override
    public String getCollName() {
        return "traffic_metrics";
    }

    @Override
    public Class<TrafficMetrics> getClassT() {
        return TrafficMetrics.class;
    }
}
