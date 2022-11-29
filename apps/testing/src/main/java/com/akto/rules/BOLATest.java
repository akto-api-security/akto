package com.akto.rules;

import com.akto.dto.*;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestResult.Confidence;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.store.SampleMessageStore;
import com.akto.testing.ApiExecutor;
import com.akto.testing.StatusCodeAnalyser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class BOLATest extends TestPlugin {

    public BOLATest() { }

    @Override
    public Result start(ApiInfo.ApiInfoKey apiInfoKey, AuthMechanism authMechanism, Map<ApiInfo.ApiInfoKey, List<String>> sampleMessages, Map<String, SingleTypeInfo> singleTypeInfoMap) {

        List<RawApi> messages = SampleMessageStore.fetchAllOriginalMessages(apiInfoKey, sampleMessages);
        if (messages.isEmpty()) return null;
        List<RawApi> filteredMessages = SampleMessageStore.filterMessagesWithAuthToken(messages, authMechanism);
        if (filteredMessages.isEmpty()) return null;

        boolean vulnerable = false;
        ExecutorResult result = null;
        TestResult.TestError testError = null;

        for (RawApi rawApi: filteredMessages) {
            result = execute(rawApi, apiInfoKey, authMechanism, singleTypeInfoMap);
            testError = result.testError;
            if (result.vulnerable) {
                vulnerable = true;
                break;
            }
        }

        if (testError != null) {
            return addWithRequestError( result.rawApi.getOriginalMessage(), testError, result.rawApi.getRequest());
        } else {
            TestResult testResult = buildTestResult(
                    result.testRequest, result.testResponse, result.rawApi.getOriginalMessage(),
                    result.percentageMatch, result.vulnerable
            );
            return addTestSuccessResult(
                    vulnerable,Collections.singletonList(testResult), result.singleTypeInfos, result.confidence
            );
        }

    }

    @Override
    public String superTestName() {
        return "BOLA";
    }

    @Override
    public String subTestName() {
        return "REPLACE_AUTH_TOKEN";
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

        public ExecutorResult(boolean vulnerable, TestResult.Confidence confidence, List<SingleTypeInfo> singleTypeInfos,
                              double percentageMatch, RawApi rawApi, TestResult.TestError testError,
                              OriginalHttpRequest testRequest, OriginalHttpResponse testResponse) {
            this.vulnerable = vulnerable;
            this.confidence = confidence;
            this.singleTypeInfos = singleTypeInfos;
            this.percentageMatch = percentageMatch;
            this.rawApi = rawApi;
            this.testError = testError;
            this.testRequest = testRequest;
            this.testResponse = testResponse;
        }
    }

    public ExecutorResult execute(RawApi rawApi, ApiInfo.ApiInfoKey apiInfoKey, AuthMechanism authMechanism, Map<String, SingleTypeInfo> singleTypeInfoMap) {
        OriginalHttpRequest testRequest = rawApi.getRequest().copy();
        OriginalHttpResponse originalHttpResponse = rawApi.getResponse().copy();

        authMechanism.addAuthToRequest(testRequest);

        ContainsPrivateResourceResult containsPrivateResourceResult = containsPrivateResource(testRequest, apiInfoKey, singleTypeInfoMap);
        // We consider API contains private resources if : 
        //      a) Contains 1 or more private resources
        //      b) We couldn't find uniqueCount or publicCount for some request params
        // When it comes to case b we still say private resource but with low confidence, hence the below line
        TestResult.Confidence confidence = containsPrivateResourceResult.findPrivateOnes().size() > 0 ? TestResult.Confidence.HIGH : TestResult.Confidence.LOW;

        ApiExecutionDetails apiExecutionDetails;
        try {
            apiExecutionDetails = executeApiAndReturnDetails(testRequest, true, originalHttpResponse);
        } catch (Exception e) {
            return new ExecutorResult(false, null, new ArrayList<>(), 0, rawApi,
                    TestResult.TestError.API_REQUEST_FAILED, testRequest, null);
        }

        boolean vulnerable = isStatusGood(apiExecutionDetails.statusCode) && containsPrivateResourceResult.isPrivate && apiExecutionDetails.percentageMatch > 90;

        // We can say with high confidence if an api is not vulnerable, and we don't need help of private resources for this
        if (!vulnerable) confidence = Confidence.HIGH;

        return new ExecutorResult(vulnerable,confidence, containsPrivateResourceResult.singleTypeInfos, apiExecutionDetails.percentageMatch,
                rawApi, null, testRequest, apiExecutionDetails.testResponse);

    }


}
