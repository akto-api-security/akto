package com.akto.testing;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.kafka.clients.consumer.*;
import org.bson.types.ObjectId;

import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.TestingRunResult;
import com.akto.store.TestingUtil;
import com.akto.util.Constants;

public class TestingConsumer {
    private Consumer<String, String> consumer;
    final private TestExecutor testExecutor = new TestExecutor();

    public void initializeConsumer() {
        TestingConsumer testingConsumer = new TestingConsumer();

        Properties properties = com.akto.runtime.utils.Utils.configProperties(Constants.LOCAL_KAFKA_BROKER_URL, Constants.AKTO_KAFKA_GROUP_ID_CONFIG, Constants.AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG);
        testingConsumer.consumer = new KafkaConsumer<>(properties);
    }

    
    public Void runAndInsertNewTestResult(ApiInfo.ApiInfoKey apiInfoKey, ObjectId testRunId, TestingUtil testingUtil,
                                       ObjectId testRunResultSummaryId, TestConfig testConfig, TestingRunConfig testingRunConfig, boolean debug, List<TestingRunResult.TestLog> testLogs, String message, int accountId){
        Context.accountId.set(accountId);
        TestingRunResult testingRunResult = testExecutor.runTestNew(apiInfoKey, testRunId, testingUtil, testRunResultSummaryId, testConfig, testingRunConfig, debug, testLogs, message, accountId);
        testExecutor.insertResultsAndMakeIssues(Arrays.asList(testingRunResult), testRunResultSummaryId);
        return null;
    }
}
