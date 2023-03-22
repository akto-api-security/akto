package com.akto.rules;

import com.akto.dao.testing.AccessMatrixUrlToRolesDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestRoles;
import com.akto.dto.testing.info.BFLATestInfo;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.store.TestingUtil;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BFLATest extends AuthRequiredRunAllTestPlugin {

    public List<String> updateAllowedRoles(RawApi rawApi, ApiInfo.ApiInfoKey apiInfoKey, TestingUtil testingUtil) {
        List<String> ret = new ArrayList<>();
        OriginalHttpRequest testRequest = rawApi.getRequest().copy();
        testingUtil.getAuthMechanism().addAuthToRequest(testRequest);
        ApiExecutionDetails apiExecutionDetails;
        RawApi rawApiDuplicate = rawApi.copy();
        try {
            apiExecutionDetails = executeApiAndReturnDetails(testRequest, true, rawApiDuplicate);

            System.out.println("orig: " + apiInfoKey.url + " "+ apiExecutionDetails.statusCode);

            if (!isStatusGood(apiExecutionDetails.statusCode)) {
                return ret;
            }
        } catch (Exception e) {
            return ret;
        }

        for (TestRoles testRoles: testingUtil.getTestRoles()) {
            testRoles.getAuthMechanism().addAuthToRequest(testRequest);
            rawApiDuplicate = rawApi.copy();
            try {
                apiExecutionDetails = executeApiAndReturnDetails(testRequest, true, rawApiDuplicate);
            } catch (Exception e) {

            }
            System.out.println("role: " + testRoles.getName() + " " + apiInfoKey.url + " " + testRequest.getBody() + " " + testRequest.getHeaders());
            for (String hh: testRequest.getHeaders().keySet()) {
                System.out.println(hh+ ": " + testRequest.getHeaders().get(hh));
            }
            System.out.println(apiExecutionDetails.statusCode + " " + (apiExecutionDetails.statusCode == 200 ? apiExecutionDetails.testResponse.getBody(): ""));

            if(isStatusGood(apiExecutionDetails.statusCode)) {
                ret.add(testRoles.getName());
            }
        }


        Bson q = Filters.eq("_id", apiInfoKey);
        Bson update = Updates.addEachToSet("roles", ret);
        UpdateOptions opts = new UpdateOptions().upsert(true);
        AccessMatrixUrlToRolesDao.instance.getMCollection().updateOne(q, update, opts);

        return ret;        
    }

    public List<ExecutorResult> execute(RawApi rawApi, ApiInfo.ApiInfoKey apiInfoKey, TestingUtil testingUtil) {

        updateAllowedRoles(rawApi, apiInfoKey, testingUtil);

        TestPlugin.TestRoleMatcher testRoleMatcher = new TestPlugin.TestRoleMatcher(testingUtil.getTestRoles(), apiInfoKey);

        TestRoles normalUserTestRole = new TestRoles();
        normalUserTestRole.setAuthMechanism(testingUtil.getAuthMechanism());
        testRoleMatcher.enemies.add(normalUserTestRole);

        List<ExecutorResult> executorResults = new ArrayList<>();

        for (TestRoles testRoles: testRoleMatcher.enemies) {
            OriginalHttpRequest testRequest = rawApi.getRequest().copy();

            testRoles.getAuthMechanism().addAuthToRequest(testRequest);
            BFLATestInfo bflaTestInfo = new BFLATestInfo(
                    "NORMAL", testingUtil.getTestRoles().get(0).getName()
            );

            ApiExecutionDetails apiExecutionDetails;
            RawApi rawApiDuplicate = rawApi.copy();
            try {
                apiExecutionDetails = executeApiAndReturnDetails(testRequest, true, rawApiDuplicate);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error while after executing " + subTestName() +"test : " + e,LogDb.TESTING);
                return Collections.singletonList(new ExecutorResult(false, null, new ArrayList<>(), 0, rawApi,
                        TestResult.TestError.API_REQUEST_FAILED, testRequest, null, bflaTestInfo));
            }

            boolean vulnerable = isStatusGood(apiExecutionDetails.statusCode);
            TestResult.Confidence confidence = vulnerable ? TestResult.Confidence.HIGH : TestResult.Confidence.LOW;

            ExecutorResult executorResult = new ExecutorResult(vulnerable,confidence, null, apiExecutionDetails.percentageMatch,
            rawApiDuplicate, null, testRequest, apiExecutionDetails.testResponse, bflaTestInfo);

            executorResults.add(executorResult);
        }

        return executorResults;

    }

    @Override
    public String superTestName() {
        return "BFLA";
    }

    @Override
    public String subTestName() {
        return "BFLA";
    }

}
