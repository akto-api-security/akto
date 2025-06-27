package com.akto.rules;

import com.akto.util.data_actor.DataActor;
import com.akto.util.data_actor.DataActorFactory;
import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.testing.*;
import com.akto.dao.common.LoggerMaker.LogDb;
import com.akto.store.TestingUtil;
import com.akto.test_editor.execution.Executor;
import com.akto.testing.ApiExecutor;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

import static com.akto.rules.TestPlugin.isStatusGood;
import static com.akto.rules.TestPlugin.loggerMaker;

public class BFLATest {

    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    public TestPlugin.ApiExecutionDetails executeApiAndReturnDetails(OriginalHttpRequest testRequest, boolean followRedirects, RawApi rawApi) throws Exception {
        OriginalHttpResponse testResponse = ApiExecutor.sendRequest(testRequest, followRedirects, null, false, new ArrayList<>());

        OriginalHttpResponse originalHttpResponse = rawApi.getResponse().copy();

        int statusCode = testResponse.getStatusCode();
        String originalMessage = rawApi.getOriginalMessage();

        loggerMaker.infoAndAddToDb("Request: " + testRequest, LogDb.TESTING);
        String testResponseTrimmed = testResponse.getJsonResponseBody();
        if (testResponseTrimmed == null) {
            testResponseTrimmed = "";
        } else {
            testResponseTrimmed = testResponseTrimmed.substring(0, Math.min(500, testResponseTrimmed.length()));
        }
        loggerMaker.infoAndAddToDb("Response: " + testResponse.getStatusCode() + " "+ testResponse.getHeaders() + " " + testResponseTrimmed, LogDb.TESTING);

        return new TestPlugin.ApiExecutionDetails(statusCode, 0, testResponse, originalHttpResponse, originalMessage);
    }

    public List<String> updateAllowedRoles(RawApi rawApi, ApiInfo.ApiInfoKey apiInfoKey, TestingUtil testingUtil) throws Exception {
        List<String> ret = new ArrayList<>();
        for (TestRoles testRoles: testingUtil.getTestRoles()) {

            RawApi copiedApi = rawApi.copy();
            if (Executor.modifyAuthTokenInRawApi(testRoles, copiedApi) == null) {
                continue;
            }
            RawApi rawApiDuplicate = rawApi.copy();
            try {
                TestPlugin.ApiExecutionDetails apiExecutionDetails = executeApiAndReturnDetails(copiedApi.getRequest(), true, rawApiDuplicate);
                if(isStatusGood(apiExecutionDetails.statusCode)) {
                    ret.add(testRoles.getName());
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("BFLA Matrix update error" + e.toString(), LogDb.TESTING);
            }

        }
        dataActor.updateAccessMatrixUrlToRoles(apiInfoKey, ret);
        loggerMaker.infoAndAddToDb("updated for " + apiInfoKey.getUrl() + " role: " + StringUtils.join(ret, ","), LogDb.TESTING);
        return ret;        
    }
}
