package com.akto.testing;

import com.akto.dao.AccountsDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingOriginalMessageDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dto.Account;
import com.akto.dto.ApiInfo;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingOriginalMessage;
import com.akto.dto.testing.TestingRunResult;
import com.akto.log.LoggerMaker;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import org.apache.commons.lang3.tuple.Triple;
import org.bson.types.ObjectId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.akto.dao.MCollection.clients;

public class OptimizeStorageCron {

    private static final LoggerMaker logger = new LoggerMaker(OptimizeStorageCron.class);

    public void init(){
        List<Account> accountList = AccountsDao.instance.findAll(new BasicDBObject(), new BasicDBObject("_id", 1));
        for (Account account : accountList) {
            try {
                logger.infoAndAddToDb("Starting optimize storage for account: " + account.getId(), LoggerMaker.LogDb.TESTING);
                int skip = 0;
                int limit = 10000;
                Context.accountId.set(account.getId());
                List<TestingRunResult> testingRunResults = TestingRunResultDao.instance.findAll(new BasicDBObject(), skip, limit, new BasicDBObject("_id", -1));
                while (!testingRunResults.isEmpty()) {
                    logger.infoAndAddToDb("Processing testing run results from: " + skip + " to: " + (skip + limit) + " for account: " + account.getId(), LoggerMaker.LogDb.TESTING);
                    Map<String, Triple<ApiInfo.ApiInfoKey, ObjectId, String>> urlToOriginalMessageMap = new HashMap<>();
                    for (TestingRunResult testingRunResult : testingRunResults) {
                        ApiInfo.ApiInfoKey apiInfoKey = testingRunResult.getApiInfoKey();
                        ObjectId testingRunResultSummaryId = testingRunResult.getTestRunResultSummaryId();
                        List<TestResult> testResults = testingRunResult.getTestResults();
                        if (testResults.isEmpty()) {
                            logger.infoAndAddToDb("No test result found for testing run result: " + testingRunResult.getId(), LoggerMaker.LogDb.TESTING);
                            continue;
                        }
                        int fixedCount = 0;
                        int notFixedCount = 0;
                        for (TestResult testResult : testResults) {
                            if (testResult.getOriginalMessage() != null) {
                                notFixedCount++;
                                testResult.setOriginalMessage(null);
                                urlToOriginalMessageMap.putIfAbsent(apiInfoKey.getUrl(), Triple.of(apiInfoKey, testingRunResultSummaryId, testResult.getOriginalMessage()));
                            } else {
                                fixedCount++;
                            }
                        }
                        testingRunResult.setTestResults(testResults);
                        if (notFixedCount > 0) {
//                            logger.infoAndAddToDb("Fixed: " + fixedCount + " not fixed: " + notFixedCount + " for testing run result: " + testingRunResult.getId(), LoggerMaker.LogDb.TESTING);
                            TestingRunResultDao.instance.replaceOne(Filters.eq("_id", testingRunResult.getId()), testingRunResult);
                        }
                    }
                    if (!urlToOriginalMessageMap.isEmpty()) {
                        for (Map.Entry<String, Triple<ApiInfo.ApiInfoKey, ObjectId, String>> entry : urlToOriginalMessageMap.entrySet()) {
                            Triple<ApiInfo.ApiInfoKey, ObjectId, String> triple = entry.getValue();
                            ApiInfo.ApiInfoKey apiInfoKey = triple.getLeft();
                            ObjectId testingRunResultSummaryId = triple.getMiddle();
                            String originalMessage = triple.getRight();
                            TestingOriginalMessage testingOriginalMessage = TestingOriginalMessageDao.instance.findOne(Filters.and(Filters.eq(TestingOriginalMessage.API_INFO_KEY, apiInfoKey), Filters.eq(TestingOriginalMessage.TESTING_RUN_RESULT_SUMMARY_ID, testingRunResultSummaryId)));
                            if (testingOriginalMessage != null) {
//                                logger.infoAndAddToDb("Original message already exists for url: " + apiInfoKey.getUrl() + " method: " + apiInfoKey.getMethod() + " testingRunResultSummaryId: " + testingRunResultSummaryId, LoggerMaker.LogDb.TESTING);
                                continue;
                            }
//                            logger.infoAndAddToDb("Inserting original message for url: " + apiInfoKey.getUrl() + " method: " + apiInfoKey.getMethod() + " testingRunResultSummaryId: " + testingRunResultSummaryId, LoggerMaker.LogDb.TESTING);
                            testingOriginalMessage = new TestingOriginalMessage();
                            testingOriginalMessage.setOriginalMessage(originalMessage);
                            testingOriginalMessage.setApiInfoKey(apiInfoKey);
                            testingOriginalMessage.setTestingRunResultSummaryId(testingRunResultSummaryId);
                            TestingOriginalMessageDao.instance.insertOne(testingOriginalMessage);
                        }
                    }
                    skip = skip + limit;
                    testingRunResults = TestingRunResultDao.instance.findAll(new BasicDBObject(), skip, limit, new BasicDBObject("_id", 1));
                }
                int accountId = account.getId();
                logger.infoAndAddToDb("Starting compact for account: " + accountId, LoggerMaker.LogDb.TESTING);
                int now = Context.now();
                clients[0].getDatabase(String.valueOf(accountId)).runCommand(new BasicDBObject("compact", "testing_run_result"));
                int compactTime = Context.now() - now;
                logger.infoAndAddToDb("Finished optimizing storage, compact time for account: " + accountId + " is: " + compactTime, LoggerMaker.LogDb.TESTING);
            } catch(Exception e) {
                e.printStackTrace();
                logger.errorAndAddToDb("Error while optimizing storage for account: " + account.getId(), LoggerMaker.LogDb.TESTING);
            }
        }
    }
}
