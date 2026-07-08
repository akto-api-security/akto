package com.akto.dao.endpoint_shield;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.endpoint_shield.EndpointConfigRemediation;

public class EndpointConfigRemediationDao extends AccountsContextDao<EndpointConfigRemediation> {

    public static final EndpointConfigRemediationDao instance = new EndpointConfigRemediationDao();
    public static final String COLLECTION_NAME = "endpoint_config_remediations";

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<EndpointConfigRemediation> getClassT() {
        return EndpointConfigRemediation.class;
    }

    public void createIndicesIfAbsent() {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{EndpointConfigRemediation.CREATED_AT}, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{EndpointConfigRemediation.STATUS}, false);
    }
}
