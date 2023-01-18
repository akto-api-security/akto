package com.akto.rules;

import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.testing.TestResult;
import com.akto.store.TestingUtil;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class ModifyAuthTokenTestPlugin extends AuthRequiredTestPlugin {

    private static final Gson gson = new Gson();

    public abstract Map<String, List<String>> modifyHeaders(Map<String, List<String>> headers);
    @Override
    public Result exec(ApiInfo.ApiInfoKey apiInfoKey, TestingUtil testingUtil, List<RawApi> filteredMessages) {
        RawApi rawApi = filteredMessages.get(0).copy();

        OriginalHttpRequest testRequest = rawApi.getRequest();
        OriginalHttpResponse originalHttpResponse = rawApi.getResponse();

        Map<String, List<String>> modifiedHeaders = modifyHeaders(testRequest.getHeaders());
        if (modifiedHeaders == null || modifiedHeaders.isEmpty()) return null;
        testRequest.setHeaders(modifiedHeaders);

        ApiExecutionDetails apiExecutionDetails;
        try {
            OriginalHttpRequest originalHttpRequest = rawApi.getRequest().copy();
            apiExecutionDetails = executeApiAndReturnDetails(testRequest, true, originalHttpResponse, originalHttpRequest);
        } catch (Exception e) {
            return addWithRequestError( rawApi.getOriginalMessage(), TestResult.TestError.API_REQUEST_FAILED, testRequest, null);
        }

        boolean vulnerable = isStatusGood(apiExecutionDetails.statusCode);

        String originalMessage = rawApi.getOriginalMessage();

        Map<String, Object> json = gson.fromJson(originalMessage, Map.class);
        if (apiExecutionDetails.baseResponse != null) {
            json.put("responsePayload", apiExecutionDetails.baseResponse.getBody());
            originalMessage = gson.toJson(json);
        }

        TestResult testResult = buildTestResult(
                testRequest, apiExecutionDetails.testResponse, originalMessage, apiExecutionDetails.percentageMatch, vulnerable, null
        );

        return addTestSuccessResult(
                vulnerable, Collections.singletonList(testResult), new ArrayList<>(), TestResult.Confidence.HIGH
        );

    }

}
