/**
 * Constants for AI Agent Connector imports
 * Centralized location for all connector-related constants in the frontend
 */

// Connector Types
export const CONNECTOR_TYPE_N8N = 'N8N';
export const CONNECTOR_TYPE_LANGCHAIN = 'LANGCHAIN';
export const CONNECTOR_TYPE_COPILOT_STUDIO = 'COPILOT_STUDIO';
export const CONNECTOR_TYPE_DATABRICKS = 'DATABRICKS';

// Connector Names (Display)
export const CONNECTOR_NAME_N8N = 'N8N';
export const CONNECTOR_NAME_LANGCHAIN = 'Langchain';
export const CONNECTOR_NAME_COPILOT_STUDIO = 'Copilot Studio';
export const CONNECTOR_NAME_DATABRICKS = 'Databricks';

// Documentation URLs
export const DOCS_URL_N8N = 'https://docs.akto.io/traffic-connector/workflow-automation/n8n';
export const DOCS_URL_LANGCHAIN = 'https://docs.akto.io/traffic-connector/workflow-automation/langchain';
export const DOCS_URL_COPILOT_STUDIO = 'https://docs.akto.io/traffic-connector/workflow-automation/copilot-studio';
export const DOCS_URL_DATABRICKS = 'https://docs.akto.io/traffic-connector/workflow-automation/databricks';

// Recurring Interval Seconds (in seconds)
export const INTERVAL_N8N = 300; // 5 minutes
export const INTERVAL_LANGCHAIN = 300; // 5 minutes
export const INTERVAL_COPILOT_STUDIO = 300; // 5 minutes
export const INTERVAL_DATABRICKS = 300; // 5 minutes

// Field Names
export const FIELD_N8N_URL = 'n8nUrl';
export const FIELD_N8N_API_KEY = 'n8nApiKey';
export const FIELD_LANGSMITH_URL = 'langsmithUrl';
export const FIELD_LANGSMITH_API_KEY = 'langsmithApiKey';
export const FIELD_APPINSIGHTS_APP_ID = 'appInsightsAppId';
export const FIELD_APPINSIGHTS_API_KEY = 'appInsightsApiKey';
export const FIELD_DATABRICKS_HOST = 'databricksHost';
export const FIELD_DATABRICKS_CLIENT_ID = 'databricksClientId';
export const FIELD_DATABRICKS_CLIENT_SECRET = 'databricksClientSecret';
export const FIELD_DATABRICKS_CATALOG = 'databricksCatalog';
export const FIELD_DATABRICKS_SCHEMA = 'databricksSchema';
export const FIELD_DATABRICKS_PREFIX = 'databricksPrefix';
export const FIELD_DATA_INGESTION_URL = 'dataIngestionUrl';

// Field Types
export const FIELD_TYPE_TEXT = 'text';
export const FIELD_TYPE_URL = 'url';
export const FIELD_TYPE_PASSWORD = 'password';

// Descriptions
export const DESCRIPTION_N8N = 'Use our N8N feature to capture traffic and instantly send it to your dashboard for real-time insights.';
export const DESCRIPTION_LANGCHAIN = 'Use our Langchain feature to capture traffic from LangSmith and instantly send it to your dashboard for real-time insights.';
export const DESCRIPTION_COPILOT_STUDIO = 'Use our Copilot Studio feature to capture traffic from Azure Application Insights and instantly send it to your dashboard for real-time insights.';
export const DESCRIPTION_DATABRICKS = 'Use our Databricks feature to capture traffic from serving endpoint inference tables and instantly send it to your dashboard for real-time insights.';

// N8N Field Configuration
export const N8N_FIELDS = [
    {
        name: FIELD_N8N_URL,
        label: 'N8N URL',
        type: FIELD_TYPE_URL,
        placeholder: 'https://n8n.example.com',
        configKey: FIELD_N8N_URL
    },
    {
        name: FIELD_N8N_API_KEY,
        label: 'N8N API Key',
        type: FIELD_TYPE_PASSWORD,
        placeholder: '*******',
        configKey: FIELD_N8N_API_KEY
    },
    {
        name: FIELD_DATA_INGESTION_URL,
        label: 'URL for Data Ingestion Service',
        type: FIELD_TYPE_URL,
        placeholder: 'https://ingestion.example.com'
    }
];

// Langchain Field Configuration
export const LANGCHAIN_FIELDS = [
    {
        name: FIELD_LANGSMITH_URL,
        label: 'LangSmith Base URL',
        type: FIELD_TYPE_URL,
        placeholder: 'https://api.smith.langchain.com',
        configKey: FIELD_LANGSMITH_URL
    },
    {
        name: FIELD_LANGSMITH_API_KEY,
        label: 'LangSmith API Key',
        type: FIELD_TYPE_PASSWORD,
        placeholder: '*******',
        configKey: FIELD_LANGSMITH_API_KEY
    },
    {
        name: FIELD_DATA_INGESTION_URL,
        label: 'URL for Data Ingestion Service',
        type: FIELD_TYPE_URL,
        placeholder: 'https://ingestion.example.com'
    }
];

// Copilot Studio Field Configuration
export const COPILOT_STUDIO_FIELDS = [
    {
        name: FIELD_APPINSIGHTS_APP_ID,
        label: 'App Insights App ID',
        type: FIELD_TYPE_TEXT,
        placeholder: 'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx',
        configKey: FIELD_APPINSIGHTS_APP_ID
    },
    {
        name: FIELD_APPINSIGHTS_API_KEY,
        label: 'App Insights API Key',
        type: FIELD_TYPE_PASSWORD,
        placeholder: '*******',
        configKey: FIELD_APPINSIGHTS_API_KEY
    },
    {
        name: FIELD_DATA_INGESTION_URL,
        label: 'URL for Data Ingestion Service',
        type: FIELD_TYPE_URL,
        placeholder: 'https://ingestion.example.com'
    }
];

// Databricks Field Configuration
export const DATABRICKS_FIELDS = [
    {
        name: FIELD_DATABRICKS_HOST,
        label: 'Databricks Host',
        type: FIELD_TYPE_URL,
        placeholder: 'https://your-workspace.cloud.databricks.com',
        configKey: FIELD_DATABRICKS_HOST
    },
    {
        name: FIELD_DATABRICKS_CLIENT_ID,
        label: 'Databricks Client ID (Service Principal)',
        type: FIELD_TYPE_TEXT,
        placeholder: 'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx',
        configKey: FIELD_DATABRICKS_CLIENT_ID
    },
    {
        name: FIELD_DATABRICKS_CLIENT_SECRET,
        label: 'Databricks Client Secret',
        type: FIELD_TYPE_PASSWORD,
        placeholder: '*******',
        configKey: FIELD_DATABRICKS_CLIENT_SECRET
    },
    {
        name: FIELD_DATABRICKS_CATALOG,
        label: 'Unity Catalog Name (Optional)',
        type: FIELD_TYPE_TEXT,
        placeholder: 'main',
        configKey: FIELD_DATABRICKS_CATALOG
    },
    {
        name: FIELD_DATABRICKS_SCHEMA,
        label: 'Unity Catalog Schema (Optional)',
        type: FIELD_TYPE_TEXT,
        placeholder: 'default',
        configKey: FIELD_DATABRICKS_SCHEMA
    },
    {
        name: FIELD_DATABRICKS_PREFIX,
        label: 'Table Prefix (Optional)',
        type: FIELD_TYPE_TEXT,
        placeholder: '',
        configKey: FIELD_DATABRICKS_PREFIX
    },
    {
        name: FIELD_DATA_INGESTION_URL,
        label: 'URL for Data Ingestion Service',
        type: FIELD_TYPE_URL,
        placeholder: 'https://ingestion.example.com'
    }
];
