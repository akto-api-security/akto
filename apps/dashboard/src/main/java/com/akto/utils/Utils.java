package com.akto.utils;

import com.akto.dao.AccountSettingsDao;
import com.akto.dao.ThirdPartyAccessDao;
import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.akto.dependency.DependencyAnalyser;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.dependency_flow.DependencyFlow;
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
import com.akto.runtime.policies.AktoPolicyNew;
import com.akto.testing.ApiExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import org.apache.commons.lang3.StringUtils;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.akto.dto.RawApi.convertHeaders;


public class Utils {

    private static final LoggerMaker loggerMaker = new LoggerMaker(Utils.class);
    private final static ObjectMapper mapper = new ObjectMapper();

    public static Map<String, String> getAuthMap(JsonNode auth, Map<String, String> variableMap) {
        Map<String,String> result = new HashMap<>();

        if (auth == null) {
            return result;
        }

        try {
            String authType = auth.get("type").asText().toLowerCase();

            switch (authType) {
                case "bearer":
                    ArrayNode authParams = (ArrayNode) auth.get("bearer");
                    for (JsonNode authHeader : authParams) {
                        String tokenKey = authHeader.get("key").asText();
                        if (tokenKey.equals("token")) {
                            String tokenValue = authHeader.get("value").asText();
                            String replacedTokenValue = replaceVariables(tokenValue, variableMap);

                            result.put("Authorization", "Bearer " + replacedTokenValue);

                        }
                    }
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Unable to parse auth from postman file: " + e.getMessage(), LogDb.DASHBOARD);
        }

        return result;
    }

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
    
    public static Map<String, String> convertApiInAktoFormat(JsonNode apiInfo, Map<String, String> variables, String accountId, boolean allowReplay, Map<String, String> authMap) {
        try {
            JsonNode request = apiInfo.get("request");

            String apiName = apiInfo.get("name").asText();

            JsonNode authApiNode = request.get("auth");
            if (authApiNode != null) {
                authMap = getAuthMap(authApiNode, variables);
            }

            Map<String, String> result = new HashMap<>();
            result.put("akto_account_id", accountId);
            result.put("path", getPath(request, variables));

            JsonNode methodObj = request.get("method");
            if (methodObj == null) throw new Exception("No method field exists");
            result.put("method", methodObj.asText());

            ArrayNode requestHeadersNode = (ArrayNode) request.get("header");
            Map<String, String> requestHeadersMap = getHeaders(requestHeadersNode, variables);
            requestHeadersMap.putAll(authMap);
            String requestHeadersString =  mapper.writeValueAsString(requestHeadersMap);
            result.put("requestHeaders", requestHeadersString);

            JsonNode bodyNode = request.get("body");
            String requestPayload = extractRequestPayload(bodyNode);
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
                        loggerMaker.errorAndAddToDb(e,"Error while making request for " + originalHttpRequest.getFullUrlWithParams() + " : " + e.toString(), LogDb.DASHBOARD);
                        return null;
                    }
                } else {
                    return null;
                }
            } else {
                JsonNode respHeaders = response.get("header");
                Map<String, String> responseHeadersMap = new HashMap<>();
                if (respHeaders == null) {
                    responseHeadersMap = getHeaders((ArrayNode) response.get("header"), variables);
                }

                JsonNode originalRequest = response.get("originalRequest");

                if (originalRequest != null) {
                    result.put("path", getPath(originalRequest, variables));
                }

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
            loggerMaker.errorAndAddToDb(e, String.format("Failed to convert postman obj to Akto format : %s", e.toString()), LogDb.DASHBOARD);
            return null;
        }
    }

    public static String extractRequestPayload(JsonNode bodyNode) {
        if(bodyNode == null || bodyNode.isNull()){
            return "";
        }
        String mode = bodyNode.get("mode").asText();
        if(mode.equals("none")){
            return "";
        }
        if(mode.equals("raw")){
            return bodyNode.get("raw").asText();
        }
        if(mode.equals("formdata")){
            ArrayNode formdata = (ArrayNode) bodyNode.get("formdata");
            StringBuilder sb = new StringBuilder();
            for(JsonNode node : formdata){
                String type = node.get("type").asText();
                if(type.equals("file")){
                    sb.append(node.get("key").asText()).append("=").append(node.get("src").asText()).append("&");
                } else if(type.equals("text")){
                    sb.append(node.get("key").asText()).append("=").append(node.get("value").asText()).append("&");
                }
            }
            if (sb.length() > 0) sb.deleteCharAt(sb.length()-1);
            return sb.toString();
        }
        if(mode.equals("urlencoded")){
            ArrayNode urlencoded = (ArrayNode) bodyNode.get("urlencoded");
            StringBuilder sb = new StringBuilder();
            for(JsonNode node : urlencoded){
                sb.append(node.get("key").asText()).append("=").append(node.get("value").asText()).append("&");
            }
            if (sb.length() > 0) sb.deleteCharAt(sb.length()-1);
            return sb.toString();
        }
        if(mode.equals("graphql")){
            return bodyNode.get("graphql").toPrettyString();
        }
        if(mode.equals("file")){
            return bodyNode.get("file").get("src").asText();
        }
        return bodyNode.toPrettyString();
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
            String accountIdStr = responses.get(0).accountId;
            if (!StringUtils.isNumeric(accountIdStr)) {
                return;
            }

            int accountId = Integer.parseInt(accountIdStr);
            Context.accountId.set(accountId);

            SingleTypeInfo.fetchCustomDataTypes(accountId);
            AccountHTTPCallParserAktoPolicyInfo info = RuntimeListener.accountHTTPParserMap.get(accountId);
            if (info == null) { // account created after docker run
                info = new AccountHTTPCallParserAktoPolicyInfo();
                HttpCallParser callParser = new HttpCallParser("userIdentifier", 1, 1, 1, false);
                info.setHttpCallParser(callParser);
                RuntimeListener.accountHTTPParserMap.put(accountId, info);
            }


            AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
            info.getHttpCallParser().syncFunction(responses, true, false, accountSettings);
            APICatalogSync.mergeUrlsAndSave(apiCollectionId, true);
            info.getHttpCallParser().apiCatalogSync.buildFromDB(false, false);
            APICatalogSync.updateApiCollectionCount(info.getHttpCallParser().apiCatalogSync.getDbState(apiCollectionId), apiCollectionId);
            EndpointUtil.calcAndDeleteEndpoints();
            try {
                DependencyFlow dependencyFlow = new DependencyFlow();
                dependencyFlow.run();
                dependencyFlow.syncWithDb();
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e,"Exception while running dependency flow", LoggerMaker.LogDb.DASHBOARD);
            }
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

    public static <T> List<T> castList(Class<? extends T> clazz, Collection<?> rawCollection) {
        List<T> result = new ArrayList<>(rawCollection.size());
        for (Object o : rawCollection) {
            try {
                result.add(clazz.cast(o));
            } catch (ClassCastException e) {
                // skip the one that cannot be casted
            }
        }
        return result;
    }
    
    private static final Gson gson = new Gson();
    public static BasicDBObject extractJsonResponse(String message, boolean isRequest) {
        Map<String, Object> json = gson.fromJson(message, Map.class);

        String respPayload = (String) json.get(isRequest ? "requestPayload" : "responsePayload");

        if (respPayload == null || respPayload.isEmpty()) {
            respPayload = "{}";
        }

        if(respPayload.startsWith("[")) {
            respPayload = "{\"json\": "+respPayload+"}";
        }

        BasicDBObject payload;
        try {
            payload = BasicDBObject.parse(respPayload);
        } catch (Exception e) {
            payload = BasicDBObject.parse("{}");
        }

        return payload;
    }

    public static float calculateRiskValueForSeverity(String severity){
        float riskScore = 0 ;
        switch (severity) {
            case "HIGH":
                riskScore += 100;
                break;

            case "MEDIUM":
                riskScore += 10;
                break;

            case "LOW":
                riskScore += 1;
        
            default:
                break;
        }

        return riskScore;
    }

}
