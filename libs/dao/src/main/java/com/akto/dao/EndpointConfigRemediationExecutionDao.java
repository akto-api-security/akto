package com.akto.dao;

import com.akto.dto.EndpointConfigRemediationExecution;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

public class EndpointConfigRemediationExecutionDao extends AccountsContextDao<EndpointConfigRemediationExecution> {

    public static final String COLLECTION_NAME = "endpoint_config_remediation_executions";
    public static final EndpointConfigRemediationExecutionDao instance = new EndpointConfigRemediationExecutionDao();

    private EndpointConfigRemediationExecutionDao() {}

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<EndpointConfigRemediationExecution> getClassT() {
        return EndpointConfigRemediationExecution.class;
    }

    public void createIndicesIfAbsent() {
        createCollectionIfAbsent(getDBName(), getCollName(), new CreateCollectionOptions());
        // id is no longer _id (BsonProperty breaks the convention) — unique index for point-lookups
        MCollection.createUniqueIndex(getDBName(), getCollName(),
                new String[]{EndpointConfigRemediationExecution.ID}, false);
        // agent polling lookup: (accountId[db], deviceId, status)
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{EndpointConfigRemediationExecution.DEVICE_ID, EndpointConfigRemediationExecution.STATUS}, false);
        // dashboard per-remediation drill-down: (accountId[db], remediationId, status)
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{EndpointConfigRemediationExecution.REMEDIATION_ID, EndpointConfigRemediationExecution.STATUS}, false);
        // one execution per (remediationId, deviceId) ever — enforces lazy fan-out uniqueness
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                Indexes.compoundIndex(
                        Indexes.ascending(EndpointConfigRemediationExecution.REMEDIATION_ID),
                        Indexes.ascending(EndpointConfigRemediationExecution.DEVICE_ID)),
                new IndexOptions().unique(true).name("remediationId_deviceId_unique"));
    }
}
