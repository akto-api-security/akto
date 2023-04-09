package com.akto.dao.test_editor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.access.method.P;

import com.akto.dao.test_editor.TestEditorEnums.OperandTypes;
import com.akto.dto.ApiInfo;
import com.akto.dto.RawApi;
import com.akto.dto.api_workflow.Node;
import com.akto.dto.test_editor.ConfigParserResult;
import com.akto.dto.test_editor.ConfigParserValidationResult;
import com.akto.dto.test_editor.DataOperandsFilterResponse;
import com.akto.dto.test_editor.FilterActionRequest;
import com.akto.dto.test_editor.FilterNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestConfigParser {

    private List<String> allowedDataParentNodes = Arrays.asList("pred", "payload", "term");
    private List<String> allowedPredParentNodes = Arrays.asList("pred", "collection", "payload", "term");
    private List<String> allowedTermParentNodes = Arrays.asList("pred");
    private List<String> allowedCollectionParentNodes = Arrays.asList("pred", "term");
    private FilterAction filterAction;

    public TestConfigParser() {
        this.filterAction = new FilterAction();
    }
    
    public ConfigParserResult parse(Map<String, Object> filters) {

        if (filters == null) {
            // throw exception
        }
        
        FilterNode node = new FilterNode("and", false, null, filters, "_ETHER_", new ArrayList<>(), null);
        ConfigParserResult configParserResult = validateAndTransform(filters, node, node, false, false, null, null);

        return configParserResult;
    }

    public ConfigParserResult validateAndTransform(Map<String, Object> filters, FilterNode curNode, FilterNode parentNode, Boolean termNodeExists, 
        Boolean collectionNodeExists, String concernedProperty, String subConcernedProperty) {

        Object values = curNode.getValues();

        ConfigParserValidationResult configParserValidationResult = validateNodeAgainstRules(curNode, parentNode, termNodeExists, collectionNodeExists, concernedProperty);
        if (!configParserValidationResult.getIsValid()) {
            return new ConfigParserResult(null, false, configParserValidationResult.getErrMsg());
        }

        if (curNode.getNodeType().equals(OperandTypes.Data.toString().toLowerCase())) {
            return new ConfigParserResult(null, true, "");
        }

        if (curNode.getNodeType().equals(OperandTypes.Term.toString().toLowerCase())) {
            termNodeExists = true;
            concernedProperty = curNode.getOperand();
        }

        if (curNode.getNodeType().equals(OperandTypes.Collection.toString().toLowerCase())) {
            collectionNodeExists = true;
            subConcernedProperty = curNode.getSubConcernedProperty();
        }

        if (curNode.getNodeType().equals(OperandTypes.Payload.toString().toLowerCase())) {
            subConcernedProperty = curNode.getOperand();
        }

        ObjectMapper m = new ObjectMapper();
        TestEditorEnums testEditorEnums = new TestEditorEnums();
        List<FilterNode> childNodes = new ArrayList<>();

        if (values instanceof List) {
            List<Object> listValues = (List<Object>) values;

            for (int i = 0; i < listValues.size(); i++) {
                Object obj = listValues.get(i);
                Map<String,Object> mapValues = m.convertValue(obj, Map.class);

                for (Map.Entry<String, Object> entry : mapValues.entrySet()) {
                    String operand = testEditorEnums.getOperandValue(entry.getKey());
                    String operandType = testEditorEnums.getOperandType(entry.getKey());
                    if (!(entry.getValue() instanceof List)) {
                        entry.setValue(Arrays.asList(entry.getValue()));
                    }
                    FilterNode node = new FilterNode(operand, false, concernedProperty, entry.getValue(), operandType, new ArrayList<>(), subConcernedProperty);
                    ConfigParserResult configParserResult = validateAndTransform(filters, node, curNode, termNodeExists, collectionNodeExists, concernedProperty, subConcernedProperty);
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
                String operand = testEditorEnums.getOperandValue(entry.getKey());
                String operandType = testEditorEnums.getOperandType(entry.getKey());
                if (!(entry.getValue() instanceof List)) {
                    entry.setValue(Arrays.asList(entry.getValue()));
                }
                FilterNode node = new FilterNode(operand, false, concernedProperty, entry.getValue(), operandType, new ArrayList<>(), subConcernedProperty);
                ConfigParserResult configParserResult = validateAndTransform(filters, node, curNode, termNodeExists, collectionNodeExists, concernedProperty, subConcernedProperty);
                if (!configParserResult.getIsValid()) {
                    return configParserResult;
                }
                childNodes.add(node);
                curNode.setChildNodes(childNodes);
            }
        } else {
            ConfigParserResult configParserResult = new ConfigParserResult(null, false, "invalid yaml, structure is neither map/list");
            return configParserResult;
        }

        ConfigParserResult configParserResult = new ConfigParserResult(curNode, true, "");
        return configParserResult;

    }


    public DataOperandsFilterResponse isEndpointValid(FilterNode node, RawApi rawApi, ApiInfo.ApiInfoKey apiInfoKey, List<String> matchingKeySet) {

        List<FilterNode> childNodes = node.getChildNodes();
        if (childNodes.size() == 0) {
            if (!node.getNodeType().toLowerCase().equals(OperandTypes.Data.toString().toLowerCase())) {
                return new DataOperandsFilterResponse(false, null);
            }
            String operand = node.getOperand();
            return filterAction.apply(new FilterActionRequest(node.getValues(), rawApi, apiInfoKey, node.getConcernedProperty(), node.getSubConcernedProperty(), matchingKeySet, operand));
        }

        Boolean result = true;
        DataOperandsFilterResponse dataOperandsFilterResponse;
        String operator = "and";
        if (node.getOperand().toLowerCase().equals("or")) {
            operator = "or";
        }
        Boolean hasKeyOperand = false;

        // todo: introduce priority for each operand
        for (int i = 0; i < childNodes.size(); i++) {
            if (childNodes.get(i).getOperand().toLowerCase().equals("key")) {
                hasKeyOperand = true;
                dataOperandsFilterResponse = isEndpointValid(childNodes.get(i), rawApi, apiInfoKey, null);
                matchingKeySet = dataOperandsFilterResponse.getMatchedEntities();
                result = operator.equals("and") ? result && dataOperandsFilterResponse.getResult() : result || dataOperandsFilterResponse.getResult();
                if (matchingKeySet.size() == 0) {
                    return new DataOperandsFilterResponse(false, matchingKeySet);
                }
            }
        }

        for (int i = 0; i < childNodes.size(); i++) {
            FilterNode childNode = childNodes.get(i);
            if (hasKeyOperand && childNode.getOperand().toLowerCase().equals("key")) {
                continue;
            }
            dataOperandsFilterResponse = isEndpointValid(childNode, rawApi, apiInfoKey, matchingKeySet);
            result = operator.equals("and") ? result && dataOperandsFilterResponse.getResult() : result || dataOperandsFilterResponse.getResult();
            if (childNode.getSubConcernedProperty() != null && childNode.getSubConcernedProperty().toLowerCase().equals("key")) {
                matchingKeySet =  evaluateMatchingKeySet(matchingKeySet, dataOperandsFilterResponse.getMatchedEntities(), operator);
            }
        }

        return new DataOperandsFilterResponse(result, matchingKeySet);
    }

    public ConfigParserValidationResult validateNodeAgainstRules(FilterNode curNode, FilterNode parentNode, Boolean termNodeExists, 
        Boolean collectionNodeExists, String concernedProperty) {

        Object values = curNode.getValues();

        String curNodeType = curNode.getNodeType();
        String parentNodeType = parentNode.getNodeType();
        ConfigParserValidationResult configParserValidationResult = new ConfigParserValidationResult(true, "");

        // todo: extract out all these filters in separate classes and call validate() on all of them

        // ignore all checks if it's the starting node
        if (curNodeType.equals("_ETHER_")) {
            return configParserValidationResult;
        }

        // 1. terminal data nodes should have String/Arraylist<String> values

        if (curNodeType.equals(OperandTypes.Data.toString().toLowerCase())) {
            if (!(isString(values) || (isListOfString(values)))){
                configParserValidationResult.setIsValid(false);
                configParserValidationResult.setErrMsg("terminal data nodes should have String/Arraylist<String> values");
                return configParserValidationResult;
            }
        }

        // 2. A term cannot reside as a child node of another term
        if (curNodeType.equals(OperandTypes.Term.toString().toLowerCase())) {
            if (termNodeExists) {
                configParserValidationResult.setIsValid(false);
                configParserValidationResult.setErrMsg("A term cannot reside as a child node of another term");
                return configParserValidationResult;
            }
        }

        // 3. A collection node cannot reside as a child node of another collection node
        if (curNodeType.equals(OperandTypes.Collection.toString().toLowerCase())) {
            if (collectionNodeExists) {
                configParserValidationResult.setIsValid(false);
                configParserValidationResult.setErrMsg("A collection node cannot reside as a child node of another collection node");
                return configParserValidationResult;
            }
        }

        // 4. data nodes and collection nodes cannot have a null concerned property, i.e. they have to 
        // know what which property they are working on (for ex - request_body, url, method, queryParam etc)

        if (((curNodeType.equals(OperandTypes.Data.toString().toLowerCase()) || curNodeType.equals(OperandTypes.Collection.toString().toLowerCase())) && concernedProperty == null)) {
            configParserValidationResult.setIsValid(false);
            configParserValidationResult.setErrMsg("data nodes and collection nodes cannot have a null concerned property");
            return configParserValidationResult;
        }

        // 5. Last Node should always be a data node
        if (!curNodeType.equals(OperandTypes.Data.toString().toLowerCase())) {

            if (isString(values) || isListOfString(values)) {
                configParserValidationResult.setIsValid(false);
                configParserValidationResult.setErrMsg("Last Node should always be a data node");
                return configParserValidationResult;
            }
        }

        // skip parent node checks if it was the first node
        if (parentNodeType == "_ETHER_") {
            return configParserValidationResult;
        }

        // 6. data node can have either pred, term, collection nodes as parent node

        if (curNodeType.equals(OperandTypes.Data.toString().toLowerCase()) && !this.allowedDataParentNodes.contains(parentNodeType)) {
            configParserValidationResult.setIsValid(false);
            configParserValidationResult.setErrMsg("data node can have either pred, term, collection nodes as parent node");
            return configParserValidationResult;
        }

        // 7. pred node can have either pred, collection, term nodes as parent node

        if (curNodeType.equals(OperandTypes.Pred.toString().toLowerCase()) && !this.allowedPredParentNodes.contains(parentNodeType)) {
            configParserValidationResult.setIsValid(false);
            configParserValidationResult.setErrMsg("pred node can have either pred, collection, term nodes as parent node");
            return configParserValidationResult;
        }

        // 8. term nodes can have only pred nodes as parent node

        if (curNodeType.equals(OperandTypes.Term.toString().toLowerCase()) && !this.allowedTermParentNodes.contains(parentNodeType)) {
            configParserValidationResult.setIsValid(false);
            configParserValidationResult.setErrMsg("term nodes can have only pred nodes as parent node");
            return configParserValidationResult;
        }

        // 9. collection node can have either pred, term nodes as parent node

        if (curNodeType.equals(OperandTypes.Collection.toString().toLowerCase()) && !this.allowedCollectionParentNodes.contains(parentNodeType)) {
            configParserValidationResult.setIsValid(false);
            configParserValidationResult.setErrMsg("collection node can have either pred, term nodes as parent node");
            return configParserValidationResult;
        }

        // 10. data, collection nodes cannot have a null term
        if ((curNodeType.equals(OperandTypes.Data.toString().toLowerCase()) || curNodeType.equals(OperandTypes.Collection.toString().toLowerCase())) && !termNodeExists) {
            configParserValidationResult.setIsValid(false);
            configParserValidationResult.setErrMsg("data, collection nodes cannot have a null term");
            return configParserValidationResult;
        }
        
        return configParserValidationResult;
    }

    public List<String> evaluateMatchingKeySet(List<String> oldSet, List<String> newMatches, String operand) {
        Set<String> s1 = new HashSet<>();
        if (newMatches == null) {
            return new ArrayList<>();
        }
        if (oldSet == null) {
            // doing this for initial step where oldset would be null, hence assigning initially with newmatches
            s1 = new HashSet<>(newMatches);
        } else {
            s1 = new HashSet<>(oldSet);
        }
        Set<String> s2 = new HashSet<>(newMatches);

        if (operand == "and") {
            s1.retainAll(s2);
        } else {
            s1.addAll(s2);
        }

        List<String> output = new ArrayList<>();
        for (String s: s1) {
            output.add(s);
        }
        return output;
    }

    public Boolean isString(Object value) {
        return value instanceof String;
    }

    public Boolean isListOfString(Object value) {
        if(!(value instanceof List)) {
            return false;
        }

        List<Object> listValues = (List<Object>) value;
        for (int i = 0; i < listValues.size(); i++) {
            if (! ( (listValues.get(i) instanceof String) || (listValues.get(i) instanceof Boolean) || (listValues.get(i) instanceof Integer))) {
                return false;
            }
        }

        return true;
    }

}
