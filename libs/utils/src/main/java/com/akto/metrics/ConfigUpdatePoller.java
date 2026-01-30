package com.akto.metrics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

public class ConfigUpdatePoller {
    private static final LoggerMaker loggerMaker = new LoggerMaker(ConfigUpdatePoller.class, LogDb.RUNTIME);
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final Random random = new Random();
    
    private final ModuleInfo.ModuleType moduleType;
    private final String moduleName;
    private Map<String, String> envVars = new HashMap<>();

    private ConfigUpdatePoller(ModuleInfo.ModuleType moduleType, String moduleName) {
        this.moduleType = moduleType;
        this.moduleName = moduleName;
    }

    private void startPolling() {
        // Add jitter to avoid thundering herd
        int jitterSeconds = random.nextInt(10);
        
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                checkForConfigUpdates();
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error checking for config updates: " + e.getMessage(), LogDb.RUNTIME);
            }
        }, jitterSeconds, 20, TimeUnit.SECONDS);
        
        loggerMaker.infoAndAddToDb("Started config update poller for module: " + moduleType.name() + 
                                   " with name: " + moduleName + " (initial delay: " + jitterSeconds + "s)");
    }

    private void checkForConfigUpdates() {
        DataActor dataActor = DataActorFactory.fetchInstance();
        if (dataActor == null) {
            loggerMaker.errorAndAddToDb("DataActor instance is null", LogDb.RUNTIME);
            return;
        }

        try {
            // Pass null for THREAT_DETECTION, actual moduleName for mini-runtime
            String miniRuntimeName = (moduleType == ModuleInfo.ModuleType.THREAT_DETECTION) ? null : moduleName;
            List<ModuleInfo> moduleInfos = dataActor.fetchAndUpdateModuleForReboot(moduleType, miniRuntimeName);
            if (moduleInfos == null || moduleInfos.isEmpty()) {
                return;
            }

            // Process the first matching module (should typically be only one)
            ModuleInfo moduleInfo = moduleInfos.get(0);
            
            if (!moduleInfo.isReboot()) {
                return;
            }

            loggerMaker.infoAndAddToDb("Reboot flag detected for module: " + moduleName);

            // Extract updated environment variables from additionalData
            if (moduleInfo.getAdditionalData() != null && moduleInfo.getAdditionalData().containsKey("env")) {
                Object envObj = moduleInfo.getAdditionalData().get("env");
                if (envObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> newEnvVars = (Map<String, String>) envObj;
                    envVars = newEnvVars;
                    
                    loggerMaker.infoAndAddToDb("Received " + envVars.size() + " environment variable updates for module: " + moduleName);
                    
                    // Apply environment variables
                    for (Map.Entry<String, String> entry : envVars.entrySet()) {
                        String key = entry.getKey();
                        String value = entry.getValue();
                        System.setProperty(key, value);
                        loggerMaker.infoAndAddToDb("Applied env var: " + key + " = " + value);
                    }
                }
            }

            // Trigger shutdown to allow orchestrator (K8s/Docker) to restart
            loggerMaker.infoAndAddToDb("Initiating shutdown for module: " + moduleName);
            System.exit(0);

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in checkForConfigUpdates: " + e.getMessage(), LogDb.RUNTIME);
        }
    }

    public static void init(ModuleInfo.ModuleType moduleType, String moduleName) {
        try {
            loggerMaker.infoAndAddToDb("Initializing ConfigUpdatePoller for module: " + moduleType.name() + 
                                       " with name: " + moduleName);
            ConfigUpdatePoller poller = new ConfigUpdatePoller(moduleType, moduleName);
            poller.startPolling();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Failed to initialize ConfigUpdatePoller: " + e.getMessage(), LogDb.RUNTIME);
        }
    }
}
