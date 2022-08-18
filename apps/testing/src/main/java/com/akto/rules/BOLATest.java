package com.akto.rules;

import com.akto.dto.ApiInfo;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestResult;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.store.AuthMechanismStore;
import com.akto.store.SampleMessageStore;
import com.akto.testing.ApiExecutor;
import com.akto.testing.StatusCodeAnalyser;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

public class BOLATest extends TestPlugin {

    public BOLATest() { }

    @Override
    public boolean start(ApiInfo.ApiInfoKey apiInfoKey, ObjectId testRunId) {
        HttpResponseParams originalHttpResponseParams = SampleMessageStore.fetchOriginalMessage(apiInfoKey);
        if (originalHttpResponseParams == null) {
            addWithoutRequestError(apiInfoKey, testRunId, TestResult.TestError.NO_PATH);
            return false;
        }

        AuthMechanism authMechanism = AuthMechanismStore.getAuthMechanism();
        if (authMechanism == null) {
            addWithoutRequestError(apiInfoKey, testRunId, TestResult.TestError.NO_AUTH_MECHANISM);
            return false;
        }

        HttpRequestParams httpRequestParams = originalHttpResponseParams.getRequestParams();
        boolean result = authMechanism.addAuthToRequest(httpRequestParams);
        if (!result) return false; // this means that auth token was not there in original request so exit

        ContainsPrivateResourceResult containsPrivateResourceResult = containsPrivateResource(httpRequestParams, apiInfoKey);
        if (!containsPrivateResourceResult.isPrivate) { // contains 1 or more public parameters... so don't test
            HttpResponseParams newHttpResponseParams = generateEmptyResponsePayload(httpRequestParams);
            addTestSuccessResult(apiInfoKey, newHttpResponseParams, testRunId, false, new ArrayList<>());
            return false;
        }

        HttpResponseParams httpResponseParams = null;
        try {
            httpResponseParams = ApiExecutor.sendRequest(httpRequestParams);
        } catch (Exception e) {
            HttpResponseParams newHttpResponseParams = generateEmptyResponsePayload(httpRequestParams);
            addWithRequestError(apiInfoKey, testRunId, TestResult.TestError.API_REQUEST_FAILED, newHttpResponseParams);
            return false;
        }

        int statusCode = StatusCodeAnalyser.getStatusCode(httpResponseParams);
        boolean vulnerable = isStatusGood(statusCode);
        if (vulnerable) {
            double val = compareWithOriginalResponse(originalHttpResponseParams.getPayload(), httpResponseParams.getPayload());
            vulnerable = val > 90;
        }

        addTestSuccessResult(apiInfoKey,httpResponseParams, testRunId, vulnerable, containsPrivateResourceResult.findPrivateOnes());

        return vulnerable;
    }

    @Override
    public String testName() {
        return "BOLA";
    }



}
