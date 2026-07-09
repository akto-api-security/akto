package com.akto.dao;

import com.akto.dto.AgentGuardCorpusQueueEntry;
public class AgentGuardCorpusQueueDao extends AccountsContextDao<AgentGuardCorpusQueueEntry> {

    public static final String COLLECTION_NAME = "agent_guard_corpus_queue";
    public static final AgentGuardCorpusQueueDao instance = new AgentGuardCorpusQueueDao();

    private AgentGuardCorpusQueueDao() {}

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<AgentGuardCorpusQueueEntry> getClassT() {
        return AgentGuardCorpusQueueEntry.class;
    }

    public void createIndicesIfAbsent() {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{AgentGuardCorpusQueueEntry.CREATED_AT}, false);
    }
}
