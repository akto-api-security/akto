package com.akto.dao.agents;

import java.util.ArrayList;
import java.util.List;

import org.bson.conversions.Bson;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.agents.AgentSubProcessSingleAttempt;
import com.mongodb.client.model.Filters;

public class AgentSubProcessSingleAttemptDao extends AccountsContextDao<AgentSubProcessSingleAttempt>{

    public static final AgentSubProcessSingleAttemptDao instance = new AgentSubProcessSingleAttemptDao();

    public void createIndicesIfAbsent() {
        // Index for fetching all attempts of a given process
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{AgentSubProcessSingleAttempt.PROCESS_ID}, true);
        // Compound index for the primary lookup: process + sub-process + attempt
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{AgentSubProcessSingleAttempt.PROCESS_ID,
                        AgentSubProcessSingleAttempt.SUB_PROCESS_ID,
                        AgentSubProcessSingleAttempt.ATTEMPT_ID}, true);
        // Index for querying by state (e.g. find all RUNNING attempts)
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{AgentSubProcessSingleAttempt._STATE}, true);
    }

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
