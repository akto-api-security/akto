package com.akto.config;

import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.AccountSettings;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConfigHandler {

    private static final DataActor dataActor = DataActorFactory.fetchInstance();
    private static volatile Map<String, String> overrides = new HashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    static {
        scheduler.scheduleAtFixedRate(ConfigHandler::poll, 0, 60, TimeUnit.SECONDS);
    }

    private ConfigHandler() {
    }

    // Fetches the latest overrides and caches them for readEnv to look up.
    public static Map<String, String> poll() {
        Map<String, String> fetched = new HashMap<>();
        try {
            AccountSettings settings = dataActor.fetchAccountSettings();
            if (settings != null && settings.getRuntimeEnvOverrides() != null) {
                fetched = settings.getRuntimeEnvOverrides();
            }
        } catch (Exception e) {
            System.err.println("ConfigHandler: poll failed: " + e.getMessage());
        }
        overrides = fetched;
        return fetched;
    }

    public static String readEnv(String name, String defaultValue) {
        String value = overrides.get(name);
        if (value == null) {
            value = System.getenv(name);
        }
        return value != null ? value : defaultValue;
    }
}
