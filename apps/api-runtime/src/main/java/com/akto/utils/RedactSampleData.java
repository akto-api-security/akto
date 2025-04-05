package com.akto.utils;

import com.akto.dto.*;
import com.akto.dto.type.KeyTypes;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.graphql.GraphQLUtils;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.policies.AuthPolicy;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.*;

import static com.akto.dto.RawApi.convertHeaders;
import static com.akto.runtime.utils.Utils.parseCookie;

public class RedactSampleData {
    static ObjectMapper mapper = new ObjectMapper();
    static JsonFactory factory = mapper.getFactory();
    private static final LoggerMaker loggerMaker = new LoggerMaker(RedactSampleData.class, LogDb.TESTING);

    public static final String redactValue = "****";

    public static String redactIfRequired(String sample, boolean accountLevelRedact, boolean apiCollectionLevelRedact) throws Exception {
        HttpResponseParams httpResponseParams = HttpCallParser.parseKafkaMessage(sample);
        HttpResponseParams.Source source = httpResponseParams.getSource();
        if(source.equals(HttpResponseParams.Source.HAR) || source.equals(HttpResponseParams.Source.PCAP)) return sample;
        return redact(httpResponseParams, accountLevelRedact || apiCollectionLevelRedact);
    }

    public static String redactDataTypes(String sample) throws Exception{
        return redact(HttpCallParser.parseKafkaMessage(sample), false);
    }

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

    private static void handleHeaders(Map<String, List<String>> responseHeaders, boolean redactAll) {
        if(redactAll){
            for (String header : responseHeaders.keySet()) {
                if (header.equalsIgnoreCase(AuthPolicy.COOKIE_NAME)) {
                    String cookie = redactCookie(responseHeaders, header);
                    responseHeaders.put(header, Collections.singletonList(cookie));
                    continue;
                }
                responseHeaders.put(header, Collections.singletonList(redactValue));
            }
            return;
        }
        Set<Map.Entry<String, List<String>>> entries = responseHeaders.entrySet();
        for(Map.Entry<String, List<String>> entry : entries){
            String key = entry.getKey();
            List<String> values = entry.getValue();
            SingleTypeInfo.SubType subType = KeyTypes.findSubType(values.get(0), key, null);
            if(SingleTypeInfo.isRedacted(subType.getName())){
                if (key.equalsIgnoreCase(AuthPolicy.COOKIE_NAME)) {
                    String cookie = redactCookie(responseHeaders, key);
                    responseHeaders.put(key, Collections.singletonList(cookie));
                    continue;
                }
                responseHeaders.put(key, Collections.singletonList(redactValue));
            }
        }
    }

    // never use this function directly. This alters the httpResponseParams
    public static String redact(HttpResponseParams httpResponseParams, final boolean redactAll) throws Exception {
        // response headers
        Map<String, List<String>> responseHeaders = httpResponseParams.getHeaders();
        if (responseHeaders == null) responseHeaders = new HashMap<>();
        handleHeaders(responseHeaders, redactAll);

        // response payload
        String responsePayload = httpResponseParams.getPayload();
        if (responsePayload == null) responsePayload = "{}";
        try {
            JsonParser jp = factory.createParser(responsePayload);
            JsonNode node = mapper.readTree(jp);
            change(null, node, redactValue, redactAll, false);
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
        handleHeaders(requestHeaders, redactAll);

        // request payload
        String requestPayload = httpResponseParams.requestParams.getPayload();
        if (requestPayload == null) requestPayload = "{}";
        try {
            // TODO: support subtype/collection wise redact for graphql
            boolean isGraphqlModified = false;
            if (redactAll) {
                try {
                    requestPayload = GraphQLUtils.getUtils().modifyGraphqlStaticArguments(requestPayload, redactValue);
                    isGraphqlModified = true;
                } catch(Exception e){
                    loggerMaker.infoAndAddToDb("query key not graphql, working as usual");
                }
            }
            JsonParser jp = factory.createParser(requestPayload);
            JsonNode node = mapper.readTree(jp);
            change(null, node, redactValue, redactAll, isGraphqlModified);
            if (node != null) {
                requestPayload= node.toString();
            } else {
                requestPayload = "{}";
            }
        } catch (Exception e) {
            requestPayload = "{}";
        }

        httpResponseParams.requestParams.setPayload(requestPayload);

        return convertHttpRespToOriginalString(httpResponseParams);

    }

    public static void change(String parentName, JsonNode parent, String newValue, boolean redactAll, boolean isGraphqlModified) {
        if (parent == null) return;

        if (parent.isArray()) {
            ArrayNode arrayNode = (ArrayNode) parent;
            for(int i = 0; i < arrayNode.size(); i++) {
                JsonNode arrayElement = arrayNode.get(i);
                if (arrayElement.isValueNode()) {
                    if(redactAll){
                        arrayNode.set(i, new TextNode(redactValue));
                    } else{
                        SingleTypeInfo.SubType subType = KeyTypes.findSubType(arrayElement.asText(), parentName, null);
                        if(SingleTypeInfo.isRedacted(subType.getName())){
                            arrayNode.set(i, new TextNode(newValue));
                        }
                    }
                } else {
                    change(parentName, arrayElement, newValue, redactAll, isGraphqlModified);
                }
            }
        } else {
            Iterator<String> fieldNames = parent.fieldNames();
            while(fieldNames.hasNext()) {
                String f = fieldNames.next();
                JsonNode fieldValue = parent.get(f);
                if (fieldValue.isValueNode()) {
                    if(redactAll && !(isGraphqlModified && f.equalsIgnoreCase(GraphQLUtils.QUERY))){
                        ((ObjectNode) parent).put(f, newValue);
                    }
                    else {
                        SingleTypeInfo.SubType subType = KeyTypes.findSubType(fieldValue.asText(), f, null);
                        if (SingleTypeInfo.isRedacted(subType.getName())) {
                            ((ObjectNode) parent).put(f, newValue);
                        }
                    }

                } else {
                    change(f, fieldValue, newValue, redactAll, isGraphqlModified);
                }
            }
        }

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

