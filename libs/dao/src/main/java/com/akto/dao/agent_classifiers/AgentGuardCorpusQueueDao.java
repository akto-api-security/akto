package com.akto.dao.agent_classifiers;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.agent_classifiers.AgentGuardCorpusQueueEntry;

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
