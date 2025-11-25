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

import com.akto.dao.context.Context;
import com.akto.dto.billing.SyncLimit;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.info.SingleTestPayload;
import com.akto.kafka.Kafka;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.test_editor.execution.Executor;
import com.akto.testing.TestExecutor;
import com.akto.util.Constants;

public class Producer {

    public static final Kafka producer = Constants.IS_NEW_TESTING_ENABLED
            ? new Kafka(Constants.LOCAL_KAFKA_BROKER_URL, Constants.LINGER_MS_KAFKA, 100, Constants.MAX_REQUEST_TIMEOUT, 3)
            : null;
    
    private static final LoggerMaker loggerMaker = new LoggerMaker(Producer.class, LogDb.TESTING);

    public static Void pushMessagesToKafka(List<SingleTestPayload> messages, AtomicInteger totalRecords, AtomicInteger throttleNumber){
        for(SingleTestPayload singleTestPayload: messages){
            String messageString = singleTestPayload.toString();
            try {
                int waitStart = Context.now();
                if (throttleNumber.get() > 10000) {
                    loggerMaker.insertImportantTestingLog("Throttling: Waiting due to high throttleNumber: " + throttleNumber.get());
                }
                while (throttleNumber.get() > 10000 && (Context.now() - waitStart) < Constants.MAX_WAIT_FOR_SLEEP) {
                    Thread.sleep(1000);
                }
                if (throttleNumber.get() > 10000) {
                    loggerMaker.insertImportantTestingLog("Throttling timeout reached. Still have high throttleNumber: " + throttleNumber.get() + ". Proceeding anyway.");
                }
            } catch (Exception e) {
                loggerMaker.insertImportantTestingLog("Error during throttling wait: " + e.getMessage());
                e.printStackTrace();
            }
            totalRecords.incrementAndGet();
            throttleNumber.incrementAndGet();

            // Check if producer is ready before sending
            if (producer == null) {
                loggerMaker.insertImportantTestingLog("Kafka producer is null! Cannot send message. ThrottleNumber will remain incremented.");
                continue;
            }

            if (!producer.producerReady) {
                loggerMaker.insertImportantTestingLog("Kafka producer not ready! Cannot send message. ThrottleNumber will remain incremented.");
                continue;
            }

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
                loggerMaker.infoAndAddToDb("Attempt " +retries + " to delete topic failed: " + e.getMessage());
    
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting to retry topic deletion", ie);
                }
            }
        }

        loggerMaker.insertImportantTestingLog("CRITICAL: Failed to delete topic '" + topicName + "' after " + maxRetries + " retries.");
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
                loggerMaker.insertImportantTestingLog("Attempt " + retries + " to create topic failed: " + e.getMessage());

                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while retrying topic creation", ie);
                }
            }
        }

        loggerMaker.insertImportantTestingLog("CRITICAL: Failed to create topic '" + topicName + "' after " + maxRetries + " retries.");
        throw new RuntimeException("Failed to create topic '" + topicName + "' after " + maxRetries + " retries.");
    }

    private static void deleteTopic(String bootstrapServers, String topicName) 
            throws ExecutionException, InterruptedException {

        Properties adminProps = new Properties();
        adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        try (AdminClient adminClient = AdminClient.create(adminProps)) {
            ListTopicsResult listTopicsResult = adminClient.listTopics();
            if (!listTopicsResult.names().get().contains(topicName)) {
                loggerMaker.infoAndAddToDb("Topic \"" + topicName + "\" does not exist.");
                return;
            }
            DeleteTopicsResult deleteTopicsResult = adminClient.deleteTopics(Collections.singletonList(topicName));
            deleteTopicsResult.all().get();
            loggerMaker.infoAndAddToDb("Topic \"" + topicName + "\" deletion initiated.");

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

                loggerMaker.infoAndAddToDb("Waiting for topic \"" + topicName + "\" to be fully deleted... retry attempt: " + retries);
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
    public static String getProducerStatus() {
        if (producer == null) {
            return "Producer is null (new testing not enabled)";
        }
        return "Producer ready: " + producer.producerReady;
    }

    public void initProducer(TestingRun testingRun, ObjectId summaryId, boolean doInitOnly, SyncLimit syncLimit){
        TestExecutor executor = new TestExecutor();
        if(!doInitOnly){
            try {
                deleteTopicWithRetries(Constants.LOCAL_KAFKA_BROKER_URL, Constants.TEST_RESULTS_TOPIC_NAME);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error deleting topic: " + e.getMessage());
                e.printStackTrace();
            }
        }
        Executor.clearRoleCache();
        executor.init(testingRun, summaryId, syncLimit, doInitOnly);
    }
}
