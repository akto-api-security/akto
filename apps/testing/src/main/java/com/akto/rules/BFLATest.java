package com.akto.rules;

import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestRoles;
import com.akto.dto.testing.info.BFLATestInfo;
import com.akto.store.TestingUtil;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class BFLATest extends AuthRequiredRunAllTestPlugin {

    private static final Gson gson = new Gson();

    public List<ExecutorResult> execute(RawApi rawApi, ApiInfo.ApiInfoKey apiInfoKey, TestingUtil testingUtil) {
        TestPlugin.TestRoleMatcher testRoleMatcher = new TestPlugin.TestRoleMatcher(testingUtil.getTestRoles(), apiInfoKey);

        TestRoles normalUserTestRole = new TestRoles();
        normalUserTestRole.setAuthMechanism(testingUtil.getAuthMechanism());
        testRoleMatcher.enemies.add(normalUserTestRole);

        OriginalHttpResponse originalHttpResponse = rawApi.getResponse().copy();
        List<ExecutorResult> executorResults = new ArrayList<>();

        for (TestRoles testRoles: testRoleMatcher.enemies) {
            OriginalHttpRequest testRequest = rawApi.getRequest().copy();

            testRoles.getAuthMechanism().addAuthToRequest(testRequest);
            BFLATestInfo bflaTestInfo = new BFLATestInfo(
                    "NORMAL", testingUtil.getTestRoles().get(0).getName()
            );

            ApiExecutionDetails apiExecutionDetails;
            try {
                OriginalHttpRequest originalHttpRequest = rawApi.getRequest().copy();
                apiExecutionDetails = executeApiAndReturnDetails(testRequest, true, originalHttpResponse, originalHttpRequest);
            } catch (Exception e) {
                return Collections.singletonList(new ExecutorResult(false, null, new ArrayList<>(), 0, rawApi,
                        TestResult.TestError.API_REQUEST_FAILED, testRequest, null, bflaTestInfo));
            }

            boolean vulnerable = isStatusGood(apiExecutionDetails.statusCode);
            TestResult.Confidence confidence = vulnerable ? TestResult.Confidence.HIGH : TestResult.Confidence.LOW;

            RawApi rawApiDuplicate = rawApi.copy();

            String originalMessage = rawApiDuplicate.getOriginalMessage();

            Map<String, Object> json = gson.fromJson(originalMessage, Map.class);
            if (apiExecutionDetails.baseResponse != null) {
                json.put("responsePayload", apiExecutionDetails.baseResponse.getBody());
                originalMessage = gson.toJson(json);
            }

            rawApiDuplicate.setOriginalMessage(originalMessage);

            ExecutorResult executorResult = new ExecutorResult(vulnerable,confidence, null, apiExecutionDetails.percentageMatch,
            rawApiDuplicate, null, testRequest, apiExecutionDetails.testResponse, bflaTestInfo);

            executorResults.add(executorResult);
        }

        return executorResults;

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
