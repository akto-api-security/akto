package com.akto.dto.adx_integration;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdxIntegration {

    public static final String CLUSTER_ENDPOINT = "clusterEndpoint";
    private String clusterEndpoint;
    public static final String DATABASE_NAME = "databaseName";
    private String databaseName;
    public static final String TENANT_ID = "tenantId";
    private String tenantId;
    public static final String APPLICATION_CLIENT_ID = "applicationClientId";
    private String applicationClientId;
    public static final String APPLICATION_KEY = "applicationKey";
    private String applicationKey;
    public static final String CREATED_TS = "createdTs";
    private int createdTs;
    public static final String UPDATED_TS = "updatedTs";
    private int updatedTs;

    public AdxIntegration() {}

    public AdxIntegration(String clusterEndpoint, String databaseName, 
                         String tenantId, String applicationClientId, String applicationKey, 
                         int createdTs, int updatedTs) {
        this.clusterEndpoint = clusterEndpoint;
        this.databaseName = databaseName;
        this.tenantId = tenantId;
        this.applicationClientId = applicationClientId;
        this.applicationKey = applicationKey;
        this.createdTs = createdTs;
        this.updatedTs = updatedTs;
    }
}

