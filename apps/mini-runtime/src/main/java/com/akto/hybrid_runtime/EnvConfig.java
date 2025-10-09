package com.akto.hybrid_runtime;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dto.deployment.DeploymentConfig;
import com.akto.dto.deployment.EnvVariable;

public final class EnvConfig {

    private static final Map<String, String> envMap = new HashMap<>();

    private EnvConfig() {}

    public static void hydrate(DeploymentConfig deploymentConfig) {
        envMap.clear();
        if (deploymentConfig == null) return;
        List<EnvVariable> vars = deploymentConfig.getEnvVars();
        if (vars == null) return;
        for (EnvVariable v : vars) {
            if (v == null || v.getKey() == null) continue;
            envMap.put(v.getKey(), v.getValue());
        }
    }

    public static String get(String key, String defaultValue) {
        String v = envMap.get(key);
        if (v != null) return v;
        String sys = System.getenv(key);
        return sys != null ? sys : defaultValue;
    }

    public static Map<String, String> snapshot() {
        return Collections.unmodifiableMap(envMap);
    }
}
