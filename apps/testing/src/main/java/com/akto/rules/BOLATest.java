package com.akto.rules;

import com.akto.dto.*;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestResult;
import com.akto.store.AuthMechanismStore;
import com.akto.store.SampleMessageStore;
import com.akto.testing.ApiExecutor;
import com.akto.testing.StatusCodeAnalyser;
import org.bson.types.ObjectId;

import java.util.HashMap;

public class BOLATest extends TestPlugin {

    public BOLATest() { }

    @Override
    public boolean start(ApiInfo.ApiInfoKey apiInfoKey, ObjectId testRunId) {
        RawApi rawApi = SampleMessageStore.fetchOriginalMessage(apiInfoKey);
        if (rawApi == null) {
            addWithoutRequestError(apiInfoKey, testRunId, TestResult.TestError.NO_PATH);
            return false;
        }

        OriginalHttpRequest originalHttpRequest = rawApi.getRequest();
        OriginalHttpResponse originalHttpResponse = rawApi.getResponse();

        AuthMechanism authMechanism = AuthMechanismStore.getAuthMechanism();
        if (authMechanism == null) {
            addWithoutRequestError(apiInfoKey, testRunId, TestResult.TestError.NO_AUTH_MECHANISM);
            return false;
        }

        boolean result = authMechanism.addAuthToRequest(originalHttpRequest);
        if (!result) return false; // this means that auth token was not there in original request so exit

        ContainsPrivateResourceResult containsPrivateResourceResult = containsPrivateResource(originalHttpRequest, apiInfoKey);
        if (!containsPrivateResourceResult.isPrivate) { // contains 1 or more public parameters... so don't test
            OriginalHttpResponse newOriginalHttpResponse= new OriginalHttpResponse(null, new HashMap<>(), 0);
            addTestSuccessResult(apiInfoKey, originalHttpRequest,  newOriginalHttpResponse, testRunId, false);
            return false;
        }

        OriginalHttpResponse response = null;
        try {
            response = ApiExecutor.sendRequest(originalHttpRequest, true);
        } catch (Exception e) {
            addWithRequestError(apiInfoKey, testRunId, TestResult.TestError.API_REQUEST_FAILED, originalHttpRequest);
            return false;
        }

        int statusCode = StatusCodeAnalyser.getStatusCode(response.getBody(), response.getStatusCode());
        boolean vulnerable = isStatusGood(statusCode);
        if (vulnerable) {
            double val = compareWithOriginalResponse(originalHttpResponse.getBody(), response.getBody());
            vulnerable = val > 90;
        }

        addTestSuccessResult(apiInfoKey,originalHttpRequest, response, testRunId, vulnerable);

        return vulnerable;
    }

    @Override
    public String testName() {
        return "BOLA";
    }



}
