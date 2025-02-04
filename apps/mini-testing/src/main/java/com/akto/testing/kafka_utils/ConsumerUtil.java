package com.akto.testing.kafka_utils;
import static com.akto.testing.Utils.readJsonContentFromFile;
import static com.akto.testing.Utils.writeJsonContentInFile;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.kafka.clients.consumer.*;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import com.akto.crons.GetRunningTestsStatus;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.info.SingleTestPayload;
// import com.akto.notifications.slack.CustomTextAlert;
import com.akto.testing.Main;
import com.akto.testing.TestExecutor;
import com.akto.util.Constants;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mongodb.BasicDBObject;

import io.confluent.parallelconsumer.ParallelConsumerOptions;
import io.confluent.parallelconsumer.ParallelStreamProcessor;

public class ConsumerUtil {

    static Properties properties = com.akto.runtime.utils.Utils.configProperties(Constants.LOCAL_KAFKA_BROKER_URL, Constants.AKTO_KAFKA_GROUP_ID_CONFIG, Constants.AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG);
    static{
        properties.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 10000); 
    }
    private static Consumer<String, String> consumer = Constants.IS_NEW_TESTING_ENABLED ? new KafkaConsumer<>(properties) : null; 
    private static final Logger logger = LoggerFactory.getLogger(ConsumerUtil.class);
    public static ExecutorService executor = Executors.newFixedThreadPool(100);

    public static SingleTestPayload parseTestMessage(String message) {
        JSONObject jsonObject = JSON.parseObject(message);
        ObjectId testingRunId = new ObjectId(jsonObject.getString("testingRunId"));
        ObjectId testingRunResultSummaryId = new ObjectId(jsonObject.getString("testingRunResultSummaryId"));
        ApiInfo.ApiInfoKey apiInfoKey = ApiInfo.getApiInfoKeyFromString(jsonObject.getString("apiInfoKey"));
        String subcategory = jsonObject.getString("subcategory");
        List<TestingRunResult.TestLog> testLogs = JSON.parseArray(jsonObject.getString("testLogs"), TestingRunResult.TestLog.class);
        int accountId = jsonObject.getInteger("accountId");
        return new SingleTestPayload(testingRunId, testingRunResultSummaryId, apiInfoKey, subcategory, testLogs, accountId);
    }

    public void runTestFromMessage(String message){
        SingleTestPayload singleTestPayload = parseTestMessage(message);
        Context.accountId.set(singleTestPayload.getAccountId());
        TestExecutor executor = new TestExecutor();

        TestingConfigurations instance = TestingConfigurations.getInstance();
        String subCategory = singleTestPayload.getSubcategory();
        TestConfig testConfig = instance.getTestConfigMap().get(subCategory);
        ApiInfoKey apiInfoKey = singleTestPayload.getApiInfoKey();

        List<String> messagesList = instance.getTestingUtil().getSampleMessages().get(apiInfoKey);
        if(messagesList == null || messagesList.isEmpty()){}
        else{
            String sample = messagesList.get(messagesList.size() - 1);
            logger.info("Running test for: " + apiInfoKey + " with subcategory: " + subCategory);
            // TestingRunResult runResult = executor.runTestNew(apiInfoKey, singleTestPayload.getTestingRunId(), instance.getTestingUtil(), singleTestPayload.getTestingRunResultSummaryId(),testConfig , instance.getTestingRunConfig(), instance.isDebug(), singleTestPayload.getTestLogs(), sample);
            TestingRunResult runResult = null;
            executor.insertResultsAndMakeIssues(Collections.singletonList(runResult), singleTestPayload.getTestingRunResultSummaryId());
        }
    }
    
    public void init(int maxRunTimeInSeconds) {
        executor = Executors.newFixedThreadPool(100);
        BasicDBObject currentTestInfo = readJsonContentFromFile(Constants.TESTING_STATE_FOLDER_PATH, Constants.TESTING_STATE_FILE_NAME, BasicDBObject.class);
        final String summaryIdForTest = currentTestInfo.getString("summaryId");
        final ObjectId summaryObjectId = new ObjectId(summaryIdForTest);
        final int startTime = Context.now();
        AtomicInteger lastRecordRead = new AtomicInteger(Context.now());
        boolean isConsumerRunning = false;
        if(currentTestInfo != null){
            isConsumerRunning = currentTestInfo.getBoolean("CONSUMER_RUNNING");
        }

        ParallelStreamProcessor<String, String> parallelConsumer = null;

        if(isConsumerRunning){
            String topicName = Constants.TEST_RESULTS_TOPIC_NAME;
            consumer = new KafkaConsumer<>(properties); 

            ParallelConsumerOptions<String, String> options = ParallelConsumerOptions.<String, String>builder()
                .consumer(consumer)
                .ordering(ParallelConsumerOptions.ProcessingOrder.UNORDERED) // Use unordered for parallelism
                .maxConcurrency(100) // Number of threads for parallel processing
                .commitMode(ParallelConsumerOptions.CommitMode.PERIODIC_CONSUMER_SYNC) // Commit offsets synchronously
                .batchSize(1) // Number of records to process in each poll
                .maxFailureHistory(3)
                .build();

            parallelConsumer = ParallelStreamProcessor.createEosStreamProcessor(options);
            parallelConsumer.subscribe(Arrays.asList(topicName)); 
            // if (StringUtils.hasLength(Main.AKTO_SLACK_WEBHOOK) ) {
            //     try {
            //         CustomTextAlert customTextAlert = new CustomTextAlert("Tests being picked for execution" + currentTestInfo.getInt("accountId") + " summaryId=" + summaryIdForTest);
            //         Main.SLACK_INSTANCE.send(Main.AKTO_SLACK_WEBHOOK, customTextAlert.toJson());
            //     } catch (Exception e) {
            //         logger.error("Error sending slack alert for completion of test", e);
            //     }
                
            // }
        }

        try {
            parallelConsumer.poll(record -> {
                String threadName = Thread.currentThread().getName();
                String message = record.value();
                logger.info("Thread [" + threadName + "] picked up record: " + message);
                try {
                    lastRecordRead.set(Context.now());
                    Future<?> future = executor.submit(() -> runTestFromMessage(message));
                    try {
                        future.get(4, TimeUnit.MINUTES); 
                    } catch (InterruptedException e) {
                        logger.error("Task timed out: " + message);
                        future.cancel(true);
                    } catch (Exception e) {
                        logger.error("Error in task execution: " + message, e);
                    }
                } finally {
                    logger.info("Thread [" + threadName + "] finished processing record: " + message);
                }
            });

            while (parallelConsumer != null) {
                if(!GetRunningTestsStatus.getRunningTests().isTestRunning(summaryObjectId)){
                    logger.info("Tests have been marked stopped.");
                    executor.shutdownNow();
                    break;
                }
                else if ((Context.now() - startTime > maxRunTimeInSeconds)) {
                    logger.info("Max run time reached. Stopping consumer.");
                    executor.shutdownNow();
                    break;
                }else if((Context.now() - lastRecordRead.get() > 10)){
                    logger.info("Records are empty now, thus executing final tests");
                    executor.shutdown();
                    executor.awaitTermination(maxRunTimeInSeconds, TimeUnit.SECONDS);
                    break;
                }
                Thread.sleep(100);
            }

        } catch (Exception e) {
            logger.info("Error in polling records");
        }finally{
            logger.info("Closing consumer as all results have been executed.");
            parallelConsumer.closeDrainFirst();
            parallelConsumer = null;
            consumer.close();
            writeJsonContentInFile(Constants.TESTING_STATE_FOLDER_PATH, Constants.TESTING_STATE_FILE_NAME, null);
        }
    }
}
