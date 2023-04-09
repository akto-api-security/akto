package com.akto.dao.test_editor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.access.method.P;

import com.akto.dao.test_editor.data_operands_impl.ContainsAllFilter;
import com.akto.dao.test_editor.data_operands_impl.ContainsEitherFilter;
import com.akto.dao.test_editor.data_operands_impl.DataOperandsImpl;
import com.akto.dao.test_editor.data_operands_impl.EqFilter;
import com.akto.dao.test_editor.data_operands_impl.GreaterThanEqFilter;
import com.akto.dao.test_editor.data_operands_impl.GreaterThanFilter;
import com.akto.dao.test_editor.data_operands_impl.LesserThanEqFilter;
import com.akto.dao.test_editor.data_operands_impl.LesserThanFilter;
import com.akto.dao.test_editor.data_operands_impl.NeqFilter;
import com.akto.dao.test_editor.data_operands_impl.RegexFilter;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.DataOperandFilterRequest;
import com.akto.dto.test_editor.DataOperandsFilterResponse;
import com.akto.dto.test_editor.FilterActionRequest;
import com.akto.dto.test_editor.Pred.V;
import com.akto.dto.type.RequestTemplate;
import com.akto.util.JSONUtils;
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

    public final Map<String, DataOperandsImpl> dynamicValueMapper = new HashMap<String, DataOperandsImpl>() {{
        put("sample_request_payload", new ContainsAllFilter());
        put("sample_response_payload", new ContainsEitherFilter());
        put("sample_request_headers", new RegexFilter());
        put("sample_response_headers", new EqFilter());
        put("test_request_payload", new ContainsAllFilter());
        put("test_response_payload", new ContainsEitherFilter());
        put("test_request_headers", new RegexFilter());
        put("test_response_headers", new EqFilter());
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
            case "queryparams":
                return applyFilterOnQueryParams(filterActionRequest);
            case "response_code":
                return applyFilterOnResponseCode(filterActionRequest);
            default:
                return new DataOperandsFilterResponse(false, null);

        }
    }

    public DataOperandsFilterResponse applyFilterOnUrl(FilterActionRequest filterActionRequest) {

        String url = filterActionRequest.getApiInfoKey().getUrl();

        DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(url, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
        Boolean res = invokeFilter(dataOperandFilterRequest);
        return new DataOperandsFilterResponse(res, null);
    }

    public DataOperandsFilterResponse applyFilterOnMethod(FilterActionRequest filterActionRequest) {

        String method = filterActionRequest.getApiInfoKey().getMethod().toString();
        DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(method, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
        Boolean res = invokeFilter(dataOperandFilterRequest);
        return new DataOperandsFilterResponse(res, null);
    }

    public DataOperandsFilterResponse applyFilterOnApiCollectionId(FilterActionRequest filterActionRequest) {

        String apiCollectionId = Integer.toString(filterActionRequest.getApiInfoKey().getApiCollectionId());
        DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(apiCollectionId, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
        Boolean res = invokeFilter(dataOperandFilterRequest);
        return new DataOperandsFilterResponse(res, null);
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

    public DataOperandsFilterResponse applyFilterOnRequestPayload(FilterActionRequest filterActionRequest) {

        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null) {
            return new DataOperandsFilterResponse(false, null);
        }
        String reqBody = rawApi.getRequest().getBody();
        BasicDBObject reqObj =  BasicDBObject.parse(reqBody);

        Set<String> matchingKeySet = new HashSet<>();
        List<String> matchingKeys = new ArrayList<>();
        Boolean res = false;

        if (filterActionRequest.getConcernedSubProperty() != null && filterActionRequest.getConcernedSubProperty().toLowerCase().equals("key")) {

            getMatchingKeysForPayload(reqObj, null, filterActionRequest.getQuerySet(), filterActionRequest.getOperand(), matchingKeySet);
            for (String s: matchingKeySet) {
                matchingKeys.add(s);
            }
            return new DataOperandsFilterResponse(matchingKeys.size() > 0, matchingKeys);

        } else if (filterActionRequest.getConcernedSubProperty() != null && filterActionRequest.getConcernedSubProperty().toLowerCase().equals("value")) {
            res = valueExists(reqObj, null, filterActionRequest.getQuerySet(), reqBody, matchingKeys);
            return new DataOperandsFilterResponse(res, null);
        }

        return new DataOperandsFilterResponse(false, null);
    }

    public DataOperandsFilterResponse applyFilterOnResponsePayload(FilterActionRequest filterActionRequest) {

        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null) {
            return new DataOperandsFilterResponse(false, null);
        }
        String respBody = rawApi.getResponse().getBody();
        BasicDBObject basicDBObject =  BasicDBObject.parse(respBody);
        BasicDBObject respObj = JSONUtils.flattenWithDots(basicDBObject);
        Object data = respObj;
        DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(data, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
        Boolean res = invokeFilter(dataOperandFilterRequest);
        return new DataOperandsFilterResponse(res, null);
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

    public DataOperandsFilterResponse applyFiltersOnHeaders(FilterActionRequest filterActionRequest, Map<String, List<String>> headers) {

        List<String> newMatchingKeys = new ArrayList<>();
        List<String> oldMatchingKeys = filterActionRequest.getMatchingKeySet();
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
                if (oldMatchingKeys != null && !oldMatchingKeys.contains(key)) {
                    continue;
                }
                for (String val: headers.get(key)) {
                    DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(val, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
                    res = invokeFilter(dataOperandFilterRequest);
                    if (res) {
                        break;
                    }
                }
                result = result || res;
            }
            return new DataOperandsFilterResponse(result, null);
        }
        return new DataOperandsFilterResponse(false, null);
    }

    public DataOperandsFilterResponse applyFilterOnQueryParams(FilterActionRequest filterActionRequest) {

        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null) {
            return new DataOperandsFilterResponse(false, null);
        }
        String queryParams = rawApi.getRequest().getQueryParams();
        String url = filterActionRequest.getApiInfoKey().getUrl();
        BasicDBObject queryParamObj = RequestTemplate.getQueryJSON(url + "?" + queryParams);

        ArrayList<String> matchingKeys = new ArrayList<>();
        Boolean res = false;

        for (String key: queryParamObj.keySet()) {

            if (filterActionRequest.getConcernedSubProperty() != null && filterActionRequest.getConcernedSubProperty().toLowerCase().equals("key")) {
                DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(key, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
                res = invokeFilter(dataOperandFilterRequest);
                if (res) {
                    matchingKeys.add(key);
                }
                return new DataOperandsFilterResponse(res, matchingKeys);
            } else if (filterActionRequest.getConcernedSubProperty() != null && filterActionRequest.getConcernedSubProperty().toLowerCase().equals("value")) {
                if (matchingKeys != null && !matchingKeys.contains(key)) {
                    continue;
                }
                DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(queryParamObj.getString(key), filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
                res = invokeFilter(dataOperandFilterRequest);
                if (res) {
                    break;
                }
                return new DataOperandsFilterResponse(res, null);
            }
        }

        return new DataOperandsFilterResponse(false, null);
    }

    public Boolean invokeFilter(DataOperandFilterRequest dataOperandFilterRequest) {

        DataOperandsImpl handler = this.filters.get(dataOperandFilterRequest.getOperand().toLowerCase());
        if (handler == null) {
            return false;
        }

        return handler.isValid(dataOperandFilterRequest);
    }

    public Object resolveQuerySetValues(FilterActionRequest filterActionRequest, Object querySet) {
        Object obj = null;
        try {
            List<Object> listVal = (List) querySet;
            for (Object val: listVal) {
                if (!(val instanceof String)) {
                    continue;
                }
                Boolean matches = Utils.checkIfContainsMatch(val, "\\${[^}]*}");
                if (matches) {
                    val = val.substring(2, val.length());
                    val = val.substring(2, val.length() - 1);
                    String[] params = val.split(".");
                    String firstParam = params[0];
                    String secondParam = null;
                    if (params.length > 1) {
                        secondParam = params[1];
                    }
                    resolveDynamicValue(filterActionRequest, firstParam, secondParam);
                }
            }
        } catch (Exception e) {
            return obj;
        }
        return obj;
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

    public Boolean valueExists(Object obj, String parentKey, Object querySet, String operand, List<String> matchingKeys) {
        Boolean res = false;
        if (obj instanceof BasicDBObject) {
            BasicDBObject basicDBObject = (BasicDBObject) obj;

            Set<String> keySet = basicDBObject.keySet();

            for(String key: keySet) {
                if (key == null) {
                    continue;
                }
                Object value = basicDBObject.get(key);
                res = valueExists(value, key, querySet, operand, matchingKeys);
            }
        } else if (obj instanceof BasicDBList) {
            for(Object elem: (BasicDBList) obj) {
                res = valueExists(elem, parentKey, querySet, operand, matchingKeys);
                if (res) {
                    break;
                }
            }
        } else {
            if (matchingKeys != null && !matchingKeys.contains(parentKey)) {
                return false;
            }
            DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(obj, querySet, operand);
            res = invokeFilter(dataOperandFilterRequest);
        }

        return res;
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

        String concernedProperty = filterActionRequest.getConcernedProperty();
        switch (concernedProperty.toLowerCase()) {
            case "sample_request_payload":
                return resolveRequestPayload(filterActionRequest, true, secondParam);
            case "sample_response_payload":
                return resolveResponsePayload(filterActionRequest, true, secondParam);
            case "test_request_payload":
                return resolveResponsePayload(filterActionRequest, false, secondParam);
            case "test_response_payload":
                return resolveResponsePayload(filterActionRequest, false, secondParam);
            case "sample_request_headers":
                return resolveRequestHeader(filterActionRequest, true, secondParam);
            case "sample_response_headers":
                return resolveRequestHeader(filterActionRequest, true, secondParam);
            case "test_request_headers":
                return resolveResponseHeader(filterActionRequest, false, secondParam);
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
        BasicDBObject reqObj =  BasicDBObject.parse(reqBody);

        Object val = getValue(reqObj, null, key);

        return val;

    }

    public Object resolveResponsePayload(FilterActionRequest filterActionRequest, Boolean isSample, String key) {

        RawApi rawApi = filterActionRequest.fetchRawApi(isSample);
        if (rawApi == null) {
            return null;
        }

        String reqBody = rawApi.getResponse().getBody();
        BasicDBObject reqObj =  BasicDBObject.parse(reqBody);

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
            return headers.get(key);
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
            return headers.get(key);
        } else {
            return null;
        }
    }

}
