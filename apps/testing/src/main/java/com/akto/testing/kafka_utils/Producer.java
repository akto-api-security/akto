package com.akto.testing.kafka_utils;

import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DeleteTopicsResult;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
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

    private static void deleteTopic(String bootstrapServers, String topicName) 
            throws ExecutionException, InterruptedException {

        // 1) Build properties for AdminClient
        Properties adminProps = new Properties();
        adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // 2) Create AdminClient
        try (AdminClient adminClient = AdminClient.create(adminProps)) {
            
            // 3) Check if topic exists
            ListTopicsResult listTopicsResult = adminClient.listTopics();
            if (!listTopicsResult.names().get().contains(topicName)) {
                System.out.println("Topic \"" + topicName + "\" does not exist.");
                return;
            }
            
            // 4) Delete the topic
            DeleteTopicsResult deleteTopicsResult = adminClient.deleteTopics(Collections.singletonList(topicName));
            deleteTopicsResult.all().get(); // Wait for deletion to complete

            System.out.println("Topic \"" + topicName + "\" has been deleted successfully.");
            Thread.sleep(3000); 
        }
    }

    public static void createTopic(String bootstrapServers, String topicName) 
        throws ExecutionException, InterruptedException {
        Properties adminProps = new Properties();
        adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        try (AdminClient adminClient = AdminClient.create(adminProps)) {
            ListTopicsResult listTopicsResult = adminClient.listTopics();
            if (!listTopicsResult.names().get().contains(topicName)) {
                System.out.println("Topic \"" + topicName + "\" does not exist.");
                return;
            }
            NewTopic newTopic = new NewTopic(topicName, 1, (short) 1);  // Partitions = 1, Replicas = 1
            adminClient.createTopics(Collections.singletonList(newTopic)).all().get();
            System.out.println("Topic \"" + topicName + "\" created successfully.");
        }
    }

    public void initProducer(TestingRun testingRun, ObjectId summaryId, SyncLimit syncLimit, boolean doInitOnly){
        TestExecutor executor = new TestExecutor();
        if(!doInitOnly){
            try {
                deleteTopic(Constants.LOCAL_KAFKA_BROKER_URL, Constants.TEST_RESULTS_TOPIC_NAME);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }
        executor.init(testingRun, summaryId, syncLimit, doInitOnly);
    }
}
