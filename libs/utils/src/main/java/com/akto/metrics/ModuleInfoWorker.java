package com.akto.metrics;

import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.log.LoggerMaker;
import com.akto.util.VersionUtil;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ModuleInfoWorker {
    private static LoggerMaker loggerMaker = new LoggerMaker(ModuleInfoWorker.class, LoggerMaker.LogDb.RUNTIME);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ModuleInfo.ModuleType moduleType;
    private final int startedTs = Context.now();
    private final String version;
    private final DataActor dataActor;
    private ModuleInfoWorker(ModuleInfo.ModuleType moduleType, String version, DataActor dataActor) {
        this.moduleType = moduleType;
        this.version = version;
        this.dataActor = dataActor;
    }

    private ModuleInfoWorker() {
        this.moduleType = null;
        this.version = null;
        this.dataActor = null;
    }

    private Map<String, Object> collectEnvironmentVariables(ModuleInfo.ModuleType moduleType) {
        Map<String, Object> envMap = new HashMap<>();
        
        // Collect relevant environment variables based on module type
        // Dashboard will filter to only whitelisted keys via ModuleInfoAction
        if (moduleType == ModuleInfo.ModuleType.THREAT_DETECTION) {
            // Collect all AKTO_* and relevant threat-detection env variables
            Map<String, String> systemEnv = System.getenv();
            for (Map.Entry<String, String> entry : systemEnv.entrySet()) {
                String key = entry.getKey();
                // Include AKTO_* prefixed vars and specific threat-detection config vars
                if (key.startsWith("AKTO_") || 
                    key.equals("AGGREGATION_RULES_ENABLED") || 
                    key.equals("API_DISTRIBUTION_ENABLED")) {
                    envMap.put(key, entry.getValue());
                }
            }
        }
        // Add more module types here as needed
        
        return envMap;
    }

    private void scheduleHeartBeatUpdate () {
        ModuleInfoWorker _this = this;
        ModuleInfo moduleInfo = new ModuleInfo();
        moduleInfo.setModuleType(this.moduleType);
        moduleInfo.setCurrentVersion(this.version);
        moduleInfo.setStartedTs(this.startedTs);
        moduleInfo.setId(moduleInfo.getId());//Setting new uuid for id
        
        // Collect environment variables once at initialization
        Map<String, Object> envVariables = collectEnvironmentVariables(this.moduleType);
        Map<String, Object> additionalData = new HashMap<>();
        if (!envVariables.isEmpty()) {
            additionalData.put("env", envVariables);
        }
        moduleInfo.setAdditionalData(additionalData);

        scheduler.scheduleWithFixedDelay(() -> {
            moduleInfo.setLastHeartbeatReceived(Context.now());
            assert _this.dataActor != null;
            _this.dataActor.updateModuleInfo(moduleInfo);
            loggerMaker.info("Sending heartbeat at :" + moduleInfo.getLastHeartbeatReceived() + " for module:" + moduleInfo.getModuleType().name());
        }, 0, 30, TimeUnit.SECONDS);
    }

    public static void init(ModuleInfo.ModuleType moduleType, DataActor dataActor) {
        String version;
        try (InputStream in = ModuleInfoWorker.class.getResourceAsStream("/version.txt")) {
            if (in != null) {
                version = VersionUtil.getVersion(in);
            } else {
                throw new Exception("Input stream null");
            }
        } catch (Exception e) {
            loggerMaker.error("Error getting local version, skipping heartbeat check");
            return;
        }
        loggerMaker.infoAndAddToDb("Starting heartbeat update for module:" + moduleType.name());
        ModuleInfoWorker infoWorker = new ModuleInfoWorker(moduleType, version, dataActor);
        infoWorker.scheduleHeartBeatUpdate();
    }
}
