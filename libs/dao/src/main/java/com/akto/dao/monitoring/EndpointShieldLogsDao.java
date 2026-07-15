package com.akto.dao.monitoring;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.ApiSequences;
import com.akto.dto.monitoring.EndpointShieldLog;
import com.akto.util.DbMode;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

import java.util.List;

public class EndpointShieldLogsDao extends AccountsContextDao<EndpointShieldLog> {

    public static final long CAPPED_SIZE_IN_BYTES = 100_000_000L; // 100MB

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
            if (DbMode.allowCappedCollections()) {
                db.createCollection(getCollName(), new CreateCollectionOptions().capped(true).sizeInBytes(CAPPED_SIZE_IN_BYTES));
            } else {
                db.createCollection(getCollName());
            }
        } else if (DbMode.allowCappedCollections() && !isCapped()) {
            convertToCappedCollection(CAPPED_SIZE_IN_BYTES);
        }

        // Single secondary index, matching master. Every real query is served by the API-serving
        // dashboard (not these job-runner instances): it targets one (agentId, key) partition and
        // sorts+ranges on _id, so {agentId, key, _id} serves it with no in-memory sort. The
        // previously-created {timestamp}, {agentId} and {deviceId} indexes are dropped to cut write
        // amplification on this now-capped, high-write collection.
        // NOTE: the key field is referenced by literal ("key") rather than EndpointShieldLog.KEY
        // because Log.KEY is not present on all of these job branches; the resulting index is
        // identical to master's.
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[] { EndpointShieldLog.AGENT_ID, "key", MCollection.ID }, false);
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
