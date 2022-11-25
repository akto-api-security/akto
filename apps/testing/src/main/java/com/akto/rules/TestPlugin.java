package com.akto.rules;

import com.akto.dto.*;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestResult;
import com.akto.dto.type.*;
import com.akto.runtime.APICatalogSync;
import com.akto.runtime.RelationshipSync;
import com.akto.testing.ApiExecutor;
import com.akto.testing.StatusCodeAnalyser;
import com.akto.types.CappedSet;
import com.akto.util.JSONUtils;
import com.akto.utils.RedactSampleData;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.akto.runtime.APICatalogSync.trim;
import static com.akto.runtime.APICatalogSync.trimAndSplit;


public abstract class TestPlugin {
    static ObjectMapper mapper = new ObjectMapper();
    static JsonFactory factory = mapper.getFactory();

    private static final Logger logger = LoggerFactory.getLogger(TestPlugin.class);

    public abstract Result  start(ApiInfo.ApiInfoKey apiInfoKey, AuthMechanism authMechanism, Map<ApiInfo.ApiInfoKey, List<String>> sampleMessages,
                                     Map<String, SingleTypeInfo> singleTypeInfos);

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

    public static double compareWithOriginalResponse(String originalPayload, String currentPayload) {
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

        Set<String> visited = new HashSet<>();
        int matched = 0;
        for (String k1: originalResponseParamMap.keySet()) {
            if (visited.contains(k1)) continue;
            visited.add(k1);
            Set<String> v1 = originalResponseParamMap.get(k1);
            Set<String> v2 = currentResponseParamMap.get(k1);
            if (Objects.equals(v1, v2)) matched +=1;
        }

        for (String k1: currentResponseParamMap.keySet()) {
            if (visited.contains(k1)) continue;
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
        testResults.add(new TestResult(null, originalMessage, Collections.singletonList(testError), 0, false, TestResult.Confidence.HIGH));
        return new Result(testResults, false,new ArrayList<>(), 0);
    }

    public TestResult buildFailedTestResultWithOriginalMessage(String originalMessage, TestResult.TestError testError, OriginalHttpRequest request) {
        String message = null;
        try {
            message = RedactSampleData.convertOriginalReqRespToString(request, null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new TestResult(message, originalMessage, Collections.singletonList(testError), 0, false, TestResult.Confidence.HIGH);
    }

    public Result addWithRequestError(String originalMessage, TestResult.TestError testError, OriginalHttpRequest request) {
        TestResult testResult = buildFailedTestResultWithOriginalMessage(originalMessage,testError,request);
        List<TestResult> testResults = new ArrayList<>();
        testResults.add(testResult);
        return new Result(testResults, false,new ArrayList<>(), 0);
    }

    public TestResult buildTestResult(OriginalHttpRequest request,
                                      OriginalHttpResponse response, String originalMessage,double percentageMatch, boolean isVulnerable) {

        List<TestResult.TestError> errors = new ArrayList<>();
        String message = null;
        try {
            message = RedactSampleData.convertOriginalReqRespToString(request, response);
        } catch (Exception e) {
            // TODO:
            logger.error("Error while converting OriginalHttpRequest to string", e);
            message = RedactSampleData.convertOriginalReqRespToString(new OriginalHttpRequest(), new OriginalHttpResponse());
            errors.add(TestResult.TestError.FAILED_TO_CONVERT_TEST_REQUEST_TO_STRING);
        }

        return new TestResult(message, originalMessage, errors, percentageMatch, isVulnerable, TestResult.Confidence.HIGH);

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
    }

    public static SingleTypeInfo findSti(String param, boolean isUrlParam,
                                         ApiInfo.ApiInfoKey apiInfoKey, boolean isHeader, int responseCode,
                                         Map<String, SingleTypeInfo> singleTypeInfoMap) {

        String key = SingleTypeInfo.composeKey(
                apiInfoKey.url, apiInfoKey.method.name(), responseCode, isHeader,
                param,SingleTypeInfo.GENERIC, apiInfoKey.getApiCollectionId(), isUrlParam
        );

        SingleTypeInfo singleTypeInfo = singleTypeInfoMap.get(key);

        if (singleTypeInfo == null) return null;

        return singleTypeInfo.copy();
    }

    public void asdf(OriginalHttpRequest originalHttpRequest) {
        String urlWithParams = originalHttpRequest.getFullUrlWithParams();
        BasicDBObject payload = RequestTemplate.parseRequestPayload(originalHttpRequest.getJsonRequestBody(), urlWithParams);
    }

    public ContainsPrivateResourceResult containsPrivateResource(OriginalHttpRequest originalHttpRequest, ApiInfo.ApiInfoKey apiInfoKey, Map<String, SingleTypeInfo> singleTypeInfoMap) {
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
                    SingleTypeInfo singleTypeInfo = findSti(i+"", true,apiInfoKey, false, -1, singleTypeInfoMap);
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
            SingleTypeInfo singleTypeInfo = findSti(param,false,apiInfoKey, false, -1, singleTypeInfoMap);
            if (singleTypeInfo != null) {
                Set<Object> valSet = flattened.get(param);
                Set<String> valStringSet = new HashSet<>();
                for (Object v: valSet) valStringSet.add(v.toString());
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
        for (URLMethods.Method method: methodList) {
            ApiInfo.ApiInfoKey methodApiInfoKey = new ApiInfo.ApiInfoKey(apiInfoKey.getApiCollectionId(), apiInfoKey.getUrl(), method);
            if (sampleMessages.containsKey(methodApiInfoKey)) continue;
            undocumentedMethods.add(method);
        }

        return undocumentedMethods;
    }

    public ApiExecutionDetails executeApiAndReturnDetails(OriginalHttpRequest testRequest, boolean followRedirects, OriginalHttpResponse originalHttpResponse) throws Exception {
        OriginalHttpResponse testResponse = ApiExecutor.sendRequest(testRequest, followRedirects);;

        int statusCode = StatusCodeAnalyser.getStatusCode(testResponse.getBody(), testResponse.getStatusCode());
        double percentageMatch = compareWithOriginalResponse(originalHttpResponse.getBody(), testResponse.getBody());

        return new ApiExecutionDetails(statusCode, percentageMatch, testResponse);
    }

    public static class ApiExecutionDetails {
        public int statusCode;
        public double percentageMatch;
        public OriginalHttpResponse testResponse;

        public ApiExecutionDetails(int statusCode, double percentageMatch, OriginalHttpResponse testResponse) {
            this.statusCode = statusCode;
            this.percentageMatch = percentageMatch;
            this.testResponse = testResponse;
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

}
