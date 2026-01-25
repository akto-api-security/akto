package com.akto.jobs.executors;

/**
 * Constants for AI Agent Connector operations.
 * Centralized location for all connector-related constants.
 */
public final class AIAgentConnectorConstants {

    private AIAgentConnectorConstants() {
        // Prevent instantiation
    }

    // Connector Types
    public static final String CONNECTOR_TYPE_N8N = "N8N";
    public static final String CONNECTOR_TYPE_LANGCHAIN = "LANGCHAIN";
    public static final String CONNECTOR_TYPE_COPILOT_STUDIO = "COPILOT_STUDIO";

    // Binary Names
    public static final String BINARY_NAME_N8N = "n8n-shield";
    public static final String BINARY_NAME_LANGCHAIN = "langchain-shield";
    public static final String BINARY_NAME_COPILOT_STUDIO = "copilot-shield";

    // Configuration Keys - N8N
    public static final String CONFIG_N8N_BASE_URL = "N8N_BASE_URL";
    public static final String CONFIG_N8N_API_KEY = "N8N_API_KEY";

    // Configuration Keys - Langchain
    public static final String CONFIG_LANGSMITH_BASE_URL = "LANGSMITH_BASE_URL";
    public static final String CONFIG_LANGSMITH_API_KEY = "LANGSMITH_API_KEY";

    // Configuration Keys - Copilot Studio (Dataverse API)
    public static final String CONFIG_DATAVERSE_ENVIRONMENT_URL = "DATAVERSE_ENVIRONMENT_URL";
    public static final String CONFIG_DATAVERSE_TENANT_ID = "DATAVERSE_TENANT_ID";
    public static final String CONFIG_DATAVERSE_CLIENT_ID = "DATAVERSE_CLIENT_ID";
    public static final String CONFIG_DATAVERSE_CLIENT_SECRET = "DATAVERSE_CLIENT_SECRET";

    // Common Configuration Keys
    public static final String CONFIG_DATA_INGESTION_SERVICE_URL = "DATA_INGESTION_SERVICE_URL";

    // JWT Token Configuration
    public static final String CONFIG_JWT_TOKEN = "AKTO_JWT_TOKEN";
    public static final String JWT_SUBJECT_AI_AGENT_CONNECTOR = "ai-agent-connector";
    public static final int JWT_EXPIRY_HOURS = 3; // 3 hours for short-lived tokens

    // Binary Paths
    public static final String BINARY_BASE_PATH = "apps/dashboard/src/main/java/com/akto/action/";
    public static final int BINARY_TIMEOUT_SECONDS = 300; // 5 minutes

    // Azure Blob Storage
    public static final String AZURE_CONNECTION_STRING_ENV = "AZURE_BINARY_STORAGE_CONNECTION_STRING";
    public static final String AZURE_BLOB_URL_ENV = "AZURE_BINARY_BLOB_URL";
    public static final String AZURE_CONTAINER_NAME = "binaries";

    // Default Job Settings
    public static final int DEFAULT_RECURRING_INTERVAL_SECONDS = 5; // 5 seconds
}
