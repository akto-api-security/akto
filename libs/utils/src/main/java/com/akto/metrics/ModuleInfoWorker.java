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

        envMap.putAll(System.getenv());

        return envMap;
    }

    public static Map<String, Object> filterWhitelistedEnvVariables(Map<String, Object> allEnvVariables, ModuleInfo.ModuleType moduleType) {
        Map<String, Object> filteredEnvMap = new HashMap<>();

        // Get whitelisted keys for this module type
        Map<String, String> allowedKeys = ModuleInfoConstants.ALLOWED_ENV_KEYS_BY_MODULE.get(moduleType);

        if (allowedKeys == null || allowedKeys.isEmpty()) {
            return filteredEnvMap;
        }

        // Only include whitelisted environment variables
        for (String allowedKey : allowedKeys.keySet()) {
            if (allEnvVariables.containsKey(allowedKey)) {
                filteredEnvMap.put(allowedKey, allEnvVariables.get(allowedKey));
            }
        }

        return filteredEnvMap;
    }

    public static String getModuleName(ModuleInfo.ModuleType moduleType) {
        switch (moduleType) {
            case THREAT_DETECTION:
                if (!hostname.isEmpty()) {
                    return "akto-threat:" + hostname;
                } else if (!podName.isEmpty() && !nodeName.isEmpty()) {
                    return "akto-threat:" + podName + ":" + nodeName;
                } else {
                    return "akto-threat:" + UUID.randomUUID().toString();
                }
            default:
                return "Default_" + UUID.randomUUID().toString();
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

        scheduler.scheduleWithFixedDelay(() -> {
            Map<String, Object> envVariables = collectEnvironmentVariables(_this.moduleType);
            Map<String, Object> additionalData = new HashMap<>();
            if (!envVariables.isEmpty()) {
                additionalData.put("env", envVariables);
            }
            moduleInfo.setAdditionalData(additionalData);
            
            moduleInfo.setLastHeartbeatReceived(Context.now());
            assert _this.dataActor != null;
            _this.dataActor.updateModuleInfo(moduleInfo);
            loggerMaker.debug("Sending heartbeat at :" + moduleInfo.getLastHeartbeatReceived() + " for module:" + moduleInfo.getModuleType().name());
        }, 0, 60, TimeUnit.SECONDS);
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
        String moduleName = getModuleName(moduleType);
        loggerMaker.infoAndAddToDb("Starting heartbeat update for module:" + moduleType.name() + " with name:" + moduleName);
        ModuleInfoWorker infoWorker = new ModuleInfoWorker(moduleType, version, dataActor, moduleName);
        infoWorker.scheduleHeartBeatUpdate();
    }
}
