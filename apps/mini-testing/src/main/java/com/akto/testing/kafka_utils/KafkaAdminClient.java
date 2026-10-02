package com.akto.testing.kafka_utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;

import java.util.Collections;

import com.akto.kafka.KafkaConfig;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;

/**
 * The one AdminClient this module needs, shared by both Producer and ConsumerUtil. Not a cache -
 * there is nothing here that goes stale or needs invalidating, just a single connection created
 * once and reused for the process's lifetime instead of a fresh TCP handshake (plus a full
 * AdminClientConfig/metrics-reporter log dump, through the same synchronized logger every other
 * thread is also contending for) on every call.
 *
 * getConsumerLag lives here rather than on Producer because it isn't a producer concern - it's
 * read by ConsumerUtil's own completion check, on a ~100ms cadence while idle, which is exactly
 * why a fresh AdminClient per call was expensive enough to matter.
 */
public class KafkaAdminClient {

    private static final LoggerMaker loggerMaker = new LoggerMaker(KafkaAdminClient.class, LogDb.TESTING);

    private static final AdminClient INSTANCE = Constants.IS_NEW_TESTING_ENABLED ? buildClient() : null;

    private KafkaAdminClient() {}

    private static AdminClient buildClient() {
        try {
            Properties adminProps = KafkaConfig.createAdminProperties(Constants.LOCAL_KAFKA_BROKER_URL,
                    KafkaConfig.isKafkaAuthenticationEnabled(), KafkaConfig.getKafkaUsername(), KafkaConfig.getKafkaPassword());
            if (adminProps == null) {
                loggerMaker.errorAndAddToDb("Kafka authentication is enabled but credentials are missing; AdminClient not created.");
                return null;
            }
            return AdminClient.create(adminProps);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error creating shared Kafka AdminClient");
            return null;
        }
    }

    /** Null when IS_NEW_TESTING_ENABLED is false, or construction failed - callers must check. */
    public static AdminClient get() {
        return INSTANCE;
    }

    /** Called once, from Main's shutdown path. Not per-call - this client outlives every caller. */
    public static void close() {
        if (INSTANCE != null) {
            try {
                INSTANCE.close();
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error closing shared Kafka AdminClient");
            }
        }
    }

    /**
     * Messages produced to this attempt's topic that its consumer group has not yet committed.
     *
     * This is what tells a resumed process that the run is finished. The in-process counter it
     * replaces starts at zero on every init(), so after a restart it can never reach the expected
     * total however much work is genuinely left; lag is absolute rather than a delta from process
     * start, so a fresh process reads the same truth as the one that died.
     *
     * A committed offset here means the test actually ran: the parallel consumer only advances an
     * offset once the record's handler has returned, and the handler blocks on the test future.
     *
     * @return the lag, or -1 when it cannot be determined - callers must treat that as "unknown",
     *         never as zero, or a broker hiccup would look like completion.
     */
    public static long getConsumerLag(String topicName, String groupId) {
        AdminClient adminClient = get();
        if (adminClient == null) {
            return -1;
        }
        try {
            Map<TopicPartition, OffsetAndMetadata> committed = adminClient
                    .listConsumerGroupOffsets(groupId)
                    .partitionsToOffsetAndMetadata()
                    .get(10, TimeUnit.SECONDS);

            Map<TopicPartition, OffsetSpec> endSpecs = new HashMap<>();
            for (TopicPartition tp : committed.keySet()) {
                if (topicName.equals(tp.topic())) {
                    endSpecs.put(tp, OffsetSpec.latest());
                }
            }
            if (endSpecs.isEmpty()) {
                // group has committed nothing for this topic yet - not the same as drained
                return -1;
            }

            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffsets = adminClient
                    .listOffsets(endSpecs).all().get(10, TimeUnit.SECONDS);

            long lag = 0;
            for (Map.Entry<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> entry : endOffsets.entrySet()) {
                OffsetAndMetadata offsetAndMetadata = committed.get(entry.getKey());
                long committedOffset = offsetAndMetadata == null ? 0 : offsetAndMetadata.offset();
                lag += Math.max(0, entry.getValue().offset() - committedOffset);
            }
            return lag;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error reading consumer lag for topic " + topicName + " group " + groupId);
            return -1;
        }
    }

    /**
     * The total number of messages ever produced to this attempt's topic - the producer's own
     * count, read back from Kafka rather than a file the producer used to write it to
     * (TestingStateStore.EXPECTED_RECORDS, removed along with the file). Restores
     * TestRunMetrics' expectedRecords, which has been hardcoded to -1 (dead: no source) since
     * that removal - TESTRUN PROGRESS's done=X/Y and its ETA only work with a real value here.
     *
     * Called once per init(), before any consumption starts, so its own listOffsets call isn't
     * in the hot per-tick loop the way getConsumerLag's is.
     *
     * @return the end offset across all partitions, or -1 when it cannot be determined.
     */
    public static long getEndOffset(String topicName) {
        AdminClient adminClient = get();
        if (adminClient == null) {
            return -1;
        }
        try {
            TopicDescription description = adminClient
                    .describeTopics(Collections.singletonList(topicName))
                    .allTopicNames().get(10, TimeUnit.SECONDS)
                    .get(topicName);
            if (description == null) {
                return -1;
            }

            Map<TopicPartition, OffsetSpec> endSpecs = new HashMap<>();
            for (TopicPartitionInfo partition : description.partitions()) {
                endSpecs.put(new TopicPartition(topicName, partition.partition()), OffsetSpec.latest());
            }
            if (endSpecs.isEmpty()) {
                return -1;
            }

            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffsets = adminClient
                    .listOffsets(endSpecs).all().get(10, TimeUnit.SECONDS);

            long total = 0;
            for (ListOffsetsResult.ListOffsetsResultInfo info : endOffsets.values()) {
                total += info.offset();
            }
            return total;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error reading end offset for topic " + topicName);
            return -1;
        }
    }
}
