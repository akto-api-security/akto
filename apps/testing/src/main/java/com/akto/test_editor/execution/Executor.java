package com.akto.test_editor.execution;

import java.util.*;

import com.akto.dao.CustomAuthTypeDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dao.test_editor.TestEditorEnums.ExecutorOperandTypes;
import com.akto.dao.testing.TestRolesDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.CustomAuthType;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.testing.*;
import com.akto.dto.testing.sources.AuthWithCond;
import com.akto.dto.test_editor.ExecutionResult;
import com.akto.dto.test_editor.ExecutorNode;
import com.akto.dto.test_editor.ExecutorSingleOperationResp;
import com.akto.dto.test_editor.ExecutorSingleRequest;
import com.akto.dto.test_editor.FilterNode;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.testing.ApiExecutor;
import com.akto.testing.TestExecutor;
import com.akto.util.enums.LoginFlowEnums;
import com.akto.util.enums.LoginFlowEnums.AuthMechanismTypes;
import com.akto.utils.RedactSampleData;
import com.akto.dto.api_workflow.Graph;
import com.akto.dto.test_editor.*;
import com.akto.dto.testing.*;
import com.akto.dto.testing.TestResult.Confidence;
import com.akto.dto.testing.TestResult.TestError;
import com.akto.dto.testing.sources.AuthWithCond;
import com.akto.dto.type.KeyTypes;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.rules.TestPlugin;
import com.akto.test_editor.Utils;
import com.akto.testing.ApiExecutor;
import com.akto.testing.ApiWorkflowExecutor;
import com.akto.testing.TestExecutor;
import com.akto.util.enums.LoginFlowEnums;
import com.akto.util.enums.LoginFlowEnums.AuthMechanismTypes;
import com.akto.util.modifier.JWTPayloadReplacer;
import com.akto.utils.RedactSampleData;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.akto.rules.TestPlugin.extractAllValuesFromPayload;
import static com.akto.test_editor.Utils.bodyValuesUnchanged;
import static com.akto.test_editor.Utils.headerValuesUnchanged;

import com.mongodb.client.model.Filters;
import org.json.JSONObject;
import com.mongodb.client.model.Filters;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;

public class Executor {

    private static final LoggerMaker loggerMaker = new LoggerMaker(Executor.class);

    public final String _HOST = "host";

    public YamlTestResult execute(ExecutorNode node, RawApi rawApi, Map<String, Object> varMap, String logId,
        AuthMechanism authMechanism, FilterNode validatorNode, ApiInfo.ApiInfoKey apiInfoKey, TestingRunConfig testingRunConfig, List<CustomAuthType> customAuthTypes) {
        List<GenericTestResult> result = new ArrayList<>();
        
        ExecutionListBuilder executionListBuilder = new ExecutionListBuilder();
        List<ExecutorNode> executorNodes = new ArrayList<>();
        ExecutionOrderResp executionOrderResp = executionListBuilder.parseExecuteOperations(node, executorNodes);

        boolean followRedirect = executionOrderResp.getFollowRedirect();
        
        TestResult invalidExecutionResult = new TestResult(null, rawApi.getOriginalMessage(), Collections.singletonList(TestError.INVALID_EXECUTION_BLOCK.getMessage()), 0, false, TestResult.Confidence.HIGH, null);
        YamlTestResult yamlTestResult;
        WorkflowTest workflowTest = null;
        if (node.getChildNodes().size() < 2) {
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
                    if (role.getDefaultAuthMechanism() != null) {
                        loggerMaker.infoAndAddToDb("attempting to override auth " + logId, LogDb.TESTING);
                        modifyAuthTokenInRawApi(role, sampleRawApi);
                    } else {
                        loggerMaker.infoAndAddToDb("Default auth mechanism absent: " + logId, LogDb.TESTING);
                    }
                } else {
                    loggerMaker.infoAndAddToDb("Endpoint didn't satisfy endpoint condition for testRole" + logId, LogDb.TESTING);
                }
            } else {
                String reason = "Test role has been deleted";
                loggerMaker.infoAndAddToDb(reason + ", going ahead with sample auth", LogDb.TESTING);
            }
        }

        boolean requestSent = false;

        String executionType = node.getChildNodes().get(0).getValues().toString();
        if (executionType.equals("multiple")) {
            workflowTest = buildWorkflowGraph(reqNodes, rawApi, authMechanism, customAuthTypes, apiInfoKey, varMap, validatorNode);
            result.add(triggerMultiExecution(workflowTest, reqNodes, rawApi, authMechanism, customAuthTypes, apiInfoKey, varMap, validatorNode));
            yamlTestResult = new YamlTestResult(result, workflowTest);
            
            return yamlTestResult;
        }

        List<RawApi> testRawApis = new ArrayList<>();
        testRawApis.add(sampleRawApi.copy());
        ExecutorAlgorithm executorAlgorithm = new ExecutorAlgorithm(sampleRawApi, varMap, authMechanism, customAuthTypes);
        Map<Integer, ExecuteAlgoObj> algoMap = new HashMap<>();
        ExecutorSingleRequest singleReq = executorAlgorithm.execute(executorNodes, 0, algoMap, testRawApis, false, 0);
        
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

                // change host header in case of override URL ( if not already changed by test template )
                try {
                    List<String> originalHostHeaders = rawApi.getRequest().getHeaders().getOrDefault(_HOST, new ArrayList<>());
                    List<String> attemptHostHeaders = testReq.getRequest().getHeaders().getOrDefault(_HOST, new ArrayList<>());

                    if (!originalHostHeaders.isEmpty()
                        && originalHostHeaders.get(0) != null
                        && !attemptHostHeaders.isEmpty()
                        && attemptHostHeaders.get(0) != null
                        && originalHostHeaders.get(0).equals(attemptHostHeaders.get(0))
                        && testingRunConfig != null
                        && !StringUtils.isEmpty(testingRunConfig.getOverriddenTestAppUrl())) {

                        String url = ApiExecutor.prepareUrl(testReq.getRequest(), testingRunConfig);
                        URI uri = new URI(url);
                        String host = uri.getHost();
                        if (uri.getPort() != -1) {
                            host += ":" + uri.getPort();
                        }
                        testReq.getRequest().getHeaders().put(_HOST, Collections.singletonList(host));
                    }

                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e,"unable to update host header for overridden test URL",
                            LogDb.TESTING);
                }
                    
                // follow redirects = true for now
                testResponse = ApiExecutor.sendRequest(testReq.getRequest(), followRedirect, testingRunConfig);
                requestSent = true;
                ExecutionResult attempt = new ExecutionResult(singleReq.getSuccess(), singleReq.getErrMsg(), testReq.getRequest(), testResponse);
                TestResult res = validate(attempt, sampleRawApi, varMap, logId, validatorNode, apiInfoKey);
                if (res != null) {
                    result.add(res);
                }
                vulnerable = res.getVulnerable();
            } catch(Exception e) {
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

    public WorkflowTest buildWorkflowGraph(ExecutorNode reqNodes, RawApi rawApi, AuthMechanism authMechanism,
        List<CustomAuthType> customAuthTypes, ApiInfo.ApiInfoKey apiInfoKey, Map<String, Object> varMap, FilterNode validatorNode) {

            return convertToWorkflowGraph(reqNodes, rawApi, authMechanism, customAuthTypes, apiInfoKey, varMap, validatorNode);
        }

    public MultiExecTestResult triggerMultiExecution(WorkflowTest workflowTest, ExecutorNode reqNodes, RawApi rawApi, AuthMechanism authMechanism,
        List<CustomAuthType> customAuthTypes, ApiInfo.ApiInfoKey apiInfoKey, Map<String, Object> varMap, FilterNode validatorNode) {
        
        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        Graph graph = new Graph();
        graph.buildGraph(workflowTest);
        int id = Context.now();
        List<String> executionOrder = new ArrayList<>();
        WorkflowTestResult workflowTestResult = new WorkflowTestResult(id, workflowTest.getId(), new HashMap<>(), null, null);
        GraphExecutorRequest graphExecutorRequest = new GraphExecutorRequest(graph, graph.getNode("x1"), workflowTest, null, null, varMap, "conditional", workflowTestResult, new HashMap<>(), executionOrder);
        GraphExecutorResult graphExecutorResult = apiWorkflowExecutor.init(graphExecutorRequest);
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

            if (testId != null) {
                JSONObject json = new JSONObject() ;
                json.put("method", rawApi.getRequest().getMethod());
                json.put("requestPayload", rawApi.getRequest().getBody());
                json.put("path", rawApi.getRequest().getUrl());
                json.put("requestHeaders", rawApi.getRequest().getHeaders().toString());
                json.put("type", "");
                
                YamlNodeDetails yamlNodeDetails = new YamlNodeDetails(testId, null, reqNode, customAuthTypes, authMechanism, rawApi, apiInfoKey, rawApi.getOriginalMessage(), successNodeId, failureNodeId);
                mapNodeIdToWorkflowNodeDetails.put(source, yamlNodeDetails);
            } else {
                YamlNodeDetails yamlNodeDetails = new YamlNodeDetails(null, validatorNode, reqNode, customAuthTypes, authMechanism, rawApi, apiInfoKey, rawApi.getOriginalMessage(), successNodeId, failureNodeId);
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

        String msg = RedactSampleData.convertOriginalReqRespToString(attempt.getRequest(), attempt.getResponse());
        RawApi testRawApi = new RawApi(attempt.getRequest(), attempt.getResponse(), msg);
        boolean vulnerable = TestPlugin.validateValidator(validatorNode, rawApi, testRawApi , apiInfoKey, varMap, logId);
        if (vulnerable) {
            loggerMaker.infoAndAddToDb("found vulnerable " + logId, LogDb.TESTING);
        }
        double percentageMatch = 0;
        if (rawApi.getResponse() != null && testRawApi.getResponse() != null) {
            percentageMatch = TestPlugin.compareWithOriginalResponse(
                rawApi.getResponse().getBody(), testRawApi.getResponse().getBody(), new HashMap<>()
            );
        }
        TestResult testResult = new TestResult(
                msg, rawApi.getOriginalMessage(), new ArrayList<>(), percentageMatch, vulnerable, TestResult.Confidence.HIGH, null
        );

        return testResult;
    }

    public ExecutorSingleOperationResp invokeOperation(String operationType, Object key, Object value, RawApi rawApi, Map<String, Object> varMap, AuthMechanism authMechanism, List<CustomAuthType> customAuthTypes) {
        try {
            ExecutorSingleOperationResp resp = runOperation(operationType, rawApi, key, value, varMap, authMechanism, customAuthTypes);
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

    private ExecutorSingleOperationResp modifyAuthTokenInRawApi(TestRoles testRole, RawApi rawApi) {
        Map<String, List<String>> rawHeaders = rawApi.fetchReqHeaders();
        for(AuthWithCond authWithCond: testRole.getAuthWithCondList()) {

            boolean allSatisfied = true;
            for(String headerKey: authWithCond.getHeaderKVPairs().keySet()) {
                String headerVal = authWithCond.getHeaderKVPairs().get(headerKey);

                List<String> rawHeaderValue = rawHeaders.getOrDefault(headerKey.toLowerCase(), new ArrayList<>());
                if (!rawHeaderValue.contains(headerVal)) {
                    allSatisfied = false;
                    break;
                }
            }

            if (allSatisfied) {
                AuthMechanism authMechanismForRole = authWithCond.getAuthMechanism();

                if (AuthMechanismTypes.LOGIN_REQUEST.toString().equalsIgnoreCase(authMechanismForRole.getType())) {
                    try {
                        LoginFlowResponse loginFlowResponse = TestExecutor.executeLoginFlow(authWithCond.getAuthMechanism(), null);
                        if (!loginFlowResponse.getSuccess())
                            throw new Exception(loginFlowResponse.getError());

                        authMechanismForRole.setType(LoginFlowEnums.AuthMechanismTypes.HARDCODED.name());
                    } catch (Exception e) {
                        return new ExecutorSingleOperationResp(false, "Failed to replace roles_access_context: " + e.getMessage());
                    }
                }

                if (!authMechanismForRole.getType().equalsIgnoreCase(AuthMechanismTypes.HARDCODED.toString())) {
                    return new ExecutorSingleOperationResp(false, "Auth type is not HARDCODED");
                }

                List<AuthParam> authParamList = authMechanismForRole.getAuthParams();
                if (!authParamList.isEmpty()) {
                    ExecutorSingleOperationResp ret = null;
                    for (AuthParam authParam1: authParamList) {
                        ret = Operations.modifyHeader(rawApi, authParam1.getKey().toLowerCase(), authParam1.getValue());
                    }

                    return ret;
                }
            }
        }

        return null;
    }

    public ExecutorSingleOperationResp runOperation(String operationType, RawApi rawApi, Object key, Object value, Map<String, Object> varMap, AuthMechanism authMechanism, List<CustomAuthType> customAuthTypes) {
        switch (operationType.toLowerCase()) {
            case "add_body_param":
                return Operations.addBody(rawApi, key.toString(), value);
            case "modify_body_param":
                return Operations.modifyBodyParam(rawApi, key.toString(), value);
            case "delete_graphql_field":
                return Operations.deleteGraphqlField(rawApi, key.toString());
            case "add_graphql_field":
                return Operations.addGraphqlField(rawApi, key.toString(), value.toString());
            case "modify_graphql_field":
                return Operations.modifyGraphqlField(rawApi, key.toString(), value.toString());
            case "delete_body_param":
                return Operations.deleteBodyParam(rawApi, key.toString());
            case "replace_body":
                String newPayload = rawApi.getRequest().getBody();
                if (key instanceof Map) {
                    Map<String, Map<String, String>> regexReplace = (Map) key;
                    String payload = rawApi.getRequest().getBody();
                    Map<String, String> regexInfo = regexReplace.get("regex_replace");
                    String regex = regexInfo.get("regex");
                    String replaceWith = regexInfo.get("replace_with");
                    newPayload = Utils.applyRegexModifier(payload, regex, replaceWith);
                } else {
                    newPayload = key.toString();
                }
                return Operations.replaceBody(rawApi, newPayload);
            case "add_header":
                return Operations.addHeader(rawApi, key.toString(), value.toString());
            case "modify_header":
                String keyStr = key.toString();
                String valStr = value.toString();

                String ACCESS_ROLES_CONTEXT = "${roles_access_context.";
                if (keyStr.startsWith(ACCESS_ROLES_CONTEXT)) {

                    keyStr = keyStr.replace(ACCESS_ROLES_CONTEXT, "");
                    keyStr = keyStr.substring(0,keyStr.length()-1).trim();
                    TestRoles testRole = TestRolesDao.instance.findOne(TestRoles.NAME, keyStr);

                    ExecutorSingleOperationResp insertedAuthResp = modifyAuthTokenInRawApi(testRole, rawApi);
                    if (insertedAuthResp != null) {
                        return insertedAuthResp;
                    }

                    return new ExecutorSingleOperationResp(true, "Unable to match request headers " + key);
                } else {
                    return Operations.modifyHeader(rawApi, keyStr, valStr);
                }
            case "delete_header":
                return Operations.deleteHeader(rawApi, key.toString());
            case "add_query_param":
                return Operations.addQueryParam(rawApi, key.toString(), value);
            case "modify_query_param":
                return Operations.modifyQueryParam(rawApi, key.toString(), value);
            case "delete_query_param":
                return Operations.deleteQueryParam(rawApi, key.toString());
            case "modify_url":
                String newUrl = null;
                UrlModifierPayload urlModifierPayload = Utils.fetchUrlModifyPayload(key.toString());
                if (urlModifierPayload != null) {
                    newUrl = Utils.buildNewUrl(urlModifierPayload, rawApi.getRequest().getUrl());
                } else {
                    newUrl = key.toString();
                }
                return Operations.modifyUrl(rawApi, newUrl);
            case "modify_method":
                return Operations.modifyMethod(rawApi, key.toString());
            case "remove_auth_header":
                List<String> authHeaders = (List<String>) varMap.get("auth_headers");
                boolean removed = false;
                for (String header: authHeaders) {
                    removed = Operations.deleteHeader(rawApi, header).getErrMsg().isEmpty() || removed;
                }
                removed = removeCustomAuth(rawApi, customAuthTypes) || removed ;
                if (removed) {
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
                    authVal = authMechanism.getAuthParams().get(0).getValue();
                    ExecutorSingleOperationResp result = Operations.modifyHeader(rawApi, authHeader, authVal);
                    modifiedAtLeastOne = modifiedAtLeastOne || result.getSuccess();
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
                return new ExecutorSingleOperationResp(false, "invalid operationType");

        }
    }

}
