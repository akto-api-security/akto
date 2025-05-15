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

        return instance.findAll(Filters.and(filters), 0, 100_000, Sorts.ascending("timestamp"));
    }
}