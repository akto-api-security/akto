package com.akto.dao;

import com.akto.dto.EndpointConfigRemediation;
import com.mongodb.client.model.CreateCollectionOptions;

public class EndpointConfigRemediationDao extends AccountsContextDao<EndpointConfigRemediation> {

    public static final String COLLECTION_NAME = "endpoint_config_remediations";
    public static final EndpointConfigRemediationDao instance = new EndpointConfigRemediationDao();

    private EndpointConfigRemediationDao() {}

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<EndpointConfigRemediation> getClassT() {
        return EndpointConfigRemediation.class;
    }

    public void createIndicesIfAbsent() {
        createCollectionIfAbsent(getDBName(), getCollName(), new CreateCollectionOptions());
        // settings page list: (accountId[db], status, createdAt)
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{EndpointConfigRemediation.STATUS, EndpointConfigRemediation.CREATED_AT}, false);
        // agent fan-out: (accountId[db], targetType, status)
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{EndpointConfigRemediation.TARGET_TYPE, EndpointConfigRemediation.STATUS}, false);
    }
}
