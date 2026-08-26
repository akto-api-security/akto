package com.akto.dao.insights;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.insights.InsightClassificationCache;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class InsightClassificationCacheDao extends AccountsContextDao<InsightClassificationCache> {

    public static final InsightClassificationCacheDao instance = new InsightClassificationCacheDao();

    private InsightClassificationCacheDao() {}

    @Override
    public String getCollName() {
        return "insight_classification_cache";
    }

    @Override
    public Class<InsightClassificationCache> getClassT() {
        return InsightClassificationCache.class;
    }

    public void createIndicesIfAbsent() {
        Bson ttlIndex = Indexes.ascending(InsightClassificationCache.EXPIRES_AT);
        IndexOptions ttlOptions = new IndexOptions()
                .name("expiresAt_ttl")
                .expireAfter(0L, TimeUnit.SECONDS);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), ttlIndex, ttlOptions);
    }

    public Map<String, String> bulkGet(Collection<String> ids) {
        Map<String, String> out = new HashMap<>();
        if (ids == null || ids.isEmpty()) return out;
        List<InsightClassificationCache> found = findAll(Filters.in("_id", ids));
        if (found == null) return out;
        for (InsightClassificationCache c : found) {
            if (c.getId() != null) out.put(c.getId(), c.getValueJson());
        }
        return out;
    }

    public void bulkPut(List<InsightClassificationCache> entries) {
        if (entries == null || entries.isEmpty()) return;
        List<WriteModel<InsightClassificationCache>> ops = new ArrayList<>();
        for (InsightClassificationCache e : entries) {
            if (e.getId() == null || e.getId().isEmpty()) continue;
            ops.add(new UpdateOneModel<>(
                    Filters.eq("_id", e.getId()),
                    Updates.combine(
                            Updates.setOnInsert("_id", e.getId()),
                            Updates.set(InsightClassificationCache.CLASSIFIER, e.getClassifier()),
                            Updates.set(InsightClassificationCache.VALUE_JSON, e.getValueJson()),
                            Updates.set(InsightClassificationCache.CREATED_AT, e.getCreatedAt()),
                            Updates.set(InsightClassificationCache.EXPIRES_AT, e.getExpiresAt())
                    ),
                    new UpdateOptions().upsert(true)));
        }
        if (!ops.isEmpty()) bulkWrite(ops, new BulkWriteOptions().ordered(false));
    }
}
