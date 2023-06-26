package com.akto.utils;

import com.akto.analyser.ResourceAnalyser;
import com.akto.dao.ThirdPartyAccessDao;
import com.akto.dao.context.Context;
import com.akto.dao.AccountSettingsDao;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.third_party_access.Credential;
import com.akto.dto.third_party_access.PostmanCredential;
import com.akto.dto.third_party_access.ThirdPartyAccess;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.listener.KafkaListener;
import com.akto.listener.RuntimeListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.APICatalogSync;
import com.akto.testing.ApiExecutor;
import com.akto.testing.TestExecutor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.Timestamp;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mongodb.client.model.Filters;

import static com.akto.utils.RedactSampleData.convertHeaders;


public class Utils {

    private static final LoggerMaker loggerMaker = new LoggerMaker(Utils.class);
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

    public static boolean isValidURL(String url) {
        try {
            new URL(url).toURI();
            return true;
        } catch (MalformedURLException | URISyntaxException e) {
            return false;
        }
    }

    public static String replaceVariables(String payload, Map<String, String> variableMap) {
        String regex = "\\{\\{(.*?)\\}\\}";
        Pattern p = Pattern.compile(regex);

        // replace with values
        Matcher matcher = p.matcher(payload);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (key == null) continue;
            if (!variableMap.containsKey(key)) {
                loggerMaker.infoAndAddToDb("Missed: " + key, LogDb.DASHBOARD);
                continue;
            }
            String value = variableMap.get(key);
            if (value == null) value = "null";

            String val = value.toString();
            matcher.appendReplacement(sb, "");
            sb.append(val);
        }

        matcher.appendTail(sb);

        return sb.toString();
    }
    
    public static Map<String, String> convertApiInAktoFormat(JsonNode apiInfo, Map<String, String> variables, String accountId, boolean allowReplay) {
        try {
            JsonNode request = apiInfo.get("request");
            String apiName = apiInfo.get("name").asText();

            Map<String, String> result = new HashMap<>();
            result.put("akto_account_id", accountId);
            result.put("path", getPath(request, variables));

            JsonNode methodObj = request.get("method");
            if (methodObj == null) throw new Exception("No method field exists");
            result.put("method", methodObj.asText());

            ArrayNode requestHeadersNode = (ArrayNode) request.get("header");
            Map<String, String> requestHeadersMap = getHeaders(requestHeadersNode, variables);
            String requestHeadersString =  mapper.writeValueAsString(requestHeadersMap);
            result.put("requestHeaders", requestHeadersString);

            JsonNode bodyNode = request.get("body");
            String requestPayload = bodyNode != null ?  bodyNode.asText() : "";
            requestPayload = replaceVariables(requestPayload, variables);

            JsonNode responseNode = apiInfo.get("response");
            JsonNode response = responseNode != null && responseNode.has(0) ?  responseNode.get(0): null;

            String responseHeadersString;
            String responsePayload;
            String statusCode;
            String status;

            if (response == null) {
                if (allowReplay) {
                    Map<String, List<String>> reqHeadersListMap = new HashMap<>();
                    for (String key: requestHeadersMap.keySet()) {
                        reqHeadersListMap.put(key, Collections.singletonList(requestHeadersMap.get(key)));
                    }

                    OriginalHttpRequest originalHttpRequest = new OriginalHttpRequest(result.get("path"), "", result.get("method"), requestPayload, reqHeadersListMap , "http");
                    try {
                        OriginalHttpResponse res = ApiExecutor.sendRequest(originalHttpRequest, true, null);
                        responseHeadersString = convertHeaders(res.getHeaders());
                        responsePayload =  res.getBody();
                        statusCode =  res.getStatusCode()+"";
                        status =  "";
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb("Error while making request for " + originalHttpRequest.getFullUrlWithParams() + " : " + e.toString(), null);
                        return null;
                    }
                } else {
                    return null;
                }
            } else {
                Map<String, String> responseHeadersMap = getHeaders((ArrayNode) response.get("header"), variables);
                responseHeadersString = mapper.writeValueAsString(responseHeadersMap);

                JsonNode responsePayloadNode = response.get("body");
                responsePayload = responsePayloadNode != null ? responsePayloadNode.asText() : "";

                JsonNode statusCodeNode = response.get("code");
                statusCode = statusCodeNode != null ? statusCodeNode.asText() : "0";

                JsonNode statusNode = response.get("status");
                status = statusNode != null ? statusNode.asText() : "";
            }

            result.put("responseHeaders", responseHeadersString);
            result.put("responsePayload", responsePayload);
            result.put("statusCode", statusCode);
            result.put("status", status);
            result.put("requestPayload", requestPayload);
            result.put("ip", "null");
            result.put("time", Context.now()+"");
            result.put("type", "http");
            result.put("source", "POSTMAN");

            return result;
        } catch (Exception e){
            loggerMaker.errorAndAddToDb(String.format("Failed to convert postman obj to Akto format : %s", e.toString()), LogDb.DASHBOARD);
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

    public static String getPath(JsonNode request, Map<String, String> variables) throws Exception {
        JsonNode urlObj = request.get("url");
        if (urlObj == null) throw new Exception("URL field doesn't exists");
        JsonNode raw = urlObj.get("raw");
        if (raw == null) throw new Exception("Raw field doesn't exists");
        String url = raw.asText();
        return replaceVariables(url, variables);
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


    private static Map<String, String> getHeaders(ArrayNode headers, Map<String, String> variables){
        Map<String, String> result = new HashMap<>();
        if (headers == null) return result;
        for(JsonNode node: headers){
            String key = node.get("key").asText().toLowerCase();
            key = replaceVariables(key,variables);

            String value = node.get("value").asText();
            value = replaceVariables(value, variables);

            result.put(key, value);
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
            SingleTypeInfo.fetchCustomDataTypes(); //todo:
            responses = com.akto.runtime.Main.filterBasedOnHeaders(responses, AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter()));
            APICatalogSync apiCatalogSync = RuntimeListener.httpCallParser.syncFunction(responses, true, false);
            RuntimeListener.aktoPolicyNew.main(responses,true, false);
            for (HttpResponseParams responseParams: responses)  {
                responseParams.requestParams.getHeaders().put("x-forwarded-for", Collections.singletonList("127.0.0.1"));
                RuntimeListener.resourceAnalyser.analyse(responseParams);
            }
            RuntimeListener.resourceAnalyser.syncWithDb();
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
