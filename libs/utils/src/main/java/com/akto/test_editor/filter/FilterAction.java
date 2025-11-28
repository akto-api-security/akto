package com.akto.test_editor.filter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.testing.AccessMatrixUrlToRole;
import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dao.test_editor.TestEditorEnums.BodyOperator;
import com.akto.dao.test_editor.TestEditorEnums.CollectionOperands;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.RawApi;
import com.akto.dto.ApiInfo.ApiAccessType;
import com.akto.dto.test_editor.DataOperandFilterRequest;
import com.akto.dto.test_editor.DataOperandsFilterResponse;
import com.akto.dto.test_editor.FilterActionRequest;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.APICatalog;
import com.akto.dto.type.RequestTemplate;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLTemplate;
import com.akto.test_editor.Utils;
import com.akto.test_editor.execution.VariableResolver;
import com.akto.test_editor.filter.data_operands_impl.*;
import com.akto.util.JSONUtils;
import com.alibaba.fastjson2.JSONObject;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import static com.akto.runtime.utils.Utils.parseCookie;
import static com.akto.dto.RawApi.convertHeaders;
import static com.akto.runtime.RuntimeUtil.createUrlTemplate;
import static com.akto.testing.Utils.compareWithOriginalResponse;

import static com.akto.runtime.parser.SampleParser.parseSampleMessage;

public final class FilterAction {
    
    public final Map<String, DataOperandsImpl> filters = new HashMap<String, DataOperandsImpl>() {{
        put("contains_all", new ContainsAllFilter());
        put("contains_either", new ContainsEitherFilter());
        put("not_contains", new NotContainsFilter());
        put("regex", new RegexFilter());
        put("eq", new EqFilter());
        put("eq_obj", new EqFilterObj());
        put("neq_obj", new NeqFilterObj());
        put("neq", new NeqFilter());
        put("gt", new GreaterThanFilter());
        put("gte", new GreaterThanEqFilter());
        put("lt", new LesserThanFilter());
        put("lte", new LesserThanEqFilter());
        put("not_contains_either", new NotContainsEitherFilter());
        put("contains_jwt", new ContainsJwt());
        put("cookie_expire_filter", new CookieExpireFilter());
        put("datatype", new DatatypeFilter());
        put("ssrf_url_hit", new SsrfUrlHitFilter());
        put("belongs_to_collections", new ApiCollectionFilter());
        put("magic_validate", new MagicValidateFilter());
        put("not_magic_validate", new NotMagicValidateFilter());
    }};
    private static final DataActor dataActor = DataActorFactory.fetchInstance();

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
            case "include_roles_access":
                return evaluateRolesAccessContext(filterActionRequest, true);
            case "exclude_roles_access":
                return evaluateRolesAccessContext(filterActionRequest, false);
            case "api_access_type":
                return applyFilterOnAccessType(filterActionRequest);                
            default:
                return new DataOperandsFilterResponse(false, null, null, null);
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
            case "test_type":
                return applyFilterOnTestType(filterActionRequest);
            default:
                return new DataOperandsFilterResponse(false, null, null, null);

        }
    }

    public void extract(FilterActionRequest filterActionRequest, Map<String, Object> varMap, boolean extractMultiple) {

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
                extractReqPayload(filterActionRequest, varMap, extractMultiple);
                return;
            case "response_payload":
                extractRespPayload(filterActionRequest, varMap, extractMultiple);
                return;
            case "request_headers":
                extractRequestHeaders(filterActionRequest, varMap, extractMultiple);
                return;
            case "response_headers":
                extractResponseHeaders(filterActionRequest, varMap, extractMultiple);
                return;
            case "query_param":
                extractQueryParams(filterActionRequest, varMap, extractMultiple);
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
        return new DataOperandsFilterResponse(res, null, null, null);
    }

    public void extractUrl(FilterActionRequest filterActionRequest, Map<String, Object> varMap) {
        String url = filterActionRequest.getRawApi().getRequest().getUrl();
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
        return new DataOperandsFilterResponse(res, null, null, null);
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
        return new DataOperandsFilterResponse(res, null, null, null);
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
            return new DataOperandsFilterResponse(false, null, null, null);
        }
        if (rawApi.getResponse() == null) {
            return new DataOperandsFilterResponse(false, null, null, null);
        }
        int respCode = rawApi.getResponse().getStatusCode();
        DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(respCode, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
        Boolean res = invokeFilter(dataOperandFilterRequest);
        return new DataOperandsFilterResponse(res, null, null, null);
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

    // apply filter on test types
    public DataOperandsFilterResponse applyFilterOnTestType(FilterActionRequest filterActionRequest) {
        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null || rawApi.getRequest() == null) {
            return new DataOperandsFilterResponse(false, null, null, null);
        }
        int apiCollectionId = filterActionRequest.getApiInfoKey().getApiCollectionId();
        ApiCollection apiCollection = dataActor.fetchApiCollectionMeta(apiCollectionId);
        if (apiCollection == null) {
            return new DataOperandsFilterResponse(false, null, null, null, "API collection not found");
        }
        if (apiCollection.isGenAICollection() ||
                apiCollection.isMcpCollection() ||
                apiCollection.isGuardRailCollection()) {
            return new DataOperandsFilterResponse(true, null, null, null);
        }
        return new DataOperandsFilterResponse(false, null, null, null, "The request is not an Agentic request");
    }

    public DataOperandsFilterResponse applyFilterOnRequestPayload(FilterActionRequest filterActionRequest) {

        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null) {
            return new DataOperandsFilterResponse(false, null, null, null);
        }
        OriginalHttpRequest request = rawApi.getRequest();
        if (request == null) {
            return new DataOperandsFilterResponse(false, null, null, null);
        }

        String reqBody = rawApi.getRequest().getBody();
        return applyFilterOnPayload(filterActionRequest, reqBody);
    }

    public DataOperandsFilterResponse applyFilterOnResponsePayload(FilterActionRequest filterActionRequest) {

        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null) {
            return new DataOperandsFilterResponse(false, null, null, null);
        }
        OriginalHttpResponse response = rawApi.getResponse();
        if (response == null) {
            return new DataOperandsFilterResponse(false, null, null, null);
        }

        String reqBody = response.getBody();
        return applyFilterOnPayload(filterActionRequest, reqBody);
    }

    public DataOperandsFilterResponse applyFilterOnPayload(FilterActionRequest filterActionRequest, String payload) {

        String origPayload = payload;
        BasicDBObject payloadObj = new BasicDBObject();
        try {
            payload = Utils.jsonifyIfArray(payload);
            payloadObj =  BasicDBObject.parse(payload);
        } catch(Exception e) {
            // add log
        }

        Set<String> matchingKeySet = new HashSet<>();
        List<String> matchingKeys = new ArrayList<>();
        List<String> matchingValueKeySet = new ArrayList<>();
        Boolean res = false;

        boolean allShouldSatisfy = false;
        boolean doAllSatisfy = true;
        if (filterActionRequest.getCollectionProperty() != null && filterActionRequest.getCollectionProperty().equalsIgnoreCase(CollectionOperands.FOR_ALL.toString())) {
            allShouldSatisfy = true;
        }

        if (filterActionRequest.getConcernedSubProperty() != null && filterActionRequest.getConcernedSubProperty().toLowerCase().equals("key")) {

            // if concerned prop for_all, all keys should match else empty list
            doAllSatisfy = getMatchingKeysForPayload(payloadObj, null, filterActionRequest.getQuerySet(), filterActionRequest.getOperand(), matchingKeySet, doAllSatisfy);
            for (String s: matchingKeySet) {
                matchingKeys.add(s);
            }
            Boolean filterResp = matchingKeys.size() > 0;
            if (allShouldSatisfy) {
                filterResp = filterResp && doAllSatisfy;
            }
            // if (filterActionRequest.getOperand().equalsIgnoreCase("not_contains") || filterActionRequest.getOperand().equalsIgnoreCase("not_contains_either")) {
            //     int keyCount = getKeyCount(payloadObj, null);
            //     filterResp = matchingKeySet.size() == keyCount;
            // }
            return new DataOperandsFilterResponse(filterResp, matchingKeys, null, null);

        } else if (filterActionRequest.getConcernedSubProperty() != null && filterActionRequest.getConcernedSubProperty().toLowerCase().equals("value")) {
            matchingKeys = filterActionRequest.getMatchingKeySet();
            // if concerned prop for_all, all values should satisfy
            valueExists(payloadObj, null, filterActionRequest.getQuerySet(), filterActionRequest.getOperand(), matchingKeys, filterActionRequest.getKeyValOperandSeen(), matchingValueKeySet, doAllSatisfy);
            Boolean filterResp = matchingValueKeySet.size() > 0;
            if (allShouldSatisfy) {
                filterResp = filterResp && doAllSatisfy;
            }
            // if (filterActionRequest.getOperand().equalsIgnoreCase("not_contains") || filterActionRequest.getOperand().equalsIgnoreCase("not_contains_either")) {
            //     int keyCount = getKeyCount(payloadObj, null);
            //     filterResp = matchingKeySet.size() == keyCount;
            // }
            return new DataOperandsFilterResponse(filterResp, matchingValueKeySet, null, null);
        } else if (filterActionRequest.getConcernedSubProperty() == null) {
            Object val = origPayload;

            if (filterActionRequest.getBodyOperand() != null && filterActionRequest.getBodyOperand().equalsIgnoreCase(BodyOperator.LENGTH.toString())) {
                val = origPayload.trim().length() - 2; // todo:
            } else if (filterActionRequest.getBodyOperand() != null && filterActionRequest.getBodyOperand().equalsIgnoreCase(BodyOperator.PERCENTAGE_MATCH.toString())) {
                RawApi sampleRawApi = filterActionRequest.getRawApi();
                if (sampleRawApi == null) {
                    return new DataOperandsFilterResponse(false, null, null, null);
                }
                double percentageMatch = compareWithOriginalResponse(origPayload, sampleRawApi.getResponse().getBody(), new HashMap<>());
                val = (int) percentageMatch;
            } else if (filterActionRequest.getBodyOperand() != null && filterActionRequest.getBodyOperand().equalsIgnoreCase(BodyOperator.PERCENTAGE_MATCH_SCHEMA.toString())) {
                RawApi sampleRawApi = filterActionRequest.getRawApi();
                if (sampleRawApi == null) {
                    return new DataOperandsFilterResponse(false, null, null, null);
                }
                double percentageMatch = Utils.structureMatch(filterActionRequest.getRawApi(), filterActionRequest.fetchRawApiBasedOnContext());
                val = (int) percentageMatch;
            }

            if (filterActionRequest.getOperand().equalsIgnoreCase(TestEditorEnums.DataOperands.REGEX_EXTRACT.toString())) {
                List<String> querySet = Utils.convertObjectToListOfString(filterActionRequest.getQuerySet());
                List<String> matches = new ArrayList<>();
                for (String query : querySet) {
                    matches.addAll(Utils.extractRegex(payload, query));
                }
                if (matches.size() > 0) {
                    return new DataOperandsFilterResponse(true, matches, null, null);
                }
                return new DataOperandsFilterResponse(false, null, null, null);
            }
            
            DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(val, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
            res = invokeFilter(dataOperandFilterRequest);
            return new DataOperandsFilterResponse(res, null, null, null);
        }

        return new DataOperandsFilterResponse(false, null, null, null);
    }

    public void extractReqPayload(FilterActionRequest filterActionRequest, Map<String, Object> varMap, boolean extractMultiple) {
        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null) {
            return;
        }
        String payload = rawApi.getRequest().getBody();
        extractPayload(filterActionRequest, varMap, payload, extractMultiple);
    }

    public void extractRespPayload(FilterActionRequest filterActionRequest, Map<String, Object> varMap, boolean extractMultiple) {
        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null) {
            return;
        }
        String payload = rawApi.getResponse().getBody();
        extractPayload(filterActionRequest, varMap, payload, extractMultiple);
    }

    public void extractPayload(FilterActionRequest filterActionRequest, Map<String, Object> varMap, String payload, boolean extractMultiple) {

        List<String> querySet = (List<String>) filterActionRequest.getQuerySet();
        Object val = null;
        String key = querySet.get(0);
        BasicDBObject reqObj = new BasicDBObject();
        try {
            payload = Utils.jsonifyIfArray(payload);
            reqObj =  BasicDBObject.parse(payload);
        } catch(Exception e) {
            // add log
        }

        if (filterActionRequest.getConcernedSubProperty() != null && filterActionRequest.getConcernedSubProperty().toLowerCase().equals("key")) {
            if (filterActionRequest.getMatchingKeySet() != null && filterActionRequest.getMatchingKeySet().size() > 0) {
                if (extractMultiple) {
                    val = filterActionRequest.getMatchingKeySet();
                } else {
                    val = filterActionRequest.getMatchingKeySet().get(0);
                }
            }

        } else if (filterActionRequest.getConcernedSubProperty() != null && filterActionRequest.getConcernedSubProperty().toLowerCase().equals("value")) {
            if (filterActionRequest.getMatchingKeySet() != null && filterActionRequest.getMatchingKeySet().size() > 0) {
                List<Object> listVal = new ArrayList<>();
                for (String matchKey: filterActionRequest.getMatchingKeySet()) {
                    listVal.add(getValue(reqObj, null, matchKey));
                    if (!extractMultiple) {
                        break;
                    }
                }
                val = listVal;
            }
        } else if (filterActionRequest.getBodyOperand() != null) {
            if (filterActionRequest.getBodyOperand().equalsIgnoreCase(BodyOperator.LENGTH.toString())) {
                val = payload.length() - 2;
            } else if (filterActionRequest.getBodyOperand().equalsIgnoreCase(BodyOperator.PERCENTAGE_MATCH.toString())) {
                RawApi sampleRawApi = filterActionRequest.getRawApi();
                if (sampleRawApi == null) {
                    return;
                }
                double percentageMatch = compareWithOriginalResponse(payload, sampleRawApi.getResponse().getBody(), new HashMap<>());
                val = (int) percentageMatch;
            }
            /*
             * no concerned sub property means that
             * the operation was directly applied on the payload
             * so we need to extract the matching key set, if any
             */
        } else if (filterActionRequest.getConcernedSubProperty() == null &&
                filterActionRequest.getMatchingKeySet() != null &&
                filterActionRequest.getMatchingKeySet().size() > 0) {
            if (extractMultiple) {
                val = filterActionRequest.getMatchingKeySet();
            } else {
                val = filterActionRequest.getMatchingKeySet().get(0);
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
            return new DataOperandsFilterResponse(false, null, null, null);
        }
        
        Map<String, List<String>> reqHeaders = rawApi.getRequest().getHeaders();
        return applyFiltersOnHeaders(filterActionRequest, reqHeaders);
    }

    public DataOperandsFilterResponse applyFilterOnResponseHeaders(FilterActionRequest filterActionRequest) {

        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null) {
            return new DataOperandsFilterResponse(false, null, null, null);
        }
        
        Map<String, List<String>> respHeaders = rawApi.getResponse().getHeaders();

        return applyFiltersOnHeaders(filterActionRequest, respHeaders);
    }

    public void extractRequestHeaders(FilterActionRequest filterActionRequest, Map<String, Object> varMap, boolean extractMultiple) {

        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null) {
            return;
        }
        
        Map<String, List<String>> reqHeaders = rawApi.getRequest().getHeaders();

        extractHeaders(filterActionRequest, varMap, reqHeaders, extractMultiple);
    }

    public void extractResponseHeaders(FilterActionRequest filterActionRequest, Map<String, Object> varMap, boolean extractMultiple) {

        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null) {
            return;
        }
        
        Map<String, List<String>> respHeaders = rawApi.getResponse().getHeaders();

        extractHeaders(filterActionRequest, varMap, respHeaders, extractMultiple);
    }

    public void extractHeaders(FilterActionRequest filterActionRequest, Map<String, Object> varMap, Map<String, List<String>> headers, boolean extractMultiple) {

        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null) {
            return;
        }
        
        String headerString = convertHeaders(headers);
        Object val = null;

        List<String> querySet = (List<String>) filterActionRequest.getQuerySet();
        String key = querySet.get(0);

        if (filterActionRequest.getConcernedSubProperty() != null && filterActionRequest.getConcernedSubProperty().toLowerCase().equals("key")) {
            if (filterActionRequest.getMatchingKeySet() != null && filterActionRequest.getMatchingKeySet().size() > 0) {
                if (extractMultiple) {
                    val = filterActionRequest.getMatchingKeySet();
                } else {
                    val = filterActionRequest.getMatchingKeySet().get(0);
                }                
            }
        } else if (filterActionRequest.getConcernedSubProperty() != null && filterActionRequest.getConcernedSubProperty().toLowerCase().equals("value")) {
            if (filterActionRequest.getMatchingKeySet() != null && filterActionRequest.getMatchingKeySet().size() > 0) {
                List<Object> listVal = new ArrayList<>();
                for (String matchKey: filterActionRequest.getMatchingKeySet()) {
                    List<String> values = headers.get(matchKey);
                    if (values != null && values.size() > 0) {
                        listVal.add(values.get(0));
                    }
                    if (!extractMultiple) {
                        break;
                    }
                }
                val = listVal;
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

        // result && res if concerned prop for_all
        // remove not contains logic 

        String operation = "or";
        if (filterActionRequest.getCollectionProperty() != null && filterActionRequest.getCollectionProperty().equalsIgnoreCase(CollectionOperands.FOR_ALL.toString())) {
            operation = "and";
            result = headers.size() > 0;
        }

        if (filterActionRequest.getConcernedSubProperty() != null && filterActionRequest.getConcernedSubProperty().toLowerCase().equals("key")) {
            for (String key: headers.keySet()) {

                DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(key, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
                res = invokeFilter(dataOperandFilterRequest);
                if (res) {
                    newMatchingKeys.add(key);
                }
                
                if (!res && (key.equals("cookie") || key.equals("set-cookie"))) {
                    List<String> cookieList = headers.getOrDefault(key, new ArrayList<>());
                    Map<String,String> cookieMap = parseCookie(cookieList);
                    for (String cookieKey : cookieMap.keySet()) {
                        dataOperandFilterRequest = new DataOperandFilterRequest(cookieKey, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
                        res = invokeFilter(dataOperandFilterRequest);
                        if (res) {
                            newMatchingKeys.add(cookieKey);
                            result = Utils.evaluateResult(operation, result, res);
                            break;
                        }
                    }
                }
                result = Utils.evaluateResult(operation, result, res);
            }

            // if (!result) {
            //     // some keys could match in case of for_all, so setting this empty again if all keys are not matching
            //     newMatchingKeys = new ArrayList<>();
            // }

            return new DataOperandsFilterResponse(result, newMatchingKeys, null, null);
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

                if (!res && (key.equals("cookie") || key.equals("set-cookie"))) {
                    List<String> cookieList = headers.getOrDefault("cookie", new ArrayList<>());
                    Map<String,String> cookieMap = parseCookie(cookieList);
                    for (String cookieKey : cookieMap.keySet()) {
                        DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(cookieMap.get(cookieKey), filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
                        res = invokeFilter(dataOperandFilterRequest);
                        if (res) {
                            matchingValueKeySet.add(cookieKey);
                            result = Utils.evaluateResult(operation, result, res);
                            break;
                        }
                    }
                }
                result = Utils.evaluateResult(operation, result, res);
            }

            // if (!result) {
            //     // some keys could match in case of for_all, so setting this empty again if all keys are not matching
            //     matchingValueKeySet = new ArrayList<>();
            // }

            // if (filterActionRequest.getOperand().equalsIgnoreCase("not_contains") || filterActionRequest.getOperand().equalsIgnoreCase("not_contains_either")) {
            //     result = matchingValueKeySet.size() == headers.size();
            // }
            return new DataOperandsFilterResponse(result, matchingValueKeySet, null, null);
        } else {
            String headerString = convertHeaders(headers);
            DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(headerString, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
            res = invokeFilter(dataOperandFilterRequest);
            return new DataOperandsFilterResponse(res, null, null, null);
        }
    }

    public DataOperandsFilterResponse applyFilterOnQueryParams(FilterActionRequest filterActionRequest) {

        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null) {
            return new DataOperandsFilterResponse(false, null, null, null);
        }
        String queryParams = rawApi.getRequest().getQueryParams();
        String url = filterActionRequest.getApiInfoKey().getUrl();
        BasicDBObject queryParamObj = RequestTemplate.getQueryJSON(url + "?" + queryParams);

        List<String> matchingKeys = new ArrayList<>();
        List<String> matchingValueKeySet = new ArrayList<>();
        // init based on operation
        // take care if obj set is empty
        Boolean result = false;
        Boolean res = false;
        String operation = "or";
        if (filterActionRequest.getCollectionProperty() != null && filterActionRequest.getCollectionProperty().equalsIgnoreCase(CollectionOperands.FOR_ALL.toString())) {
            operation = "and";
            result = queryParamObj.size() > 0;
        }

        if (filterActionRequest.getConcernedSubProperty() != null && filterActionRequest.getConcernedSubProperty().toLowerCase().equals("key")) {
            for (String key: queryParamObj.keySet()) {
                DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(key, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
                res = invokeFilter(dataOperandFilterRequest);
                result = Utils.evaluateResult(operation, result, res);
                if (res) {
                    matchingKeys.add(key);
                }
            }
            // if (!result) {
            //     // some keys could match in case of for_all, so setting this empty again if all keys are not matching
            //     matchingKeys = new ArrayList<>();
            // }
            return new DataOperandsFilterResponse(result, matchingKeys, null, null);
        } else if (filterActionRequest.getConcernedSubProperty() != null && filterActionRequest.getConcernedSubProperty().toLowerCase().equals("value")) {
            matchingKeys = filterActionRequest.getMatchingKeySet();
            for (String key: queryParamObj.keySet()) {
                if (filterActionRequest.getKeyValOperandSeen() && matchingKeys != null && !matchingKeys.contains(key)) {
                    continue;
                }
                DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(queryParamObj.getString(key), filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
                res = invokeFilter(dataOperandFilterRequest);
                result = Utils.evaluateResult(operation, result, res);
                if (res) {
                    matchingValueKeySet.add(key);
                    break;
                }
            }
            // if (!result) {
            //     // some keys could match in case of for_all, so setting this empty again if all keys are not matching
            //     matchingValueKeySet = new ArrayList<>();
            // }
            return new DataOperandsFilterResponse(result, matchingValueKeySet, null, null);
        } else {
            DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(queryParams, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
            res = invokeFilter(dataOperandFilterRequest);
            return new DataOperandsFilterResponse(res, null, null, null);
        }
    }

    public void extractQueryParams(FilterActionRequest filterActionRequest, Map<String, Object> varMap, boolean extractMultiple) {

        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null) {
            return;
        }

        Object val = null;
        List<String> querySet = (List<String>) filterActionRequest.getQuerySet();
        Object k = querySet.get(0);
        String key = k.toString();

        String queryParams = rawApi.getRequest().getQueryParams();
        String url = filterActionRequest.getApiInfoKey().getUrl();
        BasicDBObject queryParamObj = RequestTemplate.getQueryJSON(url + "?" + queryParams);
        
        if (filterActionRequest.getConcernedSubProperty() != null && filterActionRequest.getConcernedSubProperty().toLowerCase().equals("key")) {
            if (filterActionRequest.getMatchingKeySet().size() > 0) {
                if (extractMultiple) {
                    val = filterActionRequest.getMatchingKeySet();
                } else {
                    val = filterActionRequest.getMatchingKeySet().get(0);
                }
            }
        } else if (filterActionRequest.getConcernedSubProperty() != null && filterActionRequest.getConcernedSubProperty().toLowerCase().equals("value")) {
            if (filterActionRequest.getMatchingKeySet() != null && filterActionRequest.getMatchingKeySet().size() > 0) {
                List<Object> listVal = new ArrayList<>();
                for (String matchKey: filterActionRequest.getMatchingKeySet()) {
                    listVal.add(queryParamObj.get(matchKey));
                    if (!extractMultiple) {
                        break;
                    }
                }
                val = listVal;  
            }
        } else {
            if (queryParams == null) {
                val = "";
            } else {
                val = queryParams;
            }
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
        List<Object> listVal = new ArrayList<>();
        List<Object> updatedValues = new ArrayList<>();
        Object returnVal = new ArrayList<>();
        try {
            listVal = (List) querySet;
            for (Object objVal: listVal) {
                returnVal = resolveVar(filterActionRequest, querySet, varMap, objVal);
                if (returnVal instanceof ArrayList) {
                    List<Object> returnValList = (List) returnVal;
                    for (Object obj: returnValList) {
                        updatedValues.add(obj);
                    }
                } else {
                    updatedValues.add(returnVal);
                }
            }
        } catch (Exception e) {
            return null;
        }
        return updatedValues;
    }

    public Object resolveVar(FilterActionRequest filterActionRequest, Object querySet, Map<String, Object> varMap, Object objVal) {
        if (!(objVal instanceof String)) {
            return objVal;
        }
        Object obj;

        if (varMap.containsKey(objVal)) {
            obj = varMap.get(objVal);
            return obj;
        }

        if (VariableResolver.isWordListVariable(objVal, varMap)) {
            obj = (List) VariableResolver.resolveWordListVar(objVal.toString(), varMap);
            return obj;
        }

        Object contextVal = VariableResolver.resolveContextVariable(varMap, objVal.toString());
        if (contextVal instanceof  List) {
            List<String> contextList = (List<String>) contextVal;
            if (contextList != null && contextList.size() > 0) {
                return contextList;
            }
        }

        String val = (String) objVal;
        Boolean matches = Utils.checkIfContainsMatch(val, "\\$\\{[^}]*\\}");
        if (matches) {
            String origVal = val;
            val = val.substring(2, val.length());
            val = val.substring(0, val.length() - 1);

            if (varMap.containsKey(val)) {
                obj = varMap.get(val);
                return obj;
            }

            String[] params = val.split("\\.");
            String firstParam = params[0];
            String secondParam = null;
            if (params.length > 1) {
                secondParam = params[1];
            }
            if (isDynamicParamType(firstParam)) {
                obj = resolveDynamicValue(filterActionRequest, firstParam, secondParam);
                return obj;
            } else {
                obj = VariableResolver.resolveExpression(varMap, origVal);
                return obj;
            }
        }
        return objVal;
    }

    public boolean getMatchingKeysForPayload(Object obj, String parentKey, Object querySet, String operand, Set<String> matchingKeys, boolean doAllSatisfy) {
        Boolean res = false;
        if (obj instanceof JSONObject) {
            JSONObject basicDBObject = (JSONObject) obj;

            Set<String> keySet = basicDBObject.keySet();

            for(String key: keySet) {
                if (key == null) {
                    continue;
                }
                Object value = basicDBObject.get(key);
                doAllSatisfy = getMatchingKeysForPayload(value, key, querySet, operand, matchingKeys, doAllSatisfy);
                if (parentKey != null && TestEditorEnums.DataOperands.VALUETYPE.toString().equals(operand)) {
                    matchingKeys.add(parentKey);
                }

            }
        } else if (obj instanceof BasicDBObject) {

            BasicDBObject basicDBObject = (BasicDBObject) obj;

            Set<String> keySet = basicDBObject.keySet();

            for(String key: keySet) {
                if (key == null) {
                    continue;
                }
                Object value = basicDBObject.get(key);
                doAllSatisfy = getMatchingKeysForPayload(value, key, querySet, operand, matchingKeys, doAllSatisfy);
                if (parentKey != null && TestEditorEnums.DataOperands.VALUETYPE.toString().equals(operand)) {
                    matchingKeys.add(parentKey);
                }
                
            }
        } else if (obj instanceof BasicDBList) {
            for(Object elem: (BasicDBList) obj) {
                doAllSatisfy = getMatchingKeysForPayload(elem, parentKey, querySet, operand, matchingKeys, doAllSatisfy);
            }
            if (parentKey != null && TestEditorEnums.DataOperands.VALUETYPE.toString().equals(operand)) {
                matchingKeys.add(parentKey);
            }

        } else {
            if (!TestEditorEnums.DataOperands.VALUETYPE.toString().equals(operand)) {
                DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(parentKey, querySet, operand);
                res = invokeFilter(dataOperandFilterRequest);
                if (res) {
                    matchingKeys.add(parentKey);
                }
                doAllSatisfy = Utils.evaluateResult("and", doAllSatisfy, res);
            }
        }
        return doAllSatisfy;
    }

    public void valueExists(Object obj, String parentKey, Object querySet, String operand, List<String> matchingKeys, Boolean keyOperandSeen, List<String> matchingValueKeySet, boolean doAllSatisfy) {
        Boolean res = false;
        if (obj instanceof JSONObject) {
            JSONObject basicDBObject = (JSONObject) obj;

            Set<String> keySet = basicDBObject.keySet();

            for(String key: keySet) {
                if (key == null) {
                    continue;
                }
                Object value = basicDBObject.get(key);
                valueExists(value, key, querySet, operand, matchingKeys, keyOperandSeen, matchingValueKeySet, doAllSatisfy);
            }
        } else if (obj instanceof BasicDBObject) {
            BasicDBObject basicDBObject = (BasicDBObject) obj;

            Set<String> keySet = basicDBObject.keySet();

            for(String key: keySet) {
                if (key == null) {
                    continue;
                }
                Object value = basicDBObject.get(key);
                valueExists(value, key, querySet, operand, matchingKeys, keyOperandSeen, matchingValueKeySet, doAllSatisfy);
            }
        } else if (obj instanceof BasicDBList) {
            for(Object elem: (BasicDBList) obj) {
                valueExists(elem, parentKey, querySet, operand, matchingKeys, keyOperandSeen, matchingValueKeySet, doAllSatisfy);
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
            doAllSatisfy = Utils.evaluateResult("and", doAllSatisfy, res);
        }

        return;
    }

    public static Object getValue(Object obj, String parentKey, String queryKey) {
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

    public boolean isDynamicParamType(String param) {
        switch (param) {
            case "sample_request_payload":
            case "sample_response_payload":
            case "test_request_payload":
            case "test_response_payload":
            case "sample_request_headers":
            case "sample_response_headers":
            case "test_request_headers":
            case "test_response_headers":
                return true;
            default:
                return false;

        }
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

        List<BasicDBObject> privateValues = new ArrayList<>();
        if (filterActionRequest.getOperand().equalsIgnoreCase(TestEditorEnums.DataOperands.REGEX.toString())) {
            if (filterActionRequest.getContextEntities() == null) {
                return new DataOperandsFilterResponse(false, null, filterActionRequest.getContextEntities(), null);
            } else {
                for (BasicDBObject obj: filterActionRequest.getContextEntities()) {
                    DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(obj.get("value"), filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
                    Boolean res = invokeFilter(dataOperandFilterRequest);
                    if (res) {
                        privateValues.add(obj);
                        break;
                    }
                }
                return new DataOperandsFilterResponse(privateValues.size() > 0, null, privateValues, null);
            }
        }

        OriginalHttpRequest request = filterActionRequest.getRawApi().getRequest();
        BasicDBObject resp = getPrivateResourceCount(request, filterActionRequest.getApiInfoKey());

        int privateCount = (int) resp.get("privateCount");
        privateValues = (List<BasicDBObject>) resp.get("values");
        return new DataOperandsFilterResponse(privateCount > 0, null, privateValues, null);

    }

    public DataOperandsFilterResponse evaluateParamContext(FilterActionRequest filterActionRequest) {

        ApiInfo.ApiInfoKey apiInfoKey = filterActionRequest.getApiInfoKey();

        List<String> querySet = (List<String>) filterActionRequest.getQuerySet();
        if (querySet.get(0) == null) {
            return new DataOperandsFilterResponse(false, null, null, null);
        }
        String param = querySet.get(0).toString().trim();

        List<SingleTypeInfo> singleTypeInfos = dataActor.findStiByParam(apiInfoKey.getApiCollectionId(), param);

        if (singleTypeInfos.isEmpty()) {
            return new DataOperandsFilterResponse(false, null, null, null);
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

        return new DataOperandsFilterResponse(paramValues.size() > 0, null, paramValues, null);

    }

    public DataOperandsFilterResponse endpointInTraffic(FilterActionRequest filterActionRequest) {
        ApiInfo.ApiInfoKey apiInfoKey = filterActionRequest.getApiInfoKey();

        List<Boolean> querySet = (List) filterActionRequest.getQuerySet();
        Boolean shouldBePresent = (Boolean) querySet.get(0);

        SingleTypeInfo singleTypeInfo = dataActor.findSti(apiInfoKey.getApiCollectionId(), filterActionRequest.getTestRunRawApi().getRequest().getUrl(), apiInfoKey.getMethod());
        boolean res = false;
        if (shouldBePresent) {
            res = singleTypeInfo != null;
        } else {
            res = singleTypeInfo == null;
        }
        return new DataOperandsFilterResponse(res, null, null, null);
    }

    private DataOperandsFilterResponse evaluateRolesAccessContext(FilterActionRequest filterActionRequest, boolean include) {

        ApiInfo.ApiInfoKey apiInfoKey = filterActionRequest.getApiInfoKey();
        List<String> querySet = (List) filterActionRequest.getQuerySet();
        String roleName = querySet.get(0);

        AccessMatrixUrlToRole accessMatrixUrlToRole = dataActor.fetchAccessMatrixUrlToRole(apiInfoKey);

        List<String> rolesThatHaveAccessToApi = new ArrayList<>();
        if (accessMatrixUrlToRole != null) {
            rolesThatHaveAccessToApi = accessMatrixUrlToRole.getRoles();
        }

        int indexOfRole = rolesThatHaveAccessToApi.indexOf(roleName);

        boolean res = include == (indexOfRole != -1);

        return new DataOperandsFilterResponse(res, null, null, null);
    }

    private DataOperandsFilterResponse applyFilterOnAccessType(FilterActionRequest filterActionRequest){
        List<String> querySet = (List<String>) filterActionRequest.getQuerySet();
        ApiInfo.ApiInfoKey apiInfoKey = filterActionRequest.getApiInfoKey();
        ApiInfo apiInfo = dataActor.fetchApiInfo(apiInfoKey);
        Set<ApiAccessType> apiAccessTypes = apiInfo.getApiAccessTypes();
        boolean res = false;
        if(apiInfo != null && !querySet.isEmpty() && apiAccessTypes.size() > 0){
            ApiAccessType apiAccessType = Utils.getApiAccessTypeFromString(querySet.get(0).toString());
            if(apiAccessTypes.size() == 1){
                res = apiAccessTypes.contains(apiAccessType);
            }else{
                res = apiAccessType == ApiAccessType.PUBLIC;
            }
        }
        return new DataOperandsFilterResponse(res, null, null, null);
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
            URLTemplate urlTemplate = createUrlTemplate(url, method);
            String[] tokens = urlTemplate.getTokens();

            String[] urlWithParamsTokens = createUrlTemplate(urlWithParams, method).getTokens();
            for (int i = 0;i < tokens.length; i++) {
                if (tokens[i] == null) {
                    SingleTypeInfo singleTypeInfo = querySti(i+"", true,apiInfoKey, false, -1);
                    BasicDBObject obj = new BasicDBObject();
                    singleTypeInfo = new SingleTypeInfo();
                    if (singleTypeInfo != null && singleTypeInfo.getIsPrivate()) {
                        privateCnt++;
                    }
                    if (singleTypeInfo == null || !singleTypeInfo.getIsPrivate() || singleTypeInfo.getValues() == null || singleTypeInfo.getValues().getElements().size() == 0) {
                        if (urlWithParamsTokens.length > i) {
                            obj.put("key", i+"");
                            obj.put("value", urlWithParamsTokens[i]);
                            if (privateValues.size() < 5 && obj.get("value") != null) {
                                privateValues.add(obj);
                            }
                            privateCnt++;
                        }
                        continue;
                    }
                    if (singleTypeInfo.getValues() == null || singleTypeInfo.getValues().getElements().size() == 0) {
                        SampleData sd = dataActor.fetchSampleDataById(apiInfoKey.getApiCollectionId(), apiInfoKey.getUrl(), apiInfoKey.getMethod());
                        if (sd.getSamples() == null) {
                            continue;
                        }
                        for (String sample: sd.getSamples()) {
                            try {
                                HttpResponseParams httpResponseParams = parseSampleMessage(sample);
                                String sUrl = httpResponseParams.getRequestParams().getURL();
                                String[] sUrlTokens = sUrl.split("/");
                                String[] origUrlTokens = urlWithParams.split("/");
                                if (!origUrlTokens[i].equals(sUrlTokens[i])) {
                                    obj.put("key", i+"");
                                    obj.put("value", sUrlTokens[i]);
                                    if (privateValues.size() < 5 && obj.get("value") != null) {
                                        privateValues.add(obj);
                                    }
                                    break;
                                }
                            } catch (Exception e) {
                                // TODO: handle exception
                            }
                        }
                    } else {
                        Set<String> valSet = singleTypeInfo.getValues().getElements();
                        String val = valSet.iterator().next();
                        obj.put("key", i+"");
                        obj.put("value", val);
                        if (privateValues.size() < 5 && obj.get("value") != null) {
                            privateValues.add(obj);
                        }
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
            if (singleTypeInfo == null || !singleTypeInfo.getIsPrivate()) {
                continue;
            }

            if (singleTypeInfo.getValues() == null || singleTypeInfo.getValues().getElements().size() == 0) {
                SampleData sd = dataActor.fetchSampleDataById(apiInfoKey.getApiCollectionId(), apiInfoKey.getUrl(), apiInfoKey.getMethod());
                if (sd.getSamples() == null) {
                    continue;
                }
                for (String sample: sd.getSamples()) {
                    String key = SingleTypeInfo.findLastKeyFromParam(param);
                    BasicDBObject payloadObj = new BasicDBObject();
                    try {
                        HttpResponseParams httpResponseParams = parseSampleMessage(sample);
                        payloadObj = RequestTemplate.parseRequestPayload(httpResponseParams.getRequestParams().getPayload(), null);
                    } catch (Exception e) {
                        // TODO: handle exception
                    }
                    
                    Object paramVal = payloadObj.get(param);
                    Object origVal = payload.get(param);
                    if (paramVal != null && !paramVal.equals(origVal)) {
                        obj.put("key", key);
                        obj.put("value", paramVal.toString());
                        if (privateValues.size() < 5 && obj.get("value") != null) {
                            privateValues.add(obj);
                        }
                        break;
                    }
                }
            } else {
                Set<String> valSet = singleTypeInfo.getValues().getElements();
                String val = valSet.iterator().next();
                String key = SingleTypeInfo.findLastKeyFromParam(param);
                obj.put("key", key);
                obj.put("value", val);
                if (privateValues.size() < 5 && obj.get("value") != null) {
                    privateValues.add(obj);
                }
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
            payload = Utils.jsonifyIfArray(payload);
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
            responsePayload = Utils.jsonifyIfArray(responsePayload);
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
        SingleTypeInfo singleTypeInfo = dataActor.findStiWithUrlParamFilters(apiInfoKey.getApiCollectionId(), apiInfoKey.url, apiInfoKey.method.name(), responseCode, isHeader, param, isUrlParam);
        if (singleTypeInfo == null) return null;
        return singleTypeInfo;
    }

}
