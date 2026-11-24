package com.akto.dao.insights;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.insights.InsightClassificationCache;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.conversions.Bson;

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
}
