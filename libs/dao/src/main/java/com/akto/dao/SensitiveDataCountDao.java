package com.akto.dao;

import com.akto.dto.traffic.SensitiveDataCount;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoWriteException;
import com.mongodb.bulk.BulkWriteError;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger logger = LoggerFactory.getLogger(SensitiveDataCountDao.class);

    /**
     * Margin over the longest window an alert rule may ask for. Covers a late flush for an old
     * hour and the fact that Mongo's TTL monitor sweeps periodically rather than deleting exactly
     * on expiry.
     */
    public static final int RETENTION_SAFETY_DAYS = 10;

    /**
     * Buckets are kept for the longest window an alert rule may use, plus that margin.
     *
     * This has to cover the alert side's cap, which is 90 days. expireAt is written here, by the
     * service producing the counts - the alert job never writes it - so a cap larger than what is
     * retained would leave the aggregation summing only the buckets that survived the TTL. It
     * would report a total below the real one and the threshold would simply never trip: a wrong
     * answer with no error.
     *
     * Once SensitiveDataAlertRule exists on this branch this should become
     * MAX_WINDOW_DAYS + RETENTION_SAFETY_DAYS so the two cannot drift apart.
     */
    public static final int RETENTION_DAYS = 90 + RETENTION_SAFETY_DAYS;

    /** Keys of requests already applied, so a retried request is not counted twice. */
    public static final String APPLIED_REQUESTS_COLLECTION = "sensitive_data_count_requests";

    private SensitiveDataCountDao() {}

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<SensitiveDataCount> getClassT() {
        return SensitiveDataCount.class;
    }

    private MongoCollection<Document> appliedRequests() {
        return clients[0].getDatabase(getDBName()).getCollection(APPLIED_REQUESTS_COLLECTION);
    }

    /**
     * Applies the increments once per idempotency key: a retry of a request that already went through
     * (say, one whose response timed out) is skipped. The key is recorded before applying and removed
     * again if applying fails, so a failed request can still be retried.
     *
     * If only part of the batch fails, the key is kept, so a retry cannot apply the rest a second time,
     * and the failed increments get one more try here. If they fail again they are dropped: a lost
     * increment is better than a doubled one.
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
        List<SensitiveDataCount> valid = new ArrayList<>();
        for (SensitiveDataCount inc : increments == null ? new ArrayList<SensitiveDataCount>() : increments) {
            if (isValid(inc)) valid.add(inc);
        }
        try {
            bulkInc(valid);
        } catch (MongoBulkWriteException e) {
            List<SensitiveDataCount> failed = new ArrayList<>();
            for (BulkWriteError error : e.getWriteErrors()) {
                failed.add(valid.get(error.getIndex()));
            }
            try {
                bulkInc(failed);
            } catch (RuntimeException retryError) {
                logger.error("dropped " + failed.size() + " of " + valid.size()
                        + " sensitive data count increments for request " + idempotencyKey, retryError);
            }
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
            if (!isValid(inc)) continue;

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

    private static boolean isValid(SensitiveDataCount inc) {
        return inc != null && inc.getSubType() != null && inc.getCount() > 0;
    }
}
