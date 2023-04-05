package com.akto.dao.traffic_metrics;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.traffic_metrics.TrafficMetrics;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;

import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;

public class TrafficMetricsDao extends AccountsContextDao<TrafficMetrics> {

    public static final TrafficMetricsDao instance = new TrafficMetricsDao();
    public static final String ID = "_id.";

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

    public void createIndicesIfAbsent() {
        boolean exists = false;
        for (String col: clients[0].getDatabase(Context.accountId.get()+"").listCollectionNames()){
            if (getCollName().equalsIgnoreCase(col)){
                exists = true;
                break;
            }
        };

        if (!exists) {
            clients[0].getDatabase(Context.accountId.get()+"").createCollection(getCollName());
        }
        
        MongoCursor<Document> cursor = instance.getMCollection().listIndexes().cursor();
        int counter = 0;
        while (cursor.hasNext()) {
            counter++;
            cursor.next();
        }


        if (counter == 1) {
            String[] fieldNames = {
                    ID+ TrafficMetrics.Key.NAME,
                    ID+ TrafficMetrics.Key.BUCKET_START_EPOCH,
                    ID+ TrafficMetrics.Key.BUCKET_END_EPOCH,
                    ID+ TrafficMetrics.Key.IP,
                    ID+ TrafficMetrics.Key.HOST,
                    ID+ TrafficMetrics.Key.VXLAN_ID,
            };
            instance.getMCollection().createIndex(Indexes.ascending(fieldNames));
        }


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
