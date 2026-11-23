package com.akto.threat.detection.tasks;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.RecordsToDelete;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;

import com.akto.dto.monitoring.ModuleInfo;
import com.akto.kafka.KafkaConfig;
import com.akto.metrics.ModuleInfoWorker;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.threat.detection.constants.KafkaTopic;

public class ConfigPoller implements Runnable {

    private static final LoggerMaker logger = new LoggerMaker(ConfigPoller.class, LogDb.THREAT_DETECTION);
    private static final ModuleInfo.ModuleType MODULE_TYPE = ModuleInfo.ModuleType.THREAT_DETECTION;
    private static final long POLL_INTERVAL_MS = 20000; // 20 seconds
    private static final String ENV_FILE_PATH = "/app/.env";
    private static final Random RANDOM = new Random();
    private static final int JITTER_MIN_SECONDS = 1;
    private static final int JITTER_MAX_SECONDS = 5;
    private static final long KAFKA_ADMIN_TIMEOUT_SECONDS = 30;

    private final DataActor dataActor;
    private final String internalKafkaBootstrapServers;

    public ConfigPoller(String internalKafkaBootstrapServers) {
        this.dataActor = DataActorFactory.fetchInstance();
        this.internalKafkaBootstrapServers = internalKafkaBootstrapServers;
        logger.infoAndAddToDb("ConfigPoller initialized");
    }

    @Override
    public void run() {
        logger.infoAndAddToDb("Starting config poller");

        while (true) {
            try {
                pollAndProcessConfig();
                long jitterMs = (RANDOM.nextInt(JITTER_MAX_SECONDS - JITTER_MIN_SECONDS + 1) + JITTER_MIN_SECONDS) * 1000L;
                Thread.sleep(POLL_INTERVAL_MS + jitterMs);
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

            logger.infoAndAddToDb("Reboot flag detected, processing restart request");

            if (moduleInfoList.stream().anyMatch(ModuleInfo::isDeleteTopicAndReboot)) {
                clearAlertsTopic();
            }

            Map<String, String> envVars = extractEnvVars(moduleInfoList);
            if (!envVars.isEmpty()) {
                writeEnvFile(envVars);
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

    // Drops every queued alert; the alerts consumer then resumes from the end. The traffic topic is never touched.
    private void clearAlertsTopic() {
        String topic = KafkaTopic.ThreatDetection.ALERTS;
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, internalKafkaBootstrapServers);
        KafkaConfig.addAuthenticationFromEnv(properties);
        try (AdminClient admin = AdminClient.create(properties)) {
            Map<TopicPartition, OffsetSpec> latest = new HashMap<>();
            for (TopicPartitionInfo partition : admin.describeTopics(Collections.singletonList(topic)).allTopicNames()
                    .get(KAFKA_ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS).get(topic).partitions()) {
                latest.put(new TopicPartition(topic, partition.partition()), OffsetSpec.latest());
            }
            Map<TopicPartition, RecordsToDelete> toDelete = new HashMap<>();
            for (Map.Entry<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> end : admin.listOffsets(latest).all()
                    .get(KAFKA_ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS).entrySet()) {
                toDelete.put(end.getKey(), RecordsToDelete.beforeOffset(end.getValue().offset()));
            }
            admin.deleteRecords(toDelete).all().get(KAFKA_ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            logger.warnAndAddToDb("Cleared " + topic + " (" + toDelete.size() + " partitions)");
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error clearing " + topic);
        }
    }

    private void restartSelf() {
        logger.infoAndAddToDb("Restarting application to apply new configuration");
        System.exit(0);
    }
}
