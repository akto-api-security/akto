package com.akto.threat.detection.tasks;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dto.monitoring.ModuleInfo;
import com.akto.metrics.ModuleInfoWorker;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;

public class ConfigPoller implements Runnable {

    private static final LoggerMaker logger = new LoggerMaker(ConfigPoller.class, LogDb.THREAT_DETECTION);
    private static final ModuleInfo.ModuleType MODULE_TYPE = ModuleInfo.ModuleType.THREAT_DETECTION;
    private static final long POLL_INTERVAL_MS = 20000; // 20 seconds
    private static final String ENV_FILE_PATH = "/app/.env";

    private final DataActor dataActor;

    public ConfigPoller() {
        this.dataActor = DataActorFactory.fetchInstance();
        logger.infoAndAddToDb("ConfigPoller initialized");
    }

    @Override
    public void run() {
        logger.infoAndAddToDb("Starting config poller");

        while (true) {
            try {
                pollAndProcessConfig();
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                logger.errorAndAddToDb(ie, "Config poller interrupted");
                break;
            } catch (Exception e) {
                logger.errorAndAddToDb(e, "Error in config poller");
            }
        }
    }

    private void pollAndProcessConfig() {
        try {
            List<ModuleInfo> moduleInfoList = dataActor.fetchAndUpdateModuleForReboot(MODULE_TYPE, null);

            if (moduleInfoList == null || moduleInfoList.isEmpty()) {
                return;
            }

            // Reboot flag was set - either plain restart or env update + restart
            logger.infoAndAddToDb("Reboot flag detected, processing restart request");

            Map<String, String> envVars = extractEnvVars(moduleInfoList);

            if (!envVars.isEmpty()) {
                // Write env vars to file (even if unchanged, to ensure .env file exists)
                writeEnvFile(envVars);

                if (hasEnvironmentChanged(envVars)) {
                    logger.infoAndAddToDb("Configuration changes detected, restarting with new env vars");
                } else {
                    logger.infoAndAddToDb("No configuration changes, performing plain restart");
                }
            } else {
                logger.infoAndAddToDb("No env vars found, performing plain restart");
            }

            restartSelf();
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error polling and processing config");
        }
    }

    private Map<String, String> extractEnvVars(List<ModuleInfo> moduleInfoList) {
        Map<String, String> filteredEnvVars = new HashMap<>();

        if (moduleInfoList.isEmpty() || moduleInfoList.get(0).getAdditionalData() == null) {
            return filteredEnvVars;
        }

        Map<String, Object> additionalData = moduleInfoList.get(0).getAdditionalData();
        Object envObj = additionalData.get("env");

        if (!(envObj instanceof Map)) {
            return filteredEnvVars;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> envVars = (Map<String, Object>) envObj;

        // Filter whitelisted environment variables
        Map<String, Object> filteredEnvVarsObject = ModuleInfoWorker.filterWhitelistedEnvVariables(envVars, MODULE_TYPE);

        // Convert to String values
        for (Map.Entry<String, Object> entry : filteredEnvVarsObject.entrySet()) {
            filteredEnvVars.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "");
        }

        return filteredEnvVars;
    }

    private void writeEnvFile(Map<String, String> envVars) throws IOException {
        try (FileWriter writer = new FileWriter(ENV_FILE_PATH)) {
            for (Map.Entry<String, String> entry : envVars.entrySet()) {
                String value = entry.getValue().replace("\"", "\\\"");
                writer.write(String.format("export %s=\"%s\"\n", entry.getKey(), value));
            }
        }
        logger.infoAndAddToDb("Updated .env file with " + envVars.size() + " variables");
    }

    private boolean hasEnvironmentChanged(Map<String, String> envVars) {
        for (Map.Entry<String, String> entry : envVars.entrySet()) {
            String currentValue = System.getenv(entry.getKey());
            String newValue = entry.getValue();
            if (currentValue == null || !currentValue.equals(newValue)) {
                return true;
            }
        }
        return false;
    }

    private void restartSelf() {
        logger.infoAndAddToDb("Restarting application to apply new configuration");
        System.exit(0);
    }
}
