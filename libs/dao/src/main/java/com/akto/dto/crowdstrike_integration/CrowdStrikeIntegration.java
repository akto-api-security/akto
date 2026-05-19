package com.akto.dto.crowdstrike_integration;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CrowdStrikeIntegration {

    public static final String CLIENT_ID = "clientId";
    private String clientId;

    public static final String CLIENT_SECRET = "clientSecret";
    private String clientSecret;

    public static final String BASE_URL = "baseUrl";
    private String baseUrl;

    public static final String DATA_INGESTION_URL = "dataIngestionUrl";
    private String dataIngestionUrl;

    public static final String RECURRING_INTERVAL_SECONDS = "recurringIntervalSeconds";
    private int recurringIntervalSeconds = 3600;

    public static final String CREATED_TS = "createdTs";
    private int createdTs;

    public static final String UPDATED_TS = "updatedTs";
    private int updatedTs;

    public CrowdStrikeIntegration() {}

    public CrowdStrikeIntegration(String clientId, String clientSecret, String baseUrl,
                                   String dataIngestionUrl, int recurringIntervalSeconds,
                                   int createdTs, int updatedTs) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.baseUrl = baseUrl;
        this.dataIngestionUrl = dataIngestionUrl;
        this.recurringIntervalSeconds = recurringIntervalSeconds;
        this.createdTs = createdTs;
        this.updatedTs = updatedTs;
    }
}
