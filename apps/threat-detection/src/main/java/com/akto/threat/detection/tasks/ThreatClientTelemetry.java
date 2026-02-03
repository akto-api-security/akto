package com.akto.threat.detection.tasks;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import com.akto.config.DynamicConfig;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.kafka.KafkaConfig;
import com.akto.metrics.ModuleInfoWorker;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ThreatClientTelemetry implements Runnable {

    private static final LoggerMaker logger = new LoggerMaker(ThreatClientTelemetry.class, LogDb.THREAT_DETECTION);
    private static final Gson gson = new Gson();
    private static final String CONFIG_UPDATE_TOPIC = "akto.config.updates";
    private static final String MESSAGE_TYPE_RESTART = "RESTART";
    private static final String MESSAGE_TYPE_ENV_RELOAD = "ENV_RELOAD";
    private static final ModuleInfo.ModuleType MODULE_TYPE = ModuleInfo.ModuleType.THREAT_DETECTION;

    private final Consumer<String, String> kafkaConsumer;
    private final String moduleName;

    public ThreatClientTelemetry(KafkaConfig kafkaConfig) {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", kafkaConfig.getBootstrapServers());
        properties.put("group.id", kafkaConfig.getGroupId() + ".config.updates");
        properties.put("key.deserializer", StringDeserializer.class.getName());
        properties.put("value.deserializer", StringDeserializer.class.getName());
        properties.put("enable.auto.commit", "true");
        properties.put("auto.commit.interval.ms", "1000");
        properties.put("auto.offset.reset", "latest");

        this.kafkaConsumer = new KafkaConsumer<>(properties);
        this.kafkaConsumer.subscribe(Collections.singletonList(CONFIG_UPDATE_TOPIC));
        this.moduleName = ModuleInfoWorker.getModuleName(MODULE_TYPE);

        logger.infoAndAddToDb("ThreatClientTelemetry initialized for module " + moduleName + ", topic: " + CONFIG_UPDATE_TOPIC);
    } 

    @Override
    public void run() {
        logger.infoAndAddToDb("Starting threat client telemetry config consumer");

        while (true) {
            try {
                ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofMillis(1000));

                for (ConsumerRecord<String, String> record : records) {
                    processCommandMessage(record.value());
                }

            } catch (Exception e) {
                logger.errorAndAddToDb(e, "Error in threat client telemetry config consumer");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.errorAndAddToDb(ie, "Threat client telemetry config consumer interrupted");
                    break;
                }
            }
        }
    }

    private void processCommandMessage(String message) {
        try {
            Map<String, Object> command = gson.fromJson(message, new TypeToken<Map<String, Object>>(){}.getType());

            String messageType = (String) command.get("messageType");
            if (messageType == null) {
                return;
            }

            if (!isMessageForThisInstance(command)) {
                return;
            }

            logger.infoAndAddToDb("Processing command message messageType=" + messageType);

            if (MESSAGE_TYPE_RESTART.equals(messageType)) {
                logger.infoAndAddToDb("Received RESTART message, but restarts are no longer supported with DynamicConfig");
                // restartSelf(null); // Commented out: No longer restart, use DynamicConfig instead
                return;
            }

            if (MESSAGE_TYPE_ENV_RELOAD.equals(messageType)) {
                Map<String, String> envVars = parseEnvFromCommand(command);
                if (envVars != null && !envVars.isEmpty()) {
                    // Update DynamicConfig with new environment variables
                    int changedCount = DynamicConfig.updateAll(envVars);

                    if (changedCount > 0) {
                        logger.infoAndAddToDb("Updated " + changedCount + " configuration values in DynamicConfig");

                        // Update runtime environment and system properties
                        updateRuntimeEnvironment(envVars);
                    } else {
                        logger.infoAndAddToDb("No configuration changes detected");
                    }
                } else {
                    logger.infoAndAddToDb("No environment variables to update");
                }
                return;
            }

        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error processing command message: " + message);
        }
    }

    private boolean isMessageForThisInstance(Map<String, Object> command) {
        String cmdModuleType = (String) command.get("moduleType");
        if (!MODULE_TYPE.name().equals(cmdModuleType)) {
            return false;
        }
        Object namesObj = command.get("moduleNames");
        if (namesObj == null) {
            return true;
        }
        List<String> moduleNames = gson.fromJson(gson.toJson(namesObj), new TypeToken<List<String>>(){}.getType());
        if (moduleNames == null || moduleNames.isEmpty()) {
            return true;
        }
        return moduleNames.contains(moduleName) || moduleNames.contains("ALL");
    }

    /** Parse env map from command; values may be String or Number from JSON. */
    private Map<String, String> parseEnvFromCommand(Map<String, Object> command) {
        Object envObj = command.get("env");
        if (envObj == null || !(envObj instanceof Map)) {
            return Collections.emptyMap();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = (Map<String, Object>) envObj;
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            out.put(e.getKey(), e.getValue() != null ? e.getValue().toString() : "");
        }
        return out;
    }

    /**
     * Updates runtime environment variables and system properties.
     * This allows code that reads from System.getenv() or System.getProperty() to get updated values.
     * Only updates whitelisted variables for THREAT_DETECTION module.
     */
    private void updateRuntimeEnvironment(Map<String, String> envVars) {
        try {
            // Filter to only whitelisted environment variables using ModuleInfoWorker
            Map<String, Object> envVarsAsObject = new HashMap<>(envVars);
            Map<String, Object> filteredEnvVarsObject = ModuleInfoWorker.filterWhitelistedEnvVariables(envVarsAsObject, MODULE_TYPE);

            if (filteredEnvVarsObject.isEmpty()) {
                logger.infoAndAddToDb("No whitelisted environment variables to update");
                return;
            }

            // Convert back to Map<String, String> for processing
            Map<String, String> filteredEnvVars = new HashMap<>();
            for (Map.Entry<String, Object> entry : filteredEnvVarsObject.entrySet()) {
                filteredEnvVars.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "");
            }

            // Update System properties (accessible via System.getProperty())
            for (Map.Entry<String, String> entry : filteredEnvVars.entrySet()) {
                System.setProperty(entry.getKey(), entry.getValue());
            }

            if (filteredEnvVars.containsKey("AKTO_LOG_LEVEL")) {
                LoggerMaker.setDefaultLogLevel(filteredEnvVars.get("AKTO_LOG_LEVEL"));
            }
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Failed to update runtime environment");
        }
    }
}
