package com.akto.testing.kafka_utils;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

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

import com.akto.dao.context.Context;
import com.akto.dto.billing.SyncLimit;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.info.SingleTestPayload;
import com.akto.kafka.Kafka;
import com.akto.testing.TestExecutor;
import com.akto.util.Constants;

public class Producer {

    private static final LoggerMaker logger = new LoggerMaker(Producer.class, LogDb.TESTING);

    public static final Kafka producer = Constants.IS_NEW_TESTING_ENABLED ?  new Kafka(Constants.LOCAL_KAFKA_BROKER_URL, Constants.LINGER_MS_KAFKA, 100, Constants.MAX_REQUEST_TIMEOUT, 3) : null;
    public static Void pushMessagesToKafka(List<SingleTestPayload> messages, AtomicInteger totalRecords, AtomicInteger throttleNumber){
        for(SingleTestPayload singleTestPayload: messages){
            String messageString = singleTestPayload.toString();
            try {
                int waitStart = Context.now();
                while (throttleNumber.get() > 1000 && (Context.now() - waitStart) < Constants.MAX_WAIT_FOR_SLEEP) {
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            if(totalRecords.get() % 3000 == 0) {
                logger.debug("Total records sent to Kafka: {}, Throttle count: {}", totalRecords.get(), throttleNumber.get());
            }
            totalRecords.incrementAndGet();
            throttleNumber.incrementAndGet();
            producer.sendWithCounter(messageString, Constants.TEST_RESULTS_TOPIC_NAME, throttleNumber);
        }
        return null;
    }

    private static void deleteTopicWithRetries(String bootstrapServers, String topicName) {
        int retries = 0;
        int maxRetries = 5;
        int baseBackoff = 500; 
    
        while (retries < maxRetries) {
            try {
                deleteTopic(bootstrapServers, topicName);
                return; 
            } catch (Exception e) {
                retries++;
                long backoff = (long) (baseBackoff * Math.pow(2, retries));
                logger.debug("Attempt {} to delete topic '{}' failed: {}. Retrying in {}ms...", retries, topicName, e.getMessage(), backoff);
    
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting to retry topic deletion", ie);
                }
            }
        }
    
        throw new RuntimeException("Failed to delete topic '" + topicName + "' after " + maxRetries + " retries.");
    }

    public static void createTopicWithRetries(String bootstrapServers, String topicName) {
        int retries = 0;
        int maxRetries = 5;
        int baseBackoff = 500;
    
        while (retries < maxRetries) {
            try {
                createTopic(bootstrapServers, topicName);
                return; // success
            } catch (Exception e) {
                retries++;
                long backoff = (long) (baseBackoff * Math.pow(2, retries));
                logger.warn("Attempt {} to create topic '{}' failed: {}. Retrying in {}ms...", retries, topicName, e.getMessage(), backoff);
    
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while retrying topic creation", ie);
                }
            }
        }
    
        throw new RuntimeException("Failed to create topic '" + topicName + "' after " + maxRetries + " retries.");
    }
    
    

    private static void deleteTopic(String bootstrapServers, String topicName) 
            throws ExecutionException, InterruptedException {

        Properties adminProps = new Properties();
        adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        try (AdminClient adminClient = AdminClient.create(adminProps)) {
            ListTopicsResult listTopicsResult = adminClient.listTopics();
            if (!listTopicsResult.names().get().contains(topicName)) {
                logger.debug("Topic \"" + topicName + "\" does not exist.");
                return;
            }
            DeleteTopicsResult deleteTopicsResult = adminClient.deleteTopics(Collections.singletonList(topicName));
            logger.debug("Topic \"" + topicName + "\" deletion initiated.");
            deleteTopicsResult.all().get();

            int retries = 0;
            int maxRetries = 8;
            int baseBackoff = 500;

            while (retries < maxRetries) {
                Thread.sleep((long) (baseBackoff * Math.pow(2, retries)));
                retries++;

                Set<String> topics = adminClient.listTopics().names().get();
                if (!topics.contains(topicName)) {
                    logger.debug("Confirmed topic \"" + topicName + "\" is deleted on retry attempt: " + retries);
                    return;
                }

                logger.debug("Waiting for topic \"" + topicName + "\" to be fully deleted... retry attempt: " + retries);
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
            logger.debug("Topic \"" + topicName + "\" creation initiated.");
            adminClient.createTopics(Collections.singletonList(newTopic)).all().get();

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
                        logger.debug("Confirmed topic \"" + topicName + "\" has leader assigned on retry attempt: " + retries);
                        return;
                    }
                } catch (Exception e) {
                    logger.warn("Retry {}: Topic metadata not ready yet - {}", retries, e.getMessage());
                }

                logger.debug("Waiting for topic \"" + topicName + "\" to be fully created with leader on retry attempt: " + retries);
            }

            throw new RuntimeException("Topic creation not confirmed after retries.");
        }
    }

    public void initProducer(TestingRun testingRun, ObjectId summaryId, SyncLimit syncLimit, boolean doInitOnly){
        TestExecutor executor = new TestExecutor();
        if(!doInitOnly){
            try {
                deleteTopicWithRetries(Constants.LOCAL_KAFKA_BROKER_URL, Constants.TEST_RESULTS_TOPIC_NAME);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        executor.init(testingRun, summaryId, syncLimit, doInitOnly);
    }
}
