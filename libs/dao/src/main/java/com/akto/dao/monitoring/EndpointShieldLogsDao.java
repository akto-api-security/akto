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

        // Single secondary index. Every real query targets one (agentId, key) partition; the
        // paged log query then sorts+ranges on _id (Filters.lt("_id", afterId) +
        // Sorts.descending("_id")). Per the ESR rule (Equality, Sort, Range) _id trails the
        // equality fields, so this one index serves the cursor range and the sort with no
        // collection scan and no blocking in-memory sort.
        //
        // This is the only index we keep, to minimise write amplification on this high-write log
        // collection (which is also capped). What was dropped and why:
        //  - {timestamp}: nothing fetches these logs by timestamp alone — the only such path
        //    (DbLogsAction/LoggerMaker with LogDb.ENDPOINT_SHIELD) is never invoked; the log viewer
        //    only queries TESTING/RUNTIME/DASHBOARD.
        //  - {agentId} / {deviceId}: {agentId} is a prefix of this index; {deviceId} has no caller.
        //  - {agentId, key, timestamp}: only backed the display-only countDocuments. That count now
        //    rides on this index (agentId+key equality bound, timestamp applied as a residual filter
        //    over the small per-partition set) — acceptable since the collection is capped, the count
        //    runs only on the first page, and it degrades to "unknown" on its 10s maxTime.
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[] { EndpointShieldLog.AGENT_ID, EndpointShieldLog.KEY, MCollection.ID }, false);
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