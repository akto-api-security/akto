package com.akto.dao;

import com.akto.dto.open_telemetry_integration.OpenTelemetryIntegration;

public class OpenTelemetryIntegrationDao extends AccountsContextDao<OpenTelemetryIntegration> {

    public static final OpenTelemetryIntegrationDao instance = new OpenTelemetryIntegrationDao();

    private OpenTelemetryIntegrationDao() {}

    @Override
    public String getCollName() {
        return "open_telemetry_integration";
    }

    @Override
    public Class<OpenTelemetryIntegration> getClassT() {
        return OpenTelemetryIntegration.class;
    }
}
