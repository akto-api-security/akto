package com.akto.dao.agents;

import java.util.ArrayList;
import java.util.List;

import org.bson.conversions.Bson;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.agents.AgentSubProcessSingleAttempt;
import com.mongodb.client.model.Filters;

public class AgentSubProcessSingleAttemptDao extends AccountsContextDao<AgentSubProcessSingleAttempt>{

    public static final AgentSubProcessSingleAttemptDao instance = new AgentSubProcessSingleAttemptDao();

    // TODO: create indices

    public Bson getFiltersForAgentSubProcess(String processId, String subProcessId, int attemptId) {
        List<Bson> filters = new ArrayList<>();

        filters.add(Filters.eq(AgentSubProcessSingleAttempt.PROCESS_ID, processId));
        filters.add(Filters.eq(AgentSubProcessSingleAttempt.SUB_PROCESS_ID, subProcessId));
        filters.add(Filters.eq(AgentSubProcessSingleAttempt.ATTEMPT_ID, attemptId));

        return Filters.and(filters);
    }

    public Bson getFiltersForAgentSubProcess(AgentSubProcessSingleAttempt subProcess){
        return getFiltersForAgentSubProcess(subProcess.getProcessId(), subProcess.getSubProcessId(), subProcess.getAttemptId());
    }


    @Override
    public String getCollName() {
        return "agent_sub_process_attempts";
    }

    @Override
    public Class<AgentSubProcessSingleAttempt> getClassT() {
        return AgentSubProcessSingleAttempt.class;
    }
    
}
