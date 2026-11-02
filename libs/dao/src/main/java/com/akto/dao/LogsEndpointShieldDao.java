package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.LogsEndpointShield;
import com.akto.util.DbMode;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;

public class LogsEndpointShieldDao extends AccountsContextDao<LogsEndpointShield> {

    public static final LogsEndpointShieldDao instance = new LogsEndpointShieldDao();

    // Capped by size only (no maxDocuments), matching master's EndpointShieldLogsDao — see
    // commit a104c00 "added capped collection for endpoint logs and removed redundant indexes".
    public static final long CAPPED_SIZE_IN_BYTES = 100_000_000L; // 100MB

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
            // Collection predates capping (created uncapped): convert the existing collection to
            // capped so it stays bounded. Equivalent to:
            //   db.runCommand({convertToCapped: "logs_endpoint_shield", size: 100000000})
            // convertToCapped drops secondary indexes, so the index creation below re-adds them.
            convertToCappedCollection(CAPPED_SIZE_IN_BYTES);
        }

        // Single secondary index, matching master (commit a104c00). Every real query targets one
        // (agentId, key) partition and paginates/sorts on _id (Sorts.descending("_id") +
        // Filters.lt("_id", afterId)); per the ESR rule _id trails the equality fields so this one
        // index serves the range + sort with no collection scan and no in-memory sort. The former
        // {timestamp}, {agentId} and {deviceId} indexes were dropped: {timestamp} has no query
        // path, {agentId} is a prefix of this index, and {deviceId} has no caller.
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { LogsEndpointShield.AGENT_ID, LogsEndpointShield.KEY, MCollection.ID }, false);
    }

    @Override
    public String getCollName() {
        return "logs_endpoint_shield";
    }

    @Override
    public Class<LogsEndpointShield> getClassT() {
        return LogsEndpointShield.class;
    }
}