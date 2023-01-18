package com.akto.rules;

import com.akto.dto.*;
import com.akto.dto.testing.*;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;
import com.akto.types.CappedSet;

import java.util.*;

public class ParameterPollutionTest extends TestPlugin {

    public ParameterPollutionTest() {}


    @Override
    public Result start(ApiInfo.ApiInfoKey apiInfoKey, TestingUtil testingUtil) {
        List<RawApi> messages = SampleMessageStore.fetchAllOriginalMessages(apiInfoKey, testingUtil.getSampleMessages());
        if (messages.isEmpty()) return null;
        List<RawApi> filteredMessages = SampleMessageStore.filterMessagesWithAuthToken(messages, testingUtil.getAuthMechanism());
        if (filteredMessages.size() < 2) return addWithoutRequestError(null, TestResult.TestError.INSUFFICIENT_MESSAGES);

        RawApi message1 = filteredMessages.get(0).copy();
        RawApi message2 = filteredMessages.get(1).copy();

        OriginalHttpRequest testRequest1 = message1.getRequest();
        OriginalHttpRequest testRequest2 = message2.getRequest();

        ContainsPrivateResourceResult containsPrivateResourceResult2 = containsPrivateResource(testRequest2, apiInfoKey, testingUtil.getSingleTypeInfoMap());

        boolean atLeastOneParam = false;
        for (SingleTypeInfo singleTypeInfo: containsPrivateResourceResult2.findPrivateOnes()) {
            if (singleTypeInfo.getIsUrlParam()) continue;

            String param = SingleTypeInfo.findLastKeyFromParam(singleTypeInfo.getParam());
            if (param == null || param.trim().length() < 1) continue;

            CappedSet<String> values = singleTypeInfo.getValues();
            if (values.count() == 0) continue;

            String value = values.getElements().toArray()[0].toString();
            atLeastOneParam = true;

            String ogQuery = testRequest1.getQueryParams();
            String combinedQueryParams = OriginalHttpRequest.combineQueryParams(ogQuery, param+"="+value);
            testRequest1.setQueryParams(combinedQueryParams);
        }

        if (!atLeastOneParam) return null;

        ApiExecutionDetails apiExecutionDetails;
        try {
            apiExecutionDetails = executeApiAndReturnDetails(testRequest1, true, message1);
        } catch (Exception e) {
            return addWithRequestError( message1.getOriginalMessage(), TestResult.TestError.API_REQUEST_FAILED, testRequest1, null);
        }

        boolean vulnerable = isStatusGood(apiExecutionDetails.statusCode) && apiExecutionDetails.percentageMatch < 80;

        TestResult testResult = buildTestResult(
                testRequest1, apiExecutionDetails.testResponse, apiExecutionDetails.originalReqResp, apiExecutionDetails.percentageMatch, vulnerable, null
        );
        return addTestSuccessResult(
                vulnerable, Collections.singletonList(testResult), new ArrayList<>(), TestResult.Confidence.HIGH
        );


    }

    @Override
    public String superTestName() {
        return "BOLA";
    }

    @Override
    public String subTestName() {
        return "PARAMETER_POLLUTION";
    }
    
}
