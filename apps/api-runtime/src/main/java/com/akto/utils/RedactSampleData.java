package com.akto.utils;

import com.akto.dto.*;
import com.akto.dto.type.KeyTypes;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.parsers.HttpCallParser;
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

public class RedactSampleData {
    static ObjectMapper mapper = new ObjectMapper();
    static JsonFactory factory = mapper.getFactory();

    public static final String redactValue = "****";

    public static String redactIfRequired(String sample, boolean accountLevelRedact, boolean apiCollectionLevelRedact) throws Exception {
        HttpResponseParams httpResponseParams = HttpCallParser.parseKafkaMessage(sample);
        HttpResponseParams.Source source = httpResponseParams.getSource();
        if(source.equals(HttpResponseParams.Source.HAR) || source.equals(HttpResponseParams.Source.PCAP)) return sample;
        if(accountLevelRedact || apiCollectionLevelRedact) return redact(httpResponseParams);
        return redactMarkedDataTypes(httpResponseParams);
    }

    public static String redact(String sample) throws Exception {
        HttpResponseParams httpResponseParams = HttpCallParser.parseKafkaMessage(sample);
        return redact(httpResponseParams);
    }

    public static String redactMarkedDataTypes(String sample) throws Exception {
        HttpResponseParams httpResponseParams = HttpCallParser.parseKafkaMessage(sample);
        return redactMarkedDataTypes(httpResponseParams);
    }

    public static String redactMarkedDataTypes(HttpResponseParams httpResponseParams) throws Exception{

        Map<String, List<String>> responseHeaders = httpResponseParams.getHeaders();
        if (responseHeaders == null) responseHeaders = new HashMap<>();
        handleHeaders(responseHeaders);

        String responsePayload = httpResponseParams.getPayload();
        if (responsePayload == null) responsePayload = "{}";
        try {
            JsonParser jp = factory.createParser(responsePayload);
            JsonNode node = mapper.readTree(jp);
            changeRedactedDataType(node, redactValue);
            if (node != null) {
                responsePayload = node.toString();
            } else {
                responsePayload = "{}";
            }
        } catch (Exception e) {
            responsePayload = "{}";
        }
        httpResponseParams.setPayload(responsePayload);

        Map<String, List<String>> requestHeaders = httpResponseParams.requestParams.getHeaders();
        if (requestHeaders == null) requestHeaders = new HashMap<>();
        handleHeaders(requestHeaders);

        String requestPayload = httpResponseParams.requestParams.getPayload();
        if (requestPayload == null) requestPayload = "{}";
        try {
            JsonParser jp = factory.createParser(requestPayload);
            JsonNode node = mapper.readTree(jp);
            changeRedactedDataType(node, redactValue);
            if (node != null) {
                requestPayload= node.toString();
            } else {
                requestPayload = "{}";
            }
        } catch (Exception e) {
            requestPayload = "{}";
        }

        httpResponseParams.requestParams.setPayload(requestPayload);

        //TODO confirm if this is required or not
        httpResponseParams.setSourceIP(redactValue);

        return convertHttpRespToOriginalString(httpResponseParams);
    }

    private static void handleHeaders(Map<String, List<String>> responseHeaders) {
        Set<Map.Entry<String, List<String>>> entries = responseHeaders.entrySet();
        for(Map.Entry<String, List<String>> entry : entries){
            String key = entry.getKey();
            List<String> values = entry.getValue();
            SingleTypeInfo.SubType subType = KeyTypes.findSubType(values.get(0), values.get(0), null);
            if(SingleTypeInfo.isRedacted(subType.getName())){
                responseHeaders.put(key, Collections.singletonList(redactValue));
            }
        }
    }

    // never use this function directly. This alters the httpResponseParams
    public static String redact(HttpResponseParams httpResponseParams) throws Exception {
        // response headers
        Map<String, List<String>> responseHeaders = httpResponseParams.getHeaders();
        if (responseHeaders == null) responseHeaders = new HashMap<>();
        responseHeaders.replaceAll((n, v) -> Collections.singletonList(redactValue));

        // response payload
        String responsePayload = httpResponseParams.getPayload();
        if (responsePayload == null) responsePayload = "{}";
        try {
            JsonParser jp = factory.createParser(responsePayload);
            JsonNode node = mapper.readTree(jp);
            change(node, redactValue);
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
        requestHeaders.replaceAll((n, v) -> Collections.singletonList(redactValue));

        // request payload
        String requestPayload = httpResponseParams.requestParams.getPayload();
        if (requestPayload == null) requestPayload = "{}";
        try {
            JsonParser jp = factory.createParser(requestPayload);
            JsonNode node = mapper.readTree(jp);
            change(node, redactValue);
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
        httpResponseParams.setSourceIP(redactValue);

        return convertHttpRespToOriginalString(httpResponseParams);

    }

    public static void change(JsonNode parent, String newValue) {
        if (parent == null) return;

        if (parent.isArray()) {
            ArrayNode arrayNode = (ArrayNode) parent;
            for(int i = 0; i < arrayNode.size(); i++) {
                JsonNode arrayElement = arrayNode.get(i);
                if (arrayElement.isValueNode()) {
                    arrayNode.set(i, new TextNode(newValue));
                } else {
                    change(arrayElement, newValue);
                }
            }
        } else {
            Iterator<String> fieldNames = parent.fieldNames();
            while(fieldNames.hasNext()) {
                String f = fieldNames.next();
                JsonNode fieldValue = parent.get(f);
                if (fieldValue.isValueNode()) {
                    ((ObjectNode) parent).put(f, newValue);
                } else {
                    change(fieldValue, newValue);
                }
            }
        }

    }

    public static void changeRedactedDataType(JsonNode parent, String newValue) {
        if (parent == null) return;

        if (parent.isArray()) {
            ArrayNode arrayNode = (ArrayNode) parent;
            for(int i = 0; i < arrayNode.size(); i++) {
                JsonNode arrayElement = arrayNode.get(i);
                if (arrayElement.isValueNode()) {
                    SingleTypeInfo.SubType subType = KeyTypes.findSubType(arrayElement.asText(), arrayElement.asText(), null);
                    if(SingleTypeInfo.isRedacted(subType.getName())){
                        arrayNode.set(i, new TextNode(newValue));
                    }
                } else {
                    changeRedactedDataType(arrayElement, newValue);
                }
            }
        } else {
            Iterator<String> fieldNames = parent.fieldNames();
            while(fieldNames.hasNext()) {
                String f = fieldNames.next();
                JsonNode fieldValue = parent.get(f);
                if (fieldValue.isValueNode()) {
                    SingleTypeInfo.SubType subType = KeyTypes.findSubType(fieldValue.asText(), fieldValue.asText(), null);
                    if(SingleTypeInfo.isRedacted(subType.getName())){
                        ((ObjectNode) parent).put(f, newValue);
                    }

                } else {
                    changeRedactedDataType(fieldValue, newValue);
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
        m.put("is_pending", httpResponseParams.getIsPending() + "");
        m.put("source", httpResponseParams.getSource());

        return mapper.writeValueAsString(m);
    }

    public static String convertHeaders(Map<String, List<String>> headers) {
        Map<String, String> headerMap = new HashMap<>();
        if (headers == null) return "{}";

        for (String h: headers.keySet()) {
            List<String> values = headers.get(h);
            if (values == null) continue;
            headerMap.put(h, String.join(";",values));
        }

        try {
            return mapper.writeValueAsString(headerMap);
        } catch (JsonProcessingException e) {
            ;
            return "{}";
        }
    }
}

