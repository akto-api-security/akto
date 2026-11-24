package com.akto.dao.insights;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.insights.InsightNarrativeCache;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.conversions.Bson;

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
}
