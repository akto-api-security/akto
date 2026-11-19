package com.akto.service.collections;

import com.akto.dao.ApiCollectionStatsDao;
import com.akto.dto.ApiCollectionStats;
import com.mongodb.client.model.WriteModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Writes one metric of many stats rows in bulk batches. Close it (try-with-resources) to flush the
 * last batch; afterwards resetUnwritten gives the rows it never reached the metric's empty value.
 */
class ApiCollectionStatsMetricWriter implements AutoCloseable {

    private static final int BATCH_SIZE = 200;

    private final ApiCollectionStatsDao dao = ApiCollectionStatsDao.instance;
    private final List<WriteModel<ApiCollectionStats>> writes = new ArrayList<>();
    private final String metricField;
    private final int startedAt;

    ApiCollectionStatsMetricWriter(String metricField, int startedAt) {
        this.metricField = metricField;
        this.startedAt = startedAt;
    }

    void set(int collectionId, Object value) {
        writes.add(dao.setMetric(collectionId, metricField, value, startedAt));
        if (writes.size() >= BATCH_SIZE) dao.bulkWrite(writes);
    }

    @Override
    public void close() {
        dao.bulkWrite(writes);
    }

    void resetUnwritten(Object emptyValue) {
        close();
        dao.resetStale(metricField, emptyValue, startedAt);
    }
}
