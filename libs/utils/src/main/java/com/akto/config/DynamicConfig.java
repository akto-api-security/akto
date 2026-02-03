package com.akto.config;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicConfig {
    private static final LoggerMaker logger = new LoggerMaker(DynamicConfig.class, LogDb.RUNTIME);
    private static final ConcurrentHashMap<String, String> config = new ConcurrentHashMap<>();

    static {
        config.putAll(System.getenv());
    }

    public static String get(String key) {
        return config.get(key);
    }

    public static String get(String key, String defaultValue) {
        return config.getOrDefault(key, defaultValue);
    }

    public static void update(String key, String value) {
        String oldValue = config.put(key, value);
        if (oldValue == null) {
            logger.info("Added new config: {} = {}", key, value);
        } else if (!oldValue.equals(value)) {
            logger.info("Updated config: {} = {} (was: {})", key, value, oldValue);
        }
    }

    public static int updateAll(Map<String, String> updates) {
        int changedCount = 0;
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            String key = entry.getKey();
            String newValue = entry.getValue();
            String oldValue = config.get(key);

            if (oldValue == null || !oldValue.equals(newValue)) {
                config.put(key, newValue);
                changedCount++;
                logger.debug("Config changed: {} = {} (was: {})", key, newValue, oldValue);
            }
        }

        if (changedCount > 0) {
            logger.info("Updated {} configuration values", changedCount);
        }

        return changedCount;
    }

    public static boolean hasChanges(Map<String, String> newConfig) {
        for (Map.Entry<String, String> entry : newConfig.entrySet()) {
            String key = entry.getKey();
            String newValue = entry.getValue();
            String currentValue = config.get(key);

            if (currentValue == null || !currentValue.equals(newValue)) {
                return true;
            }
        }
        return false;
    }

    public static Map<String, String> getAll() {
        return new ConcurrentHashMap<>(config);
    }

    public static int size() {
        return config.size();
    }
}
