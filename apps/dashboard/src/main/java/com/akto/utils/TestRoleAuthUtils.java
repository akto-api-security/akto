package com.akto.utils;

import com.akto.dao.testing.TestRolesDao;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.test_editor.ExecutorSingleOperationResp;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.AuthParam;
import com.akto.dto.testing.LoginFlowResponse;
import com.akto.dto.testing.TestRoles;
import com.akto.testing.TestExecutor;
import com.akto.util.Constants;
import com.akto.util.enums.LoginFlowEnums;
import com.mongodb.client.model.Filters;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestRoleAuthUtils {

    /**
     * Mirrors Executor.modifyAuthTokenInRawApi (apps/testing), with two differences needed here:
     * a failed role is an error (not a silent unauthenticated call), and header-type auth params are
     * added even when the request doesn't carry that header yet (AuthMechanism.addAuthToRequest only
     * replaces headers that already exist, and this request is built from scratch).
     */
    public static void applyTestRoleAuth(OriginalHttpRequest request, String roleId) throws Exception {
        TestRoles testRole = TestRolesDao.instance.findOne(Filters.eq(Constants.ID, new ObjectId(roleId)));
        if (testRole == null) {
            throw new Exception("Test role not found: " + roleId);
        }
        AuthMechanism authMechanism = testRole.findDefaultAuthMechanism();
        if (authMechanism == null || authMechanism.getAuthParams() == null || authMechanism.getAuthParams().isEmpty()) {
            throw new Exception("No default auth mechanism configured for test role: " + testRole.getName());
        }

        // LOGIN_REQUEST (incl. recorded flows) params are templates like ${x1.response.body...};
        // they only hold real values after the login flow has run and filled the cached params.
        boolean usesCachedParams = LoginFlowEnums.AuthMechanismTypes.LOGIN_REQUEST.toString().equalsIgnoreCase(authMechanism.getType());
        if (usesCachedParams) {
            LoginFlowResponse loginFlowResponse = TestExecutor.executeLoginFlow(authMechanism, null, testRole.getName());
            if (loginFlowResponse == null || !Boolean.TRUE.equals(loginFlowResponse.getSuccess())) {
                String reason = loginFlowResponse != null && !StringUtils.isEmpty(loginFlowResponse.getError())
                        ? loginFlowResponse.getError() : "login flow did not succeed";
                throw new Exception("Could not fetch auth for test role " + testRole.getName() + ": " + reason);
            }
        }

        List<AuthParam> paramsToUse = authMechanism.getAuthParamsFromAuthMechanism();
        if (paramsToUse == null || paramsToUse.isEmpty()) {
            throw new Exception("No resolved auth params for test role: " + testRole.getName());
        }
        for (AuthParam authParam : paramsToUse) {
            if (authParam.getKey() != null && authParam.getWhere() == AuthParam.Location.HEADER) {
                request.getHeaders().putIfAbsent(authParam.getKey().toLowerCase().trim(), new ArrayList<>(Arrays.asList("")));
            }
        }

        ExecutorSingleOperationResp result = authMechanism.addAuthToRequest(request, usesCachedParams);
        if (!Boolean.TRUE.equals(result.getSuccess())) {
            throw new Exception("Auth was not applied for test role " + testRole.getName() + ": " + result.getErrMsg());
        }
    }

    /**
     * Resolves a test role into the auth headers it adds, for callers that send requests through
     * their own client instead of an OriginalHttpRequest (e.g. the MCP tools sync job).
     */
    public static Map<String, String> fetchTestRoleAuthHeaders(String url, String roleId) throws Exception {
        OriginalHttpRequest request = new OriginalHttpRequest(url, "", "POST", "", new HashMap<>(), "HTTP/1.1");
        applyTestRoleAuth(request, roleId);

        Map<String, String> authHeaders = new HashMap<>();
        for (Map.Entry<String, List<String>> header : request.getHeaders().entrySet()) {
            List<String> values = header.getValue();
            if (values != null && !values.isEmpty() && !StringUtils.isEmpty(values.get(0))) {
                authHeaders.put(header.getKey(), values.get(0));
            }
        }
        return authHeaders;
    }
}
