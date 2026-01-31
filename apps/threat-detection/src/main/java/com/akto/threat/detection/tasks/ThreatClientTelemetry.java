package com.akto.threat.detection.tasks;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

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

            if (!isMessageForThreatDetection(command)) {
                return;
            }

            logger.infoAndAddToDb("Processing command message messageType=" + messageType);

            if (MESSAGE_TYPE_RESTART.equals(messageType)) {
                logger.infoAndAddToDb("Restarting process...");
                restartSelf(null);
                return;
            }

            if (MESSAGE_TYPE_ENV_RELOAD.equals(messageType)) {
                @SuppressWarnings("unchecked")
                Map<String, String> envVars = (Map<String, String>) command.get("env");
                if (envVars != null && !envVars.isEmpty()) {
                    for (Map.Entry<String, String> entry : envVars.entrySet()) {
                        String key = entry.getKey();
                        String value = entry.getValue();
                        String oldValue = System.getProperty(key);
                        if (oldValue == null) {
                            oldValue = System.getenv(key);
                        }
                        if (value != null && !value.equals(oldValue)) {
                            logger.infoAndAddToDb("Updating env " + key);
                            System.setProperty(key, value);
                        }
                    }
                }
                logger.infoAndAddToDb("Environment updated, restarting process...");
                restartSelf(envVars);
                return;
            }

        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error processing command message: " + message);
        }
    }

    private boolean isMessageForThreatDetection(Map<String, Object> command) {
        String moduleType = (String) command.get("moduleType");
        return MODULE_TYPE.name().equals(moduleType);
    }

    private void restartSelf(Map<String, String> envOverrides) {
        logger.infoAndAddToDb("Exiting for restart (handled by entrypoint script)");
        System.exit(0);
    }
}
