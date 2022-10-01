package com.akto.rules;

import com.akto.dto.*;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestResult;
import com.akto.dto.type.*;
import com.akto.runtime.APICatalogSync;
import com.akto.runtime.RelationshipSync;
import com.akto.store.SampleMessageStore;
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



public abstract class TestPlugin {
    static ObjectMapper mapper = new ObjectMapper();
    static JsonFactory factory = mapper.getFactory();

    private static final Logger logger = LoggerFactory.getLogger(TestPlugin.class);

    public abstract TestResult start(ApiInfo.ApiInfoKey apiInfoKey, AuthMechanism authMechanism, List<RawApi> messages,
                                     Map<String, SingleTypeInfo> singleTypeInfos);

    public abstract String testName();

    public static boolean isStatusGood(int statusCode) {
        return statusCode >= 200 && statusCode<300;
    }

    public static void extractAllValuesFromPayload(String payload, Map<String,Set<String>> payloadMap) throws Exception{
        JsonParser jp = factory.createParser(payload);
        JsonNode node = mapper.readTree(jp);
        RelationshipSync.extractAllValuesFromPayload(node,new ArrayList<>(),payloadMap);
    }

    public static double compareWithOriginalResponse(String originalPayload, String currentPayload) {
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

    public TestResult addWithoutRequestError(String originalMessage, TestResult.TestError testError) {
        return new TestResult(null, originalMessage,false, Collections.singletonList(testError), new ArrayList<>(), 0, TestResult.Confidence.LOW);
    }

    public TestResult addWithRequestError(String originalMessage, TestResult.TestError testError, OriginalHttpRequest request) {
        String message = null;
        try {
            message = RedactSampleData.convertOriginalReqRespToString(request, null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new TestResult(message, originalMessage, false, Collections.singletonList(testError), new ArrayList<>(), 0, TestResult.Confidence.LOW);
    }


    public TestResult addTestSuccessResult( OriginalHttpRequest request,
                                     OriginalHttpResponse response, String originalMessage,
                                     boolean vulnerable, double percentageMatch, List<SingleTypeInfo> singleTypeInfos,
                                     TestResult.Confidence confidence) {
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

        return new TestResult(message, originalMessage, vulnerable, errors, singleTypeInfos,
                percentageMatch, confidence);
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

        return singleTypeInfoMap.get(key);
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
            for (int i = 0;i < tokens.length; i++) {
                if (tokens[i] == null) {
                    atLeastOneValueInRequest = true;
                    SingleTypeInfo singleTypeInfo = findSti(i+"", true,apiInfoKey, false, -1, singleTypeInfoMap);
                    if (singleTypeInfo != null) {
                        singleTypeInfoList.add(singleTypeInfo);
                        isPrivate = isPrivate && singleTypeInfo.getIsPrivate();
                    }
                }
            }
        }

        // 2. payload
        BasicDBObject payload = RequestTemplate.parseRequestPayload(originalHttpRequest.getBody(), urlWithParams);
        Map<String, Set<Object>> flattened = JSONUtils.flatten(payload);
        for (String param: flattened.keySet()) {
            atLeastOneValueInRequest = true;
            SingleTypeInfo singleTypeInfo = findSti(param,false,apiInfoKey, false, -1, singleTypeInfoMap);
            if (singleTypeInfo != null) {
                singleTypeInfoList.add(singleTypeInfo);
                isPrivate = isPrivate && singleTypeInfo.getIsPrivate();
            }
        }

        // For private at least one value in request
        boolean finalPrivateResult = isPrivate && atLeastOneValueInRequest;

        return new ContainsPrivateResourceResult(finalPrivateResult, singleTypeInfoList);
    }

}
