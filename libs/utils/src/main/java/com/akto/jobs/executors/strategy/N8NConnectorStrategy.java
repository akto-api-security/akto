package com.akto.jobs.executors.strategy;

import java.util.Map;

import static com.akto.jobs.executors.AIAgentConnectorConstants.*;

/**
 * Strategy implementation for N8N Connector
 */
public class N8NConnectorStrategy implements AIAgentConnectorStrategy {

    @Override
    public String getConnectorType() {
        return CONNECTOR_TYPE_N8N;
    }

    @Override
    public String getBinaryName() {
        return BINARY_NAME_N8N;
    }

    @Override
    public void validateConfig(Map<String, String> config) throws Exception {
        String n8nUrl = config.get(CONFIG_N8N_BASE_URL);
        String n8nApiKey = config.get(CONFIG_N8N_API_KEY);

        if (n8nUrl == null || n8nUrl.isEmpty()) {
            throw new Exception("Missing required N8N configuration: N8N_BASE_URL");
        }

        if (n8nApiKey == null || n8nApiKey.isEmpty()) {
            throw new Exception("Missing required N8N configuration: N8N_API_KEY");
        }
    }

    @Override
    public void setEnvironmentVariables(Map<String, String> env, Map<String, String> config) throws Exception {
        validateConfig(config);

        env.put(CONFIG_N8N_BASE_URL, config.get(CONFIG_N8N_BASE_URL));
        env.put(CONFIG_N8N_API_KEY, config.get(CONFIG_N8N_API_KEY));
    }

    @Override
    public String[] getRequiredConfigKeys() {
        return new String[]{CONFIG_N8N_BASE_URL, CONFIG_N8N_API_KEY};
    }
}
