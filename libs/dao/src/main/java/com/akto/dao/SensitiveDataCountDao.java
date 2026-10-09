package com.akto.dao;

import com.akto.dto.traffic.SensitiveDataCount;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SensitiveDataCountDao extends AccountsContextDao<SensitiveDataCount> {

    public static final String COLLECTION_NAME = "sensitive_data_counts";
    public static final SensitiveDataCountDao instance = new SensitiveDataCountDao();

    /** Longest alert window we expect (a month), plus margin. */
    public static final int RETENTION_DAYS = 40;

    /** Keys of requests already applied, so a retried request is not counted twice. */
    public static final String APPLIED_REQUESTS_COLLECTION = "sensitive_data_count_requests";
    public static final int APPLIED_REQUESTS_TTL_HOURS = 24;

    private SensitiveDataCountDao() {}

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<SensitiveDataCount> getClassT() {
        return SensitiveDataCount.class;
    }

    /**
     * The unique index is what the upsert matches on, and also serves the alert query: one type,
     * hour >= start, grouped by collection. The fields are flat rather than a compound _id
     * because an _id index cannot serve a range on one of its sub-fields.
     */
    public void createIndicesIfAbsent() {
        createCollectionIfAbsent(getDBName(), getCollName(), new CreateCollectionOptions());

        Bson key = Indexes.ascending(SensitiveDataCount.SUB_TYPE, SensitiveDataCount.HOUR,
                SensitiveDataCount.API_COLLECTION_ID);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), key,
                new IndexOptions().name("subType_hour_apiCollectionId").unique(true));

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                Indexes.ascending(SensitiveDataCount.EXPIRE_AT),
                new IndexOptions().name("expireAt_ttl").expireAfter(0L, TimeUnit.SECONDS));

        createCollectionIfAbsent(getDBName(), APPLIED_REQUESTS_COLLECTION, new CreateCollectionOptions());
        MCollection.createIndexIfAbsent(getDBName(), APPLIED_REQUESTS_COLLECTION, Indexes.ascending("createdAt"),
                new IndexOptions().name("createdAt_ttl").expireAfter((long) APPLIED_REQUESTS_TTL_HOURS, TimeUnit.HOURS));
    }

    private MongoCollection<Document> appliedRequests() {
        return clients[0].getDatabase(getDBName()).getCollection(APPLIED_REQUESTS_COLLECTION);
    }

    /**
     * Applies the increments once per idempotency key: a retry of a request that already went through
     * (say, one whose response timed out) is skipped. The key is recorded before applying and removed
     * again if applying fails, so a failed request can still be retried.
     */
    public void bulkInc(String idempotencyKey, List<SensitiveDataCount> increments) {
        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            bulkInc(increments);
            return;
        }
        try {
            appliedRequests().insertOne(new Document("_id", idempotencyKey).append("createdAt", new Date()));
        } catch (MongoWriteException e) {
            if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) return;
            throw e;
        }
        try {
            bulkInc(increments);
        } catch (RuntimeException e) {
            appliedRequests().deleteOne(Filters.eq("_id", idempotencyKey));
            throw e;
        }
    }

    /**
     * Events of one type per collection from {@code fromHour} on, keeping only the collections whose
     * total is above {@code moreThan}.
     */
    public Map<Integer, Long> countsByCollectionAbove(String subType, int fromHour, long moreThan) {
        List<Bson> pipeline = Arrays.asList(
                Aggregates.match(Filters.and(
                        Filters.eq(SensitiveDataCount.SUB_TYPE, subType),
                        Filters.gte(SensitiveDataCount.HOUR, fromHour))),
                Aggregates.group("$" + SensitiveDataCount.API_COLLECTION_ID,
                        Accumulators.sum("total", "$" + SensitiveDataCount.COUNT)),
                Aggregates.match(Filters.gt("total", moreThan)));

        Map<Integer, Long> totals = new HashMap<>();
        for (Document doc : getMCollection().aggregate(pipeline, Document.class)) {
            Object id = doc.get("_id");
            Object total = doc.get("total");
            if (id instanceof Number && total instanceof Number) {
                totals.put(((Number) id).intValue(), ((Number) total).longValue());
            }
        }
        return totals;
    }

    /** Adds each increment to its hourly bucket, creating the bucket if it does not exist yet. */
    public void bulkInc(List<SensitiveDataCount> increments) {
        if (increments == null || increments.isEmpty()) return;

        List<WriteModel<SensitiveDataCount>> writes = new ArrayList<>(increments.size());
        for (SensitiveDataCount inc : increments) {
            if (inc == null || inc.getSubType() == null || inc.getCount() <= 0) continue;

            Bson filter = Filters.and(
                    Filters.eq(SensitiveDataCount.API_COLLECTION_ID, inc.getApiCollectionId()),
                    Filters.eq(SensitiveDataCount.SUB_TYPE, inc.getSubType()),
                    Filters.eq(SensitiveDataCount.HOUR, inc.getHour()));

            // Expiry follows the bucket's own hour rather than when it was written, so a late
            // flush for an old hour does not keep that hour around longer than the rest.
            Date expireAt = new Date((inc.getHour() + TimeUnit.DAYS.toSeconds(RETENTION_DAYS)) * 1000L);

            Bson update = Updates.combine(
                    Updates.inc(SensitiveDataCount.COUNT, inc.getCount()),
                    Updates.setOnInsert(SensitiveDataCount.EXPIRE_AT, expireAt));

            writes.add(new UpdateOneModel<>(filter, update, new UpdateOptions().upsert(true)));
        }
        if (writes.isEmpty()) return;

        getMCollection().bulkWrite(writes, new BulkWriteOptions().ordered(false));
    }
}
