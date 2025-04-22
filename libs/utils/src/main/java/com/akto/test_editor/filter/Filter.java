package com.akto.test_editor.filter;

import java.util.*;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dao.test_editor.TestEditorEnums.ExtractOperator;
import com.akto.dao.test_editor.TestEditorEnums.OperandTypes;
import com.akto.dto.ApiInfo;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.DataOperandFilterRequest;
import com.akto.dto.test_editor.DataOperandsFilterResponse;
import com.akto.dto.test_editor.FilterActionRequest;
import com.akto.dto.test_editor.FilterNode;
import com.akto.test_editor.filter.data_operands_impl.ValidationResult;
import com.mongodb.BasicDBObject;
import org.apache.commons.lang3.StringUtils;

public class Filter {

    private FilterAction filterAction;
    private static final LoggerMaker loggerMaker = new LoggerMaker(Filter.class);
    
    private static boolean isTestingContext = true;

    public Filter() {
        this.filterAction = new FilterAction();
        if (System.getenv("IS_TESTING_CONTEXT") != null) {
            this.isTestingContext = System.getenv("IS_TESTING_CONTEXT") == "true";
        }
    }
//    public DataOperandsFilterResponse isEndpointValid(FilterNode node, RawApi rawApi, RawApi testRawApi, ApiInfo.ApiInfoKey apiInfoKey, List<String> matchingKeySet, List<BasicDBObject> contextEntities, boolean keyValOperandSeen, String context, Map<String, Object> varMap, String logId, boolean skipExtractExecution) {
//        StringBuilder stringBuilder = new StringBuilder();
//        return isEndpointValid(node, rawApi, testRawApi, apiInfoKey, matchingKeySet, contextEntities, keyValOperandSeen, context, varMap, logId,skipExtractExecution, stringBuilder);
//    }
    
    public DataOperandsFilterResponse isEndpointValid(FilterNode node, RawApi rawApi, RawApi testRawApi, ApiInfo.ApiInfoKey apiInfoKey, List<String> matchingKeySet, List<BasicDBObject> contextEntities, boolean keyValOperandSeen, String context, Map<String, Object> varMap, String logId, boolean skipExtractExecution) {

        List<FilterNode> childNodes = node.getChildNodes();
        if (node.getNodeType().equalsIgnoreCase(OperandTypes.Term.toString()) || node.getNodeType().equalsIgnoreCase(OperandTypes.Collection.toString())) {
            matchingKeySet = null;
        }
        if (childNodes.size() == 0) {
            if (node.getOperand().equalsIgnoreCase(TestEditorEnums.PredicateOperator.COMPARE_GREATER.toString())) {
                Object updatedQuerySet = filterAction.resolveQuerySetValues(null, node.fetchNodeValues(), varMap);
                List<Object> val = (List<Object>) updatedQuerySet;
                DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(val.get(0), Arrays.asList(val.get(1)), "gt");
                ValidationResult validationResult = filterAction.invokeFilter(dataOperandFilterRequest);
                return new DataOperandsFilterResponse(validationResult.getIsValid(), matchingKeySet, contextEntities, null, validationResult.getValidationReason());
            }
            if (node.getOperand().equalsIgnoreCase(TestEditorEnums.PredicateOperator.SSRF_URL_HIT.toString())) {
                Object updatedQuerySet = filterAction.resolveQuerySetValues(null, node.fetchNodeValues(), varMap);
                List<Object> val = (List<Object>) updatedQuerySet;
                DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(null, val, "ssrf_url_hit");
                ValidationResult validationResult = filterAction.invokeFilter(dataOperandFilterRequest);
                return new DataOperandsFilterResponse(validationResult.getIsValid(), matchingKeySet, contextEntities, null, validationResult.getValidationReason());
            }
            if (! (node.getNodeType().toLowerCase().equals(OperandTypes.Data.toString().toLowerCase()) || node.getNodeType().toLowerCase().equals(OperandTypes.Extract.toString().toLowerCase()) || node.getNodeType().toLowerCase().equals(OperandTypes.Context.toString().toLowerCase() ))) {
                return new DataOperandsFilterResponse(false, null, null, null);
            }
            String operand = node.getOperand();
            FilterActionRequest filterActionRequest = new FilterActionRequest(node.getValues(), rawApi, testRawApi, apiInfoKey, node.getConcernedProperty(), node.getSubConcernedProperty(), matchingKeySet, contextEntities, operand, context, keyValOperandSeen, node.getBodyOperand(), node.getContextProperty(), node.getCollectionProperty());
            if (this.isTestingContext) {
                Object updatedQuerySet = filterAction.resolveQuerySetValues(filterActionRequest, node.fetchNodeValues(), varMap);
                filterActionRequest.setQuerySet(updatedQuerySet);
            }
            if (node.getOperand().equalsIgnoreCase(ExtractOperator.EXTRACT.toString()) || node.getOperand().equalsIgnoreCase(ExtractOperator.EXTRACTMULTIPLE.toString())) {
                boolean resp = true;
                boolean extractMultiple = node.getOperand().equalsIgnoreCase(ExtractOperator.EXTRACTMULTIPLE.toString());
                if (node.getCollectionProperty() != null && (node.getCollectionProperty().equalsIgnoreCase(TestEditorEnums.CollectionOperands.FOR_ONE.toString()) || node.getCollectionProperty().equalsIgnoreCase(TestEditorEnums.CollectionOperands.FOR_ALL.toString()))) {
                    if (skipExtractExecution) {
                        return new DataOperandsFilterResponse(true, null, null, node);
                    }
                }
                if (filterActionRequest.getConcernedProperty() != null) {
                    filterAction.extract(filterActionRequest, varMap, extractMultiple);
                } else {
                    resp = filterAction.extractContextVar(filterActionRequest, varMap);
                }
                return new DataOperandsFilterResponse(resp, null, null, null);
            } else if (filterActionRequest.getConcernedProperty() != null && !node.getNodeType().equalsIgnoreCase("context")) {
                return filterAction.apply(filterActionRequest);
            } else {
                if (filterActionRequest.getContextProperty() == null) {
                    filterActionRequest.setContextProperty(node.getOperand());
                }
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
        
        FilterNode firstExtractNode = null;
        StringBuilder validationReason = new StringBuilder();
        try {
            Map<FilterNode, String> childNodeVsValidationReason = new HashMap<>();
            for (int i = 0; i < childNodes.size(); i++) {
                FilterNode childNode = childNodes.get(i);
                boolean skipExecutingExtractNode = skipExtractExecution;
                if (node.getNodeType().equalsIgnoreCase(TestEditorEnums.OperandTypes.Collection.toString()) && i == 0) {
                    skipExecutingExtractNode = (firstExtractNode == null);
                }
                dataOperandsFilterResponse = isEndpointValid(childNode, rawApi, testRawApi, apiInfoKey, matchingKeySet, contextEntities, keyValOpSeen,context, varMap, logId, skipExecutingExtractNode);
                if (!dataOperandsFilterResponse.getResult()) {
                    childNodeVsValidationReason.put(childNode, dataOperandsFilterResponse.getValidationReason());
                }

                if (firstExtractNode == null) {
                    firstExtractNode = dataOperandsFilterResponse.getExtractNode();
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
            if (!result && !childNodeVsValidationReason.isEmpty()) {//Validation failed by all conditions
                validationReason.append("\n").append(node.getOperand().toLowerCase()).append(":");
                if (operator.equalsIgnoreCase("or")) {
                    for (FilterNode failedValidation: childNodeVsValidationReason.keySet()) {
                        String validationReasonStr = childNodeVsValidationReason.getOrDefault(failedValidation, null);
                        if (!StringUtils.isEmpty(validationReasonStr)) {
                            validationReasonStr = validationReasonStr.replaceAll("\n","\n\t");
                            validationReason.append(validationReasonStr);
                        }
                    }
                } else {
                    String validationReasonStr = childNodeVsValidationReason.getOrDefault(childNodeVsValidationReason.keySet().iterator().next(), null);
                    if (!StringUtils.isEmpty(validationReasonStr)) {
                        validationReasonStr = validationReasonStr.replaceAll("\n","\n\t");
                        validationReason.append(validationReasonStr);
                    }
                }
            }

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while creating failed validation reason", LogDb.TESTING);
        }
        if (node.getNodeType().equalsIgnoreCase(TestEditorEnums.OperandTypes.Collection.toString()) && firstExtractNode != null && result) {
            DataOperandsFilterResponse resp = isEndpointValid(firstExtractNode, rawApi, testRawApi, apiInfoKey, matchingKeySet, contextEntities, keyValOpSeen,context, varMap, logId, false);
            result = resp.getResult();
        }

        return new DataOperandsFilterResponse(result, matchingKeySet, contextEntities, firstExtractNode, validationReason.toString());

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
