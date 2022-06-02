package com.akto.rules;


import com.akto.DaoInit;
import com.akto.dao.AuthMechanismsDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.store.AuthMechanismStore;
import com.akto.store.SampleMessageStore;
import com.akto.testing.ApiExecutor;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import org.bson.types.ObjectId;

import java.util.List;

public class NoAuthTest extends TestPlugin {

    @Override
    public void start(ApiInfo.ApiInfoKey apiInfoKey, ObjectId testRunId) {
        HttpRequestParams httpRequestParams = SampleMessageStore.fetchPath(apiInfoKey);
        if (httpRequestParams == null) {
            addWithoutRequestError(apiInfoKey, testRunId, TestResult.TestError.NO_PATH);
            return;
        }

        AuthMechanism authMechanism = AuthMechanismStore.getAuthMechanism();
        if (authMechanism == null) {
            addWithoutRequestError(apiInfoKey, testRunId, TestResult.TestError.NO_AUTH_MECHANISM);
            return;
        }

        authMechanism.removeAuthFromRequest(httpRequestParams);

        HttpResponseParams httpResponseParams = null;
        try {
            httpResponseParams = ApiExecutor.sendRequest(httpRequestParams);
        } catch (Exception e) {
            e.printStackTrace();
            HttpResponseParams newHttpResponseParams = generateEmptyResponsePayload(httpRequestParams);
            addWithRequestError(apiInfoKey, testRunId, TestResult.TestError.API_REQUEST_FAILED, newHttpResponseParams);
            // TODO:
            return ;
        }

        boolean vulnerable = isStatusGood(httpResponseParams);

        addTestSuccessResult(httpResponseParams, testRunId, vulnerable);

    }

    @Override
    public String testName() {
        return "NO_AUTH";
    }
}
