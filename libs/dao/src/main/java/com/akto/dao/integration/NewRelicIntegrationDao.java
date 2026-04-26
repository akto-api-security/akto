package com.akto.dao.integration;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.integration.NewRelicIntegration;

/**
 * Data Access Object for NewRelic Integration Configuration
 */
public class NewRelicIntegrationDao extends AccountsContextDao<NewRelicIntegration> {

    public static final NewRelicIntegrationDao instance = new NewRelicIntegrationDao();

    @Override
    public String getCollName() {
        return NewRelicIntegration.COLLECTION_NAME;
    }

    @Override
    public Class<NewRelicIntegration> getClassT() {
        return NewRelicIntegration.class;
    }
}
