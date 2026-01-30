package com.akto.threat.detection.tasks;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import com.akto.kafka.KafkaConfig;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ConfigUpdateConsumerTask implements Runnable {

    private static final LoggerMaker logger = new LoggerMaker(ConfigUpdateConsumerTask.class, LogDb.THREAT_DETECTION);
    private static final Gson gson = new Gson();
    private static final String CONFIG_UPDATE_TOPIC = "akto.config.updates";

    private final Consumer<String, String> kafkaConsumer;

    public ConfigUpdateConsumerTask(KafkaConfig kafkaConfig) {
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
        
        logger.infoAndAddToDb("ConfigUpdateConsumerTask initialized listening on topic: " + CONFIG_UPDATE_TOPIC);
    }

    @Override
    public void run() {
        logger.infoAndAddToDb("Starting config update consumer task");

        while (true) {
            try {
                ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofMillis(1000));
                
                if (!records.isEmpty()) {
                    logger.infoAndAddToDb("[DEBUG] Polled " + records.count() + " Kafka records from topic: " + CONFIG_UPDATE_TOPIC);
                }
                
                for (ConsumerRecord<String, String> record : records) {
                    logger.infoAndAddToDb("[DEBUG] Processing Kafka message: " + record.value());
                    processConfigUpdate(record.value());
                }
                
            } catch (Exception e) {
                logger.errorAndAddToDb(e, "Error in config update consumer task");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.errorAndAddToDb(ie, "Config update consumer task interrupted");
                    break;
                }
            }
        }
    }

    private void processConfigUpdate(String message) {
        try {
            logger.infoAndAddToDb("[DEBUG] Parsing config update message");
            Map<String, Object> configUpdate = gson.fromJson(message, new TypeToken<Map<String, Object>>(){}.getType());
            
            String messageType = (String) configUpdate.get("messageType");
            logger.infoAndAddToDb("[DEBUG] Message type: " + messageType);
            if (!"ENV_RELOAD".equals(messageType)) {
                logger.infoAndAddToDb("[DEBUG] Ignoring message - not ENV_RELOAD type");
                return;
            }

            String moduleType = (String) configUpdate.get("moduleType");
            logger.infoAndAddToDb("[DEBUG] Module type: " + moduleType);
            
            // Check if this message is for THREAT_DETECTION modules
            if (!"THREAT_DETECTION".equals(moduleType)) {
                logger.infoAndAddToDb("[DEBUG] Ignoring message - not for THREAT_DETECTION (got: " + moduleType + ")");
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, String> envVars = (Map<String, String>) configUpdate.get("env");
            
            if (envVars == null || envVars.isEmpty()) {
                logger.infoAndAddToDb("Received config update with no env vars");
                return;
            }

            logger.infoAndAddToDb("Received config update for THREAT_DETECTION with " + envVars.size() + " env vars");
                        logger.infoAndAddToDb("[DEBUG] Config update matched for THREAT_DETECTION - applying env var updates");            // Apply environment variable updates
            applyEnvVarUpdates(envVars);
            
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error processing config update message: " + message);
        }
    }

    private void applyEnvVarUpdates(Map<String, String> envVars) {
        logger.infoAndAddToDb("[DEBUG] Applying " + envVars.size() + " env var updates");
        
        int updated = 0;
        for (Map.Entry<String, String> entry : envVars.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            
            logger.infoAndAddToDb("[DEBUG] Setting System property: " + key + " = " + value);
            
            // Update system property so it's accessible via System.getProperty()
            System.setProperty(key, value);
            updated++;
            
            logger.infoAndAddToDb("Updated env var: " + key + " = " + value);
        }
        
        logger.infoAndAddToDb("Successfully applied " + updated + " env var updates for THREAT_DETECTION");
    }
}
