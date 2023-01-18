package com.akto.rules;


import com.akto.dto.*;
import com.akto.dto.testing.*;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;


public class NoAuthTest extends AuthRequiredTestPlugin {

    private static final Gson gson = new Gson();

    @Override
    public Result exec(ApiInfo.ApiInfoKey apiInfoKey, TestingUtil testingUtil, List<RawApi> filteredMessages) {
        RawApi rawApi = filteredMessages.get(0).copy();

        OriginalHttpRequest testRequest = rawApi.getRequest();
        OriginalHttpResponse originalHttpResponse = rawApi.getResponse();

        testingUtil.getAuthMechanism().removeAuthFromRequest(testRequest);

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

    @Override
    public String superTestName() {
        return "NO_AUTH";
    }

    @Override
    public String subTestName() {
        return "REMOVE_TOKENS";
    }

}
