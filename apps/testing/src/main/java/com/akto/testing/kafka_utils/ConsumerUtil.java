package com.akto.testing.kafka_utils;

import static com.akto.testing.Utils.readJsonContentFromFile;
import static com.akto.testing.Utils.writeJsonContentInFile;

import com.akto.DaoInit;
import com.akto.crons.GetRunningTestsStatus;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.testing.TestResult.TestError;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.TestingRunResultSummary;
import com.akto.dto.testing.info.SingleTestPayload;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.testing.TestExecutor;
import com.akto.testing.Utils;
import com.akto.util.Constants;
import com.akto.util.DashboardMode;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import io.confluent.parallelconsumer.ParallelConsumerOptions;
import io.confluent.parallelconsumer.ParallelStreamProcessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.bson.types.ObjectId;

public class ConsumerUtil {

    static Properties properties = com.akto.runtime.utils.Utils.configProperties(Constants.LOCAL_KAFKA_BROKER_URL, Constants.AKTO_KAFKA_GROUP_ID_CONFIG, Constants.AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG);
    static{
        properties.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 10000); 
    }
    private static Consumer<String, String> consumer = Constants.IS_NEW_TESTING_ENABLED ? new KafkaConsumer<>(properties) : null; 
    private static final LoggerMaker logger = new LoggerMaker(ConsumerUtil.class, LogDb.TESTING);
    public static ExecutorService executor = Executors.newFixedThreadPool(100);

    private final int maxRunTimeForTests = 4 * 60;

    public void initializeConsumer() {
        String mongoURI = System.getenv("AKTO_MONGO_CONN");
        ReadPreference readPreference = ReadPreference.secondary();
        if(DashboardMode.isOnPremDeployment()){
            readPreference = ReadPreference.primary();
        }
        WriteConcern writeConcern = WriteConcern.W1;
        DaoInit.init(new ConnectionString(mongoURI), readPreference, writeConcern);
    }

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
            logger.info("Running test for: " + apiInfoKey + " with subcategory: " + subCategory);
            String sample = messagesList.get(messagesList.size() - 1);
            TestingRunResult runResult = executor.runTestNew(apiInfoKey, singleTestPayload.getTestingRunId(), instance.getTestingUtil(), singleTestPayload.getTestingRunResultSummaryId(),testConfig , instance.getTestingRunConfig(), instance.isDebug(), singleTestPayload.getTestLogs(), sample);
            executor.insertResultsAndMakeIssues(Collections.singletonList(runResult), singleTestPayload.getTestingRunResultSummaryId());
            
            // update the last tested field in the api info
            ApiInfoDao.instance.updateManyNoUpsert(ApiInfoDao.getFilter(apiInfoKey),
                Updates.set(ApiInfo.LAST_TESTED, Context.now())
            );
        }
    }

    private void createTimedOutResultFromMessage(String message){
        SingleTestPayload singleTestPayload = parseTestMessage(message);
        Context.accountId.set(singleTestPayload.getAccountId());

        String subCategory = singleTestPayload.getSubcategory();
        TestConfig testConfig = TestingConfigurations.getInstance().getTestConfigMap().get(subCategory);

        String testSuperType = testConfig.getInfo().getCategory().getName();
        String testSubType = testConfig.getInfo().getSubCategory();

        TestingRunResult runResult = Utils.generateFailedRunResultForMessage(singleTestPayload.getTestingRunId(), singleTestPayload.getApiInfoKey(), testSuperType, testSubType, singleTestPayload.getTestingRunResultSummaryId(), new ArrayList<>(),  TestError.TEST_TIMED_OUT.getMessage());
        TestExecutor.trim(runResult);
        TestingRunResultSummariesDao.instance.getMCollection().withWriteConcern(WriteConcern.W1).findOneAndUpdate(
            Filters.eq(Constants.ID, singleTestPayload.getTestingRunResultSummaryId()),
            Updates.inc(TestingRunResultSummary.TEST_RESULTS_COUNT, 1)
        );
        TestingRunResultDao.instance.insertOne(runResult);
    }
    
    public void init(int maxRunTimeInSeconds) {
        TestingConfigurations instance = TestingConfigurations.getInstance();
        executor = Executors.newFixedThreadPool(instance.getMaxConcurrentRequest());
        BasicDBObject currentTestInfo = readJsonContentFromFile(Constants.TESTING_STATE_FOLDER_PATH, Constants.TESTING_STATE_FILE_NAME, BasicDBObject.class);
        final String summaryIdForTest = currentTestInfo.getString("summaryId");
        final ObjectId summaryObjectId = new ObjectId(summaryIdForTest);
        final int startTime = Context.now();
        boolean isConsumerRunning = false;
        AtomicBoolean firstRecordRead = new AtomicBoolean(false);
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
                .maxConcurrency(instance.getMaxConcurrentRequest()) // Number of threads for parallel processing
                .commitMode(ParallelConsumerOptions.CommitMode.PERIODIC_CONSUMER_SYNC) // Commit offsets synchronously
                .batchSize(1) // Number of records to process in each poll
                .maxFailureHistory(3)
                .build();

            parallelConsumer = ParallelStreamProcessor.createEosStreamProcessor(options);
            parallelConsumer.subscribe(Arrays.asList(topicName)); 
        }

        try {
            parallelConsumer.poll(record -> {
                String threadName = Thread.currentThread().getName();
                String message = record.value();
                logger.debug("Thread [" + threadName + "] picked up record: " + message);
                try {
                    if(!executor.isShutdown()){
                        Future<?> future = executor.submit(() -> runTestFromMessage(message));
                        firstRecordRead.set(true);
                        try {
                            future.get(maxRunTimeForTests, TimeUnit.SECONDS); 
                        } catch (InterruptedException | TimeoutException f) {
                            logger.error("Task timed out: "+  message);
                            future.cancel(true);
                            createTimedOutResultFromMessage(message);
                        }catch(RejectedExecutionException e){
                            future.cancel(true);
                        } 
                        catch (Exception e) {
                            future.cancel(true);
                            logger.error("Error in task execution: " + message, e);
                        }
                    }
                    
                } finally {
                    logger.debug("Thread [" + threadName + "] finished processing record: " + message);
                }
            });

            while (parallelConsumer != null) {
                if(!GetRunningTestsStatus.getRunningTests().isTestRunning(summaryObjectId, true)){
                    logger.info("Tests have been marked stopped.");
                    executor.shutdownNow();
                    break;
                }
                else if ((Context.now() - startTime > maxRunTimeInSeconds)) {
                    logger.info("Max run time reached. Stopping consumer.");
                    executor.shutdownNow();
                    break;
                }else if(firstRecordRead.get() && parallelConsumer.workRemaining() == 0){
                    int timeConsumed = Context.now() - startTime;
                    int timeLeft = Math.min(Math.abs(maxRunTimeInSeconds - timeConsumed), maxRunTimeForTests);
                    logger.info("Records are empty now, thus executing final tests");
                    executor.shutdown();
                    executor.awaitTermination(timeLeft, TimeUnit.SECONDS);
                    break;
                }
                Thread.sleep(100);
            }

        } catch (Exception e) {
            logger.error("Error in polling records");
        }finally{
            logger.info("Closing consumer as all results have been executed.");
            parallelConsumer.closeDrainFirst();
            parallelConsumer = null;
            consumer.close();
            writeJsonContentInFile(Constants.TESTING_STATE_FOLDER_PATH, Constants.TESTING_STATE_FILE_NAME, null);
        }
    }
}
