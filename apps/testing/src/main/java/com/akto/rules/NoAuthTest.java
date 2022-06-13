package com.akto.rules;


import com.akto.dto.ApiInfo;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.testing.*;
import com.akto.store.AuthMechanismStore;
import com.akto.store.SampleMessageStore;
import com.akto.testing.ApiExecutor;
import com.akto.testing.StatusCodeAnalyser;
import org.bson.types.ObjectId;


public class NoAuthTest extends TestPlugin {

    @Override
    public boolean start(ApiInfo.ApiInfoKey apiInfoKey, ObjectId testRunId) {
        HttpRequestParams httpRequestParams = SampleMessageStore.fetchPath(apiInfoKey);
        if (httpRequestParams == null) {
            addWithoutRequestError(apiInfoKey, testRunId, TestResult.TestError.NO_PATH);
            return false;
        }

        AuthMechanism authMechanism = AuthMechanismStore.getAuthMechanism();
        if (authMechanism == null) {
            addWithoutRequestError(apiInfoKey, testRunId, TestResult.TestError.NO_AUTH_MECHANISM);
            return false;
        }

        boolean result = authMechanism.removeAuthFromRequest(httpRequestParams);
        if (!result) return false;
        
        HttpResponseParams httpResponseParams = null;
        try {
            httpResponseParams = ApiExecutor.sendRequest(httpRequestParams);
        } catch (Exception e) {
            HttpResponseParams newHttpResponseParams = generateEmptyResponsePayload(httpRequestParams);
            addWithRequestError(apiInfoKey, testRunId, TestResult.TestError.API_REQUEST_FAILED, newHttpResponseParams);
            // TODO:
            return false;
        }

        int statusCode = StatusCodeAnalyser.getStatusCode(httpResponseParams);
        boolean vulnerable = isStatusGood(statusCode);

        addTestSuccessResult(apiInfoKey, httpResponseParams,testRunId, vulnerable);

        return vulnerable;
    }

    @Override
    public String testName() {
        return "NO_AUTH";
    }
}
