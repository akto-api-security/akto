package com.akto.dto.monitoring;

import java.util.HashMap;
import java.util.Map;

public class ModuleInfoConstants {

    public static final Map<ModuleInfo.ModuleType, Map<String, String>> ALLOWED_ENV_KEYS_BY_MODULE = new HashMap<ModuleInfo.ModuleType, Map<String, String>>() {{
        put(ModuleInfo.ModuleType.MINI_RUNTIME, new HashMap<String, String>() {{
            put("AKTO_LOG_LEVEL", "Log Level");
            put("DEBUG_URLS", "Debug URLs (url1,url2,url3)");
            put("DEBUG_HOSTS", "Debug Hosts (host1,host2,host3)");
            put("MINI_RUNTIME_NAME", "Mini Runtime Name");
            put("AKTO_CONFIG_NAME", "Config Name");
            put("AKTO_KAFKA_BROKER_URL", "Kafka Broker URL");
            put("AKTO_KAFKA_GROUP_ID_CONFIG", "Kafka Group ID Config");
            put("AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG", "Kafka Max Poll Records Config");
        }});
    }};

    private ModuleInfoConstants() {
    }
}
