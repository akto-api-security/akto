package com.akto.hybrid_runtime;

import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.metrics.AllMetrics;
import com.akto.runtime.parser.SampleParser;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class AktoTrafficCollectorTelemetry {

    private static final LoggerMaker loggerMaker = new LoggerMaker(AktoTrafficCollectorTelemetry.class, LogDb.RUNTIME);
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final long POLL_TIMEOUT_MS = 10000;
    private static final String HEARTBEAT_GROUP_ID = "-heartbeat";

    private final String kafkaUrl;
    private final String groupIdConfig;
    private final int maxPollRecordsConfig;
    private final String heartbeatTopicName;
    private final DataActor dataActor;
    private final int batchSize;
    private final String miniRuntimeName;

    public AktoTrafficCollectorTelemetry(
            String kafkaUrl,
            String groupIdConfig,
            int maxPollRecordsConfig,
            String heartbeatTopicName,
            DataActor dataActor,
            String miniRuntimeName) {
        this(kafkaUrl, groupIdConfig, maxPollRecordsConfig, heartbeatTopicName, dataActor, DEFAULT_BATCH_SIZE, miniRuntimeName);
    }

    public AktoTrafficCollectorTelemetry(
            String kafkaUrl,
            String groupIdConfig,
            int maxPollRecordsConfig,
            String heartbeatTopicName,
            DataActor dataActor,
            int batchSize,
            String miniRuntimeName) {
        this.kafkaUrl = kafkaUrl;
        this.groupIdConfig = groupIdConfig;
        this.maxPollRecordsConfig = maxPollRecordsConfig;
        this.heartbeatTopicName = heartbeatTopicName;
        this.dataActor = dataActor;
        this.batchSize = batchSize;
        this.miniRuntimeName = miniRuntimeName;
    }

    public void run() {
        ExecutorService pollingExecutor = Executors.newSingleThreadExecutor();
        pollingExecutor.execute(() -> {
            try {
                Context.accountId.set(Context.getActualAccountId());
                loggerMaker.infoAndAddToDb("Starting pod heartbeat consumer");

                runConsumerLoop();

            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error while starting pod heartbeat consumer");
            }
        });
    }


    private void runConsumerLoop() {
        Properties consumerProps = Main.configProperties(kafkaUrl, groupIdConfig + HEARTBEAT_GROUP_ID, maxPollRecordsConfig);
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps);
        List<ModuleInfo> heartbeatBatch = new ArrayList<>();

        try {
            consumer.subscribe(Collections.singletonList(heartbeatTopicName));
            loggerMaker.infoAndAddToDb("Heartbeat consumer subscribed to " + heartbeatTopicName);

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(POLL_TIMEOUT_MS));

                commitRecords(consumer);
                processHeartbeatBatch(records, heartbeatBatch);
                flushRemainingHeartbeats(heartbeatBatch);
            }

        } catch (WakeupException ignored) {
            loggerMaker.infoAndAddToDb("Heartbeat consumer received shutdown signal");
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in heartbeat consumer");
        } finally {
            flushRemainingHeartbeats(heartbeatBatch);
            consumer.close();
            loggerMaker.infoAndAddToDb("Heartbeat consumer closed");
        }
    }


    private void commitRecords(KafkaConsumer<String, String> consumer) {
        try {
            consumer.commitSync();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error committing heartbeat consumer offsets");
            throw e;
        }
    }


    private void processHeartbeatBatch(ConsumerRecords<String, String> records, List<ModuleInfo> heartbeatBatch) {
        for (ConsumerRecord<String, String> record : records) {
            ModuleInfo heartbeat = parseHeartbeat(record);

            if (heartbeat == null) {
                continue;
            }

            heartbeatBatch.add(heartbeat);


            if (heartbeatBatch.size() >= batchSize) {
                upsertHeartbeats(heartbeatBatch);
            }
        }
    }

    private ModuleInfo parseHeartbeat(ConsumerRecord<String, String> record) {
        try {
            ModuleInfo heartbeat = SampleParser.parseHeartbeatMessage(record.value());

            if (heartbeat == null) {
                loggerMaker.errorAndAddToDb("Failed to parse module heartbeat: " + record.value());
                return null;
            }

            heartbeat.setMiniRuntimeName(this.miniRuntimeName);

            // Extract and send profiling metrics
            extractAndSendProfilingMetrics(heartbeat);

            return heartbeat;

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error while parsing module heartbeat kafka message: " + e);
            return null;
        }
    }

    private void extractAndSendProfilingMetrics(ModuleInfo heartbeat) {
        try {
            if (heartbeat.getModuleType() != ModuleInfo.ModuleType.TRAFFIC_COLLECTOR) {
                return;
            }

            Map<String, Object> additionalData = heartbeat.getAdditionalData();
            if (additionalData == null || !additionalData.containsKey("profiling")) {
                return;
            }

            Map<String, Object> profiling = (Map<String, Object>) additionalData.get("profiling");
            if (profiling == null) {
                return;
            }

            String instanceId = heartbeat.getName(); // Use pod/daemon name as instance ID

            // Record CPU metric
            if (profiling.containsKey("cpu_percent")) {
                float cpuUsage = extractFloat(profiling.get("cpu_percent"));
                AllMetrics.instance.setTcCpuUsage(instanceId, cpuUsage);
                loggerMaker.debugInfoAddToDb("Recorded CPU metric for " + instanceId + ": " + cpuUsage + "%");
            }

            // Record Memory metric
            if (profiling.containsKey("memory_used_mb")) {
                float memoryUsedMB = extractFloat(profiling.get("memory_used_mb"));
                AllMetrics.instance.setTcMemoryUsage(instanceId, memoryUsedMB);
                loggerMaker.debugInfoAddToDb("Recorded Memory metric for " + instanceId + ": " +
                    (int)memoryUsedMB + " MB");
            }

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error extracting profiling metrics: " + e.getMessage());
        }
    }

    private float extractFloat(Object value) {
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        } else if (value instanceof String) {
            return Float.parseFloat((String) value);
        }
        return 0.0f;
    }


    private void flushRemainingHeartbeats(List<ModuleInfo> heartbeatBatch) {
        if (!heartbeatBatch.isEmpty()) {
            loggerMaker.infoAndAddToDb("Flushing remaining " + heartbeatBatch.size() + " heartbeats on shutdown");
            upsertHeartbeats(heartbeatBatch);
        }
    }


    private void upsertHeartbeats(List<ModuleInfo> heartbeatBatch) {
        try {
            dataActor.bulkUpdateModuleInfo(heartbeatBatch);
            loggerMaker.infoAndAddToDb("Upserted " + heartbeatBatch.size() + " module heartbeats to DB");
            heartbeatBatch.clear();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error upserting heartbeats to DB");
        }
    }
}
