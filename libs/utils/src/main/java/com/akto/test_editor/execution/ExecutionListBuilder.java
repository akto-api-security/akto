package com.akto.test_editor.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dao.test_editor.TestEditorEnums.ExecutorOperandTypes;
import com.akto.dto.test_editor.ExecutionOrderResp;
import com.akto.dto.test_editor.ExecutorNode;
import com.akto.dto.test_editor.ModifyExecutionOrderResp;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

public class ExecutionListBuilder {
    
    private static final LoggerMaker loggerMaker = new LoggerMaker(ExecutionListBuilder.class);

    private boolean allowAllCombinations;
    public ExecutionOrderResp parseExecuteOperations(ExecutorNode node, List<ExecutorNode> executeOrder) {

        String error = null;
        boolean followRedirect = true;
        if (node.getChildNodes().size() < 2) {
            error = "executor child nodes is less than 2, returning empty execution result";
            loggerMaker.errorAndAddToDb(error, LogDb.TESTING);
            return new ExecutionOrderResp(error, true);
        }

        ExecutorNode reqNodes = node.getChildNodes().get(1);
        if (reqNodes.getChildNodes() == null || reqNodes.getChildNodes().size() == 0) {
            error = "executor child nodes is less than 2, returning empty execution result";
            loggerMaker.errorAndAddToDb(error, LogDb.TESTING);
            return new ExecutionOrderResp(error, true);
        }

        followRedirect = buildExecuteOrder(reqNodes.getChildNodes().get(0), executeOrder);
        return new ExecutionOrderResp(error, followRedirect);

    }


    public boolean buildExecuteOrder(ExecutorNode node, List<ExecutorNode> executionOrder) {

        List<ExecutorNode> childNodes = node.getChildNodes();

        if(node.getOperationType().equalsIgnoreCase(TestEditorEnums.TerminalExecutorDataOperands.FOLLOW_REDIRECT.toString())) {
            boolean redirect = true;
            try {
                redirect = Boolean.valueOf(node.getValues().toString());
            } catch (Exception e) {
            }
            return redirect;
        }

        if(node.getOperationType().equalsIgnoreCase(TestEditorEnums.TerminalExecutorDataOperands.FOR_EACH_COMBINATION.toString())) {
            boolean allCombinations = false;
            try {
                allCombinations = Boolean.valueOf(node.getValues().toString());
                this.allowAllCombinations = allCombinations;
            } catch (Exception e) {
            }
            return true;
        }

        if (node.getOperationType().equalsIgnoreCase(TestEditorEnums.ExecutorParentOperands.TYPE.toString()) || 
            node.getOperationType().equalsIgnoreCase(TestEditorEnums.ExecutorOperandTypes.Validate.toString()) || 
            node.getNodeType().equalsIgnoreCase(ExecutorOperandTypes.TerminalNonExecutable.toString())) {
                return true;
        }

        if (node.getNodeType().equalsIgnoreCase(ExecutorOperandTypes.Terminal.toString()) || 
                node.getNodeType().equalsIgnoreCase(ExecutorOperandTypes.NonTerminal.toString()) ||
                node.getNodeType().equalsIgnoreCase(ExecutorOperandTypes.Dynamic.toString())) {
            executionOrder.add(node);
            return true;
        }
        ExecutorNode childNode;
        boolean followRedirect = true;
        for (int i = 0; i < childNodes.size(); i++) {
            childNode = childNodes.get(i);
            boolean res = buildExecuteOrder(childNode, executionOrder);
            followRedirect = followRedirect && res;
        }
        return followRedirect;
    }

    public ModifyExecutionOrderResp modifyExecutionFlow(List<ExecutorNode> executorNodes, Map<String, Object> varMap) {
        List<ExecutorNode> updatedExecutionFlow = new ArrayList<>();
        for (ExecutorNode executorNode: executorNodes) {
            if (executorNode.getNodeType().equalsIgnoreCase(TestEditorEnums.ExecutorOperandTypes.Dynamic.toString())) {
                List<Object> values = VariableResolver.resolveExpression(varMap, executorNode.getOperationType());
                if (values == null) {
                    return new ModifyExecutionOrderResp(executorNodes, "cannot loop on the given parameter, invalid value specified " + executorNode.getOperationType());
                }
                if (values.size() == 1) {
                    String val = values.get(0).toString();
                    if (val.startsWith("${") && val.endsWith("}")) {
                        return new ModifyExecutionOrderResp(executorNodes, "cannot loop on the given parameter, unable to resolve value for the paramater " + executorNode.getOperationType());
                    }
                }
                ExecutorNode loopNode = executorNode.getChildNodes().get(0);
                int startIndex = 0;
                int endIndex = values.size();
                if (loopNode.getOperationType().equalsIgnoreCase(TestEditorEnums.LoopExecutorOperands.FOR_ONE.toString())) {
                    endIndex = 1;
                }
                List<ExecutorNode> childNodes = loopNode.getChildNodes();

                for (int i = startIndex; i < endIndex; i++) {
                    for (int j = 0; j < childNodes.size(); j++) {
                        ExecutorNode childNode = childNodes.get(j);
                        if (childNode.getNodeType().equalsIgnoreCase(TestEditorEnums.ExecutorOperandTypes.NonTerminal.toString())) {
                            ExecutorNode dataNode = childNode.getChildNodes().get(0);
                            String opType = dataNode.getOperationType();
                            opType = opType.replace("${iteratorKey}", values.get(i).toString());

                            Object dataValues = dataNode.getValues();
                            if (dataValues != null && dataValues instanceof String) {
                                dataValues = dataValues.toString().replace("${iteratorKey}", values.get(i).toString());
                                if (!dataValues.toString().startsWith("${")) {
                                    dataValues = "${" + dataValues.toString() + "}";
                                }
                            }
                            List<ExecutorNode> newChildNodes = new ArrayList<>();
                            ExecutorNode newDataNode = new ExecutorNode(dataNode.getNodeType(), dataNode.getChildNodes(), dataValues, opType);
                            newChildNodes.add(newDataNode);
                            updatedExecutionFlow.add(new ExecutorNode(childNode.getNodeType(), newChildNodes, childNode.getValues(), childNode.getOperationType()));
                        } else {
                            Object dataValues = childNode.getValues();
                            if (dataValues != null && dataValues instanceof String) {
                                dataValues = dataValues.toString().replace("${iteratorKey}", values.get(i).toString());
                            }
                            updatedExecutionFlow.add(new ExecutorNode(childNode.getNodeType(), childNode.getChildNodes(), dataValues, childNode.getOperationType()));
                        }
                    }
                }
            } else {
                updatedExecutionFlow.add(executorNode);
            }
        }
        return new ModifyExecutionOrderResp(updatedExecutionFlow, null);
    }

    public boolean isAllowAllCombinations() {
        return allowAllCombinations;
    }

    public void setAllowAllCombinations(boolean allowAllCombinations) {
        this.allowAllCombinations = allowAllCombinations;
    }
}
