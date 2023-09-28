package com.akto.test_editor.execution;

import java.util.*;

import com.akto.dao.CustomAuthTypeDao;
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
import com.akto.dto.test_editor.*;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.rules.TestPlugin;
import com.akto.test_editor.Utils;
import com.akto.testing.ApiExecutor;
import com.akto.utils.RedactSampleData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.akto.rules.TestPlugin.extractAllValuesFromPayload;
import static com.akto.test_editor.Utils.bodyValuesUnchanged;
import static com.akto.test_editor.Utils.headerValuesUnchanged;

public class Executor {

    private static final LoggerMaker loggerMaker = new LoggerMaker(Executor.class);

    public List<TestResult> execute(ExecutorNode node, RawApi rawApi, Map<String, Object> varMap, String logId,
        AuthMechanism authMechanism, FilterNode validatorNode, ApiInfo.ApiInfoKey apiInfoKey, TestingRunConfig testingRunConfig, List<CustomAuthType> customAuthTypes) {
        List<TestResult> result = new ArrayList<>();
        
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

            singleReq = buildTestRequest(reqNode, null, sampleRawApis, varMap, authMechanism, customAuthTypes);
            List<RawApi> testRawApis = new ArrayList<>();
            testRawApis = singleReq.getRawApis();
            if (testRawApis == null) {
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
                    ExecutionResult attempt = new ExecutionResult(singleReq.getSuccess(), singleReq.getErrMsg(), testReq.getRequest(), testResponse);
                    TestResult res = validate(attempt, sampleRawApi, varMap, logId, validatorNode, apiInfoKey);
                    if (res != null) {
                        result.add(res);
                    }
                    vulnerable = res.getVulnerable();
                } catch(Exception e) {
                    loggerMaker.errorAndAddToDb("error executing test request " + logId + " " + e.getMessage(), LogDb.TESTING);
                }
            }
        }

        return result;
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

            ExecutorSingleOperationResp resp = runOperation(operationType, rawApi, key, value, varMap, authMechanism, customAuthTypes);
            return resp;
        } catch(Exception e) {
            return new ExecutorSingleOperationResp(false, "error executing executor operation " + e.getMessage());
        }
    }


    private static boolean removeAuthIfNotChanged(RawApi originalRawApi, RawApi testRawApi, String authMechanismHeaderKey) {
        boolean removed = false;
        // find set of all headers and body params that didn't change
        Map<String, List<String>> originalRequestHeaders = originalRawApi.fetchReqHeaders();
        Map<String, List<String>> testRequestHeaders = testRawApi.fetchReqHeaders();
        Set<String> unchangedHeaders = headerValuesUnchanged(originalRequestHeaders, testRequestHeaders);

        String originalJsonRequestBody = originalRawApi.getRequest().getJsonRequestBody();
        String testJsonRequestBody = testRawApi.getRequest().getJsonRequestBody();
        Set<String> unchangedBodyKeys = bodyValuesUnchanged(originalJsonRequestBody, testJsonRequestBody);

        // then loop over custom auth types and hardcoded auth mechanism to see if any auth token hasn't changed
        List<CustomAuthType> customAuthTypes = CustomAuthTypeDao.instance.findAll(CustomAuthType.ACTIVE,true);
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
                return Operations.replaceBody(rawApi, key);
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
                RawApi copy = rawApi.copy();
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
                removeAuthIfNotChanged(copy,rawApi, authHeader);

                if (modifiedAtLeastOne) {
                    return new ExecutorSingleOperationResp(true, "");
                } else {
                    return new ExecutorSingleOperationResp(false, "Couldn't find token");
                }
            default:
                return new ExecutorSingleOperationResp(false, "invalid operationType");

        }
    }

}
