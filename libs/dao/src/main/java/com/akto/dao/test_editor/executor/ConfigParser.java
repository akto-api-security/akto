package com.akto.dao.test_editor.executor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dao.test_editor.TestEditorEnums.ExecutorOperandTypes;
import com.akto.dto.test_editor.ConfigParserResult;
import com.akto.dto.test_editor.ConfigParserValidationResult;
import com.akto.dto.test_editor.ExecutorConfigParserResult;
import com.akto.dto.test_editor.ExecutorNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ConfigParser {

    public ExecutorConfigParserResult parseConfigMap(Object config) {

        Map<String, Object> configMap = (Map) config;

        if (configMap == null) {
            return null;
        }
        
        ExecutorNode node = new ExecutorNode("_ETHER_", new ArrayList<>(), config, "_ETHER_");

        ExecutorConfigParserResult configParserResult = parse(configMap, node, node);

        return configParserResult;
    }

    public ExecutorConfigParserResult parse(Map<String, Object> config, ExecutorNode curNode, ExecutorNode parentNode) {

        Object values = curNode.getValues();
        
        ConfigParserValidationResult configParserValidationResult = validate(curNode, parentNode);

        if (!configParserValidationResult.getIsValid()) {
            return new ExecutorConfigParserResult(null, false, configParserValidationResult.getErrMsg());
        }

        if (curNode.getOperationType().equalsIgnoreCase(TestEditorEnums.ExecutorParentOperands.TYPE.toString())) {
            return new ExecutorConfigParserResult(null, true, "");
        }

        if (curNode.getNodeType().equalsIgnoreCase(TestEditorEnums.ExecutorOperandTypes.Data.toString()) || 
                curNode.getNodeType().equalsIgnoreCase(TestEditorEnums.ExecutorOperandTypes.Terminal.toString()) || 
                curNode.getNodeType().equalsIgnoreCase(TestEditorEnums.ExecutorOperandTypes.TerminalNonExecutable.toString())) {
            return new ExecutorConfigParserResult(curNode, true, "");
        }

        if (curNode.getNodeType().equalsIgnoreCase(TestEditorEnums.ExecutorOperandTypes.Validate.toString())) {
            com.akto.dao.test_editor.filter.ConfigParser configParser = new com.akto.dao.test_editor.filter.ConfigParser();
            ConfigParserResult configParserResult = configParser.parse(curNode.getValues());
            List<ExecutorNode> childNodes = curNode.getChildNodes();
            childNodes.add(new ExecutorNode(TestEditorEnums.ExecutorOperandTypes.Validate.toString(), new ArrayList<>(), configParserResult.getNode(), TestEditorEnums.ExecutorOperandTypes.Validate.toString()));
            curNode.setChildNodes(childNodes);
            return new ExecutorConfigParserResult(curNode, true, "");
        }

        ObjectMapper m = new ObjectMapper();
        TestEditorEnums testEditorEnums = new TestEditorEnums();
        List<ExecutorNode> childNodes = new ArrayList<>();

        if (values instanceof List) {
            List<Object> listValues = (List<Object>) values;

            for (int i = 0; i < listValues.size(); i++) {
                Object obj = listValues.get(i);
                Map<String,Object> mapValues = m.convertValue(obj, Map.class);

                for (Map.Entry<String, Object> entry : mapValues.entrySet()) {
                    String operandType = testEditorEnums.getExecutorOperandType(entry.getKey(), curNode.getNodeType());
                    String operandValue = testEditorEnums.getExecutorOperandValue(entry.getKey());
                    ExecutorNode node = new ExecutorNode(operandType, new ArrayList<>(), entry.getValue(), operandValue);
                    ExecutorConfigParserResult configParserResult = parse(config, node, curNode);
                    if (!configParserResult.getIsValid()) {
                        return configParserResult;
                    }
                    childNodes.add(node);
                    curNode.setChildNodes(childNodes);
                }
            }

        } else if (values instanceof Map) {
            Map<String,Object> mapValues = m.convertValue(values, Map.class);
            for (Map.Entry<String, Object> entry : mapValues.entrySet()) {
                String operandType = testEditorEnums.getExecutorOperandType(entry.getKey(), curNode.getNodeType());
                String operandValue = testEditorEnums.getExecutorOperandValue(entry.getKey());
                ExecutorNode node = new ExecutorNode(operandType, new ArrayList<>(), entry.getValue(), operandValue);
                ExecutorConfigParserResult configParserResult = parse(config, node, curNode);
                if (!configParserResult.getIsValid()) {
                    return configParserResult;
                }
                childNodes.add(node);
                curNode.setChildNodes(childNodes);
            }
        } else {
            ExecutorConfigParserResult configParserResult = new ExecutorConfigParserResult(null, false, "invalid yaml, structure is neither map/list");
            return configParserResult;
        }

        ExecutorConfigParserResult configParserResult = new ExecutorConfigParserResult(curNode, true, "");
        return configParserResult;

    }

    public ConfigParserValidationResult validate(ExecutorNode curNode, ExecutorNode parentNode) {

        Object values = curNode.getValues();

        String curNodeType = curNode.getNodeType();
        String parentNodeType = parentNode.getNodeType();
        ConfigParserValidationResult configParserValidationResult = new ConfigParserValidationResult(true, "");

        if (curNodeType.equalsIgnoreCase(ExecutorOperandTypes.Parent.toString()) && !parentNodeType.equalsIgnoreCase("_ETHER_")) {
            configParserValidationResult.setIsValid(false);
            configParserValidationResult.setErrMsg("executor yaml should start with type/req keys");
            return configParserValidationResult;
        }

        if (curNodeType.equalsIgnoreCase(ExecutorOperandTypes.Req.toString()) && !parentNodeType.equalsIgnoreCase(ExecutorOperandTypes.Parent.toString())) {
            configParserValidationResult.setIsValid(false);
            configParserValidationResult.setErrMsg("req node should have executor parent node as a parent");
            return configParserValidationResult;
        }

        if ((curNodeType.equalsIgnoreCase(ExecutorOperandTypes.Terminal.toString()) || curNodeType.equalsIgnoreCase(ExecutorOperandTypes.NonTerminal.toString())) && !(parentNodeType.equalsIgnoreCase(ExecutorOperandTypes.Req.name()) || parentNodeType.equalsIgnoreCase(ExecutorOperandTypes.Loop.name()) )) {
            configParserValidationResult.setIsValid(false);
            configParserValidationResult.setErrMsg("terminal/nonTerminal Executor operand should have executorParentNode as the parent node");
            return configParserValidationResult;
        }

        if (curNodeType.equalsIgnoreCase(ExecutorOperandTypes.Data.toString()) && !(curNodeType.equalsIgnoreCase(ExecutorOperandTypes.Terminal.toString()) || parentNodeType.equalsIgnoreCase(ExecutorOperandTypes.NonTerminal.toString()))) {
            configParserValidationResult.setIsValid(false);
            configParserValidationResult.setErrMsg("terminalOperand should have terminalExecutorOperand/nonTerminalExecutorOperand as the parent node");
            return configParserValidationResult;
        }

        if (curNodeType.equalsIgnoreCase(ExecutorOperandTypes.Validate.toString())) {
            return new ConfigParserValidationResult(true, "");
        }

        if (curNodeType.equalsIgnoreCase(ExecutorOperandTypes.Terminal.toString())) {
            if (values instanceof Map) {
                Map<String, Object> mapVal = (Map) values;
                if (!mapVal.containsKey("regex_replace")) {
                    configParserValidationResult.setIsValid(false);
                    configParserValidationResult.setErrMsg("terminalOperands Executor operand doesn't have regex replace value");
                }
            } else if (!( (values instanceof String) || (values instanceof Integer) || (values instanceof Boolean) )) {
                configParserValidationResult.setIsValid(false);
                configParserValidationResult.setErrMsg("terminalOperands should only have string/boolean/int value");
            }
        }

        if (curNodeType.equalsIgnoreCase(ExecutorOperandTypes.Data.toString()) && !( (values instanceof String) || (values instanceof Integer) || (values instanceof Boolean) )) {
            if (values instanceof Map)
            configParserValidationResult.setIsValid(false);
            configParserValidationResult.setErrMsg("data should only have string value");
            return configParserValidationResult;
        }

        return new ConfigParserValidationResult(true, "");

    }

}
