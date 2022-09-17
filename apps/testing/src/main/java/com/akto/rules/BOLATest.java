package com.akto.rules;

import com.akto.dto.*;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestResult.Confidence;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.testing.ApiExecutor;
import com.akto.testing.StatusCodeAnalyser;

import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

public class BOLATest extends TestPlugin {

    public BOLATest() { }

    @Override
    public boolean start(ApiInfo.ApiInfoKey apiInfoKey, ObjectId testRunId, AuthMechanism authMechanism) {

        List<RawApi> filteredMessages = fetchMessagesWithAuthToken(apiInfoKey, testRunId, authMechanism);
        if (filteredMessages == null) return false;

        boolean vulnerable = false;
        ExecutorResult result = null;
        TestResult.TestError testError = null;

        for (RawApi rawApi: filteredMessages) {
            result = execute(rawApi, apiInfoKey, authMechanism);
            testError = result.testError;
            if (result.vulnerable) {
                vulnerable = true;
                break;
            }
        }

        if (testError != null) {
            addWithRequestError(apiInfoKey, result.rawApi.getOriginalMessage(), testRunId, testError, result.rawApi.getRequest());
        } else {
            addTestSuccessResult(
                    apiInfoKey, result.rawApi.getRequest(), result.rawApi.getResponse(),
                    result.rawApi.getOriginalMessage(), testRunId,
                    vulnerable, result.percentageMatch, result.singleTypeInfos, result.confidence
            );
        }

        return vulnerable;
    }

    public static class ExecutorResult {
        boolean vulnerable;
        TestResult.Confidence confidence;
        List<SingleTypeInfo> singleTypeInfos;
        double percentageMatch;
        RawApi rawApi;
        TestResult.TestError testError;

        public ExecutorResult(boolean vulnerable, TestResult.Confidence confidence, List<SingleTypeInfo> singleTypeInfos, double percentageMatch, RawApi rawApi, TestResult.TestError testError) {
            this.vulnerable = vulnerable;
            this.confidence = confidence;
            this.singleTypeInfos = singleTypeInfos;
            this.percentageMatch = percentageMatch;
            this.rawApi = rawApi;
            this.testError = testError;
        }
    }

    public ExecutorResult execute(RawApi rawApi, ApiInfo.ApiInfoKey apiInfoKey, AuthMechanism authMechanism) {
        OriginalHttpRequest originalHttpRequest = rawApi.getRequest();
        OriginalHttpResponse originalHttpResponse = rawApi.getResponse();

        authMechanism.addAuthToRequest(originalHttpRequest);

        ContainsPrivateResourceResult containsPrivateResourceResult = containsPrivateResource(originalHttpRequest, apiInfoKey);
        // We consider API contains private resources if : 
        //      a) Contains 1 or more private resources
        //      b) We couldn't find uniqueCount or publicCount for some of the request params
        // When it comes to case b we still say private resource but with low confidence, hence the below line
        TestResult.Confidence confidence = containsPrivateResourceResult.findPrivateOnes().size() > 0 ? TestResult.Confidence.HIGH : TestResult.Confidence.LOW;

        OriginalHttpResponse response;
        try {
            response = ApiExecutor.sendRequest(originalHttpRequest, true);
        } catch (Exception e) {
            return new ExecutorResult(false, null, new ArrayList<>(), 0, rawApi, TestResult.TestError.API_REQUEST_FAILED);
        }

        int statusCode = StatusCodeAnalyser.getStatusCode(response.getBody(), response.getStatusCode());
        double percentageMatch = compareWithOriginalResponse(originalHttpResponse.getBody(), response.getBody());
        boolean vulnerable = isStatusGood(statusCode) && !containsPrivateResourceResult.isPrivate && percentageMatch > 90;

        // We can say with high confidence if an api is not vulnerable and we don't need help of private resources for this
        if (!vulnerable) confidence = Confidence.HIGH;

        return new ExecutorResult(vulnerable,confidence, containsPrivateResourceResult.singleTypeInfos, percentageMatch, rawApi, null);

    }

    @Override
    public String testName() {
        return "BOLA";
    }



}
