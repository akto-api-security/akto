package com.akto.dao.insights;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.insights.InsightNarrativeCache;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.model.BulkWriteOptions;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * TTL-indexed cache of AI-rendered insight narratives, one per account. TTL uses a
 * java.util.Date field (expireAfter(0, SECONDS)) — NOT a long epoch, which is the bug
 * in QueryTopicCacheDao (Mongo TTL only fires on BSON Date fields) — and its
 * createIndicesIfAbsent() must actually be called from DaoInit, unlike that DAO's.
 */
public class InsightNarrativeCacheDao extends AccountsContextDao<InsightNarrativeCache> {

    public static final InsightNarrativeCacheDao instance = new InsightNarrativeCacheDao();

    private InsightNarrativeCacheDao() {}

    @Override
    public String getCollName() {
        return "insight_narrative_cache";
    }

    @Override
    public Class<InsightNarrativeCache> getClassT() {
        return InsightNarrativeCache.class;
    }

    public void createIndicesIfAbsent() {
        Bson ttlIndex = Indexes.ascending(InsightNarrativeCache.EXPIRES_AT);
        IndexOptions ttlOptions = new IndexOptions()
                .name("expiresAt_ttl")
                .expireAfter(0L, TimeUnit.SECONDS);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), ttlIndex, ttlOptions);
    }

    public InsightNarrativeCache get(String fingerprint) {
        return findOne(Filters.eq("_id", fingerprint));
    }

    public void put(InsightNarrativeCache entry) {
        if (entry == null || entry.getId() == null || entry.getId().isEmpty()) return;
        List<WriteModel<InsightNarrativeCache>> ops = new ArrayList<>();
        ops.add(new UpdateOneModel<>(
                Filters.eq("_id", entry.getId()),
                Updates.combine(
                        Updates.setOnInsert("_id", entry.getId()),
                        Updates.set(InsightNarrativeCache.INSIGHT_ID, entry.getInsightId()),
                        Updates.set(InsightNarrativeCache.PROVIDER_VERSION, entry.getProviderVersion()),
                        Updates.set(InsightNarrativeCache.PROMPT_VERSION, entry.getPromptVersion()),
                        Updates.set(InsightNarrativeCache.NARRATIVE_MARKDOWN, entry.getNarrativeMarkdown()),
                        Updates.set(InsightNarrativeCache.GENERATED_AT, entry.getGeneratedAt()),
                        Updates.set(InsightNarrativeCache.EXPIRES_AT, entry.getExpiresAt())
                ),
                new UpdateOptions().upsert(true)));
        bulkWrite(ops, new BulkWriteOptions());
    }
}
