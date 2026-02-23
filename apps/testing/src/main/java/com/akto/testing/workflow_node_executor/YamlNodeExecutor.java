package com.akto.testing.workflow_node_executor;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.akto.test_editor.TestingUtilsSingleton;

import com.akto.dto.*;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.test_editor.execution.Memory;
import org.json.JSONObject;

import com.akto.agent.AgentClient;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dto.api_workflow.Node;
import com.akto.dto.test_editor.ConfigParserResult;
import com.akto.dto.test_editor.ExecuteAlgoObj;
import com.akto.dto.test_editor.ExecutionResult;
import com.akto.dto.test_editor.ExecutorNode;
import com.akto.dto.test_editor.ExecutorSingleRequest;
import com.akto.dto.test_editor.FilterNode;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.GenericTestResult;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.WorkflowTestResult;
import com.akto.dto.testing.YamlNodeDetails;
import com.akto.dto.testing.WorkflowTestResult.NodeResult;
import com.akto.store.SampleMessageStore;
import com.akto.test_editor.execution.ExecutionListBuilder;
import com.akto.test_editor.execution.Executor;
import com.akto.test_editor.execution.ExecutorAlgorithm;
import com.akto.testing.ApiExecutor;
import com.akto.testing.TestExecutor;
import com.akto.testing.Utils;
import com.akto.testing.kafka_utils.TestingConfigurations;
import com.akto.util.Constants;
import static com.akto.runtime.utils.Utils.convertOriginalReqRespToString;
import com.google.gson.Gson;
import com.mongodb.client.model.Filters;

public class YamlNodeExecutor extends NodeExecutor {
    
    private static final Gson gson = new Gson();

    public YamlNodeExecutor(boolean allowAllCombinations) {
        super(allowAllCombinations);
    }

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

        FilterNode finalValidatorNode = validatorNode;
        boolean isValidateAll = false;
        for (ExecutorNode execNode: childNodes) {
            if (execNode.getNodeType().equalsIgnoreCase(TestEditorEnums.ValidateExecutorDataOperands.Validate.toString())) {
                finalValidatorNode = (FilterNode) execNode.getChildNodes().get(0).getValues();
                isValidateAll = false;
            } else if (execNode.getNodeType().equalsIgnoreCase(TestEditorEnums.ValidateExecutorDataOperands.ValidateAll.toString())) {
                finalValidatorNode = (FilterNode) execNode.getChildNodes().get(0).getValues();
                isValidateAll = true;
            }
        }

        AuthMechanism authMechanism = yamlNodeDetails.getAuthMechanism();
        List<CustomAuthType> customAuthTypes = yamlNodeDetails.getCustomAuthTypes();

        ExecutionListBuilder executionListBuilder = new ExecutionListBuilder();
        List<ExecutorNode> executorNodes = new ArrayList<>();
        boolean followRedirect = executionListBuilder.buildExecuteOrder(executorNode, executorNodes);

        ExecutorAlgorithm executorAlgorithm = new ExecutorAlgorithm(sampleRawApi, varMap, authMechanism, customAuthTypes, this.allowAllCombinations);
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
        if(memory != null) {
            logId = memory.getLogId();
            if(testingRunConfig == null) {
                testingRunConfig = memory.getTestingRunConfig();
            }
        }
        
        // Make final copies for lambda usage
        final TestingRunConfig finalTestingRunConfig = testingRunConfig;
        final String finalLogId = logId;
        final FilterNode finalValidatorNodeForLambda = finalValidatorNode;
        final ExecutorSingleRequest finalSingleReq = singleReq;
        final boolean finalIsValidateAll = isValidateAll;
        
        boolean vulnerable;
        List<String> message;
        String savedResponses;
        String eventStreamResponse;
        int statusCode;
        List<Integer> responseTimeArr;
        List<Integer> responseLenArr;

        // Prepare test requests and execute
        TestingUtilsSingleton.getInstance().clearApiCallExecutorService();
        ExecutorService apiCallExecutor = TestingUtilsSingleton.getInstance().getApiCallExecutorService();
        
        List<RawApi> requestsToProcess = prepareTestRequests(rawApis, sampleRawApi, node, yamlNodeDetails);
        
        ExecutionContext execContext = (apiCallExecutor != null)
            ? executeParallel(requestsToProcess, node, yamlNodeDetails, sampleRawApi, executor, varMap,
                             finalLogId, finalValidatorNodeForLambda, followRedirect, finalTestingRunConfig,
                             debug, testLogs, apiInfoKey, memory, finalSingleReq, apiCallExecutor, finalIsValidateAll)
            : executeSequential(requestsToProcess, node, yamlNodeDetails, sampleRawApi, executor, varMap,
                               finalLogId, finalValidatorNodeForLambda, followRedirect, finalTestingRunConfig,
                               debug, testLogs, apiInfoKey, memory, finalSingleReq, finalIsValidateAll);
        
        // Extract results from context
        vulnerable = execContext.vulnerable;
        message = execContext.messages;
        responseTimeArr = execContext.responseTimes;
        responseLenArr = execContext.responseSizes;
        savedResponses = execContext.lastResponseBody;
        statusCode = execContext.lastStatusCode;
        eventStreamResponse = execContext.lastEventStream;

        calcTimeAndLenStats(node.getId(), responseTimeArr, responseLenArr, varMap);

        System.out.println("Saved Responses " + savedResponses);

        if (savedResponses != null) {
            varMap.put(node.getId() + ".response.body", savedResponses);
            varMap.put(node.getId() + ".response.status_code", String.valueOf(statusCode));
        }

        if (eventStreamResponse != null) {
            varMap.put(node.getId() + ".response.body.eventStream", eventStreamResponse);
        }

        // Run validate_all if specified - now all node variables are in varMap
        if (isValidateAll && finalValidatorNode != null) {
            // For validate_all, we don't validate against a specific response
            // We only use the variables in varMap (like ${x1.response.status_code})
            // So we pass the last response as context, but validation uses varMap
            OriginalHttpResponse syntheticResponse = new OriginalHttpResponse();
            if (savedResponses != null) {
                syntheticResponse.setBody(savedResponses);
                syntheticResponse.setStatusCode(statusCode);
            }

            ExecutionResult validateAllAttempt = new ExecutionResult(
                true, "",
                sampleRawApi.getRequest(),
                syntheticResponse
            );

            TestResult validateAllResult = executor.validate(
                validateAllAttempt, sampleRawApi, varMap, logId,
                finalValidatorNode, yamlNodeDetails.getApiInfoKey()
            );

            if (validateAllResult != null && validateAllResult.getVulnerable()) {
                vulnerable = true;
                message.add(validateAllResult.getMessage());
            }
        }

        // call executor's build request, which returns all rawapi by taking a rawapi argument
        // valuemap -> use varmap

        // loop on all rawapis and hit requests
        // call validate node present in node
        // if vulnerable, populate map

        //

        return new WorkflowTestResult.NodeResult(message.toString(), vulnerable, testErrors);

    }

    /**
     * Prepare test requests by filtering and adding tracking headers
     */
    private List<RawApi> prepareTestRequests(List<RawApi> rawApis, RawApi sampleRawApi, Node node, YamlNodeDetails yamlNodeDetails) {
        List<RawApi> requestsToProcess = rawApis.stream()
            .filter(testReq -> !testReq.equals(sampleRawApi))
            .collect(Collectors.toList());

        // Add node tracking headers if needed
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
                                            Memory memory, ExecutorSingleRequest singleReq, ExecutorService apiCallExecutor,
                                            boolean isValidateAll) {
        
        ExecutionContext context = new ExecutionContext();
        AtomicBoolean vulnerabilityFound = new AtomicBoolean(false);
        
        List<CompletableFuture<ApiCallResult>> futures = submitApiCalls(
            requests, node, yamlNodeDetails, sampleRawApi, executor, varMap, logId, validatorNode,
            followRedirect, testingRunConfig, debug, testLogs, apiInfoKey, memory, singleReq,
            vulnerabilityFound, apiCallExecutor, isValidateAll
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
                                                                   ExecutorService apiCallExecutor, boolean isValidateAll) {
        List<CompletableFuture<ApiCallResult>> futures = new ArrayList<>();
        
        for (RawApi testReq : requests) {
            CompletableFuture<ApiCallResult> future = CompletableFuture.supplyAsync(() -> {
                if (vulnerabilityFound.get()) {
                    return null; // Early exit optimization
                }
                return processSingleApiCall(testReq, node, yamlNodeDetails, sampleRawApi, executor,
                    varMap, logId, validatorNode, followRedirect, testingRunConfig, debug, testLogs,
                    apiInfoKey, memory, singleReq, isValidateAll);
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
        // Wait for all futures to complete in parallel (not sequentially!)
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        // Now collect all results
        for (CompletableFuture<ApiCallResult> future : futures) {
            try {
                ApiCallResult callResult = future.getNow(null); // Non-blocking since already completed
                if (callResult != null) {
                    aggregateResult(callResult, context, vulnerabilityFound);
                }
            } catch (Exception e) {
                // Continue processing other results
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
                                              Memory memory, ExecutorSingleRequest singleReq, boolean isValidateAll) {
        ExecutionContext context = new ExecutionContext();
        
        for (RawApi testReq : requests) {
            if (context.vulnerable) {
                break; // Stop if vulnerability found
            }
            
            ApiCallResult callResult = processSingleApiCall(testReq, node, yamlNodeDetails, sampleRawApi,
                executor, varMap, logId, validatorNode, followRedirect, testingRunConfig, debug,
                testLogs, apiInfoKey, memory, singleReq, isValidateAll);
            
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

        public ApiCallResult(TestResult testResult, String message, Integer responseTime, 
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
                                               Memory memory, ExecutorSingleRequest singleReq, boolean isValidateAll) {
        try {
            TestResult res = null;
            String messageStr = null;
            Integer responseTime = null;
            Integer responseLength = null;
            String savedResponses = null;
            int statusCode = 0;
            String eventStreamResponse = null;

            if (AgentClient.isRawApiValidForAgenticTest(testReq)) {
                // execute agentic test here
                res = agentClient.executeAgenticTest(testReq, yamlNodeDetails.getApiCollectionId());
            } else {
                int tsBeforeReq = Context.nowInMillis();
                String url = testReq.getRequest().getUrl();
                if (url.contains("sampl-aktol-1exannwybqov-67928726")) {
                    try {
                        URI uri = new URI(url);
                        String newUrl = "https://vulnerable-server.akto.io" + uri.getPath();
                        testReq.getRequest().setUrl(newUrl);
                    } catch (Exception e) {
                        // TODO: handle exception
                    }
                }
                
                // This is the key API call that gets parallelized
                OriginalHttpResponse testResponse = ApiExecutor.sendRequest(
                    testReq.getRequest(), followRedirect, testingRunConfig, debug, testLogs, 
                    com.akto.test_editor.Utils.SKIP_SSRF_CHECK
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

                // Skip validation if validate_all is specified - will be run after all requests complete
                if (!isValidateAll) {
                    res = executor.validate(attempt, sampleRawApi, varMap, logId, validatorNode, yamlNodeDetails.getApiInfoKey());
                } else {
                    // Create a non-vulnerable result - validation will be done later
                    String msg = convertOriginalReqRespToString(testReq.getRequest(), testResponse, 0);
                    res = new TestResult(msg, sampleRawApi.getOriginalMessage(), new ArrayList<>(), 0, false, TestResult.Confidence.HIGH, null);
                }
                
                try {
                    messageStr = convertOriginalReqRespToString(testReq.getRequest(), testResponse, responseTime);
                } catch (Exception e) {
                    // ignore
                }

                // save response in a list
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
            // TODO: handle exception
            return null;
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
        Map<String, TestConfig> testConfigMap = YamlTemplateDao.instance.fetchTestConfigMap(false, false, 0, 10_000, Filters.empty());
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
        TestingRunResult testingRunResult = Utils.generateFailedRunResultForMessage(null, infoKey, testConfig.getInfo().getCategory().getName(), testConfig.getInfo().getSubCategory(), null,samples , null);
        
        if(testingRunResult == null){
            testingRunResult = executor.runTestNew(infoKey, null, messageStore, authMechanism, customAuthTypes, null, testConfig, null, debug, testLogs, RawApi.buildFromMessage(samples.get(samples.size() - 1), true));
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
