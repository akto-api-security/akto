package com.akto.dao.monitoring;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.monitoring.EndpointShieldLog;
import com.akto.dto.monitoring.EndpointShieldServer;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

import java.util.List;

public class EndpointShieldServerDao extends AccountsContextDao<EndpointShieldServer> {
    
    public static final EndpointShieldServerDao instance = new EndpointShieldServerDao();
    
    private EndpointShieldServerDao() {}

    @Override
    public String getCollName() {
        return "endpoint_shield_servers";
    }

    @Override
    public Class<EndpointShieldServer> getClassT() {
        return EndpointShieldServer.class;
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

        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[] { EndpointShieldServer.AGENT_ID }, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[] { EndpointShieldServer.DEVICE_ID }, false);
    }

    public List<EndpointShieldServer> findByAgentId(String agentId) {
        Bson filter = Filters.eq(EndpointShieldServer.AGENT_ID, agentId);
        return instance.findAll(filter);
    }

    public List<EndpointShieldServer> findByAgentIdAndDeviceId(String agentId, String deviceId) {
        Bson filter = Filters.and(
            Filters.eq(EndpointShieldServer.AGENT_ID, agentId),
            Filters.eq(EndpointShieldServer.DEVICE_ID, deviceId)
        );
        return instance.findAll(filter);
    }

    public List<EndpointShieldServer> findByDeviceId(String deviceId) {
        Bson filter = Filters.eq(EndpointShieldServer.DEVICE_ID, deviceId);
        return instance.findAll(filter);
    }

}