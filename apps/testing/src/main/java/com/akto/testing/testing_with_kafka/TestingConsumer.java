package com.akto.testing.testing_with_kafka;
import static com.akto.testing.Utils.readJsonContentFromFile;
import static com.akto.testing.Utils.writeJsonContentInFile;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.errors.WakeupException;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.DaoInit;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.info.TestMessages;
import com.akto.runtime.utils.Utils;
import com.akto.testing.TestExecutor;
import com.akto.util.Constants;
import com.akto.util.DashboardMode;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;

public class TestingConsumer {

    static Properties properties = com.akto.runtime.utils.Utils.configProperties(Constants.LOCAL_KAFKA_BROKER_URL, Constants.AKTO_KAFKA_GROUP_ID_CONFIG, Constants.AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG);
    private static Consumer<String, String> consumer = new KafkaConsumer<>(properties); 
    private static final Logger logger = LoggerFactory.getLogger(TestingConsumer.class);
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(5);

    public void initializeConsumer() {
        String mongoURI = System.getenv("AKTO_MONGO_CONN");
        ReadPreference readPreference = ReadPreference.secondary();
        if(DashboardMode.isOnPremDeployment()){
            readPreference = ReadPreference.primary();
        }
        WriteConcern writeConcern = WriteConcern.W1;
        DaoInit.init(new ConnectionString(mongoURI), readPreference, writeConcern);
    }

    public static TestMessages parseTestMessage(String message) {
        JSONObject jsonObject = JSON.parseObject(message);
        ObjectId testingRunId = new ObjectId(jsonObject.getString("testingRunId"));
        ObjectId testingRunResultSummaryId = new ObjectId(jsonObject.getString("testingRunResultSummaryId"));
        ApiInfo.ApiInfoKey apiInfoKey = ApiInfo.getApiInfoKeyFromString(jsonObject.getString("apiInfoKey"));
        String subcategory = jsonObject.getString("subcategory");
        List<TestingRunResult.TestLog> testLogs = JSON.parseArray(jsonObject.getString("testLogs"), TestingRunResult.TestLog.class);
        int accountId = jsonObject.getInteger("accountId");
        return new TestMessages(testingRunId, testingRunResultSummaryId, apiInfoKey, subcategory, testLogs, accountId);
    }

    public void runTestFromMessage(String message){
        TestMessages testMessages = parseTestMessage(message);
        Context.accountId.set(testMessages.getAccountId());
        TestExecutor executor = new TestExecutor();

        CommonSingletonForTesting instance = CommonSingletonForTesting.getInstance();
        String subCategory = testMessages.getSubcategory();
        TestConfig testConfig = instance.getTestConfigMap().get(subCategory);
        ApiInfoKey apiInfoKey = testMessages.getApiInfoKey();

        List<String> messagesList = instance.getTestingUtil().getSampleMessages().get(apiInfoKey);
        if(messagesList == null || messagesList.isEmpty()){}
        else{
            String sample = messagesList.get(messagesList.size() - 1);
            logger.info("Running test for: " + apiInfoKey + " with subcategory: " + subCategory);
            TestingRunResult runResult = executor.runTestNew(apiInfoKey, testMessages.getTestingRunId(), instance.getTestingUtil(), testMessages.getTestingRunResultSummaryId(),testConfig , instance.getTestingRunConfig(), instance.isDebug(), testMessages.getTestLogs(), sample);
            executor.insertResultsAndMakeIssues(Collections.singletonList(runResult), testMessages.getTestingRunResultSummaryId());
        }
    }
    
    public void init(int maxRunTimeInSeconds) {
        BasicDBObject currentTestInfo = readJsonContentFromFile(Constants.TESTING_STATE_FOLDER_PATH, Constants.TESTING_STATE_FILE_NAME, BasicDBObject.class);
        boolean isConsumerRunning = false;
        if(currentTestInfo != null){
            isConsumerRunning = currentTestInfo.getBoolean("CONSUMER_RUNNING");
        }
        if(isConsumerRunning){
            String topicName = Constants.TEST_RESULTS_TOPIC_NAME;
            consumer = new KafkaConsumer<>(properties); 
            consumer.subscribe(Arrays.asList(topicName));   
        }
        final AtomicBoolean exceptionOnCommitSync = new AtomicBoolean(false);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        long lastSyncOffset = 0;
        try {
            boolean completed = false;
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(5000));
                if(records.isEmpty()){
                    logger.info("No records found in testing consumer");
                    completed = true;
                }

                for (ConsumerRecord<String, String> record : records) {
                    lastSyncOffset++;
                    logger.info("Reading message in testing consumer: " + lastSyncOffset);
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        runTestFromMessage(record.value());
                    }, threadPool);
                    
                    futures.add(future);
                    try {
                        consumer.commitSync();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                if (completed) {
                    CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                    logger.info("Waiting for all futures to complete in testing consumer");
                    allFutures.join();
                    logger.info("All futures completed in testing consumer");  
                    break;
                }
                
            }
        } catch (WakeupException ignored) {
            logger.info("Wake up exception in testing consumer");
        } catch (Exception e) {
            exceptionOnCommitSync.set(true);
            Utils.printL(e);
            logger.error("Error in main testing consumer: " + e.getMessage());
            logger.info(e.getCause().getMessage());
            e.printStackTrace();
        } finally {
            logger.info("Shutting down consumer and thread pool.");
            consumer.close();
            consumer = null;
            writeJsonContentInFile(Constants.TESTING_STATE_FOLDER_PATH, Constants.TESTING_STATE_FILE_NAME, null);
        }
    }
}
