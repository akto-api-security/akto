package com.akto.dao.test_editor.filter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dao.test_editor.TestEditorEnums.ContextOperator;
import com.akto.dao.test_editor.TestEditorEnums.DataOperands;
import com.akto.dao.test_editor.TestEditorEnums.OperandTypes;
import com.akto.dao.test_editor.TestEditorEnums.PredicateOperator;
import com.akto.dao.test_editor.TestEditorEnums.TermOperands;
import com.akto.dto.test_editor.ConfigParserResult;
import com.akto.dto.test_editor.ConfigParserValidationResult;
import com.akto.dto.test_editor.FilterNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ConfigParser {

    private List<String> allowedDataParentNodes = Arrays.asList("pred", "payload", "term", "body", "context");
    private List<String> allowedPredParentNodes = Arrays.asList("pred", "collection", "payload", "term");
    private List<String> allowedTermParentNodes = Arrays.asList("pred");
    private List<String> allowedCollectionParentNodes = Arrays.asList("pred", "term");
    private static final String CIDR_REGEX = "^([0-1]?\\d{1,2}|2[0-4]\\d|25[0-5])\\.([0-1]?\\d{1,2}|2[0-4]\\d|25[0-5])\\.([0-1]?\\d{1,2}|2[0-4]\\d|25[0-5])\\.([0-1]?\\d{1,2}|2[0-4]\\d|25[0-5])/([0-2]?\\d|3[0-2])$";

    public ConfigParser() {
    }
    
    public ConfigParserResult parse(Object filters) {

        Map<String, Object> filterMap = (Map) filters;

        if (filterMap == null) {
            return null;
        }
        
        FilterNode node = new FilterNode("and", false, null, filters, "_ETHER_", new ArrayList<>(), null, null, null, null);
        ConfigParserResult configParserResult = validateAndTransform(filterMap, node, node, false, false, null, null, null, null, null);

        return configParserResult;
    }

    public ConfigParserResult validateAndTransform(Map<String, Object> filters, FilterNode curNode, FilterNode parentNode, Boolean termNodeExists, 
        Boolean collectionNodeExists, String concernedProperty, String subConcernedProperty, String bodyOperand, String contextProperty, String collectionProperty) {

        Object values = curNode.getValues();

        ConfigParserValidationResult configParserValidationResult = validateNodeAgainstRules(curNode, parentNode, termNodeExists, collectionNodeExists, concernedProperty, contextProperty);
        if (curNode.getOperand().equalsIgnoreCase(PredicateOperator.SSRF_URL_HIT.toString())) {
            return new ConfigParserResult(null, true, "");
        }

        if (!configParserValidationResult.getIsValid()) {
            return new ConfigParserResult(null, false, configParserValidationResult.getErrMsg());
        }

        if (curNode.getNodeType().equalsIgnoreCase(OperandTypes.Data.toString()) || curNode.getNodeType().equalsIgnoreCase(OperandTypes.Extract.toString())) {
            return new ConfigParserResult(null, true, "");
        }

        if (curNode.getOperand().equalsIgnoreCase(PredicateOperator.COMPARE_GREATER.toString())) {
            return new ConfigParserResult(null, true, "");
        }

        if (curNode.getNodeType().equalsIgnoreCase(OperandTypes.Context.toString()) && curNode.getOperand().equalsIgnoreCase(ContextOperator.ENDPOINT_IN_TRAFFIC_CONTEXT.toString()) && (isString(values) || isListOfString(values))) {
            return new ConfigParserResult(null, true, "");
        }

        if (curNode.getNodeType().equalsIgnoreCase(OperandTypes.Term.toString())) {
            termNodeExists = true;
            concernedProperty = curNode.getOperand();
        }

        if (curNode.getNodeType().equalsIgnoreCase(OperandTypes.Context.toString())) {
            contextProperty = curNode.getOperand();
        }

        if (curNode.getNodeType().equalsIgnoreCase(OperandTypes.Collection.toString())) {
            collectionNodeExists = true;
            collectionProperty = curNode.getOperand();
        }

        if (curNode.getNodeType().equalsIgnoreCase(OperandTypes.Body.toString())) {
            bodyOperand = curNode.getOperand();
        }

        if (curNode.getNodeType().equalsIgnoreCase(OperandTypes.Payload.toString())) {
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
                    FilterNode node = new FilterNode(operand, false, concernedProperty, entry.getValue(), operandType, new ArrayList<>(), subConcernedProperty, bodyOperand, contextProperty, collectionProperty);
                    ConfigParserResult configParserResult = validateAndTransform(filters, node, curNode, termNodeExists, collectionNodeExists, concernedProperty, subConcernedProperty, bodyOperand, contextProperty, collectionProperty);
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
                FilterNode node = new FilterNode(operand, false, concernedProperty, entry.getValue(), operandType, new ArrayList<>(), subConcernedProperty, bodyOperand, contextProperty, collectionProperty);
                ConfigParserResult configParserResult = validateAndTransform(filters, node, curNode, termNodeExists, collectionNodeExists, concernedProperty, subConcernedProperty, bodyOperand, contextProperty, collectionProperty);
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

    public ConfigParserValidationResult validateNodeAgainstRules(FilterNode curNode, FilterNode parentNode, Boolean termNodeExists, 
        Boolean collectionNodeExists, String concernedProperty, String contextProperty) {

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
            if (!(isString(values) || isListOfString(values) || values instanceof Double || isListOfDouble(values))){
                configParserValidationResult.setIsValid(false);
                configParserValidationResult.setErrMsg("terminal data nodes should have String/Arraylist<String> or Double/ArrayList<Double> values");
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

        if (((curNodeType.equals(OperandTypes.Data.toString().toLowerCase()) || curNodeType.equals(OperandTypes.Collection.toString().toLowerCase())) && concernedProperty == null && contextProperty == null)) {
            configParserValidationResult.setIsValid(false);
            configParserValidationResult.setErrMsg("data nodes and collection nodes cannot have a null concerned property");
            return configParserValidationResult;
        }

        // 5. Last Node should always be a data/extract node
        if (! (curNodeType.equals(OperandTypes.Data.toString().toLowerCase()) || curNodeType.equals(OperandTypes.Extract.toString().toLowerCase()) || curNodeType.equals(OperandTypes.Context.toString().toLowerCase()) || curNode.getOperand().equalsIgnoreCase(TestEditorEnums.PredicateOperator.COMPARE_GREATER.toString()) || curNode.getOperand().equalsIgnoreCase(TestEditorEnums.PredicateOperator.SSRF_URL_HIT.toString()))) {
            if (isString(values) || isListOfString(values)) {
                configParserValidationResult.setIsValid(false);
                configParserValidationResult.setErrMsg("Last Node should always be a data/extract node");
                return configParserValidationResult;
            }
        }

        // skip parent node checks if it was the first node
        if (parentNodeType.equals("_ETHER_")) {
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
        if ((curNodeType.equals(OperandTypes.Data.toString().toLowerCase()) || curNodeType.equals(OperandTypes.Collection.toString().toLowerCase())) && (contextProperty == null &&!termNodeExists)) {
            configParserValidationResult.setIsValid(false);
            configParserValidationResult.setErrMsg("data, collection nodes cannot have a null term");
            return configParserValidationResult;
        }
        
        // 11. CIDR operands can only be applied to IP properties.
        if (isIpOperand(curNode.getOperand())) {
            
            if (!isIpProperty(concernedProperty)) {
                configParserValidationResult.setIsValid(false);
                configParserValidationResult.setErrMsg("IP CIDR rules can only be applied to source_ip and destination_ip");
                return configParserValidationResult;
            }

            if (!isListOfValidIPv4CIDR(values)) {
                configParserValidationResult.setIsValid(false);
                configParserValidationResult.setErrMsg("Values must be a list of valid IPv4 CIDR notations");
                return configParserValidationResult;
            }
        
        }

        if (isIpProperty(concernedProperty) && !isIpOperand(curNode.getOperand())) {
            configParserValidationResult.setIsValid(false);
            configParserValidationResult.setErrMsg("IP properties can only be used with CIDR operands");
            return configParserValidationResult;
        }


        return configParserValidationResult;
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

    
    private Boolean isIpOperand(String operand) {
        return DataOperands.CONTAINS_EITHER_CIDR.toString().equals(operand)
                || DataOperands.NOT_CONTAINS_CIDR.toString().equals(operand);
    }

    private Boolean isIpProperty(String concernedProperty) {
        return TermOperands.SOURCE_IP.toString().equals(concernedProperty)
                || TermOperands.DESTINATION_IP.toString().equals(concernedProperty);
    }

    public Boolean isListOfValidIPv4CIDR(Object value) {
        if (!(value instanceof List)) {
            return false;
        }
    
        List<Object> listValues = (List<Object>) value;
        Pattern cidrPattern = Pattern.compile(ConfigParser.CIDR_REGEX);
    
        for (Object item : listValues) {
            if (!(item instanceof String) || !cidrPattern.matcher((String) item).matches()) {
                return false;
            }
        }
    
        return true;
    }

    public static boolean isFilterNodeValidForAgenticTest(FilterNode filterNode) {
        List<FilterNode> childNodes = filterNode.getChildNodes();
        for (FilterNode childNode : childNodes) {
            if (childNode.getOperand().equalsIgnoreCase(TermOperands.TEST_TYPE.toString())) {
                Object values = childNode.getValues();
                if (values instanceof List) {
                    List<Map<String,String>> listValues = (List<Map<String,String>>) values;
                    if (listValues.size() != 1) {
                        return false;
                    }
                    Map<String,String> typeValueObj = listValues.get(0);
                    String typeValue = typeValueObj.get("eq");
                    return typeValue.equalsIgnoreCase("AGENTIC");
                }
            }
        }
        return false;
    }
    
    public Boolean isListOfDouble(Object value) {
        if(!(value instanceof List)) {
            return false;
        }
        List<Object> listValues = (List<Object>) value;
        for (Object v : listValues) {
            if (!(v instanceof Double)) {
                return false;
            }
        }
        return true;
    }

}
