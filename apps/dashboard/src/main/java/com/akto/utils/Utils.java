package com.akto.utils;

import com.akto.analyser.ResourceAnalyser;
import com.akto.dao.ThirdPartyAccessDao;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.third_party_access.Credential;
import com.akto.dto.third_party_access.PostmanCredential;
import com.akto.dto.third_party_access.ThirdPartyAccess;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.listener.KafkaListener;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.APICatalogSync;
import com.akto.runtime.policies.AktoPolicy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.sql.Timestamp;
import java.util.*;

import com.mongodb.client.model.Filters;
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
            result.put("type", "http");
            result.put("status", response.get("status").asText());
            result.put("contentType", contentType);
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
        if(request.has("body")){
            JsonNode body = request.get("body");
            if(body.has("options")){
                JsonNode options = request.get("options");
                if(options.has("raw")){
                    JsonNode raw = request.get("raw");
                    if(raw.has("language")){
                        return raw.get("language").asText();
                    }
                }
            }
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

    public static void pushDataToKafka(int apiCollectionId, String topic, List<String> messages, List<String> errors, boolean skipKafka) throws Exception {
        List<HttpResponseParams> responses = new ArrayList<>();
        for (String message: messages){
            if (message.length() < 0.8 * KafkaListener.BATCH_SIZE_CONFIG) {
                if (!skipKafka) {
                    KafkaListener.kafka.send(message,"har_" + topic);
                } else {
                    HttpResponseParams responseParams =  HttpCallParser.parseKafkaMessage(message);
                    responseParams.getRequestParams().setApiCollectionId(apiCollectionId);
                    responses.add(responseParams);
                }
            } else {
                errors.add("Message too big size: " + message.length());
            }
        }

        if(skipKafka) {
            HttpCallParser parser = new HttpCallParser("userIdentifier", 1, 1, 1, false);
            SingleTypeInfo.fetchCustomDataTypes();
            APICatalogSync apiCatalogSync = parser.syncFunction(responses, true, false);
            AktoPolicy aktoPolicy = new AktoPolicy(parser.apiCatalogSync, false);
            aktoPolicy.main(responses, apiCatalogSync, false);
            ResourceAnalyser resourceAnalyser = new ResourceAnalyser(300_000, 0.01, 100_000, 0.01);
            for (HttpResponseParams responseParams: responses)  {
                responseParams.requestParams.getHeaders().put("x-forwarded-for", Collections.singletonList("127.0.0.1"));
                resourceAnalyser.analyse(responseParams);
            }
            resourceAnalyser.syncWithDb();
        }
    }

    public static PostmanCredential fetchPostmanCredential(int userId) {
        ThirdPartyAccess thirdPartyAccess = ThirdPartyAccessDao.instance.findOne(
                Filters.and(
                        Filters.eq("owner", userId),
                        Filters.eq("credential.type", Credential.Type.POSTMAN)
                )
        );

        if (thirdPartyAccess == null) {
            return null;
        }

        return (PostmanCredential) thirdPartyAccess.getCredential();
    }

}
