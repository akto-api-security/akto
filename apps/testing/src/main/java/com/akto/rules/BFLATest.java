package com.akto.rules;

import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestRoles;
import com.akto.dto.testing.info.BFLATestInfo;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BFLATest extends TestPlugin {

    @Override
    public Result start(ApiInfo.ApiInfoKey apiInfoKey, TestingUtil testingUtil) {
        List<RawApi> messages = SampleMessageStore.fetchAllOriginalMessages(apiInfoKey, testingUtil.getSampleMessages());
        if (messages.isEmpty()) return null;
        List<RawApi> filteredMessages = SampleMessageStore.filterMessagesWithAuthToken(messages, testingUtil.getAuthMechanism());
        if (filteredMessages.isEmpty()) return null;


        boolean vulnerable = false;
        List<ExecutorResult> results = null;

        for (RawApi rawApi: filteredMessages) {
            if (vulnerable) break;
            results = execute(rawApi, testingUtil.getAuthMechanism(), testingUtil.getTestRoles());
            for (ExecutorResult result: results) {
                if (result.vulnerable) {
                    vulnerable = true;
                    break;
                }
            }
        }

        return convertExecutorResultsToResult(results);

    }


    public List<ExecutorResult> execute(RawApi rawApi, AuthMechanism authMechanism, List<TestRoles> testRoles) {
        OriginalHttpRequest testRequest = rawApi.getRequest().copy();
        OriginalHttpResponse originalHttpResponse = rawApi.getResponse().copy();

        authMechanism.addAuthToRequest(testRequest);
        TestRoles testRoles1 = testRoles.get(0);
        BFLATestInfo bflaTestInfo = new BFLATestInfo(
                "NORMAL", testRoles1.getName()
        );

        ApiExecutionDetails apiExecutionDetails;
        try {
            apiExecutionDetails = executeApiAndReturnDetails(testRequest, true, originalHttpResponse);
        } catch (Exception e) {
            return Collections.singletonList(new ExecutorResult(false, null, new ArrayList<>(), 0, rawApi,
                    TestResult.TestError.API_REQUEST_FAILED, testRequest, null, bflaTestInfo));
        }

        boolean vulnerable = isStatusGood(apiExecutionDetails.statusCode);
        TestResult.Confidence confidence = vulnerable ? TestResult.Confidence.HIGH : TestResult.Confidence.LOW;

        return Collections.singletonList(new ExecutorResult(vulnerable,confidence, null, apiExecutionDetails.percentageMatch,
                rawApi, null, testRequest, apiExecutionDetails.testResponse, bflaTestInfo));
    }

    @Override
    public String superTestName() {
        return "BFLA";
    }

    @Override
    public String subTestName() {
        return "BFLA";
    }

}
