package com.akto.test_editor.filter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.akto.dto.OriginalHttpResponse;
import org.bson.conversions.Bson;

import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.test_editor.TestEditorEnums.BodyOperator;
import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.DataOperandFilterRequest;
import com.akto.dto.test_editor.DataOperandsFilterResponse;
import com.akto.dto.test_editor.FilterActionRequest;
import com.akto.dto.type.APICatalog;
import com.akto.dto.type.RequestTemplate;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLTemplate;
import com.akto.rules.TestPlugin;
import com.akto.runtime.APICatalogSync;
import com.akto.test_editor.Utils;
import com.akto.test_editor.execution.VariableResolver;
import com.akto.test_editor.filter.data_operands_impl.ContainsAllFilter;
import com.akto.test_editor.filter.data_operands_impl.ContainsEitherFilter;
import com.akto.test_editor.filter.data_operands_impl.ContainsJwt;
import com.akto.test_editor.filter.data_operands_impl.DataOperandsImpl;
import com.akto.test_editor.filter.data_operands_impl.EqFilter;
import com.akto.test_editor.filter.data_operands_impl.GreaterThanEqFilter;
import com.akto.test_editor.filter.data_operands_impl.GreaterThanFilter;
import com.akto.test_editor.filter.data_operands_impl.LesserThanEqFilter;
import com.akto.test_editor.filter.data_operands_impl.LesserThanFilter;
import com.akto.test_editor.filter.data_operands_impl.NeqFilter;
import com.akto.test_editor.filter.data_operands_impl.NotContainsEitherFilter;
import com.akto.test_editor.filter.data_operands_impl.NotContainsFilter;
import com.akto.test_editor.filter.data_operands_impl.RegexFilter;
import com.akto.util.JSONUtils;
import com.akto.utils.RedactSampleData;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

public final class FilterAction {
    
    public final Map<String, DataOperandsImpl> filters = new HashMap<String, DataOperandsImpl>() {{
        put("contains_all", new ContainsAllFilter());
        put("contains_either", new ContainsEitherFilter());
        put("not_contains", new NotContainsFilter());
        put("regex", new RegexFilter());
        put("eq", new EqFilter());
        put("neq", new NeqFilter());
        put("gt", new GreaterThanFilter());
        put("gte", new GreaterThanEqFilter());
        put("lt", new LesserThanFilter());
        put("lte", new LesserThanEqFilter());
        put("not_contains_either", new NotContainsEitherFilter());
        put("contains_jwt", new ContainsJwt());
    }};

    public FilterAction() { }

    public DataOperandsFilterResponse evaluateContext(FilterActionRequest filterActionRequest) {

        String contextProperty = filterActionRequest.getContextProperty();
        switch (contextProperty.toLowerCase()) {
            case "private_variable_context":
                return evaluatePrivateVariables(filterActionRequest);
            case "param_context":
                return evaluateParamContext(filterActionRequest);
            case "endpoint_in_traffic_context":
                return endpointInTraffic(filterActionRequest);
            default:
                return new DataOperandsFilterResponse(false, null, null);
        }
    }

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
                return new DataOperandsFilterResponse(false, null, null);

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

    public boolean extractContextVar(FilterActionRequest filterActionRequest, Map<String, Object> varMap) {

        if (filterActionRequest.getContextEntities() == null || filterActionRequest.getContextEntities().size() == 0) {
            return false;
        }
        Object val = filterActionRequest.getContextEntities();

        List<String> querySet = (List<String>) filterActionRequest.getQuerySet();
        varMap.put("context_" + querySet.get(0).toString(), val);
        return true;
    }

    public DataOperandsFilterResponse applyFilterOnUrl(FilterActionRequest filterActionRequest) {

        String url = filterActionRequest.getApiInfoKey().getUrl();

        DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(url, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
        Boolean res = invokeFilter(dataOperandFilterRequest);
        return new DataOperandsFilterResponse(res, null, null);
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
        return new DataOperandsFilterResponse(res, null, null);
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
        return new DataOperandsFilterResponse(res, null, null);
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
            return new DataOperandsFilterResponse(false, null, null);
        }
        if (rawApi.getResponse() == null) {
            return new DataOperandsFilterResponse(false, null, null);
        }
        int respCode = rawApi.getResponse().getStatusCode();
        DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(respCode, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
        Boolean res = invokeFilter(dataOperandFilterRequest);
        return new DataOperandsFilterResponse(res, null, null);
    }

    public void extractResponseCode(FilterActionRequest filterActionRequest, Map<String, Object> varMap) {
        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null) {
            return;
        }
        if (rawApi.getResponse() == null) {
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
            return new DataOperandsFilterResponse(false, null, null);
        }
        OriginalHttpRequest request = rawApi.getRequest();
        if (request == null) {
            return new DataOperandsFilterResponse(false, null, null);
        }

        String reqBody = rawApi.getRequest().getBody();
        return applyFilterOnPayload(filterActionRequest, reqBody);
    }

    public DataOperandsFilterResponse applyFilterOnResponsePayload(FilterActionRequest filterActionRequest) {

        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null) {
            return new DataOperandsFilterResponse(false, null, null);
        }
        OriginalHttpResponse response = rawApi.getResponse();
        if (response == null) {
            return new DataOperandsFilterResponse(false, null, null);
        }

        String reqBody = response.getBody();
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
            Boolean filterResp = matchingKeys.size() > 0;
            if (filterActionRequest.getOperand().equalsIgnoreCase("not_contains") || filterActionRequest.getOperand().equalsIgnoreCase("not_contains_either")) {
                int keyCount = getKeyCount(payloadObj, null);
                filterResp = matchingKeySet.size() == keyCount;
            }
            return new DataOperandsFilterResponse(filterResp, matchingKeys, null);

        } else if (filterActionRequest.getConcernedSubProperty() != null && filterActionRequest.getConcernedSubProperty().toLowerCase().equals("value")) {
            matchingKeys = filterActionRequest.getMatchingKeySet();
            valueExists(payloadObj, null, filterActionRequest.getQuerySet(), filterActionRequest.getOperand(), matchingKeys, filterActionRequest.getKeyValOperandSeen(), matchingValueKeySet);
            Boolean filterResp = matchingValueKeySet.size() > 0;
            if (filterActionRequest.getOperand().equalsIgnoreCase("not_contains") || filterActionRequest.getOperand().equalsIgnoreCase("not_contains_either")) {
                int keyCount = getKeyCount(payloadObj, null);
                filterResp = matchingKeySet.size() == keyCount;
            }
            return new DataOperandsFilterResponse(filterResp, matchingValueKeySet, null);
        } else if (filterActionRequest.getConcernedSubProperty() == null) {
            Object val = payload;

            if (filterActionRequest.getBodyOperand() != null && filterActionRequest.getBodyOperand().equalsIgnoreCase(BodyOperator.LENGTH.toString())) {
                val = payload.trim().length() - 2; // todo:
            } else if (filterActionRequest.getBodyOperand() != null && filterActionRequest.getBodyOperand().equalsIgnoreCase(BodyOperator.PERCENTAGE_MATCH.toString())) {
                RawApi sampleRawApi = filterActionRequest.getRawApi();
                if (sampleRawApi == null) {
                    return new DataOperandsFilterResponse(false, null, null);
                }
                double percentageMatch = TestPlugin.compareWithOriginalResponse(payload, sampleRawApi.getResponse().getBody(), new HashMap<>());
                val = (int) percentageMatch;
            }
            
            DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(val, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
            res = invokeFilter(dataOperandFilterRequest);
            return new DataOperandsFilterResponse(res, null, null);
        } else if (filterActionRequest.getConcernedSubProperty() == null) {
            DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(payload, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
            res = invokeFilter(dataOperandFilterRequest);
            return new DataOperandsFilterResponse(res, null, null);
        }

        return new DataOperandsFilterResponse(false, null, null);
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
            return new DataOperandsFilterResponse(false, null, null);
        }
        
        Map<String, List<String>> reqHeaders = rawApi.getRequest().getHeaders();
        return applyFiltersOnHeaders(filterActionRequest, reqHeaders);
    }

    public DataOperandsFilterResponse applyFilterOnResponseHeaders(FilterActionRequest filterActionRequest) {

        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null) {
            return new DataOperandsFilterResponse(false, null, null);
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
            if (filterActionRequest.getOperand().equalsIgnoreCase("not_contains") || filterActionRequest.getOperand().equalsIgnoreCase("not_contains_either")) {
                result = newMatchingKeys.size() == headers.size();
            }
            return new DataOperandsFilterResponse(result, newMatchingKeys, null);
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
            if (filterActionRequest.getOperand().equalsIgnoreCase("not_contains") || filterActionRequest.getOperand().equalsIgnoreCase("not_contains_either")) {
                result = matchingValueKeySet.size() == headers.size();
            }
            return new DataOperandsFilterResponse(result, matchingValueKeySet, null);
        } else {
            String headerString = RedactSampleData.convertHeaders(headers);
            DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(headerString, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
            res = invokeFilter(dataOperandFilterRequest);
            return new DataOperandsFilterResponse(res, null, null);
        }
    }

    public DataOperandsFilterResponse applyFilterOnQueryParams(FilterActionRequest filterActionRequest) {

        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null) {
            return new DataOperandsFilterResponse(false, null, null);
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
            return new DataOperandsFilterResponse(matchingKeys.size() > 0, matchingKeys, null);
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
            return new DataOperandsFilterResponse(res, matchingValueKeySet, null);
        } else {
            DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(queryParams, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
            res = invokeFilter(dataOperandFilterRequest);
            return new DataOperandsFilterResponse(res, null, null);
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

                Object contextVal = VariableResolver.resolveContextVariable(varMap, objVal.toString());
                if (contextVal instanceof  List) {
                    List<String> contextList = (List<String>) contextVal;
                    if (contextList != null && contextList.size() > 0) {
                        listVal.set(index, contextList.get(0));
                        index++;
                        continue;
                    }
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

    public int getKeyCount(Object obj, String parentKey) {
        int count = 0;
        if (obj instanceof BasicDBObject) {
            BasicDBObject basicDBObject = (BasicDBObject) obj;

            Set<String> keySet = basicDBObject.keySet();

            for(String key: keySet) {
                if (key == null) {
                    continue;
                }
                Object value = basicDBObject.get(key);
                count += getKeyCount(value, key);
            }
        } else if (obj instanceof BasicDBList) {
            for(Object elem: (BasicDBList) obj) {
                count += getKeyCount(elem, parentKey);
            }
        } else {
            count++;
        }

        return count;

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

    public DataOperandsFilterResponse evaluatePrivateVariables(FilterActionRequest filterActionRequest) {

        OriginalHttpRequest request = filterActionRequest.getRawApi().getRequest();
        BasicDBObject resp = getPrivateResourceCount(request, filterActionRequest.getApiInfoKey());

        int privateCount = (int) resp.get("privateCount");
        List<BasicDBObject> privateValues = (List<BasicDBObject>) resp.get("values");
        return new DataOperandsFilterResponse(privateCount > 0, null, privateValues);

    }

    public DataOperandsFilterResponse evaluateParamContext(FilterActionRequest filterActionRequest) {

        ApiInfo.ApiInfoKey apiInfoKey = filterActionRequest.getApiInfoKey();

        List<String> querySet = (List<String>) filterActionRequest.getQuerySet();
        if (querySet.get(0) == null) {
            return new DataOperandsFilterResponse(false, null, null);
        }
        String param = querySet.get(0).toString().trim();

        Bson filter = Filters.and(
            Filters.eq("apiCollectionId", apiInfoKey.getApiCollectionId()),
            Filters.regex("param", param),
            Filters.eq("isHeader", false)
        );

        List<SingleTypeInfo> singleTypeInfos = SingleTypeInfoDao.instance.findAll(filter, 0, 500, null);

        if (singleTypeInfos.isEmpty()) {
            return new DataOperandsFilterResponse(false, null, null);
        }

        List<BasicDBObject> paramValues = new ArrayList<>();

        for (SingleTypeInfo singleTypeInfo: singleTypeInfos) {
            Set<String> valSet  = singleTypeInfo.getValues() != null ? singleTypeInfo.getValues().getElements() : new HashSet<>();
            if (valSet == null) continue;

            String key = SingleTypeInfo.findLastKeyFromParam(singleTypeInfo.getParam());
            if (key == null || !Utils.checkIfContainsMatch(key, param)) {
                continue;
            }

            for (String val: valSet) {
                boolean exists = paramExists(filterActionRequest.getRawApi(), key, val);
                if (!exists && val != null && val.length() > 0) {
                    BasicDBObject obj = new BasicDBObject();
                    obj.put("key", key);
                    obj.put("value", val);
                    paramValues.add(obj);
                    break;
                }
            }
            if (paramValues.size() > 0) {
                break;
            }
        }

        return new DataOperandsFilterResponse(paramValues.size() > 0, null, paramValues);

    }

    public DataOperandsFilterResponse endpointInTraffic(FilterActionRequest filterActionRequest) {
        ApiInfo.ApiInfoKey apiInfoKey = filterActionRequest.getApiInfoKey();

        List<Boolean> querySet = (List) filterActionRequest.getQuerySet();
        Boolean shouldBePresent = (Boolean) querySet.get(0);

        Bson filters = Filters.and(
            Filters.eq("apiCollectionId", apiInfoKey.getApiCollectionId()),
            Filters.regex("url", filterActionRequest.getTestRunRawApi().getRequest().getUrl()),
            Filters.eq("method", apiInfoKey.getMethod())
        );
        SingleTypeInfo singleTypeInfo = SingleTypeInfoDao.instance.findOne(filters);
        boolean res = false;
        if (shouldBePresent) {
            res = singleTypeInfo != null;
        } else {
            res = singleTypeInfo == null;
        }
        return new DataOperandsFilterResponse(res, null, null);
    }

    public BasicDBObject getPrivateResourceCount(OriginalHttpRequest originalHttpRequest, ApiInfo.ApiInfoKey apiInfoKey) {
        String urlWithParams = originalHttpRequest.getFullUrlWithParams();
        String url = apiInfoKey.url;
        URLMethods.Method method = apiInfoKey.getMethod();

        // check private resource in
        // 1. url
        BasicDBObject resp = new BasicDBObject();
        int privateCnt = 0;
        List<BasicDBObject> privateValues = new ArrayList<>();
        if (APICatalog.isTemplateUrl(url)) {
            URLTemplate urlTemplate = APICatalogSync.createUrlTemplate(url, method);
            String[] tokens = urlTemplate.getTokens();
            for (int i = 0;i < tokens.length; i++) {
                if (tokens[i] == null) {
                    SingleTypeInfo singleTypeInfo = querySti(i+"", true,apiInfoKey, false, -1);
                    BasicDBObject obj = new BasicDBObject();
                    if (singleTypeInfo != null && singleTypeInfo.getIsPrivate()) {
                        privateCnt++;
                    }
                    if (singleTypeInfo == null || !singleTypeInfo.getIsPrivate() || singleTypeInfo.getValues() == null || singleTypeInfo.getValues().getElements().size() == 0) {
                        continue;
                    }
                    Set<String> valSet = singleTypeInfo.getValues().getElements();
                    String val = valSet.iterator().next();
                    obj.put("key", i+"");
                    obj.put("value", val);
                    if (privateValues.size() < 5) {
                        privateValues.add(obj);
                    }
                }
            }
        }

        // 2. payload
        BasicDBObject payload = RequestTemplate.parseRequestPayload(originalHttpRequest.getJsonRequestBody(), urlWithParams);
        Map<String, Set<Object>> flattened = JSONUtils.flatten(payload);
        for (String param: flattened.keySet()) {
            SingleTypeInfo singleTypeInfo = querySti(param,false,apiInfoKey, false, -1);
            BasicDBObject obj = new BasicDBObject();
            if (singleTypeInfo != null && singleTypeInfo.getIsPrivate()) {
                privateCnt++;
            }
            if (singleTypeInfo == null || !singleTypeInfo.getIsPrivate() || singleTypeInfo.getValues() == null || singleTypeInfo.getValues().getElements().size() == 0) {
                continue;
            }
            Set<String> valSet = singleTypeInfo.getValues().getElements();
            String val = valSet.iterator().next();
            String key = SingleTypeInfo.findLastKeyFromParam(param);
            obj.put("key", key);
            obj.put("value", val);
            if (privateValues.size() < 5) {
                privateValues.add(obj);
            }
        }

        resp.put("privateCount", privateCnt);
        resp.put("values", privateValues);

        return resp;

    }

    public boolean paramExists(RawApi rawApi, String param, String val) {

        Map<String, List<String>> headers = rawApi.getRequest().getHeaders();

        if (headers.containsKey(param)) {
            List<String> headerVal = headers.get(param);
            if (headerVal.contains(val)) {
                return true;
            }
        }

        String queryParams = rawApi.getRequest().getQueryParams();
        String url = rawApi.getRequest().getUrl();
        BasicDBObject queryParamObj = RequestTemplate.getQueryJSON(url + "?" + queryParams);

        if (queryParamObj.containsKey(param)) {
            Object paramVal = queryParamObj.get(param);
            if (val.equalsIgnoreCase(paramVal.toString())) {
                return true;
            }
        }

        String payload = rawApi.getRequest().getJsonRequestBody();
        BasicDBObject reqObj = new BasicDBObject();
        try {
            reqObj =  BasicDBObject.parse(payload);
        } catch(Exception e) {
            // add log
        }

        Object fetchedVal = getValue(reqObj, null, param);

        if (fetchedVal != null) {
            if (val.equalsIgnoreCase(fetchedVal.toString())) {
                return true;
            }
        }

        // if (reqObj.containsKey(param)) {
        //     Object paramVal = reqObj.get(param);
        //     if (val.equalsIgnoreCase(paramVal.toString())) {
        //         return true;
        //     }
        // }

        String responsePayload = rawApi.getResponse().getJsonResponseBody();
        BasicDBObject respObj = new BasicDBObject();
        try {
            respObj =  BasicDBObject.parse(responsePayload);
        } catch(Exception e) {
            // add log
        }

        fetchedVal = getValue(respObj, null, param);

        if (fetchedVal != null) {
            if (val.equalsIgnoreCase(fetchedVal.toString())) {
                return true;
            }
        }

        // if (respObj.containsKey(param)) {
        //     Object paramVal = respObj.get(param);
        //     if (val.equalsIgnoreCase(paramVal.toString())) {
        //         return true;
        //     }
        // }

        return false;

    }

    public static SingleTypeInfo querySti(String param, boolean isUrlParam, ApiInfo.ApiInfoKey apiInfoKey, boolean isHeader, int responseCode) {

        Bson urlParamFilters;
        if (!isUrlParam) {
            urlParamFilters = Filters.or(
                Filters.and(
                    Filters.exists("isUrlParam"),
                    Filters.eq("isUrlParam", isUrlParam)
                ),
                Filters.exists("isUrlParam", false)
            );

        } else {
            urlParamFilters = Filters.eq("isUrlParam", isUrlParam);
        }

        Bson filter = Filters.and(
            Filters.eq("apiCollectionId", apiInfoKey.getApiCollectionId()),
            Filters.eq("url", apiInfoKey.url),
            Filters.eq("method", apiInfoKey.method.name()),
            Filters.eq("responseCode", responseCode),
            Filters.eq("isHeader", isHeader),
            Filters.regex("param", param),
            urlParamFilters
        );
        
        SingleTypeInfo singleTypeInfo = SingleTypeInfoDao.instance.findOne(filter);

        if (singleTypeInfo == null) return null;

        return singleTypeInfo;
    }

}
