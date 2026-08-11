package com.akto.dto.monitoring;

import java.util.HashMap;
import java.util.Map;

public class ModuleInfoConstants {

    public static final Map<ModuleInfo.ModuleType, Map<String, String>> ALLOWED_ENV_KEYS_BY_MODULE = new HashMap<ModuleInfo.ModuleType, Map<String, String>>() {{
        put(ModuleInfo.ModuleType.MINI_RUNTIME, new HashMap<String, String>() {{
            put("DEBUG_URLS", "Debug URLs (url1,url2,url3)");
            put("DEBUG_HOSTS", "Debug Hosts (host1,host2,host3)");
        }});
    }};

    private ModuleInfoConstants() {
    }
}
