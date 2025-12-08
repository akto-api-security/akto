package com.akto.test_editor.execution;

import com.akto.dao.billing.OrganizationsDao;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.akto.dao.context.Context;
import com.akto.dao.testing.TestRolesDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.CustomAuthType;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.RecordedLoginFlowInput;
import com.akto.dto.testing.*;
import com.akto.dto.testing.sources.AuthWithCond;
import com.akto.testing.*;
import com.akto.util.enums.LoginFlowEnums;
import com.akto.util.enums.LoginFlowEnums.AuthMechanismTypes;
import com.akto.dto.api_workflow.Graph;
import com.akto.dto.test_editor.*;
import com.akto.dto.testing.TestResult.Confidence;
import com.akto.dto.testing.TestResult.TestError;
import com.akto.dto.type.KeyTypes;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.rules.TestPlugin;
import com.akto.test_editor.Utils;
import com.akto.util.Constants;
import com.akto.util.modifier.JWTPayloadReplacer;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;

import org.json.JSONArray;
import org.json.JSONObject;

import com.mongodb.BasicDBObject;
import static com.akto.test_editor.Utils.bodyValuesUnchanged;
import static com.akto.test_editor.Utils.headerValuesUnchanged;
import static com.akto.runtime.utils.Utils.convertOriginalReqRespToString;
import static com.akto.testing.Utils.compareWithOriginalResponse;

import com.mongodb.client.model.Filters;

import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;


public class Executor {

    private static final LoggerMaker loggerMaker = new LoggerMaker(Executor.class);

    public final String _HOST = "host";

    public YamlTestResult execute(ExecutorNode node, RawApi rawApi, Map<String, Object> varMap, String logId,
                                  AuthMechanism authMechanism, FilterNode validatorNode, ApiInfo.ApiInfoKey apiInfoKey, TestingRunConfig testingRunConfig,
                                  List<CustomAuthType> customAuthTypes, boolean debug, List<TestingRunResult.TestLog> testLogs,
                                  Memory memory) {
        List<GenericTestResult> result = new ArrayList<>();

        ExecutionListBuilder executionListBuilder = new ExecutionListBuilder();
        List<ExecutorNode> executorNodes = new ArrayList<>();
        ExecutionOrderResp executionOrderResp = executionListBuilder.parseExecuteOperations(node, executorNodes);

        boolean followRedirect = executionOrderResp.getFollowRedirect();
        
        TestResult invalidExecutionResult = new TestResult(null, rawApi.getOriginalMessage(), Collections.singletonList(TestError.INVALID_EXECUTION_BLOCK.getMessage()), 0, false, TestResult.Confidence.HIGH, null);
        YamlTestResult yamlTestResult;
        WorkflowTest workflowTest = null;
        if (node.getChildNodes().size() < 2) {
            testLogs.add(new TestingRunResult.TestLog(TestingRunResult.TestLogType.ERROR, "executor child nodes is less than 2, returning empty execution result"));
            loggerMaker.errorAndAddToDb("executor child nodes is less than 2, returning empty execution result " + logId, LogDb.TESTING);
            result.add(invalidExecutionResult);
            yamlTestResult = new YamlTestResult(result, workflowTest);
            return yamlTestResult;
        }

        List<String> error_messages = new ArrayList<>();

        ModifyExecutionOrderResp modifyExecutionOrderResp = executionListBuilder.modifyExecutionFlow(executorNodes, varMap);

        Map<ApiInfo.ApiInfoKey, List<String>> newSampleDataMap = new HashMap<>();
        varMap = VariableResolver.resolveDynamicWordList(varMap, apiInfoKey, newSampleDataMap);

        if (modifyExecutionOrderResp.getError() != null) {
            error_messages.add(modifyExecutionOrderResp.getError());
            testLogs.add(new TestingRunResult.TestLog(TestingRunResult.TestLogType.ERROR, modifyExecutionOrderResp.getError()));
            invalidExecutionResult = new TestResult(null, rawApi.getOriginalMessage(), error_messages, 0, false, TestResult.Confidence.HIGH, null);
            result.add(invalidExecutionResult);
            yamlTestResult = new YamlTestResult(result, workflowTest);
            return yamlTestResult;
        }
        executorNodes = modifyExecutionOrderResp.getExecutorNodes();

        ExecutorNode reqNodes = node.getChildNodes().get(1);
        OriginalHttpResponse testResponse;
        RawApi origRawApi = rawApi.copy();
        RawApi sampleRawApi = rawApi.copy();
        if (reqNodes.getChildNodes() == null || reqNodes.getChildNodes().size() == 0) {
            result.add(invalidExecutionResult);
            yamlTestResult = new YamlTestResult(result, workflowTest);
            return yamlTestResult;
        }
        if (testingRunConfig != null && StringUtils.isNotBlank(testingRunConfig.getTestRoleId())) {
            TestRoles role = TestRolesDao.instance.findOne(Filters.eq("_id", new ObjectId(testingRunConfig.getTestRoleId())));
            if (role != null) {
                EndpointLogicalGroup endpointLogicalGroup = role.fetchEndpointLogicalGroup();
                if (endpointLogicalGroup != null && endpointLogicalGroup.getTestingEndpoints() != null  && endpointLogicalGroup.getTestingEndpoints().containsApi(apiInfoKey)) {
                    synchronized(role) {
                        loggerMaker.infoAndAddToDb("attempting to override auth " + logId, LogDb.TESTING);
                        if (modifyAuthTokenInRawApi(role, sampleRawApi) == null) {
                            loggerMaker.infoAndAddToDb("Default auth mechanism absent: " + logId, LogDb.TESTING);
                        }
                    }
                } else {
                    loggerMaker.infoAndAddToDb("Endpoint didn't satisfy endpoint condition for testRole" + logId, LogDb.TESTING);
                }
            } else {
                String reason = "Test role has been deleted";
                loggerMaker.infoAndAddToDb(reason + ", going ahead with sample auth", LogDb.TESTING);
            }
        }
        origRawApi = sampleRawApi.copy();

        boolean requestSent = false;

        String executionType = node.getChildNodes().get(0).getValues().toString();
        if (executionType.equals("multiple") || executionType.equals("graph")) {
            if (executionType.equals("graph")) {
                List<ApiInfo.ApiInfoKey> apiInfoKeys = new ArrayList<>();
                apiInfoKeys.add(apiInfoKey);
                memory = new Memory(apiInfoKeys, new HashMap<>());
            }
            workflowTest = buildWorkflowGraph(reqNodes, sampleRawApi, authMechanism, customAuthTypes, apiInfoKey, varMap, validatorNode);
            result.add(triggerMultiExecution(workflowTest, reqNodes, sampleRawApi, authMechanism, customAuthTypes, apiInfoKey, varMap, validatorNode, debug, testLogs, memory));
            yamlTestResult = new YamlTestResult(result, workflowTest);
            
            return yamlTestResult;
        }

        List<RawApi> testRawApis = new ArrayList<>();
        testRawApis.add(sampleRawApi.copy());
        ExecutorAlgorithm executorAlgorithm = new ExecutorAlgorithm(sampleRawApi, varMap, authMechanism, customAuthTypes);
        Map<Integer, ExecuteAlgoObj> algoMap = new HashMap<>();
        ExecutorSingleRequest singleReq = executorAlgorithm.execute(executorNodes, 0, algoMap, testRawApis, false, 0, apiInfoKey);
        
        if (!singleReq.getSuccess()) {
            testRawApis = new ArrayList<>();
            error_messages.add(singleReq.getErrMsg());
        }

        boolean vulnerable = false;
        for (RawApi testReq: testRawApis) {
            if (executorNodes.size() > 0 && testReq.equals(origRawApi)) {
                continue;
            }
            if (vulnerable) { //todo: introduce a flag stopAtFirstMatch
                break;
            }
            try {
                // follow redirects = true for now
                testResponse = ApiExecutor.sendRequest(testReq.getRequest(), followRedirect, testingRunConfig, debug, testLogs, Utils.SKIP_SSRF_CHECK);
                requestSent = true;
                ExecutionResult attempt = new ExecutionResult(singleReq.getSuccess(), singleReq.getErrMsg(), testReq.getRequest(), testResponse);
                TestResult res = validate(attempt, sampleRawApi, varMap, logId, validatorNode, apiInfoKey);
                if (res != null) {
                    result.add(res);
                }
                vulnerable = res.getVulnerable();
            } catch(Exception e) {
                testLogs.add(new TestingRunResult.TestLog(TestingRunResult.TestLogType.ERROR, "Error executing test request: " + e.getMessage()));
                error_messages.add("Error executing test request: " + e.getMessage());
                loggerMaker.errorAndAddToDb("Error executing test request " + logId + " " + e.getMessage(), LogDb.TESTING);
            }
        }
        
        if(result.isEmpty()){
            if(requestSent){
                error_messages.add(TestError.API_REQUEST_FAILED.getMessage());
            } else {
                error_messages.add(TestError.NO_API_REQUEST.getMessage());
            }
            result.add(new TestResult(null, rawApi.getOriginalMessage(), error_messages, 0, false, TestResult.Confidence.HIGH, null));
        }

        yamlTestResult = new YamlTestResult(result, workflowTest);

        return yamlTestResult;
    }

    public void overrideTestUrl(RawApi rawApi, TestingRunConfig testingRunConfig) {
        try {
            String url = "";
            List<String> originalHostHeaders = rawApi.getRequest().getHeaders().getOrDefault(_HOST, new ArrayList<>());
            if (!originalHostHeaders.isEmpty() && testingRunConfig != null
                && !StringUtils.isEmpty(testingRunConfig.getOverriddenTestAppUrl())) {

                Pattern pattern = Pattern.compile("\\$\\{[^}]*\\}");
                Matcher matcher = pattern.matcher(testingRunConfig.getOverriddenTestAppUrl());
                if (matcher.find()) {
                    String match = matcher.group(0);
                    match = match.substring(2, match.length());
                    match = match.substring(0, match.length() - 1);
                    String[] params = match.split("\\+");
                    for (int i = 0; i < params.length; i++) {
                        url += resolveParam(params[i], rawApi);
                    }
                    testingRunConfig.setOverriddenTestAppUrl(url);
                } else {
                    url = testingRunConfig.getOverriddenTestAppUrl();
                }

                String newUrl = ApiExecutor.replacePathFromConfig(rawApi.getRequest().getUrl(), testingRunConfig);
                URI uri = new URI(newUrl);
                String host = uri.getHost();
                if (uri.getPort() != -1) {
                    host += ":" + uri.getPort();
                }
                rawApi.getRequest().getHeaders().put(_HOST, Collections.singletonList(host));
                rawApi.getRequest().setUrl(newUrl);

            }

        } catch (Exception e) {
            //testLogs.add(new TestingRunResult.TestLog(TestingRunResult.TestLogType.ERROR, "unable to update host header for overridden test URL"));
            loggerMaker.errorAndAddToDb(e,"unable to update host header for overridden test URL",
                    LogDb.TESTING);
        }
    }

    public String resolveParam(String param, RawApi rawApi) {
        param = param.trim();
        String[] params = param.split("\\.");

        if (params.length == 1) {
            return params[0];
        }

        String key = params[params.length - 1];
        String val = rawApi.getRequest().getHeaders().get(key).get(0);
        return val;
    }

    public WorkflowTest buildWorkflowGraph(ExecutorNode reqNodes, RawApi rawApi, AuthMechanism authMechanism,
        List<CustomAuthType> customAuthTypes, ApiInfo.ApiInfoKey apiInfoKey, Map<String, Object> varMap, FilterNode validatorNode) {

            return convertToWorkflowGraph(reqNodes, rawApi, authMechanism, customAuthTypes, apiInfoKey, varMap, validatorNode);
        }

    public MultiExecTestResult triggerMultiExecution(WorkflowTest workflowTest, ExecutorNode reqNodes, RawApi rawApi, AuthMechanism authMechanism,
        List<CustomAuthType> customAuthTypes, ApiInfo.ApiInfoKey apiInfoKey, Map<String, Object> varMap, FilterNode validatorNode, boolean debug, List<TestingRunResult.TestLog> testLogs, Memory memory) {
        
        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        Graph graph = new Graph();
        graph.buildGraph(workflowTest);
        int id = Context.now();
        List<String> executionOrder = new ArrayList<>();
        WorkflowTestResult workflowTestResult = new WorkflowTestResult(id, workflowTest.getId(), new HashMap<>(), null, null);
        GraphExecutorRequest graphExecutorRequest = new GraphExecutorRequest(graph, graph.getNode("x1"), workflowTest, null, null, varMap, "conditional", workflowTestResult, new HashMap<>(), executionOrder);
        GraphExecutorResult graphExecutorResult = apiWorkflowExecutor.init(graphExecutorRequest, debug, testLogs, memory);
        return new MultiExecTestResult(graphExecutorResult.getWorkflowTestResult().getNodeResultMap(), graphExecutorResult.getVulnerable(), Confidence.HIGH, graphExecutorRequest.getExecutionOrder());
    }

    public WorkflowTest convertToWorkflowGraph(ExecutorNode reqNodes, RawApi rawApi, AuthMechanism authMechanism, 
        List<CustomAuthType> customAuthTypes, ApiInfo.ApiInfoKey apiInfoKey, Map<String, Object> varMap, FilterNode validatorNode) {

        ObjectMapper m = new ObjectMapper();
        String source, target;
        List<String> edges = new ArrayList<>();
        int edgeNumber = 1;
        LoginWorkflowGraphEdge edgeObj;
        Map<String,WorkflowNodeDetails> mapNodeIdToWorkflowNodeDetails = new HashMap<>();

        for (ExecutorNode reqNode: reqNodes.getChildNodes()) {

            source = "x"+ edgeNumber;

            String testId = null;
            try {
                Map<String,Object> mapValues = m.convertValue(reqNode.getValues(), Map.class);
                testId = (String) mapValues.get("test_name");
            } catch (Exception e) {
                try {
                    List<Object> listValues = (List<Object>) reqNode.getValues();
                    for (int i = 0; i < listValues.size(); i++) {
                        Map<String,Object> mapValues = m.convertValue(listValues.get(i), Map.class);
                        testId = (String) mapValues.get("test_name");
                        if (testId != null) {
                            break;
                        }
                    }
                } catch (Exception er) {
                    // TODO: handle exception
                }
                // TODO: handle exception
            }

            String successNodeId = reqNode.fetchConditionalString("success");
            String failureNodeId = reqNode.fetchConditionalString("failure");
            String waitInSecondsStr = reqNode.fetchConditionalString("wait");
            int waitInSeconds = 0;
            try {
                waitInSeconds = Integer.parseInt(waitInSecondsStr);
            } catch (Exception e) {
            }

            if (testId != null) {
                JSONObject json = new JSONObject() ;
                json.put("method", rawApi.getRequest().getMethod());
                json.put("requestPayload", rawApi.getRequest().getBody());
                json.put("path", rawApi.getRequest().getUrl());
                json.put("requestHeaders", rawApi.getRequest().getHeaders().toString());
                json.put("type", "");
                
                YamlNodeDetails yamlNodeDetails = new YamlNodeDetails(testId, null, reqNode, customAuthTypes, authMechanism, rawApi, apiInfoKey, rawApi.getOriginalMessage(), successNodeId, failureNodeId, waitInSeconds);
                mapNodeIdToWorkflowNodeDetails.put(source, yamlNodeDetails);
            } else {
                YamlNodeDetails yamlNodeDetails = new YamlNodeDetails(null, validatorNode, reqNode, customAuthTypes, authMechanism, rawApi, apiInfoKey, rawApi.getOriginalMessage(), successNodeId, failureNodeId, waitInSeconds);
                mapNodeIdToWorkflowNodeDetails.put(source, yamlNodeDetails);
            }

            target = successNodeId;
            if (edgeNumber != reqNodes.getChildNodes().size()) {
                if (target == null || target.equals("vulnerable") || target.equals("exit")) {
                    target = com.akto.testing.workflow_node_executor.Utils.evaluateNextNodeId(source);
                }
                edgeObj = new LoginWorkflowGraphEdge(source, target, target);
                edges.add(edgeObj.toString());
            } else {
                edgeObj = new LoginWorkflowGraphEdge(source, "terminal", "terminal");
                edges.add(edgeObj.toString());
            }
            target = failureNodeId;
            if (target != null && !target.equals("vulnerable") && !target.equals("exit")) {
                edgeObj = new LoginWorkflowGraphEdge(source, target, target);
                edges.add(edgeObj.toString());
            }

            edgeNumber++;

        }

        return new WorkflowTest(Context.now(), apiInfoKey.getApiCollectionId(), "", Context.now(), "", Context.now(),
                null, edges, mapNodeIdToWorkflowNodeDetails, WorkflowTest.State.DRAFT);
    }

    public TestResult validate(ExecutionResult attempt, RawApi rawApi, Map<String, Object> varMap, String logId, FilterNode validatorNode, ApiInfo.ApiInfoKey apiInfoKey) {
        if (attempt == null || attempt.getResponse() == null) {
            return null;
        }

        String msg = convertOriginalReqRespToString(attempt.getRequest(), attempt.getResponse());
        RawApi testRawApi = new RawApi(attempt.getRequest(), attempt.getResponse(), msg);
        boolean vulnerable = TestPlugin.validateValidator(validatorNode, rawApi, testRawApi , apiInfoKey, varMap, logId);
        if (vulnerable) {
            loggerMaker.infoAndAddToDb("found vulnerable " + logId, LogDb.TESTING);
        }
        double percentageMatch = 0;
        if (rawApi.getResponse() != null && testRawApi.getResponse() != null) {
            percentageMatch = compareWithOriginalResponse(
                rawApi.getResponse().getBody(), testRawApi.getResponse().getBody(), new HashMap<>()
            );
        }
        TestResult testResult = new TestResult(
                msg, rawApi.getOriginalMessage(), new ArrayList<>(), percentageMatch, vulnerable, TestResult.Confidence.HIGH, null
        );

        return testResult;
    }

    public ExecutorSingleOperationResp invokeOperation(String operationType, Object key, Object value, RawApi rawApi, Map<String, Object> varMap, AuthMechanism authMechanism, List<CustomAuthType> customAuthTypes, ApiInfo.ApiInfoKey apiInfoKey) {
        try {
            ExecutorSingleOperationResp resp = runOperation(operationType, rawApi, key, value, varMap, authMechanism, customAuthTypes, apiInfoKey);
            return resp;
        } catch(Exception e) {
            return new ExecutorSingleOperationResp(false, "error executing executor operation " + e.getMessage());
        }
    }


    private static boolean removeAuthIfNotChanged(RawApi originalRawApi, RawApi testRawApi, String authMechanismHeaderKey, List<CustomAuthType> customAuthTypes) {
        boolean removed = false;
        // find set of all headers and body params that didn't change
        Map<String, List<String>> originalRequestHeaders = originalRawApi.fetchReqHeaders();
        Map<String, List<String>> testRequestHeaders = testRawApi.fetchReqHeaders();
        Set<String> unchangedHeaders = headerValuesUnchanged(originalRequestHeaders, testRequestHeaders);

        String originalJsonRequestBody = originalRawApi.getRequest().getJsonRequestBody();
        String testJsonRequestBody = testRawApi.getRequest().getJsonRequestBody();
        Set<String> unchangedBodyKeys = bodyValuesUnchanged(originalJsonRequestBody, testJsonRequestBody);

        // then loop over custom auth types and hardcoded auth mechanism to see if any auth token hasn't changed
        List<String> authHeaders = new ArrayList<>();
        List<String> authBodyParams = new ArrayList<>();

        for (CustomAuthType customAuthType : customAuthTypes) {
            List<String> customAuthTypeHeaderKeys = customAuthType.getHeaderKeys();
            authHeaders.addAll(customAuthTypeHeaderKeys);

            List<String> customAuthTypePayloadKeys = customAuthType.getPayloadKeys();
            authBodyParams.addAll(customAuthTypePayloadKeys);
        }

        authHeaders.add(authMechanismHeaderKey);

        for (String headerAuthKey: authHeaders) {
            if (unchangedHeaders.contains(headerAuthKey)) {
                removed = Operations.deleteHeader(testRawApi, headerAuthKey).getErrMsg().isEmpty() || removed;
            }
        }

        for (String payloadAuthKey: authBodyParams) {
            if (unchangedBodyKeys.contains(payloadAuthKey)) {
                removed = Operations.deleteBodyParam(testRawApi, payloadAuthKey).getErrMsg().isEmpty() || removed;
            }
        }

        return removed;
    }

    private static boolean removeCustomAuth(RawApi rawApi, List<CustomAuthType> customAuthTypes) {
        boolean removed = false;

        for (CustomAuthType customAuthType : customAuthTypes) {
            List<String> customAuthTypeHeaderKeys = customAuthType.getHeaderKeys();
            for (String headerAuthKey: customAuthTypeHeaderKeys) {
                removed = Operations.deleteHeader(rawApi, headerAuthKey).getErrMsg().isEmpty() || removed;
            }
            List<String> customAuthTypePayloadKeys = customAuthType.getPayloadKeys();
            for (String payloadAuthKey: customAuthTypePayloadKeys) {
                removed = Operations.deleteBodyParam(rawApi, payloadAuthKey).getErrMsg().isEmpty() || removed;
            }
        }
        return removed;
    }

    public static ExecutorSingleOperationResp modifyAuthTokenInRawApi(TestRoles testRole, RawApi rawApi) {

        AuthMechanism authMechanismForRole = testRole.findMatchingAuthMechanism(rawApi);

        if (authMechanismForRole == null) {
            return null;
        }

        List<AuthParam> authParamList = authMechanismForRole.getAuthParams();

        if (authParamList.isEmpty()) {
            return null;
        }

        boolean eligibleForCachedToken = AuthMechanismTypes.LOGIN_REQUEST.toString().equalsIgnoreCase(authMechanismForRole.getType());
        boolean shouldCalculateNewToken = eligibleForCachedToken && authMechanismForRole.isCacheExpired();

        if (shouldCalculateNewToken) {
            try {
                LoginFlowResponse loginFlowResponse= new LoginFlowResponse();
                loggerMaker.infoAndAddToDb("trying to fetch token of step builder type for role " + testRole.getName(), LogDb.TESTING);
                loginFlowResponse = TestExecutor.executeLoginFlow(authMechanismForRole, null);
                if (!loginFlowResponse.getSuccess())
                    throw new Exception(loginFlowResponse.getError());
            } catch (Exception e) {
                return new ExecutorSingleOperationResp(false, "Failed to replace roles_access_context: " + e.getMessage());
            }
        }

        ExecutorSingleOperationResp ret = authMechanismForRole.addAuthToRequest(rawApi.getRequest(), eligibleForCachedToken);

        return ret;        

    }

    private static ConcurrentHashMap<String, TestRoles> roleCache = new ConcurrentHashMap<>();
	
    public static TestRoles fetchOrFindAttackerRole() {
        return fetchOrFindTestRole("ATTACKER_TOKEN_ALL", false);
    }

    public synchronized static TestRoles fetchOrFindTestRole(String name, boolean isId) {
        if (roleCache == null) {
            roleCache = new ConcurrentHashMap<>();
        }
        if (roleCache.containsKey(name)) {
            return roleCache.get(name);
        }
        if(!isId){
            TestRoles testRole = TestRolesDao.instance.findOne(TestRoles.NAME, name);
            roleCache.put(name, testRole);
            return roleCache.get(name);
        }else{
            TestRoles testRole = TestRolesDao.instance.findOne(Constants.ID, new ObjectId(name));
            roleCache.put(name, testRole);
            return roleCache.get(name);
        }
    }    

    public static void clearRoleCache() {
        if (Executor.roleCache != null) {
            roleCache.clear();
        }
    }

    public ExecutorSingleOperationResp runOperation(String operationType, RawApi rawApi, Object key, Object value, Map<String, Object> varMap, AuthMechanism authMechanism, List<CustomAuthType> customAuthTypes, ApiInfo.ApiInfoKey apiInfoKey) {
        switch (operationType.toLowerCase()) {
            case "send_ssrf_req":
                String keyValue = key.toString().replaceAll("\\$\\{random_uuid\\}", "");
                String url = Utils.extractValue(keyValue, "url=");
                String redirectUrl = Utils.extractValue(keyValue, "redirect_url=");
                List<String> uuidList = (List<String>) varMap.getOrDefault("random_uuid", new ArrayList<>());
                String generatedUUID =  UUID.randomUUID().toString();
                uuidList.add(generatedUUID);
                varMap.put("random_uuid", uuidList);

                BasicDBObject response = OrganizationsDao.getBillingTokenForAuth();
                if(response.getString("token") != null){
                    String tokenVal = response.getString("token");
                    return Utils.sendRequestToSsrfServer(url + generatedUUID, redirectUrl, tokenVal);
                }else{
                    return new ExecutorSingleOperationResp(false, response.getString("error"));
                }
            case "attach_file":
                String headerValue;

                if (key instanceof Map) {
                    Map<String, Object> fileAttachMap = (Map<String, Object>) key;
                    if (fileAttachMap.isEmpty()) {
                        return new ExecutorSingleOperationResp(false, "attach_file: Map cannot be empty");
                    }
                    
                    if (fileAttachMap.size() == 1) {
                        // Single file: use simple format "fieldName::fileUrl"
                        Map.Entry<String, Object> entry = fileAttachMap.entrySet().iterator().next();
                        String fieldName = entry.getKey();
                        String fileUrl = entry.getValue().toString();
                        headerValue = fieldName + "::" + fileUrl;
                    } else {
                        // Multiple files: use JSON array format [{"field":"name","url":"url"},...]
                        JSONArray filesArray = new JSONArray();
                        for (Map.Entry<String, Object> entry : fileAttachMap.entrySet()) {
                            JSONObject fileObj = new JSONObject();
                            fileObj.put("field", entry.getKey());
                            fileObj.put("url", entry.getValue().toString());
                            filesArray.put(fileObj);
                        }
                        headerValue = filesArray.toString();
                    }
                } else if (key != null && value != null) {
                    // YAML format: attach_file: {key: fieldName, value: fileUrl}
                    String fieldName = key.toString();
                    String fileUrl = value.toString();
                    headerValue = fieldName + "::" + fileUrl;
                } else if (key != null) {
                    // Legacy format: attach_file: fileUrl (key is the URL, no field name)
                    headerValue = "file::" + key.toString();
                } else {
                    return new ExecutorSingleOperationResp(false, "attach_file: Missing file URL");
                }
                
                return Operations.addHeader(rawApi, Constants.AKTO_ATTACH_FILE , headerValue);

            case "modify_header":
                Object epochVal = Utils.getEpochTime(value);
                String keyStr = key.toString();
                String valStr = value.toString();

                String ACCESS_ROLES_CONTEXT = "${roles_access_context.";
                if (keyStr.startsWith(ACCESS_ROLES_CONTEXT)) {

                    keyStr = keyStr.replace(ACCESS_ROLES_CONTEXT, "");
                    keyStr = keyStr.substring(0,keyStr.length()-1).trim();
                    TestRoles testRole = TestRolesDao.instance.findOne(TestRoles.NAME, keyStr);
                    if (testRole == null) {
                        return new ExecutorSingleOperationResp(false, "Test Role " + keyStr +  " Doesn't Exist ");
                    }

                    ExecutorSingleOperationResp insertedAuthResp = modifyAuthTokenInRawApi(testRole, rawApi);
                    if (insertedAuthResp != null) {
                        return insertedAuthResp;
                    }

                    return new ExecutorSingleOperationResp(true, "Unable to match request headers " + key);
                } else {
                    epochVal = Utils.getEpochTime(valStr);
                    if (epochVal != null) {
                        valStr = epochVal.toString();
                    }
                    return Operations.modifyHeader(rawApi, keyStr, valStr);
                }
            case "remove_auth_header":
                List<String> authHeaders = (List<String>) varMap.get("auth_headers");
                boolean removed = false;
                for (String header: authHeaders) {
                    removed = Operations.deleteHeader(rawApi, header).getErrMsg().isEmpty() || removed;
                }
                removed = removeCustomAuth(rawApi, customAuthTypes) || removed ;
                if (removed) {
                    if (apiInfoKey.getApiCollectionId() == 1111111111) {
                        Operations.addHeader(rawApi, Constants.AKTO_REMOVE_AUTH , "0");
                    }
                    return new ExecutorSingleOperationResp(true, "");
                } else {
                    return new ExecutorSingleOperationResp(false, "header key not present");
                }
            case "replace_auth_header":
                RawApi copy = rawApi.copy();
                authHeaders = (List<String>) varMap.get("auth_headers");
                String authHeader;
                if (authHeaders == null) {
                    return new ExecutorSingleOperationResp(false, "auth headers missing from var map");
                }
                if (authHeaders.size() == 0 || authHeaders.size() > 1){
                    if (authMechanism == null || authMechanism.getAuthParams() == null || authMechanism.getAuthParams().size() == 0) {
                        return new ExecutorSingleOperationResp(false, "auth headers missing");
                    }
                    authHeader = authMechanism.getAuthParams().get(0).getKey();
                } else {
                    authHeader = authHeaders.get(0);
                }
                boolean modifiedAtLeastOne = false;
                String authVal;

                // value of replace_auth_header can be
                //  1. boolean -> Then we only care about hardcoded auth params (handled in the else part)
                //  2. auth_context
                //      a. For hardcoded auth mechanism
                //      b. For custom auths (header and body params)

                if (VariableResolver.isAuthContext(key)) {
                    // resolve context for auth mechanism keys
                    authVal = VariableResolver.resolveAuthContext(key, rawApi.getRequest().getHeaders(), authHeader);
                    if (authVal != null) {
                        ExecutorSingleOperationResp authMechanismContextResult = Operations.modifyHeader(rawApi, authHeader, authVal);
                        modifiedAtLeastOne = modifiedAtLeastOne || authMechanismContextResult.getSuccess();
                    }

                    for (CustomAuthType customAuthType : customAuthTypes) {
                        // resolve context for custom auth header keys
                        List<String> customAuthHeaderKeys = customAuthType.getHeaderKeys();
                        for (String customAuthHeaderKey: customAuthHeaderKeys) {
                            authVal = VariableResolver.resolveAuthContext(key.toString(), rawApi.getRequest().getHeaders(), customAuthHeaderKey);
                            if (authVal == null) continue;
                            ExecutorSingleOperationResp customAuthContextResult = Operations.modifyHeader(rawApi, customAuthHeaderKey, authVal);
                            modifiedAtLeastOne = modifiedAtLeastOne || customAuthContextResult.getSuccess();
                        }

                        // resolve context for custom auth body params
                        List<String> customAuthPayloadKeys = customAuthType.getPayloadKeys();
                        for (String customAuthPayloadKey: customAuthPayloadKeys) {
                            authVal = VariableResolver.resolveAuthContext(key.toString(), rawApi.getRequest().getHeaders(), customAuthPayloadKey);
                            if (authVal == null) continue;
                            ExecutorSingleOperationResp customAuthContextResult = Operations.modifyBodyParam(rawApi, customAuthPayloadKey, authVal);
                            modifiedAtLeastOne = modifiedAtLeastOne || customAuthContextResult.getSuccess();
                        }
                    }

                } else {
                    if (authMechanism == null || authMechanism.getAuthParams() == null || authMechanism.getAuthParams().size() == 0) {
                        return new ExecutorSingleOperationResp(false, "auth headers missing");
                    }

                    ExecutorSingleOperationResp opResponse = modifyAuthTokenInRawApi(Executor.fetchOrFindAttackerRole(), rawApi);
                    if (opResponse == null) {
                        return new ExecutorSingleOperationResp(false, "auth headers missing");
                    }

                    modifiedAtLeastOne = modifiedAtLeastOne || opResponse.getSuccess();
                }

                // once all the replacement has been done.. .remove all the auth keys that were not impacted by the change by comparing it with initial request
                removeAuthIfNotChanged(copy,rawApi, authHeader, customAuthTypes);

                if (modifiedAtLeastOne) {
                    return new ExecutorSingleOperationResp(true, "");
                } else {
                    return new ExecutorSingleOperationResp(false, "Couldn't find token");
                }
            case "test_name":
                return new ExecutorSingleOperationResp(true, "");
            case "jwt_replace_body":
                JWTPayloadReplacer jwtPayloadReplacer = new JWTPayloadReplacer(key.toString());
                boolean modified = false;
                for (String k: rawApi.getRequest().getHeaders().keySet()) {
                    List<String> hList = rawApi.getRequest().getHeaders().getOrDefault(k, new ArrayList<>());
                    if (hList.size() == 0){
                        continue;
                    }
                    String hVal = hList.get(0);
                    String[] splitValue = hVal.toString().split(" ");
                    List<String> finalValue = new ArrayList<>();

                    boolean isJwt = false;

                    for (String val: splitValue) {
                        if (!KeyTypes.isJWT(val)) {
                            finalValue.add(val);
                            continue;
                        }
                        isJwt = true;
                        String modifiedHeaderVal = null;

                        try {
                            modifiedHeaderVal = jwtPayloadReplacer.jwtModify("", val);
                        } catch(Exception e) {
                            return null;
                        }
                        finalValue.add(modifiedHeaderVal);
                    }

                    if (!isJwt) {
                        continue;
                    }
                    modified = true;

                    return Operations.modifyHeader(rawApi, k, String.join( " ", finalValue));
                }
                if (!modified) {
                    return new ExecutorSingleOperationResp(true, "");
                }
            default:
                return Utils.modifySampleDataUtil(operationType, rawApi, key, value, varMap, apiInfoKey);

        }
    }

}
