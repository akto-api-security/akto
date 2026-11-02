package com.akto.dto.monitoring;

import java.util.HashMap;
import java.util.Map;

public class ModuleInfoConstants {

    public static final Map<ModuleInfo.ModuleType, Map<String, String>> ALLOWED_ENV_KEYS_BY_MODULE = new HashMap<ModuleInfo.ModuleType, Map<String, String>>() {{
        put(ModuleInfo.ModuleType.MINI_RUNTIME, new HashMap<String, String>() {{
            put("DEBUG_URLS", "Debug URLs (url1,url2,url3)");
            put("DEBUG_HOSTS", "Debug Hosts (host1,host2,host3)");
        }});
        put(ModuleInfo.ModuleType.MINI_TESTING, new HashMap<String, String>() {{
            put("DATABASE_ABSTRACTOR_SERVICE_TOKEN", "Database Abstractor Service Token");
            put("AKTO_LOG_LEVEL", "Akto Log Level");
            put("DATABASE_ABSTRACTOR_SERVICE_URL", "Database Abstractor Service URL");
            put("NEW_TESTING_ENABLED", "New Testing Enabled");
            put("KAFKA_BROKER_URL", "Kafka Broker URL");
            put("KAFKA_AUTH_ENABLED", "Kafka Auth Enabled");
            put("RUNTIME_MODE", "Runtime Mode");
            put("LINGER_MS_KAFKA", "Kafka Linger MS");
            put("SEND_LOGS_FOR_TESTING", "Send Logs For Testing");
            put("AGENT_BASE_URL", "Agent Base URL");
            put("AUTOMATED_AGENT_BASE_URL", "Automated Agent Base URL");
        }});
    }};

    private ModuleInfoConstants() {
    }
}
