package com.akto.testing;

import com.akto.dao.AuthMechanismsDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.*;
import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.Auth;
import com.akto.dto.test_editor.ConfigParserResult;
import com.akto.dto.test_editor.ExecutorNode;
import com.akto.dto.test_editor.FilterNode;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.testing.*;
import com.akto.dto.testing.TestingRun.State;
import com.akto.dto.type.RequestTemplate;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.rules.*;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;
import com.akto.testing.yaml_tests.YamlTestTemplate;
import com.akto.testing_issues.TestingIssuesHandler;
import com.akto.util.JSONUtils;
import com.akto.util.enums.LoginFlowEnums;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.types.ObjectId;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class TestExecutor {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TestExecutor.class);
    public static long acceptableSizeInBytes = 5_000_000;
    
    public void init(TestingRun testingRun, ObjectId summaryId) {
        if (testingRun.getTestIdConfig() != 1) {
            apiWiseInit(testingRun, summaryId);
        } else {
            workflowInit(testingRun, summaryId);
        }
    }

    public void workflowInit (TestingRun testingRun, ObjectId summaryId) {
        TestingEndpoints testingEndpoints = testingRun.getTestingEndpoints();
        if (!testingEndpoints.getType().equals(TestingEndpoints.Type.WORKFLOW)) {
            loggerMaker.errorAndAddToDb("Invalid workflow type", LogDb.TESTING);
            return;
        }

        WorkflowTestingEndpoints workflowTestingEndpoints = (WorkflowTestingEndpoints) testingEndpoints;
        WorkflowTest workflowTestOld = workflowTestingEndpoints.getWorkflowTest();

        WorkflowTest workflowTest = WorkflowTestsDao.instance.findOne(
                Filters.eq("_id", workflowTestOld.getId())
        );

        if (workflowTest == null) {
            loggerMaker.errorAndAddToDb("Workflow test has been deleted", LogDb.TESTING);
            return ;
        }

        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        try {
            apiWorkflowExecutor.init(workflowTest, testingRun.getId(), summaryId);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while executing workflow test " + e, LogDb.TESTING);
        }

        Map<String, Integer> totalCountIssues = new HashMap<>();
        totalCountIssues.put("HIGH", 0);
        totalCountIssues.put("MEDIUM", 0);
        totalCountIssues.put("LOW", 0);

        TestingRunResultSummariesDao.instance.updateOne(
                Filters.eq("_id", summaryId),
                Updates.combine(
                        Updates.set(TestingRunResultSummary.END_TIMESTAMP, Context.now()),
                        Updates.set(TestingRunResultSummary.STATE, State.COMPLETED),
                        Updates.set(TestingRunResultSummary.COUNT_ISSUES, totalCountIssues)
                )
        );
    }

    private Set<Integer> extractApiCollectionIds(List<ApiInfo.ApiInfoKey> apiInfoKeyList) {
        Set<Integer> ret = new HashSet<>();
        for(ApiInfo.ApiInfoKey apiInfoKey: apiInfoKeyList) {
            ret.add(apiInfoKey.getApiCollectionId());
        }

        return ret;
    }

    public void apiWiseInit(TestingRun testingRun, ObjectId summaryId) {
        int accountId = Context.accountId.get();
        int now = Context.now();
        int maxConcurrentRequests = testingRun.getMaxConcurrentRequests() > 0 ? testingRun.getMaxConcurrentRequests() : 100;
        TestingEndpoints testingEndpoints = testingRun.getTestingEndpoints();

        Map<String, SingleTypeInfo> singleTypeInfoMap = SampleMessageStore.buildSingleTypeInfoMap(testingEndpoints);

        List<ApiInfo.ApiInfoKey> apiInfoKeyList = testingEndpoints.returnApis();
        if (apiInfoKeyList == null || apiInfoKeyList.isEmpty()) return;
        loggerMaker.infoAndAddToDb("APIs found: " + apiInfoKeyList.size(), LogDb.TESTING);


        Set<Integer> apiCollectionIds = extractApiCollectionIds(apiInfoKeyList);

        Map<ApiInfo.ApiInfoKey, List<String>> sampleMessages = SampleMessageStore.fetchSampleMessages(apiCollectionIds);
        List<TestRoles> testRoles = SampleMessageStore.fetchTestRoles();
        AuthMechanism authMechanism = AuthMechanismsDao.instance.findOne(new BasicDBObject());

        List<AuthParam> authParams = authMechanism.getAuthParams();

        Map<String, TestConfig> testConfigMap = YamlTemplateDao.instance.fetchTestConfigMap(false);

        authMechanism.setAuthParams(authParams);

        TestingUtil testingUtil = new TestingUtil(authMechanism, sampleMessages, singleTypeInfoMap, testRoles);

        try {
            LoginFlowResponse loginFlowResponse = triggerLoginFlow(authMechanism, 3);
            if (!loginFlowResponse.getSuccess()) {
                loggerMaker.errorAndAddToDb("login flow failed", LogDb.TESTING);
                throw new Exception("login flow failed");
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e.getMessage(), LogDb.TESTING);
            return;
        }

        Map<ApiInfo.ApiInfoKey, List<String>> sampleDataMapForStatusCodeAnalyser = new HashMap<>();
        Set<ApiInfo.ApiInfoKey> apiInfoKeySet = new HashSet<>(apiInfoKeyList);
        for (ApiInfo.ApiInfoKey apiInfoKey: sampleMessages.keySet()) {
            if (apiInfoKeySet.contains(apiInfoKey)) {
                sampleDataMapForStatusCodeAnalyser.put(apiInfoKey, sampleMessages.get(apiInfoKey));
            }
        }

        try {
            StatusCodeAnalyser.run(sampleDataMapForStatusCodeAnalyser, testingRun.getTestingRunConfig());
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while running status code analyser " + e.getMessage(), LogDb.TESTING);
        }

        loggerMaker.infoAndAddToDb("StatusCodeAnalyser result = " + StatusCodeAnalyser.result, LogDb.TESTING);
        loggerMaker.infoAndAddToDb("StatusCodeAnalyser defaultPayloadsMap = " + StatusCodeAnalyser.defaultPayloadsMap, LogDb.TESTING);

        TestingRunResultSummariesDao.instance.updateOne(
            Filters.eq("_id", summaryId),
            Updates.set(TestingRunResultSummary.TOTAL_APIS, apiInfoKeyList.size()));

        CountDownLatch latch = new CountDownLatch(apiInfoKeyList.size());
        ExecutorService threadPool = Executors.newFixedThreadPool(maxConcurrentRequests);
        List<Future<List<TestingRunResult>>> futureTestingRunResults = new ArrayList<>();
        Map<String, Integer> hostsToApiCollectionMap = new HashMap<>();

        for (ApiInfo.ApiInfoKey apiInfoKey: apiInfoKeyList) {
            try {
                String host = findHost(apiInfoKey, testingUtil.getSampleMessages());
                if (host != null && hostsToApiCollectionMap.get(host) == null) {
                    hostsToApiCollectionMap.put(host, apiInfoKey.getApiCollectionId());
                }
            } catch (URISyntaxException e) {
                loggerMaker.errorAndAddToDb("Error while finding host: " + e, LogDb.TESTING);
            }
            try {
                 Future<List<TestingRunResult>> future = threadPool.submit(
                         () -> startWithLatch(apiInfoKey,
                                 testingRun.getTestIdConfig(),
                                 testingRun.getId(),testingRun.getTestingRunConfig(), testingUtil, summaryId,
                                 accountId, latch, now, testingRun.getTestRunTime(), testConfigMap, testingRun));
                 futureTestingRunResults.add(future);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error in API " + apiInfoKey + " : " + e.getMessage(), LogDb.TESTING);
            }
        }

        loggerMaker.infoAndAddToDb("hostsToApiCollectionMap : " + hostsToApiCollectionMap.keySet(), LogDb.TESTING);
        loggerMaker.infoAndAddToDb("Waiting...", LogDb.TESTING);

        try {
            int maxRunTime = testingRun.getTestRunTime();
            if (maxRunTime < 0) maxRunTime = 30*60; // if nothing specified wait for 30 minutes
            boolean awaitResult = latch.await(maxRunTime, TimeUnit.SECONDS);
            loggerMaker.infoAndAddToDb("Await result: " + awaitResult, LogDb.TESTING);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        loggerMaker.infoAndAddToDb("Finished testing", LogDb.TESTING);

        List<TestingRunResult> testingRunResults = new ArrayList<>();
        for (Future<List<TestingRunResult>> future: futureTestingRunResults) {
            if (!future.isDone()) continue;
            try {
                if (!future.get().isEmpty()) {
                    testingRunResults.addAll(future.get());
                }
            } catch (InterruptedException | ExecutionException e) {
                loggerMaker.errorAndAddToDb("Error while after running test : " + e, LogDb.TESTING);
            }
        }

        for (String host: hostsToApiCollectionMap.keySet()) {
            Integer apiCollectionId = hostsToApiCollectionMap.get(host);
            List<TestingRunResult> nucleiResults = runNucleiTests(new ApiInfo.ApiInfoKey(apiCollectionId, host, URLMethods.Method.GET), testingRun, testingUtil, summaryId);
            if (nucleiResults != null && !nucleiResults.isEmpty()) {
                testingRunResults.addAll(nucleiResults);
            }
        }

        loggerMaker.infoAndAddToDb("Finished adding " + testingRunResults.size() + " testingRunResults", LogDb.TESTING);

        TestingRunResultSummariesDao.instance.updateOne(
            Filters.eq("_id", summaryId),
            Updates.set(TestingRunResultSummary.TEST_RESULTS_COUNT, testingRunResults.size())
        );

        loggerMaker.infoAndAddToDb("Finished adding issues", LogDb.TESTING);

        Map<String, Integer> totalCountIssues = new HashMap<>();
        totalCountIssues.put("HIGH", 0);
        totalCountIssues.put("MEDIUM", 0);
        totalCountIssues.put("LOW", 0);

        for (TestingRunResult testingRunResult: testingRunResults) {
            if (testingRunResult.isVulnerable()) {
                int initialCount = totalCountIssues.get("HIGH");
                totalCountIssues.put("HIGH", initialCount + 1);
            }
        }

        TestingRunResultSummariesDao.instance.updateOne(
            Filters.eq("_id", summaryId),
            Updates.combine(
                    Updates.set(TestingRunResultSummary.END_TIMESTAMP, Context.now()),
                    Updates.set(TestingRunResultSummary.STATE, State.COMPLETED),
                    Updates.set(TestingRunResultSummary.COUNT_ISSUES, totalCountIssues)
            )
        );

        loggerMaker.infoAndAddToDb("Finished updating TestingRunResultSummariesDao", LogDb.TESTING);

    }

    public static String findHost(ApiInfo.ApiInfoKey apiInfoKey, Map<ApiInfo.ApiInfoKey, List<String>> sampleMessagesMap) throws URISyntaxException {
        List<String> sampleMessages = sampleMessagesMap.get(apiInfoKey);
        if (sampleMessages == null || sampleMessagesMap.isEmpty()) return null;

        List<RawApi> messages = SampleMessageStore.fetchAllOriginalMessages(apiInfoKey, sampleMessagesMap);
        if (messages.isEmpty()) return null;

        OriginalHttpRequest originalHttpRequest = messages.get(0).getRequest();

        String baseUrl = originalHttpRequest.getUrl();
        if (baseUrl.startsWith("http")) {
            URI uri = new URI(baseUrl);
            String host = uri.getScheme() + "://" + uri.getHost();
            return (uri.getPort() != -1)  ? host + ":" + uri.getPort() : host;
        } else {
            return "https://" + originalHttpRequest.findHostFromHeader();
        }
    }

    private LoginFlowResponse triggerLoginFlow(AuthMechanism authMechanism, int retries) {
        LoginFlowResponse loginFlowResponse = null;
        for (int i=0; i<retries; i++) {
            try {
                loginFlowResponse = executeLoginFlow(authMechanism, null);
                if (loginFlowResponse.getSuccess()) {
                    loggerMaker.infoAndAddToDb("login flow success", LogDb.TESTING);
                    break;
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e.getMessage(), LogDb.TESTING);
            }
        }
        return loginFlowResponse;
    }

    public List<TestingRunResult> runNucleiTests(ApiInfo.ApiInfoKey apiInfoKey, TestingRun testingRun, TestingUtil testingUtil, ObjectId summaryId) {
        List<TestingRunResult> testingRunResults = new ArrayList<>();
        List<String> testSubCategories = testingRun.getTestingRunConfig().getTestSubCategoryList();
        if (testSubCategories != null) {
            for (String testSubCategory: testSubCategories) {
                if (testSubCategory.startsWith("http://") || testSubCategory.startsWith("https://")) {
                    try {
                        String origTemplateURL = testSubCategory;
                        origTemplateURL = origTemplateURL.replace("https://github.com/", "https://raw.githubusercontent.com/").replace("/blob/", "/");
                        String subcategory = origTemplateURL.substring(origTemplateURL.lastIndexOf("/")+1).split("\\.")[0];

                        FuzzingTest fuzzingTest = new FuzzingTest(testingRun.getId().toHexString(), summaryId.toHexString(), origTemplateURL, subcategory, testSubCategory, null);
                        TestingRunResult fuzzResult = runTestNuclei(fuzzingTest, apiInfoKey, testingUtil, testingRun.getId(), summaryId, testingRun.getTestingRunConfig());
                        if (fuzzResult != null) {
                            trim(fuzzResult);
                            TestingRunResultDao.instance.insertOne(fuzzResult);
                            TestingIssuesHandler handler = new TestingIssuesHandler();
                            handler.handleIssuesCreationFromTestingRunResults(Collections.singletonList(fuzzResult), false);
                            testingRunResults.add(fuzzResult);
                        }
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb("unable to execute fuzzing for " + testSubCategory, LogDb.TESTING);
                    }
                }
            }
            
        }
        return testingRunResults;
    }

    // public List<TestingRunResult> runNucleiTests(ApiInfo.ApiInfoKey apiInfoKey, TestingRun testingRun, TestingUtil testingUtil, ObjectId summaryId, Map<String, TestConfig> testConfigMap) {
    //     List<TestingRunResult> testingRunResults = new ArrayList<>();
    //     List<String> testSubCategories = testingRun.getTestingRunConfig().getTestSubCategoryList();
    //     List<RawApi> messages = SampleMessageStore.fetchAllOriginalMessages(apiInfoKey, testingUtil.getSampleMessages());
    //     RawApi message = messages.size() == 0? null: messages.get(0);
    //     if (testSubCategories != null) {
    //         for (String testSubCategory: testSubCategories) {
    //             if (testSubCategory.startsWith("http://") || testSubCategory.startsWith("https://")) {
    //                 try {
    //                     String origTemplateURL = testSubCategory;
    //                     origTemplateURL = origTemplateURL.replace("https://github.com/", "https://raw.githubusercontent.com/").replace("/blob/", "/");
    //                     String subcategory = origTemplateURL.substring(origTemplateURL.lastIndexOf("/")+1).split("\\.")[0];

    //                     TestPlugin fuzzingTest = new FuzzingTest(testingRun.getId().toHexString(), summaryId.toHexString(), origTemplateURL, subcategory, testSubCategory, null);
    //                     TestConfig testConfig = testConfigMap.get("FUZZING");
    //                     TestingRunResult fuzzResult = runTest(fuzzingTest, apiInfoKey, testingUtil, testingRun.getId(), summaryId, testConfig.getApiSelectionFilters().getNode(), message);
    //                     if (fuzzResult != null) {
    //                         trim(fuzzResult);
    //                         TestingRunResultDao.instance.insertOne(fuzzResult);
    //                         TestingIssuesHandler handler = new TestingIssuesHandler();
    //                         handler.handleIssuesCreationFromTestingRunResults(Collections.singletonList(fuzzResult));
    //                         testingRunResults.add(fuzzResult);
    //                     }
    //                 } catch (Exception e) {
    //                     loggerMaker.errorAndAddToDb("unable to execute fuzzing for " + testSubCategory, LogDb.TESTING);
    //                 }
    //             }
    //         }
    //     }

    //     return testingRunResults;
    // }

    public LoginFlowResponse executeLoginFlow(AuthMechanism authMechanism, LoginFlowParams loginFlowParams) throws Exception {

        if (authMechanism.getType() == null) {
            loggerMaker.infoAndAddToDb("auth type value is null", LogDb.TESTING);
            return new LoginFlowResponse(null, null, true);
        }

        if (!authMechanism.getType().equals(LoginFlowEnums.AuthMechanismTypes.LOGIN_REQUEST.toString())) {
            loggerMaker.infoAndAddToDb("invalid auth type for login flow execution", LogDb.TESTING);
            return new LoginFlowResponse(null, null, true);
        }

        loggerMaker.infoAndAddToDb("login flow execution started", LogDb.TESTING);

        WorkflowTest workflowObj = convertToWorkflowGraph(authMechanism.getRequestData(), loginFlowParams);
        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        LoginFlowResponse loginFlowResp;
        loginFlowResp = apiWorkflowExecutor.runLoginFlow(workflowObj, authMechanism, loginFlowParams);
        return loginFlowResp;
    }

    public WorkflowTest convertToWorkflowGraph(ArrayList<RequestData> requestData, LoginFlowParams loginFlowParams) {

        String source, target;
        List<String> edges = new ArrayList<>();
        int edgeNumber = 1;
        LoginWorkflowGraphEdge edgeObj;
        Map<String,WorkflowNodeDetails> mapNodeIdToWorkflowNodeDetails = new HashMap<>();
        for (int i=0; i< requestData.size(); i++) {

            RequestData data = requestData.get(i);

            source = (i==0)? "1" : "x"+ (edgeNumber - 2);
            target = "x"+ edgeNumber;
            edgeNumber += 2;

            edgeObj = new LoginWorkflowGraphEdge(source, target, target);
            edges.add(edgeObj.toString());

            JSONObject json = new JSONObject() ;
            json.put("method", data.getMethod());
            json.put("requestPayload", data.getBody());
            json.put("path", data.getUrl());
            json.put("requestHeaders", data.getHeaders());
            json.put("type", "");

            WorkflowUpdatedSampleData sampleData = new WorkflowUpdatedSampleData(json.toString(), data.getQueryParams(),
                    data.getHeaders(), data.getBody(), data.getUrl());

            int waitTime = 0;
            WorkflowNodeDetails.Type nodeType = WorkflowNodeDetails.Type.API;
            if (data.getType().equals(LoginFlowEnums.LoginStepTypesEnums.OTP_VERIFICATION.toString())) {
                nodeType = WorkflowNodeDetails.Type.OTP;
                if (loginFlowParams == null || !loginFlowParams.getFetchValueMap()) {
                    waitTime = 60;
                }
            }
            if (data.getType().equals(LoginFlowEnums.LoginStepTypesEnums.RECORDED_FLOW.toString())) {
                nodeType = WorkflowNodeDetails.Type.RECORDED;
            }
            WorkflowNodeDetails workflowNodeDetails = new WorkflowNodeDetails(0, data.getUrl(),
                    URLMethods.Method.fromString(data.getMethod()), "", sampleData,
                    nodeType, true, waitTime, 0, 0, data.getRegex(), data.getOtpRefUuid());

            mapNodeIdToWorkflowNodeDetails.put(target, workflowNodeDetails);
        }

        edgeObj = new LoginWorkflowGraphEdge("x"+ (edgeNumber - 2), "3", "x"+ edgeNumber);
        edges.add(edgeObj.toString());

        return new WorkflowTest(0, 0, "", Context.now(), "", Context.now(),
                null, edges, mapNodeIdToWorkflowNodeDetails, WorkflowTest.State.DRAFT);
    }

    public Map<String, Object> generateResponseMap(String payloadStr, Map<String, List<String>> headers) {
        boolean isList = false;

        Map<String, Object> respMap = new HashMap<>();

        if (payloadStr == null) payloadStr = "{}";
        if (payloadStr.startsWith("[")) {
            payloadStr = "{\"json\": "+payloadStr+"}";
            isList = true;
        }

        BasicDBObject payloadObj;
        try {
            payloadObj = BasicDBObject.parse(payloadStr);
        } catch (Exception e) {
            boolean isPostFormData = payloadStr.contains("&") && payloadStr.contains("=");
            if (isPostFormData) {
                String mockUrl = "url?"+ payloadStr; // because getQueryJSON function needs complete url
                payloadObj = RequestTemplate.getQueryJSON(mockUrl);
            } else {
                payloadObj = BasicDBObject.parse("{}");
            }
        }

        Object obj;
        if (isList) {
            obj = payloadObj.get("json");
        } else {
            obj = payloadObj;
        }

        BasicDBObject flattened = JSONUtils.flattenWithDots(obj);


        for (String param: flattened.keySet()) {
            System.out.println(param);
            System.out.println(flattened.get(param));
            respMap.put(param, flattened.get(param));
        }

        for (String headerName: headers.keySet()) {
            for (String val: headers.get(headerName)) {
                System.out.println(headerName);
                System.out.println(val);
                respMap.put(headerName, val);
            }
        }
        return respMap;
    }

    public List<TestingRunResult> startWithLatch(
            ApiInfo.ApiInfoKey apiInfoKey, int testIdConfig, ObjectId testRunId, TestingRunConfig testingRunConfig,
            TestingUtil testingUtil, ObjectId testRunResultSummaryId, int accountId, CountDownLatch latch, int startTime,
            int timeToKill, Map<String, TestConfig> testConfigMap, TestingRun testingRun) {

        loggerMaker.infoAndAddToDb("Starting test for " + apiInfoKey, LogDb.TESTING);

        Context.accountId.set(accountId);
        List<TestingRunResult> testingRunResults = new ArrayList<>();
        int now = Context.now();
        if ( timeToKill <= 0 || now - startTime <= timeToKill) {
            try {
                // todo: commented out older one
//                testingRunResults = start(apiInfoKey, testIdConfig, testRunId, testingRunConfig, testingUtil, testRunResultSummaryId, testConfigMap);
                testingRunResults = startTestNew(apiInfoKey, testRunId, testingRunConfig, testingUtil, testRunResultSummaryId, testConfigMap);
                String size = testingRunResults.size()+"";
                loggerMaker.infoAndAddToDb("testingRunResults size: " + size, LogDb.TESTING);
                if (!testingRunResults.isEmpty()) {
                    trim(testingRunResults);
                    TestingRunResultDao.instance.insertMany(testingRunResults);
                    loggerMaker.infoAndAddToDb("Inserted testing results", LogDb.TESTING);
                    //Creating issues from testingRunResults
                   TestingIssuesHandler handler = new TestingIssuesHandler();
                   boolean triggeredByTestEditor = false;
                   if (testingRun.getTriggeredBy() != null) {
                        triggeredByTestEditor = testingRun.getTriggeredBy().equals("test_editor");
                   }
                   handler.handleIssuesCreationFromTestingRunResults(testingRunResults, triggeredByTestEditor); // pass new field here
                }
            } catch (Exception e) {
                e.printStackTrace();
                loggerMaker.errorAndAddToDb("error while running tests: " + e, LogDb.TESTING);
            }
        }

        latch.countDown();
        return testingRunResults;
    }

    public static void trim(TestingRunResult testingRunResult) {
        List<TestResult> testResults = testingRunResult.getTestResults();
        int endIdx = testResults.size();
        long currentSize = 0;

        for (int idx=0;idx< testResults.size();idx++) {
            TestResult testResult = testResults.get(idx);

            String originalMessage = testResult.getOriginalMessage();
            long originalMessageSize = originalMessage == null ? 0 : originalMessage.getBytes().length;

            String message = testResult.getMessage();
            long messageSize = message == null ? 0 : message.getBytes().length;

            currentSize += originalMessageSize + messageSize;

            if (currentSize > acceptableSizeInBytes) {
                endIdx = idx;
                break;
            }
        }

        testResults = testResults.subList(0,endIdx);
        testingRunResult.setTestResults(testResults);
    }

    public void trim(List<TestingRunResult> testingRunResults) {
        for (TestingRunResult testingRunResult: testingRunResults) {
            trim(testingRunResult);
        }
    }

    public List<TestingRunResult> startTestNew(ApiInfo.ApiInfoKey apiInfoKey, ObjectId testRunId,
                                               TestingRunConfig testingRunConfig, TestingUtil testingUtil,
                                               ObjectId testRunResultSummaryId, Map<String, TestConfig> testConfigMap) {
        List<TestingRunResult> testingRunResults = new ArrayList<>();

        List<String> testSubCategories = testingRunConfig == null ? new ArrayList<>() : testingRunConfig.getTestSubCategoryList();

        for (String testSubCategory: testSubCategories) {
            TestConfig testConfig = testConfigMap.get(testSubCategory);
            if (testConfig == null) continue;
            TestingRunResult testingRunResult = null;
            try {
                testingRunResult = runTestNew(apiInfoKey,testRunId,testingUtil,testRunResultSummaryId, testConfig, testingRunConfig);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error while running tests for " + testSubCategory +  ": " + e.getMessage(), LogDb.TESTING);
                e.printStackTrace();
            }
            if (testingRunResult != null) testingRunResults.add(testingRunResult);
        }

        return testingRunResults;
    }

    public TestingRunResult runTestNew(ApiInfo.ApiInfoKey apiInfoKey, ObjectId testRunId, TestingUtil testingUtil, ObjectId testRunResultSummaryId, TestConfig testConfig, TestingRunConfig testingRunConfig) {

        List<String> messages = testingUtil.getSampleMessages().get(apiInfoKey);
        if (messages == null || messages.size() == 0) return null;

        String message = messages.get(0);

        RawApi rawApi = RawApi.buildFromMessage(message);

        int startTime = Context.now();

        FilterNode filterNode = testConfig.getApiSelectionFilters().getNode();
        FilterNode validatorNode = testConfig.getValidation().getNode();
        ExecutorNode executorNode = testConfig.getExecute().getNode();
        Auth auth = testConfig.getAuth();
        Map<String, List<String>> wordListsMap = testConfig.getWordlists();
        Map<String, Object> varMap = new HashMap<>();

        for (String key: wordListsMap.keySet()) {
            varMap.put("wordList_" + key, wordListsMap.get(key));
        }

        String testSuperType = testConfig.getInfo().getCategory().getName();
        String testSubType = testConfig.getInfo().getSubCategory();

        String testExecutionLogId = UUID.randomUUID().toString();
        
        loggerMaker.infoAndAddToDb("triggering test run for apiInfoKey " + apiInfoKey + "test " + 
            testSubType + "logId" + testExecutionLogId, LogDb.TESTING);

        YamlTestTemplate yamlTestTemplate = new YamlTestTemplate(apiInfoKey,filterNode, validatorNode, executorNode, rawApi, varMap, auth, testingUtil.getAuthMechanism(), testExecutionLogId, testingRunConfig);
        List<TestResult> testResults = yamlTestTemplate.run();
        if (testResults == null || testResults.size() == 0) {
            return null;
        }
        int endTime = Context.now();

        boolean vulnerable = false;
        for (TestResult testResult: testResults) {
            if (testResult == null) continue;
            vulnerable = vulnerable || testResult.isVulnerable();
        }

        List<SingleTypeInfo> singleTypeInfos = new ArrayList<>();

        int confidencePercentage = 100;

        return new TestingRunResult(
                testRunId, apiInfoKey, testSuperType, testSubType ,testResults,
                vulnerable,singleTypeInfos,confidencePercentage,startTime,
                endTime, testRunResultSummaryId
        );
    }

    public TestingRunResult runTestNuclei(TestPlugin testPlugin, ApiInfo.ApiInfoKey apiInfoKey, TestingUtil testingUtil,
                                          ObjectId testRunId, ObjectId testRunResultSummaryId, TestingRunConfig testingRunConfig) {

        int startTime = Context.now();
        TestPlugin.Result result = testPlugin.start(apiInfoKey, testingUtil, testingRunConfig);
        if (result == null) return null;
        int endTime = Context.now();

        String subTestName = testPlugin.subTestName();

        if (testPlugin instanceof FuzzingTest) {
            FuzzingTest test = (FuzzingTest) testPlugin;
            subTestName = test.getTestSourceConfigCategory();
        }

        return new TestingRunResult(
                testRunId, apiInfoKey, testPlugin.superTestName(), subTestName, result.testResults,
                result.isVulnerable,result.singleTypeInfos, result.confidencePercentage,
                startTime, endTime, testRunResultSummaryId
        );
    }

}
