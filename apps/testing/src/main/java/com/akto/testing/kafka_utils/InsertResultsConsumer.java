package com.akto.testing.kafka_utils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.bson.conversions.Bson;

import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.VulnerableTestingRunResultDao;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.info.SingleTestResultPayload;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.testing_issues.TestingIssuesHandler;
import com.akto.util.Constants;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
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
    private static final LoggerMaker loggerMaker = new LoggerMaker(InsertResultsConsumer.class, LogDb.TESTING);
    
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
                
                UpdateOneModel<TestingRunResult> trrModel = new UpdateOneModel<>(filter,TestingRunResult.buildFullUpdate(runResult), new UpdateOptions().upsert(true));
                if (runResult != null && runResult.isVulnerable()) {
                    insertVulnerableTRR.add(trrModel);

                }
                insertTRR.add(trrModel);
            }
            loggerMaker.infoAndAddToDb("Inserting results in batch for accountId: " + Context.accountId.get() + ", size: " + insertTRR.size());  
            if(insertTRR.size() == 0){
                return true;
            }
            TestingRunResultDao.instance.getMCollection().bulkWrite(insertTRR, new BulkWriteOptions().ordered(false));
            if(!insertVulnerableTRR.isEmpty()){
                VulnerableTestingRunResultDao.instance.getMCollection().bulkWrite(insertVulnerableTRR, new BulkWriteOptions().ordered(false));
            }
            issuesHandler.handleIssuesCreationFromTestingRunResults(runResults, false);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void insertResultsWithRetries(List<TestingRunResult> runResults) {
        int retryCount = 3;
        List<TestingRunResult> resultsToBeInserted = new ArrayList<>();
        while (retryCount > 0) {
            int subListSize = (int) Math.pow(10, retryCount - 1);
            int total = (runResults.size() + subListSize - 1) / subListSize ;
            loggerMaker.infoAndAddToDb("Inserting results in batches of size: " + subListSize + ", total batches: " + total);
            for (int i = 0; i < total; i++) {
                int start = i * subListSize;
                int end = Math.min(start + subListSize, runResults.size());
                List<TestingRunResult> subList = runResults.subList(start, end);
                loggerMaker.infoAndAddToDb("Inserting results for accountId: " + Context.accountId.get() + ", size: " + subList.size());
                boolean val = insertResultsAndMakeIssuesInBatch(subList);
                if(!val){
                    resultsToBeInserted.addAll(subList);
                }else{
                    return;
                }
            }
            if(resultsToBeInserted.size() > 0){
                loggerMaker.errorAndAddToDb("Error inserting results, retrying... " + resultsToBeInserted.size() + " results failed to insert");
                runResults = new ArrayList<>(resultsToBeInserted);
                resultsToBeInserted.clear();
                retryCount--;
            } else {
                return ;
            }
        }
    }

    private void insertResultsTotal(Map<Integer,List<TestingRunResult>> runResultsMap) {
        if (runResultsMap.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, List<TestingRunResult>> entry : runResultsMap.entrySet()) {
            int accountId = entry.getKey();
            Context.accountId.set(accountId);
            List<TestingRunResult> runResults = entry.getValue();
            loggerMaker.infoAndAddToDb("Inserting results for accountId: " + Context.accountId.get() + ", size: " + runResults.size());
            insertResultsWithRetries(runResults);
        }
    }

    public void run() {
        List<TestingRunResult> emptyList = new ArrayList<>();
        consumer.subscribe(Collections.singletonList(Constants.TEST_RESULTS_FOR_INSERTION_TOPIC_NAME));
        int count = 0;
        AtomicInteger counter = new AtomicInteger(0);
        Map<Integer,List<TestingRunResult>> runResultsMap = new HashMap<>();
        try {
            loggerMaker.infoAndAddToDb("Fetching records for insertion records");
            while (true) {
                try {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                    if (records.isEmpty() && !runResultsMap.isEmpty()) {
                        insertResultsTotal(runResultsMap);
                        consumer.commitSync();
                        runResultsMap.clear();
                        continue;
                    }
                    for (ConsumerRecord<String,String> record : records) {
                        String message = record.value();
                        SingleTestResultPayload payload = SingleTestResultPayload.getTestingRunResultFromMessage(message);
                        loggerMaker.info("Got message number: " + counter.get());
                        if (payload.getTestingRunResult() == null) {
                            loggerMaker.errorAndAddToDb("Error in consumer: runResult is null");
                            continue;
                        }else{
                            int accountId = payload.getAccountId();
                            List<TestingRunResult> runResults = runResultsMap.getOrDefault(accountId, emptyList);
                            runResults.add(payload.getTestingRunResult());
                            runResultsMap.put(accountId, runResults);
                        }
                        count++;
                        counter.incrementAndGet();
                    }
                    if(count >= 100){
                        insertResultsTotal(runResultsMap);
                        consumer.commitSync();
                        runResultsMap.clear();
                        count = 0;
                    }   
                } catch (Exception e) {
                    e.printStackTrace();
                    loggerMaker.errorAndAddToDb("Error in consumer: " + e.getMessage());
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
