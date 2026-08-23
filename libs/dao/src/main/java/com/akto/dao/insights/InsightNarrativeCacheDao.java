package com.akto.dao.insights;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.insights.InsightNarrativeCache;
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
import java.util.List;
import java.util.concurrent.TimeUnit;

public class InsightNarrativeCacheDao extends AccountsContextDao<InsightNarrativeCache> {

    public static final String COLLECTION_NAME = "insight_narrative_cache";
    public static final InsightNarrativeCacheDao instance = new InsightNarrativeCacheDao();

    private InsightNarrativeCacheDao() {}

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<InsightNarrativeCache> getClassT() {
        return InsightNarrativeCache.class;
    }

    public void createIndicesIfAbsent() {
        // TTL index — MongoDB deletes a document once its expiresAt Date is reached. Deliberately
        // a java.util.Date field (not a long), since TTL indexes only act on BSON Date fields.
        Bson ttlIndex = Indexes.ascending(InsightNarrativeCache.EXPIRES_AT);
        IndexOptions ttlOptions = new IndexOptions()
                .name("expiresAt_ttl")
                .expireAfter(0L, TimeUnit.SECONDS);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), ttlIndex, ttlOptions);

        String[] fieldNames = {InsightNarrativeCache.INSIGHT_ID};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);
    }

    public InsightNarrativeCache findById(String id) {
        return instance.findOne(Filters.eq(InsightNarrativeCache.ID, id));
    }

    /** Upsert by content-fingerprint id — same shape as the discovery-side insights branch's own
     *  InsightNarrativeCacheDao.put(), so the two converge cleanly once merged. */
    public void put(InsightNarrativeCache entry) {
        if (entry == null || entry.getId() == null || entry.getId().isEmpty()) {
            return;
        }
        List<WriteModel<InsightNarrativeCache>> ops = new ArrayList<>();
        ops.add(new UpdateOneModel<>(
                Filters.eq(InsightNarrativeCache.ID, entry.getId()),
                Updates.combine(
                        Updates.setOnInsert(InsightNarrativeCache.ID, entry.getId()),
                        Updates.set(InsightNarrativeCache.INSIGHT_ID, entry.getInsightId()),
                        Updates.set(InsightNarrativeCache.PROVIDER_VERSION, entry.getProviderVersion()),
                        Updates.set(InsightNarrativeCache.PROMPT_VERSION, entry.getPromptVersion()),
                        Updates.set(InsightNarrativeCache.NARRATIVE_MARKDOWN, entry.getNarrativeMarkdown()),
                        Updates.set(InsightNarrativeCache.FACTS_USED, entry.getFactsUsed()),
                        Updates.set(InsightNarrativeCache.GENERATED_AT, entry.getGeneratedAt()),
                        Updates.set(InsightNarrativeCache.EXPIRES_AT, entry.getExpiresAt())
                ),
                new UpdateOptions().upsert(true)));
        bulkWrite(ops, new BulkWriteOptions());
    }
}
