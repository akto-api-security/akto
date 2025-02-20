package com.akto.dao.agents;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.agents.AgentSubProcessSingleAttempt;

public class AgentSubProcessSingleAttemptDao extends AccountsContextDao<AgentSubProcessSingleAttempt>{

    public static final AgentSubProcessSingleAttemptDao instance = new AgentSubProcessSingleAttemptDao();

    // TODO: create indices

    @Override
    public String getCollName() {
        return "agent_sub_process_attempts";
    }

    @Override
    public Class<AgentSubProcessSingleAttempt> getClassT() {
        return AgentSubProcessSingleAttempt.class;
    }
    
}
