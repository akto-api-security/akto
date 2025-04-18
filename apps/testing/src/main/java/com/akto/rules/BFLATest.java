package com.akto.rules;

import com.akto.dao.testing.AccessMatrixUrlToRolesDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.testing.*;
import com.akto.dto.testing.sources.AuthWithCond;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.store.TestingUtil;
import com.akto.test_editor.execution.Executor;
import com.akto.testing.ApiExecutor;
import com.akto.testing.TestExecutor;
import com.akto.util.Constants;
import com.akto.util.enums.LoginFlowEnums;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.akto.rules.TestPlugin.isStatusGood;
import static com.akto.rules.TestPlugin.loggerMaker;

public class BFLATest {

    public TestPlugin.ApiExecutionDetails executeApiAndReturnDetails(OriginalHttpRequest testRequest, boolean followRedirects, RawApi rawApi) throws Exception {
        OriginalHttpResponse testResponse = ApiExecutor.sendRequest(testRequest, followRedirects, null, false, new ArrayList<>());

        OriginalHttpResponse originalHttpResponse = rawApi.getResponse().copy();

        int statusCode = testResponse.getStatusCode();
        String originalMessage = rawApi.getOriginalMessage();

        loggerMaker.debugAndAddToDb("Request: " + testRequest, LogDb.TESTING);
        String testResponseTrimmed = testResponse.getJsonResponseBody();
        if (testResponseTrimmed == null) {
            testResponseTrimmed = "";
        } else {
            testResponseTrimmed = testResponseTrimmed.substring(0, Math.min(500, testResponseTrimmed.length()));
        }
        loggerMaker.debugAndAddToDb("Response: " + testResponse.getStatusCode() + " "+ testResponse.getHeaders() + " " + testResponseTrimmed, LogDb.TESTING);

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

        Bson q = Filters.eq(Constants.ID, apiInfoKey);
        Bson update = Updates.addEachToSet(AccessMatrixUrlToRole.ROLES, ret);
        UpdateOptions opts = new UpdateOptions().upsert(true);
        AccessMatrixUrlToRolesDao.instance.getMCollection().updateOne(q, update, opts);
        loggerMaker.debugAndAddToDb("updated for " + apiInfoKey.getUrl() + " role: " + StringUtils.join(ret, ","), LogDb.TESTING);
        return ret;        
    }
}
