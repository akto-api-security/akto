package com.akto.dao;

import com.akto.dto.AgentGuardCorpusEntry;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;

import org.bson.conversions.Bson;

import java.util.List;
import java.util.Set;

public class AgentGuardCorpusDao extends AccountsContextDao<AgentGuardCorpusEntry> {

    public static final String COLLECTION_NAME = "agent_guard_corpus";
    public static final AgentGuardCorpusDao instance = new AgentGuardCorpusDao();

    private AgentGuardCorpusDao() {}

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<AgentGuardCorpusEntry> getClassT() {
        return AgentGuardCorpusEntry.class;
    }

    public void createIndicesIfAbsent() {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{AgentGuardCorpusEntry.AGENT_HOST}, false);
    }

    /**
     * All labeled rows for an agent — feeds both classifier training and
     * structure-profile learning on the worker side.
     */
    public List<AgentGuardCorpusEntry> findByAgentHost(String agentHost) {
        Bson filter = Filters.eq(AgentGuardCorpusEntry.AGENT_HOST, agentHost);
        return findAll(filter, 0, 0, Sorts.descending(AgentGuardCorpusEntry.CREATED_AT), null);
    }
    
    public Set<String> findDistinctTaskIntents(String agentHost) {
        Bson filter = Filters.eq(AgentGuardCorpusEntry.AGENT_HOST, agentHost);
        return findDistinctFields(AgentGuardCorpusEntry.TASK_INTENT, String.class, filter);
    }
}
