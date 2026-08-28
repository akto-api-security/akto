package com.akto.dao.agents;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.agents.AgentRun;

public class AgentRunDao extends AccountsContextDao<AgentRun> {

    public static final AgentRunDao instance = new AgentRunDao();

    public void createIndicesIfAbsent() {
        // Index for looking up a run by its unique process id
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{AgentRun.PROCESS_ID}, true);
        // Index for filtering runs by state (e.g. find all RUNNING agents)
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{AgentRun._STATE}, true);
        // Index for time-based queries and sorting
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{AgentRun.CREATED_TIMESTAMP}, false);
    }

    @Override
    public String getCollName() {
        return "agent_runs";
    }

    @Override
    public Class<AgentRun> getClassT() {
        return AgentRun.class;
    }

}
