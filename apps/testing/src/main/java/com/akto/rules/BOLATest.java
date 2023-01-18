package com.akto.rules;

import com.akto.dto.*;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestResult.Confidence;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;
import com.akto.testing.ApiExecutor;
import com.akto.testing.StatusCodeAnalyser;
import com.akto.util.JSONUtils;
import com.akto.util.modifier.ConvertToArrayPayloadModifier;
import com.akto.util.modifier.NestedObjectModifier;
import com.google.gson.Gson;

import java.util.*;

public class BOLATest extends AuthRequiredRunAllTestPlugin {

    private static final Gson gson = new Gson();
    
    public BOLATest() { }

    @Override
    public String superTestName() {
        return "BOLA";
    }

    @Override
    public String subTestName() {
        return "REPLACE_AUTH_TOKEN";
    }


    @Override
    public List<ExecutorResult> execute(RawApi rawApi, ApiInfo.ApiInfoKey apiInfoKey, TestingUtil testingUtil) {
        OriginalHttpRequest testRequest = rawApi.getRequest().copy();
        OriginalHttpResponse originalHttpResponse = rawApi.getResponse().copy();

        testingUtil.getAuthMechanism().addAuthToRequest(testRequest);

        ContainsPrivateResourceResult containsPrivateResourceResult = containsPrivateResource(testRequest, apiInfoKey, testingUtil.getSingleTypeInfoMap());
        // We consider API contains private resources if : 
        //      a) Contains 1 or more private resources
        //      b) We couldn't find uniqueCount or publicCount for some request params
        // When it comes to case b we still say private resource but with low confidence, hence the below line

        ExecutorResult executorResultSimple = util(testRequest, originalHttpResponse, rawApi, containsPrivateResourceResult);

        List<ExecutorResult> executorResults = new ArrayList<>();
        executorResults.add(executorResultSimple);

        Set<String> privateStiParams = containsPrivateResourceResult.findPrivateParams();
        if (testRequest.isJsonRequest()) {
            OriginalHttpRequest testRequestArray = testRequest.copy();
            String modifiedPayload = JSONUtils.modify(testRequestArray.getJsonRequestBody(), privateStiParams, new ConvertToArrayPayloadModifier());
            if (modifiedPayload != null) {
                testRequestArray.setBody(modifiedPayload);
                ExecutorResult executorResultArray = util(testRequestArray, originalHttpResponse, rawApi, containsPrivateResourceResult);
                executorResults.add(executorResultArray);
            }

            OriginalHttpRequest testRequestJson = testRequest.copy();
            modifiedPayload = JSONUtils.modify(testRequestJson.getJsonRequestBody(), privateStiParams, new NestedObjectModifier());
            if (modifiedPayload != null) {
                testRequestJson.setBody(modifiedPayload);
                ExecutorResult executorResultJson = util(testRequestJson, originalHttpResponse, rawApi, containsPrivateResourceResult);
                executorResults.add(executorResultJson);
            }
        }


        return executorResults;
    }

    public ExecutorResult util(OriginalHttpRequest testRequest, OriginalHttpResponse originalHttpResponse, RawApi rawApi,
                               ContainsPrivateResourceResult containsPrivateResourceResult) {
        ApiExecutionDetails apiExecutionDetails;
        try {
            OriginalHttpRequest originalHttpRequest = rawApi.getRequest().copy();
            apiExecutionDetails = executeApiAndReturnDetails(testRequest, true, originalHttpResponse, originalHttpRequest);
        } catch (Exception e) {
            return new ExecutorResult(false, null, new ArrayList<>(), 0, rawApi,
                    TestResult.TestError.API_REQUEST_FAILED, testRequest, null, null);
        }

        RawApi rawApiDuplicate = rawApi.copy();

        String originalMessage = rawApiDuplicate.getOriginalMessage();

        Map<String, Object> json = gson.fromJson(originalMessage, Map.class);
        if (apiExecutionDetails.baseResponse != null) {
            json.put("responsePayload", apiExecutionDetails.baseResponse.getBody());
            originalMessage = gson.toJson(json);
        }

        rawApiDuplicate.setOriginalMessage(originalMessage);

        TestResult.Confidence confidence = containsPrivateResourceResult.findPrivateOnes().size() > 0 ? TestResult.Confidence.HIGH : TestResult.Confidence.LOW;

        boolean vulnerable = isStatusGood(apiExecutionDetails.statusCode) && containsPrivateResourceResult.isPrivate && apiExecutionDetails.percentageMatch > 90;

        // We can say with high confidence if an api is not vulnerable, and we don't need help of private resources for this
        if (!vulnerable) confidence = Confidence.HIGH;

        return new ExecutorResult(vulnerable,confidence, containsPrivateResourceResult.singleTypeInfos, apiExecutionDetails.percentageMatch,
            rawApiDuplicate, null, testRequest, apiExecutionDetails.testResponse, null);

    }


}
