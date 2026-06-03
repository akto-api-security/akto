package com.akto.dao;

import com.akto.dto.EndpointRemoteCommand;
import com.mongodb.client.model.CreateCollectionOptions;

public class EndpointRemoteCommandDao extends AccountsContextDao<EndpointRemoteCommand> {

    public static final String COLLECTION_NAME = "endpoint_remote_commands";
    public static final EndpointRemoteCommandDao instance = new EndpointRemoteCommandDao();

    private EndpointRemoteCommandDao() {}

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<EndpointRemoteCommand> getClassT() {
        return EndpointRemoteCommand.class;
    }

    public void createIndicesIfAbsent() {
        createCollectionIfAbsent(getDBName(), getCollName(), new CreateCollectionOptions());
        // settings page list: (accountId[db], status, createdAt)
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{EndpointRemoteCommand.STATUS, EndpointRemoteCommand.CREATED_AT}, false);
        // agent fan-out: (accountId[db], targetType, status)
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{EndpointRemoteCommand.TARGET_TYPE, EndpointRemoteCommand.STATUS}, false);
    }
}
