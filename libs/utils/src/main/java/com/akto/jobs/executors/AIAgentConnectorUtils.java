package com.akto.jobs.executors;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.akto.jobs.executors.AIAgentConnectorConstants.*;

/**
 * Utility class for AI Agent Connector operations.
 * Provides common validation and helper methods for AI Agent Connectors.
 */
public final class AIAgentConnectorUtils {

    private AIAgentConnectorUtils() {
        // Prevent instantiation
    }

    private static final Map<String, String> CONNECTOR_TO_JOB_TYPE = new HashMap<>();

    static {
        // Connectors using default AI_AGENT_CONNECTOR job type
        CONNECTOR_TO_JOB_TYPE.put(CONNECTOR_TYPE_N8N, JOB_TYPE_AI_AGENT_CONNECTOR);
        CONNECTOR_TO_JOB_TYPE.put(CONNECTOR_TYPE_LANGCHAIN, JOB_TYPE_AI_AGENT_CONNECTOR);
        CONNECTOR_TO_JOB_TYPE.put(CONNECTOR_TYPE_COPILOT_STUDIO, JOB_TYPE_AI_AGENT_CONNECTOR);
        CONNECTOR_TO_JOB_TYPE.put(CONNECTOR_TYPE_DATABRICKS, JOB_TYPE_AI_AGENT_CONNECTOR);
        CONNECTOR_TO_JOB_TYPE.put(CONNECTOR_TYPE_SNOWFLAKE, JOB_TYPE_AI_AGENT_CONNECTOR);
        // Connectors with custom job types
        CONNECTOR_TO_JOB_TYPE.put(CONNECTOR_TYPE_VERTEX_AI_CUSTOM_DEPLOYED_MODEL, JOB_TYPE_VERTEX_AI_CUSTOM_DEPLOYED_MODEL_CONNECTOR);
    }

    private static final Set<String> VALID_CONNECTOR_TYPES =
            Collections.unmodifiableSet(new HashSet<>(CONNECTOR_TO_JOB_TYPE.keySet()));

    /**
     * Validates if the connector type is supported.
     *
     * @param connectorType The connector type to validate
     * @return true if the connector type is valid, false otherwise
     */
    public static boolean isValidConnectorType(String connectorType) {
        return connectorType != null && VALID_CONNECTOR_TYPES.contains(connectorType);
    }
    
    public static String getJobTypeForConnector(String connectorType) {
        return connectorType == null
                ? JOB_TYPE_AI_AGENT_CONNECTOR
                : CONNECTOR_TO_JOB_TYPE.getOrDefault(connectorType, JOB_TYPE_AI_AGENT_CONNECTOR);
    }
}
