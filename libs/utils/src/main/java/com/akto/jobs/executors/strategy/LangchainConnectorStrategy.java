package com.akto.jobs.executors.strategy;

import java.util.Map;

import static com.akto.jobs.executors.AIAgentConnectorConstants.*;

/**
 * Strategy implementation for Langchain Connector
 */
public class LangchainConnectorStrategy implements AIAgentConnectorStrategy {

    @Override
    public String getConnectorType() {
        return CONNECTOR_TYPE_LANGCHAIN;
    }

    @Override
    public String getBinaryName() {
        return BINARY_NAME_LANGCHAIN;
    }

    @Override
    public void validateConfig(Map<String, String> config) throws Exception {
        String langsmithUrl = config.get(CONFIG_LANGSMITH_BASE_URL);
        String langsmithApiKey = config.get(CONFIG_LANGSMITH_API_KEY);

        if (langsmithUrl == null || langsmithUrl.isEmpty()) {
            throw new Exception("Missing required Langchain configuration: LANGSMITH_BASE_URL");
        }

        if (langsmithApiKey == null || langsmithApiKey.isEmpty()) {
            throw new Exception("Missing required Langchain configuration: LANGSMITH_API_KEY");
        }
    }

    @Override
    public void setEnvironmentVariables(Map<String, String> env, Map<String, String> config) throws Exception {
        validateConfig(config);

        env.put(CONFIG_LANGSMITH_BASE_URL, config.get(CONFIG_LANGSMITH_BASE_URL));
        env.put(CONFIG_LANGSMITH_API_KEY, config.get(CONFIG_LANGSMITH_API_KEY));
    }

    @Override
    public String[] getRequiredConfigKeys() {
        return new String[]{CONFIG_LANGSMITH_BASE_URL, CONFIG_LANGSMITH_API_KEY};
    }
}
