package com.akto.dao.agentic_sessions;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.agentic_sessions.QueryTopicCache;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class QueryTopicCacheDao extends AccountsContextDao<QueryTopicCache> {

    public static final QueryTopicCacheDao instance = new QueryTopicCacheDao();

    public void createIndicesIfAbsent() {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            Indexes.ascending(QueryTopicCache.CREATED_AT),
            new IndexOptions().name("createdAt_ttl").expireAfter(7L, TimeUnit.DAYS));
    }

    public Map<String, QueryTopicCache> bulkGet(Collection<String> hashes) {
        if (hashes == null || hashes.isEmpty()) return Collections.emptyMap();
        List<QueryTopicCache> found = findAll(Filters.in("_id", hashes));
        if (found == null) return Collections.emptyMap();
        Map<String, QueryTopicCache> result = new HashMap<>();
        for (QueryTopicCache entry : found) {
            if (entry.getId() != null) result.put(entry.getId(), entry);
        }
        return result;
    }

    public void bulkPut(List<QueryTopicCache> entries) {
        if (entries == null || entries.isEmpty()) return;
        List<WriteModel<QueryTopicCache>> ops = new ArrayList<>();
        for (QueryTopicCache entry : entries) {
            if (entry.getId() == null || entry.getId().isEmpty()) continue;
            ops.add(new UpdateOneModel<>(
                Filters.eq("_id", entry.getId()),
                Updates.combine(
                    Updates.setOnInsert("_id", entry.getId()),
                    Updates.set(QueryTopicCache.DOMAIN, entry.getDomain()),
                    Updates.set(QueryTopicCache.SUB_DOMAIN, entry.getSubDomain()),
                    Updates.set(QueryTopicCache.HARMFUL, entry.isHarmful()),
                    Updates.set(QueryTopicCache.HARMFUL_CATEGORY, entry.getHarmfulCategory()),
                    Updates.set(QueryTopicCache.HARMFUL_REASON, entry.getHarmfulReason()),
                    Updates.set(QueryTopicCache.CREATED_AT, entry.getCreatedAt())
                ),
                new UpdateOptions().upsert(true)
            ));
        }
        if (!ops.isEmpty()) bulkWrite(ops, new BulkWriteOptions().ordered(false));
    }

    @Override
    public String getCollName() {
        return "query_topic_cache";
    }

    @Override
    public Class<QueryTopicCache> getClassT() {
        return QueryTopicCache.class;
    }
}
