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
    public static final String CONNECTOR_TYPE_DATABRICKS = "DATABRICKS";
    public static final String CONNECTOR_TYPE_SNOWFLAKE = "SNOWFLAKE";
    public static final String CONNECTOR_TYPE_VERTEX_AI_CUSTOM_DEPLOYED_MODEL = "VERTEX_AI_CUSTOM_DEPLOYED_MODEL";

    // Binary Names
    public static final String BINARY_NAME_N8N = "n8n-shield";
    public static final String BINARY_NAME_LANGCHAIN = "langchain-shield";
    public static final String BINARY_NAME_COPILOT_STUDIO = "copilot-shield";
    public static final String BINARY_NAME_DATABRICKS = "databricks-shield";
    public static final String BINARY_NAME_SNOWFLAKE = "snowflake-shield";

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

    // Configuration Keys - Snowflake
    public static final String CONFIG_SNOWFLAKE_ACCOUNT_URL = "SNOWFLAKE_ACCOUNT_URL";
    public static final String CONFIG_SNOWFLAKE_AUTH_TYPE = "SNOWFLAKE_AUTH_TYPE"; // "PASSWORD", "TOKEN", or "KEY_PAIR"
    public static final String CONFIG_SNOWFLAKE_USERNAME = "SNOWFLAKE_USERNAME";
    public static final String CONFIG_SNOWFLAKE_PASSWORD = "SNOWFLAKE_PASSWORD";
    public static final String CONFIG_SNOWFLAKE_TOKEN = "SNOWFLAKE_TOKEN"; // OAuth token
    public static final String CONFIG_SNOWFLAKE_PRIVATE_KEY = "SNOWFLAKE_PRIVATE_KEY"; // RSA private key for key pair auth
    public static final String CONFIG_SNOWFLAKE_PRIVATE_KEY_PASSPHRASE = "SNOWFLAKE_PRIVATE_KEY_PASSPHRASE"; // Optional passphrase for encrypted private key
    public static final String CONFIG_SNOWFLAKE_WAREHOUSE = "SNOWFLAKE_WAREHOUSE";
    public static final String CONFIG_SNOWFLAKE_DATABASE = "SNOWFLAKE_DATABASE";
    public static final String CONFIG_SNOWFLAKE_SCHEMA = "SNOWFLAKE_SCHEMA";
    
    // Snowflake Auth Types
    public static final String SNOWFLAKE_AUTH_TYPE_PASSWORD = "PASSWORD";
    public static final String SNOWFLAKE_AUTH_TYPE_TOKEN = "TOKEN";
    public static final String SNOWFLAKE_AUTH_TYPE_KEY_PAIR = "KEY_PAIR";

    // Configuration Keys - Databricks
    public static final String CONFIG_DATABRICKS_HOST = "DATABRICKS_HOST";
    public static final String CONFIG_DATABRICKS_CLIENT_ID = "DATABRICKS_CLIENT_ID";
    public static final String CONFIG_DATABRICKS_CLIENT_SECRET = "DATABRICKS_CLIENT_SECRET";
    public static final String CONFIG_DATABRICKS_CATALOG = "DATABRICKS_CATALOG";
    public static final String CONFIG_DATABRICKS_SCHEMA = "DATABRICKS_SCHEMA";
    public static final String CONFIG_DATABRICKS_PREFIX = "DATABRICKS_PREFIX";

    // Configuration Keys - Vertex AI Custom Deployed Model
    public static final String CONFIG_VERTEX_AI_PROJECT_ID = "VERTEX_AI_PROJECT_ID";
    public static final String CONFIG_VERTEX_AI_BIGQUERY_DATASET = "VERTEX_AI_BIGQUERY_DATASET";
    public static final String CONFIG_VERTEX_AI_BIGQUERY_TABLE = "VERTEX_AI_BIGQUERY_TABLE";

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
