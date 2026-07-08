package com.akto.dao.endpoint_shield;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.endpoint_shield.EndpointConfigRemediationExecution;

public class EndpointConfigRemediationExecutionDao extends AccountsContextDao<EndpointConfigRemediationExecution> {

    public static final EndpointConfigRemediationExecutionDao instance = new EndpointConfigRemediationExecutionDao();
    public static final String COLLECTION_NAME = "endpoint_config_remediation_executions";

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<EndpointConfigRemediationExecution> getClassT() {
        return EndpointConfigRemediationExecution.class;
    }

    public void createIndicesIfAbsent() {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{EndpointConfigRemediationExecution.REMEDIATION_ID}, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{EndpointConfigRemediationExecution.DEVICE_ID}, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{EndpointConfigRemediationExecution.REMEDIATION_ID, EndpointConfigRemediationExecution.DEVICE_ID}, false);
    }
}
