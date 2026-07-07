package com.akto.dao.agent_classifiers;

import java.util.ArrayList;
import java.util.List;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.agent_classifiers.AgentGuardCorpusEntry;
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

    public List<AgentGuardCorpusEntry> findBucketsByAgentHost(String agentHost){

        return new ArrayList<>();

    }

}
