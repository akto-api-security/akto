package com.akto.test_editor.filter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.akto.dao.test_editor.TestEditorEnums.ExtractOperator;
import com.akto.dao.test_editor.TestEditorEnums.OperandTypes;
import com.akto.dto.ApiInfo;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.DataOperandsFilterResponse;
import com.akto.dto.test_editor.FilterActionRequest;
import com.akto.dto.test_editor.FilterNode;

public class Filter {

    private FilterAction filterAction;

    public Filter() {
        this.filterAction = new FilterAction();
    }
    
    public DataOperandsFilterResponse isEndpointValid(FilterNode node, RawApi rawApi, RawApi testRawApi, ApiInfo.ApiInfoKey apiInfoKey, List<String> matchingKeySet, List<String> matchingValues, Boolean keyOperandSeen, String context, Map<String, Object> varMap) {

        List<FilterNode> childNodes = node.getChildNodes();
        if (childNodes.size() == 0) {
            if (! (node.getNodeType().toLowerCase().equals(OperandTypes.Data.toString().toLowerCase()) || node.getNodeType().toLowerCase().equals(OperandTypes.Extract.toString().toLowerCase()))) {
                return new DataOperandsFilterResponse(false, null, null);
            }
            String operand = node.getOperand();
            FilterActionRequest filterActionRequest = new FilterActionRequest(node.getValues(), rawApi, testRawApi, apiInfoKey, node.getConcernedProperty(), node.getSubConcernedProperty(), matchingKeySet, matchingValues, operand, context, keyOperandSeen, node.getBodyOperand());
            Object updatedQuerySet = filterAction.resolveQuerySetValues(filterActionRequest, node.getValues(), varMap);
            filterActionRequest.setQuerySet(updatedQuerySet);
            if (node.getOperand().equalsIgnoreCase(ExtractOperator.EXTRACT.toString())) {
                filterAction.extract(filterActionRequest, varMap);
                return new DataOperandsFilterResponse(true, null, null);
            } else {
                return filterAction.apply(filterActionRequest);
            }
        }

        Boolean result = true;
        DataOperandsFilterResponse dataOperandsFilterResponse;
        String operator = "and";
        if (node.getOperand().toLowerCase().equals("or")) {
            operator = "or";
            result = false;
        }
        Boolean hasKeyOperand = false;

        // todo: introduce priority for each operand
        for (int i = 0; i < childNodes.size(); i++) {
            if (childNodes.get(i).getOperand().toLowerCase().equals("key")) {
                hasKeyOperand = true;
                keyOperandSeen = true;
                dataOperandsFilterResponse = isEndpointValid(childNodes.get(i), rawApi, testRawApi, apiInfoKey, null, null, true, context, varMap);
                matchingKeySet = dataOperandsFilterResponse.getMatchedEntities();
                result = operator.equals("and") ? result && dataOperandsFilterResponse.getResult() : result || dataOperandsFilterResponse.getResult();
                if (matchingKeySet.size() == 0) {
                    return new DataOperandsFilterResponse(false, matchingKeySet, null);
                }
            }
        }

        for (int i = 0; i < childNodes.size(); i++) {
            FilterNode childNode = childNodes.get(i);
            if (hasKeyOperand && childNode.getOperand().toLowerCase().equals("key")) {
                continue;
            }
            dataOperandsFilterResponse = isEndpointValid(childNode, rawApi, testRawApi, apiInfoKey, matchingKeySet, matchingValues, keyOperandSeen,context, varMap);
            result = operator.equals("and") ? result && dataOperandsFilterResponse.getResult() : result || dataOperandsFilterResponse.getResult();
            if (childNode.getSubConcernedProperty() != null && childNode.getSubConcernedProperty().toLowerCase().equals("key") && !childNode.getNodeType().equalsIgnoreCase("extract")) {
                matchingKeySet = evaluateMatchingKeySet(matchingKeySet, dataOperandsFilterResponse.getMatchedEntities(), operator);
            } else if (childNode.getSubConcernedProperty() != null && childNode.getSubConcernedProperty().toLowerCase().equals("value") && !childNode.getNodeType().equalsIgnoreCase("extract")) {
                matchingValues = evaluateMatchingKeySet(matchingValues, dataOperandsFilterResponse.getMatchedValues(), operator);
            }
        }

        return new DataOperandsFilterResponse(result, matchingKeySet, matchingValues);
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

}
