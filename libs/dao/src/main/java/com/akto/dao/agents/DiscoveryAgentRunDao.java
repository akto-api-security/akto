package com.akto.dao.agents;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.agents.DiscoveryAgentRun;

public class DiscoveryAgentRunDao extends AccountsContextDao<DiscoveryAgentRun> {

    public static final DiscoveryAgentRunDao instance = new DiscoveryAgentRunDao();

    @Override
    public String getCollName() {
        return "discovery_agent_runs";
    }

    @Override
    public Class<DiscoveryAgentRun> getClassT() {
        return DiscoveryAgentRun.class;
    }

}
