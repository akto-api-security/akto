package com.akto.metrics;

import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.monitoring.ModuleInfo.ModuleType;
import com.akto.kafka.Kafka;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConfigUpdatePoller {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ConfigUpdatePoller.class, LogDb.RUNTIME);
    private static final Gson gson = new Gson();
    private static final long POLL_INTERVAL_SECONDS = 20;

    private final String miniRuntimeName;
    private final Kafka kafkaProducer;
    private final String configUpdateTopicName;
    private final ScheduledExecutorService scheduler;

    public ConfigUpdatePoller(String miniRuntimeName, Kafka kafkaProducer, String configUpdateTopicName) {
        this.miniRuntimeName = miniRuntimeName;
        this.kafkaProducer = kafkaProducer;
        this.configUpdateTopicName = configUpdateTopicName;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        loggerMaker.infoAndAddToDb("ConfigUpdatePoller: Initialized with kafka producer for topic: " + configUpdateTopicName);
    }

    public void start() {
        loggerMaker.infoAndAddToDb("Starting config update poller for mini-runtime: " + miniRuntimeName);

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                Context.accountId.set(Context.getActualAccountId());
                pollAndPublishConfigUpdates();
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error in config update poller");
            }
        }, 0, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void pollAndPublishConfigUpdates() {
        try {
            DataActor dataActor = DataActorFactory.fetchInstance();
            List<ModuleInfo> moduleInfoList = dataActor.fetchAndUpdateModuleForReboot(
                    ModuleType.TRAFFIC_COLLECTOR,
                    miniRuntimeName
            );

            if (moduleInfoList == null || moduleInfoList.isEmpty()) {
                return;
            }

            loggerMaker.infoAndAddToDb("Found " + moduleInfoList.size() + " module(s) with config updates for mini-runtime: " + miniRuntimeName);

            // Collect all daemon names
            List<String> daemonNames = new ArrayList<>();
            for (ModuleInfo moduleInfo : moduleInfoList) {
                daemonNames.add(moduleInfo.getName());
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

            publishConfigUpdate(daemonNames, envVars);

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error polling and publishing config updates");
        }
    }

    private void publishConfigUpdate(List<String> daemonNames, Map<String, String> envVars) {
        try {
            if (kafkaProducer == null || !kafkaProducer.producerReady) {
                loggerMaker.infoAndAddToDb("Kafka producer not ready, will retry in next poll cycle");
                return;
            }

            Map<String, Object> configUpdate = new HashMap<>();
            configUpdate.put("messageType", "ENV_RELOAD");
            configUpdate.put("daemonNames", daemonNames);
            configUpdate.put("env", envVars);
            configUpdate.put("timestamp", System.currentTimeMillis());

            String message = gson.toJson(configUpdate);
            kafkaProducer.send(message, configUpdateTopicName);

            loggerMaker.infoAndAddToDb("Published config update for " + daemonNames.size() + " daemon(s) to topic: " + configUpdateTopicName);

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error publishing config update");
        }
    }
}
