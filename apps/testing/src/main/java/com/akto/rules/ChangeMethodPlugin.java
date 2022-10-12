package com.akto.rules;

import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestResult;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.store.SampleMessageStore;
import com.akto.testing.ApiExecutor;
import com.akto.testing.StatusCodeAnalyser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class ChangeMethodPlugin extends TestPlugin {

    public abstract void modifyRequest(OriginalHttpRequest originalHttpRequest, URLMethods.Method method);

    public abstract boolean isVulnerable(double percentageBodyMatch, int statusCode);

    @Override
    public Result start(ApiInfo.ApiInfoKey apiInfoKey, AuthMechanism authMechanism, Map<ApiInfo.ApiInfoKey, List<String>> sampleMessages, Map<String, SingleTypeInfo> singleTypeInfos) {

        List<URLMethods.Method> undocumentedMethods = findUndocumentedMethods(sampleMessages, apiInfoKey);

        List<RawApi> messages = SampleMessageStore.fetchAllOriginalMessages(apiInfoKey, sampleMessages);
        if (messages.isEmpty()) return addWithoutRequestError(null, TestResult.TestError.NO_PATH);

        RawApi rawApi = messages.get(0);

        boolean overallVulnerable = false;

        List<TestResult> testResults = new ArrayList<>();
        for (URLMethods.Method method: undocumentedMethods) {
            OriginalHttpRequest testRequest = rawApi.getRequest().copy();
            OriginalHttpResponse originalHttpResponse = rawApi.getResponse().copy();

            modifyRequest(testRequest, method);

            ApiExecutionDetails apiExecutionDetails;
            // todo: if making post -> get (convert to request body to ?)
            try {
                apiExecutionDetails = executeApiAndReturnDetails(testRequest, true, originalHttpResponse);
            } catch (Exception e) {
                return addWithRequestError( rawApi.getOriginalMessage(), TestResult.TestError.API_REQUEST_FAILED, testRequest);
            }

            int statusCode = StatusCodeAnalyser.getStatusCode(apiExecutionDetails.testResponse.getBody(), apiExecutionDetails.testResponse.getStatusCode());
            double percentageMatch = compareWithOriginalResponse(originalHttpResponse.getBody(), apiExecutionDetails.testResponse.getBody());
            boolean vulnerable = isVulnerable(percentageMatch, statusCode);
            overallVulnerable = overallVulnerable || vulnerable;

            TestResult testResult = buildTestResult(testRequest, apiExecutionDetails.testResponse, rawApi.getOriginalMessage(), percentageMatch, vulnerable);
            testResults.add(testResult);

        }

        return addTestSuccessResult(overallVulnerable, testResults, new ArrayList<>(), TestResult.Confidence.HIGH);

    }

    @Override
    public String superTestName() {
        return "CHANGE_METHOD";
    }
}
