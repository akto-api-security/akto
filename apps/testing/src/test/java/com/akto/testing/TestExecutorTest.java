package com.akto.testing;

import com.akto.MongoBasedTest;
import com.akto.crons.GetRunningTestsStatus;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.RawApi;
import com.akto.dto.testing.GenericTestResult;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.URLMethods;
import com.akto.store.SampleMessageStore;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.junit.Test;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestExecutorTest extends MongoBasedTest {

    private TestResult generateTestResult(boolean bigPayload) {
        String message = bigPayload ? StringUtils.repeat("A", 3_000_000) : "something small";
        String originalMessage = bigPayload ? StringUtils.repeat("B", 1_000_000) : "something small";
        return new TestResult(
                message, originalMessage, new ArrayList<>(), 100, false, TestResult.Confidence.LOW, null
        );
    }

    @Test
    public void testTrim() {
        List<GenericTestResult> testResultList = new ArrayList<>();
        testResultList.add(generateTestResult(false));
        testResultList.add(generateTestResult(false));
        testResultList.add(generateTestResult(false));
        testResultList.add(generateTestResult(true));
        testResultList.add(generateTestResult(false));
        testResultList.add(generateTestResult(true));
        testResultList.add(generateTestResult(false));
        TestingRunResult testingRunResult = new TestingRunResult(
                new ObjectId(), new ApiInfo.ApiInfoKey(0, "url", URLMethods.Method.GET), "BOLA",
                "REPLACE_AUTH_TOKEN", testResultList ,true, new ArrayList<>(), 90, 0, 100, new ObjectId(), null, new ArrayList<>()
        );
        TestExecutor.trim(testingRunResult);
        assertEquals(5, testingRunResult.getTestResults().size());
    }

    private void testFindHostUtil(String url, String answer, String hostName) throws URISyntaxException {
        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(0, url, URLMethods.Method.GET);
        String message = String.format("{\"path\": \"%s\", \"method\": \"POST\", \"type\": \"HTTP/1.1\", \"requestHeaders\": \"{\\\"host\\\": \\\"%s\\\", \\\"Authorization\\\": \\\"Basic somerandom=\\\", \\\"X-Killbill-ApiSecret\\\": \\\"something\\\", \\\"Accept\\\": \\\"application/json\\\", \\\"X-MPL-COUNTRYCODE\\\": \\\"IN\\\", \\\"X-Killbill-CreatedBy\\\": \\\"test-payment\\\", \\\"Content-type\\\": \\\"application/json\\\"}\", \"requestPayload\": \"{}\", \"statusCode\": \"200\", \"responseHeaders\": \"{\\\"Date\\\": \\\"Mon, 18 Apr 2022 13:05:16 GMT\\\", \\\"Content-Type\\\": \\\"application/json\\\", \\\"Transfer-Encoding\\\": \\\"chunked\\\", \\\"Connection\\\": \\\"keep-alive\\\", \\\"Server\\\": \\\"Apache-Coyote/1.1\\\", \\\"Access-Control-Allow-Origin\\\": \\\"*\\\", \\\"Access-Control-Allow-Methods\\\": \\\"GET, POST, DELETE, PUT, OPTIONS\\\", \\\"Access-Control-Allow-Headers\\\": \\\"Authorization,Content-Type,Location,X-Killbill-ApiKey,X-Killbill-ApiSecret,X-Killbill-Comment,X-Killbill-CreatedBy,X-Killbill-Pagination-CurrentOffset,X-Killbill-Pagination-MaxNbRecords,X-Killbill-Pagination-NextOffset,X-Killbill-Pagination-NextPageUri,X-Killbill-Pagination-TotalNbRecords,X-Killbill-Reason\\\", \\\"Access-Control-Expose-Headers\\\": \\\"Authorization,Content-Type,Location,X-Killbill-ApiKey,X-Killbill-ApiSecret,X-Killbill-Comment,X-Killbill-CreatedBy,X-Killbill-Pagination-CurrentOffset,X-Killbill-Pagination-MaxNbRecords,X-Killbill-Pagination-NextOffset,X-Killbill-Pagination-NextPageUri,X-Killbill-Pagination-TotalNbRecords,X-Killbill-Reason\\\", \\\"Access-Control-Allow-Credentials\\\": \\\"true\\\"}\", \"status\": \"OK\", \"responsePayload\": \"aaaaa\", \"ip\": \"\", \"time\": \"1650287116\", \"akto_account_id\": \"1000000\", \"akto_vxlan_id\": 123, \"source\": \"OTHER\"}", url, hostName);
        List<String> messages = Collections.singletonList(message);
        SampleData data = new SampleData();
        data.setSamples(messages);
        Key key = new Key(apiInfoKey.getApiCollectionId(), apiInfoKey.getUrl(), apiInfoKey.getMethod(), 200, Context.now(),Context.now());
        data.setId(key);
        SampleDataDao.instance.insertOne(data);
        SampleMessageStore messageStore = SampleMessageStore.create();
        Set<Integer> apiCollectionSet = new HashSet<>();
        apiCollectionSet.add(0);
        messageStore.fetchSampleMessages(apiCollectionSet);
        OriginalHttpRequest request = TestExecutor.findOriginalHttpRequest(apiInfoKey, messageStore.getSampleDataMap(),
                messageStore);
        String host = TestExecutor.findHostFromOriginalHttpRequest(request);
        assertEquals(answer, host);
    }

    @Test
    public void testFindHost() throws URISyntaxException {
        testFindHostUtil("/api/books", "https://akto.io","akto.io");

        testFindHostUtil("https://akto.io/api/books", "https://akto.io", "akto.io");
        testFindHostUtil("http://akto.io/api/books", "http://akto.io", "akto.io");

        testFindHostUtil("https://akto.io:8080/api/books", "https://akto.io:8080","akto.io");
        testFindHostUtil("http://akto.io:8080/api/books", "http://akto.io:8080","akto.io");

        testFindHostUtil("https://docs.akto.io/readme", "https://docs.akto.io","docs.akto.io");
        testFindHostUtil("https://docs.akto.io:8080/readme", "https://docs.akto.io:8080","docs.akto.io");
        testFindHostUtil("http://docs.akto.io/readme", "http://docs.akto.io","docs.akto.io");
    }

    @Test
    public void testFilterJsonRpcPayload() throws Exception {
        TestExecutor testExecutor = new TestExecutor();

        // Case 1: Valid JSON-RPC 2.0 payload with method name
        RawApi rawApi = new RawApi();
        rawApi.setRequest(new OriginalHttpRequest());
        rawApi.getRequest().setBody("{\"jsonrpc\": \"2.0\", \"method\": \"testMethod\", \"params\": {}}");
        rawApi.getRequest().setUrl("http://example.com/testMethod");
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("content-type", Collections.singletonList("application/json"));
        rawApi.getRequest().setHeaders(headers);
        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(0, "http://example.com/testMethod", URLMethods.Method.POST);

        boolean result = testExecutor.filterJsonRpcPayload(rawApi, apiInfoKey);
        assertEquals(true, result);
        assertEquals("http://example.com", rawApi.getRequest().getUrl());

        // Case 2: Invalid JSON-RPC version
        rawApi.getRequest().setBody("{\"jsonrpc\": \"1.0\", \"method\": \"testMethod\", \"params\": {}}");
        result = testExecutor.filterJsonRpcPayload(rawApi, apiInfoKey);
        assertEquals(false, result);

        // Case 3: Missing method name in the payload
        rawApi.getRequest().setBody("{\"jsonrpc\": \"2.0\", \"params\": {}}");
        result = testExecutor.filterJsonRpcPayload(rawApi, apiInfoKey);
        assertEquals(false, result);

        // Case 4: URL does not contain the method name
        rawApi.getRequest().setBody("{\"jsonrpc\": \"2.0\", \"method\": \"anotherMethod\", \"params\": {}}");
        rawApi.getRequest().setUrl("http://example.com/testMethod");
        apiInfoKey.setUrl("http://example.com/testMethod");
        result = testExecutor.filterJsonRpcPayload(rawApi, apiInfoKey);
        assertEquals(true, result);
    }

    @Test
    public void testNoInfiniteLoopWhenTestsFinish() throws Exception {
        // Setup inputs
        AtomicInteger totalTestsToBeExecuted = new AtomicInteger(0);
        int totalTestsToBeExecutedCount = 10;
        long maxRunTime = 60 * 1000; // 1 min max wait

        CountDownLatch latch = new CountDownLatch(1);

        AtomicInteger tempVal = new AtomicInteger(1);

        // Set up the thread to simulate the actual logic
        Thread thread = new Thread(() -> {
            try {
                int waitTs = Context.now();
                int prevCalcTime = Context.now();
                int lastCheckedCount = 0;
                tempVal.set(2);
                while (latch.getCount() > 0 && (Context.now() - waitTs < maxRunTime)) {
                    tempVal.set(3);
                    if (lastCheckedCount != totalTestsToBeExecuted.get()) {
                        lastCheckedCount = totalTestsToBeExecuted.get();
                        prevCalcTime = Context.now();
                    } else {
                        int relaxingTime = Utils.getRelaxingTimeForTests(totalTestsToBeExecuted, totalTestsToBeExecutedCount);
                        if (relaxingTime == 0 || (Context.now() - prevCalcTime > relaxingTime)) {
                            break;
                        }
                    }
                    Thread.sleep(100); // shorter sleep for faster test
                }
            } catch (InterruptedException e) {
                fail("Thread interrupted unexpectedly");
            }
        });

        thread.start();
        thread.join(); // wait for the loop to finish

        // If the test reaches this point without timeout, it's a pass
        assertEquals(3, tempVal.get());
    }
}

