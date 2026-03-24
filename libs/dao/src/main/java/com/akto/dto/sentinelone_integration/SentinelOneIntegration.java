package com.akto.dto.sentinelone_integration;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SentinelOneIntegration {

    public static final String API_TOKEN = "apiToken";
    private String apiToken;

    public static final String CONSOLE_URL = "consoleUrl";
    private String consoleUrl;

    public static final String DATA_INGESTION_URL = "dataIngestionUrl";
    private String dataIngestionUrl;

    public static final String RECURRING_INTERVAL_SECONDS = "recurringIntervalSeconds";
    private int recurringIntervalSeconds = 3600;

    public static final String CREATED_TS = "createdTs";
    private int createdTs;

    public static final String UPDATED_TS = "updatedTs";
    private int updatedTs;

    public SentinelOneIntegration() {}

    public SentinelOneIntegration(String apiToken, String consoleUrl, String dataIngestionUrl,
                                   int recurringIntervalSeconds, int createdTs, int updatedTs) {
        this.apiToken = apiToken;
        this.consoleUrl = consoleUrl;
        this.dataIngestionUrl = dataIngestionUrl;
        this.recurringIntervalSeconds = recurringIntervalSeconds;
        this.createdTs = createdTs;
        this.updatedTs = updatedTs;
    }
}
