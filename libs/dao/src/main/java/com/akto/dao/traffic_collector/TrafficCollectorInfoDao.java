package com.akto.dao.traffic_collector;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.context.Context;
import com.akto.dto.traffic_collector.TrafficCollectorInfo;
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


    public void updateHeartbeat(String id, String runtimeId) {
        instance.updateOne(
                Filters.eq("_id", id),
                Updates.combine(
                        Updates.set(TrafficCollectorInfo.LAST_HEARTBEAT, Context.now()),
                        Updates.setOnInsert(TrafficCollectorInfo.START_TIME, Context.now()),
                        Updates.setOnInsert(TrafficCollectorInfo.RUNTIME_ID, Context.now())
                )
        );
    }
}
