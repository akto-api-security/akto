package com.akto.dao.monitoring;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.ApiSequences;
import com.akto.dto.monitoring.EndpointShieldLog;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

import java.util.List;

public class EndpointShieldLogsDao extends AccountsContextDao<EndpointShieldLog> {
    
    public static final EndpointShieldLogsDao instance = new EndpointShieldLogsDao();
    
    private EndpointShieldLogsDao() {}

    @Override
    public String getCollName() {
        return "logs_endpoint_shield";
    }

    @Override
    public Class<EndpointShieldLog> getClassT() {
        return EndpointShieldLog.class;
    }

    public void createIndicesIfAbsent() {
        boolean exists = false;
        String dbName = Context.accountId.get()+"";
        MongoDatabase db = clients[0].getDatabase(dbName);
        for (String col: db.listCollectionNames()){
            if (getCollName().equalsIgnoreCase(col)){
                exists = true;
                break;
            }
        };

        if (!exists) {
            db.createCollection(getCollName());
        }

        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[] { EndpointShieldLog.TIMESTAMP }, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[] { EndpointShieldLog.AGENT_ID}, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[] { EndpointShieldLog.DEVICE_ID }, false);

        // The paged log query filters by (agentId, key) equality and sorts+ranges on _id
        // (Filters.lt("_id", afterId) + Sorts.descending("_id")). Following the ESR rule
        // (Equality, Sort, Range), _id trails the equality fields so a single index serves
        // both the cursor range and the sort — no collection scan, no blocking in-memory sort.
        // Without this, the planner falls back to the plain _id index and scans the whole
        // collection filtering out other agents'/keys' logs, timing out on getMore for sparse
        // keys (proxy/installation logs).
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[] { EndpointShieldLog.AGENT_ID, EndpointShieldLog.KEY, MCollection.ID }, false);
        // countDocuments filters by (agentId, key) equality + timestamp range.
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[] { EndpointShieldLog.AGENT_ID, EndpointShieldLog.KEY, EndpointShieldLog.TIMESTAMP }, false);
    }
    public List<EndpointShieldLog> findByAgentId(String agentId) {
        Bson filter = Filters.eq(EndpointShieldLog.AGENT_ID, agentId);
        return instance.findAll(filter);
    }

    public List<EndpointShieldLog> findByAgentIdAndDeviceId(String agentId, String deviceId) {
        Bson filter = Filters.and(
            Filters.eq(EndpointShieldLog.AGENT_ID, agentId),
            Filters.eq(EndpointShieldLog.DEVICE_ID, deviceId)
        );
        return instance.findAll(filter);
    }

    public List<EndpointShieldLog> findByDeviceId(String deviceId) {
        Bson filter = Filters.eq(EndpointShieldLog.DEVICE_ID, deviceId);
        return instance.findAll(filter);
    }
}