package com.akto.rules;

import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestResult;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.store.SampleMessageStore;
import com.akto.types.CappedSet;

import java.util.*;

public class AddUserIdTest extends TestPlugin {

    @Override
    public Result start(ApiInfo.ApiInfoKey apiInfoKey, AuthMechanism authMechanism, Map<ApiInfo.ApiInfoKey, List<String>> sampleMessages, Map<String, SingleTypeInfo> singleTypeInfos) {

        List<RawApi> messages = SampleMessageStore.fetchAllOriginalMessages(apiInfoKey, sampleMessages);
        if (messages.isEmpty()) return addWithoutRequestError(null, TestResult.TestError.NO_PATH);
        List<RawApi> filteredMessages = SampleMessageStore.filterMessagesWithAuthToken(messages, authMechanism);
        if (filteredMessages.isEmpty()) return addWithoutRequestError(null, TestResult.TestError.NO_MESSAGE_WITH_AUTH_TOKEN);

        List<String> userIdNameList = Arrays.asList(
                "user", "User", "userId", "UserId", "user_id", "customer_id", "customerId", "CustomerId", "customer",
                "user_name", "username", "UserName","customer_name"
        );

        Map<String, SingleTypeInfo> validUserIdNameMap = new HashMap<>();
        for (SingleTypeInfo singleTypeInfo: singleTypeInfos.values()) {
            String param = singleTypeInfo.getParam();
            if (param == null) continue;
            String paramReplaced = param.replaceAll("#", ".").replaceAll("\\.\\$", "");
            String[] paramList = paramReplaced.split("\\.");
            String key = paramList[paramList.length-1]; // choosing the last key

            CappedSet<String> values = singleTypeInfo.getValues();
            if (values.count() == 0) continue;
            if (userIdNameList.contains(key)) validUserIdNameMap.put(key,singleTypeInfo);
        }

        if (validUserIdNameMap.isEmpty()) return null;

        RawApi rawApi = filteredMessages.get(0);
        List<TestResult> testResults = new ArrayList<>();
        OriginalHttpRequest testRequest = rawApi.getRequest().copy();

        for (String key: validUserIdNameMap.keySet()) {
            SingleTypeInfo singleTypeInfo = validUserIdNameMap.get(key);
            CappedSet<String> values = singleTypeInfo.getValues();
            if (values.count() == 0) continue;
            String val = (String) values.getElements().toArray()[0];
            String combinedQueryParams = OriginalHttpRequest.combineQueryParams(testRequest.getQueryParams(), key + "=" + val );
            testRequest.setQueryParams(combinedQueryParams);
        }

        OriginalHttpResponse originalHttpResponse = rawApi.getResponse().copy();

        ApiExecutionDetails apiExecutionDetails;
        try {
            apiExecutionDetails = executeApiAndReturnDetails(testRequest, true, originalHttpResponse);
        } catch (Exception e) {
            return addWithRequestError( rawApi.getOriginalMessage(), TestResult.TestError.API_REQUEST_FAILED, testRequest);
        }

        boolean vulnerable = isStatusGood(apiExecutionDetails.statusCode) && apiExecutionDetails.percentageMatch < 50;

        TestResult testResult = buildTestResult(
                testRequest, apiExecutionDetails.testResponse, rawApi.getOriginalMessage(), apiExecutionDetails.percentageMatch, vulnerable
        );

        testResults.add(testResult);

        return addTestSuccessResult(vulnerable, testResults, new ArrayList<>(), TestResult.Confidence.HIGH);

    }


    @Override
    public String superTestName() {
        return "ADD_USER_ID";
    }

    @Override
    public String subTestName() {
        return "ADD_USER_ID";
    }
}
