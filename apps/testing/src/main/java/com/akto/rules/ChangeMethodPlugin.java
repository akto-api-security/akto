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
import com.akto.store.TestingUtil;
import com.akto.testing.ApiExecutor;
import com.akto.testing.StatusCodeAnalyser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class ChangeMethodPlugin extends TestPlugin {

    public abstract void modifyRequest(OriginalHttpRequest originalHttpRequest, URLMethods.Method method);

    public abstract boolean isVulnerable(double percentageBodyMatch, int statusCode);

    @Override
    public Result start(ApiInfo.ApiInfoKey apiInfoKey, TestingUtil testingUtil) {

        List<URLMethods.Method> undocumentedMethods = findUndocumentedMethods( testingUtil.getSampleMessages(), apiInfoKey);

        List<RawApi> messages = SampleMessageStore.fetchAllOriginalMessages(apiInfoKey, testingUtil.getSampleMessages());
        if (messages.isEmpty()) return null;

        RawApi rawApi = messages.get(0);

        boolean overallVulnerable = false;

        List<TestResult> testResults = new ArrayList<>();
        for (URLMethods.Method method: undocumentedMethods) {
            OriginalHttpRequest testRequest = rawApi.getRequest().copy();

            modifyRequest(testRequest, method);

            ApiExecutionDetails apiExecutionDetails;
            TestResult testResult;
            try {
                apiExecutionDetails = executeApiAndReturnDetails(testRequest, true, rawApi);
                boolean vulnerable = isVulnerable(apiExecutionDetails.percentageMatch, apiExecutionDetails.statusCode);
                overallVulnerable = overallVulnerable || vulnerable;

                testResult = buildTestResult(testRequest, apiExecutionDetails.testResponse, apiExecutionDetails.originalReqResp, apiExecutionDetails.percentageMatch, vulnerable, null);
            } catch (Exception e) {
                testResult = buildFailedTestResultWithOriginalMessage( rawApi.getOriginalMessage(), TestResult.TestError.API_REQUEST_FAILED, testRequest, null);
            }

            testResults.add(testResult);
        }

        return addTestSuccessResult(overallVulnerable, testResults, new ArrayList<>(), TestResult.Confidence.HIGH);

    }

    @Override
    public String superTestName() {
        return "PRIVILEGE_ESCALATION";
    }
}
