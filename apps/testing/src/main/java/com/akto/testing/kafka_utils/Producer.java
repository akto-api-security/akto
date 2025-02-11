package com.akto.testing.kafka_utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.RecordsToDelete;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.TopicPartition;
import org.bson.types.ObjectId;

import com.akto.dto.billing.SyncLimit;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.info.SingleTestPayload;
import com.akto.kafka.Kafka;
import com.akto.testing.TestExecutor;
import com.akto.util.Constants;

public class Producer {

    public static final Kafka producer = Constants.IS_NEW_TESTING_ENABLED ?  new Kafka(Constants.LOCAL_KAFKA_BROKER_URL, Constants.LINGER_MS_KAFKA, 100, Constants.MAX_REQUEST_TIMEOUT) : null;
    public static Void pushMessagesToKafka(List<SingleTestPayload> messages){
        for(SingleTestPayload singleTestPayload: messages){
            String messageString = singleTestPayload.toString();
            producer.send(messageString, Constants.TEST_RESULTS_TOPIC_NAME);
        }
        return null;
    }

    private static void deleteAllMessagesFromTopic(String bootstrapServers, String topicName)
            throws ExecutionException, InterruptedException {

        // 1) Build minimal properties for AdminClient
        Properties adminProps = new Properties();
        adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // 2) Create AdminClient
        try (AdminClient adminClient = AdminClient.create(adminProps)) {

            // 3) Describe the topic to get partition info
            DescribeTopicsResult describeTopicsResult = adminClient
                    .describeTopics(Collections.singletonList(topicName));
            TopicDescription topicDescription = describeTopicsResult.values()
                    .get(topicName).get(); // may throw if topic doesn’t exist

            // 4) Collect partitions for the topic
            List<TopicPartition> topicPartitions = topicDescription.partitions().stream()
                    .map(info -> new TopicPartition(topicName, info.partition()))
                    .collect(Collectors.toList());

            // 5) Request the latest offsets for each partition
            Map<TopicPartition, org.apache.kafka.clients.admin.OffsetSpec> latestOffsetsRequest = 
                    new HashMap<>();
            for (TopicPartition tp : topicPartitions) {
                latestOffsetsRequest.put(tp, org.apache.kafka.clients.admin.OffsetSpec.latest());
            }
            ListOffsetsResult listOffsetsResult = adminClient.listOffsets(latestOffsetsRequest);

            // 6) Build the map for deleteRecords (partition -> RecordsToDelete)
            Map<TopicPartition, RecordsToDelete> partitionRecordsToDelete = new HashMap<>();
            for (TopicPartition tp : topicPartitions) {
                long latestOffset = listOffsetsResult.partitionResult(tp).get().offset();
                partitionRecordsToDelete.put(tp, RecordsToDelete.beforeOffset(latestOffset));
            }

            // 7) Delete all records up to the latest offset
            adminClient.deleteRecords(partitionRecordsToDelete).all().get();

            System.out.println("All existing messages in topic \"" + topicName + "\" have been deleted.");
        }
    }

    public void initProducer(TestingRun testingRun, ObjectId summaryId, SyncLimit syncLimit, boolean doInitOnly){
        TestExecutor executor = new TestExecutor();
        if(!doInitOnly){
            try {
                deleteAllMessagesFromTopic(Constants.LOCAL_KAFKA_BROKER_URL, Constants.TEST_RESULTS_TOPIC_NAME);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }
        executor.init(testingRun, summaryId, syncLimit, doInitOnly);
    }
}
