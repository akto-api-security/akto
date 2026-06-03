package com.akto.dao;

import com.akto.dto.EndpointRemoteCommandExecution;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

public class EndpointRemoteCommandExecutionDao extends AccountsContextDao<EndpointRemoteCommandExecution> {

    public static final String COLLECTION_NAME = "endpoint_remote_command_executions";
    public static final EndpointRemoteCommandExecutionDao instance = new EndpointRemoteCommandExecutionDao();

    private EndpointRemoteCommandExecutionDao() {}

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<EndpointRemoteCommandExecution> getClassT() {
        return EndpointRemoteCommandExecution.class;
    }

    public void createIndicesIfAbsent() {
        createCollectionIfAbsent(getDBName(), getCollName(), new CreateCollectionOptions());
        // id is no longer _id (BsonProperty breaks the convention) — unique index for point-lookups
        MCollection.createUniqueIndex(getDBName(), getCollName(),
                new String[]{EndpointRemoteCommandExecution.ID}, false);
        // agent polling lookup: (accountId[db], deviceId, status)
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{EndpointRemoteCommandExecution.DEVICE_ID, EndpointRemoteCommandExecution.STATUS}, false);
        // dashboard per-command drill-down: (accountId[db], commandId, status)
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{EndpointRemoteCommandExecution.COMMAND_ID, EndpointRemoteCommandExecution.STATUS}, false);
        // one execution per (commandId, deviceId) ever — enforces lazy fan-out uniqueness
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                Indexes.compoundIndex(
                        Indexes.ascending(EndpointRemoteCommandExecution.COMMAND_ID),
                        Indexes.ascending(EndpointRemoteCommandExecution.DEVICE_ID)),
                new IndexOptions().unique(true).name("commandId_deviceId_unique"));
    }
}
