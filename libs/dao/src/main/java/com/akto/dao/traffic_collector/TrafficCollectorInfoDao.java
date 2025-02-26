package com.akto.dao.traffic_collector;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.context.Context;
import com.akto.dto.traffic_collector.TrafficCollectorInfo;
import com.akto.util.DbMode;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public class TrafficCollectorInfoDao extends AccountsContextDao<TrafficCollectorInfo> {

    public static final TrafficCollectorInfoDao instance = new TrafficCollectorInfoDao();

    @Override
    public String getCollName() {
        return "traffic_collector_info";
    }

    @Override
    public Class<TrafficCollectorInfo> getClassT() {
        return TrafficCollectorInfo.class;
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


    public void updateHeartbeat(String id, String runtimeId) {
        instance.updateOne(
                Filters.eq("_id", id),
                Updates.combine(
                        Updates.set(TrafficCollectorInfo.LAST_HEARTBEAT, Context.now()),
                        Updates.setOnInsert(TrafficCollectorInfo.START_TIME, Context.now()),
                        Updates.setOnInsert(TrafficCollectorInfo.RUNTIME_ID, runtimeId)
                )
        );
    }
}
