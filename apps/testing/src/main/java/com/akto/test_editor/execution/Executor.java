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

public class Executor {

    public List<ExecutionResult> execute(ExecutorNode node, RawApi rawApi, Map<String, Object> varMap) {

        List<ExecutionResult> result = new ArrayList<>();
        
        if (node.getChildNodes().size() < 2) {
            return result;
        }
        ExecutorNode reqNodes = node.getChildNodes().get(1);
        OriginalHttpResponse testResponse;
        for (ExecutorNode reqNode: reqNodes.getChildNodes()) {
            RawApi sampleRawApi = rawApi.copy();
            // make copy of varMap as well
            ExecutorSingleRequest singleReq = buildTestRequest(reqNode, null, sampleRawApi, varMap);

            try {
                // follow redirects = true for now
                testResponse = ApiExecutor.sendRequest(singleReq.getRawApi().getRequest(), singleReq.getFollowRedirect());
            } catch(Exception e) {
                continue;
            }
            result.add(new ExecutionResult(singleReq.getSuccess(), singleReq.getErrMsg(), singleReq.getRawApi().getRequest(), testResponse));
        }
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
        Boolean followRedirect = false;
        if (childNodes.size() == 0) {
            String key = node.getOperationType();
            Object value = node.getValues();
            if (node.getNodeType().equalsIgnoreCase(ExecutorOperandTypes.Terminal.toString())) {
                key = (String) node.getValues();
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

            if (key instanceof String) {
                key = VariableResolver.resolveExpression(varMap, key);
            }

            if (value instanceof String) {
                value = VariableResolver.resolveExpression(varMap, value.toString());
            }

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
        } catch(Exception e) {
            return new ExecutorSingleOperationResp(false, "error executing executor operation " + e.getMessage());
        }
        
    }

}
