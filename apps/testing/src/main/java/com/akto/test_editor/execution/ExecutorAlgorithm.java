package com.akto.test_editor.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dao.test_editor.TestEditorEnums.ExecutorOperandTypes;
import com.akto.dto.ApiInfo;
import com.akto.dto.CustomAuthType;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.ExecuteAlgoObj;
import com.akto.dto.test_editor.ExecutorNode;
import com.akto.dto.test_editor.ExecutorSingleOperationResp;
import com.akto.dto.test_editor.ExecutorSingleRequest;
import com.akto.dto.testing.AuthMechanism;

public class ExecutorAlgorithm {

    private RawApi sampleRawApi;
    private Map<String, Object> varMap;
    private AuthMechanism authMechanism;
    private List<CustomAuthType> customAuthTypes;
    private boolean allowAllCombinations;
    private Executor executor = new Executor();

    public ExecutorAlgorithm(RawApi sampleRawApi, Map<String, Object> varMap, AuthMechanism authMechanism,
        List<CustomAuthType> customAuthTypes) {
        this.sampleRawApi = sampleRawApi;
        this.varMap = varMap;
        this.authMechanism = authMechanism;
        this.customAuthTypes = customAuthTypes;
        this.allowAllCombinations = false;
    }

    public ExecutorAlgorithm(RawApi sampleRawApi, Map<String, Object> varMap, AuthMechanism authMechanism,
        List<CustomAuthType> customAuthTypes, boolean allowAllCombinations) {
        this.sampleRawApi = sampleRawApi;
        this.varMap = varMap;
        this.authMechanism = authMechanism;
        this.customAuthTypes = customAuthTypes;
        this.allowAllCombinations = allowAllCombinations;
    }

    public ExecutorAlgorithm(){
    }
    
    public ExecutorSingleRequest execute(List<ExecutorNode> executorNodes, int operationIndex, Map<Integer, ExecuteAlgoObj> algoMap, List<RawApi> rawApis, boolean expandRawApis, int rawapiInsertCount, ApiInfo.ApiInfoKey apiInfoKey) {

        if (operationIndex < 0 || operationIndex >= executorNodes.size()) {
            return new ExecutorSingleRequest(true, "", rawApis, null);
        }
        ExecutorNode executorNode = executorNodes.get(operationIndex);
        // iterate on all child nodes
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

        int rawApiIndex = 0;
        int keyIndex = 0;
        int valIndex = 0;
        int numberOfOperations = Math.max(rawApis.size(), calcNumberOfOperations(keyList, valList));
        
        if (expandRawApis) {
            numberOfOperations = rawapiInsertCount;
            rawApiIndex = algoMap.get(operationIndex).getRawApiIndexModified();
            keyIndex = algoMap.get(operationIndex).getKeyIndex();
            valIndex = algoMap.get(operationIndex).getValueIndex();
            if (operationIndex == 0) {
                for (int i = 0; i < numberOfOperations; i++) {
                    rawApis.add(sampleRawApi.copy());
                }
            }
        } else {
            if (operationIndex == 0) {
                for (int i = 0; i < numberOfOperations - 1; i++) {
                    rawApis.add(sampleRawApi.copy());
                }
            }
        }
        ExecutorSingleRequest executorSingleRequest = new ExecutorSingleRequest(true, "", rawApis, null);
        for (int i = 0; i < numberOfOperations; i++) {
            if (!expandRawApis && rawApiIndex >= rawApis.size()) {
                for (int j = 0; j < operationIndex; j++) {
                    executorSingleRequest = execute(executorNodes, j, algoMap, rawApis, true, numberOfOperations - i, apiInfoKey);
                    if (!executorSingleRequest.getSuccess()) {
                        return executorSingleRequest;
                    }
                }
            }

            if (keyIndex >= keyList.size()) {
                return new ExecutorSingleRequest(false, "empty arg at left side for operation no " + operationIndex, null, false);
            }
            Object key = keyList.get(keyIndex);
            Object val = null;
            if (valList != null && valList.size() > 0) {
                val = valList.get(valIndex);
                if (valList.size() > 1) {
                    valIndex = (valIndex + 1)%valList.size();
                }
            }
            ExecutorSingleOperationResp resp = executor.invokeOperation(executorNode.getOperationType(), key, val, rawApis.get(rawApiIndex), varMap, authMechanism, customAuthTypes, apiInfoKey);
            if (!resp.getSuccess()) {
                return new ExecutorSingleRequest(false, resp.getErrMsg(), null, false);
            }
            rawApiIndex++;
            if (keyList.size() > 1) {
                if (valList.size() > 0 && this.allowAllCombinations) {
                    keyIndex = i/valList.size();
                } else {
                    keyIndex = (keyIndex + 1)%keyList.size();
                }
            }

        }

        algoMap.put(operationIndex, new ExecuteAlgoObj(numberOfOperations, keyIndex, valIndex, rawApis.size()));
        if (!expandRawApis) {
            executorSingleRequest = execute(executorNodes, operationIndex + 1, algoMap, rawApis, false, 0, apiInfoKey);
        }
        return executorSingleRequest;
    }

    public int calcNumberOfOperations(List<Object> keyList, List<Object> valList) {

        if (valList == null || valList.size() == 0) {
            return keyList.size();
        }

        if (keyList.size() == 1) {
            return valList.size();
        }

        if (this.allowAllCombinations) {
            return keyList.size() * valList.size();
        }
        return keyList.size();
    }


}
