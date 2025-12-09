package com.akto.action;

/**
 * Constants for AI Agent Connector Import Actions.
 * Centralized location for all connector-related constants in the action layer.
 */
public final class AIAgentConnectorConstants {

    private AIAgentConnectorConstants() {
        // Prevent instantiation
    }

    // Connector Types
    public static final String CONNECTOR_TYPE_N8N = "N8N";
    public static final String CONNECTOR_TYPE_LANGCHAIN = "LANGCHAIN";
    public static final String CONNECTOR_TYPE_COPILOT_STUDIO = "COPILOT_STUDIO";

    // Configuration Keys - N8N
    public static final String CONFIG_N8N_BASE_URL = "N8N_BASE_URL";
    public static final String CONFIG_N8N_API_KEY = "N8N_API_KEY";

    // Configuration Keys - Langchain
    public static final String CONFIG_LANGSMITH_BASE_URL = "LANGSMITH_BASE_URL";
    public static final String CONFIG_LANGSMITH_API_KEY = "LANGSMITH_API_KEY";

    // Configuration Keys - Copilot Studio
    public static final String CONFIG_APPINSIGHTS_APP_ID = "APPINSIGHTS_APP_ID";
    public static final String CONFIG_APPINSIGHTS_API_KEY = "APPINSIGHTS_API_KEY";

    // Common Configuration Keys
    public static final String CONFIG_DATA_INGESTION_SERVICE_URL = "DATA_INGESTION_SERVICE_URL";

    // Default Job Settings
    public static final int DEFAULT_RECURRING_INTERVAL_SECONDS = 300; // 5 minutes
}
