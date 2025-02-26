package com.akto.dao.traffic_collector;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.context.Context;
import com.akto.dto.traffic_collector.TrafficCollectorInfo;
import com.akto.dto.traffic_collector.TrafficCollectorMetrics;
import com.akto.util.DbMode;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.apache.commons.collections.ArrayStack;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TrafficCollectorMetricsDao extends AccountsContextDao<TrafficCollectorMetrics> {

    public static final TrafficCollectorMetricsDao instance = new TrafficCollectorMetricsDao();

    @Override
    public String getCollName() {
        return "traffic_collector_metrics";
    }

    @Override
    public Class<TrafficCollectorMetrics> getClassT() {
        return TrafficCollectorMetrics.class;
    }

    public static final int maxDocuments = 10_000;
    public static final int sizeInBytes = 10_000_000;

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
    }

    public void updateCount(TrafficCollectorMetrics trafficCollectorMetrics) {
        List<Bson> updates = new ArrayList<>();
        Map<String, Integer> requestsCountMapPerMinute = trafficCollectorMetrics.getRequestsCountMapPerMinute();
        if (requestsCountMapPerMinute == null || requestsCountMapPerMinute.isEmpty()) return;
        for (String key: requestsCountMapPerMinute.keySet()) {
            updates.add(Updates.inc(TrafficCollectorMetrics.REQUESTS_COUNT_MAP_PER_MINUTE + "." + key, requestsCountMapPerMinute.getOrDefault(key, 0)));
        }
        instance.updateOne(
                Filters.and(
                        Filters.eq("_id", trafficCollectorMetrics.getId()),
                        Filters.eq(TrafficCollectorMetrics.BUCKET_START_EPOCH, trafficCollectorMetrics.getBucketStartEpoch()),
                        Filters.eq(TrafficCollectorMetrics.BUCKET_END_EPOCH, trafficCollectorMetrics.getBucketEndEpoch())
                ),
                Updates.combine(updates)
        );
    }
}
