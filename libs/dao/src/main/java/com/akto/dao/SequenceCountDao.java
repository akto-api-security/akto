package com.akto.dao;

import com.akto.dto.SequenceCount;
import com.akto.util.SequenceVomc;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SequenceCountDao extends AccountsContextDao<SequenceCount> {

    public static final SequenceCountDao instance = new SequenceCountDao();

    public static final int BUCKET_SECONDS = 3600;
    public static final int RETENTION_SECONDS = 48 * 3600;

    private SequenceCountDao() {}

    @Override
    public String getCollName() {
        return "sequence_counts";
    }

    @Override
    public Class<SequenceCount> getClassT() {
        return SequenceCount.class;
    }

    public void createIndicesIfAbsent() {
        createCollectionIfAbsent(getDBName(), getCollName(), new CreateCollectionOptions());
        MCollection.createUniqueIndex(getDBName(), getCollName(),
                new String[] { SequenceCount.CONTEXT_KEY, SequenceCount.NEXT, SequenceCount.BUCKET_START }, true);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[] { SequenceCount.PARENT_CONTEXT_KEY }, true);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[] { SequenceCount.NEXT, SequenceCount.CONTEXT_KEY }, true);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), Indexes.ascending(SequenceCount.EXPIRES_AT),
                new IndexOptions().name("expiresAt_ttl").expireAfter(0L, TimeUnit.SECONDS));
    }

    public static boolean isValid(SequenceCount sequenceCount) {
        return sequenceCount != null && sequenceCount.getNext() != null && sequenceCount.getCount() > 0;
    }

    public Map<String, String> incrementCounts(List<SequenceCount> sequenceCounts) {
        if (sequenceCounts == null || sequenceCounts.isEmpty()) return Collections.emptyMap();

        Map<String, SequenceCount> merged = new LinkedHashMap<>();
        for (SequenceCount sc : sequenceCounts) {
            if (!isValid(sc)) continue;
            List<String> context = sc.getContext() == null ? new ArrayList<String>() : sc.getContext();
            int bucketStart = sc.getBucketStart() - Math.floorMod(sc.getBucketStart(), BUCKET_SECONDS);
            String key = SequenceVomc.contextKey(context) + "\u0000" + sc.getNext() + "\u0000" + bucketStart;
            SequenceCount existing = merged.get(key);
            if (existing == null) {
                merged.put(key, new SequenceCount(sc.getApiCollectionId(), context, sc.getNext(), bucketStart, sc.getCount()));
            } else {
                existing.setCount(existing.getCount() + sc.getCount());
            }
        }
        if (merged.isEmpty()) return Collections.emptyMap();

        Map<String, String> touched = new HashMap<>();
        List<WriteModel<SequenceCount>> writeModels = new ArrayList<>();
        for (SequenceCount sc : merged.values()) {
            String contextKey = SequenceVomc.contextKey(sc.getContext());
            touched.put(contextKey, SequenceVomc.parentContextKey(sc.getContext()));
            Bson filter = Filters.and(
                    Filters.eq(SequenceCount.CONTEXT_KEY, contextKey),
                    Filters.eq(SequenceCount.NEXT, sc.getNext()),
                    Filters.eq(SequenceCount.BUCKET_START, sc.getBucketStart())
            );
            Bson update = Updates.combine(
                    Updates.inc(SequenceCount.COUNT, sc.getCount()),
                    Updates.setOnInsert(SequenceCount.API_COLLECTION_ID, sc.getApiCollectionId()),
                    Updates.setOnInsert(SequenceCount.CONTEXT, sc.getContext()),
                    Updates.setOnInsert(SequenceCount.PARENT_CONTEXT_KEY, SequenceVomc.parentContextKey(sc.getContext())),
                    Updates.setOnInsert(SequenceCount.EXPIRES_AT, new Date((sc.getBucketStart() + (long) RETENTION_SECONDS) * 1000L))
            );
            writeModels.add(new UpdateOneModel<>(filter, update, new UpdateOptions().upsert(true)));
        }
        getMCollection().bulkWrite(writeModels, new BulkWriteOptions().ordered(false));
        return touched;
    }
}
