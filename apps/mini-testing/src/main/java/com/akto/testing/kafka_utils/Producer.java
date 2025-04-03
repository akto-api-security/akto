package com.akto.testing.kafka_utils;

import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DeleteTopicsResult;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.bson.types.ObjectId;

import com.akto.dto.billing.SyncLimit;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.info.SingleTestPayload;
import com.akto.kafka.Kafka;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.testing.TestExecutor;
import com.akto.util.Constants;

public class Producer {

    public static final Kafka producer = Constants.IS_NEW_TESTING_ENABLED
            ? new Kafka(Constants.LOCAL_KAFKA_BROKER_URL, Constants.LINGER_MS_KAFKA, 100, Constants.MAX_REQUEST_TIMEOUT)
            : null;
    private static final LoggerMaker loggerMaker = new LoggerMaker(Producer.class);
    public static Void pushMessagesToKafka(List<SingleTestPayload> messages, AtomicInteger totalRecords){
        for(SingleTestPayload singleTestPayload: messages){
            String messageString = singleTestPayload.toString();
            producer.send(messageString, Constants.TEST_RESULTS_TOPIC_NAME, totalRecords);
        }
        return null;
    }

    private static void deleteTopic(String bootstrapServers, String topicName) 
            throws ExecutionException, InterruptedException {

        Properties adminProps = new Properties();
        adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        try (AdminClient adminClient = AdminClient.create(adminProps)) {
            ListTopicsResult listTopicsResult = adminClient.listTopics();
            if (!listTopicsResult.names().get().contains(topicName)) {
                loggerMaker.infoAndAddToDb("Topic \"" + topicName + "\" does not exist.", LogDb.TESTING);
                return;
            }
            DeleteTopicsResult deleteTopicsResult = adminClient.deleteTopics(Collections.singletonList(topicName));
            deleteTopicsResult.all().get();
            loggerMaker.infoAndAddToDb("Topic \"" + topicName + "\" deletion initiated.", LogDb.TESTING);

            int retries = 0;
            int maxRetries = 8;
            int baseBackoff = 500;

            while (retries < maxRetries) {
                Thread.sleep((long) (baseBackoff * Math.pow(2, retries)));
                retries++;

                Set<String> topics = adminClient.listTopics().names().get();
                if (!topics.contains(topicName)) {
                    loggerMaker.insertImportantTestingLog("Confirmed topic \"" + topicName + "\" is deleted on retry attempt: " + retries);
                    return;
                }

                loggerMaker.infoAndAddToDb("Waiting for topic \"" + topicName + "\" to be fully deleted... retry attempt: " + retries, LogDb.TESTING);
            }

            throw new RuntimeException("Topic deletion not confirmed after retries.");
        }
    }

    public static void createTopic(String bootstrapServers, String topicName) 
        throws ExecutionException, InterruptedException {
        Properties adminProps = new Properties();
        adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        try (AdminClient adminClient = AdminClient.create(adminProps)) {
            NewTopic newTopic = new NewTopic(topicName, 1, (short) 1); 
            adminClient.createTopics(Collections.singletonList(newTopic)).all().get();
            loggerMaker.insertImportantTestingLog("Topic \"" + topicName + "\" creation initiated.");

            int retries = 0;
            int maxRetries = 8;
            int baseBackoff = 500; // ms

            while (retries < maxRetries) {
                Thread.sleep((long) (baseBackoff * Math.pow(2, retries)));
                retries++;

                try {
                    TopicDescription description = adminClient.describeTopics(Collections.singletonList(topicName)).all().get().get(topicName);
                    boolean allHaveLeaders = description.partitions().stream().allMatch(p -> p.leader() != null);
                    if (allHaveLeaders) {
                        loggerMaker.insertImportantTestingLog("Confirmed topic \"" + topicName + "\" has leader assigned on retry attempt: " + retries);
                        return;
                    }
                } catch (Exception e) {
                    loggerMaker.insertImportantTestingLog("Retry attempt: " + retries + "Topic metadata not ready yet");
                }
            }

            throw new RuntimeException("Topic creation not confirmed after retries.");
        }
    }    
    public void initProducer(TestingRun testingRun, ObjectId summaryId, boolean doInitOnly, SyncLimit syncLimit){
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
