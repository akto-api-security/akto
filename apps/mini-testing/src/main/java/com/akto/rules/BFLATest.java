package com.akto.rules;

import com.akto.dao.testing.AccessMatrixUrlToRolesDao;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.testing.*;
import com.akto.dto.testing.info.BFLATestInfo;
import com.akto.dto.testing.sources.AuthWithCond;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.store.TestingUtil;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
        OriginalHttpRequest testRequest = rawApi.getRequest().copy();

        for (TestRoles testRoles: testingUtil.getTestRoles()) {
            Map<String, List<String>> reqHeaders = testRequest.getHeaders();

            for(AuthWithCond authWithCond: testRoles.getAuthWithCondList()) {
                boolean allHeadersMatched = true;
                if (authWithCond != null && authWithCond.getHeaderKVPairs() != null) {
                    for(String hKey: authWithCond.getHeaderKVPairs().keySet()) {
                        String hVal = authWithCond.getHeaderKVPairs().get(hKey);
                        if (reqHeaders.containsKey(hKey.toLowerCase())) {
                            if (reqHeaders.get(hKey.toLowerCase()).indexOf(hVal) == -1) {
                                allHeadersMatched = false;
                                break;
                            }
                        }
                    }
                }

                if (allHeadersMatched) {
                    AuthMechanism authMechanismForRole = authWithCond.getAuthMechanism();
                    if (authMechanismForRole.getType().equalsIgnoreCase(LoginFlowEnums.AuthMechanismTypes.LOGIN_REQUEST.name())) {
                        LoginFlowResponse loginFlowResponse = TestExecutor.executeLoginFlow(authMechanismForRole, null);
                        if (authWithCond.getRecordedLoginFlowInput() != null) {
                            authMechanismForRole.setRecordedLoginFlowInput(authWithCond.getRecordedLoginFlowInput());
                        }
                        if (!loginFlowResponse.getSuccess()) throw new Exception(loginFlowResponse.getError());

                        authMechanismForRole.setType(LoginFlowEnums.AuthMechanismTypes.HARDCODED.name());
                    }

                    authMechanismForRole.addAuthToRequest(testRequest);
                    break;
                }
            }


            RawApi rawApiDuplicate = rawApi.copy();
            try {
                TestPlugin.ApiExecutionDetails apiExecutionDetails = executeApiAndReturnDetails(testRequest, true, rawApiDuplicate);
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
