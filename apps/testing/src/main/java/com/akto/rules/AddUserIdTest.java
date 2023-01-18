package com.akto.rules;

import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestResult;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;
import com.akto.types.CappedSet;
import com.google.gson.Gson;

import java.util.*;

public class AddUserIdTest extends AuthRequiredTestPlugin{

    private static final Gson gson = new Gson();

    @Override
    public Result exec(ApiInfo.ApiInfoKey apiInfoKey, TestingUtil testingUtil, List<RawApi> filteredMessages) {
        List<String> userIdNameList = Arrays.asList(
                "user", "User", "userId", "UserId", "user_id", "customer_id", "customerId", "CustomerId", "customer",
                "user_name", "username", "UserName","customer_name"
        );

        Map<String, SingleTypeInfo> validUserIdNameMap = new HashMap<>();
        for (SingleTypeInfo singleTypeInfo: testingUtil.getSingleTypeInfoMap().values()) {
            String param = singleTypeInfo.getParam();
            String key = SingleTypeInfo.findLastKeyFromParam(param);
            if (key == null) continue;

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
        OriginalHttpRequest originalHttpRequest = rawApi.getRequest().copy();

        ApiExecutionDetails apiExecutionDetails;
        try {
            apiExecutionDetails = executeApiAndReturnDetails(testRequest, true, originalHttpResponse, originalHttpRequest);
        } catch (Exception e) {
            return addWithRequestError( rawApi.getOriginalMessage(), TestResult.TestError.API_REQUEST_FAILED, testRequest, null);
        }

        boolean vulnerable = isStatusGood(apiExecutionDetails.statusCode) && apiExecutionDetails.percentageMatch < 50;

        String originalMessage = rawApi.getOriginalMessage();

        Map<String, Object> json = gson.fromJson(originalMessage, Map.class);
        if (apiExecutionDetails.baseResponse != null) {
            json.put("responsePayload", apiExecutionDetails.baseResponse.getBody());
            originalMessage = gson.toJson(json);
        }

        TestResult testResult = buildTestResult(
                testRequest, apiExecutionDetails.testResponse, originalMessage, apiExecutionDetails.percentageMatch, vulnerable, null
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
