package com.akto.test_editor.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.akto.dao.CustomAuthTypeDao;
import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dao.test_editor.TestEditorEnums.ExecutorOperandTypes;
import com.akto.dto.CustomAuthType;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.ExecutionResult;
import com.akto.dto.test_editor.ExecutorNode;
import com.akto.dto.test_editor.ExecutorSingleOperationResp;
import com.akto.dto.test_editor.ExecutorSingleRequest;
import com.akto.dto.testing.AuthMechanism;
import com.akto.testing.ApiExecutor;
import com.mongodb.BasicDBObject;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.test_editor.Utils;

public class Executor {

    private static final LoggerMaker loggerMaker = new LoggerMaker(Executor.class);

    public List<ExecutionResult> execute(ExecutorNode node, RawApi rawApi, Map<String, Object> varMap, String logId, AuthMechanism authMechanism) {

        List<ExecutionResult> result = new ArrayList<>();
        
        if (node.getChildNodes().size() < 2) {
            loggerMaker.errorAndAddToDb("executor child nodes is less than 2, returning empty execution result " + logId, LogDb.TESTING);
            return result;
        }
        ExecutorNode reqNodes = node.getChildNodes().get(1);
        OriginalHttpResponse testResponse;
        RawApi sampleRawApi = rawApi.copy();
        ExecutorSingleRequest singleReq = null;
        if (reqNodes.getChildNodes() == null || reqNodes.getChildNodes().size() == 0) {
            return null;
        }

        for (ExecutorNode reqNode: reqNodes.getChildNodes()) {
            // make copy of varMap as well
            List<RawApi> sampleRawApis = new ArrayList<>();
            sampleRawApis.add(sampleRawApi);

            singleReq = buildTestRequest(reqNode, null, sampleRawApis, varMap, authMechanism, sampleRawApi.copy());
            List<RawApi> testRawApis = new ArrayList<>();
            testRawApis = singleReq.getRawApis();
            if (testRawApis == null) {
                continue;
            }
            for (RawApi testReq: testRawApis) {
                try {
                    // follow redirects = true for now
                    testResponse = ApiExecutor.sendRequest(testReq.getRequest(), singleReq.getFollowRedirect());
                    result.add(new ExecutionResult(singleReq.getSuccess(), singleReq.getErrMsg(), testReq.getRequest(), testResponse));
                } catch(Exception e) {
                    loggerMaker.errorAndAddToDb("error executing test request " + logId + " " + e.getMessage(), LogDb.TESTING);
                    result.add(new ExecutionResult(false, singleReq.getErrMsg(), testReq.getRequest(), null));
                }
            }
        }

        return result;
    }

    public ExecutorSingleRequest buildTestRequest(ExecutorNode node, String operation, List<RawApi> rawApis, Map<String, Object> varMap, AuthMechanism authMechanism, RawApi baseRawApi) {

        List<ExecutorNode> childNodes = node.getChildNodes();
        if (node.getNodeType().equalsIgnoreCase(ExecutorOperandTypes.NonTerminal.toString()) || node.getNodeType().equalsIgnoreCase(ExecutorOperandTypes.Terminal.toString())) {
            operation = node.getOperationType();
        }

        if (node.getOperationType().equalsIgnoreCase(TestEditorEnums.ExecutorParentOperands.TYPE.toString())) {
            return new ExecutorSingleRequest(true, "", rawApis, true);
        }

        if (node.getOperationType().equalsIgnoreCase(TestEditorEnums.TerminalExecutorDataOperands.FOLLOW_REDIRECT.toString())) {
            return new ExecutorSingleRequest(true, "", rawApis, false);
        }
        Boolean followRedirect = true;
        List<RawApi> newRawApis = new ArrayList<>();
        List<RawApi> existingRawApis = new ArrayList<>();
        boolean addNewRawApis = true;

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

            Object expandedKey = expandKey(varMap, key, value);
            Object expandedVal = expandValue(varMap, value);
            if (expandedKey != null && (expandedKey instanceof ArrayList)) {
                ArrayList<String> keyArr = (ArrayList<String>) expandedKey;
                if (expandedVal != null && (expandedVal instanceof ArrayList)) {
                    ArrayList<Object> valArr = (ArrayList<Object>) expandedVal;
                    for (String k: keyArr) {
                        for (Object v: valArr) {
                            RawApi copyRApi = baseRawApi.copy();
                            ExecutorSingleOperationResp resp = invokeOperation(operation, k, v, copyRApi, varMap, authMechanism);
                            if (!resp.getSuccess() || resp.getErrMsg().length() > 0) {
                                continue;
                            }
                            newRawApis.add(copyRApi);
                        }
                    }
                } else {
                    for (String k: keyArr) {
                        RawApi copyRApi = baseRawApi.copy();
                        ExecutorSingleOperationResp resp = invokeOperation(operation, k, value, copyRApi, varMap, authMechanism);
                        if (!resp.getSuccess() || resp.getErrMsg().length() > 0) {
                            continue;
                        }
                        newRawApis.add(copyRApi);
                    }
                }

            } else {
                if (expandedVal != null && (expandedVal instanceof ArrayList)) {
                    ArrayList<Object> valArr = (ArrayList<Object>) expandedVal;
                    for (Object v: valArr) {
                        RawApi copyRApi = baseRawApi.copy();
                        ExecutorSingleOperationResp resp = invokeOperation(operation, key, v, copyRApi, varMap, authMechanism);
                        if (!resp.getSuccess() || resp.getErrMsg().length() > 0) {
                            continue;
                        }
                        newRawApis.add(copyRApi);
                    }
                } else {
                    addNewRawApis = false;
                    for (RawApi rawApi : rawApis) {
                        ExecutorSingleOperationResp resp = invokeOperation(operation, key, value, rawApi, varMap, authMechanism);
                        if (!resp.getSuccess() || resp.getErrMsg().length() > 0) {
                            continue;
                        }
                        existingRawApis.add(rawApi.copy());
                    }
                    invokeOperation(operation, key, value, baseRawApi, varMap, authMechanism);
                }
            }

        }

        ExecutorNode childNode;
        for (int i = 0; i < childNodes.size(); i++) {
            childNode = childNodes.get(i);
            ExecutorSingleRequest executionResult = buildTestRequest(childNode, operation, rawApis, varMap, authMechanism, baseRawApi);
            rawApis = executionResult.getRawApis();
            if (!executionResult.getSuccess()) {
                return executionResult;
            }
            followRedirect = followRedirect && executionResult.getFollowRedirect();
        }

        if (newRawApis.size() > 0) {
            if (rawApis.size() <= 1) {
                rawApis = newRawApis;
            } else {
                rawApis.addAll(newRawApis);
            }
        }

        if (childNodes.size() == 0 && !addNewRawApis) {
            return new ExecutorSingleRequest(true, "", existingRawApis, followRedirect);
        } else {
            return new ExecutorSingleRequest(true, "", rawApis, followRedirect);
        }

    }

    public Object expandKey(Map<String, Object> varMap, Object key, Object value) {

        if (key == null || !(key instanceof String)) {
            return null;
        }

        Object keyContext = null, valContext = null;
        if (key instanceof String) {
            keyContext = VariableResolver.resolveContextKey(varMap, key.toString());
        }
        if (value instanceof String) {
            valContext = VariableResolver.resolveContextVariable(varMap, value.toString());
        }

        if (keyContext instanceof ArrayList && valContext instanceof ArrayList) {
            return null;
        }

        if (VariableResolver.isWordListVariable(key, varMap)) {
            return VariableResolver.resolveWordListVar(key.toString(), varMap);
        }

        return VariableResolver.resolveExpression(varMap, key.toString());

    }

    public Object expandValue(Map<String, Object> varMap, Object value) {

        if (value == null || !(value instanceof String)) {
            return null;
        }

        if (VariableResolver.isWordListVariable(value, varMap)) {
            return VariableResolver.resolveWordListVar(value.toString(), varMap);
        }

        return VariableResolver.resolveExpression(varMap, value.toString());

    }

    public ExecutorSingleOperationResp invokeOperation(String operationType, Object key, Object value, RawApi rawApi, Map<String, Object> varMap, AuthMechanism authMechanism) {
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
                    ExecutorSingleOperationResp resp = runOperation(operationType, rawApi, keyContextList.get(i), v1, varMap, authMechanism);
                    if (!resp.getSuccess() || resp.getErrMsg().length() > 0) {
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

            ExecutorSingleOperationResp resp = runOperation(operationType, rawApi, key, value, varMap, authMechanism);
            return resp;
        } catch(Exception e) {
            return new ExecutorSingleOperationResp(false, "error executing executor operation " + e.getMessage());
        }
        
    }
    
    public ExecutorSingleOperationResp runOperation(String operationType, RawApi rawApi, Object key, Object value, Map<String, Object> varMap, AuthMechanism authMechanism) {
        switch (operationType.toLowerCase()) {
            case "add_body_param":
                return Operations.addBody(rawApi, key.toString(), value);
            case "modify_body_param":
                return Operations.modifyBodyParam(rawApi, key.toString(), value);
            case "delete_body_param":
                return Operations.deleteBodyParam(rawApi, key.toString());
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
                for (String header: authHeaders) {
                    ExecutorSingleOperationResp resp = Operations.deleteHeader(rawApi, header);
                    if (resp.getErrMsg().contains("header key not present")) {
                        return new ExecutorSingleOperationResp(false, resp.getErrMsg());
                    }
                }
                List<CustomAuthType> customAuthTypes = CustomAuthTypeDao.instance.findAll(CustomAuthType.ACTIVE,true);
                for (CustomAuthType customAuthType : customAuthTypes) {
                    List<String> customAuthTypeHeaderKeys = customAuthType.getHeaderKeys();
                    for (String headerAuthKey: customAuthTypeHeaderKeys) {
                        Operations.deleteHeader(rawApi, headerAuthKey);
                    }
                    List<String> customAuthTypePayloadKeys = customAuthType.getPayloadKeys();
                    for (String payloadAuthKey: customAuthTypePayloadKeys) {
                        Operations.deleteBodyParam(rawApi, payloadAuthKey);
                    }
                }
                return new ExecutorSingleOperationResp(true, "");
            case "replace_auth_header":
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
            default:
                return new ExecutorSingleOperationResp(false, "invalid operationType");

        }
    }

}
