package com.akto.testing.workflow_node_executor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONObject;

import com.akto.agent.AgentClient;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.CustomAuthType;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.api_workflow.Node;
import com.akto.dto.test_editor.ConfigParserResult;
import com.akto.dto.test_editor.ExecuteAlgoObj;
import com.akto.dto.test_editor.ExecutionResult;
import com.akto.dto.test_editor.ExecutorNode;
import com.akto.dto.test_editor.ExecutorSingleRequest;
import com.akto.dto.test_editor.FilterNode;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.GenericTestResult;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestResult.TestError;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.WorkflowTestResult;
import com.akto.dto.testing.WorkflowTestResult.NodeResult;
import com.akto.dto.testing.YamlNodeDetails;
import com.akto.sql.SampleDataAltDb;
import com.akto.store.SampleMessageStore;
import com.akto.test_editor.TestingUtilsSingleton;
import com.akto.test_editor.execution.ExecutionListBuilder;
import com.akto.test_editor.execution.Executor;
import com.akto.test_editor.execution.ExecutorAlgorithm;
import com.akto.test_editor.execution.Memory;
import com.akto.testing.ApiExecutor;
import com.akto.testing.Main;
import com.akto.testing.TestExecutor;
import com.akto.testing.Utils;
import com.akto.testing.kafka_utils.TestingConfigurations;
import com.akto.util.Constants;
import com.google.gson.Gson;

import static com.akto.runtime.utils.Utils.convertOriginalReqRespToString;

public class YamlNodeExecutor extends NodeExecutor {
    
    private static final Gson gson = new Gson();
    private static final DataActor dataActor = DataActorFactory.fetchInstance();
    private final AgentClient agentClient = new AgentClient(
        Constants.AGENT_BASE_URL
    );

    public NodeResult processNode(Node node, Map<String, Object> varMap, Boolean allowAllStatusCodes, boolean debug, List<TestingRunResult.TestLog> testLogs, Memory memory) {
        List<String> testErrors = new ArrayList<>();

        YamlNodeDetails yamlNodeDetails = (YamlNodeDetails) node.getWorkflowNodeDetails();

        if (yamlNodeDetails.getTestId() != null && yamlNodeDetails.getTestId().length() > 0) {
            return processYamlNode(node, varMap, allowAllStatusCodes, yamlNodeDetails, debug, testLogs);
        }

        RawApi rawApi = yamlNodeDetails.getRawApi();
        RawApi sampleRawApi = rawApi.copy();

        Executor executor = new Executor();
        ExecutorNode executorNode = yamlNodeDetails.getExecutorNode();
        FilterNode validatorNode = yamlNodeDetails.getValidatorNode();
        List<ExecutorNode> childNodes = executorNode.getChildNodes();

        ApiInfo.ApiInfoKey apiInfoKey = ((YamlNodeDetails) node.getWorkflowNodeDetails()).getApiInfoKey();
        ExecutorNode firstChildNode = childNodes.get(0); // todo check for length
        if (memory != null) {
            if (firstChildNode.getOperationType().equalsIgnoreCase("api")) {
                String apiType = firstChildNode.getValues().toString();
                if (apiType.equalsIgnoreCase("get_asset_api")) {
                    rawApi = memory.findAssetGetterRequest(apiInfoKey);
                    if (rawApi == null)  {
                        testErrors.add("Couldn't find corresponding getter api");
                        new WorkflowTestResult.NodeResult("[]",false, testErrors);
                    }
                }
                childNodes.remove(0);
            } else {
                OriginalHttpRequest request = memory.run(apiInfoKey.getApiCollectionId(), apiInfoKey.getUrl(), apiInfoKey.getMethod().name());
                if (request == null) {
                    testErrors.add("Failed getting request from dependency graph");
                    new WorkflowTestResult.NodeResult("[]",false, testErrors);
                }
                rawApi.setRequest(request);
            }
        }


        List<RawApi> rawApis = new ArrayList<>();
        rawApis.add(rawApi.copy());

        for (ExecutorNode execNode: childNodes) {
            if (execNode.getNodeType().equalsIgnoreCase(TestEditorEnums.ValidateExecutorDataOperands.Validate.toString())) {
                validatorNode = (FilterNode) execNode.getChildNodes().get(0).getValues();
            }
        }

        AuthMechanism authMechanism = yamlNodeDetails.getAuthMechanism();
        List<CustomAuthType> customAuthTypes = yamlNodeDetails.getCustomAuthTypes();

        ExecutionListBuilder executionListBuilder = new ExecutionListBuilder();
        List<ExecutorNode> executorNodes = new ArrayList<>();
        boolean followRedirect = executionListBuilder.buildExecuteOrder(executorNode, executorNodes);

        ExecutorAlgorithm executorAlgorithm = new ExecutorAlgorithm(sampleRawApi, varMap, authMechanism, customAuthTypes);
        Map<Integer, ExecuteAlgoObj> algoMap = new HashMap<>();
        ExecutorSingleRequest singleReq = executorAlgorithm.execute(executorNodes, 0, algoMap, rawApis, false, 0, yamlNodeDetails.getApiInfoKey());

        if (!singleReq.getSuccess()) {
            rawApis = new ArrayList<>();
            testErrors.add(singleReq.getErrMsg());
        }
        //ExecutorSingleRequest singleReq = executor.buildTestRequest(executorNode, null, rawApis, varMap, authMechanism, customAuthTypes);
        //List<RawApi> testRawApis = singleReq.getRawApis();

        TestingRunConfig testingRunConfig = TestingConfigurations.getInstance().getTestingRunConfig();
        String logId = "";

        boolean vulnerable;
        List<String> message;
        String savedResponses;
        String eventStreamResponse;
        int statusCode;
        List<Integer> responseTimeArr;
        List<Integer> responseLenArr;

        // Use the thread-local executor (set by ParallelGraphExecutor when in parallel mode)
        ExecutorService apiCallExecutor = TestingUtilsSingleton.getInstance().getApiCallExecutorService();
        List<RawApi> requestsToProcess = prepareTestRequests(rawApis, sampleRawApi, node, yamlNodeDetails);

        ExecutionContext execContext = (apiCallExecutor != null)
            ? executeParallel(requestsToProcess, node, yamlNodeDetails, sampleRawApi, executor, varMap,
                              logId, validatorNode, followRedirect, testingRunConfig,
                              debug, testLogs, apiInfoKey, memory, singleReq, apiCallExecutor)
            : executeSequential(requestsToProcess, node, yamlNodeDetails, sampleRawApi, executor, varMap,
                                logId, validatorNode, followRedirect, testingRunConfig,
                                debug, testLogs, apiInfoKey, memory, singleReq);

        vulnerable = execContext.vulnerable;
        message = execContext.messages;
        responseTimeArr = execContext.responseTimes;
        responseLenArr = execContext.responseSizes;
        savedResponses = execContext.lastResponseBody;
        statusCode = execContext.lastStatusCode;
        eventStreamResponse = execContext.lastEventStream;

        // Extract error messages from TestResults in execContext.results
        for (TestResult result : execContext.results) {
            if (result.getErrors() != null && !result.getErrors().isEmpty()) {
                testErrors.addAll(result.getErrors());
            }
        }

        calcTimeAndLenStats(node.getId(), responseTimeArr, responseLenArr, varMap);

        if (savedResponses != null) {
            Utils.populateValuesMap(varMap, savedResponses, node.getId(),
                    new HashMap<>(), false, null);
            varMap.put(node.getId() + ".response.status_code", String.valueOf(statusCode));
        }
        if (eventStreamResponse != null) {
            varMap.put(node.getId() + ".response.body.eventStream", eventStreamResponse);
        }

        return new WorkflowTestResult.NodeResult(message.toString(), vulnerable, testErrors);

    }

    /**
     * Prepare test requests by filtering and adding tracking headers
     */
    private List<RawApi> prepareTestRequests(List<RawApi> rawApis, RawApi sampleRawApi, Node node, YamlNodeDetails yamlNodeDetails) {
        List<RawApi> requestsToProcess = new ArrayList<>();
        for (RawApi testReq : rawApis) {
            if (!testReq.equals(sampleRawApi)) {
                requestsToProcess.add(testReq);
            }
        }

        if (yamlNodeDetails.getApiCollectionId() == 1111111111) {
            for (RawApi testReq : requestsToProcess) {
                Map<String, List<String>> headers = testReq.fetchReqHeaders();
                headers.put(Constants.AKTO_NODE_ID, Collections.singletonList(node.getId()));
                testReq.modifyReqHeaders(headers);
            }
        }
        return requestsToProcess;
    }

    /**
     * Execute API tests in parallel using CompletableFuture
     */
    private ExecutionContext executeParallel(List<RawApi> requests, Node node, YamlNodeDetails yamlNodeDetails,
                                             RawApi sampleRawApi, Executor executor, Map<String, Object> varMap,
                                             String logId, FilterNode validatorNode, boolean followRedirect,
                                             TestingRunConfig testingRunConfig, boolean debug,
                                             List<TestingRunResult.TestLog> testLogs, ApiInfo.ApiInfoKey apiInfoKey,
                                             Memory memory, ExecutorSingleRequest singleReq, ExecutorService apiCallExecutor) {
        ExecutionContext context = new ExecutionContext();
        AtomicBoolean vulnerabilityFound = new AtomicBoolean(false);

        List<CompletableFuture<ApiCallResult>> futures = submitApiCalls(
            requests, node, yamlNodeDetails, sampleRawApi, executor, varMap, logId, validatorNode,
            followRedirect, testingRunConfig, debug, testLogs, apiInfoKey, memory, singleReq,
            vulnerabilityFound, apiCallExecutor
        );

        collectParallelResults(futures, context, vulnerabilityFound);
        return context;
    }

    /**
     * Submit all API calls to the executor service
     */
    private List<CompletableFuture<ApiCallResult>> submitApiCalls(List<RawApi> requests, Node node,
                                                                  YamlNodeDetails yamlNodeDetails, RawApi sampleRawApi,
                                                                  Executor executor, Map<String, Object> varMap,
                                                                  String logId, FilterNode validatorNode,
                                                                  boolean followRedirect, TestingRunConfig testingRunConfig,
                                                                  boolean debug, List<TestingRunResult.TestLog> testLogs,
                                                                  ApiInfo.ApiInfoKey apiInfoKey, Memory memory,
                                                                  ExecutorSingleRequest singleReq, AtomicBoolean vulnerabilityFound,
                                                                  ExecutorService apiCallExecutor) {
        List<CompletableFuture<ApiCallResult>> futures = new ArrayList<>();

        for (RawApi testReq : requests) {
            CompletableFuture<ApiCallResult> future = CompletableFuture.supplyAsync(() -> {
                if (vulnerabilityFound.get()) {
                    return null;
                }
                return processSingleApiCall(testReq, node, yamlNodeDetails, sampleRawApi, executor,
                        varMap, logId, validatorNode, followRedirect, testingRunConfig, debug,
                        testLogs, apiInfoKey, memory, singleReq);
            }, apiCallExecutor);
            futures.add(future);
        }
        return futures;
    }

    /**
     * Collect results from parallel execution
     */
    private void collectParallelResults(List<CompletableFuture<ApiCallResult>> futures,
                                        ExecutionContext context, AtomicBoolean vulnerabilityFound) {
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        for (CompletableFuture<ApiCallResult> future : futures) {
            try {
                ApiCallResult callResult = future.getNow(null);
                if (callResult != null) {
                    aggregateResult(callResult, context, vulnerabilityFound);
                }
            } catch (Exception e) {
                // ignore individual failures and continue
            }
        }
        context.vulnerable = vulnerabilityFound.get();
    }

    /**
     * Execute API tests sequentially (original behavior)
     */
    private ExecutionContext executeSequential(List<RawApi> requests, Node node, YamlNodeDetails yamlNodeDetails,
                                              RawApi sampleRawApi, Executor executor, Map<String, Object> varMap,
                                              String logId, FilterNode validatorNode, boolean followRedirect,
                                              TestingRunConfig testingRunConfig, boolean debug,
                                              List<TestingRunResult.TestLog> testLogs, ApiInfo.ApiInfoKey apiInfoKey,
                                              Memory memory, ExecutorSingleRequest singleReq) {
        ExecutionContext context = new ExecutionContext();
        for (RawApi testReq : requests) {
            if (context.vulnerable) break;
            ApiCallResult callResult = processSingleApiCall(testReq, node, yamlNodeDetails, sampleRawApi,
                    executor, varMap, logId, validatorNode, followRedirect, testingRunConfig, debug,
                    testLogs, apiInfoKey, memory, singleReq);
            if (callResult != null) {
                aggregateResult(callResult, context, null);
            }
        }
        return context;
    }

    /**
     * Aggregate a single API call result into the execution context
     */
    private void aggregateResult(ApiCallResult callResult, ExecutionContext context, AtomicBoolean vulnerabilityFound) {
        if (callResult.getTestResult() != null) {
            context.results.add(callResult.getTestResult());
            if (callResult.getTestResult().getVulnerable()) {
                context.vulnerable = true;
                if (vulnerabilityFound != null) {
                    vulnerabilityFound.set(true);
                }
            }
        }
        if (callResult.getMessage() != null) {
            context.messages.add(callResult.getMessage());
        }
        if (callResult.getResponseTime() != null) {
            context.responseTimes.add(callResult.getResponseTime());
        }
        if (callResult.getResponseLength() != null) {
            context.responseSizes.add(callResult.getResponseLength());
        }
        if (callResult.getSavedResponses() != null) {
            context.lastResponseBody = callResult.getSavedResponses();
            context.lastStatusCode = callResult.getStatusCode();
            context.lastEventStream = callResult.getEventStreamResponse();
        }
    }

    /**
     * Context to hold execution results (similar to ParallelGraphExecutor pattern)
     */
    private static class ExecutionContext {
        List<TestResult> results = Collections.synchronizedList(new ArrayList<>());
        List<String> messages = Collections.synchronizedList(new ArrayList<>());
        List<Integer> responseTimes = Collections.synchronizedList(new ArrayList<>());
        List<Integer> responseSizes = Collections.synchronizedList(new ArrayList<>());
        boolean vulnerable = false;
        String lastResponseBody = null;
        int lastStatusCode = 0;
        String lastEventStream = null;
    }

    /**
     * Helper class to hold the result of processing a single API call
     */
    private static class ApiCallResult {
        private final TestResult testResult;
        private final String message;
        private final Integer responseTime;
        private final Integer responseLength;
        private final String savedResponses;
        private final int statusCode;
        private final String eventStreamResponse;

        ApiCallResult(TestResult testResult, String message, Integer responseTime,
                      Integer responseLength, String savedResponses, int statusCode,
                      String eventStreamResponse) {
            this.testResult = testResult;
            this.message = message;
            this.responseTime = responseTime;
            this.responseLength = responseLength;
            this.savedResponses = savedResponses;
            this.statusCode = statusCode;
            this.eventStreamResponse = eventStreamResponse;
        }

        public TestResult getTestResult() { return testResult; }
        public String getMessage() { return message; }
        public Integer getResponseTime() { return responseTime; }
        public Integer getResponseLength() { return responseLength; }
        public String getSavedResponses() { return savedResponses; }
        public int getStatusCode() { return statusCode; }
        public String getEventStreamResponse() { return eventStreamResponse; }
    }

    /**
     * Abstracted method to process a single API call.
     * This method contains the common logic used in both sequential and parallel execution paths.
     */
    private ApiCallResult processSingleApiCall(RawApi testReq, Node node, YamlNodeDetails yamlNodeDetails,
                                               RawApi sampleRawApi, Executor executor, Map<String, Object> varMap,
                                               String logId, FilterNode validatorNode, boolean followRedirect,
                                               TestingRunConfig testingRunConfig, boolean debug,
                                               List<TestingRunResult.TestLog> testLogs, ApiInfo.ApiInfoKey apiInfoKey,
                                               Memory memory, ExecutorSingleRequest singleReq) {
        try {
            TestResult res = null;
            String messageStr = null;
            Integer responseTime = null;
            Integer responseLength = null;
            String savedResponses = null;
            int statusCode = 0;
            String eventStreamResponse = null;

            if (AgentClient.isRawApiValidForAgenticTest(testReq)) {
                res = agentClient.executeAgenticTest(testReq, yamlNodeDetails.getApiCollectionId());
            } else {
                int tsBeforeReq = Context.nowInMillis();
                OriginalHttpResponse testResponse = ApiExecutor.sendRequest(
                        testReq.getRequest(), followRedirect, testingRunConfig, debug, testLogs, Main.SKIP_SSRF_CHECK
                );

                if (apiInfoKey != null && memory != null) {
                    memory.fillResponse(testReq.getRequest(), testResponse,
                            apiInfoKey.getApiCollectionId(), apiInfoKey.getUrl(), apiInfoKey.getMethod().name());
                    memory.reset(apiInfoKey.getApiCollectionId(), apiInfoKey.getUrl(), apiInfoKey.getMethod().name());
                }

                int tsAfterReq = Context.nowInMillis();
                responseTime = tsAfterReq - tsBeforeReq;

                ExecutionResult attempt = new ExecutionResult(singleReq.getSuccess(), singleReq.getErrMsg(),
                        testReq.getRequest(), testResponse);
                res = executor.validate(attempt, sampleRawApi, varMap, logId, validatorNode, yamlNodeDetails.getApiInfoKey());

                try {
                    messageStr = convertOriginalReqRespToString(testReq.getRequest(), testResponse, responseTime);
                } catch (Exception ignored) {
                }

                savedResponses = testResponse.getBody();
                statusCode = testResponse.getStatusCode();
                eventStreamResponse = com.akto.test_editor.Utils.buildEventStreamResponseIHttpFormat(testResponse);

                if (testResponse.getBody() == null) {
                    responseLength = 0;
                } else {
                    responseLength = testResponse.getBody().length();
                }
            }

            return new ApiCallResult(res, messageStr, responseTime, responseLength,
                    savedResponses, statusCode, eventStreamResponse);
        } catch (Exception e) {
            // Categorize the error similar to single execution mode
            String errorMessage = "Error executing test request: " + e.getMessage();
            testLogs.add(new TestingRunResult.TestLog(TestingRunResult.TestLogType.ERROR, errorMessage));

            TestError categorizedError = Executor.categorizeError(errorMessage);
            List<String> errorMessages = new ArrayList<>();
            errorMessages.add(categorizedError.getMessage());

            TestResult errorResult = new TestResult(
                null,
                yamlNodeDetails.getOriginalMessage(),
                errorMessages,
                0,
                false,
                TestResult.Confidence.HIGH,
                null
            );

            return new ApiCallResult(errorResult, null, null, null, null, 0, null);
        }
    }

    public void calcTimeAndLenStats(String nodeId, List<Integer> responseTimeArr, List<Integer> responseLenArr, Map<String, Object> varMap) {

        Map<String, Double> m = new HashMap<>();
        m = calcStats(responseTimeArr);
        for (String k: m.keySet()) {
            if (k.equals("last")) {
                varMap.put(nodeId + ".response.response_time", m.getOrDefault(k, 0.0));
            } else {
                varMap.put(nodeId + ".response.stats." + k + "_response_time", m.getOrDefault(k, 0.0));
            }
        }

        m = calcStats(responseLenArr);
        for (String k: m.keySet()) {
            if (k.equals("last")) {
                varMap.put(nodeId + ".response.response_len", m.getOrDefault(k, 0.0));
            } else {
                varMap.put(nodeId + ".response.stats." + k + "_response_len", m.getOrDefault(k, 0.0));
            }
        }

    }

    public Map<String, Double> calcStats(List<Integer> arr) {
        Map<String, Double> m = new HashMap<>();
        if (arr.size() == 0) {
            return m;
        }
        double last = 0.0;
        double median;
        last = arr.get(arr.size() - 1);
        Collections.sort(arr);

        int total = 0;
        for (int i = 0; i < arr.size(); i++) {
            total += arr.get(i);
        }
        
        if (arr.size() % 2 == 0)
            median = ((double)arr.get(arr.size()/2) + (double)arr.get(arr.size()/2 - 1))/2;
        else
            median = (double) arr.get(arr.size()/2);
        
        m.put("min", (double) arr.get(0));
        m.put("max", (double) arr.get(arr.size() - 1));
        m.put("average", (double) (total/arr.size()));
        m.put("median", median);
        m.put("last", last);

        return m;
    }

    public WorkflowTestResult.NodeResult processYamlNode(Node node, Map<String, Object> valuesMap, Boolean allowAllStatusCodes, YamlNodeDetails yamlNodeDetails, boolean debug, List<TestingRunResult.TestLog> testLogs) {

        String testSubCategory = yamlNodeDetails.getTestId();
        List<YamlTemplate> yamlTemplates = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            yamlTemplates.addAll(dataActor.fetchYamlTemplates(true, i*50));
        }
        YamlTemplate commonTemplate = dataActor.fetchCommonWordList();
        Map<String, TestConfig> testConfigMap = YamlTemplateDao.instance.fetchTestConfigMap(false, false, yamlTemplates, commonTemplate);
        TestConfig testConfig = testConfigMap.get(testSubCategory);

        ExecutorNode executorNode = yamlNodeDetails.getExecutorNode();

        for (ExecutorNode execNode: executorNode.getChildNodes()) {
            if (execNode.getNodeType().equalsIgnoreCase(TestEditorEnums.ValidateExecutorDataOperands.Validate.toString())) {
                FilterNode validatorNode = (FilterNode) execNode.getChildNodes().get(0).getValues();
                ConfigParserResult configParserResult = testConfig.getValidation();
                configParserResult.setNode(validatorNode);
                testConfig.setValidation(configParserResult);
            }
        }

        RawApi rawApi = yamlNodeDetails.getRawApi();

        Map<String, Object> m = new HashMap<>();
        for (String k: rawApi.getRequest().getHeaders().keySet()) {
            List<String> v = rawApi.getRequest().getHeaders().getOrDefault(k, new ArrayList<>());
            if (v.size() > 0) {
                m.put(k, v.get(0).toString());
            }
        }

        Map<String, Object> m2 = new HashMap<>();
        for (String k: rawApi.getResponse().getHeaders().keySet()) {
            List<String> v = rawApi.getRequest().getHeaders().getOrDefault(k, new ArrayList<>());
            if (v.size() > 0) {
                m2.put(k, v.get(0).toString());
            }
        }

        JSONObject json = new JSONObject() ;
        json.put("method", rawApi.getRequest().getMethod());
        json.put("requestPayload", rawApi.getRequest().getBody());
        json.put("path", rawApi.getRequest().getUrl());
        json.put("requestHeaders", gson.toJson(m));
        json.put("type", "");
        json.put("responsePayload", rawApi.getResponse().getBody());
        json.put("responseHeaders", gson.toJson(m2));
        json.put("statusCode", Integer.toString(rawApi.getResponse().getStatusCode()));
        
        AuthMechanism authMechanism = yamlNodeDetails.getAuthMechanism();
        Map<ApiInfo.ApiInfoKey, List<String>> sampleDataMap = new HashMap<>();
        sampleDataMap.put(yamlNodeDetails.getApiInfoKey(), Collections.singletonList(json.toString()));
        SampleMessageStore messageStore = SampleMessageStore.create(sampleDataMap);
        List<CustomAuthType> customAuthTypes = yamlNodeDetails.getCustomAuthTypes();
        TestExecutor executor = new TestExecutor();
        ApiInfoKey infoKey = yamlNodeDetails.getApiInfoKey();
        List<String> samples = messageStore.getSampleDataMap().get(infoKey);
        TestingRunResult testingRunResult = com.akto.testing.Utils.generateFailedRunResultForMessage(null, infoKey, testConfig.getInfo().getCategory().getName(), testConfig.getInfo().getSubCategory(), null,samples , null);
        if(testingRunResult == null){
            String message = samples.get(samples.size() - 1);
            String msg = null;
            try {
                msg = SampleDataAltDb.findLatestSampleByApiInfoKey(infoKey);
            } catch (Exception e) {
            }
            if (msg != null) {
                message = msg;
            }
            testingRunResult = executor.runTestNew(infoKey, null, messageStore, authMechanism, customAuthTypes, null, testConfig, null, debug, testLogs, RawApi.buildFromMessage(samples.get(samples.size() - 1)));
        }

        List<String> errors = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        if (testingRunResult.isVulnerable()) {
            List<GenericTestResult> testResults = testingRunResult.getTestResults();
            for (GenericTestResult testResult: testResults) {
                TestResult t = (TestResult) testResult;
                messages.add(t.getMessage());
            }
        }

        return new WorkflowTestResult.NodeResult(messages.toString(), testingRunResult.isVulnerable(), errors);
    }
    
}
