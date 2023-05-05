package com.akto.test_editor.filter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import com.akto.dao.test_editor.TestEditorEnums.ExtractOperator;
import com.akto.dao.test_editor.TestEditorEnums.OperandTypes;
import com.akto.dto.ApiInfo;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.DataOperandsFilterResponse;
import com.akto.dto.test_editor.FilterActionRequest;
import com.akto.dto.test_editor.FilterNode;
import com.mongodb.BasicDBObject;

public class Filter {

    private FilterAction filterAction;
    private static final LoggerMaker loggerMaker = new LoggerMaker(Filter.class);

    public Filter() {
        this.filterAction = new FilterAction();
    }
    
    public DataOperandsFilterResponse isEndpointValid(FilterNode node, RawApi rawApi, RawApi testRawApi, ApiInfo.ApiInfoKey apiInfoKey, List<String> matchingKeySet, List<BasicDBObject> contextEntities, boolean keyValOperandSeen, String context, Map<String, Object> varMap, String logId) {

        List<FilterNode> childNodes = node.getChildNodes();
        if (node.getNodeType().equalsIgnoreCase(OperandTypes.Term.toString())) {
            matchingKeySet = null;
        }
        if (childNodes.size() == 0) {
            if (! (node.getNodeType().toLowerCase().equals(OperandTypes.Data.toString().toLowerCase()) || node.getNodeType().toLowerCase().equals(OperandTypes.Extract.toString().toLowerCase()))) {
                return new DataOperandsFilterResponse(false, null, null);
            }
            String operand = node.getOperand();
            FilterActionRequest filterActionRequest = new FilterActionRequest(node.getValues(), rawApi, testRawApi, apiInfoKey, node.getConcernedProperty(), node.getSubConcernedProperty(), matchingKeySet, contextEntities, operand, context, keyValOperandSeen, node.getBodyOperand(), node.getContextProperty());
            Object updatedQuerySet = filterAction.resolveQuerySetValues(filterActionRequest, node.fetchNodeValues(), varMap);
            filterActionRequest.setQuerySet(updatedQuerySet);
            if (node.getOperand().equalsIgnoreCase(ExtractOperator.EXTRACT.toString())) {
                if (filterActionRequest.getConcernedProperty() != null) {
                    filterAction.extract(filterActionRequest, varMap);
                } else {
                    filterAction.extractContextVar(filterActionRequest, varMap);
                }
                return new DataOperandsFilterResponse(true, null, null);
            } else if (filterActionRequest.getConcernedProperty() != null) {
                return filterAction.apply(filterActionRequest);
            } else {
                return filterAction.evaluateContext(filterActionRequest);
            }
        }

        boolean result = true;
        DataOperandsFilterResponse dataOperandsFilterResponse;
        String operator = "and";
        if (node.getOperand().toLowerCase().equals("or")) {
            operator = "or";
            result = false;
        }
        boolean keyValOpSeen = keyValOperandSeen;
        
        for (int i = 0; i < childNodes.size(); i++) {
            FilterNode childNode = childNodes.get(i);
            dataOperandsFilterResponse = isEndpointValid(childNode, rawApi, testRawApi, apiInfoKey, matchingKeySet, contextEntities, keyValOpSeen,context, varMap, logId);
            if (!dataOperandsFilterResponse.getResult()) {
                loggerMaker.infoAndAddToDb("invalid node condition " + logId + " operand " + childNode.getOperand() + 
                " concernedProperty " + childNode.getConcernedProperty() + " subConcernedProperty " + childNode.getSubConcernedProperty()
                + " contextProperty " + childNode.getContextProperty() + " context " + context, LogDb.TESTING);
            }
            contextEntities = dataOperandsFilterResponse.getContextEntities();
            result = operator.equals("and") ? result && dataOperandsFilterResponse.getResult() : result || dataOperandsFilterResponse.getResult();
            
            if (childNodes.get(i).getOperand().toLowerCase().equals("key")) {
                keyValOpSeen = true;
            }

            if (!childNode.getNodeType().equalsIgnoreCase("extract")) {
                matchingKeySet = evaluateMatchingKeySet(matchingKeySet, dataOperandsFilterResponse.getMatchedEntities(), operator);
            }
        }

        return new DataOperandsFilterResponse(result, matchingKeySet, contextEntities);

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
