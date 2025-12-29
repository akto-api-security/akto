/**
 * Constants for AI Agent Connector imports
 * Centralized location for all connector-related constants in the frontend
 */

// Connector Types
export const CONNECTOR_TYPE_N8N = 'N8N';
export const CONNECTOR_TYPE_LANGCHAIN = 'LANGCHAIN';
export const CONNECTOR_TYPE_COPILOT_STUDIO = 'COPILOT_STUDIO';

// Connector Names (Display)
export const CONNECTOR_NAME_N8N = 'N8N';
export const CONNECTOR_NAME_LANGCHAIN = 'Langchain';
export const CONNECTOR_NAME_COPILOT_STUDIO = 'Copilot Studio';

// Documentation URLs
export const DOCS_URL_N8N = 'https://docs.akto.io/traffic-connector/workflow-automation/n8n';
export const DOCS_URL_LANGCHAIN = 'https://docs.akto.io/traffic-connector/workflow-automation/langchain';
export const DOCS_URL_COPILOT_STUDIO = 'https://docs.akto.io/traffic-connector/workflow-automation/copilot-studio';

// Recurring Interval Seconds (in seconds)
export const INTERVAL_N8N = 300; // 5 minutes
export const INTERVAL_LANGCHAIN = 300; // 5 minutes
export const INTERVAL_COPILOT_STUDIO = 300; // 5 minutes

// Field Names
export const FIELD_N8N_URL = 'n8nUrl';
export const FIELD_N8N_API_KEY = 'n8nApiKey';
export const FIELD_LANGSMITH_URL = 'langsmithUrl';
export const FIELD_LANGSMITH_API_KEY = 'langsmithApiKey';
export const FIELD_DATAVERSE_ENVIRONMENT_URL = 'dataverseEnvironmentUrl';
export const FIELD_DATAVERSE_TENANT_ID = 'dataverseTenantId';
export const FIELD_DATAVERSE_CLIENT_ID = 'dataverseClientId';
export const FIELD_DATAVERSE_CLIENT_SECRET = 'dataverseClientSecret';
export const FIELD_DATA_INGESTION_URL = 'dataIngestionUrl';

// Field Types
export const FIELD_TYPE_TEXT = 'text';
export const FIELD_TYPE_URL = 'url';
export const FIELD_TYPE_PASSWORD = 'password';

// Descriptions
export const DESCRIPTION_N8N = 'Use our N8N feature to capture traffic and instantly send it to your dashboard for real-time insights.';
export const DESCRIPTION_LANGCHAIN = 'Use our Langchain feature to capture traffic from LangSmith and instantly send it to your dashboard for real-time insights.';
export const DESCRIPTION_COPILOT_STUDIO = 'Use our Copilot Studio feature to capture conversation data from Azure Dataverse API and instantly send it to your dashboard for real-time insights.';

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
        name: FIELD_DATAVERSE_ENVIRONMENT_URL,
        label: 'Dataverse Environment URL',
        type: FIELD_TYPE_URL,
        placeholder: 'https://org.crm.dynamics.com',
        configKey: FIELD_DATAVERSE_ENVIRONMENT_URL
    },
    {
        name: FIELD_DATAVERSE_TENANT_ID,
        label: 'Azure AD Tenant ID',
        type: FIELD_TYPE_TEXT,
        placeholder: 'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx',
        configKey: FIELD_DATAVERSE_TENANT_ID
    },
    {
        name: FIELD_DATAVERSE_CLIENT_ID,
        label: 'Azure AD App Client ID',
        type: FIELD_TYPE_TEXT,
        placeholder: 'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx',
        configKey: FIELD_DATAVERSE_CLIENT_ID
    },
    {
        name: FIELD_DATAVERSE_CLIENT_SECRET,
        label: 'Azure AD App Client Secret',
        type: FIELD_TYPE_PASSWORD,
        placeholder: '*******',
        configKey: FIELD_DATAVERSE_CLIENT_SECRET
    },
    {
        name: FIELD_DATA_INGESTION_URL,
        label: 'URL for Data Ingestion Service',
        type: FIELD_TYPE_URL,
        placeholder: 'https://ingestion.example.com'
    }
];
