package com.akto.test_editor.filter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.akto.dao.test_editor.TestEditorEnums.BodyOperator;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.DataOperandFilterRequest;
import com.akto.dto.test_editor.DataOperandsFilterResponse;
import com.akto.dto.test_editor.FilterActionRequest;
import com.akto.dto.type.RequestTemplate;
import com.akto.rules.TestPlugin;
import com.akto.test_editor.Utils;
import com.akto.test_editor.execution.VariableResolver;
import com.akto.test_editor.filter.data_operands_impl.ContainsAllFilter;
import com.akto.test_editor.filter.data_operands_impl.ContainsEitherFilter;
import com.akto.test_editor.filter.data_operands_impl.DataOperandsImpl;
import com.akto.test_editor.filter.data_operands_impl.EqFilter;
import com.akto.test_editor.filter.data_operands_impl.GreaterThanEqFilter;
import com.akto.test_editor.filter.data_operands_impl.GreaterThanFilter;
import com.akto.test_editor.filter.data_operands_impl.LesserThanEqFilter;
import com.akto.test_editor.filter.data_operands_impl.LesserThanFilter;
import com.akto.test_editor.filter.data_operands_impl.NeqFilter;
import com.akto.test_editor.filter.data_operands_impl.RegexFilter;
import com.akto.utils.RedactSampleData;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

public final class FilterAction {
    
    public final Map<String, DataOperandsImpl> filters = new HashMap<String, DataOperandsImpl>() {{
        put("contains_all", new ContainsAllFilter());
        put("contains_either", new ContainsEitherFilter());
        put("regex", new RegexFilter());
        put("eq", new EqFilter());
        put("neq", new NeqFilter());
        put("gt", new GreaterThanFilter());
        put("gte", new GreaterThanEqFilter());
        put("lt", new LesserThanFilter());
        put("lte", new LesserThanEqFilter());
    }};

    public FilterAction() { }

    public DataOperandsFilterResponse apply(FilterActionRequest filterActionRequest) {

        String concernedProperty = filterActionRequest.getConcernedProperty();
        switch (concernedProperty.toLowerCase()) {
            case "url":
                return applyFilterOnUrl(filterActionRequest);
            case "method":
                return applyFilterOnMethod(filterActionRequest);
            case "api_collection_id":
                return applyFilterOnApiCollectionId(filterActionRequest);
            case "request_payload":
                return applyFilterOnRequestPayload(filterActionRequest);
            case "response_payload":
                return applyFilterOnResponsePayload(filterActionRequest);
            case "request_headers":
                return applyFilterOnRequestHeaders(filterActionRequest);
            case "response_headers":
                return applyFilterOnResponseHeaders(filterActionRequest);
            case "query_param":
                return applyFilterOnQueryParams(filterActionRequest);
            case "response_code":
                return applyFilterOnResponseCode(filterActionRequest);
            default:
                return new DataOperandsFilterResponse(false, null);

        }
    }

    public void extract(FilterActionRequest filterActionRequest, Map<String, Object> varMap) {

        String concernedProperty = filterActionRequest.getConcernedProperty();
        switch (concernedProperty.toLowerCase()) {
            case "url":
                extractUrl(filterActionRequest, varMap);
                return;
            case "method":
                extractMethod(filterActionRequest, varMap);
                return;
            case "api_collection_id":
                extractApiCollectionId(filterActionRequest, varMap);
                return;
            case "request_payload":
                extractReqPayload(filterActionRequest, varMap);
                return;
            case "response_payload":
                extractRespPayload(filterActionRequest, varMap);
                return;
            case "request_headers":
                extractRequestHeaders(filterActionRequest, varMap);
                return;
            case "response_headers":
                extractResponseHeaders(filterActionRequest, varMap);
                return;
            case "query_param":
                extractQueryParams(filterActionRequest, varMap);
                return;
            case "response_code":
                extractResponseCode(filterActionRequest, varMap);
                return;
            default:
                return;

        }
    }

    public DataOperandsFilterResponse applyFilterOnUrl(FilterActionRequest filterActionRequest) {

        String url = filterActionRequest.getApiInfoKey().getUrl();

        DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(url, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
        Boolean res = invokeFilter(dataOperandFilterRequest);
        return new DataOperandsFilterResponse(res, null);
    }

    public void extractUrl(FilterActionRequest filterActionRequest, Map<String, Object> varMap) {
        String url = filterActionRequest.getApiInfoKey().getUrl();
        List<String> querySet = (List<String>) filterActionRequest.getQuerySet();
        if (varMap.containsKey(querySet.get(0)) && varMap.get(querySet.get(0)) != null) {
            return;
        }
        varMap.put(querySet.get(0), url);
    }

    public DataOperandsFilterResponse applyFilterOnMethod(FilterActionRequest filterActionRequest) {

        String method = filterActionRequest.getApiInfoKey().getMethod().toString();
        DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(method, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
        Boolean res = invokeFilter(dataOperandFilterRequest);
        return new DataOperandsFilterResponse(res, null);
    }

    public void extractMethod(FilterActionRequest filterActionRequest, Map<String, Object> varMap) {
        String method = filterActionRequest.getApiInfoKey().getMethod().toString();
        List<String> querySet = (List<String>) filterActionRequest.getQuerySet();
        if (varMap.containsKey(querySet.get(0)) && varMap.get(querySet.get(0)) != null) {
            return;
        }
        varMap.put(querySet.get(0), method);
    }

    public DataOperandsFilterResponse applyFilterOnApiCollectionId(FilterActionRequest filterActionRequest) {

        String apiCollectionId = Integer.toString(filterActionRequest.getApiInfoKey().getApiCollectionId());
        DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(apiCollectionId, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
        Boolean res = invokeFilter(dataOperandFilterRequest);
        return new DataOperandsFilterResponse(res, null);
    }

    public void extractApiCollectionId(FilterActionRequest filterActionRequest, Map<String, Object> varMap) {
        String apiCollectionId = Integer.toString(filterActionRequest.getApiInfoKey().getApiCollectionId());
        List<String> querySet = (List<String>) filterActionRequest.getQuerySet();
        if (varMap.containsKey(querySet.get(0)) && varMap.get(querySet.get(0)) != null) {
            return;
        }
        varMap.put(querySet.get(0), apiCollectionId);
    }

    public DataOperandsFilterResponse applyFilterOnResponseCode(FilterActionRequest filterActionRequest) {

        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null) {
            return new DataOperandsFilterResponse(false, null);
        }
        int respCode = rawApi.getResponse().getStatusCode();
        DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(respCode, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
        Boolean res = invokeFilter(dataOperandFilterRequest);
        return new DataOperandsFilterResponse(res, null);
    }

    public void extractResponseCode(FilterActionRequest filterActionRequest, Map<String, Object> varMap) {
        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null) {
            return;
        }
        String respCode = Integer.toString(rawApi.getResponse().getStatusCode());
        List<String> querySet = (List<String>) filterActionRequest.getQuerySet();
        if (varMap.containsKey(querySet.get(0)) && varMap.get(querySet.get(0)) != null) {
            return;
        }
        varMap.put(querySet.get(0), respCode);
    }

    public DataOperandsFilterResponse applyFilterOnRequestPayload(FilterActionRequest filterActionRequest) {

        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null) {
            return new DataOperandsFilterResponse(false, null);
        }
        String reqBody = rawApi.getRequest().getBody();
        return applyFilterOnPayload(filterActionRequest, reqBody);
    }

    public DataOperandsFilterResponse applyFilterOnResponsePayload(FilterActionRequest filterActionRequest) {

        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null) {
            return new DataOperandsFilterResponse(false, null);
        }
        String reqBody = rawApi.getResponse().getBody();
        return applyFilterOnPayload(filterActionRequest, reqBody);
    }

    public DataOperandsFilterResponse applyFilterOnPayload(FilterActionRequest filterActionRequest, String payload) {

        BasicDBObject payloadObj = new BasicDBObject();
        try {
            payloadObj =  BasicDBObject.parse(payload);
        } catch(Exception e) {
            // add log
        }

        Set<String> matchingKeySet = new HashSet<>();
        List<String> matchingKeys = new ArrayList<>();
        List<String> matchingValueKeySet = new ArrayList<>();
        Boolean res = false;

        if (filterActionRequest.getConcernedSubProperty() != null && filterActionRequest.getConcernedSubProperty().toLowerCase().equals("key")) {

            getMatchingKeysForPayload(payloadObj, null, filterActionRequest.getQuerySet(), filterActionRequest.getOperand(), matchingKeySet);
            for (String s: matchingKeySet) {
                matchingKeys.add(s);
            }
            return new DataOperandsFilterResponse(matchingKeys.size() > 0, matchingKeys);

        } else if (filterActionRequest.getConcernedSubProperty() != null && filterActionRequest.getConcernedSubProperty().toLowerCase().equals("value")) {
            matchingKeys = filterActionRequest.getMatchingKeySet();
            valueExists(payloadObj, null, filterActionRequest.getQuerySet(), filterActionRequest.getOperand(), matchingKeys, filterActionRequest.getKeyValOperandSeen(), matchingValueKeySet);
            return new DataOperandsFilterResponse(matchingValueKeySet.size() > 0, matchingValueKeySet);
        } else if (filterActionRequest.getConcernedSubProperty() == null) {
            Object val = payload;

            if (filterActionRequest.getBodyOperand() != null && filterActionRequest.getBodyOperand().equalsIgnoreCase(BodyOperator.LENGTH.toString())) {
                val = payload.trim().length() - 2; // todo:
            } else if (filterActionRequest.getBodyOperand() != null && filterActionRequest.getBodyOperand().equalsIgnoreCase(BodyOperator.PERCENTAGE_MATCH.toString())) {
                RawApi sampleRawApi = filterActionRequest.getRawApi();
                if (sampleRawApi == null) {
                    return new DataOperandsFilterResponse(false, null);
                }
                double percentageMatch = TestPlugin.compareWithOriginalResponse(payload, sampleRawApi.getResponse().getBody(), new HashMap<>());
                val = (int) percentageMatch;
            }
            
            DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(val, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
            res = invokeFilter(dataOperandFilterRequest);
            return new DataOperandsFilterResponse(res, null);
        } else if (filterActionRequest.getConcernedSubProperty() == null) {
            DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(payload, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
            res = invokeFilter(dataOperandFilterRequest);
            return new DataOperandsFilterResponse(res, null);
        }

        return new DataOperandsFilterResponse(false, null);
    }

    public void extractReqPayload(FilterActionRequest filterActionRequest, Map<String, Object> varMap) {
        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null) {
            return;
        }
        String payload = rawApi.getRequest().getBody();
        extractPayload(filterActionRequest, varMap, payload);
    }

    public void extractRespPayload(FilterActionRequest filterActionRequest, Map<String, Object> varMap) {
        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null) {
            return;
        }
        String payload = rawApi.getResponse().getBody();
        extractPayload(filterActionRequest, varMap, payload);
    }

    public void extractPayload(FilterActionRequest filterActionRequest, Map<String, Object> varMap, String payload) {

        List<String> querySet = (List<String>) filterActionRequest.getQuerySet();
        Object val = null;
        String key = querySet.get(0);
        BasicDBObject reqObj = new BasicDBObject();
        try {
            reqObj =  BasicDBObject.parse(payload);
        } catch(Exception e) {
            // add log
        }

        if (filterActionRequest.getConcernedSubProperty() != null && filterActionRequest.getConcernedSubProperty().toLowerCase().equals("key")) {
            if (filterActionRequest.getMatchingKeySet() != null && filterActionRequest.getMatchingKeySet().size() > 0) {
                val = filterActionRequest.getMatchingKeySet().get(0);
            }

        } else if (filterActionRequest.getConcernedSubProperty() != null && filterActionRequest.getConcernedSubProperty().toLowerCase().equals("value")) {
            if (filterActionRequest.getMatchingKeySet() != null && filterActionRequest.getMatchingKeySet().size() > 0) {
                val = getValue(reqObj, null, filterActionRequest.getMatchingKeySet().get(0));
            }
        } else if (filterActionRequest.getBodyOperand() != null) {
            if (filterActionRequest.getBodyOperand().equalsIgnoreCase(BodyOperator.LENGTH.toString())) {
                val = payload.length() - 2;
            } else if (filterActionRequest.getBodyOperand().equalsIgnoreCase(BodyOperator.PERCENTAGE_MATCH.toString())) {
                RawApi sampleRawApi = filterActionRequest.getRawApi();
                if (sampleRawApi == null) {
                    return;
                }
                double percentageMatch = TestPlugin.compareWithOriginalResponse(payload, sampleRawApi.getResponse().getBody(), new HashMap<>());
                val = (int) percentageMatch;
            }
        } else {
            val = payload;
        }

        if (val != null) {
            if (varMap.containsKey(key) && varMap.get(key) != null) {
                return;
            }
            varMap.put(key, val);
        }
    }

    public DataOperandsFilterResponse applyFilterOnRequestHeaders(FilterActionRequest filterActionRequest) {

        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null) {
            return new DataOperandsFilterResponse(false, null);
        }
        
        Map<String, List<String>> reqHeaders = rawApi.getRequest().getHeaders();
        return applyFiltersOnHeaders(filterActionRequest, reqHeaders);
    }

    public DataOperandsFilterResponse applyFilterOnResponseHeaders(FilterActionRequest filterActionRequest) {

        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null) {
            return new DataOperandsFilterResponse(false, null);
        }
        
        Map<String, List<String>> respHeaders = rawApi.getResponse().getHeaders();

        return applyFiltersOnHeaders(filterActionRequest, respHeaders);
    }

    public void extractRequestHeaders(FilterActionRequest filterActionRequest, Map<String, Object> varMap) {

        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null) {
            return;
        }
        
        Map<String, List<String>> reqHeaders = rawApi.getRequest().getHeaders();

        extractHeaders(filterActionRequest, varMap, reqHeaders);
    }

    public void extractResponseHeaders(FilterActionRequest filterActionRequest, Map<String, Object> varMap) {

        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null) {
            return;
        }
        
        Map<String, List<String>> respHeaders = rawApi.getResponse().getHeaders();

        extractHeaders(filterActionRequest, varMap, respHeaders);
    }

    public void extractHeaders(FilterActionRequest filterActionRequest, Map<String, Object> varMap, Map<String, List<String>> headers) {

        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null) {
            return;
        }
        
        String headerString = RedactSampleData.convertHeaders(headers);
        String val = null;

        List<String> querySet = (List<String>) filterActionRequest.getQuerySet();
        String key = querySet.get(0);

        if (filterActionRequest.getConcernedSubProperty() != null && filterActionRequest.getConcernedSubProperty().toLowerCase().equals("key")) {
            if (filterActionRequest.getMatchingKeySet() != null && filterActionRequest.getMatchingKeySet().size() > 0) {
                val = filterActionRequest.getMatchingKeySet().get(0);
            }
        } else if (filterActionRequest.getConcernedSubProperty() != null && filterActionRequest.getConcernedSubProperty().toLowerCase().equals("value")) {
            if (filterActionRequest.getMatchingKeySet() != null && filterActionRequest.getMatchingKeySet().size() > 0) {
                List<String> values = headers.get(filterActionRequest.getMatchingKeySet().get(0));
                if (values != null && values.size() > 0) {
                    val = values.get(0);
                }
            } 
        } else {
            val = headerString;
        }

        if (val != null) {
            if (varMap.containsKey(key) && varMap.get(key) != null) {
                return;
            }
            varMap.put(key, val);
        }
    }

    public DataOperandsFilterResponse applyFiltersOnHeaders(FilterActionRequest filterActionRequest, Map<String, List<String>> headers) {

        List<String> newMatchingKeys = new ArrayList<>();
        List<String> oldMatchingKeys = filterActionRequest.getMatchingKeySet();
        List<String> matchingValueKeySet = new ArrayList<>();
        Boolean result = false;
        Boolean res = false;

        if (filterActionRequest.getConcernedSubProperty() != null && filterActionRequest.getConcernedSubProperty().toLowerCase().equals("key")) {
            for (String key: headers.keySet()) {
                DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(key, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
                res = invokeFilter(dataOperandFilterRequest);
                if (res) {
                    newMatchingKeys.add(key);
                }
                result = result || res;
            }
            return new DataOperandsFilterResponse(result, newMatchingKeys);
        } else if (filterActionRequest.getConcernedSubProperty() != null && filterActionRequest.getConcernedSubProperty().toLowerCase().equals("value")) {
            
            for (String key: headers.keySet()) {
                if (filterActionRequest.getKeyValOperandSeen() && oldMatchingKeys != null && !oldMatchingKeys.contains(key)) {
                    continue;
                }
                for (String val: headers.get(key)) {
                    DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(val, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
                    res = invokeFilter(dataOperandFilterRequest);
                    if (res) {
                        matchingValueKeySet.add(key);
                        break;
                    }
                }
                result = result || res;
            }
            return new DataOperandsFilterResponse(result, matchingValueKeySet);
        } else {
            String headerString = RedactSampleData.convertHeaders(headers);
            DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(headerString, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
            res = invokeFilter(dataOperandFilterRequest);
            return new DataOperandsFilterResponse(res, null);
        }
    }

    public DataOperandsFilterResponse applyFilterOnQueryParams(FilterActionRequest filterActionRequest) {

        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null) {
            return new DataOperandsFilterResponse(false, null);
        }
        String queryParams = rawApi.getRequest().getQueryParams();
        String url = filterActionRequest.getApiInfoKey().getUrl();
        BasicDBObject queryParamObj = RequestTemplate.getQueryJSON(url + "?" + queryParams);

        List<String> matchingKeys = new ArrayList<>();
        List<String> matchingValueKeySet = new ArrayList<>();
        Boolean res = false;

        if (filterActionRequest.getConcernedSubProperty() != null && filterActionRequest.getConcernedSubProperty().toLowerCase().equals("key")) {
            for (String key: queryParamObj.keySet()) {
                DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(key, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
                res = invokeFilter(dataOperandFilterRequest);
                if (res) {
                    matchingKeys.add(key);
                }
            }
            return new DataOperandsFilterResponse(matchingKeys.size() > 0, matchingKeys);
        } else if (filterActionRequest.getConcernedSubProperty() != null && filterActionRequest.getConcernedSubProperty().toLowerCase().equals("value")) {
            matchingKeys = filterActionRequest.getMatchingKeySet();
            for (String key: queryParamObj.keySet()) {
                if (filterActionRequest.getKeyValOperandSeen() && matchingKeys != null && !matchingKeys.contains(key)) {
                    continue;
                }
                DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(queryParamObj.getString(key), filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
                res = invokeFilter(dataOperandFilterRequest);
                if (res) {
                    matchingValueKeySet.add(key);
                    break;
                }
            }
            return new DataOperandsFilterResponse(res, matchingValueKeySet);
        } else {
            DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(queryParams, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
            res = invokeFilter(dataOperandFilterRequest);
            return new DataOperandsFilterResponse(res, null);
        }
    }

    public void extractQueryParams(FilterActionRequest filterActionRequest, Map<String, Object> varMap) {

        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null) {
            return;
        }

        Object val = null;
        List<String> querySet = (List<String>) filterActionRequest.getQuerySet();
        String key = querySet.get(0);

        String queryParams = rawApi.getRequest().getQueryParams();
        String url = filterActionRequest.getApiInfoKey().getUrl();
        BasicDBObject queryParamObj = RequestTemplate.getQueryJSON(url + "?" + queryParams);
        
        if (filterActionRequest.getConcernedSubProperty() != null && filterActionRequest.getConcernedSubProperty().toLowerCase().equals("key")) {
            if (filterActionRequest.getMatchingKeySet().size() > 0) {
                val = filterActionRequest.getMatchingKeySet().get(0);
            }
        } else if (filterActionRequest.getConcernedSubProperty() != null && filterActionRequest.getConcernedSubProperty().toLowerCase().equals("value")) {
            if (filterActionRequest.getMatchingKeySet() != null && filterActionRequest.getMatchingKeySet().size() > 0) {
                val = queryParamObj.get(filterActionRequest.getMatchingKeySet().get(0));
            }
        } else {
            val = queryParams;
        }

        if (val != null) {
            if (varMap.containsKey(key) && varMap.get(key) != null) {
                return;
            }
            varMap.put(key, val);
        }
    }

    public Boolean invokeFilter(DataOperandFilterRequest dataOperandFilterRequest) {

        DataOperandsImpl handler = this.filters.get(dataOperandFilterRequest.getOperand().toLowerCase());
        if (handler == null) {
            return false;
        }

        return handler.isValid(dataOperandFilterRequest);
    }

    public Object resolveQuerySetValues(FilterActionRequest filterActionRequest, Object querySet, Map<String, Object> varMap) {
        Object obj = null;
        List<Object> listVal = new ArrayList<>();
        try {
            listVal = (List) querySet;
            int index = 0;
            for (Object objVal: listVal) {
                if (!(objVal instanceof String)) {
                    continue;
                }
                String val = (String) objVal;
                Boolean matches = Utils.checkIfContainsMatch(val, "\\$\\{[^}]*\\}");
                if (matches) {
                    val = val.substring(2, val.length());
                    val = val.substring(0, val.length() - 1);

                    String[] params = val.split("\\.");
                    String firstParam = params[0];
                    String secondParam = null;
                    if (params.length > 1) {
                        secondParam = params[1];
                    }
                    if (secondParam == null) {
                        obj = VariableResolver.resolveExpression(varMap, val);
                    } else {
                        obj = resolveDynamicValue(filterActionRequest, firstParam, secondParam);
                    }
                    listVal.set(index, obj);
                    index++;
                }
            }
        } catch (Exception e) {
            return null;
        }
        return listVal;
    }

    public void getMatchingKeysForPayload(Object obj, String parentKey, Object querySet, String operand, Set<String> matchingKeys) {
        Boolean res = false;
        if (obj instanceof BasicDBObject) {
            BasicDBObject basicDBObject = (BasicDBObject) obj;

            Set<String> keySet = basicDBObject.keySet();

            for(String key: keySet) {
                if (key == null) {
                    continue;
                }
                Object value = basicDBObject.get(key);
                getMatchingKeysForPayload(value, key, querySet, operand, matchingKeys);
                
            }
        } else if (obj instanceof BasicDBList) {
            for(Object elem: (BasicDBList) obj) {
                getMatchingKeysForPayload(elem, parentKey, querySet, operand, matchingKeys);
            }
        } else {
            DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(parentKey, querySet, operand);
            res = invokeFilter(dataOperandFilterRequest);
            if (res) {
                matchingKeys.add(parentKey);
            }
        }
    }

    public void valueExists(Object obj, String parentKey, Object querySet, String operand, List<String> matchingKeys, Boolean keyOperandSeen, List<String> matchingValueKeySet) {
        Boolean res = false;
        if (obj instanceof BasicDBObject) {
            BasicDBObject basicDBObject = (BasicDBObject) obj;

            Set<String> keySet = basicDBObject.keySet();

            for(String key: keySet) {
                if (key == null) {
                    continue;
                }
                Object value = basicDBObject.get(key);
                valueExists(value, key, querySet, operand, matchingKeys, keyOperandSeen, matchingValueKeySet);
            }
        } else if (obj instanceof BasicDBList) {
            for(Object elem: (BasicDBList) obj) {
                valueExists(elem, parentKey, querySet, operand, matchingKeys, keyOperandSeen, matchingValueKeySet);
            }
        } else {
            if (keyOperandSeen && matchingKeys != null && !matchingKeys.contains(parentKey)) {
                return;
            }
            DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(obj, querySet, operand);
            res = invokeFilter(dataOperandFilterRequest);
            if (res) {
                matchingValueKeySet.add(parentKey);
            }
        }

        return;
    }

    public Object getValue(Object obj, String parentKey, String queryKey) {
        Object val = null;
        if (obj instanceof BasicDBObject) {
            BasicDBObject basicDBObject = (BasicDBObject) obj;

            Set<String> keySet = basicDBObject.keySet();

            for(String key: keySet) {
                if (key == null) {
                    continue;
                }
                Object value = basicDBObject.get(key);
                val = getValue(value, key, queryKey);
                if (val != null) {
                    break;
                }
            }
        } else if (obj instanceof BasicDBList) {
            for(Object elem: (BasicDBList) obj) {
                val = getValue(elem, parentKey, queryKey);
                if (val != null) {
                    break;
                }
            }
        } else {
            if (queryKey.toLowerCase().equals(parentKey.toLowerCase())) {
                return obj;
            } else {
                return null;
            }
        }

        return val;
    }
    
    public Object resolveDynamicValue(FilterActionRequest filterActionRequest, String firstParam, String secondParam) {

        switch (firstParam) {
            case "sample_request_payload":
                return resolveRequestPayload(filterActionRequest, true, secondParam);
            case "sample_response_payload":
                return resolveResponsePayload(filterActionRequest, true, secondParam);
            case "test_request_payload":
                return resolveRequestPayload(filterActionRequest, false, secondParam);
            case "test_response_payload":
                return resolveResponsePayload(filterActionRequest, false, secondParam);
            case "sample_request_headers":
                return resolveRequestHeader(filterActionRequest, true, secondParam);
            case "sample_response_headers":
                return resolveResponseHeader(filterActionRequest, true, secondParam);
            case "test_request_headers":
                return resolveRequestHeader(filterActionRequest, false, secondParam);
            case "test_response_headers":
                return resolveResponseHeader(filterActionRequest, false, secondParam);
            default:
                return null;

        }
    }

    public Object resolveRequestPayload(FilterActionRequest filterActionRequest, Boolean isSample, String key) {

        RawApi rawApi = filterActionRequest.fetchRawApi(isSample);
        if (rawApi == null) {
            return null;
        }

        String reqBody = rawApi.getRequest().getBody();
        
        BasicDBObject reqObj = new BasicDBObject();
        try {
            reqObj =  BasicDBObject.parse(reqBody);
        } catch(Exception e) {
            // add log
        }

        Object val = getValue(reqObj, null, key);

        return val;

    }

    public Object resolveResponsePayload(FilterActionRequest filterActionRequest, Boolean isSample, String key) {

        RawApi rawApi = filterActionRequest.fetchRawApi(isSample);
        if (rawApi == null) {
            return null;
        }

        String reqBody = rawApi.getResponse().getBody();
        BasicDBObject reqObj = new BasicDBObject();
        try {
            reqObj =  BasicDBObject.parse(reqBody);
        } catch(Exception e) {
            // add log
        }

        Object val = getValue(reqObj, null, key);

        return val;

    }

    public Object resolveRequestHeader(FilterActionRequest filterActionRequest, Boolean isSample, String key) {

        RawApi rawApi = filterActionRequest.fetchRawApi(isSample);
        if (rawApi == null) {
            return null;
        }

        Map<String, List<String>> headers = rawApi.getRequest().getHeaders();

        if (headers.containsKey(key)) {
            return headers.get(key).get(0);
        } else {
            return null;
        }
    }

    public Object resolveResponseHeader(FilterActionRequest filterActionRequest, Boolean isSample, String key) {

        RawApi rawApi = filterActionRequest.fetchRawApi(isSample);
        if (rawApi == null) {
            return null;
        }

        Map<String, List<String>> headers = rawApi.getResponse().getHeaders();

        if (headers.containsKey(key)) {
            return headers.get(key).get(0);
        } else {
            return null;
        }
    }

}
