package com.akto.dao.endpoint_shield;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.endpoint_shield.EndpointRemoteCommand;

public class EndpointRemoteCommandDao extends AccountsContextDao<EndpointRemoteCommand> {

    public static final EndpointRemoteCommandDao instance = new EndpointRemoteCommandDao();
    public static final String COLLECTION_NAME = "endpoint_remote_commands";

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<EndpointRemoteCommand> getClassT() {
        return EndpointRemoteCommand.class;
    }

    public void createIndicesIfAbsent() {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{EndpointRemoteCommand.CREATED_AT}, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{EndpointRemoteCommand.STATUS}, false);
    }
}
