package com.akto.metrics;

import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.monitoring.ModuleInfoConstants;
import com.akto.log.LoggerMaker;
import com.akto.util.VersionUtil;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
    private final String moduleName;
    private static final String podName = System.getenv().getOrDefault("POD_NAME", "");
    private static final String nodeName = System.getenv().getOrDefault("NODE_NAME", "");
    private static final String hostname = System.getenv().getOrDefault("HOSTNAME", "");
    private static final String threatDetectionName = System.getenv().getOrDefault("THREAT_DETECTION_NAME", "");

    private ModuleInfoWorker(ModuleInfo.ModuleType moduleType, String version, DataActor dataActor, String moduleName) {
        this.moduleType = moduleType;
        this.version = version;
        this.dataActor = dataActor;
        this.moduleName = moduleName;
    }

    private ModuleInfoWorker() {
        this.moduleType = null;
        this.version = null;
        this.dataActor = null;
        this.moduleName = null;
    }

    private Map<String, Object> collectEnvironmentVariables(ModuleInfo.ModuleType moduleType) {
        Map<String, Object> envMap = new HashMap<>();

        // Collect only whitelisted environment variables from ModuleInfoConstants
        // Single source of truth for allowed environment variables
        Map<String, String> systemEnv = System.getenv();

        // Determine which module category to use based on moduleType
        ModuleInfoConstants.ModuleCategory category = getModuleCategoryForType(moduleType);
        Map<String, String> allowedKeys = ModuleInfoConstants.ALLOWED_ENV_KEYS_BY_MODULE.get(category);

        if (allowedKeys != null) {
            for (String key : allowedKeys.keySet()) {
                String value = systemEnv.get(key);
                if (value != null) {
                    envMap.put(key, value);
                }
            }
        }

        return envMap;
    }

    private ModuleInfoConstants.ModuleCategory getModuleCategoryForType(ModuleInfo.ModuleType moduleType) {
        switch (moduleType) {
            case TRAFFIC_COLLECTOR:
                return ModuleInfoConstants.ModuleCategory.TRAFFIC_COLLECTOR;
            case THREAT_DETECTION:
                return ModuleInfoConstants.ModuleCategory.THREAT_DETECTION;
            default:
                throw new IllegalArgumentException("Unsupported module type: " + moduleType);
        }
    }

    public static String getModuleName() {
        if (!threatDetectionName.isEmpty()) {
            return threatDetectionName;
        } else if (!podName.isEmpty() && !nodeName.isEmpty()) {
            return "akto-threat:" + podName + ":" + nodeName;
        } else {
            return UUID.randomUUID().toString();
        }
    }

    private void scheduleHeartBeatUpdate () {
        ModuleInfoWorker _this = this;
        ModuleInfo moduleInfo = new ModuleInfo();
        moduleInfo.setModuleType(this.moduleType);
        moduleInfo.setCurrentVersion(this.version);
        moduleInfo.setStartedTs(this.startedTs);
        moduleInfo.setId(moduleInfo.getId());//Setting new uuid for id
        moduleInfo.setName(this.moduleName);

        //Collect environment variables once at initialization
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
        String moduleName = getModuleName();
        loggerMaker.infoAndAddToDb("Starting heartbeat update for module:" + moduleType.name() + " with name:" + moduleName);
        ModuleInfoWorker infoWorker = new ModuleInfoWorker(moduleType, version, dataActor, moduleName);
        infoWorker.scheduleHeartBeatUpdate();
    }
}
