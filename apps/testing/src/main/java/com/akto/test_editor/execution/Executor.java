package com.akto.test_editor.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dao.test_editor.TestEditorEnums.ExecutorOperandTypes;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.ExecutionResult;
import com.akto.dto.test_editor.ExecutorNode;
import com.akto.dto.test_editor.ExecutorSingleOperationResp;
import com.akto.dto.test_editor.ExecutorSingleRequest;
import com.akto.testing.ApiExecutor;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

public class Executor {

    private static final LoggerMaker loggerMaker = new LoggerMaker(Executor.class);

    public List<ExecutionResult> execute(ExecutorNode node, RawApi rawApi, Map<String, Object> varMap, String logId) {

        List<ExecutionResult> result = new ArrayList<>();
        
        if (node.getChildNodes().size() < 2) {
            loggerMaker.errorAndAddToDb("executor child nodes is less than 2, returning empty execution result", LogDb.TESTING);
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
            singleReq = buildTestRequest(reqNode, null, sampleRawApi, varMap);
            sampleRawApi = singleReq.getRawApi();
        }

        try {
            // follow redirects = true for now
            testResponse = ApiExecutor.sendRequest(singleReq.getRawApi().getRequest(), singleReq.getFollowRedirect());
        } catch(Exception e) {
            return null;
        }
        result.add(new ExecutionResult(singleReq.getSuccess(), singleReq.getErrMsg(), singleReq.getRawApi().getRequest(), testResponse));

        return result;
    }

    public ExecutorSingleRequest buildTestRequest(ExecutorNode node, String operation, RawApi rawApi, Map<String, Object> varMap) {

        List<ExecutorNode> childNodes = node.getChildNodes();
        if (node.getNodeType().equalsIgnoreCase(ExecutorOperandTypes.NonTerminal.toString()) || node.getNodeType().equalsIgnoreCase(ExecutorOperandTypes.Terminal.toString())) {
            operation = node.getOperationType();
        }

        if (node.getOperationType().equalsIgnoreCase(TestEditorEnums.ExecutorParentOperands.TYPE.toString())) {
            return new ExecutorSingleRequest(true, "", null, false);
        }

        if (node.getOperationType().equalsIgnoreCase(TestEditorEnums.TerminalExecutorDataOperands.FOLLOW_REDIRECT.toString())) {
            return new ExecutorSingleRequest(true, "", null, true);
        }
        Boolean followRedirect = true;
        if (childNodes.size() == 0) {
            String key = node.getOperationType();
            Object value = node.getValues();
            if (node.getNodeType().equalsIgnoreCase(ExecutorOperandTypes.Terminal.toString())) {
                if (node.getValues() instanceof Boolean) {
                    key = Boolean.toString((Boolean) node.getValues());
                } else {
                    key = (String) node.getValues();
                }
                value = null;
            }
            ExecutorSingleOperationResp resp = invokeOperation(operation, key, value, rawApi, varMap);
            if (!resp.getSuccess()) {
                return new ExecutorSingleRequest(false, resp.getErrMsg(), null, false);
            }
        }

        ExecutorNode childNode;
        for (int i = 0; i < childNodes.size(); i++) {
            childNode = childNodes.get(i);
            ExecutorSingleRequest executionResult = buildTestRequest(childNode, operation, rawApi, varMap);
            if (!executionResult.getSuccess()) {
                return executionResult;
            }
            followRedirect = followRedirect || executionResult.getFollowRedirect();
        }

        return new ExecutorSingleRequest(true, "", rawApi, followRedirect);

    }

    public ExecutorSingleOperationResp invokeOperation(String operationType, String key, Object value, RawApi rawApi, Map<String, Object> varMap) {
        try {

            if (key == null || value == null) {
                return new ExecutorSingleOperationResp(false, "error executing executor operation, key or value is null " + key + " " + value);
            }
            Object keyContext = null, valContext = null;
            if (key instanceof String) {
                keyContext = VariableResolver.resolveContextKey(varMap, key);
            }
            if (value instanceof String) {
                valContext = VariableResolver.resolveContextVariable(varMap, value.toString());
            }

            if (keyContext instanceof ArrayList && valContext instanceof ArrayList) {
                List<String> keyContextList = (List<String>) keyContext;
                List<String> valueContextList = (List<String>) valContext;

                for (int i = 0; i < keyContextList.size(); i++) {
                    String v1 = valueContextList.get(i);
                    ExecutorSingleOperationResp resp = runOperation(operationType, rawApi, keyContextList.get(i), v1, varMap);
                    if (!resp.getSuccess()) {
                        return resp;
                    }
                }
                return new ExecutorSingleOperationResp(true, "");
            }

            if (key instanceof String) {
                key = VariableResolver.resolveExpression(varMap, key);
            }

            if (value instanceof String) {
                value = VariableResolver.resolveExpression(varMap, value.toString());
            }

            ExecutorSingleOperationResp resp = runOperation(operationType, rawApi, key, value, varMap);
            return resp;
        } catch(Exception e) {
            return new ExecutorSingleOperationResp(false, "error executing executor operation " + e.getMessage());
        }
        
    }
    
    public ExecutorSingleOperationResp runOperation(String operationType, RawApi rawApi, String key, Object value, Map<String, Object> varMap) {
        switch (operationType.toLowerCase()) {
            case "add_body_param":
                return Operations.addBody(rawApi, key, value);
            case "modify_body_param":
                return Operations.modifyBodyParam(rawApi, key, value);
            case "delete_body_param":
                return Operations.deleteBodyParam(rawApi, key);
            case "add_header":
                return Operations.addHeader(rawApi, key, value.toString());
            case "modify_header":
                return Operations.modifyHeader(rawApi, key, value.toString());
            case "delete_header":
                return Operations.deleteHeader(rawApi, key);
            case "add_query_param":
                return Operations.addQueryParam(rawApi, key, value);
            case "modify_query_param":
                return Operations.modifyQueryParam(rawApi, key, value);
            case "delete_query_param":
                return Operations.deleteQueryParam(rawApi, key);
            case "modify_url":
                return Operations.modifyUrl(rawApi, key);
            case "modify_method":
                return Operations.modifyMethod(rawApi, key);
            case "remove_auth_headers":
                List<String> authHeaders = (List<String>) varMap.get("auth_headers");
                for (String header: authHeaders) {
                    Operations.deleteHeader(rawApi, header);
                }
                return new ExecutorSingleOperationResp(true, "");
            default:
                return new ExecutorSingleOperationResp(false, "invalid operationType");

        }
    }

}
