package com.akto.testing.kafka_utils;
import static com.akto.testing.Utils.readJsonContentFromFile;
import static com.akto.testing.Utils.writeJsonContentInFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import org.apache.kafka.clients.consumer.*;
import org.bson.types.ObjectId;

import com.akto.crons.GetRunningTestsStatus;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.TestResult.TestError;
import com.akto.dto.testing.info.SingleTestPayload;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.testing.TestExecutor;
import com.akto.testing.Utils;
import com.akto.util.Constants;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mongodb.BasicDBObject;

import io.confluent.parallelconsumer.ParallelConsumerOptions;
import io.confluent.parallelconsumer.ParallelStreamProcessor;

public class ConsumerUtil {

    public static final String EXPECTED_RECORDS_KEY = "expectedRecords";

    private static final int DEBUG_ACCOUNT_ID = 1764738582;
    private static final int MAX_POLL_INTERVAL_MS = 10000;
    /** If queue stays empty with no processed progress this long, treat remaining expected records as lost. */
    private static final long DRAIN_IDLE_GRACE_MS = 5L * 60L * 1000L;
    private static final long DEBUG_PROGRESS_LOG_INTERVAL_MS = 60_000L;

    static Properties properties = com.akto.runtime.utils.Utils.configProperties(Constants.LOCAL_KAFKA_BROKER_URL, Constants.AKTO_KAFKA_GROUP_ID_CONFIG, Constants.AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG);
    static{
        properties.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, MAX_POLL_INTERVAL_MS);
    }
    private static Consumer<String, String> consumer = Constants.IS_NEW_TESTING_ENABLED ? new KafkaConsumer<>(properties) : null;
    private static final LoggerMaker loggerMaker = new LoggerMaker(ConsumerUtil.class, LogDb.TESTING);
    public static ExecutorService executor = Executors.newFixedThreadPool(150);
    private static final int maxRunTimeForTests = 5 * 60;
    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    private static final ConcurrentHashMap<ApiInfoKey, Integer> testedApisMap = new ConcurrentHashMap<>();

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
        ObjectId summaryId = singleTestPayload.getTestingRunResultSummaryId();
        TestExecutor.setTestRunActivityContext(summaryId);
        try {
            TestExecutor executor = new TestExecutor();

            TestingConfigurations instance = TestingConfigurations.getInstance();
            String subCategory = singleTestPayload.getSubcategory();
            TestConfig testConfig = instance.getTestConfigMap().get(subCategory);
            ApiInfoKey apiInfoKey = singleTestPayload.getApiInfoKey();

            List<String> messagesList = instance.getTestingUtil().getSampleMessages().get(apiInfoKey);
            int timeNow = Context.now();
            if(messagesList == null || messagesList.isEmpty()){}
            else{
                String sample = messagesList.get(messagesList.size() - 1);
                loggerMaker.infoAndAddToDb("Running test for: " + apiInfoKey + " with subcategory: " + subCategory);
                TestingRunResult runResult = executor.runTestNew(apiInfoKey, singleTestPayload.getTestingRunId(), instance.getTestingUtil(), singleTestPayload.getTestingRunResultSummaryId(),testConfig , instance.getTestingRunConfig(), instance.isDebug(), singleTestPayload.getTestLogs(), sample);
                executor.persistTestLogsToDb(runResult != null ? runResult.getTestLogs() : null);
                executor.insertResultsAndMakeIssues(Collections.singletonList(runResult), singleTestPayload.getTestingRunResultSummaryId());

                testedApisMap.put(apiInfoKey, Context.now());

                loggerMaker.insertImportantTestingLog("Test completed for: " + apiInfoKey + " with subcategory: " + subCategory + " in " + (Context.now() - timeNow) + " seconds");
            }
        } finally {
            TestExecutor.clearActivityContext();
        }
    }

    private void createTimedOutResultFromMessage(String message){
        SingleTestPayload singleTestPayload = parseTestMessage(message);
        Context.accountId.set(singleTestPayload.getAccountId());
        TestExecutor.setTestRunActivityContext(singleTestPayload.getTestingRunResultSummaryId());
        try {
            TestExecutor testExecutor = new TestExecutor();

            String subCategory = singleTestPayload.getSubcategory();
            TestConfig testConfig = TestingConfigurations.getInstance().getTestConfigMap().get(subCategory);

            String testSuperType = testConfig.getInfo().getCategory().getName();
            String testSubType = testConfig.getInfo().getSubCategory();

            TestingRunResult runResult = Utils.generateFailedRunResultForMessage(singleTestPayload.getTestingRunId(), singleTestPayload.getApiInfoKey(), testSuperType, testSubType, singleTestPayload.getTestingRunResultSummaryId(), new ArrayList<>(),  TestError.TEST_TIMED_OUT.getMessage());
            testExecutor.insertResultsAndMakeIssues(Collections.singletonList(runResult), singleTestPayload.getTestingRunResultSummaryId());
        } finally {
            TestExecutor.clearActivityContext();
        }
    }

    private static void debugLogToDb(int accountId, String message) {
        if (accountId != DEBUG_ACCOUNT_ID) {
            return;
        }
        loggerMaker.insertImportantTestingLog("[DEBUG-CONSUMER-" + DEBUG_ACCOUNT_ID + "] " + message);
    }

    /**
     * Performs bulk update of lastTested field for all APIs that were tested
     */
    private void flushLastTestedUpdates() {
        if (testedApisMap.isEmpty()) {
            loggerMaker.infoAndAddToDb("No APIs to update for lastTested field");
            return;
        }

        try {
            dataActor.bulkUpdateLastTestedField(testedApisMap);
            testedApisMap.clear();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error during bulk update of lastTested field: " + e.getMessage());
        }
    }
    
    public void init(int maxRunTimeInSeconds) {
        TestingConfigurations instance = TestingConfigurations.getInstance();
        executor = Executors.newFixedThreadPool(instance.getMaxConcurrentRequest());
        BasicDBObject currentTestInfo = readJsonContentFromFile(Constants.TESTING_STATE_FOLDER_PATH, Constants.TESTING_STATE_FILE_NAME, BasicDBObject.class);
        final String summaryIdForTest = currentTestInfo.getString("summaryId");
        final ObjectId summaryObjectId = new ObjectId(summaryIdForTest);
        int startTime = Context.now();
        int effectiveMaxRunTime = maxRunTimeInSeconds;
        if (currentTestInfo.containsField(TestingRun.PICKED_UP_TIMESTAMP)) {
            startTime = currentTestInfo.getInt(TestingRun.PICKED_UP_TIMESTAMP, startTime);
        }
        if (currentTestInfo.containsField("testRunMaxTimeSeconds")) {
            effectiveMaxRunTime = currentTestInfo.getInt("testRunMaxTimeSeconds", maxRunTimeInSeconds);
        }
        final int expectedRecords = currentTestInfo.containsField(EXPECTED_RECORDS_KEY)
                ? currentTestInfo.getInt(EXPECTED_RECORDS_KEY)
                : -1;
        final int accountId = currentTestInfo.containsField("accountId")
                ? currentTestInfo.getInt("accountId")
                : (Context.accountId.get() != null ? Context.accountId.get() : -1);
        if (accountId > 0) {
            Context.accountId.set(accountId);
        }
        AtomicBoolean firstRecordRead = new AtomicBoolean(false);
        AtomicInteger processedRecords = new AtomicInteger(0);
        AtomicInteger polledRecords = new AtomicInteger(0);

        boolean isConsumerRunning = false;
        if(currentTestInfo != null){
            isConsumerRunning = currentTestInfo.getBoolean("CONSUMER_RUNNING");
        }

        ParallelStreamProcessor<String, String> parallelConsumer = null;

        /*
         * Edge case:
         * In case the module restarts and starts processing the incomplete testing run,
         * then the consumer will process some of the records again.
         * This happens because the commits to kafka are periodic (5 seconds, default) and not per message.
         */
        
        if(isConsumerRunning){
            String topicName = Constants.TEST_RESULTS_TOPIC_NAME;
            consumer = new KafkaConsumer<>(properties); 

            ParallelConsumerOptions<String, String> options = ParallelConsumerOptions.<String, String>builder()
                .consumer(consumer)
                .ordering(ParallelConsumerOptions.ProcessingOrder.UNORDERED) // Use unordered for parallelism
                .maxConcurrency(instance.getMaxConcurrentRequest()) // Number of threads for parallel processing
                .commitMode(ParallelConsumerOptions.CommitMode.PERIODIC_CONSUMER_SYNC) // Commit offsets synchronously
                .batchSize(1) // Number of records to process in each poll
                .maxFailureHistory(3)
                .build();

            parallelConsumer = ParallelStreamProcessor.createEosStreamProcessor(options);
            parallelConsumer.subscribe(Arrays.asList(topicName));
            debugLogToDb(accountId, "consumer started summaryId=" + summaryIdForTest
                    + " expectedRecords=" + expectedRecords
                    + " maxConcurrency=" + instance.getMaxConcurrentRequest());
        }

        try {
            if(parallelConsumer != null){
            parallelConsumer.poll(record -> {
                String threadName = Thread.currentThread().getName();
                String message = record.value();
                // Stable id per Kafka record: same record redelivered (e.g. after rebalance/restart) will log the same id again.
                String recordId = record.getSingleConsumerRecord().topic() + "-p" + record.getSingleConsumerRecord().partition() + "-o" + record.offset();
                polledRecords.incrementAndGet();
                loggerMaker.infoAndAddToDb("Thread [" + threadName + "] picked up record recordId=" + recordId + " " + message);
                debugLogToDb(accountId, "picked up recordId=" + recordId + " polled=" + polledRecords.get());
                try {
                    if(!executor.isShutdown()){
                        Future<?> future = executor.submit(() -> runTestFromMessage(message));
                        firstRecordRead.set(true);
                        try {
                            future.get(maxRunTimeForTests, TimeUnit.SECONDS); 
                        } catch (InterruptedException | TimeoutException e) {
                            loggerMaker.errorAndAddToDb("Task timed out");
                            future.cancel(true);
                            createTimedOutResultFromMessage(message);
                        } catch(RejectedExecutionException e){
                            future.cancel(true);
                        } catch (Exception e) {
                            future.cancel(true);
                            loggerMaker.errorAndAddToDb(e, "Error in task execution: " + message);
                        }
                    }
                    
                } finally {
                    processedRecords.incrementAndGet();
                    loggerMaker.infoAndAddToDb("Thread [" + threadName + "] finished processing record recordId=" + recordId);
                }
            });
        }

            long drainIdleSinceMs = -1L;
            int lastProcessedSeen = -1;
            long lastDebugLogMs = 0L;
            int lastLoggedProcessed = -1;
            int lastLoggedPolled = -1;
            while (parallelConsumer != null) {
                if(!GetRunningTestsStatus.getRunningTests().isTestRunning(summaryObjectId)){
                    loggerMaker.infoAndAddToDb("Tests have been marked stopped.");
                    executor.shutdownNow();
                    break;
                }
                else if ((Context.now() - startTime >= effectiveMaxRunTime)) {
                    loggerMaker.infoAndAddToDb("Max run time reached. Stopping consumer.");
                    executor.shutdownNow();
                    break;
                }

                int processed = processedRecords.get();
                if (processed > lastProcessedSeen) {
                    lastProcessedSeen = processed;
                    drainIdleSinceMs = -1L;
                }

                long workRemaining = parallelConsumer.workRemaining();
                long nowMs = System.currentTimeMillis();
                int polled = polledRecords.get();
                // Periodic progress only when counts moved (avoids duplicate spam while idle)
                if (nowMs - lastDebugLogMs >= DEBUG_PROGRESS_LOG_INTERVAL_MS
                        && (polled != lastLoggedPolled || processed != lastLoggedProcessed)) {
                    int left = expectedRecords > 0 ? Math.max(0, expectedRecords - processed) : -1;
                    debugLogToDb(accountId, "polled=" + polled + " executed=" + processed
                            + " expected=" + expectedRecords + " left=" + left
                            + " workRemaining=" + workRemaining);
                    lastDebugLogMs = nowMs;
                    lastLoggedPolled = polled;
                    lastLoggedProcessed = processed;
                }

                boolean locallyEmpty = firstRecordRead.get() && workRemaining == 0;
                if (locallyEmpty) {
                    if (expectedRecords > 0 && processed >= expectedRecords) {
                        int remainingTime = Math.min(Math.max(0, effectiveMaxRunTime - (Context.now() - startTime)), maxRunTimeForTests);
                        loggerMaker.insertImportantTestingLog(
                                "All expected records processed: " + processed + "/" + expectedRecords
                                        + " (workRemaining=0)");
                        executor.shutdown();
                        executor.awaitTermination(remainingTime, TimeUnit.SECONDS);
                        break;
                    }

                    if (drainIdleSinceMs < 0) {
                        drainIdleSinceMs = nowMs;
                    } else if (nowMs - drainIdleSinceMs >= DRAIN_IDLE_GRACE_MS) {
                        int remainingTime = Math.min(Math.max(0, effectiveMaxRunTime - (Context.now() - startTime)), maxRunTimeForTests);
                        loggerMaker.insertImportantTestingLog(
                                "No progress for " + DRAIN_IDLE_GRACE_MS + "ms with incomplete records: "
                                        + processed + "/" + expectedRecords
                                        + " (workRemaining=0). Completing consumer.");
                        executor.shutdown();
                        executor.awaitTermination(remainingTime, TimeUnit.SECONDS);
                        break;
                    }
                } else {
                    drainIdleSinceMs = -1L;
                }
                Thread.sleep(100);
            }

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in polling records");
        }finally{
            int finalPolled = polledRecords.get();
            int finalExecuted = processedRecords.get();
            int finalLeft = expectedRecords > 0 ? Math.max(0, expectedRecords - finalExecuted) : -1;
            debugLogToDb(accountId, "consumer closing polled=" + finalPolled
                    + " executed=" + finalExecuted + " expected=" + expectedRecords + " left=" + finalLeft);
            loggerMaker.infoAndAddToDb("Closing consumer as all results have been executed.");

            flushLastTestedUpdates();

            if(parallelConsumer != null){
                parallelConsumer.closeDrainFirst();
            }
            parallelConsumer = null;
            consumer.close();
            Producer.deleteTestResultsTopic();
            writeJsonContentInFile(Constants.TESTING_STATE_FOLDER_PATH, Constants.TESTING_STATE_FILE_NAME, null);
        }
    }
}
