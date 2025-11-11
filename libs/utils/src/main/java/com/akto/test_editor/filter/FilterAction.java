package com.akto.test_editor.filter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.akto.dao.testing.AccessMatrixUrlToRolesDao;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.testing.AccessMatrixUrlToRole;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.bson.conversions.Bson;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.SingleTypeInfoDao;
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
import com.akto.mcp.McpRequestResponseUtils;
import com.akto.test_editor.TestingUtilsSingleton;
import com.akto.test_editor.Utils;
import com.akto.test_editor.execution.VariableResolver;
import com.akto.test_editor.filter.data_operands_impl.*;
import com.akto.util.JSONUtils;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

import static com.akto.runtime.utils.Utils.parseCookie;
import static com.akto.dto.RawApi.convertHeaders;
import static com.akto.runtime.RuntimeUtil.createUrlTemplate;
import static com.akto.testing.Utils.compareWithOriginalResponse;
import static com.akto.runtime.utils.Utils.parseKafkaMessage;

public final class FilterAction {
    
    public final Map<String, DataOperandsImpl> filters = new HashMap<String, DataOperandsImpl>() {{
        put("contains_all", new ContainsAllFilter());
        put("contains_either", new ContainsEitherFilter());
        put("contains_either_cidr", new ContainsEitherIpFilter());
        put("not_contains_cidr", new NotContainsIpFilter());
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
        put("nlp_classification", new NlpClassificationFilter());  // TODO: MCP - Demo placeholder
        put("category", new CategoryFilter());  // TODO: MCP - Demo placeholder
        put("confidence", new ConfidenceFilter());
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
            case "source_ip":
                return applyFilterOnSourceIps(filterActionRequest);
            case "destination_ip":
                return applyFilterOnDestinationIps(filterActionRequest);
            case "country_code":
                return applyFilterOnCountryCode(filterActionRequest);
            case "nlp_classification":
                return applyFilterOnNlpClassification(filterActionRequest);
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

        // handle for mcp requests
        String url = filterActionRequest.getApiInfoKey().getUrl();
        if(url.contains("tools") && TestingUtilsSingleton.getInstance().isMcpRequest(filterActionRequest.getApiInfoKey(), filterActionRequest.getRawApi())) {
            if(url.contains("call")) {
                url = url.split("call/")[1];
            }
        }

        DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(url, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
        ValidationResult res = invokeFilter(dataOperandFilterRequest);
        return new DataOperandsFilterResponse(res.getIsValid(), null, null, null, res.getValidationReason());
    }

    public void extractUrl(FilterActionRequest filterActionRequest, Map<String, Object> varMap) {
        String url = filterActionRequest.getRawApi().getRequest().getUrl();
        if(url.contains("tools") && TestingUtilsSingleton.getInstance().isMcpRequest(filterActionRequest.getApiInfoKey(), filterActionRequest.getRawApi())) {
            if(url.contains("call")) {
                url = url.split("call/")[1];
            }
        }
        List<String> querySet = (List<String>) filterActionRequest.getQuerySet();
        if (varMap.containsKey(querySet.get(0)) && varMap.get(querySet.get(0)) != null) {
            return;
        }
        varMap.put(querySet.get(0), url);
    }

    public DataOperandsFilterResponse applyFilterOnMethod(FilterActionRequest filterActionRequest) {

        String method = filterActionRequest.getApiInfoKey().getMethod().toString();
        // handle of mcp requests
        String url = filterActionRequest.getApiInfoKey().getUrl();
        boolean isMcpRequest = McpRequestResponseUtils.isMcpRequest(filterActionRequest.getRawApi());
        if(url.contains("tools") && isMcpRequest) {
            if(url.contains("call")) {
                method = TestingUtilsSingleton.getInstance().getMcpRequestMethod(filterActionRequest.getApiInfoKey(), filterActionRequest.getRawApi());
            }else{
                method = "POST";
            }
        }
        DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(method, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
        ValidationResult res = invokeFilter(dataOperandFilterRequest);
        if(!res.getIsValid() && isMcpRequest) {
            // fallback to POST for all the mcp tests because they have filter of method eq: POST
            method = "POST";
            dataOperandFilterRequest = new DataOperandFilterRequest(method, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
            res = invokeFilter(dataOperandFilterRequest);
        }
        return new DataOperandsFilterResponse(res.getIsValid(), null, null, null, res.getValidationReason());
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
        ValidationResult res = invokeFilter(dataOperandFilterRequest);
        return new DataOperandsFilterResponse(res.getIsValid(), null, null, null, res.getValidationReason());
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
        ValidationResult res = invokeFilter(dataOperandFilterRequest);
        return new DataOperandsFilterResponse(res.getIsValid(), null, null, null, res.getValidationReason());
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

    public DataOperandsFilterResponse applyFilterOnSourceIps(FilterActionRequest filterActionRequest) {

        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null || rawApi.getRequest() == null) {
            return new DataOperandsFilterResponse(false, null, null, null);
        }

        String sourceIp = rawApi.getRequest().getSourceIp();
        DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(sourceIp, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
        ValidationResult res = invokeFilter(dataOperandFilterRequest);
        return new DataOperandsFilterResponse(res.getIsValid(), null, null, null, res.getValidationReason());
    }

    public DataOperandsFilterResponse applyFilterOnDestinationIps(FilterActionRequest filterActionRequest) {

        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null || rawApi.getRequest() == null) {
            return new DataOperandsFilterResponse(false, null, null, null);
        }

        String destinationIp = rawApi.getRequest().getDestinationIp();
        DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(destinationIp, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
        ValidationResult res = invokeFilter(dataOperandFilterRequest);
        return new DataOperandsFilterResponse(res.getIsValid(), null, null, null, res.getValidationReason());
    }

    public DataOperandsFilterResponse applyFilterOnCountryCode(FilterActionRequest filterActionRequest) {

        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null || rawApi.getRequest() == null) {
            return new DataOperandsFilterResponse(false, null, null, null);
        }

        String countryCode = rawApi.getRawApiMetadata().getCountryCode();
        if (countryCode.isEmpty()){
            return new DataOperandsFilterResponse(false, null, null, null);
        }

        DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(countryCode, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
        ValidationResult res = invokeFilter(dataOperandFilterRequest);
        return new DataOperandsFilterResponse(res.getIsValid(), null, null, null, res.getValidationReason());
    }

    public DataOperandsFilterResponse applyFilterOnTestType(FilterActionRequest filterActionRequest) {
        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null || rawApi.getRequest() == null) {
            return new DataOperandsFilterResponse(false, null, null, null);
        }
        int apiCollectionId = filterActionRequest.getApiInfoKey().getApiCollectionId();
        ApiCollection apiCollection = ApiCollectionsDao.instance.getMetaForId(apiCollectionId);
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

        // Strip BOM before processing for regex filters to avoid false positives with SOAP payloads
        if (filterActionRequest.getOperand() != null &&
            filterActionRequest.getOperand().equals(TestEditorEnums.DataOperands.REGEX.toString())) {
            reqBody = Utils.stripBOM(reqBody);
        }
        
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
        ApiInfo.ApiInfoKey apiInfoKey = filterActionRequest.getApiInfoKey();
        if(TestingUtilsSingleton.getInstance().isMcpRequest(apiInfoKey, rawApi)) {
            String contentType = response.getHeaders().get("content-type").get(0);
            String tempReqBody = McpRequestResponseUtils.parseResponse(contentType, reqBody);
           
            if(tempReqBody.contains("error") && filterActionRequest.isValidationContext()) {
                // check if error comes out in parsing, call to LLM when context is not filter
                MagicValidateFilter magicValidateFilter = new MagicValidateFilter();
                DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(reqBody, filterActionRequest.getQuerySet(), "magic_validate");
                ValidationResult validationResult = magicValidateFilter.isValid(dataOperandFilterRequest);
                return new DataOperandsFilterResponse(validationResult.getIsValid(), null, null, null, validationResult.getValidationReason());
            
            }else{
                reqBody = tempReqBody;
            }
        }
        // Strip BOM before processing for regex filters to avoid false positives with SOAP payloads
        if (filterActionRequest.getOperand() != null &&
            filterActionRequest.getOperand().equals(TestEditorEnums.DataOperands.REGEX.toString())) {
            reqBody = Utils.stripBOM(reqBody);
        }

        return applyFilterOnPayload(filterActionRequest, reqBody);
    }

    public DataOperandsFilterResponse applyFilterOnPayload(FilterActionRequest filterActionRequest, String payload) {

        String origPayload = payload;
        BasicDBObject payloadObj = new BasicDBObject();
        if (!filterActionRequest.getOperand().equals(TestEditorEnums.DataOperands.REGEX.toString()) || (filterActionRequest.getCollectionProperty() != null && filterActionRequest.getCollectionProperty().equals(TestEditorEnums.CollectionOperands.FOR_ONE.toString())) ) {
            try {
                payload = Utils.jsonifyIfArray(payload);
                JSONObject jsonObj = JSON.parseObject(payload);
                payloadObj = new BasicDBObject(jsonObj);
            } catch(Exception e) {
                // add log
            }
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
            StringBuilder validationReason = new StringBuilder();
            // if concerned prop for_all, all values should satisfy
            valueExists(payloadObj, null, filterActionRequest.getQuerySet(), filterActionRequest.getOperand(), matchingKeys, filterActionRequest.getKeyValOperandSeen(), matchingValueKeySet, doAllSatisfy, validationReason);
            Boolean filterResp = matchingValueKeySet.size() > 0;
            if (allShouldSatisfy) {
                filterResp = filterResp && doAllSatisfy;
            }
            // if (filterActionRequest.getOperand().equalsIgnoreCase("not_contains") || filterActionRequest.getOperand().equalsIgnoreCase("not_contains_either")) {
            //     int keyCount = getKeyCount(payloadObj, null);
            //     filterResp = matchingKeySet.size() == keyCount;
            // }
            if (filterResp) {
                return new DataOperandsFilterResponse(true, matchingValueKeySet, null, null);
            } else {
                return new DataOperandsFilterResponse(false, matchingValueKeySet, null, null, validationReason.toString());
            }
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
            
            DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(val, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
            ValidationResult validationResult = invokeFilter(dataOperandFilterRequest);
            return new DataOperandsFilterResponse(validationResult.getIsValid(), null, null, null, validationResult.getValidationReason());
        } else if (filterActionRequest.getConcernedSubProperty() == null) {
            DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(payload, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
            ValidationResult validationResult = invokeFilter(dataOperandFilterRequest);
            return new DataOperandsFilterResponse(validationResult.getIsValid(), null, null, null, validationResult.getValidationReason());
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
        ApiInfo.ApiInfoKey apiInfoKey = filterActionRequest.getApiInfoKey();
        if(TestingUtilsSingleton.getInstance().isMcpRequest(apiInfoKey, rawApi)) {
            String contentType = rawApi.getResponse().getHeaders().get("content-type").get(0);
            payload = McpRequestResponseUtils.parseResponse(contentType, payload);
        }
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

        // for mcp requests, remove mcp related params like name, id which is in root level, jsonrpc, etc.
        if (TestingUtilsSingleton.getInstance().isMcpRequest(filterActionRequest.getApiInfoKey(), filterActionRequest.getRawApi())) {
            reqObj = McpRequestResponseUtils.removeMcpRelatedParams(reqObj);
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

        StringBuilder validationErrorString = new StringBuilder();
        String operation = "or";
        if (filterActionRequest.getCollectionProperty() != null && filterActionRequest.getCollectionProperty().equalsIgnoreCase(CollectionOperands.FOR_ALL.toString())) {
            operation = "and";
            result = headers.size() > 0;
        }

        if (filterActionRequest.getConcernedSubProperty() != null && filterActionRequest.getConcernedSubProperty().toLowerCase().equals("key")) {
            for (String key: headers.keySet()) {

                DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(key, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
                ValidationResult validationResult = invokeFilter(dataOperandFilterRequest);
                res = validationResult.getIsValid();
                if (validationResult.getIsValid()) {
                    newMatchingKeys.add(key);
                } else {
                    validationErrorString.append("\n").append(validationResult.getValidationReason());
                }
                
                if (!res && (key.equals("cookie") || key.equals("set-cookie"))) {
                    List<String> cookieList = headers.getOrDefault(key, new ArrayList<>());
                    Map<String,String> cookieMap = parseCookie(cookieList);
                    for (String cookieKey : cookieMap.keySet()) {
                        dataOperandFilterRequest = new DataOperandFilterRequest(cookieKey, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
                        validationResult = invokeFilter(dataOperandFilterRequest);
                        res = validationResult.getIsValid();
                        if (res) {
                            newMatchingKeys.add(cookieKey);
                            result = Utils.evaluateResult(operation, result, res);
                            break;
                        } else {
                            validationErrorString.append("\n").append(validationResult.getValidationReason());
                        }
                    }
                }
                result = Utils.evaluateResult(operation, result, res);
            }

            // if (!result) {
            //     // some keys could match in case of for_all, so setting this empty again if all keys are not matching
            //     newMatchingKeys = new ArrayList<>();
            // }

            return new DataOperandsFilterResponse(result, newMatchingKeys, null, null, validationErrorString.toString());
        } else if (filterActionRequest.getConcernedSubProperty() != null && filterActionRequest.getConcernedSubProperty().toLowerCase().equals("value")) {
            
            for (String key: headers.keySet()) {
                if (filterActionRequest.getKeyValOperandSeen() && oldMatchingKeys != null && !oldMatchingKeys.contains(key)) {
                    continue;
                }
                for (String val: headers.get(key)) {
                    DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(val, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
                    ValidationResult validationResult = invokeFilter(dataOperandFilterRequest);
                    res = validationResult.getIsValid();
                    if (res) {
                        matchingValueKeySet.add(key);
                        break;
                    } else {
                        validationErrorString.append("\n").append(validationResult.getValidationReason());
                    }
                }

                if (!res && (key.equals("cookie") || key.equals("set-cookie"))) {
                    List<String> cookieList = headers.getOrDefault("cookie", new ArrayList<>());
                    Map<String,String> cookieMap = parseCookie(cookieList);
                    for (String cookieKey : cookieMap.keySet()) {
                        DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(cookieMap.get(cookieKey), filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
                        ValidationResult validationResult = invokeFilter(dataOperandFilterRequest);
                        res = validationResult.getIsValid();
                        if (res) {
                            matchingValueKeySet.add(cookieKey);
                            result = Utils.evaluateResult(operation, result, res);
                            break;
                        } else {
                            validationErrorString.append("\n").append(validationResult.getValidationReason());
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
            return new DataOperandsFilterResponse(result, matchingValueKeySet, null, null, validationErrorString.toString());
        } else {
            String headerString = convertHeaders(headers);
            DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(headerString, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
            ValidationResult validationResult = invokeFilter(dataOperandFilterRequest);
            return new DataOperandsFilterResponse(validationResult.getIsValid(), null, null, null, validationResult.getValidationReason());
        }
    }

    public DataOperandsFilterResponse applyFilterOnQueryParams(FilterActionRequest filterActionRequest) {
        StringBuilder validationErrorString = new StringBuilder();
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

        Object val = queryParams;

        if (filterActionRequest.getConcernedSubProperty() != null && filterActionRequest.getConcernedSubProperty().toLowerCase().equals("key")) {
            for (String key: queryParamObj.keySet()) {
                DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(key, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
                ValidationResult validationResult = invokeFilter(dataOperandFilterRequest);
                res = validationResult.getIsValid();
                result = Utils.evaluateResult(operation, result, res);
                if (res) {
                    matchingKeys.add(key);
                } else {
                    validationErrorString.append("\n").append(validationResult.getValidationReason());
                }
            }
            // if (!result) {
            //     // some keys could match in case of for_all, so setting this empty again if all keys are not matching
            //     matchingKeys = new ArrayList<>();
            // }
            return new DataOperandsFilterResponse(result, matchingKeys, null, null, validationErrorString.toString());
        } else if (filterActionRequest.getConcernedSubProperty() != null && filterActionRequest.getConcernedSubProperty().toLowerCase().equals("value")) {
            matchingKeys = filterActionRequest.getMatchingKeySet();
            for (String key: queryParamObj.keySet()) {
                if (filterActionRequest.getKeyValOperandSeen() && matchingKeys != null && !matchingKeys.contains(key)) {
                    continue;
                }
                DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(queryParamObj.getString(key), filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
                ValidationResult validationResult = invokeFilter(dataOperandFilterRequest);
                res = validationResult.getIsValid();
                result = Utils.evaluateResult(operation, result, res);
                if (res) {
                    matchingValueKeySet.add(key);
                    break;
                } else {
                    validationErrorString.append("\n").append(validationResult.getValidationReason());
                }
            }
            // if (!result) {
            //     // some keys could match in case of for_all, so setting this empty again if all keys are not matching
            //     matchingValueKeySet = new ArrayList<>();
            // }
            return new DataOperandsFilterResponse(result, matchingValueKeySet, null, null, validationErrorString.toString());
        } else if (filterActionRequest.getConcernedSubProperty() == null &&
                filterActionRequest.getBodyOperand() != null &&
                filterActionRequest.getBodyOperand().equalsIgnoreCase(BodyOperator.LENGTH.toString())) {
            if(queryParams == null) {
                val = 0;
            } else {
                val = queryParams.length();
            }
        }
        DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(val, filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
        ValidationResult validationResult = invokeFilter(dataOperandFilterRequest);
        res = validationResult.getIsValid();
        return new DataOperandsFilterResponse(res, null, null, null, validationResult.getValidationReason());
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

    public ValidationResult invokeFilter(DataOperandFilterRequest dataOperandFilterRequest) {

        DataOperandsImpl handler = this.filters.get(dataOperandFilterRequest.getOperand().toLowerCase());
        if (handler == null) {
          return new ValidationResult(false, "\noperand:" + dataOperandFilterRequest.getOperand().toLowerCase()+ " not found in filters");
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
                ValidationResult validationResult = invokeFilter(dataOperandFilterRequest);
                res = validationResult.getIsValid();
                if (res) {
                    matchingKeys.add(parentKey);
                }
                doAllSatisfy = Utils.evaluateResult("and", doAllSatisfy, res);
            }
        }
        return doAllSatisfy;
    }

    public void valueExists(Object obj, String parentKey, Object querySet, String operand, List<String> matchingKeys, Boolean keyOperandSeen, List<String> matchingValueKeySet, boolean doAllSatisfy, StringBuilder validationReason) {
        Boolean res = false;
        if (obj instanceof JSONObject) {
            JSONObject basicDBObject = (JSONObject) obj;

            Set<String> keySet = basicDBObject.keySet();

            for(String key: keySet) {
                if (key == null) {
                    continue;
                }
                Object value = basicDBObject.get(key);
                valueExists(value, key, querySet, operand, matchingKeys, keyOperandSeen, matchingValueKeySet, doAllSatisfy, validationReason);
            }
        } else if (obj instanceof BasicDBObject) {
            BasicDBObject basicDBObject = (BasicDBObject) obj;

            Set<String> keySet = basicDBObject.keySet();

            for(String key: keySet) {
                if (key == null) {
                    continue;
                }
                Object value = basicDBObject.get(key);
                valueExists(value, key, querySet, operand, matchingKeys, keyOperandSeen, matchingValueKeySet, doAllSatisfy, validationReason);
            }
        } else if (obj instanceof BasicDBList) {
            for(Object elem: (BasicDBList) obj) {
                valueExists(elem, parentKey, querySet, operand, matchingKeys, keyOperandSeen, matchingValueKeySet, doAllSatisfy, validationReason);
            }
        } else {
            if (keyOperandSeen && matchingKeys != null && !matchingKeys.contains(parentKey)) {
                return;
            }
            DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(obj, querySet, operand);
            ValidationResult validationResult = invokeFilter(dataOperandFilterRequest);
            res = validationResult.getIsValid();
            if (res) {
                matchingValueKeySet.add(parentKey);
            } else {
                if (!validationReason.toString().contains(validationResult.getValidationReason())) {
                    validationReason.append("\n").append(validationResult.getValidationReason());
                }
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
        StringBuilder vulnerabilityReasonString = new StringBuilder();
        if (filterActionRequest.getOperand().equalsIgnoreCase(TestEditorEnums.DataOperands.REGEX.toString())) {
            if (filterActionRequest.getContextEntities() == null) {
                return new DataOperandsFilterResponse(false, null, filterActionRequest.getContextEntities(), null);
            } else {
                for (BasicDBObject obj: filterActionRequest.getContextEntities()) {
                    DataOperandFilterRequest dataOperandFilterRequest = new DataOperandFilterRequest(obj.get("value"), filterActionRequest.getQuerySet(), filterActionRequest.getOperand());
                    ValidationResult validationResult = invokeFilter(dataOperandFilterRequest);
                    boolean res = validationResult.getIsValid();
                    if (res) {
                        privateValues.add(obj);
                        break;
                    } else {
                        vulnerabilityReasonString.append("\n").append(validationResult.getValidationReason());
                    }
                }
                if (privateValues.size() > 0) {
                    return new DataOperandsFilterResponse(true, null, privateValues, null);
                } else {
                    return new DataOperandsFilterResponse(false, null, privateValues, null, vulnerabilityReasonString.toString());
                }
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

        Bson filter = Filters.and(
            Filters.eq("apiCollectionId", apiInfoKey.getApiCollectionId()),
            Filters.regex("param", param),
            Filters.eq("isHeader", false)
        );

        List<SingleTypeInfo> singleTypeInfos = SingleTypeInfoDao.instance.findAll(filter, 0, 500, null);

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
        return new DataOperandsFilterResponse(res, null, null, null);
    }

    private DataOperandsFilterResponse evaluateRolesAccessContext(FilterActionRequest filterActionRequest, boolean include) {

        ApiInfo.ApiInfoKey apiInfoKey = filterActionRequest.getApiInfoKey();
        Bson filterQ = Filters.eq("_id", apiInfoKey);
        List<String> querySet = (List) filterActionRequest.getQuerySet();
        String roleName = querySet.get(0);

        AccessMatrixUrlToRole accessMatrixUrlToRole = AccessMatrixUrlToRolesDao.instance.findOne(filterQ);

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
        ApiInfo apiInfo = ApiInfoDao.instance.findOne(ApiInfoDao.getFilter(apiInfoKey));
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
                        Bson filterQSampleData = Filters.and(
                            Filters.eq("_id.apiCollectionId", apiInfoKey.getApiCollectionId()),
                            Filters.eq("_id.method", apiInfoKey.getMethod()),
                            Filters.eq("_id.url", apiInfoKey.getUrl())
                        );
                        SampleData sd = SampleDataDao.instance.findOne(filterQSampleData);
                        if (sd.getSamples() == null) {
                            continue;
                        }
                        for (String sample: sd.getSamples()) {
                            try {
                                HttpResponseParams httpResponseParams = parseKafkaMessage(sample);
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
                Bson filterQSampleData = Filters.and(
                    Filters.eq("_id.apiCollectionId", apiInfoKey.getApiCollectionId()),
                    Filters.eq("_id.method", apiInfoKey.getMethod()),
                    Filters.eq("_id.url", apiInfoKey.getUrl())
                );
                SampleData sd = SampleDataDao.instance.findOne(filterQSampleData);
                if (sd.getSamples() == null) {
                    continue;
                }
                for (String sample: sd.getSamples()) {
                    String key = SingleTypeInfo.findLastKeyFromParam(param);
                    BasicDBObject payloadObj = new BasicDBObject();
                    try {
                        HttpResponseParams httpResponseParams = parseKafkaMessage(sample);
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
            Filters.regex("param", Utils.escapeSpecialCharacters(param)),
            urlParamFilters
        );
        
        SingleTypeInfo singleTypeInfo = SingleTypeInfoDao.instance.findOne(filter);

        if (singleTypeInfo == null) return null;

        return singleTypeInfo;
    }

    public DataOperandsFilterResponse applyFilterOnNlpClassification(FilterActionRequest filterActionRequest) {
        // TODO: MCP - This should work like other term operands
        // It should get the payload content and perform NLP classification on it
        // For now, this is a demo placeholder that accepts all but validates nothing
        
        RawApi rawApi = filterActionRequest.fetchRawApiBasedOnContext();
        if (rawApi == null) {
            return new DataOperandsFilterResponse(false, null, null, null);
        }
        
        // Get the payload content (request or response body)
        String payload = null;
        if ("request_payload".equalsIgnoreCase(filterActionRequest.getConcernedProperty())) {
            payload = rawApi.getRequest().getBody();
        } else if ("response_payload".equalsIgnoreCase(filterActionRequest.getConcernedProperty())) {
            payload = rawApi.getResponse().getBody();
        }
        
        if (payload == null) {
            return new DataOperandsFilterResponse(false, null, null, null);
        }
        
        // TODO: MCP - Call actual NLP classification service on the payload content
        // The payload content should be analyzed for hate speech, toxicity, etc.
        // For demo purposes, always return false to indicate feature not implemented
        
        return new DataOperandsFilterResponse(false, null, null, null, 
            "NLP Classification not yet implemented - demo mode. Payload length: " + payload.length());
    }

}
