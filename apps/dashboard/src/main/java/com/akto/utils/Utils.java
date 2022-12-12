package com.akto.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Utils {

    private static final Logger logger = LoggerFactory.getLogger(Utils.class);
    private final static ObjectMapper mapper = new ObjectMapper();
    public static Map<String, String> getVariableMap(ArrayNode variables){
        Map<String,String> result = new HashMap<>();
        if(variables == null){
            return result;
        }
        for(JsonNode variable : variables){
            result.put(variable.get("key").asText(), variable.get("value").asText());
        }
        return result;
    }
    
    public static Map<String, String> convertApiInAktoFormat(JsonNode apiInfo, Map<String, String> variables, String accountId) {
        try {
            JsonNode request = apiInfo.get("request");
            JsonNode response = apiInfo.has("response") ?  apiInfo.get("response").get(0): null;
            String apiName = apiInfo.get("name").asText();
            if(response == null){
                logger.info("There are no responses for this api {}, skipping this", apiName);
                return null;
            }
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());

            Map<String, String> result = new HashMap<>();
            result.put("akto_account_id", accountId);
            result.put("path", getPath(request, variables));
            Map<String, String> requestHeadersMap = getHeaders((ArrayNode) request.get("header"));
            Map<String, String> responseHeadersMap = getHeaders((ArrayNode) response.get("header"));
            try {
                result.put("requestHeaders", mapper.writeValueAsString(requestHeadersMap));
                result.put("responseHeaders", mapper.writeValueAsString(responseHeadersMap));
            } catch (JsonProcessingException e) {
                logger.error("Error while processing request/response headers", e);
            }

            String contentType = getContentType(request, response, requestHeadersMap);
            String requestPayload;
            if(contentType.contains("json")){
                String body = response.get("body").asText();
                if(body == null){
                    body = "{}";
                }
                if(body.startsWith("[")){
                    requestPayload = body;
                } else {
                    Map<String,Object> bodyMap = mapper.readValue(body, new TypeReference<HashMap<String,Object>>() {});
                    requestPayload = mapper.writeValueAsString(bodyMap);
                }
            } else if(contentType.contains("x-www-form-urlencoded")){
                String postText = response.get("body").asText();
                if (postText == null) {
                    postText = "";
                }
                requestPayload = postText;
            } else {
                logger.info("Unsupported content type {} for api {}",contentType, apiName);
                return null;
            }

            result.put("method", request.get("method").asText());
            result.put("requestPayload", requestPayload);
            result.put("responsePayload", response.get("body").asText());
            result.put("ip", "null");
            result.put("time", Long.toString(timestamp.getTime() / 1000L));
            result.put("statusCode", response.get("code").asText());
            result.put("type", "http"); // TODO discuss with Ankush / Avneesh
            result.put("status", response.get("status").asText());

            result.put("contentType", contentType); // TODO discuss with Ankush / Avneesh
            result.put("source", "POSTMAN");

            return result;
        } catch (Exception e){
            logger.error("Failed to convert postman obj to Akto format", e);
            return null;
        }
    }

    private static String getContentType(JsonNode request, JsonNode response, Map<String, String> responseHeadersMap) {
        if(responseHeadersMap.containsKey("content-type")){
            return responseHeadersMap.get("content-type");
        }
        if(request.has("body") && request.get("body").has("options") &&
         request.get("body").get("options").has("raw") && 
         request.get("body").get("options").get("raw").has("language")){
            return request.get("body").get("options").get("raw").get("language").asText();
        }
        return response.get("_postman_previewlanguage").asText();
    }

    public static String getPath(JsonNode request, Map<String, String> variables){
        JsonNode urlObj = request.get("url");
        String url = urlObj.get("raw").asText();
        if((url.contains("{{") && url.contains("}}")) && !variables.isEmpty()){
            String host = process((ArrayNode) urlObj.get("host"), ".", variables);
            String path = process((ArrayNode) urlObj.get("path"), "/", variables);
            url = "";
            if(urlObj.has("protocol") && urlObj.get("protocol").asText().length() > 0){
                url += urlObj.get("protocol").asText();
                url += "://";
            }
            url += host;
            if(urlObj.has("port") &&  urlObj.get("port").asText().length() > 0){
                url += ":" + urlObj.get("port").asText();
            }
            url += "/" + path;
        }
        return url;
    }

    public static String process(ArrayNode arrayNode, String delimiter, Map<String, String> variables){
        ArrayList<String> variableReplacedHostList = new ArrayList<>();
        for (JsonNode jsonNode : arrayNode) {
            String hostPart = jsonNode.asText();
            if (hostPart.contains("{{") && hostPart.contains("}}")) {
                hostPart = extractVariableAndReplace(hostPart, variables);
            }
            variableReplacedHostList.add(hostPart);
        }
        return String.join(delimiter, variableReplacedHostList);
    }

    private static String extractVariableAndReplace(String str, Map<String, String> variables) {
        int start = str.indexOf('{');
        int end = str.lastIndexOf('}');
        String key = str.substring(start+2, end-1);
        if(variables.containsKey(key)){
            String val = variables.get(key);
            str = str.replace("{{" + key + "}}", val);
        }
        return str;
    }


    private static Map<String, String> getHeaders(ArrayNode headers){
        Map<String, String> result = new HashMap<>();
        for(JsonNode node: headers){
            result.put(node.get("key").asText().toLowerCase(), node.get("value").asText());
        }
        return  result;
    }

    public static void fetchApisRecursively(ArrayNode items, ArrayList<JsonNode> jsonNodes) {
        if(items == null || items.size() == 0){
            return;
        }
        for(JsonNode item: items){
            if(item.has("item")){
                fetchApisRecursively( (ArrayNode) item.get("item"), jsonNodes);
            } else {
                jsonNodes.add(item);
            }
        }

    }

}
