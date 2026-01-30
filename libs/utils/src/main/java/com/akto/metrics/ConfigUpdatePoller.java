package com.akto.metrics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.kafka.Kafka;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.google.gson.Gson;

public class ConfigUpdatePoller {
    private static final LoggerMaker loggerMaker = new LoggerMaker(ConfigUpdatePoller.class, LogDb.RUNTIME);
    private static final Gson gson = new Gson();
    private static final long POLL_INTERVAL_SECONDS = 20;
    private static final Random random = new Random();
    
    private final DataActor dataActor;
    private final String moduleName;
    private final Kafka kafkaProducer;
    private final String configUpdateTopicName;
    private final ScheduledExecutorService scheduler;
    private final ModuleInfo.ModuleType moduleType;

    public ConfigUpdatePoller(String moduleName, Kafka kafkaProducer, String configUpdateTopicName, ModuleInfo.ModuleType moduleType) {
        this.dataActor = DataActorFactory.fetchInstance();
        this.moduleName = moduleName;
        this.kafkaProducer = kafkaProducer;
        this.configUpdateTopicName = configUpdateTopicName;
        this.moduleType = moduleType;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        loggerMaker.infoAndAddToDb("ConfigUpdatePoller: Initialized with kafka producer for topic: " + configUpdateTopicName);
    }

    public void start() {
        loggerMaker.infoAndAddToDb("Starting config update poller for module: " + moduleName + " type: " + moduleType);

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                long jitter = random.nextInt(6);
                if (jitter > 0) {
                    Thread.sleep(jitter * 1000);
                }

                pollAndPublishConfigUpdates();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                loggerMaker.errorAndAddToDb(e, "Config update poller interrupted");
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error in config update poller");
            }
        }, 0, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void pollAndPublishConfigUpdates() {
        try {
            // For THREAT_DETECTION pass null for miniRuntimeName, for TRAFFIC_COLLECTOR pass actual name
            String miniRuntimeName = (moduleType == ModuleInfo.ModuleType.THREAT_DETECTION) ? null : moduleName;
            
            List<ModuleInfo> moduleInfoList = dataActor.fetchAndUpdateModuleForReboot(
                    moduleType,
                    miniRuntimeName
            );

            if (moduleInfoList == null || moduleInfoList.isEmpty()) {
                return;
            }

            loggerMaker.infoAndAddToDb("Found " + moduleInfoList.size() + " module(s) with config updates for module: " + moduleName);

            // Collect all module names (for mini-runtime, these are daemon names)
            List<String> moduleNames = new ArrayList<>();
            for (ModuleInfo moduleInfo : moduleInfoList) {
                moduleNames.add(moduleInfo.getName());
            }

            // Extract envVars from additionalData.env of the first module
            Map<String, String> envVars = new HashMap<>();
            if (!moduleInfoList.isEmpty() && moduleInfoList.get(0).getAdditionalData() != null) {
                Map<String, Object> additionalData = moduleInfoList.get(0).getAdditionalData();
                Object envObj = additionalData.get("env");
                if (envObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> env = (Map<String, String>) envObj;
                    envVars = env;
                }
            }

            publishConfigUpdate(moduleNames, envVars);

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error polling and publishing config updates");
        }
    }

    private void publishConfigUpdate(List<String> moduleNames, Map<String, String> envVars) {
        try {
            if (kafkaProducer == null || !kafkaProducer.producerReady) {
                loggerMaker.infoAndAddToDb("Kafka producer not ready, will retry in next poll cycle");
                return;
            }

            Map<String, Object> configUpdate = new HashMap<>();
            configUpdate.put("messageType", "ENV_RELOAD");
            configUpdate.put("moduleType", moduleType.name());
            configUpdate.put("moduleNames", moduleNames);
            configUpdate.put("env", envVars);
            configUpdate.put("timestamp", System.currentTimeMillis());

            String message = gson.toJson(configUpdate);
            kafkaProducer.send(message, configUpdateTopicName);

            loggerMaker.infoAndAddToDb("Published config update for " + moduleNames.size() + " module(s) to topic: " + configUpdateTopicName);

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error publishing config update");
        }
    }
}
