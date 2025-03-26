package com.akto.dao.agents;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.agents.AgentRun;

public class AgentRunDao extends AccountsContextDao<AgentRun> {

    public static final AgentRunDao instance = new AgentRunDao();

    // TODO: create indices

    @Override
    public String getCollName() {
        return "agent_runs";
    }

    @Override
    public Class<AgentRun> getClassT() {
        return AgentRun.class;
    }

}
