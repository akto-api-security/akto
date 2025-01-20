package com.akto.testing.testing_with_kafka;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.runtime.utils.Utils;
import com.akto.testing.TestExecutor;
import com.akto.util.Constants;
import com.akto.util.DashboardMode;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mongodb.ConnectionString;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;

public class TestingConsumer {

    static Properties properties = com.akto.runtime.utils.Utils.configProperties(Constants.LOCAL_KAFKA_BROKER_URL, Constants.AKTO_KAFKA_GROUP_ID_CONFIG, Constants.AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG);
    private static Consumer<String, String> consumer = new KafkaConsumer<>(properties); 
    final private TestExecutor testExecutor = new TestExecutor();

    private static final LoggerMaker loggerMaker = new LoggerMaker(TestingConsumer.class);
    private static final Logger logger = LoggerFactory.getLogger(TestingConsumer.class);
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(100);

    private void initializeConsumer() {
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

    public Void runTestFromMessage(String message){
        TestMessages testMessages = parseTestMessage(message);
        Context.accountId.set(testMessages.getAccountId());

        CommonSingletonForTesting instance = CommonSingletonForTesting.getInstance();
        String subCategory = testMessages.getSubcategory();
        TestConfig testConfig = instance.getTestConfigMap().get(subCategory);
        ApiInfoKey apiInfoKey = testMessages.getApiInfoKey();

        List<String> messagesList = instance.getTestingUtil().getSampleMessages().get(apiInfoKey);
        if(messagesList == null || messagesList.isEmpty()){
            return null;
        }else{
            String sample = messagesList.get(messagesList.size() - 1);
            logger.info("Running test for: " + apiInfoKey + " with subcategory: " + subCategory);
            TestingRunResult runResult = testExecutor.runTestNew(apiInfoKey, testMessages.getTestingRunId(), instance.getTestingUtil(), testMessages.getTestingRunResultSummaryId(),testConfig , instance.getTestingRunConfig(), instance.isDebug(), testMessages.getTestLogs(), sample);
            testExecutor.insertResultsAndMakeIssues(Arrays.asList(runResult), testMessages.getTestingRunResultSummaryId());
            return null;
        }
    }
    public void init(){
        initializeConsumer();
        consumer.wakeup();
        
        String topicName = Constants.TEST_RESULTS_TOPIC_NAME;
        final AtomicBoolean exceptionOnCommitSync = new AtomicBoolean(false);

        List<Future<Void>> futures = new ArrayList<>();

        long lastSyncOffset = 0;
        try {
            consumer.subscribe(Arrays.asList(topicName));
            loggerMaker.infoAndAddToDb("Consumer subscribed", LogDb.RUNTIME);

            // poll for new data here 

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(10000));
                if(records.isEmpty()){
                    logger.info("Returning as no records were found, so now complete the test.");
                    consumer.close();
                    return;
                }
                try {
                    consumer.commitSync();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                for (ConsumerRecord<String, String> record : records) {
                    lastSyncOffset++;
                    loggerMaker.infoAndAddToDb("Reading message in testing consumer: " + lastSyncOffset, LogDb.TESTING);
                    if (lastSyncOffset % 100 == 0) {
                        logger.info("Committing offset at position: " + lastSyncOffset);
                    }

                    Future<Void> future = threadPool.submit(() -> runTestFromMessage(record.value()));
                    futures.add(future);
                }
                
            }

        } catch (WakeupException ignored) {
          // nothing to catch. This exception is called from the shutdown hook.
        } catch (Exception e) {
            exceptionOnCommitSync.set(true);
            Utils.printL(e);
            loggerMaker.errorAndAddToDb("Error in main testing consumer: " + e.getMessage(),LogDb.TESTING);
            e.printStackTrace();
        } finally {
            consumer.close();
        }
    }
}
