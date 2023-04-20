package com.akto.dao.test_editor.executor;

import java.util.List;
import java.util.Map;

import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dao.test_editor.TestEditorEnums.ExecutorOperandTypes;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.ExecutionResult;
import com.akto.dto.test_editor.ExecutorNode;

public class Executor {

    public ExecutionResult execute(ExecutorNode node, String operation, RawApi rawApi, Map<String, Object> varMap) {

        List<ExecutorNode> childNodes = node.getChildNodes();
        if (node.getNodeType().equalsIgnoreCase(ExecutorOperandTypes.NonTerminal.toString()) || node.getNodeType().equalsIgnoreCase(ExecutorOperandTypes.Terminal.toString())) {
            operation = node.getOperationType();
        }

        if (node.getOperationType().equalsIgnoreCase(TestEditorEnums.ExecutorParentOperands.TYPE.toString())) {
            return new ExecutionResult(true, "");
        }
        if (childNodes.size() == 0) {
            String key = node.getOperationType();
            Object value = node.getValues();
            if (node.getNodeType().equalsIgnoreCase(ExecutorOperandTypes.Terminal.toString())) {
                key = (String) node.getValues();
                value = null;
            }
            ExecutionResult executionResult = invokeOperation(operation, key, value, rawApi, varMap);
            if (!executionResult.getSuccess()) {
                return executionResult;
            }
        }

        ExecutorNode childNode;
        for (int i = 0; i < childNodes.size(); i++) {
            childNode = childNodes.get(i);
            ExecutionResult executionResult = execute(childNode, operation, rawApi, varMap);
            if (!executionResult.getSuccess()) {
                return executionResult;
            }
        }

        return new ExecutionResult(true, "");

    }

    public ExecutionResult invokeOperation(String operationType, String key, Object value, RawApi rawApi, Map<String, Object> varMap) {
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
                case "add_queryparam":
                    return Operations.addQueryParam(rawApi, key, value);
                case "modify_queryparam":
                    return Operations.modifyQueryParam(rawApi, key, value);
                case "delete_queryparam":
                    return Operations.deleteQueryParam(rawApi, key);
                case "modify_url":
                    return Operations.modifyUrl(rawApi, key);
                case "modify_method":
                    return Operations.modifyMethod(rawApi, key);
                default:
                    return new ExecutionResult(false, "invalid operationType");
    
            }
        } catch(Exception e) {
            return new ExecutionResult(false, "error executing executor operation " + e.getMessage());
        }
        
    }

}
