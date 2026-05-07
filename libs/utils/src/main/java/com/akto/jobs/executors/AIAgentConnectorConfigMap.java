package com.akto.jobs.executors;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.akto.jobs.executors.AIAgentConnectorConstants.*;

public final class AIAgentConnectorConfigMap {

    private AIAgentConnectorConfigMap() {}

    // Keys that are safe to expose to the frontend
    private static final Map<String, Set<String>> PUBLIC_KEYS_BY_CONNECTOR = new HashMap<>();

    // Keys that must never leave the backend
    private static final Map<String, Set<String>> PRIVATE_KEYS_BY_CONNECTOR = new HashMap<>();

    static {
        // N8N
        PUBLIC_KEYS_BY_CONNECTOR.put(CONNECTOR_TYPE_N8N, keys(CONFIG_N8N_BASE_URL, CONFIG_DATA_INGESTION_SERVICE_URL));
        PRIVATE_KEYS_BY_CONNECTOR.put(CONNECTOR_TYPE_N8N, keys(CONFIG_N8N_API_KEY));

        // Langchain
        PUBLIC_KEYS_BY_CONNECTOR.put(CONNECTOR_TYPE_LANGCHAIN, keys(CONFIG_LANGSMITH_BASE_URL, CONFIG_DATA_INGESTION_SERVICE_URL));
        PRIVATE_KEYS_BY_CONNECTOR.put(CONNECTOR_TYPE_LANGCHAIN, keys(CONFIG_LANGSMITH_API_KEY));

        // Copilot Studio
        PUBLIC_KEYS_BY_CONNECTOR.put(CONNECTOR_TYPE_COPILOT_STUDIO, keys(CONFIG_DATAVERSE_ENVIRONMENT_URL, CONFIG_DATAVERSE_TENANT_ID, CONFIG_DATAVERSE_CLIENT_ID, CONFIG_DATA_INGESTION_SERVICE_URL));
        PRIVATE_KEYS_BY_CONNECTOR.put(CONNECTOR_TYPE_COPILOT_STUDIO, keys(CONFIG_DATAVERSE_CLIENT_SECRET));

        // Snowflake
        PUBLIC_KEYS_BY_CONNECTOR.put(CONNECTOR_TYPE_SNOWFLAKE, keys(CONFIG_SNOWFLAKE_ACCOUNT_URL, CONFIG_SNOWFLAKE_AUTH_TYPE, CONFIG_SNOWFLAKE_USERNAME, CONFIG_SNOWFLAKE_WAREHOUSE, CONFIG_SNOWFLAKE_DATABASE, CONFIG_SNOWFLAKE_SCHEMA, CONFIG_DATA_INGESTION_SERVICE_URL));
        PRIVATE_KEYS_BY_CONNECTOR.put(CONNECTOR_TYPE_SNOWFLAKE, keys(CONFIG_SNOWFLAKE_PASSWORD, CONFIG_SNOWFLAKE_TOKEN, CONFIG_SNOWFLAKE_PRIVATE_KEY, CONFIG_SNOWFLAKE_PRIVATE_KEY_PASSPHRASE));

        // Databricks
        PUBLIC_KEYS_BY_CONNECTOR.put(CONNECTOR_TYPE_DATABRICKS, keys(CONFIG_DATABRICKS_HOST, CONFIG_DATABRICKS_CLIENT_ID, CONFIG_DATABRICKS_CATALOG, CONFIG_DATABRICKS_SCHEMA, CONFIG_DATABRICKS_PREFIX, CONFIG_DATA_INGESTION_SERVICE_URL));
        PRIVATE_KEYS_BY_CONNECTOR.put(CONNECTOR_TYPE_DATABRICKS, keys(CONFIG_DATABRICKS_CLIENT_SECRET));

        // Vertex AI
        PUBLIC_KEYS_BY_CONNECTOR.put(CONNECTOR_TYPE_VERTEX_AI_CUSTOM_DEPLOYED_MODEL, keys(CONFIG_VERTEX_AI_PROJECT_ID, CONFIG_VERTEX_AI_BIGQUERY_DATASET, CONFIG_VERTEX_AI_BIGQUERY_TABLE, CONFIG_DATA_INGESTION_SERVICE_URL));
        PRIVATE_KEYS_BY_CONNECTOR.put(CONNECTOR_TYPE_VERTEX_AI_CUSTOM_DEPLOYED_MODEL, keys(CONFIG_VERTEX_AI_JSON_AUTH_FILE_PATH));

        // Salesforce
        PUBLIC_KEYS_BY_CONNECTOR.put(CONNECTOR_TYPE_SALESFORCE, keys(CONFIG_SALESFORCE_URL, CONFIG_SALESFORCE_CONSUMER_KEY, CONFIG_DATA_INGESTION_SERVICE_URL));
        PRIVATE_KEYS_BY_CONNECTOR.put(CONNECTOR_TYPE_SALESFORCE, keys(CONFIG_SALESFORCE_CONSUMER_SECRET, CONFIG_INGESTION_API_KEY));

        // Anthropic
        PUBLIC_KEYS_BY_CONNECTOR.put(CONNECTOR_TYPE_ANTHROPIC, keys(CONFIG_ANTHROPIC_API_BASE_URL, CONFIG_DATA_INGESTION_SERVICE_URL));
        PRIVATE_KEYS_BY_CONNECTOR.put(CONNECTOR_TYPE_ANTHROPIC, keys(CONFIG_ANTHROPIC_API_KEY));

        // OpenAI
        PUBLIC_KEYS_BY_CONNECTOR.put(CONNECTOR_TYPE_OPENAI, keys(CONFIG_OPENAI_ORG_ID, CONFIG_OPENAI_API_BASE_URL, CONFIG_DATA_INGESTION_SERVICE_URL));
        PRIVATE_KEYS_BY_CONNECTOR.put(CONNECTOR_TYPE_OPENAI, keys(CONFIG_OPENAI_API_KEY));
    }

    private static Set<String> keys(String... keys) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(keys)));
    }

    /**
     * Returns a filtered config map containing only public (non-sensitive) keys for the given connector type.
     * Any key not explicitly listed as public is excluded.
     */
    public static Map<String, Object> getPublicConfig(String connectorType, Map<String, Object> fullConfig) {
        if (fullConfig == null) return Collections.emptyMap();
        Set<String> allowed = PUBLIC_KEYS_BY_CONNECTOR.getOrDefault(connectorType, Collections.emptySet());
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : fullConfig.entrySet()) {
            if (allowed.contains(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    public static Set<String> getPrivateKeys(String connectorType) {
        return PRIVATE_KEYS_BY_CONNECTOR.getOrDefault(connectorType, Collections.emptySet());
    }

    public static Set<String> getPublicKeys(String connectorType) {
        return PUBLIC_KEYS_BY_CONNECTOR.getOrDefault(connectorType, Collections.emptySet());
    }
}
