package com.akto.rules;


import com.akto.dto.*;
import com.akto.dto.testing.*;
import com.akto.store.AuthMechanismStore;
import com.akto.store.SampleMessageStore;
import com.akto.testing.ApiExecutor;
import com.akto.testing.StatusCodeAnalyser;
import org.bson.types.ObjectId;


public class NoAuthTest extends TestPlugin {

    @Override
    public boolean start(ApiInfo.ApiInfoKey apiInfoKey, ObjectId testRunId) {
        RawApi rawApi = SampleMessageStore.fetchOriginalMessage(apiInfoKey);
        if (rawApi == null) {
            addWithoutRequestError(apiInfoKey, testRunId, TestResult.TestError.NO_PATH);
            return false;
        }

        OriginalHttpRequest originalHttpRequest = rawApi.getRequest();

        AuthMechanism authMechanism = AuthMechanismStore.getAuthMechanism();
        if (authMechanism == null) {
            addWithoutRequestError(apiInfoKey, testRunId, TestResult.TestError.NO_AUTH_MECHANISM);
            return false;
        }

        boolean result = authMechanism.removeAuthFromRequest(originalHttpRequest);
        if (!result) return false;

        OriginalHttpResponse response = null;
        try {
            response = ApiExecutor.sendRequest(originalHttpRequest);
        } catch (Exception e) {
            addWithRequestError(apiInfoKey, testRunId, TestResult.TestError.API_REQUEST_FAILED, originalHttpRequest);
            return false;
        }

        int statusCode = StatusCodeAnalyser.getStatusCode(response.getBody(), response.getStatusCode());
        boolean vulnerable = isStatusGood(statusCode);

        addTestSuccessResult(apiInfoKey, originalHttpRequest, response, testRunId, vulnerable);

        return vulnerable;
    }

    @Override
    public String testName() {
        return "NO_AUTH";
    }
}
