package com.akto.rules;

import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.testing.TestResult;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.store.TestingUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class ModifyAuthTokenTestPlugin extends AuthRequiredTestPlugin {

    public abstract List<Map<String, List<String>>> modifyHeaders(Map<String, List<String>> headers);
    @Override
    public Result exec(ApiInfo.ApiInfoKey apiInfoKey, TestingUtil testingUtil, List<RawApi> filteredMessages) {
        RawApi rawApi = filteredMessages.get(0).copy();

        OriginalHttpRequest testRequest = rawApi.getRequest();
        boolean overallVulnerable = false;

        List<Map<String, List<String>>> modifiedHeadersList = modifyHeaders(testRequest.getHeaders());
        if (modifiedHeadersList == null || modifiedHeadersList.isEmpty()) return null;

        List<TestResult> testResults = new ArrayList<>();
        for (Map<String, List<String>> modifiedHeaders: modifiedHeadersList) {
            if (modifiedHeaders == null || modifiedHeaders.isEmpty()) continue;
            testRequest.setHeaders(modifiedHeaders);

            ApiExecutionDetails apiExecutionDetails;
            try {
                apiExecutionDetails = executeApiAndReturnDetails(testRequest, true, rawApi);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error while after executing " + subTestName() +" test : " + e);
                return addWithRequestError( rawApi.getOriginalMessage(), TestResult.TestError.API_REQUEST_FAILED, testRequest, null);
            }

            boolean vulnerable = isStatusGood(apiExecutionDetails.statusCode);
            overallVulnerable = overallVulnerable || vulnerable;

            TestResult testResult = buildTestResult(
                    testRequest, apiExecutionDetails.testResponse, apiExecutionDetails.originalReqResp, apiExecutionDetails.percentageMatch, vulnerable, null
            );

            testResults.add(testResult);

        }

        if (testResults.isEmpty()) return null;

        return addTestSuccessResult(
                overallVulnerable, testResults, new ArrayList<>(), TestResult.Confidence.HIGH
        );

    }

}
