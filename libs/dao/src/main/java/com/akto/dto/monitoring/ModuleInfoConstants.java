package com.akto.dto.monitoring;

import java.util.HashMap;
import java.util.Map;

public class ModuleInfoConstants {

    public enum ModuleCategory {
        TRAFFIC_COLLECTOR,
        THREAT_DETECTION
    }

    // Whitelist of environment variables that are safe to expose/collect
    // This is the single source of truth for allowed environment variables
    // Organized by module type
    public static final Map<ModuleCategory, Map<String, String>> ALLOWED_ENV_KEYS_BY_MODULE = new HashMap<ModuleCategory, Map<String, String>>() {{
        put(ModuleCategory.TRAFFIC_COLLECTOR, new HashMap<String, String>() {{
            put("AKTO_KAFKA_BROKER_MAL", "Kafka Broker MAL");
            put("AKTO_KAFKA_BROKER_URL", "Kafka Broker URL");
            put("AKTO_TRAFFIC_BATCH_SIZE", "Traffic Batch Size");
            put("AKTO_TRAFFIC_BATCH_TIME_SECS", "Traffic Batch Time (Seconds)");
            put("AKTO_LOG_LEVEL", "Log Level");
            put("DEBUG_URLS", "Debug URLs (url1,url2,url3)");
            put("AKTO_K8_METADATA_CAPTURE", "K8 Metadata Capture");
            put("AKTO_THREAT_ENABLED", "Threat Enabled");
            put("AKTO_IGNORE_ENVOY_PROXY_CALLS", "Ignore Envoy Proxy Calls");
            put("AKTO_IGNORE_IP_TRAFFIC", "Ignore IP Traffic");
        }});

        put(ModuleCategory.THREAT_DETECTION, new HashMap<String, String>() {{
            put("AKTO_TRAFFIC_KAFKA_BOOTSTRAP_SERVER", "Traffic Kafka Bootstrap Server");
            put("AKTO_INTERNAL_KAFKA_BOOTSTRAP_SERVER", "Internal Kafka Bootstrap Server");
            put("AKTO_THREAT_DETECTION_LOCAL_REDIS_URI", "Local Redis URI");
            put("AGGREGATION_RULES_ENABLED", "Aggregation Rules Enabled");
            put("API_DISTRIBUTION_ENABLED", "API Distribution Enabled");
            put("AKTO_THREAT_PROTECTION_BACKEND_URL", "Threat Protection Backend URL");
            put("AKTO_MONGO_CONN", "MongoDB Connection String");
            put("RUNTIME_MODE", "Runtime Mode");
            put("AKTO_THREAT_PROTECTION_BACKEND_TOKEN", "Threat Protection Backend Token");
            put("DATABASE_ABSTRACTOR_SERVICE_TOKEN", "Database Abstractor Token");
            put("THREAT_DETECTION_NAME", "Threat Detection Name");
        }});
    }};

    private ModuleInfoConstants() {
        // Private constructor to prevent instantiation
    }
}
