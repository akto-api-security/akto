package com.akto.rules;


import com.akto.dto.*;
import com.akto.dto.testing.*;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.store.SampleMessageStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;


public class NoAuthTest extends TestPlugin {

    @Override
    public Result  start(ApiInfo.ApiInfoKey apiInfoKey, AuthMechanism authMechanism, Map<ApiInfo.ApiInfoKey, List<String>> sampleMessages, Map<String, SingleTypeInfo> singleTypeInfoMap) {
        List<RawApi> messages = SampleMessageStore.fetchAllOriginalMessages(apiInfoKey, sampleMessages);
        if (messages.isEmpty()) return addWithoutRequestError(null, TestResult.TestError.NO_PATH);
        List<RawApi> filteredMessages = SampleMessageStore.filterMessagesWithAuthToken(messages, authMechanism);
        if (filteredMessages.isEmpty()) return addWithoutRequestError(null, TestResult.TestError.NO_MESSAGE_WITH_AUTH_TOKEN);

        RawApi rawApi = filteredMessages.get(0).copy();

        OriginalHttpRequest testRequest = rawApi.getRequest();
        OriginalHttpResponse originalHttpResponse = rawApi.getResponse();

        authMechanism.removeAuthFromRequest(testRequest);

        ApiExecutionDetails apiExecutionDetails;
        try {
             apiExecutionDetails = executeApiAndReturnDetails(testRequest, true, originalHttpResponse);
        } catch (Exception e) {
            return addWithRequestError( rawApi.getOriginalMessage(), TestResult.TestError.API_REQUEST_FAILED, testRequest);
        }

        boolean vulnerable = isStatusGood(apiExecutionDetails.statusCode);

        TestResult testResult = buildTestResult(
                testRequest, apiExecutionDetails.testResponse, rawApi.getOriginalMessage(), apiExecutionDetails.percentageMatch, vulnerable
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
