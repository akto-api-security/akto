package com.akto.utils.cloud.serverless;

import java.util.Map;

public class UpdateFunctionRequest {
    private final Map<String, String> environmentVariables;

    public UpdateFunctionRequest(Map<String, String> envVarsMap) {
        this.environmentVariables = envVarsMap;
    }

    public Map<String, String> getEnvironmentVariables() {
        return this.environmentVariables;
    }
}
