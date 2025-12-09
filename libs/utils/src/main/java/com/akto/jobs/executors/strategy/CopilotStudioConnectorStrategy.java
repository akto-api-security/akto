package com.akto.jobs.executors.strategy;

import java.util.Map;

import static com.akto.jobs.executors.AIAgentConnectorConstants.*;

/**
 * Strategy implementation for Copilot Studio Connector
 */
public class CopilotStudioConnectorStrategy implements AIAgentConnectorStrategy {

    @Override
    public String getConnectorType() {
        return CONNECTOR_TYPE_COPILOT_STUDIO;
    }

    @Override
    public String getBinaryName() {
        return BINARY_NAME_COPILOT_STUDIO;
    }

    @Override
    public void validateConfig(Map<String, String> config) throws Exception {
        String appInsightsAppId = config.get(CONFIG_APPINSIGHTS_APP_ID);
        String appInsightsApiKey = config.get(CONFIG_APPINSIGHTS_API_KEY);

        if (appInsightsAppId == null || appInsightsAppId.isEmpty()) {
            throw new Exception("Missing required Copilot Studio configuration: APPINSIGHTS_APP_ID");
        }

        if (appInsightsApiKey == null || appInsightsApiKey.isEmpty()) {
            throw new Exception("Missing required Copilot Studio configuration: APPINSIGHTS_API_KEY");
        }
    }

    @Override
    public void setEnvironmentVariables(Map<String, String> env, Map<String, String> config) throws Exception {
        validateConfig(config);

        env.put(CONFIG_APPINSIGHTS_APP_ID, config.get(CONFIG_APPINSIGHTS_APP_ID));
        env.put(CONFIG_APPINSIGHTS_API_KEY, config.get(CONFIG_APPINSIGHTS_API_KEY));
    }

    @Override
    public String[] getRequiredConfigKeys() {
        return new String[]{CONFIG_APPINSIGHTS_APP_ID, CONFIG_APPINSIGHTS_API_KEY};
    }
}
