package com.akto.rules;

import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestRoles;
import com.akto.dto.testing.info.BFLATestInfo;
import com.akto.store.TestingUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class BFLATest extends AuthRequiredRunAllTestPlugin {

    public List<ExecutorResult> execute(RawApi rawApi, ApiInfo.ApiInfoKey apiInfoKey, TestingUtil testingUtil) {
        TestPlugin.TestRoleMatcher testRoleMatcher = new TestPlugin.TestRoleMatcher(testingUtil.getTestRoles(), apiInfoKey);

        TestRoles normalUserTestRole = new TestRoles();
        normalUserTestRole.setAuthMechanism(testingUtil.getAuthMechanism());
        testRoleMatcher.enemies.add(normalUserTestRole);

        List<ExecutorResult> executorResults = new ArrayList<>();

        for (TestRoles testRoles: testRoleMatcher.enemies) {
            OriginalHttpRequest testRequest = rawApi.getRequest().copy();

            testRoles.getAuthMechanism().addAuthToRequest(testRequest);
            BFLATestInfo bflaTestInfo = new BFLATestInfo(
                    "NORMAL", testingUtil.getTestRoles().get(0).getName()
            );

            ApiExecutionDetails apiExecutionDetails;
            RawApi rawApiDuplicate = rawApi.copy();
            try {
                apiExecutionDetails = executeApiAndReturnDetails(testRequest, true, rawApiDuplicate);
            } catch (Exception e) {
                return Collections.singletonList(new ExecutorResult(false, null, new ArrayList<>(), 0, rawApi,
                        TestResult.TestError.API_REQUEST_FAILED, testRequest, null, bflaTestInfo));
            }

            boolean vulnerable = isStatusGood(apiExecutionDetails.statusCode);
            TestResult.Confidence confidence = vulnerable ? TestResult.Confidence.HIGH : TestResult.Confidence.LOW;

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
