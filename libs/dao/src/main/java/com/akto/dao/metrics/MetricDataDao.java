package com.akto.dao.metrics;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.Log;
import com.akto.dto.metrics.MetricData;
import com.akto.util.DbMode;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
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

        return instance.findAll(Filters.and(filters), 0, 100_000, Sorts.ascending("timestamp"));
    }

    public List<MetricData> getMetricsForTimeRange(long startTime, long endTime, String metricIdPrefix, String instanceId) {
        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.gte("timestamp", startTime));
        filters.add(Filters.lte("timestamp", endTime));

        // Filter by metricId prefix (e.g., "TC_" for Traffic Collector metrics)
        if (metricIdPrefix != null && !metricIdPrefix.isEmpty()) {
            filters.add(Filters.regex("metricId", "^" + metricIdPrefix));
        }
        if (instanceId != null && !instanceId.isEmpty()) {
            filters.add(Filters.eq("instanceId", instanceId));
        }

        return instance.findAll(Filters.and(filters), 0, 100_000, Sorts.ascending("timestamp"));
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

    /**
     * Aggregates Top N items across multiple metric documents within a time range.
     * Uses $unwind -> $group -> $sort -> $limit pipeline to merge top items.
     *
     * @param startTime Start timestamp (inclusive)
     * @param endTime End timestamp (inclusive)
     * @param metricId The metric ID to filter by
     * @param topN Number of top items to return
     * @return List of TopNItem with aggregated values, sorted descending by value
     */
    public List<MetricData.TopNItem> aggregateTopN(long startTime, long endTime, String metricId, int topN) {
        List<Bson> pipeline = Arrays.asList(
                // Match time range and metric ID
                Aggregates.match(Filters.and(
                        Filters.gte("timestamp", startTime),
                        Filters.lte("timestamp", endTime),
                        Filters.eq("metricId", metricId)
                )),
                // Unwind the topItems array
                Aggregates.unwind("$topItems"),
                // Group by key and sum values
                Aggregates.group("$topItems.key",
                        Accumulators.sum("totalValue", "$topItems.value")),
                // Sort by totalValue descending
                Aggregates.sort(Sorts.descending("totalValue")),
                // Limit to top N
                Aggregates.limit(topN)
        );

        List<MetricData.TopNItem> result = new ArrayList<>();
        for (Document doc : getMCollection().aggregate(pipeline, Document.class)) {
            String key = doc.getString("_id");
            Number totalValue = doc.get("totalValue", Number.class);
            result.add(new MetricData.TopNItem(key, totalValue.floatValue()));
        }
        return result;
    }
}