package com.akto.testing.kafka_utils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.bson.conversions.Bson;

import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.VulnerableTestingRunResultDao;
import com.akto.dto.testing.TestingRunResult;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.testing_issues.TestingIssuesHandler;
import com.akto.util.Constants;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.WriteConcern;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;

public class InsertResultsConsumer {

    public void initializeConsumer(){
        ConsumerUtil.initializeConsumer();
        Producer.createTopicWithRetries(Constants.LOCAL_KAFKA_BROKER_URL, Constants.TEST_RESULTS_FOR_INSERTION_TOPIC_NAME);
    }

    TestingIssuesHandler  issuesHandler = new TestingIssuesHandler();
    static Properties properties = com.akto.runtime.utils.Utils.configProperties(Constants.LOCAL_KAFKA_BROKER_URL, Constants.AKTO_KAFKA_INSERTION_GROUP_ID_CONFIG, Constants.AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG);
    static{
        properties.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 1000); 
    }
    private static Consumer<String, String> consumer = new KafkaConsumer<>(properties);
    private final static ObjectMapper mapper = new ObjectMapper();
    private static final LoggerMaker loggerMaker = new LoggerMaker(InsertResultsConsumer.class);
    
    private boolean insertResultsAndMakeIssuesInBatch(List<TestingRunResult> runResults) {
        // this is the batched run results, by default it is 100
        try {
            ArrayList<WriteModel<TestingRunResult>> insertTRR = new ArrayList<>();
            ArrayList<WriteModel<TestingRunResult>> insertVulnerableTRR = new ArrayList<>();
            for (TestingRunResult runResult : runResults) {
                Bson filter = Filters.and(
                    Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, runResult.getTestRunResultSummaryId()),
                    Filters.eq(TestingRunResult.API_INFO_KEY, runResult.getApiInfoKey()),
                    Filters.eq(TestingRunResult.TEST_SUB_TYPE, runResult.getTestSubType())
                );
                
                UpdateOneModel<TestingRunResult> trrModel = new UpdateOneModel<>(filter,TestingRunResult.buildFullUpdate(runResult));
                if (runResult != null && runResult.isVulnerable()) {
                    insertVulnerableTRR.add(trrModel);

                }
                insertTRR.add(trrModel);
            }
            
            TestingRunResultDao.instance.getMCollection().withWriteConcern(WriteConcern.W1).bulkWrite(insertTRR);
            VulnerableTestingRunResultDao.instance.getMCollection().withWriteConcern(WriteConcern.W1).bulkWrite(insertVulnerableTRR);

            issuesHandler.handleIssuesCreationFromTestingRunResults(runResults, false);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void insertResultsWithRetries(List<TestingRunResult> runResults) {
        int retryCount = 3;
        boolean success = true;
        while (retryCount > 0) {
            try {
                int subListSize = (int) Math.pow(10, retryCount - 1);
                int total = (runResults.size() + subListSize - 1) / subListSize ;
                for (int i = 0; i < total; i++) {
                    int start = i * subListSize;
                    int end = Math.min(start + subListSize, runResults.size());
                    List<TestingRunResult> subList = runResults.subList(start, end);
                    boolean val = insertResultsAndMakeIssuesInBatch(subList);
                    success = success && val;
                }
                if(success) return;
            } catch (Exception e) {
                e.printStackTrace();
                retryCount--;
                loggerMaker.errorAndAddToDb("Error inserting results, retrying... " + e.getMessage(), LogDb.TESTING);
            }
        }
    }

    public void run() {
        consumer.subscribe(Collections.singletonList(Constants.TEST_RESULTS_FOR_INSERTION_TOPIC_NAME));
        List<TestingRunResult> runResults = new ArrayList<>();
        AtomicInteger totalRecords = new AtomicInteger(0);
        try {
            while (true) {
                try {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                    if (records.isEmpty() && totalRecords.get() > 0) {
                        insertResultsWithRetries(runResults);
                        totalRecords.set(0);
                        runResults.clear();
                        continue;
                    }
                    for (ConsumerRecord<String,String> record : records) {
                        String message = record.value();
                        Object object = mapper.readValue(message, Object.class);
                        TestingRunResult runResult = mapper.convertValue(object, TestingRunResult.class);
                        if (runResult == null) {
                            continue;
                        }
                        runResults.add(runResult);
                        totalRecords.incrementAndGet();
                    }
                    if(totalRecords.get() >= 100){
                        insertResultsWithRetries(runResults);
                        runResults.clear();
                        consumer.commitSync();
                        totalRecords.set(0);
                    }   
                } catch (Exception e) {
                    e.printStackTrace();
                    loggerMaker.errorAndAddToDb("Error in consumer: " + e.getMessage(), LogDb.TESTING);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            consumer.wakeup();
        }
    }

    public static void close() {
        consumer.close();
    }
}
