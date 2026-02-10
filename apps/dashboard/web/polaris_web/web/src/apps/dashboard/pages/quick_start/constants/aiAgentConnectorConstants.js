/**
 * Constants for AI Agent Connector imports
 * Centralized location for all connector-related constants in the frontend
 */

// Connector Types
export const CONNECTOR_TYPE_N8N = 'N8N';
export const CONNECTOR_TYPE_LANGCHAIN = 'LANGCHAIN';
export const CONNECTOR_TYPE_COPILOT_STUDIO = 'COPILOT_STUDIO';
export const CONNECTOR_TYPE_DATABRICKS = 'DATABRICKS';
export const CONNECTOR_TYPE_SNOWFLAKE = 'SNOWFLAKE';
export const CONNECTOR_TYPE_VERTEX_AI_CUSTOM_DEPLOYED_MODEL = 'VERTEX_AI_CUSTOM_DEPLOYED_MODEL';

// Connector Names (Display)
export const CONNECTOR_NAME_N8N = 'N8N';
export const CONNECTOR_NAME_LANGCHAIN = 'Langchain';
export const CONNECTOR_NAME_COPILOT_STUDIO = 'Copilot Studio';
export const CONNECTOR_NAME_DATABRICKS = 'Databricks';
export const CONNECTOR_NAME_SNOWFLAKE = 'Snowflake';
export const CONNECTOR_NAME_VERTEX_AI_CUSTOM_DEPLOYED_MODEL = 'Vertex AI Custom Deployed Model';

// Documentation URLs
export const DOCS_URL_N8N = 'https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/connectors/others/workflow-automation/n8n';
export const DOCS_URL_LANGCHAIN = 'https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/connectors/others/workflow-automation/langchain';
export const DOCS_URL_COPILOT_STUDIO = 'https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/connectors/others/workflow-automation/microsoft-copilot-studio';
export const DOCS_URL_DATABRICKS = 'https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/connectors/others/workflow-automation/databrics';
export const DOCS_URL_SNOWFLAKE = 'https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/connectors/others/workflow-automation/snowflake';
export const DOCS_URL_VERTEX_AI_CUSTOM_DEPLOYED_MODEL = 'https://ai-security-docs.akto.io/akto-argus-agentic-ai-security-for-homegrown-ai/connectors/others/workflow-automation/vertex-ai-custom-deployed-model';

// Recurring Interval Seconds (in seconds)
export const INTERVAL_N8N = 300; // 5 minutes
export const INTERVAL_LANGCHAIN = 300; // 5 minutes
export const INTERVAL_COPILOT_STUDIO = 300; // 5 minutes
export const INTERVAL_DATABRICKS = 300; // 5 minutes
export const INTERVAL_SNOWFLAKE = 300; // 5 minutes
export const INTERVAL_VERTEX_AI_CUSTOM_DEPLOYED_MODEL = 300; // 5 minutes

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
export const FIELD_DATAVERSE_ENVIRONMENT_URL = 'dataverseEnvironmentUrl';
export const FIELD_DATAVERSE_TENANT_ID = 'dataverseTenantId';
export const FIELD_DATAVERSE_CLIENT_ID = 'dataverseClientId';
export const FIELD_DATAVERSE_CLIENT_SECRET = 'dataverseClientSecret';
export const FIELD_SNOWFLAKE_ACCOUNT_URL = 'snowflakeAccountUrl';
export const FIELD_SNOWFLAKE_AUTH_TYPE = 'snowflakeAuthType';
export const FIELD_SNOWFLAKE_USERNAME = 'snowflakeUsername';
export const FIELD_SNOWFLAKE_PASSWORD = 'snowflakePassword';
export const FIELD_SNOWFLAKE_TOKEN = 'snowflakeToken';
export const FIELD_SNOWFLAKE_PRIVATE_KEY = 'snowflakePrivateKey';
export const FIELD_SNOWFLAKE_PRIVATE_KEY_PASSPHRASE = 'snowflakePrivateKeyPassphrase';
export const FIELD_SNOWFLAKE_WAREHOUSE = 'snowflakeWarehouse';
export const FIELD_SNOWFLAKE_DATABASE = 'snowflakeDatabase';
export const FIELD_SNOWFLAKE_SCHEMA = 'snowflakeSchema';
export const FIELD_DATA_INGESTION_URL = 'dataIngestionUrl';

// Vertex AI Custom Deployed Model Fields
export const FIELD_VERTEX_AI_PROJECT_ID = 'vertexAIProjectId';
export const FIELD_VERTEX_AI_BIGQUERY_DATASET = 'vertexAIBigQueryDataset';
export const FIELD_VERTEX_AI_BIGQUERY_TABLE = 'vertexAIBigQueryTable';
export const FIELD_VERTEX_AI_JSON_AUTH_FILE_PATH = 'vertexAIJsonAuthFilePath';

// Field Types
export const FIELD_TYPE_TEXT = 'text';
export const FIELD_TYPE_URL = 'url';
export const FIELD_TYPE_PASSWORD = 'password';
export const FIELD_TYPE_SELECT = 'select';
export const FIELD_TYPE_TEXTAREA = 'textarea';

// Auth Types (for Snowflake)
export const AUTH_TYPE_PASSWORD = 'PASSWORD';
export const AUTH_TYPE_TOKEN = 'TOKEN';
export const AUTH_TYPE_KEY_PAIR = 'KEY_PAIR';

// Descriptions
export const DESCRIPTION_N8N = 'Use our N8N feature to capture traffic and instantly send it to your dashboard for real-time insights.';
export const DESCRIPTION_LANGCHAIN = 'Use our Langchain feature to capture traffic from LangSmith and instantly send it to your dashboard for real-time insights.';
export const DESCRIPTION_COPILOT_STUDIO = 'Use our Copilot Studio feature to capture traffic from Azure Application Insights and instantly send it to your dashboard for real-time insights.';
export const DESCRIPTION_DATABRICKS = 'Import Databricks agents seamlessly into AKTO.';
export const DESCRIPTION_SNOWFLAKE = 'Connect to your Snowflake account to discover agents using Cortex and automatically fetch data for all Snowflake agents.';
export const DESCRIPTION_VERTEX_AI_CUSTOM_DEPLOYED_MODEL = 'Connect to your GCP BigQuery to import Vertex AI Custom Deployed Model prediction logs into AKTO.';

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

// Snowflake Field Configuration
export const SNOWFLAKE_FIELDS = [
    {
        name: FIELD_SNOWFLAKE_ACCOUNT_URL,
        label: 'Snowflake Account URL',
        type: FIELD_TYPE_URL,
        placeholder: 'https://your-account.snowflakecomputing.com',
        configKey: FIELD_SNOWFLAKE_ACCOUNT_URL,
        helpText: 'Your Snowflake account URL (e.g., https://xyz12345.us-east-1.snowflakecomputing.com)',
        required: true
    },
    {
        name: FIELD_SNOWFLAKE_AUTH_TYPE,
        label: 'Authentication Method',
        type: FIELD_TYPE_SELECT,
        configKey: FIELD_SNOWFLAKE_AUTH_TYPE,
        options: [
            { label: 'Username & Password', value: AUTH_TYPE_PASSWORD },
            { label: 'OAuth Token', value: AUTH_TYPE_TOKEN },
            { label: 'Key Pair (RSA)', value: AUTH_TYPE_KEY_PAIR }
        ],
        defaultValue: AUTH_TYPE_PASSWORD,
        helpText: 'Select the authentication method for your Snowflake account',
        required: true
    },
    {
        name: FIELD_SNOWFLAKE_USERNAME,
        label: 'Username',
        type: FIELD_TYPE_TEXT,
        placeholder: 'snowflake_user',
        configKey: FIELD_SNOWFLAKE_USERNAME,
        helpText: 'Snowflake username (required for Password and Key Pair authentication)',
        // Runtime component uses this function; keep signature (authType) => boolean
        showWhen: (authType) => authType === AUTH_TYPE_PASSWORD || authType === AUTH_TYPE_KEY_PAIR,
        required: true
    },
    {
        name: FIELD_SNOWFLAKE_PASSWORD,
        label: 'Password',
        type: FIELD_TYPE_PASSWORD,
        placeholder: '*******',
        configKey: FIELD_SNOWFLAKE_PASSWORD,
        helpText: 'Snowflake password (required for Password authentication)',
        showWhen: (authType) => authType === AUTH_TYPE_PASSWORD,
        required: true
    },
    {
        name: FIELD_SNOWFLAKE_TOKEN,
        label: 'OAuth Token',
        type: FIELD_TYPE_PASSWORD,
        placeholder: 'eyJhbGciOiJSUzI1NiIs...',
        configKey: FIELD_SNOWFLAKE_TOKEN,
        helpText: 'OAuth access token for Snowflake (required for Token authentication)',
        showWhen: (authType) => authType === AUTH_TYPE_TOKEN,
        required: true
    },
    {
        name: FIELD_SNOWFLAKE_PRIVATE_KEY,
        label: 'Private Key (RSA)',
        type: FIELD_TYPE_TEXTAREA,
        placeholder: '-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC...\n-----END PRIVATE KEY-----',
        configKey: FIELD_SNOWFLAKE_PRIVATE_KEY,
        helpText: 'Paste your RSA private key in PEM format. The corresponding public key must be registered in Snowflake.',
        showWhen: (authType) => authType === AUTH_TYPE_KEY_PAIR,
        multiline: 6,
        required: true
    },
    {
        name: FIELD_SNOWFLAKE_PRIVATE_KEY_PASSPHRASE,
        label: 'Private Key Passphrase (Optional)',
        type: FIELD_TYPE_PASSWORD,
        placeholder: 'Leave empty if key is not encrypted',
        configKey: FIELD_SNOWFLAKE_PRIVATE_KEY_PASSPHRASE,
        helpText: 'Only required if your private key is encrypted with a passphrase',
        showWhen: (authType) => authType === AUTH_TYPE_KEY_PAIR,
        required: false
    },
    {
        name: FIELD_SNOWFLAKE_WAREHOUSE,
        label: 'Warehouse (Optional)',
        type: FIELD_TYPE_TEXT,
        placeholder: 'COMPUTE_WH',
        configKey: FIELD_SNOWFLAKE_WAREHOUSE,
        helpText: 'Optional: Specify a warehouse to use for queries',
        required: false
    },
    {
        name: FIELD_SNOWFLAKE_DATABASE,
        label: 'Database (Optional)',
        type: FIELD_TYPE_TEXT,
        placeholder: 'SNOWFLAKE',
        configKey: FIELD_SNOWFLAKE_DATABASE,
        helpText: 'Optional: Specify a database to query',
        required: false
    },
    {
        name: FIELD_SNOWFLAKE_SCHEMA,
        label: 'Schema (Optional)',
        type: FIELD_TYPE_TEXT,
        placeholder: 'PUBLIC',
        configKey: FIELD_SNOWFLAKE_SCHEMA,
        helpText: 'Optional: Specify a schema to query',
        required: false
    },
    {
        name: FIELD_DATA_INGESTION_URL,
        label: 'URL for Data Ingestion Service',
        type: FIELD_TYPE_URL,
        placeholder: 'https://ingestion.example.com',
        helpText: 'URL of your Akto data ingestion service',
        required: true
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
        label: 'Unity Catalog Name',
        type: FIELD_TYPE_TEXT,
        placeholder: 'workspace',
        configKey: FIELD_DATABRICKS_CATALOG,
        defaultValue: "workspace"
    },
    {
        name: FIELD_DATABRICKS_SCHEMA,
        label: 'Unity Catalog Schema',
        type: FIELD_TYPE_TEXT,
        placeholder: 'default',
        configKey: FIELD_DATABRICKS_SCHEMA,
        defaultValue: 'default'
    },
    {
        name: FIELD_DATABRICKS_PREFIX,
        label: 'Table Prefix (Optional)',
        type: FIELD_TYPE_TEXT,
        placeholder: '',
        configKey: FIELD_DATABRICKS_PREFIX,
        required: false
    },
    {
        name: FIELD_DATA_INGESTION_URL,
        label: 'URL for Data Ingestion Service',
        type: FIELD_TYPE_URL,
        placeholder: 'https://ingestion.example.com'
    }
];

// Vertex AI Custom Deployed Model Field Configuration
export const VERTEX_AI_CUSTOM_DEPLOYED_MODEL_FIELDS = [
    {
        name: FIELD_VERTEX_AI_PROJECT_ID,
        label: 'GCP Project ID',
        type: FIELD_TYPE_TEXT,
        placeholder: 'gcp_project_id',
        configKey: FIELD_VERTEX_AI_PROJECT_ID,
        helpText: 'Your Google Cloud Platform project ID containing the BigQuery dataset',
        required: true
    },
    {
        name: FIELD_VERTEX_AI_BIGQUERY_DATASET,
        label: 'BigQuery Dataset',
        type: FIELD_TYPE_TEXT,
        placeholder: 'vertex_ai_logs',
        configKey: FIELD_VERTEX_AI_BIGQUERY_DATASET,
        helpText: 'BigQuery dataset containing Vertex AI prediction logs',
        required: true
    },
    {
        name: FIELD_VERTEX_AI_BIGQUERY_TABLE,
        label: 'BigQuery Table',
        type: FIELD_TYPE_TEXT,
        placeholder: 'predictions',
        configKey: FIELD_VERTEX_AI_BIGQUERY_TABLE,
        helpText: 'BigQuery table with Vertex AI Custom Deployed Model logs',
        required: true
    },
    {
        name: FIELD_VERTEX_AI_JSON_AUTH_FILE_PATH,
        label: 'JSON Authentication File Path (Optional)',
        type: FIELD_TYPE_TEXT,
        placeholder: 'path/to/service-account-key.json',
        configKey: FIELD_VERTEX_AI_JSON_AUTH_FILE_PATH,
        helpText: 'Optional: Specify the path to the JSON authentication file for BigQuery. If not provided, defaults to Application Default Credentials (ADC)',
        required: false
    },
    {
        name: FIELD_DATA_INGESTION_URL,
        label: 'URL for Data Ingestion Service',
        type: FIELD_TYPE_URL,
        placeholder: 'https://ingestion.example.com',
        helpText: 'URL of your Akto data ingestion service',
        required: true
    }
];

