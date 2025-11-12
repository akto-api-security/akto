package com.akto.utils;

import com.akto.dto.*;
import com.akto.dto.type.KeyTypes;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.enums.RedactionType;
import com.akto.graphql.GraphQLUtils;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.mongodb.BasicDBObject;

import java.util.*;

import static com.akto.dto.RawApi.convertHeaders;
import static com.akto.runtime.utils.Utils.parseCookie;

public class RedactParser {
    static ObjectMapper mapper = new ObjectMapper();
    static JsonFactory factory = mapper.getFactory();
    private static final LoggerMaker loggerMaker = new LoggerMaker(RedactParser.class, LogDb.RUNTIME);

    public static final String redactValue = "****";

    public static String redactCookie(Map<String, List<String>> headers, String header) {
        String cookie = "";
        List<String> cookieList = headers.getOrDefault(header, new ArrayList<>());
        Map<String, String> cookieMap = parseCookie(cookieList);
        for (String cookieKey : cookieMap.keySet()) {
            cookie += cookieKey + "=" + redactValue + ";";
        }
        if (cookie.isEmpty()) {
            cookie = redactValue;
        }
        return cookie;
    }

    private static String handleQueryParams(String url, RedactionType redactionType, String redactValue) {

        if (redactionType == RedactionType.NONE || url == null || url.isEmpty()) {
            return url;
        }
        String finalUrl = url;
        try {
            String[] split = url.split("\\?");
            if (split.length == 2) {
                finalUrl = split[0];
                finalUrl += "?";
                String[] urlParams = split[1].split("&");
                for (int i = 0; i < urlParams.length; i++) {
                    String[] param = urlParams[i].split("=");
                    if (i != 0) {
                        finalUrl += "&";
                    }
                    finalUrl += param[0] + "=" + redactValue;
                }
            }

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "unable to redact query params");
        }
        return finalUrl;
    }

    private static Map<String, List<String>> handleHeaders(Map<String, List<String>> responseHeaders, RedactionType redactionType) {
        
        Map<String, List<String>> tempHeaders = new HashMap<>();

        if(redactionType == RedactionType.REDACT_ALL || redactionType == RedactionType.REDACT_BY_API_COLLECTION){
            try{
            for (String header : responseHeaders.keySet()) {
                if (header.equalsIgnoreCase("cookie")) {
                    String cookie = redactCookie(responseHeaders, header);
                    tempHeaders.put(header, Collections.singletonList(cookie));
                    continue;
                }else if (header.equalsIgnoreCase("authorization")){
                    tempHeaders.put(header, Collections.singletonList("Bearer " + redactValue));
                    continue;
                }
                tempHeaders.put(header, Collections.singletonList(redactValue));
            }
            } catch (Exception e){
                loggerMaker.errorAndAddToDb(e, "unable to redact all headers");
            }
            return tempHeaders;
        }
        Set<Map.Entry<String, List<String>>> entries = responseHeaders.entrySet();
        for(Map.Entry<String, List<String>> entry : entries){
            String key = entry.getKey();
            List<String> values = entry.getValue();
            SingleTypeInfo.SubType subType = KeyTypes.findSubType(values.get(0), key, null);
            if(SingleTypeInfo.isRedacted(subType.getName())){
                if (key.equalsIgnoreCase("cookie")) {
                    String cookie = redactCookie(responseHeaders, key);
                    tempHeaders.put(key, Collections.singletonList(cookie));
                    continue;
                }else if (key.equalsIgnoreCase("authorization")){
                    tempHeaders.put(key, Collections.singletonList("Bearer " + redactValue));
                    continue;
                }
                tempHeaders.put(key, Collections.singletonList(redactValue));
            }
        }
        return tempHeaders;
    }


    public static void redactHttpResponseParam(HttpResponseParams httpResponseParams, RedactionType redactionType) throws Exception {
        // response headers
        Map<String, List<String>> responseHeaders = httpResponseParams.getHeaders();
        if (responseHeaders == null) responseHeaders = new HashMap<>();
        responseHeaders = handleHeaders(responseHeaders, redactionType);
        httpResponseParams.setHeaders(responseHeaders);
        // response payload
        String responsePayload = httpResponseParams.getPayload();
        if (responsePayload == null) responsePayload = "{}";
        try {
            JsonParser jp = factory.createParser(responsePayload);
            JsonNode node = mapper.readTree(jp);
            change(null, node, redactValue, redactionType, false);
            if (node != null) {
                responsePayload = node.toString();
            } else {
                responsePayload = "{}";
            }
        } catch (Exception e) {
            responsePayload = "{}";
        }
        httpResponseParams.setPayload(responsePayload);
        // request headers
        Map<String, List<String>> requestHeaders = httpResponseParams.requestParams.getHeaders();
        if (requestHeaders == null) requestHeaders = new HashMap<>();
        requestHeaders = handleHeaders(requestHeaders, redactionType);
        httpResponseParams.getRequestParams().setHeaders(requestHeaders);
        // request payload
        String requestPayload = httpResponseParams.requestParams.getPayload();
        //query params
        String url = handleQueryParams(httpResponseParams.requestParams.getURL(), redactionType, redactValue);
        httpResponseParams.requestParams.setUrl(url);
        if (requestPayload == null) requestPayload = "{}";
        try {
            // TODO: support subtype/collection wise redact for graphql
            boolean isGraphqlModified = false;
            if (redactionType != RedactionType.NONE) {
                try {
                    requestPayload = GraphQLUtils.getUtils().modifyGraphqlStaticArguments(requestPayload, redactValue);
                    isGraphqlModified = true;
                } catch(Exception e){
                    loggerMaker.infoAndAddToDb("query key not graphql, working as usual");
                }
            }
            JsonParser jp = factory.createParser(requestPayload);
            JsonNode node = mapper.readTree(jp);
            change(null, node, redactValue, redactionType, isGraphqlModified);
            if (node != null) {
                requestPayload= node.toString();
            } else {
                requestPayload = "{}";
            }
        } catch (Exception e) {
            requestPayload = "{}";
        }
        httpResponseParams.requestParams.setPayload(requestPayload);
        // ip
        if(redactionType == RedactionType.REDACT_ALL || redactionType == RedactionType.REDACT_BY_API_COLLECTION) {
            httpResponseParams.setSourceIP(redactValue);
        }
    }
    
    public static void change(String parentName, JsonNode parent, String newValue, RedactionType redactionType, boolean isGraphqlModified) {
        if (parent == null) return;

        if (parent.isArray()) {
            ArrayNode arrayNode = (ArrayNode) parent;
            for(int i = 0; i < arrayNode.size(); i++) {
                JsonNode arrayElement = arrayNode.get(i);
                if (arrayElement.isValueNode()) {
                    if(redactionType == RedactionType.REDACT_ALL || redactionType == RedactionType.REDACT_BY_API_COLLECTION){
                        arrayNode.set(i, new TextNode(redactValue));
                    } else{
                        SingleTypeInfo.SubType subType = KeyTypes.findSubType(arrayElement.asText(), parentName, null);
                        if(SingleTypeInfo.isRedacted(subType.getName())){
                            arrayNode.set(i, new TextNode(newValue));
                        }
                    }
                } else {
                    change(parentName, arrayElement, newValue, redactionType, isGraphqlModified);
                }
            }
        } else {
            Iterator<String> fieldNames = parent.fieldNames();
            while(fieldNames.hasNext()) {
                String f = fieldNames.next();
                JsonNode fieldValue = parent.get(f);
                if (fieldValue.isValueNode()) {
                    if((redactionType == RedactionType.REDACT_ALL || redactionType == RedactionType.REDACT_BY_API_COLLECTION) && !(isGraphqlModified && f.equalsIgnoreCase(HttpResponseParams.QUERY))){
                        ((ObjectNode) parent).put(f, newValue);
                    }
                    else {
                        SingleTypeInfo.SubType subType = KeyTypes.findSubType(fieldValue.asText(), f, null);
                        if (SingleTypeInfo.isRedacted(subType.getName())) {
                            ((ObjectNode) parent).put(f, newValue);
                        }
                    }

                } else {
                    change(f, fieldValue, newValue, redactionType, isGraphqlModified);
                }
            }
        }

    }

    public static String convertOriginalReqRespToString(OriginalHttpRequest request, OriginalHttpResponse response)  {
        BasicDBObject req = new BasicDBObject();
        if (request != null) {
            req.put("url", request.getUrl());
            req.put("method", request.getMethod());
            req.put("type", request.getType());
            req.put("queryParams", request.getQueryParams());
            req.put("body", request.getBody());
            req.put("headers", convertHeaders(request.getHeaders()));
        }

        BasicDBObject resp = new BasicDBObject();
        if (response != null) {
            resp.put("statusCode", response.getStatusCode());
            resp.put("body", response.getBody());
            resp.put("headers", convertHeaders(response.getHeaders()));
        }

        BasicDBObject ret = new BasicDBObject();
        ret.put("request", req);
        ret.put("response", resp);

        return ret.toString();
    }

    public static String convertHttpRespToOriginalString(HttpResponseParams httpResponseParams) throws JsonProcessingException {
        Map<String,Object> m = new HashMap<>();
        HttpRequestParams httpRequestParams = httpResponseParams.getRequestParams();

        m.put("method", httpRequestParams.getMethod());
        m.put("path", httpRequestParams.getURL());
        m.put("type",httpResponseParams.type);
        m.put("requestHeaders", convertHeaders(httpRequestParams.getHeaders()));

        m.put("requestPayload", httpRequestParams.getPayload());
        m.put("akto_vxlan_id", httpRequestParams.getApiCollectionId());
        m.put("statusCode", httpResponseParams.statusCode+"");
        m.put("status", httpResponseParams.status);
        m.put("responseHeaders", convertHeaders(httpResponseParams.getHeaders()));

        m.put("responsePayload", httpResponseParams.getPayload());
        m.put("time", httpResponseParams.getTime() + "");
        m.put("akto_account_id", httpResponseParams.getAccountId() + "");
        m.put("ip", httpResponseParams.getSourceIP());
        m.put("destIp", httpResponseParams.getDestIP());
        m.put("direction", httpResponseParams.getDirection());
        m.put("is_pending", httpResponseParams.getIsPending() + "");
        m.put("source", httpResponseParams.getSource());

        return mapper.writeValueAsString(m);
    }

}


