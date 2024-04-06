package com.akto.rules;

import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.*;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.test_editor.DataOperandsFilterResponse;
import com.akto.dto.test_editor.FilterNode;
import com.akto.dto.testing.*;
import com.akto.dto.testing.info.TestInfo;
import com.akto.dto.type.*;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.runtime.APICatalogSync;
import com.akto.runtime.RelationshipSync;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;
import com.akto.test_editor.filter.Filter;
import com.akto.testing.StatusCodeAnalyser;
import com.akto.types.CappedSet;
import com.akto.util.JSONUtils;
import com.akto.utils.RedactSampleData;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.akto.runtime.APICatalogSync.trimAndSplit;


public abstract class TestPlugin {
    static ObjectMapper mapper = new ObjectMapper();
    static JsonFactory factory = mapper.getFactory();
    static final LoggerMaker loggerMaker = new LoggerMaker(TestPlugin.class);

    private static final Logger logger = LoggerFactory.getLogger(TestPlugin.class);
    private static final Gson gson = new Gson();

    public abstract Result  start(ApiInfoKey apiInfoKey, TestingUtil testingUtil, TestingRunConfig testingRunConfig);

    public abstract String superTestName();
    public abstract String subTestName();

    public static boolean isStatusGood(int statusCode) {
        return statusCode >= 200 && statusCode<300;
    }

    public static void extractAllValuesFromPayload(String payload, Map<String,Set<String>> payloadMap) throws Exception{
        JsonParser jp = factory.createParser(payload);
        JsonNode node = mapper.readTree(jp);
        RelationshipSync.extractAllValuesFromPayload(node,new ArrayList<>(),payloadMap);
    }

    public String decrementUrlVersion(String url, int decrementValue, int limit) {
        String regex = "\\/v(\\d+)\\/";
        Pattern p = Pattern.compile(regex);
        Matcher matcher = p.matcher(url);
        StringBuffer sb = new StringBuffer();

        boolean containsAtLeastOneVersion = false;

        while (matcher.find()) {
            String code = matcher.group(1);
            int version;
            try {
                version = Integer.parseInt(code);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error while parsing integer " + code + " : " + e, LogDb.TESTING);
                return null;
            }
            int newVersion = version - decrementValue;
            if (newVersion < limit) return null;
            containsAtLeastOneVersion = true;
            matcher.appendReplacement(sb, "/v"+newVersion+"/");
        }

        if (!containsAtLeastOneVersion) return null;

        matcher.appendTail(sb);

        return sb.toString();
    }

    public static double compareWithOriginalResponse(String originalPayload, String currentPayload, Map<String, Boolean> comparisonExcludedKeys) {
        if (originalPayload == null && currentPayload == null) return 100;
        if (originalPayload == null || currentPayload == null) return 0;

        String trimmedOriginalPayload = originalPayload.trim();
        String trimmedCurrentPayload = currentPayload.trim();
        if (trimmedCurrentPayload.equals(trimmedOriginalPayload)) return 100;

        Map<String, Set<String>> originalResponseParamMap = new HashMap<>();
        Map<String, Set<String>> currentResponseParamMap = new HashMap<>();
        try {
            extractAllValuesFromPayload(originalPayload, originalResponseParamMap);
            extractAllValuesFromPayload(currentPayload, currentResponseParamMap);
        } catch (Exception e) {
            return 0.0;
        }

        if (originalResponseParamMap.keySet().size() == 0 && currentResponseParamMap.keySet().size() == 0) {
            return 100.0;
        }

        Set<String> visited = new HashSet<>();
        int matched = 0;
        for (String k1: originalResponseParamMap.keySet()) {
            if (visited.contains(k1) || comparisonExcludedKeys.containsKey(k1)) continue;
            visited.add(k1);
            Set<String> v1 = originalResponseParamMap.get(k1);
            Set<String> v2 = currentResponseParamMap.get(k1);
            if (Objects.equals(v1, v2)) matched +=1;
        }

        for (String k1: currentResponseParamMap.keySet()) {
            if (visited.contains(k1) || comparisonExcludedKeys.containsKey(k1)) continue;
            visited.add(k1);
            Set<String> v1 = originalResponseParamMap.get(k1);
            Set<String> v2 = currentResponseParamMap.get(k1);
            if (Objects.equals(v1, v2)) matched +=1;
        }

        int visitedSize = visited.size();
        if (visitedSize == 0) return 0.0;

        double result = (100.0*matched)/visitedSize;

        if (Double.isFinite(result)) {
            return result;
        } else {
            return 0.0;
        }

    }

    public Result addWithoutRequestError(String originalMessage, TestResult.TestError testError) {
        List<TestResult> testResults = new ArrayList<>();
        testResults.add(new TestResult(null, originalMessage, Collections.singletonList(testError.getMessage()), 0, false, TestResult.Confidence.HIGH, null));
        return new Result(testResults, false,new ArrayList<>(), 0);
    }

    public TestResult buildFailedTestResultWithOriginalMessage(String originalMessage, TestResult.TestError testError, OriginalHttpRequest request, TestInfo testInfo) {
        String message = null;
        try {
            message = RedactSampleData.convertOriginalReqRespToString(request, null);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while converting testRequest object to string : " + e, LogDb.TESTING);
        }

        return new TestResult(message, originalMessage, Collections.singletonList(testError.getMessage()), 0, false, TestResult.Confidence.HIGH, testInfo);
    }

    public Result addWithRequestError(String originalMessage, TestResult.TestError testError, OriginalHttpRequest request, TestInfo testInfo) {
        TestResult testResult = buildFailedTestResultWithOriginalMessage(originalMessage,testError,request, testInfo);
        List<TestResult> testResults = new ArrayList<>();
        testResults.add(testResult);
        return new Result(testResults, false,new ArrayList<>(), 0);
    }

    public TestResult buildTestResult(OriginalHttpRequest request, OriginalHttpResponse response, String originalMessage,
                                      double percentageMatch, boolean isVulnerable, TestInfo testInfo) {

        List<String> errors = new ArrayList<>();
        String message = null;
        try {
            message = RedactSampleData.convertOriginalReqRespToString(request, response);
        } catch (Exception e) {
            // TODO:
            logger.error("Error while converting OriginalHttpRequest to string", e);
            message = RedactSampleData.convertOriginalReqRespToString(new OriginalHttpRequest(), new OriginalHttpResponse());
            errors.add(TestResult.TestError.FAILED_TO_CONVERT_TEST_REQUEST_TO_STRING.getMessage());
        }

        return new TestResult(message, originalMessage, errors, percentageMatch, isVulnerable, TestResult.Confidence.HIGH, testInfo);

    }

    public Result addTestSuccessResult(boolean vulnerable, List<TestResult> testResults , List<SingleTypeInfo> singleTypeInfos, TestResult.Confidence confidence) {
        int confidencePercentage = confidence.equals(TestResult.Confidence.HIGH) ? 100 : 10;
        return new Result(testResults, vulnerable,singleTypeInfos, confidencePercentage);
    }

    public static class ContainsPrivateResourceResult {
        boolean isPrivate;
        List<SingleTypeInfo> singleTypeInfos;

        public ContainsPrivateResourceResult(boolean isPrivate, List<SingleTypeInfo> singleTypeInfos) {
            this.isPrivate = isPrivate;
            this.singleTypeInfos = singleTypeInfos;
        }

        public List<SingleTypeInfo> findPrivateOnes() {
            List<SingleTypeInfo> res = new ArrayList<>();
            for (SingleTypeInfo singleTypeInfo: singleTypeInfos) {
                if (singleTypeInfo.getIsPrivate()) res.add(singleTypeInfo);
            }
            return res;
        }

        public Set<String> findPrivateParams() {
            Set<String> privateParams = new HashSet<>();
            for (SingleTypeInfo privateSTI: findPrivateOnes()) {
                privateParams.add(privateSTI.getParam());
            }

            return privateParams;
        }
    }

    public static SingleTypeInfo findSti(String param, boolean isUrlParam,
                                         ApiInfo.ApiInfoKey apiInfoKey, boolean isHeader, int responseCode,
                                         Map<String, SingleTypeInfo> singleTypeInfoMap) {

        Bson filter = Filters.and(
            Filters.eq("apiCollectionId", apiInfoKey.getApiCollectionId()),
            Filters.eq("url", apiInfoKey.url),
            Filters.eq("method", apiInfoKey.method.name()),
            Filters.eq("responseCode", responseCode),
            Filters.eq("isHeader", isHeader),
            Filters.eq("param", param),
            Filters.eq("isUrlParam", isUrlParam)
        );
        SingleTypeInfo singleTypeInfo = SingleTypeInfoDao.instance.findOne(filter);

        if (singleTypeInfo == null) return null;

        return singleTypeInfo.copy();
    }

    public void asdf(OriginalHttpRequest originalHttpRequest) {
        String urlWithParams = originalHttpRequest.getFullUrlWithParams();
        BasicDBObject payload = RequestTemplate.parseRequestPayload(originalHttpRequest.getJsonRequestBody(), urlWithParams);
    }

    public ContainsPrivateResourceResult containsPrivateResource(OriginalHttpRequest originalHttpRequest, ApiInfo.ApiInfoKey apiInfoKey, SampleMessageStore sampleMessageStore) {
        String urlWithParams = originalHttpRequest.getFullUrlWithParams();
        String url = apiInfoKey.url;
        URLMethods.Method method = apiInfoKey.getMethod();
        List<SingleTypeInfo> singleTypeInfoList = new ArrayList<>();

        boolean isPrivate = true;
        boolean atLeastOneValueInRequest = false;
        // check private resource in
        // 1. url
        if (APICatalog.isTemplateUrl(url)) {
            URLTemplate urlTemplate = APICatalogSync.createUrlTemplate(url, method);
            String[] tokens = urlTemplate.getTokens();
            String[] ogTokens = trimAndSplit(url);
            for (int i = 0;i < tokens.length; i++) {
                if (tokens[i] == null) {
                    atLeastOneValueInRequest = true;
                    SingleTypeInfo singleTypeInfo = findSti(i+"", true,apiInfoKey, false, -1, sampleMessageStore.getSingleTypeInfos());
                    if (singleTypeInfo != null) {
                        String v = ogTokens[i];
                        Set<String> values = new HashSet<>();
                        values.add(v);
                        singleTypeInfo.setValues(new CappedSet<>(values));
                        singleTypeInfoList.add(singleTypeInfo);
                        isPrivate = isPrivate && singleTypeInfo.getIsPrivate();
                    }
                }
            }
        }

        // 2. payload
        BasicDBObject payload = RequestTemplate.parseRequestPayload(originalHttpRequest.getJsonRequestBody(), urlWithParams);
        Map<String, Set<Object>> flattened = JSONUtils.flatten(payload);
        for (String param: flattened.keySet()) {
            atLeastOneValueInRequest = true;
            SingleTypeInfo singleTypeInfo = findSti(param,false,apiInfoKey, false, -1, sampleMessageStore.getSingleTypeInfos());
            if (singleTypeInfo != null) {
                Set<Object> valSet = flattened.get(param);
                Set<String> valStringSet = new HashSet<>();
                for (Object v: valSet) {
                    if (v == null) {
                        continue;
                    }
                    valStringSet.add(v.toString());
                }
                singleTypeInfo.setValues(new CappedSet<>(valStringSet));
                singleTypeInfoList.add(singleTypeInfo);
                isPrivate = isPrivate && singleTypeInfo.getIsPrivate();
            }
        }

        // For private at least one value in request
        boolean finalPrivateResult = isPrivate && atLeastOneValueInRequest;

        return new ContainsPrivateResourceResult(finalPrivateResult, singleTypeInfoList);
    }

    public static List<URLMethods.Method> findUndocumentedMethods(Map<ApiInfo.ApiInfoKey, List<String>> sampleMessages, ApiInfo.ApiInfoKey apiInfoKey) {
        // We will hit only those methods whose traffic doesn't exist. For that we see if corresponding method exists or not in sample messages
        List<URLMethods.Method> undocumentedMethods = new ArrayList<>();
        List<URLMethods.Method> methodList = Arrays.asList(
                URLMethods.Method.GET, URLMethods.Method.POST, URLMethods.Method.PUT, URLMethods.Method.DELETE,
                URLMethods.Method.PATCH
        );

        String apiUrl = apiInfoKey.getUrl();
        URLMethods.Method  apiMethod = apiInfoKey.getMethod();


        for (URLMethods.Method fakeMethod: methodList) {
            ApiInfo.ApiInfoKey methodApiInfoKey = new ApiInfo.ApiInfoKey(apiInfoKey.getApiCollectionId(), apiUrl, fakeMethod);
            if (sampleMessages.containsKey(methodApiInfoKey)) continue;

            boolean found = false;
            for (ApiInfoKey apiInfoKeyFromDb: sampleMessages.keySet()) {
                String apiFromDbUrl = apiInfoKeyFromDb.getUrl(); 
                URLMethods.Method apiFromDbMethod = apiInfoKeyFromDb.getMethod();
                if (apiInfoKeyFromDb.getApiCollectionId() != apiInfoKey.getApiCollectionId()) continue;

                if (!APICatalog.isTemplateUrl(apiFromDbUrl)) continue;
                
                URLTemplate urlTemplate = APICatalogSync.createUrlTemplate(apiFromDbUrl, apiFromDbMethod);

                found = urlTemplate.match(apiUrl, fakeMethod);

                if (found) break;
            }

            if (found) continue;

            undocumentedMethods.add(fakeMethod);
        }

        return undocumentedMethods;
    }

    public static Map<String, Boolean> getComparisonExcludedKeys(SampleRequestReplayResponse sampleReplayResp, ArrayList<Map<String, Set<String>>> replayedResponseMap) {

        Map<String, Boolean> comparisonExcludedKeys = new HashMap<>();
        Set<String> keys = new HashSet<>();
        for (String k1: replayedResponseMap.get(0).keySet()) {
            keys.add(k1);
        }

        for (String key : keys) {
            Set<Set<String>> data = new HashSet<>();
            for (int i = 0; i < replayedResponseMap.size(); i++) {
                Set<String> v1 = replayedResponseMap.get(i).get(key);
                if (v1 == null) {
                    break;
                }
                data.add(v1);
            }
            if (data.size() > 1) {
                comparisonExcludedKeys.put(key, true);
            }
        }

        return comparisonExcludedKeys;
    }

    public static boolean validateFilter(FilterNode filterNode, RawApi rawApi, ApiInfoKey apiInfoKey, Map<String, Object> varMap, String logId) {
        if (filterNode == null) return true;
        if (rawApi == null) return false;
        return validate(filterNode, rawApi, null, apiInfoKey,"filter", varMap, logId);
    }

    public static boolean validateValidator(FilterNode validatorNode, RawApi rawApi, RawApi testRawApi, ApiInfoKey apiInfoKey, Map<String, Object> varMap, String logId) {
        if (validatorNode == null) return true;
        if (testRawApi == null) return false;

        OriginalHttpResponse response = testRawApi.getResponse();
        String body = response == null ? null : response.getBody();
        boolean isDefaultPayload = StatusCodeAnalyser.isDefaultPayload(body);
        boolean validateResult = validate(validatorNode,rawApi,testRawApi, apiInfoKey,"validator", varMap, logId);

        // loggerMaker.infoAndAddToDb(logId + " isDefaultPayload = " + isDefaultPayload + "; validateResult = " + validateResult, LogDb.TESTING);
        return !isDefaultPayload && validateResult;
    }

    private static boolean validate(FilterNode node, RawApi rawApi, RawApi testRawApi, ApiInfoKey apiInfoKey, String context, Map<String, Object> varMap, String logId) {
        Filter filter = new Filter();
        DataOperandsFilterResponse dataOperandsFilterResponse = filter.isEndpointValid(node, rawApi, testRawApi, apiInfoKey, null, null , false,context, varMap, logId, false);
        return dataOperandsFilterResponse.getResult();
    }

    public static class ApiExecutionDetails {
        public int statusCode;
        public double percentageMatch;
        public OriginalHttpResponse testResponse;
        public OriginalHttpResponse baseResponse;
        public String originalReqResp;

        public ApiExecutionDetails(int statusCode, double percentageMatch, OriginalHttpResponse testResponse, OriginalHttpResponse baseResponse, String originalReqResp) {
            this.statusCode = statusCode;
            this.percentageMatch = percentageMatch;
            this.testResponse = testResponse;
            this.baseResponse = baseResponse;
            this.originalReqResp = originalReqResp;
        }
    }

    public static class ExecutorResult {
        boolean vulnerable;
        TestResult.Confidence confidence;
        List<SingleTypeInfo> singleTypeInfos;
        double percentageMatch;
        RawApi rawApi;
        OriginalHttpResponse testResponse;
        OriginalHttpRequest testRequest;

        TestResult.TestError testError;
        TestInfo testInfo;

        public ExecutorResult(boolean vulnerable, TestResult.Confidence confidence, List<SingleTypeInfo> singleTypeInfos,
                              double percentageMatch, RawApi rawApi, TestResult.TestError testError,
                              OriginalHttpRequest testRequest, OriginalHttpResponse testResponse, TestInfo testInfo) {
            this.vulnerable = vulnerable;
            this.confidence = confidence;
            this.singleTypeInfos = singleTypeInfos;
            this.percentageMatch = percentageMatch;
            this.rawApi = rawApi;
            this.testError = testError;
            this.testRequest = testRequest;
            this.testResponse = testResponse;
            this.testInfo = testInfo;
        }
    }

    public static class Result {
        public List<TestResult> testResults;
        public boolean isVulnerable;
        public List<SingleTypeInfo> singleTypeInfos;
        public int confidencePercentage;

        public Result(List<TestResult> testResults, boolean isVulnerable, List<SingleTypeInfo> singleTypeInfos, int confidencePercentage) {
            this.testResults = testResults;
            this.isVulnerable = isVulnerable;
            this.singleTypeInfos = singleTypeInfos;
            this.confidencePercentage = confidencePercentage;
        }
    }

    public static class TestRoleMatcher {
        List<TestRoles> friends;
        List<TestRoles> enemies;

        public TestRoleMatcher(List<TestRoles> testRolesList, ApiInfo.ApiInfoKey apiInfoKey) {
            this.friends = new ArrayList<>();
            this.enemies = new ArrayList<>();

            for (TestRoles testRoles: testRolesList) {
                EndpointLogicalGroup endpointLogicalGroup = testRoles.fetchEndpointLogicalGroup();
                if (endpointLogicalGroup == null) continue;
                TestingEndpoints testingEndpoints = endpointLogicalGroup.getTestingEndpoints();
                if (testingEndpoints == null) continue;
                if (testingEndpoints.containsApi(apiInfoKey) ) {
                    this.friends.add(testRoles);
                } else {
                    this.enemies.add(testRoles);
                }
            }
        }


        public boolean shouldDoBFLA() {
            return this.friends.size() > 0;
        }
    }

}
