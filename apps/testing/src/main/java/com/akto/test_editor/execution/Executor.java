package com.akto.test_editor.execution;

import com.akto.dao.context.Context;
import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dao.test_editor.TestEditorEnums.ExecutorOperandTypes;
import com.akto.dto.ApiInfo;
import com.akto.dto.CustomAuthType;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.*;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.GenericTestResult;
import com.akto.dto.testing.LoginWorkflowGraphEdge;
import com.akto.dto.testing.MultiExecTestResult;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestResult.Confidence;
import com.akto.dto.testing.TestResult.TestError;
import com.akto.dto.testing.WorkflowTestResult.NodeResult;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.WorkflowNodeDetails;
import com.akto.dto.testing.WorkflowTest;
import com.akto.dto.testing.WorkflowTestResult;
import com.akto.dto.testing.NodeDetails.YamlNodeDetails;
import com.akto.dto.type.KeyTypes;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.rules.TestPlugin;
import com.akto.test_editor.Utils;
import com.akto.testing.ApiExecutor;
import com.akto.testing.ApiWorkflowExecutor;
import com.akto.util.modifier.JWTPayloadModifier;
import com.akto.util.modifier.NoneAlgoJWTModifier;
import com.akto.utils.RedactSampleData;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

public class Executor {

    private static final LoggerMaker loggerMaker = new LoggerMaker(Executor.class);

    public List<GenericTestResult> execute(ExecutorNode node, RawApi rawApi, Map<String, Object> varMap, String logId,
        AuthMechanism authMechanism, FilterNode validatorNode, ApiInfo.ApiInfoKey apiInfoKey, TestingRunConfig testingRunConfig, List<CustomAuthType> customAuthTypes) {
        List<GenericTestResult> result = new ArrayList<>();
        
        TestResult invalidExecutionResult = new TestResult(null, rawApi.getOriginalMessage(), Collections.singletonList(TestError.INVALID_EXECUTION_BLOCK.getMessage()), 0, false, TestResult.Confidence.HIGH, null);

        if (node.getChildNodes().size() < 2) {
            loggerMaker.errorAndAddToDb("executor child nodes is less than 2, returning empty execution result " + logId, LogDb.TESTING);
            result.add(invalidExecutionResult);
            return result;
        }
        ExecutorNode reqNodes = node.getChildNodes().get(1);
        OriginalHttpResponse testResponse;
        RawApi sampleRawApi = rawApi.copy();
        ExecutorSingleRequest singleReq = null;
        if (reqNodes.getChildNodes() == null || reqNodes.getChildNodes().size() == 0) {
            result.add(invalidExecutionResult);
            return result;
        }

        boolean requestSent = false;

        List<String> error_messages = new ArrayList<>();

        String executionType = node.getChildNodes().get(0).getValues().toString();
        if (executionType.equals("multiple")) {
            result.add(triggerMultiExecution(reqNodes, rawApi, authMechanism, customAuthTypes, apiInfoKey, varMap, validatorNode));
            return result;
        }

        for (ExecutorNode reqNode: reqNodes.getChildNodes()) {
            // make copy of varMap as well
            List<RawApi> sampleRawApis = new ArrayList<>();
            sampleRawApis.add(sampleRawApi);

            singleReq = buildTestRequest(reqNode, null, sampleRawApis, varMap, authMechanism, customAuthTypes);
            List<RawApi> testRawApis = new ArrayList<>();
            testRawApis = singleReq.getRawApis();
            if (testRawApis == null) {
                error_messages.add(singleReq.getErrMsg());
                continue;
            }
            boolean vulnerable = false;
            for (RawApi testReq: testRawApis) {
                if (vulnerable) { //todo: introduce a flag stopAtFirstMatch
                    break;
                }
                try {
                    // follow redirects = true for now
                    testResponse = ApiExecutor.sendRequest(testReq.getRequest(), singleReq.getFollowRedirect(), testingRunConfig);
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
        }

        if(result.isEmpty()){
            if(requestSent){
                error_messages.add(TestError.API_REQUEST_FAILED.getMessage());
            } else {
                error_messages.add(TestError.NO_API_REQUEST.getMessage());
            }
            result.add(new TestResult(null, rawApi.getOriginalMessage(), error_messages, 0, false, TestResult.Confidence.HIGH, null));
        }

        return result;
    }

    public MultiExecTestResult triggerMultiExecution(ExecutorNode reqNodes, RawApi rawApi, AuthMechanism authMechanism,
        List<CustomAuthType> customAuthTypes, ApiInfo.ApiInfoKey apiInfoKey, Map<String, Object> varMap, FilterNode validatorNode) {
        WorkflowTest workflowTest = convertToWorkflowGraph(reqNodes, rawApi, authMechanism, customAuthTypes, apiInfoKey, varMap, validatorNode);
        
        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        WorkflowTestResult workflowTestResult = apiWorkflowExecutor.init(workflowTest, null, null, varMap);
        
        for (Map.Entry<String, NodeResult> entry : workflowTestResult.getNodeResultMap().entrySet()) {
            NodeResult nodeResult = entry.getValue();
            if (!nodeResult.isVulnerable()) {
                return new MultiExecTestResult(workflowTestResult.getNodeResultMap(), false, Confidence.HIGH);
            }
        }
        return new MultiExecTestResult(workflowTestResult.getNodeResultMap(), true, Confidence.HIGH);
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

            source = (edgeNumber==1)? "1" : "x"+ (edgeNumber - 1);
            target = "x"+ edgeNumber;
            edgeNumber += 1;

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

            if (testId != null) {
                edgeObj = new LoginWorkflowGraphEdge(source, target, target);
                edges.add(edgeObj.toString());
                JSONObject json = new JSONObject() ;
                json.put("method", rawApi.getRequest().getMethod());
                json.put("requestPayload", rawApi.getRequest().getBody());
                json.put("path", rawApi.getRequest().getUrl());
                json.put("requestHeaders", rawApi.getRequest().getHeaders().toString());
                json.put("type", "");
                
                // WorkflowUpdatedSampleData sampleData = new WorkflowUpdatedSampleData(json.toString(), rawApi.getRequest().getQueryParams(),
                //     rawApi.getRequest().getHeaders().toString(), rawApi.getRequest().getBody(), rawApi.getRequest().getUrl());

                YamlNodeDetails yamlNodeDetails = new YamlNodeDetails(testId, null, reqNode, null, customAuthTypes, authMechanism, rawApi, apiInfoKey);
                WorkflowNodeDetails workflowNodeDetails = new WorkflowNodeDetails(WorkflowNodeDetails.Type.API, yamlNodeDetails);
                mapNodeIdToWorkflowNodeDetails.put(target, workflowNodeDetails);
            } else {
                // DefaultNodeDetails defaultNodeDetails = new DefaultNodeDetails(0, rawApi.getRequest().getUrl(),
                //     URLMethods.Method.fromString(rawApi.getRequest().getMethod()), null, null, true, 0, 0, 0, null, null);
                YamlNodeDetails yamlNodeDetails = new YamlNodeDetails(null, validatorNode, reqNode, varMap, customAuthTypes, authMechanism, rawApi, apiInfoKey);
                WorkflowNodeDetails workflowNodeDetails = new WorkflowNodeDetails(WorkflowNodeDetails.Type.API, yamlNodeDetails);
                mapNodeIdToWorkflowNodeDetails.put(target, workflowNodeDetails);
            }

            edgeObj = new LoginWorkflowGraphEdge(source, target, target);
            edges.add(edgeObj.toString());

        }

        edgeObj = new LoginWorkflowGraphEdge("x"+ (edgeNumber - 1), "3", "x"+ edgeNumber);
        edges.add(edgeObj.toString());

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

    public ExecutorSingleRequest buildTestRequest(ExecutorNode node, String operation, List<RawApi> rawApis, Map<String, Object> varMap, AuthMechanism authMechanism, List<CustomAuthType> customAuthTypes) {

        List<ExecutorNode> childNodes = node.getChildNodes();
        if (node.getNodeType().equalsIgnoreCase(ExecutorOperandTypes.NonTerminal.toString()) || node.getNodeType().equalsIgnoreCase(ExecutorOperandTypes.Terminal.toString())) {
            operation = node.getOperationType();
        }

        if (node.getOperationType().equalsIgnoreCase(TestEditorEnums.ExecutorParentOperands.TYPE.toString())) {
            return new ExecutorSingleRequest(true, "", null, true);
        }

        if (node.getOperationType().equalsIgnoreCase(TestEditorEnums.ExecutorOperandTypes.Validate.toString())) {
            return new ExecutorSingleRequest(true, "", rawApis, true);
        }

        if (node.getOperationType().equalsIgnoreCase(TestEditorEnums.TerminalExecutorDataOperands.FOLLOW_REDIRECT.toString())) {
            boolean redirect = true;
            try {
                redirect = Boolean.valueOf(node.getValues().toString());
            } catch (Exception e) {
            }
            return new ExecutorSingleRequest(true, "", rawApis, redirect);
        }
        Boolean followRedirect = true;
        List<RawApi> newRawApis = new ArrayList<>();
        if (childNodes.size() == 0) {
            Object key = node.getOperationType();
            Object value = node.getValues();
            if (node.getNodeType().equalsIgnoreCase(ExecutorOperandTypes.Terminal.toString())) {
                if (node.getValues() instanceof Boolean) {
                    key = Boolean.toString((Boolean) node.getValues());
                } else if (node.getValues() instanceof String) {
                    key = (String) node.getValues();
                } else {
                    key = (Map) node.getValues();
                }
                value = null;
            }
            // if rawapi size is 1, var type is wordlist, iterate on values
            RawApi rApi = rawApis.get(0).copy();
            if (rawApis.size() == 1 && VariableResolver.isWordListVariable(key, varMap)) {
                List<String> wordListVal = VariableResolver.resolveWordListVar(key.toString(), varMap);

                for (int i = 0; i < wordListVal.size(); i++) {
                    RawApi copyRApi = rApi.copy();
                    ExecutorSingleOperationResp resp = invokeOperation(operation, wordListVal.get(i), value, copyRApi, varMap, authMechanism, customAuthTypes);
                    if (!resp.getSuccess()) {
                        return new ExecutorSingleRequest(false, resp.getErrMsg(), null, false);
                    }
                    if (resp.getErrMsg() == null || resp.getErrMsg().length() == 0) {
                        newRawApis.add(copyRApi);
                    }
                }

            } else if (rawApis.size() == 1 && VariableResolver.isWordListVariable(value, varMap)) {
                List<String> wordListVal = VariableResolver.resolveWordListVar(value.toString(), varMap);

                for (int i = 0; i < wordListVal.size(); i++) {
                    RawApi copyRApi = rApi.copy();
                    ExecutorSingleOperationResp resp = invokeOperation(operation, key, wordListVal.get(i), copyRApi, varMap, authMechanism, customAuthTypes);
                    if (!resp.getSuccess()) {
                        return new ExecutorSingleRequest(false, resp.getErrMsg(), null, false);
                    }
                    if (resp.getErrMsg() == null || resp.getErrMsg().length() == 0) {
                        newRawApis.add(copyRApi);
                    }
                }

            } else {
                if (VariableResolver.isWordListVariable(key, varMap)) {
                    List<String> wordListVal = VariableResolver.resolveWordListVar(key.toString(), varMap);
                    int index = 0;
                    for (RawApi rawApi : rawApis) {
                        if (index >= wordListVal.size()) {
                            break;
                        }
                        ExecutorSingleOperationResp resp = invokeOperation(operation, wordListVal.get(index), value, rawApi, varMap, authMechanism, customAuthTypes);
                        if (!resp.getSuccess()) {
                            return new ExecutorSingleRequest(false, resp.getErrMsg(), null, false);
                        }
                        index++;
                    }
                } else if (VariableResolver.isWordListVariable(value, varMap)) {
                    List<String> wordListVal = VariableResolver.resolveWordListVar(value.toString(), varMap);
                    int index = 0;
                    for (RawApi rawApi : rawApis) {
                        if (index >= wordListVal.size()) {
                            break;
                        }
                        ExecutorSingleOperationResp resp = invokeOperation(operation, key, wordListVal.get(index), rawApi, varMap, authMechanism, customAuthTypes);
                        if (!resp.getSuccess()) {
                            return new ExecutorSingleRequest(false, resp.getErrMsg(), null, false);
                        }
                        index++;
                    }
                } else {
                    for (RawApi rawApi : rawApis) {
                        ExecutorSingleOperationResp resp = invokeOperation(operation, key, value, rawApi, varMap, authMechanism, customAuthTypes);
                        if (!resp.getSuccess()) {
                            return new ExecutorSingleRequest(false, resp.getErrMsg(), null, false);
                        }
                    }
                }
                
            }
        }

        ExecutorNode childNode;
        for (int i = 0; i < childNodes.size(); i++) {
            childNode = childNodes.get(i);
            ExecutorSingleRequest executionResult = buildTestRequest(childNode, operation, rawApis, varMap, authMechanism, customAuthTypes);
            rawApis = executionResult.getRawApis();
            if (!executionResult.getSuccess()) {
                return executionResult;
            }
            followRedirect = followRedirect && executionResult.getFollowRedirect();
        }

        if (newRawApis.size() > 0) {
            return new ExecutorSingleRequest(true, "", newRawApis, followRedirect);
        } else {
            return new ExecutorSingleRequest(true, "", rawApis, followRedirect);
        }
    }

    public ExecutorSingleOperationResp invokeOperation(String operationType, Object key, Object value, RawApi rawApi, Map<String, Object> varMap, AuthMechanism authMechanism, List<CustomAuthType> customAuthTypes) {
        try {

            if (key == null) {
                return new ExecutorSingleOperationResp(false, "error executing executor operation, key is null " + key);
            }
            Object keyContext = null, valContext = null;
            if (key instanceof String) {
                keyContext = VariableResolver.resolveContextKey(varMap, key.toString());
            }
            if (value instanceof String) {
                valContext = VariableResolver.resolveContextVariable(varMap, value.toString());
            }

            if (keyContext instanceof ArrayList && valContext instanceof ArrayList) {
                List<String> keyContextList = (List<String>) keyContext;
                List<String> valueContextList = (List<String>) valContext;

                for (int i = 0; i < keyContextList.size(); i++) {
                    String v1 = valueContextList.get(i);
                    ExecutorSingleOperationResp resp = runOperation(operationType, rawApi, keyContextList.get(i), v1, varMap, authMechanism, customAuthTypes);
                    if (!resp.getSuccess()) {
                        return resp;
                    }
                }
                return new ExecutorSingleOperationResp(true, "");
            }

            if (key instanceof String) {
                key = VariableResolver.resolveExpression(varMap, key.toString());
            }

            if (value instanceof String) {
                value = VariableResolver.resolveExpression(varMap, value.toString());
            }

            if (value instanceof List) {
                try {
                    int index = 0;
                    List<String> valList = (List<String>) value;
                    for (String v: valList) {
                        v = VariableResolver.resolveExpression(varMap, v);
                        valList.set(index, v);
                        index++;
                    }
                } catch (Exception e) {
                }
            }

            ExecutorSingleOperationResp resp = runOperation(operationType, rawApi, key, value, varMap, authMechanism, customAuthTypes);
            return resp;
        } catch(Exception e) {
            return new ExecutorSingleOperationResp(false, "error executing executor operation " + e.getMessage());
        }
        
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

    public ExecutorSingleOperationResp runOperation(String operationType, RawApi rawApi, Object key, Object value, Map<String, Object> varMap, AuthMechanism authMechanism, List<CustomAuthType> customAuthTypes) {
        switch (operationType.toLowerCase()) {
            case "add_body_param":
                return Operations.addBody(rawApi, key.toString(), value);
            case "modify_body_param":
                return Operations.modifyBodyParam(rawApi, key.toString(), value);
            case "delete_body_param":
                return Operations.deleteBodyParam(rawApi, key.toString());
            case "replace_body":
                return Operations.replaceBody(rawApi, key);
            case "add_header":
                return Operations.addHeader(rawApi, key.toString(), value.toString());
            case "modify_header":
                return Operations.modifyHeader(rawApi, key.toString(), value.toString());
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
                if (key instanceof Map) {
                    Map<String, Map<String, String>> regexReplace = (Map) key;
                    String url = rawApi.getRequest().getUrl();
                    Map<String, String> regexInfo = regexReplace.get("regex_replace");
                    String regex = regexInfo.get("regex");
                    String replaceWith = regexInfo.get("replace_with");
                    newUrl = Utils.applyRegexModifier(url, regex, replaceWith);
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
                removeCustomAuth(rawApi, customAuthTypes);
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

                String authVal;
                if (VariableResolver.isAuthContext(key)) {
                    authVal = VariableResolver.resolveAuthContext(key.toString(), rawApi.getRequest().getHeaders(), authHeader);
                } else {
                    if (authMechanism == null || authMechanism.getAuthParams() == null || authMechanism.getAuthParams().size() == 0) {
                        return new ExecutorSingleOperationResp(false, "auth headers missing");
                    }
                    authVal = authMechanism.getAuthParams().get(0).getValue();
                }
                if (authVal == null) {
                    return new ExecutorSingleOperationResp(false, "auth value missing");
                }
                return Operations.modifyHeader(rawApi, authHeader, authVal);
            case "type":
                return new ExecutorSingleOperationResp(true, "");
            case "test_name":
                return new ExecutorSingleOperationResp(true, "");
            case "jwt_replace_body":
                JWTPayloadModifier jwtPayloadModifier = new JWTPayloadModifier(key.toString());
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
                            modifiedHeaderVal = jwtPayloadModifier.jwtModify("", val);
                        } catch(Exception e) {
                            return null;
                        }
                        finalValue.add(modifiedHeaderVal);
                    }

                    if (!isJwt) {
                        continue;
                    }

                    return Operations.modifyHeader(rawApi, k, String.join( " ", finalValue));
                }
            default:
                return new ExecutorSingleOperationResp(false, "invalid operationType");

        }
    }

}
