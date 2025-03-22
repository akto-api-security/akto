package com.akto.dao.agents;

import java.util.ArrayList;
import java.util.List;

import org.bson.conversions.Bson;

import com.akto.dao.CommonContextDao;
import com.akto.dao.context.Context;
import com.akto.dto.agents.HealthCheck;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public class AgentHealthCheckDao extends CommonContextDao<HealthCheck> {

    public static final AgentHealthCheckDao instance = new AgentHealthCheckDao();

    public void saveHealth(String instanceId, String version, String processId) {
        Bson instanceFilter = Filters.eq(HealthCheck.INSTANCE_ID, instanceId);

        List<Bson> updates = new ArrayList<>();

        updates.add(Updates.set(HealthCheck.LAST_HEALTH_CHECK_TIMESTAMP, Context.now()));
        updates.add(Updates.set(HealthCheck._VERSION, version));

        if (processId == null) {
            processId = "";
        }
        updates.add(Updates.set(HealthCheck.PROCESS_ID, processId));

        /*
         * This operation has upsert: true.
         */
        AgentHealthCheckDao.instance.updateOne(instanceFilter, Updates.combine(updates));

        Bson timeoutFilter = Filters.lt(HealthCheck.LAST_HEALTH_CHECK_TIMESTAMP,
                Context.now() - HealthCheck.HEALTH_CHECK_TIMEOUT);

        AgentHealthCheckDao.instance.deleteAll(timeoutFilter);
    }

    public boolean isProcessRunningSomewhere(String processId) {
        return AgentHealthCheckDao.instance.findOne(Filters.and(
                Filters.eq(HealthCheck.PROCESS_ID, processId),
                Filters.gt(HealthCheck.LAST_HEALTH_CHECK_TIMESTAMP,
                        Context.now() - HealthCheck.HEALTH_CHECK_TIMEOUT))) != null;
    }

    @Override
    public String getCollName() {
        return "agent_health_check";
    }

    @Override
    public Class<HealthCheck> getClassT() {
        return HealthCheck.class;
    }

}
