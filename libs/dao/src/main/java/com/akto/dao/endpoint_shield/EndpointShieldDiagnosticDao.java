package com.akto.dao.endpoint_shield;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.endpoint_shield.EndpointShieldDiagnostic;

/**
 * endpoint_shield_diagnostics — written by the db-abstractor, read by the dashboard.
 * Collection and {deviceId, createdAt desc} index: account changelog
 * 031-endpoint-shield-diagnostics.xml.
 */
public class EndpointShieldDiagnosticDao extends AccountsContextDao<EndpointShieldDiagnostic> {

    public static final EndpointShieldDiagnosticDao instance = new EndpointShieldDiagnosticDao();
    public static final String COLLECTION_NAME = "endpoint_shield_diagnostics";

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<EndpointShieldDiagnostic> getClassT() {
        return EndpointShieldDiagnostic.class;
    }
}
