package com.akto.jobs.executors.strategy;

import java.util.Map;

/**
 * Strategy interface for AI Agent Connector operations.
 * Each connector type implements this interface with its specific behavior.
 */
public interface AIAgentConnectorStrategy {

    /**
     * Get the connector type identifier
     */
    String getConnectorType();

    /**
     * Get the binary name for this connector
     */
    String getBinaryName();

    /**
     * Validate the configuration map for this connector
     * @param config Configuration map to validate
     * @throws Exception if validation fails
     */
    void validateConfig(Map<String, String> config) throws Exception;

    /**
     * Set connector-specific environment variables
     * @param env Environment map to populate
     * @param config Configuration map with connector settings
     * @throws Exception if required config is missing
     */
    void setEnvironmentVariables(Map<String, String> env, Map<String, String> config) throws Exception;

    /**
     * Get configuration keys required by this connector
     * @return Array of required configuration keys
     */
    String[] getRequiredConfigKeys();
}
