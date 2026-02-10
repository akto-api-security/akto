package com.akto.jobs.executors;

import static com.akto.jobs.executors.AIAgentConnectorConstants.*;

/**
 * Utility class for AI Agent Connector operations.
 * Provides common validation and helper methods for AI Agent Connectors.
 */
public final class AIAgentConnectorUtils {

    private AIAgentConnectorUtils() {
        // Prevent instantiation
    }

    /**
     * Validates if the connector type is supported.
     *
     * @param connectorType The connector type to validate
     * @return true if the connector type is valid, false otherwise
     */
    public static boolean isValidConnectorType(String connectorType) {
        return CONNECTOR_TYPE_N8N.equals(connectorType) ||
               CONNECTOR_TYPE_LANGCHAIN.equals(connectorType) ||
               CONNECTOR_TYPE_COPILOT_STUDIO.equals(connectorType) ||
               CONNECTOR_TYPE_DATABRICKS.equals(connectorType) ||
               CONNECTOR_TYPE_SNOWFLAKE.equals(connectorType) ||
               CONNECTOR_TYPE_VERTEX_AI_CUSTOM_DEPLOYED_MODEL.equals(connectorType);
    }
}
