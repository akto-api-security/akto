package com.akto.dao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.conversions.Bson;

import com.akto.dto.AgentGuardCorpusEntry;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.PushOptions;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
public class AgentGuardCorpusDao extends AccountsContextDao<AgentGuardCorpusEntry> {

    public static final String COLLECTION_NAME = "agent_guard_corpus";
    public static final AgentGuardCorpusDao instance = new AgentGuardCorpusDao();

    // Default page size for findByAgentHost when the caller does not specify a limit.
    public static final int DEFAULT_LOAD_LIMIT = 500;

    private AgentGuardCorpusDao() {}

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<AgentGuardCorpusEntry> getClassT() {
        return AgentGuardCorpusEntry.class;
    }

    public void upsertVectors(List<AgentGuardCorpusEntry> entries) {
        if (entries == null || entries.isEmpty()) return;

        // Group vectors by bucket key to minimize round-trips.
        Map<String, List<List<Double>>> vectorsByKey = new LinkedHashMap<>();
        Map<String, AgentGuardCorpusEntry> metaByKey = new LinkedHashMap<>();

        for (AgentGuardCorpusEntry e : entries) {
            if (e.getVectors() == null || e.getVectors().isEmpty()) continue;
            String key = e.getAgentHost() + "|" + e.getTaskIntent() + "|" + e.getScopeBucket();
            vectorsByKey.computeIfAbsent(key, k -> new ArrayList<>()).addAll(e.getVectors());
            metaByKey.put(key, e);
        }

        int now = (int) (System.currentTimeMillis() / 1000);

        for (Map.Entry<String, List<List<Double>>> kv : vectorsByKey.entrySet()) {
            AgentGuardCorpusEntry rep = metaByKey.get(kv.getKey());
            Bson filter = Filters.and(
                    Filters.eq(AgentGuardCorpusEntry.AGENT_HOST, rep.getAgentHost()),
                    Filters.eq(AgentGuardCorpusEntry.TASK_INTENT, rep.getTaskIntent()),
                    Filters.eq(AgentGuardCorpusEntry.SCOPE_BUCKET, rep.getScopeBucket())
            );
            Bson update = Updates.combine(
                    Updates.pushEach(AgentGuardCorpusEntry.VECTORS, kv.getValue(),
                            new PushOptions().slice(-50)),
                    Updates.setOnInsert(AgentGuardCorpusEntry.IS_VALID, rep.isValid()),
                    Updates.set(AgentGuardCorpusEntry.UPDATED_AT, now)
            );
            getMCollection().updateOne(filter, update, new UpdateOptions().upsert(true));
        }
    }

    /**
     * Return all bucket documents for the given agent.
     * Typically 5-20 documents, each with up to MAX_VECTORS_PER_BUCKET vectors.
     */
    public List<AgentGuardCorpusEntry> findBucketsByAgentHost(String agentHost) {
        Bson filter = Filters.eq(AgentGuardCorpusEntry.AGENT_HOST, agentHost);
        Bson sort = Sorts.descending(AgentGuardCorpusEntry.UPDATED_AT);
        return findAll(filter, 0, 0, sort, null);
    }
}
