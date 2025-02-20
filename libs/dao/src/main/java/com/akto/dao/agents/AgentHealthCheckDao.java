package com.akto.dao.agents;

import com.akto.dao.CommonContextDao;
import com.akto.dto.agents.HealthCheck;

public class AgentHealthCheckDao extends CommonContextDao<HealthCheck> {

    public static final AgentHealthCheckDao instance = new AgentHealthCheckDao();

    @Override
    public String getCollName() {
        return "agent_health_check";
    }

    @Override
    public Class<HealthCheck> getClassT() {
        return HealthCheck.class;
    }

}
