package com.akto.utils;

import com.akto.dao.ThirdPartyAccessDao;
import com.akto.dao.TrafficInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.AccountsContextDaoWithRbac;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.FilterSampleDataDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.SensitiveParamInfoDao;
import com.akto.dao.SensitiveSampleDataDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.*;
import com.akto.dto.dependency_flow.DependencyFlow;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.testing.*;
import com.akto.dto.third_party_access.Credential;
import com.akto.dto.third_party_access.PostmanCredential;
import com.akto.dto.third_party_access.ThirdPartyAccess;
import com.akto.dto.traffic.Key;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.dto.upload.FileUploadError;
import com.akto.listener.KafkaListener;
import com.akto.listener.RuntimeListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.APICatalogSync;
import com.akto.testing.ApiExecutor;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.Constants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;

import static com.akto.dto.RawApi.convertHeaders;


public class Utils {

    private static final LoggerMaker loggerMaker = new LoggerMaker(Utils.class, LogDb.DASHBOARD);
    ;
    private final static ObjectMapper mapper = new ObjectMapper();

    public static Map<String, String> getAuthMap(JsonNode auth, Map<String, String> variableMap) {
        Map<String, String> result = new HashMap<>();

        if (auth == null) {
            return result;
        }


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
                break;
            case "apikey":
                ArrayNode apikeyParams = (ArrayNode) auth.get("apikey");
                String authKeyName = "", authValueName = "";
                for (JsonNode apikeyHeader : apikeyParams) {
                    String key = apikeyHeader.get("key").asText();
                    String value = apikeyHeader.get("value").asText();

                    switch (key) {
                        case "key":
                            authKeyName = replaceVariables(value, variableMap);
                            break;
                        case "value":
                            authValueName = replaceVariables(value, variableMap);
                            break;

                        case "in":
                            if (!value.equals("header")) {
                                throw new IllegalArgumentException("Only header supported in apikey");
                            }
                            break;
                        default:
                            break;
                    }
                }

                if (authKeyName.isEmpty() || authValueName.isEmpty()) {
                    throw new IllegalArgumentException(
                            "One of  kv is empty: key=" + authKeyName + " value=" + authValueName);
                } else {
                    result.put(authKeyName, authValueName);
                }
                break;
            case "basic":
                ArrayNode basicParams = (ArrayNode) auth.get("basic");
                String basicUsername = "", basicPassword = "";
                for (JsonNode basicKeyHeader : basicParams) {
                    String key = basicKeyHeader.get("key").asText();
                    String value = basicKeyHeader.get("value").asText();
                    switch (key) {
                        case "username":
                            basicUsername = replaceVariables(value, variableMap);
                            break;
                        case "password":
                            basicPassword = replaceVariables(value, variableMap);
                            break;
                        default:
                            break;
                    }
                }

                if (basicUsername.isEmpty() || basicPassword.isEmpty()) {
                    throw new IllegalArgumentException(
                            "One of  username/password is empty: username=" + basicUsername + " password="
                                    + basicPassword);
                } else {
                    /*
                     * Base64 implementation ref: https://www.ietf.org/rfc/rfc2617.txt
                     */
                    String basicCredentials = basicUsername + ":" + basicPassword;
                    String basicEncoded = Base64.getEncoder().encodeToString(basicCredentials.getBytes());

                    String basicHeader = "Basic " + basicEncoded;
                    result.put("Authorization", basicHeader);
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported auth type: " + authType);
        }


        return result;
    }

    public static Map<String, String> getVariableMap(ArrayNode variables) {
        Map<String, String> result = new HashMap<>();
        if (variables == null) {
            return result;
        }
        for (JsonNode variable : variables) {
            if (variable.get("key") != null && variable.get("value") != null) {
                result.put(variable.get("key").asText(), variable.get("value").asText());
            }
        }
        return result;
    }

    public static boolean isValidURL(String url) {
        try {
            new URL(url).toURI();
            return true;
        } catch (MalformedURLException | URISyntaxException e) {
            Pattern pattern = Pattern.compile("\\$\\{[^}]*\\}");
            Matcher matcher = pattern.matcher(url);
            if (matcher.find()) {
                return true;
            }
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
                loggerMaker.debugAndAddToDb("Missed: " + key, LogDb.DASHBOARD);
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

    public static Pair<Map<String, String>, List<FileUploadError>> convertApiInAktoFormat(JsonNode apiInfo, Map<String, String> variables, String accountId, boolean allowReplay, Map<String, String> authMap, String miniTestingName) {
        Pair<Map<String, String>, List<String>> resp;
        List<FileUploadError> errors = new ArrayList<>();
        try {
            JsonNode request = apiInfo.get("request");

            String apiName = apiInfo.get("name").asText();

            JsonNode authApiNode = request.get("auth");
            if (authApiNode != null) {
                try {
                    authMap = getAuthMap(authApiNode, variables);
                } catch (Exception e) {
                    errors.add(new FileUploadError("Error while getting auth map for " + apiName + " : " + e.toString(), FileUploadError.ErrorType.WARNING));
                    loggerMaker.errorAndAddToDb(e, String.format("Error while getting auth map for %s : %s", apiName, e.toString()), LogDb.DASHBOARD);
                }
            }

            Map<String, String> result = new HashMap<>();
            result.put("akto_account_id", accountId);
            result.put("path", getPath(request, variables));

            JsonNode methodObj = request.get("method");
            if (methodObj == null) {
                errors.add(new FileUploadError("No method field exists", FileUploadError.ErrorType.ERROR));
                return Pair.of(null, errors);
            }
            result.put("method", methodObj.asText());

            ArrayNode requestHeadersNode = (ArrayNode) request.get("header");
            Map<String, String> requestHeadersMap = getHeaders(requestHeadersNode, variables);
            requestHeadersMap.putAll(authMap);
            String requestHeadersString = mapper.writeValueAsString(requestHeadersMap);
            result.put("requestHeaders", requestHeadersString);

            JsonNode bodyNode = request.get("body");
            String requestPayload = extractRequestPayload(bodyNode);
            requestPayload = replaceVariables(requestPayload, variables);

            JsonNode responseNode = apiInfo.get("response");
            JsonNode response = responseNode != null && responseNode.has(0) ? responseNode.get(0) : null;

            String responseHeadersString;
            String responsePayload;
            String statusCode;
            String status;

            if (response == null) {
                if (allowReplay) {
                    Map<String, List<String>> reqHeadersListMap = new HashMap<>();
                    for (String key : requestHeadersMap.keySet()) {
                        reqHeadersListMap.put(key, Collections.singletonList(requestHeadersMap.get(key)));
                    }

                    OriginalHttpRequest originalHttpRequest = new OriginalHttpRequest(result.get("path"), "", result.get("method"), requestPayload, reqHeadersListMap, "http");
                    try {
                        OriginalHttpResponse res = null;
                        if (StringUtils.isEmpty(miniTestingName)) {
                            res = ApiExecutor.sendRequest(originalHttpRequest, true, null, false, new ArrayList<>());
                        } else {
                            res = com.akto.testing.Utils.runRequestOnHybridTesting(originalHttpRequest);
                        }
                        responseHeadersString = convertHeaders(res.getHeaders());
                        responsePayload = res.getBody();
                        statusCode = res.getStatusCode() + "";
                        status = "";
                        if (res.getStatusCode() < 200 || res.getStatusCode() >= 400) {
                            throw new Exception("Found non 2XX response on replaying the API");
                        }
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e, "Error while making request for " + originalHttpRequest.getFullUrlWithParams() + " : " + e.toString(), LogDb.DASHBOARD);
                        errors.add(new FileUploadError("Error while replaying request for " + originalHttpRequest.getFullUrlWithParams() + " : " + e.toString(), FileUploadError.ErrorType.ERROR));
                        return Pair.of(null, errors);
                    }
                } else {
                    errors.add(new FileUploadError("No response field exists", FileUploadError.ErrorType.WARNING));
                    return Pair.of(null, errors);
                }
            } else {
                JsonNode respHeaders = response.get("header");
                Map<String, String> responseHeadersMap = new HashMap<>();
                if (respHeaders != null) {
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
            result.put("time", Context.now() + "");
            result.put("type", "http");
            result.put("source", "POSTMAN");
            return Pair.of(result, errors);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, String.format("Failed to convert postman obj to Akto format : %s", e.toString()), LogDb.DASHBOARD);
            errors.add(new FileUploadError("Failed to convert postman obj to Akto", FileUploadError.ErrorType.ERROR));
            return Pair.of(null, errors);
        }
    }

    public static String extractRequestPayload(JsonNode bodyNode) {
        if (bodyNode == null || bodyNode.isNull()) {
            return "";
        }
        String mode = bodyNode.get("mode").asText();
        if (mode.equals("none")) {
            return "";
        }
        if (mode.equals("raw")) {
            return bodyNode.get("raw").asText();
        }
        if (mode.equals("formdata")) {
            ArrayNode formdata = (ArrayNode) bodyNode.get("formdata");
            StringBuilder sb = new StringBuilder();
            for (JsonNode node : formdata) {
                String type = node.get("type").asText();
                if (type.equals("file")) {
                    sb.append(node.get("key").asText()).append("=").append(node.get("src").asText()).append("&");
                } else if (type.equals("text")) {
                    sb.append(node.get("key").asText()).append("=").append(node.get("value").asText()).append("&");
                }
            }
            if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
            return sb.toString();
        }
        if (mode.equals("urlencoded")) {
            ArrayNode urlencoded = (ArrayNode) bodyNode.get("urlencoded");
            StringBuilder sb = new StringBuilder();
            for (JsonNode node : urlencoded) {
                sb.append(node.get("key").asText()).append("=").append(node.get("value").asText()).append("&");
            }
            if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
            return sb.toString();
        }
        if (mode.equals("graphql")) {
            return bodyNode.get("graphql").toPrettyString();
        }
        if (mode.equals("file")) {
            return bodyNode.get("file").get("src").asText();
        }
        return bodyNode.toPrettyString();
    }

    private static String getContentType(JsonNode request, JsonNode response, Map<String, String> responseHeadersMap) {
        if (responseHeadersMap.containsKey("content-type")) {
            return responseHeadersMap.get("content-type");
        }
        if (request.has("body")) {
            JsonNode body = request.get("body");
            if (body.has("options")) {
                JsonNode options = request.get("options");
                if (options.has("raw")) {
                    JsonNode raw = request.get("raw");
                    if (raw.has("language")) {
                        return raw.get("language").asText();
                    }
                }
            }
        }
        return response.get("_postman_previewlanguage").asText();
    }

    public static String getPath(JsonNode request) throws Exception {
        return getPath(request, new HashMap<>());
    }

    public static String getPath(JsonNode request, Map<String, String> variables) throws Exception {
        JsonNode urlObj = request.get("url");
        if (urlObj == null) throw new Exception("URL field doesn't exists");
        JsonNode raw = urlObj.get("raw");
        if (raw == null) throw new Exception("Raw field doesn't exists");
        String url = raw.asText();
        return replaceVariables(url, variables);
    }

    public static String process(ArrayNode arrayNode, String delimiter, Map<String, String> variables) {
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
        String key = str.substring(start + 2, end - 1);
        if (variables.containsKey(key)) {
            String val = variables.get(key);
            str = str.replace("{{" + key + "}}", val);
        }
        return str;
    }


    private static Map<String, String> getHeaders(ArrayNode headers, Map<String, String> variables) {
        Map<String, String> result = new HashMap<>();
        if (headers == null) return result;
        for (JsonNode node : headers) {
            String key = node.get("key").asText().toLowerCase();
            key = replaceVariables(key, variables);

            String value = node.get("value").asText();
            value = replaceVariables(value, variables);

            result.put(key, value);
        }

        return result;
    }

    //TODO handle item as a json object
    public static void fetchApisRecursively(ArrayNode items, ArrayList<JsonNode> jsonNodes) {
        if (items == null || items.size() == 0) {
            return;
        }
        for (JsonNode item : items) {
            if (item.has("item")) {
                fetchApisRecursively((ArrayNode) item.get("item"), jsonNodes);
            } else {
                jsonNodes.add(item);
            }
        }

    }

    public static void pushDataToKafka(int apiCollectionId, String topic, List<String> messages, List<String> errors, boolean skipKafka, boolean takeFromMsg) throws Exception {
        pushDataToKafka(apiCollectionId, topic, messages, errors, skipKafka, takeFromMsg, false, false);
    }
    /*
     * this function is used primarily for non-automated traffic collection, like
     * postman, har and openAPI.
     * Thus, we can skip advanced traffic filters for these cases.
     */

    public static void pushDataToKafka(int apiCollectionId, String topic, List<String> messages, List<String> errors, boolean skipKafka, boolean takeFromMsg, boolean skipAdvancedFilters) throws Exception {
        pushDataToKafka(apiCollectionId, topic, messages, errors, skipKafka, takeFromMsg, skipAdvancedFilters, false);
    }

    public static void pushDataToKafka(int apiCollectionId, String topic, List<String> messages, List<String> errors, boolean skipKafka, boolean takeFromMsg, boolean skipAdvancedFilters, boolean skipMergingOnKnownStaticURLsForVersionedApis) throws Exception {
        List<HttpResponseParams> responses = new ArrayList<>();
        for (String message : messages) {
            int messageLimit = (int) Math.round(0.8 * KafkaListener.BATCH_SIZE_CONFIG);
            if (skipKafka) {
                messageLimit = messageLimit * 2;
            }
            if (message.length() < messageLimit) {
                if (!skipKafka) {
                    KafkaListener.kafka.send(message, "har_" + topic);
                } else {
                    HttpResponseParams responseParams = HttpCallParser.parseKafkaMessage(message);
                    responseParams.getRequestParams().setApiCollectionId(apiCollectionId);
                    responses.add(responseParams);
                }
            } else {
                errors.add("Message too big size: " + message.length());
            }
        }

        //todo:shivam handle resource analyser in AccountHTTPCallParserAktoPolicyInfo
        if (skipKafka) {

            int accountId = Context.accountId.get();
            if (takeFromMsg) {
                String accountIdStr = responses.get(0).accountId;
                if (!StringUtils.isNumeric(accountIdStr)) {
                    return;
                }

                accountId = Integer.parseInt(accountIdStr);
                Context.accountId.set(accountId);
            }

            SingleTypeInfo.fetchCustomDataTypes(accountId);
            AccountHTTPCallParserAktoPolicyInfo info = RuntimeListener.accountHTTPParserMap.get(accountId);
            if (info == null) { // account created after docker run
                info = new AccountHTTPCallParserAktoPolicyInfo();
                HttpCallParser callParser = new HttpCallParser("userIdentifier", 1, 1, 1, false, skipMergingOnKnownStaticURLsForVersionedApis);
                info.setHttpCallParser(callParser);
                // info.setResourceAnalyser(new ResourceAnalyser(300_000, 0.01, 100_000, 0.01));
                RuntimeListener.accountHTTPParserMap.put(accountId, info);
            }

            AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
            responses = com.akto.runtime.Main.filterBasedOnHeaders(responses, accountSettings);

            boolean makeApisCaseInsensitive = false;
            boolean mergeUrlsOnVersions = false;
            if (accountSettings != null) {
                makeApisCaseInsensitive = accountSettings.getHandleApisCaseInsensitive();
                mergeUrlsOnVersions = accountSettings.isAllowMergingOnVersions();
            }

            info.getHttpCallParser().syncFunction(responses, true, false, accountSettings, skipAdvancedFilters);
            APICatalogSync.mergeUrlsAndSave(apiCollectionId, true, false, info.getHttpCallParser().apiCatalogSync.existingAPIsInDb, makeApisCaseInsensitive, mergeUrlsOnVersions);
            info.getHttpCallParser().apiCatalogSync.buildFromDB(false, false);
            APICatalogSync.updateApiCollectionCount(info.getHttpCallParser().apiCatalogSync.getDbState(apiCollectionId), apiCollectionId);
//            for (HttpResponseParams responseParams: responses)  {
//                responseParams.requestParams.getHeaders().put("x-forwarded-for", Collections.singletonList("127.0.0.1"));
//                info.getResourceAnalyser().analyse(responseParams);
//            }
//            info.getResourceAnalyser().syncWithDb();
            try {
                DependencyFlow dependencyFlow = new DependencyFlow();
                dependencyFlow.run(apiCollectionId + "");
                dependencyFlow.syncWithDb();
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Exception while running dependency flow", LoggerMaker.LogDb.DASHBOARD);
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

        if (respPayload.startsWith("[")) {
            respPayload = "{\"json\": " + respPayload + "}";
        }

        BasicDBObject payload;
        try {
            payload = BasicDBObject.parse(respPayload);
        } catch (Exception e) {
            payload = BasicDBObject.parse("{}");
        }

        return payload;
    }

    public static float calculateRiskValueForSeverity(String severity) {
        float riskScore = 0;
        switch (severity) {
            case "CRITICAL":
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

    public static float getRiskScoreValueFromSeverityScore(float severityScore) {
        if (severityScore >= 100) {
            return 2;
        } else if (severityScore >= 10) {
            return 1;
        } else if (severityScore > 0) {
            return (float) 0.5;
        } else {
            return 0;
        }
    }

    public static void deleteApis(List<Key> toBeDeleted) {

        String id = "_id.";

        AccountsContextDaoWithRbac.deleteApisPerDao(toBeDeleted, SingleTypeInfoDao.instance, "");
        AccountsContextDaoWithRbac.deleteApisPerDao(toBeDeleted, ApiInfoDao.instance, id);
        AccountsContextDaoWithRbac.deleteApisPerDao(toBeDeleted, SampleDataDao.instance, id);
        AccountsContextDaoWithRbac.deleteApisPerDao(toBeDeleted, TrafficInfoDao.instance, id);
        AccountsContextDaoWithRbac.deleteApisPerDao(toBeDeleted, SensitiveSampleDataDao.instance, id);
        AccountsContextDaoWithRbac.deleteApisPerDao(toBeDeleted, SensitiveParamInfoDao.instance, "");
        AccountsContextDaoWithRbac.deleteApisPerDao(toBeDeleted, FilterSampleDataDao.instance, id);

    }

    public static List<String> getUniqueValuesOfList(List<String> input) {
        if (input == null || input.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> copySet = new HashSet<>(input);
        input = new ArrayList<>();
        input.addAll(copySet);
        return input;
    }

    public static String createDashboardUrlFromRequest(HttpServletRequest request) {
        if (request == null) {
            return "http://localhost:8080";
        }
        return request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
    }

    public static File createRequestFile(String originalMessage, String message) {
        if (originalMessage == null || message == null) {
            return null;
        }
        try {
            String origCurl = CurlUtils.getCurl(originalMessage);
            String testCurl = CurlUtils.getCurl(message);

            HttpResponseParams origObj = HttpCallParser.parseKafkaMessage(originalMessage);
            BasicDBObject testRespObj = BasicDBObject.parse(message);
            BasicDBObject testPayloadObj = BasicDBObject.parse(testRespObj.getString("response"));
            String testResp = testPayloadObj.getString("body");

            File tmpOutputFile = File.createTempFile("output", ".txt");

            FileUtils.writeStringToFile(tmpOutputFile, "Original Curl ----- \n\n", (String) null);
            FileUtils.writeStringToFile(tmpOutputFile, origCurl + "\n\n", (String) null, true);
            FileUtils.writeStringToFile(tmpOutputFile, "Original Api Response ----- \n\n", (String) null, true);
            FileUtils.writeStringToFile(tmpOutputFile, origObj.getPayload() + "\n\n", (String) null, true);

            FileUtils.writeStringToFile(tmpOutputFile, "Test Curl ----- \n\n", (String) null, true);
            FileUtils.writeStringToFile(tmpOutputFile, testCurl + "\n\n", (String) null, true);
            FileUtils.writeStringToFile(tmpOutputFile, "Test Api Response ----- \n\n", (String) null, true);
            FileUtils.writeStringToFile(tmpOutputFile, testResp + "\n\n", (String) null, true);

            return tmpOutputFile;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static TestResult getTestResultFromTestingRunResult(TestingRunResult testingRunResult) {
        TestResult testResult;
        try {
            GenericTestResult gtr = testingRunResult.getTestResults().get(testingRunResult.getTestResults().size() - 1);
            if (gtr instanceof TestResult) {
                testResult = (TestResult) gtr;
            } else if (gtr instanceof MultiExecTestResult) {
                MultiExecTestResult multiTestRes = (MultiExecTestResult) gtr;
                List<GenericTestResult> genericTestResults = multiTestRes.convertToExistingTestResult(testingRunResult);
                GenericTestResult genericTestResult = genericTestResults.get(genericTestResults.size() - 1);
                if (genericTestResult instanceof TestResult) {
                    testResult = (TestResult) genericTestResult;
                } else {

                    testResult = null;
                }
            } else {
                testResult = null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while casting GenericTestResult obj to TestResult obj: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            testResult = null;
        }

        return testResult;
    }

    public static ApiInfoKeyResult fetchUniqueApiInfoKeys(
            MongoCollection<Document> collection,
            Bson matchFilter,
            String apiInfoKeyPath,
            boolean showApiInfo
    ) {
        Bson filterQ = UsageMetricCalculator.excludeDemosAndDeactivated(apiInfoKeyPath + ".apiCollectionId");
        List<Integer> collectionIds;
        try {
            collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            if (collectionIds == null) {
                return new ApiInfoKeyResult(0, showApiInfo ? new ArrayList<>() : null);
            }
        } catch (Exception e) {
            return new ApiInfoKeyResult(0, showApiInfo ? new ArrayList<>() : null);
        }

        // Combine filters: exclude unwanted, include only allowed
        Bson combinedFilter = Filters.and(
                matchFilter,
                filterQ,
                Filters.in(apiInfoKeyPath + ".apiCollectionId", collectionIds)
        );

        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(combinedFilter));
        pipeline.add(Aggregates.project(Projections.include(
                apiInfoKeyPath + ".apiCollectionId",
                apiInfoKeyPath + ".url",
                apiInfoKeyPath + ".method"
        )));
        pipeline.add(Aggregates.group(
                new BasicDBObject("apiCollectionId", "$" + apiInfoKeyPath + ".apiCollectionId")
                        .append("url", "$" + apiInfoKeyPath + ".url")
                        .append("method", "$" + apiInfoKeyPath + ".method"),
                Accumulators.first("apiInfoKey", "$" + apiInfoKeyPath)
        ));

        Set<ApiInfo.ApiInfoKey> apiInfoKeys = new HashSet<>();
        List<ApiInfo> apiInfoList = new ArrayList<>();
        List<Document> results = collection.aggregate(pipeline, Document.class).into(new ArrayList<>());
        for (Document doc : results) {
            Document keyDoc = (Document) doc.get("apiInfoKey");
            if (keyDoc != null) {
                ApiInfo.ApiInfoKey key = new ApiInfo.ApiInfoKey(
                        keyDoc.getInteger("apiCollectionId"),
                        keyDoc.getString("url"),
                        URLMethods.Method.fromString(keyDoc.getString("method"))
                );
                apiInfoKeys.add(key);
                if (showApiInfo) {
                    apiInfoList.add(new ApiInfo(key));
                }
            }
        }
        return new ApiInfoKeyResult(apiInfoKeys.size(), showApiInfo ? apiInfoList : null);
    }
}
