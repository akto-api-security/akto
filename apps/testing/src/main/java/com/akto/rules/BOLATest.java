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
            addWithoutRequestError(apiInfoKey, testRunId, null, TestResult.TestError.NO_PATH);
            return false;
        }

        OriginalHttpRequest originalHttpRequest = rawApi.getRequest();
        OriginalHttpResponse originalHttpResponse = rawApi.getResponse();

        AuthMechanism authMechanism = AuthMechanismStore.getAuthMechanism();
        if (authMechanism == null) {
            addWithoutRequestError(apiInfoKey, testRunId, rawApi.getOriginalMessage(), TestResult.TestError.NO_AUTH_MECHANISM);
            return false;
        }

        boolean result = authMechanism.addAuthToRequest(originalHttpRequest);
        if (!result) return false; // this means that auth token was not there in original request so exit

        ContainsPrivateResourceResult containsPrivateResourceResult = containsPrivateResource(originalHttpRequest, apiInfoKey);
        TestResult.Confidence confidence = containsPrivateResourceResult.findPrivateOnes().size() > 0 ? TestResult.Confidence.HIGH : TestResult.Confidence.LOW;

        OriginalHttpResponse response = null;
        try {
            response = ApiExecutor.sendRequest(originalHttpRequest, true);
        } catch (Exception e) {
            addWithRequestError(apiInfoKey, rawApi.getOriginalMessage(), testRunId, TestResult.TestError.API_REQUEST_FAILED, originalHttpRequest);
            return false;
        }

        int statusCode = StatusCodeAnalyser.getStatusCode(response.getBody(), response.getStatusCode());
        double percentageMatch = compareWithOriginalResponse(originalHttpResponse.getBody(), response.getBody());
        boolean vulnerable = isStatusGood(statusCode) && !containsPrivateResourceResult.isPrivate && percentageMatch > 90;

        addTestSuccessResult(apiInfoKey,originalHttpRequest, response, rawApi.getOriginalMessage(), testRunId,
                vulnerable, percentageMatch, containsPrivateResourceResult.singleTypeInfos, confidence);

        return vulnerable;
    }

    @Override
    public String testName() {
        return "BOLA";
    }



}
