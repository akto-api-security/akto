package com.akto.dao.traffic_metrics;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.traffic_metrics.TrafficMetrics;
import com.mongodb.client.model.Filters;
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

    @Override
    public String getCollName() {
        return "traffic_metrics";
    }

    @Override
    public Class<TrafficMetrics> getClassT() {
        return TrafficMetrics.class;
    }
}
