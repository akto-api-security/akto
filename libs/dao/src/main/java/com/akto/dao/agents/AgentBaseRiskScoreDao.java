package com.akto.dao.agents;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.agents.AgentBaseRiskScore;

/**
 * DAO for the agent_base_risk_scores cache collection - see AgentBaseRiskScore for why this is
 * a separate collection instead of a lookup against api_collections.
 */
public class AgentBaseRiskScoreDao extends AccountsContextDao<AgentBaseRiskScore> {

    public static final AgentBaseRiskScoreDao instance = new AgentBaseRiskScoreDao();

    @Override
    public String getCollName() {
        return "agent_base_risk_scores";
    }

    @Override
    public Class<AgentBaseRiskScore> getClassT() {
        return AgentBaseRiskScore.class;
    }

}
