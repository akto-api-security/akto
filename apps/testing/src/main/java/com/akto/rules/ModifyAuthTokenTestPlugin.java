package com.akto.rules;

import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.testing.TestResult;
import com.akto.store.TestingUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class ModifyAuthTokenTestPlugin extends AuthRequiredTestPlugin {

    public abstract Map<String, List<String>> modifyHeaders(Map<String, List<String>> headers);
    @Override
    public Result exec(ApiInfo.ApiInfoKey apiInfoKey, TestingUtil testingUtil, List<RawApi> filteredMessages) {
        RawApi rawApi = filteredMessages.get(0).copy();

        OriginalHttpRequest testRequest = rawApi.getRequest();

        Map<String, List<String>> modifiedHeaders = modifyHeaders(testRequest.getHeaders());
        if (modifiedHeaders == null || modifiedHeaders.isEmpty()) return null;
        testRequest.setHeaders(modifiedHeaders);

        ApiExecutionDetails apiExecutionDetails;
        try {
            apiExecutionDetails = executeApiAndReturnDetails(testRequest, true, rawApi);
        } catch (Exception e) {
            return addWithRequestError( rawApi.getOriginalMessage(), TestResult.TestError.API_REQUEST_FAILED, testRequest, null);
        }

        boolean vulnerable = isStatusGood(apiExecutionDetails.statusCode);

        TestResult testResult = buildTestResult(
                testRequest, apiExecutionDetails.testResponse, apiExecutionDetails.originalReqResp, apiExecutionDetails.percentageMatch, vulnerable, null
        );

        return addTestSuccessResult(
                vulnerable, Collections.singletonList(testResult), new ArrayList<>(), TestResult.Confidence.HIGH
        );

    }

}
