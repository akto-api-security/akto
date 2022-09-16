package com.akto.rules;


import com.akto.dto.*;
import com.akto.dto.testing.*;
import com.akto.store.AuthMechanismStore;
import com.akto.store.SampleMessageStore;
import com.akto.testing.ApiExecutor;
import com.akto.testing.StatusCodeAnalyser;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;


public class NoAuthTest extends TestPlugin {

    @Override
    public boolean start(ApiInfo.ApiInfoKey apiInfoKey, ObjectId testRunId, AuthMechanism authMechanism) {
        List<RawApi> filteredMessages = fetchMessagesWithAuthToken(apiInfoKey, testRunId, authMechanism);
        if (filteredMessages == null) return false;

        RawApi rawApi = filteredMessages.get(0);

        OriginalHttpRequest originalHttpRequest = rawApi.getRequest();
        OriginalHttpResponse originalHttpResponse = rawApi.getResponse();

        authMechanism.removeAuthFromRequest(originalHttpRequest);

        OriginalHttpResponse response = null;
        try {
            response = ApiExecutor.sendRequest(originalHttpRequest, true);
        } catch (Exception e) {
            addWithRequestError(apiInfoKey, rawApi.getOriginalMessage(), testRunId, TestResult.TestError.API_REQUEST_FAILED, originalHttpRequest);
            return false;
        }

        int statusCode = StatusCodeAnalyser.getStatusCode(response.getBody(), response.getStatusCode());
        boolean vulnerable = isStatusGood(statusCode);

        double percentageMatch = compareWithOriginalResponse(originalHttpResponse.getBody(), response.getBody());

        addTestSuccessResult(apiInfoKey, originalHttpRequest, response, rawApi.getOriginalMessage(), testRunId,
                vulnerable, percentageMatch, new ArrayList<>(), TestResult.Confidence.HIGH);

        return vulnerable;
    }

    @Override
    public String testName() {
        return "NO_AUTH";
    }
}
