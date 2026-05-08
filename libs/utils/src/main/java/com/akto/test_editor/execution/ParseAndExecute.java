package com.akto.test_editor.execution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dao.test_editor.TestEditorEnums.ExecutorOperandTypes;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.RawApi;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.test_editor.ExecuteAlgoObj;
import com.akto.dto.test_editor.ExecutionOrderResp;
import com.akto.dto.test_editor.ExecutorConfigParserResult;
import com.akto.dto.test_editor.ExecutorNode;
import com.akto.dto.test_editor.ExecutorSingleOperationResp;
import com.akto.mcp.McpRequestResponseUtils;
import com.akto.test_editor.Utils;

public class ParseAndExecute {

    public static List<ExecutorNode> getExecutorNodes(ExecutorNode executorNode){
        if(executorNode.getChildNodes() == null ||  executorNode.getChildNodes().isEmpty()){
            return new ArrayList<>();
        }

        ExecutionListBuilder executionListBuilder = new ExecutionListBuilder();
        List<ExecutorNode> executorNodes = new ArrayList<>();
        ExecutionOrderResp executionOrderResp = executionListBuilder.parseExecuteOperations(executorNode, executorNodes);

        ExecutorNode reqNodes = executorNode.getChildNodes().get(1);
        if (reqNodes.getChildNodes() == null || reqNodes.getChildNodes().size() == 0) {
            return new ArrayList<>();
        }
        if(executionOrderResp.getError() != null && executionOrderResp.getError().length() > 0) {
            return new ArrayList<>();
        }
        return executorNodes;
    }

    public RawApi execute (List<ExecutorNode> executorNodes, RawApi rawApi, ApiInfoKey apiInfoKey, Map<String, Object> varMap, String logId){
        RawApi origRawApi = rawApi.copy();
        RawApi modifiedRawApi = executeAlgorithm(origRawApi, varMap, executorNodes, null, apiInfoKey);
        return modifiedRawApi;
    }

    private RawApi executeAlgorithm(RawApi sampleRawApi, Map<String, Object> varMap, List<ExecutorNode> executorNodes,  Map<Integer, ExecuteAlgoObj> algoMap, ApiInfo.ApiInfoKey apiInfoKey){
        RawApi copyRawApi = sampleRawApi.copy();
        for(ExecutorNode executorNode: executorNodes){
            ExecutorNode node;
            if (executorNode.getNodeType().equalsIgnoreCase(TestEditorEnums.ExecutorOperandTypes.NonTerminal.toString())) {
                node = executorNode.getChildNodes().get(0);
            } else {
                node = executorNode;
            }
    
            Object keyOp = node.getOperationType();
            Object valueOp = node.getValues();
            if (node.getNodeType().equalsIgnoreCase(ExecutorOperandTypes.Terminal.toString())) {
                if (node.getValues() instanceof Boolean) {
                    keyOp = Boolean.toString((Boolean) node.getValues());
                } else if (node.getValues() instanceof String) {
                    keyOp = (String) node.getValues();
                } else {
                    keyOp = (Map) node.getValues();
                }
                valueOp = null;
            }
    
            List<Object> keyList = VariableResolver.resolveExpression(varMap, keyOp);
            List<Object> valList = new ArrayList<>();
            if (valueOp != null) {
                valList = VariableResolver.resolveExpression(varMap, valueOp);
            }
    
            Object key = keyList.get(0);
            Object val = null;
            if (valList != null && valList.size() > 0) {
                val = valList.get(0);
            }

            boolean isMcpRequest = McpRequestResponseUtils.isMcpRequest(copyRawApi);
            ExecutorSingleOperationResp resp = Utils.modifySampleDataUtil(executorNode.getOperationType(), copyRawApi, key, val , varMap, apiInfoKey, isMcpRequest);
            if(resp.getErrMsg() != null && resp.getErrMsg().length() > 0){
                break;
            }
        }

        return copyRawApi;
    }

    public static Map<String, List<ExecutorNode>> createExecutorNodeMap (Map<String,FilterConfig> filterMap) {
        Map<String, List<ExecutorNode>> finalMap = new HashMap<>();

        if (filterMap != null && !filterMap.isEmpty()) {
            for(Map.Entry<String,FilterConfig> iterator: filterMap.entrySet()){
                String templateId = iterator.getKey();
                if(templateId.equals(FilterConfig.DEFAULT_ALLOW_FILTER) || templateId.equals(FilterConfig.DEFAULT_BLOCK_FILTER)){
                    continue;
                }
                ExecutorConfigParserResult nodeObj = iterator.getValue().getExecutor();
                if(nodeObj != null && nodeObj.getIsValid()){
                    ExecutorNode node = nodeObj.getNode();
                    List<ExecutorNode> nodes = getExecutorNodes(node);

                    finalMap.put(templateId, nodes);
                }
            }
        }

        return finalMap;
    }

}
