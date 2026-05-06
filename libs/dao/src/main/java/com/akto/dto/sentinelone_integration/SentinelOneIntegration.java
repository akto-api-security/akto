package com.akto.dto.sentinelone_integration;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class SentinelOneIntegration {

    public static final String API_TOKEN = "apiToken";
    private String apiToken;

    public static final String CONSOLE_URL = "consoleUrl";
    private String consoleUrl;

    public static final String DATA_INGESTION_URL = "dataIngestionUrl";
    private String dataIngestionUrl;

    public static final String GUARDRAILS_URL = "guardrailsUrl";
    private String guardrailsUrl;

    public static final String RECURRING_INTERVAL_SECONDS = "recurringIntervalSeconds";
    private int recurringIntervalSeconds = 3600;

    public static final String CREATED_TS = "createdTs";
    private int createdTs;

    public static final String UPDATED_TS = "updatedTs";
    private int updatedTs;

    // Guardrails configuration
    public static final String GUARDRAIL_TYPE = "guardrailType";
    private List<String> guardrailType; // cursor-hooks, openclaw-guardrails, etc.

    public static final String GUARDRAIL_ENV_VARS = "guardrailEnvVars";
    private Map<String, String> guardrailEnvVars; // Required environment variables

    public static final String GUARDRAIL_TARGET_MODE = "guardrailTargetMode";
    private String guardrailTargetMode; // "all" or "select"

    public static final String GUARDRAIL_AGENT_IDS = "guardrailAgentIds";
    private List<String> guardrailAgentIds; // For select mode only

    public static final String GUARDRAIL_STATUS = "guardrailStatus";
    private String guardrailStatus; // pending, running, completed, failed

    public static final String GUARDRAIL_LAST_EXECUTION_TS = "guardrailLastExecutionTs";
    private int guardrailLastExecutionTs;

    public SentinelOneIntegration() {}

    public SentinelOneIntegration(String apiToken, String consoleUrl, String dataIngestionUrl,
                                   String guardrailsUrl, int recurringIntervalSeconds, int createdTs, int updatedTs) {
        this.apiToken = apiToken;
        this.consoleUrl = consoleUrl;
        this.dataIngestionUrl = dataIngestionUrl;
        this.guardrailsUrl = guardrailsUrl;
        this.recurringIntervalSeconds = recurringIntervalSeconds;
        this.createdTs = createdTs;
        this.updatedTs = updatedTs;
    }
}
