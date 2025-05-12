package com.akto.dao.metrics;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.metrics.MetricData;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;

public class MetricDataDao extends AccountsContextDao<MetricData> {
    public static final MetricDataDao instance = new MetricDataDao();

    @Override
    public String getCollName() {
        return "metrics_data";
    }

    @Override
    public Class<MetricData> getClassT() {
        return MetricData.class;
    }

    public List<MetricData> getMetricsForTimeRange(String metricId, int accountId, long startTime, long endTime) {
        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.eq("metricId", metricId));
        filters.add(Filters.eq("accountId", accountId));
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

    public List<MetricData> getMetricsForTimeRangeByHost(String metricId, int accountId, String host, long startTime, long endTime) {
        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.eq("metricId", metricId));
        filters.add(Filters.eq("accountId", accountId));
        filters.add(Filters.eq("instanceId", host));
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
} 