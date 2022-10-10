package com.akto.rules;

import com.akto.dto.*;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestResult.Confidence;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.store.SampleMessageStore;
import com.akto.testing.ApiExecutor;
import com.akto.testing.StatusCodeAnalyser;

import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BOLATest extends TestPlugin {

    public BOLATest() { }

    @Override
    public TestResult start(ApiInfo.ApiInfoKey apiInfoKey, AuthMechanism authMechanism, List<RawApi> messages, Map<String, SingleTypeInfo> singleTypeInfoMap) {

        List<RawApi> filteredMessages = SampleMessageStore.filterMessagesWithAuthToken(messages, authMechanism);
        if (filteredMessages.isEmpty()) return addWithoutRequestError(null, TestResult.TestError.NO_PATH);

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
            return addTestSuccessResult(
                    result.testRequest, result.testResponse,
                    result.rawApi.getOriginalMessage(),
                    vulnerable, result.percentageMatch, result.singleTypeInfos, result.confidence
            );
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

        OriginalHttpResponse testResponse;
        try {
            testResponse = ApiExecutor.sendRequest(testRequest, true);
        } catch (Exception e) {
            return new ExecutorResult(false, null, new ArrayList<>(), 0, rawApi,
                    TestResult.TestError.API_REQUEST_FAILED, testRequest, null);
        }

        int statusCode = StatusCodeAnalyser.getStatusCode(testResponse.getBody(), testResponse.getStatusCode());
        double percentageMatch = compareWithOriginalResponse(originalHttpResponse.getBody(), testResponse.getBody());
        boolean vulnerable = isStatusGood(statusCode) && !containsPrivateResourceResult.isPrivate && percentageMatch > 90;

        // We can say with high confidence if an api is not vulnerable, and we don't need help of private resources for this
        if (!vulnerable) confidence = Confidence.HIGH;

        return new ExecutorResult(vulnerable,confidence, containsPrivateResourceResult.singleTypeInfos, percentageMatch,
                rawApi, null, testRequest, testResponse);

    }

    @Override
    public String testName() {
        return "BOLA";
    }



}
