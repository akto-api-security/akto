package com.akto.rules;

import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dto.*;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestResult;
import com.akto.dto.type.*;
import com.akto.runtime.APICatalogSync;
import com.akto.runtime.RelationshipSync;
import com.akto.store.AuthMechanismStore;
import com.akto.store.SampleMessageStore;
import com.akto.util.JSONUtils;
import com.akto.utils.RedactSampleData;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.*;



public abstract class TestPlugin {
    static ObjectMapper mapper = new ObjectMapper();
    static JsonFactory factory = mapper.getFactory();

    public abstract boolean start(ApiInfo.ApiInfoKey apiInfoKey, ObjectId testRunId, AuthMechanism authMechanism);

    public abstract String testName();

    public static boolean isStatusGood(int statusCode) {
        return statusCode >= 200 && statusCode<300;
    }

    public List<RawApi> fetchMessagesWithAuthToken(ApiInfo.ApiInfoKey apiInfoKey, ObjectId testRunId, AuthMechanism authMechanism) {

        List<RawApi> messages = SampleMessageStore.fetchAllOriginalMessages(apiInfoKey);

        if (messages.isEmpty()) {
            addWithoutRequestError(apiInfoKey, testRunId, null, TestResult.TestError.NO_PATH);
            return null;
        }

        List<RawApi> filteredMessages = SampleMessageStore.filterMessagesWithAuthToken(messages, authMechanism);
        if (filteredMessages.isEmpty()) {
            RawApi rawApi = messages.get(0);
            addWithRequestError(apiInfoKey,rawApi.getOriginalMessage(), testRunId, TestResult.TestError.NO_AUTH_TOKEN_FOUND, rawApi.getRequest());
            return null;
        }

        return filteredMessages;
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

        return (100.0*matched)/visited.size();

    }

    public void addWithoutRequestError(ApiInfo.ApiInfoKey apiInfoKey, ObjectId testRunId, String originalMessage, TestResult.TestError testError) {
        Bson filter = TestingRunResultDao.generateFilter(testRunId, apiInfoKey.getApiCollectionId(), apiInfoKey.url, apiInfoKey.method.name());
        TestResult testResult = new TestResult(null, originalMessage, Collections.singletonList(testError), 0, false, TestResult.Confidence.LOW);
        Bson update = Updates.set("resultMap." + testName(), testResult);
        TestingRunResultDao.instance.updateOne(filter, update);
    }

    public void addWithRequestError(ApiInfo.ApiInfoKey apiInfoKey,String originalMessage, ObjectId testRunId, TestResult.TestError testError, OriginalHttpRequest request) {
        Bson filter = TestingRunResultDao.generateFilter(testRunId, apiInfoKey.getApiCollectionId(), apiInfoKey.url, apiInfoKey.method.name());

        String message = null;
        try {
            message = RedactSampleData.convertOriginalReqRespToString(request, null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        TestResult testResult = new TestResult(message, originalMessage, Collections.singletonList(testError), 0, false, TestResult.Confidence.LOW);
        Bson update = Updates.set("resultMap." + testName(), testResult);
        TestingRunResultDao.instance.updateOne(filter, update);
    }


    public void addTestSuccessResult(ApiInfo.ApiInfoKey apiInfoKey, OriginalHttpRequest request,
                                     OriginalHttpResponse response, String originalMessage, ObjectId testRunId,
                                     boolean vulnerable, double percentageMatch, List<SingleTypeInfo> singleTypeInfos,
                                     TestResult.Confidence confidence) {
        String message = null;
        try {
            message = RedactSampleData.convertOriginalReqRespToString(request, response);
        } catch (Exception e) {
            // TODO:
            e.printStackTrace();
            return;
        }

        Bson filter = TestingRunResultDao.generateFilter(testRunId, apiInfoKey);
        TestResult testResult = new TestResult(message, originalMessage, new ArrayList<>(),
                percentageMatch, vulnerable, confidence);
        Bson update = Updates.set("resultMap." + testName(), testResult);
        TestingRunResultDao.instance.updateOne(filter, update);
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

    public ContainsPrivateResourceResult containsPrivateResource(OriginalHttpRequest originalHttpRequest, ApiInfo.ApiInfoKey apiInfoKey) {
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
                    SingleTypeInfo singleTypeInfo = SampleMessageStore.findSti(i+"", true,apiInfoKey, false, -1);
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
            SingleTypeInfo singleTypeInfo = SampleMessageStore.findSti(param,false,apiInfoKey, false, -1);
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
