package com.akto.utils;

import com.akto.dto.*;
import com.akto.dto.type.KeyTypes;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.graphql.GraphQLUtils;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.policies.AuthPolicy;
import com.akto.test_editor.Utils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.akto.dto.RawApi.convertHeaders;
import static com.akto.runtime.utils.Utils.parseCookie;
import static com.akto.util.HttpRequestResponseUtils.SOAP;
import static com.akto.util.HttpRequestResponseUtils.XML;
import static com.akto.util.HttpRequestResponseUtils.getAcceptableContentType;

public class RedactSampleData {
    static ObjectMapper mapper = new ObjectMapper();
    static JsonFactory factory = mapper.getFactory();
    private static final LoggerMaker loggerMaker = new LoggerMaker(RedactSampleData.class, LogDb.RUNTIME);

    public static final String redactValue = "****";

    public static String redactIfRequired(String sample, boolean accountLevelRedact, boolean apiCollectionLevelRedact) throws Exception {
        HttpResponseParams httpResponseParams = HttpCallParser.parseKafkaMessage(sample);
        HttpResponseParams.Source source = httpResponseParams.getSource();
        if(source.equals(HttpResponseParams.Source.PCAP)) return sample;
        return redact(httpResponseParams, (accountLevelRedact && !source.equals(HttpResponseParams.Source.HAR) && !source.equals(HttpResponseParams.Source.BURP)) || apiCollectionLevelRedact);
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

    private static String handleQueryParams(String url, boolean redactAll, String redactValue) {

        if (!redactAll || url == null || url.isEmpty()) {
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
        // check for content type in both request and response headers, if they are xml format, read payload from original payload instead of httpResponseParams
        Map<String, List<String>> responseHeaders = httpResponseParams.getHeaders();
        if (responseHeaders == null) responseHeaders = new HashMap<>();

        // request headers
        Map<String, List<String>> requestHeaders = httpResponseParams.requestParams.getHeaders();
        if (requestHeaders == null) requestHeaders = new HashMap<>();

        // check for header here for content type
        String acceptableContentType = getAcceptableContentType(responseHeaders);
        if(acceptableContentType == null) {
            acceptableContentType = getAcceptableContentType(requestHeaders);
        }
        // response payload
        String responsePayload = httpResponseParams.getPayload();
        String finalOrigReqPayload = "";
        String finalOrigResPayload = "";

        if (acceptableContentType != null && (acceptableContentType.contains(XML) || acceptableContentType.contains(SOAP))) {
            String origMessage = httpResponseParams.getOrig();
            JSONObject jsonObject = JSON.parseObject(origMessage);
            String rawRequestPayload = jsonObject.getString("requestPayload");
            String rawResponsePayload = jsonObject.getString("responsePayload");

            try {
                if (rawRequestPayload != null && !rawRequestPayload.isEmpty()) {
                    finalOrigReqPayload = redactXmlWithRegex(rawRequestPayload, redactValue, redactAll);
                }
                if(rawResponsePayload != null && !rawResponsePayload.isEmpty()){
                    finalOrigResPayload = redactXmlWithRegex(rawResponsePayload, redactValue, redactAll);
                }
            } catch (Exception e) {
                // TODO: handle exception
                e.printStackTrace();
                loggerMaker.errorAndAddToDb("Error in redacting original payloads for xml content type: " +e.getMessage());
            }

            
        }

        handleHeaders(responseHeaders, redactAll);
        handleHeaders(requestHeaders, redactAll);

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
            if (Utils.isJsonPayload(responsePayload)) {
                responsePayload = "{}";
            }
        }

        httpResponseParams.setPayload(responsePayload);

        // request payload
        String requestPayload = httpResponseParams.requestParams.getPayload();
        //query params
        String url = handleQueryParams(httpResponseParams.requestParams.getURL(), redactAll, redactValue);
        httpResponseParams.requestParams.setUrl(url);
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
            if (Utils.isJsonPayload(requestPayload)) {
                requestPayload = "{}";
            }
        }

        httpResponseParams.requestParams.setPayload(requestPayload);

        // ip
        if(redactAll) {
            httpResponseParams.setSourceIP(redactValue);
        }

        if(!finalOrigReqPayload.isEmpty() || !finalOrigResPayload.isEmpty()){
            return convertHttpRespToOriginalString(httpResponseParams, finalOrigReqPayload, finalOrigResPayload);
        }else{
            return convertHttpRespToOriginalString(httpResponseParams);
        }
        

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
                    if(redactAll && !(isGraphqlModified && f.equalsIgnoreCase(HttpResponseParams.QUERY))){
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

    private static String redactXmlWithRegex(String xmlString, String newValue, boolean redactAll) {
        if (xmlString == null || xmlString.isEmpty()) {
            return xmlString;
        }
        
        // Pattern to match XML tags and their content: <tagName>content</tagName>
        Pattern pattern = Pattern.compile("<([^>/]+)>([^<>]*)</\\1>");
        Matcher matcher = pattern.matcher(xmlString);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String tagName = matcher.group(1);
            String tagContent = matcher.group(2);
            
            // Apply redaction based on conditions
            if (redactAll) {
                // Redact all content
                matcher.appendReplacement(result, Matcher.quoteReplacement("<" + tagName + ">" + newValue + "</" + tagName + ">"));
            } else {
                // Check if this specific value needs redaction
                SingleTypeInfo.SubType subType = KeyTypes.findSubType(tagContent, tagName, null);
                if (SingleTypeInfo.isRedacted(subType.getName())) {
                    matcher.appendReplacement(result, Matcher.quoteReplacement("<" + tagName + ">" + newValue + "</" + tagName + ">"));
                } else {
                    // Keep original content
                    matcher.appendReplacement(result, Matcher.quoteReplacement("<" + tagName + ">" + tagContent + "</" + tagName + ">"));
                }
            }
        }
        
        matcher.appendTail(result);
        
        // Process attributes if needed (simplified version)
        // Pattern to match attributes: tagName attribute="value"
        Pattern attrPattern = Pattern.compile("(\\w+)\\s*=\\s*\"([^\"]*)\"");
        matcher = attrPattern.matcher(result.toString());
        StringBuffer finalResult = new StringBuffer();
        
        while (matcher.find()) {
            String attrName = matcher.group(1);
            String attrValue = matcher.group(2);
            // Apply redaction based on conditions
            if (redactAll) {
                matcher.appendReplacement(finalResult, Matcher.quoteReplacement(attrName + "=\"" + newValue + "\""));
            } else {
                SingleTypeInfo.SubType subType = KeyTypes.findSubType(attrValue, attrName, null);
                if (SingleTypeInfo.isRedacted(subType.getName())) {
                    matcher.appendReplacement(finalResult, Matcher.quoteReplacement(attrName + "=\"" + newValue + "\""));
                } else {
                    matcher.appendReplacement(finalResult, Matcher.quoteReplacement(attrName + "=\"" + attrValue + "\""));
                }
            }
        }
        
        if (finalResult.length() > 0) {
            matcher.appendTail(finalResult);
            return finalResult.toString();
        }
        
        return result.toString();
    }

    private static Map<String, Object> getFinalMap(HttpResponseParams httpResponseParams) {
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
        return m;
    }

    public static String convertHttpRespToOriginalString(HttpResponseParams httpResponseParams) throws JsonProcessingException {
        Map<String, Object> m = getFinalMap(httpResponseParams);
        return mapper.writeValueAsString(m);
    }

    private static String convertHttpRespToOriginalString(HttpResponseParams httpResponseParams, String origReqPayload, String origResPayload) throws JsonProcessingException {
        Map<String, Object> m = getFinalMap(httpResponseParams);
        m.put("originalRequestPayload", origReqPayload);
        m.put("originalResponsePayload", origResPayload);
        return mapper.writeValueAsString(m);
    }

}

