package com.akto.dao.endpoint_shield;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.endpoint_shield.EndpointRemoteCommandExecution;

public class EndpointRemoteCommandExecutionDao extends AccountsContextDao<EndpointRemoteCommandExecution> {

    public static final EndpointRemoteCommandExecutionDao instance = new EndpointRemoteCommandExecutionDao();
    public static final String COLLECTION_NAME = "endpoint_remote_command_executions";

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<EndpointRemoteCommandExecution> getClassT() {
        return EndpointRemoteCommandExecution.class;
    }

    public void createIndicesIfAbsent() {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{EndpointRemoteCommandExecution.COMMAND_ID}, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{EndpointRemoteCommandExecution.DEVICE_ID}, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{EndpointRemoteCommandExecution.COMMAND_ID, EndpointRemoteCommandExecution.DEVICE_ID}, false);
    }
}
