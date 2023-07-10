package com.akto.rules;

import com.akto.dao.testing.AccessMatrixUrlToRolesDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.RawApi;
import com.akto.dto.testing.AccessMatrixUrlToRole;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestRoles;
import com.akto.dto.testing.info.BFLATestInfo;
import com.akto.dto.testing.sources.AuthWithCond;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.store.TestingUtil;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.akto.rules.TestPlugin.isStatusGood;
import static com.akto.rules.TestPlugin.loggerMaker;

public class BFLATest {

    public List<String> updateAllowedRoles(RawApi rawApi, ApiInfo.ApiInfoKey apiInfoKey, TestingUtil testingUtil) {
        List<String> ret = new ArrayList<>();
        OriginalHttpRequest testRequest = rawApi.getRequest().copy();

        /*
        testingUtil.getAuthMechanism().addAuthToRequest(testRequest);
        ApiExecutionDetails apiExecutionDetails;
        RawApi rawApiDuplicate = rawApi.copy();
        try {
            apiExecutionDetails = executeApiAndReturnDetails(testRequest, true, rawApiDuplicate);

            if (!isStatusGood(apiExecutionDetails.statusCode)) {
                return ret;
            }
        } catch (Exception e) {
            return ret;
        }
        */

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
                    authWithCond.getAuthMechanism().addAuthToRequest(testRequest);
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

        Bson q = Filters.eq(Constants.ID, apiInfoKey);
        Bson update = Updates.addEachToSet(AccessMatrixUrlToRole.ROLES, ret);
        UpdateOptions opts = new UpdateOptions().upsert(true);
        AccessMatrixUrlToRolesDao.instance.getMCollection().updateOne(q, update, opts);

        return ret;        
    }
}
