package com.akto.jobs.executors;

/**
 * Constants for AI Agent Connector operations.
 * Centralized location for all connector-related constants.
 */
public final class AIAgentConnectorConstants {

    private AIAgentConnectorConstants() {
        // Prevent instantiation
    }

    // Job Types (for AccountJob.jobType)
    public static final String JOB_TYPE_AI_AGENT_CONNECTOR = "AI_AGENT_CONNECTOR";
    public static final String JOB_TYPE_VERTEX_AI_CUSTOM_DEPLOYED_MODEL_CONNECTOR = "VERTEX_AI_CUSTOM_DEPLOYED_MODEL_CONNECTOR";

    // Connector Types
    public static final String CONNECTOR_TYPE_N8N = "N8N";
    public static final String CONNECTOR_TYPE_LANGCHAIN = "LANGCHAIN";
    public static final String CONNECTOR_TYPE_COPILOT_STUDIO = "COPILOT_STUDIO";
    public static final String CONNECTOR_TYPE_DATABRICKS = "DATABRICKS";
    public static final String CONNECTOR_TYPE_SNOWFLAKE = "SNOWFLAKE";
    public static final String CONNECTOR_TYPE_VERTEX_AI_CUSTOM_DEPLOYED_MODEL = "VERTEX_AI_CUSTOM_DEPLOYED_MODEL";
    public static final String CONNECTOR_TYPE_SALESFORCE = "SALESFORCE";
    public static final String CONNECTOR_TYPE_ANTHROPIC = "ANTHROPIC";
    public static final String CONNECTOR_TYPE_OPENAI = "OPENAI";
    public static final String CONNECTOR_TYPE_CLICKUP = "CLICKUP";

    // Binary Names
    public static final String BINARY_NAME_N8N = "n8n-shield";
    public static final String BINARY_NAME_LANGCHAIN = "langchain-shield";
    public static final String BINARY_NAME_COPILOT_STUDIO = "copilot-shield";
    public static final String BINARY_NAME_DATABRICKS = "databricks-shield";
    public static final String BINARY_NAME_SNOWFLAKE = "snowflake-shield";
    public static final String BINARY_NAME_ANTHROPIC = "claude-shield";
    public static final String BINARY_NAME_OPENAI = "openai-shield";

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
    public static final String CONFIG_VERTEX_AI_JSON_AUTH_FILE_PATH = "VERTEX_AI_JSON_AUTH_FILE_PATH";

    // Configuration Keys - Salesforce
    public static final String CONFIG_SALESFORCE_URL = "SALESFORCE_URL";
    public static final String CONFIG_SALESFORCE_CONSUMER_KEY = "SALESFORCE_CONSUMER_KEY";
    public static final String CONFIG_SALESFORCE_CONSUMER_SECRET = "SALESFORCE_CONSUMER_SECRET";
    public static final String CONFIG_INGESTION_API_KEY = "INGESTION_API_KEY";

    // Configuration Keys - Anthropic
    public static final String CONFIG_ANTHROPIC_API_KEY = "ANTHROPIC_API_KEY";
    public static final String CONFIG_ANTHROPIC_API_BASE_URL = "ANTHROPIC_API_BASE_URL";

    // Configuration Keys - OpenAI
    public static final String CONFIG_OPENAI_API_KEY = "OPENAI_API_KEY";
    public static final String CONFIG_OPENAI_ORG_ID = "OPENAI_ORG_ID";
    public static final String CONFIG_OPENAI_API_BASE_URL = "OPENAI_API_BASE_URL";

    // Configuration Keys - ClickUp
    public static final String CONFIG_CLICKUP_BASE_URL = "CLICKUP_BASE_URL";
    public static final String CONFIG_CLICKUP_API_TOKEN = "CLICKUP_API_TOKEN";
    public static final String CONFIG_CLICKUP_WORKSPACE_ID = "CLICKUP_WORKSPACE_ID";
    public static final String CONFIG_CLICKUP_PAGE_ROWS = "CLICKUP_PAGE_ROWS";
    public static final String CONFIG_CLICKUP_LOOKBACK_HOURS = "CLICKUP_LOOKBACK_HOURS";
    public static final String CONFIG_CLICKUP_LAST_TIMESTAMP = "CLICKUP_LAST_TIMESTAMP";

    // Salesforce API Configuration
    public static final String SALESFORCE_API_VERSION = "v66.0";
    public static final String SALESFORCE_OAUTH_TOKEN_ENDPOINT = "/services/oauth2/token";
    public static final String SALESFORCE_QUERY_SQL_ENDPOINT = "/services/data/{version}/ssot/query-sql";
    public static final String SALESFORCE_OAUTH_GRANT_TYPE = "client_credentials";
    public static final String SALESFORCE_TABLE_NAME = "ssot__AiAgentInteractionMessage__dlm";
    public static final String SALESFORCE_ORDER_BY = "ssot__Id__c";

    // HTTP Headers & Constants
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HEADER_X_API_KEY = "X-API-KEY";
    public static final String HEADER_X_BATCH_ID = "X-Batch-ID";
    public static final String CONTENT_TYPE_JSON = "application/json";
    public static final String AUTH_BEARER_PREFIX = "Bearer ";

    // HTTP Methods & Status
    public static final String HTTP_METHOD_POST = "POST";
    public static final String HTTP_METHOD_GET = "GET";
    public static final int HTTP_STATUS_200 = 200;
    public static final int HTTP_STATUS_401 = 401;
    public static final int HTTP_STATUS_500 = 500;
    public static final int HTTP_STATUS_503 = 503;
    public static final String HTTP_VERSION = "HTTP/1.1";
    public static final String HTTP_STATUS_OK = "OK";

    // Ingestion API Configuration
    public static final String INGESTION_API_ENDPOINT = "/api/ingestData";
    public static final String INGESTION_BATCH_DATA_KEY = "batchData";
    public static final int INGESTION_DEFAULT_BATCH_SIZE = 50;

    // Data Transformation Constants
    public static final String DATA_SOURCE_MIRRORING = "MIRRORING";
    public static final String DATA_TAG_GEN_AI = "Gen AI";
    public static final String AKTO_VXLAN_ID_DEFAULT = "0";
    public static final String IS_PENDING_FALSE = "false";
    public static final String IP_ADDRESS_DEFAULT = "10.0.0.1";
    public static final String AKTO_ACCOUNT_ID_CONSTANT = "1000000";

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
