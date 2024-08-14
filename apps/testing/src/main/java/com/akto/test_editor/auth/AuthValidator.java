package com.akto.test_editor.auth;

import com.akto.dto.CustomAuthType;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.Auth;
import com.akto.dto.test_editor.ExecutionResult;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.AuthParam;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.TestingRunResult;
import com.akto.test_editor.execution.Operations;
import com.akto.testing.ApiExecutor;
import com.akto.testing.Main;
import com.akto.util.CookieTransformer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AuthValidator {
    
    public static boolean validate(Auth auth, RawApi rawApi, AuthMechanism authMechanism, List<CustomAuthType> customAuthTypes) {

        if (auth == null) {
            return true;
        }

        List<String> headerKeys = getHeaders(auth, authMechanism, customAuthTypes);

        auth.setHeaders(headerKeys);

        if (headerKeys == null || headerKeys.size() == 0) {
            return false;
        }

        Map<String, List<String>> headers = rawApi.getRequest().getHeaders();
        boolean contains;
        boolean res;
        List<String> cookieList = headers.getOrDefault("cookie", new ArrayList<>());
        for (String header: headerKeys) {
            contains = headers.containsKey(header) || CookieTransformer.isKeyPresentInCookie(cookieList, header);
            res = auth.getAuthenticated() && contains;
            if (res) {
                return true;
            }
        }
        return false;
    }

    public static List<String> getHeaders(Auth auth, AuthMechanism authMechanism, List<CustomAuthType> customAuthTypes) {

        if (auth != null && auth.getHeaders() != null && auth.getHeaders().size() > 0) {
            return auth.getHeaders();
        }

        List<String> headerKeys = new ArrayList<>();

        if (authMechanism != null && authMechanism.getAuthParams() != null && authMechanism.getAuthParams().size() > 0) {
            for (AuthParam authParam: authMechanism.getAuthParams()) {
                String key = authParam.getKey();
                if (key == null) continue;
                headerKeys.add(key.toLowerCase());
            }
        }

        if (customAuthTypes != null) {
            for(CustomAuthType customAuthType: customAuthTypes) {
                headerKeys.addAll(customAuthType.getHeaderKeys());
            }
        }

        return headerKeys;

    }
    
    public static ExecutionResult checkAuth(Auth auth, RawApi rawApi, TestingRunConfig testingRunConfig, List<CustomAuthType> customAuthTypes, boolean debug, List<TestingRunResult.TestLog> testLogs) {

        Map<String, List<String>> headers = rawApi.getRequest().getHeaders();
        for (String header : auth.getHeaders()) {
            headers.remove(header);
        }

        for (CustomAuthType customAuthType : customAuthTypes) {
            List<String> customAuthTypeHeaderKeys = customAuthType.getHeaderKeys();
            for (String headerAuthKey: customAuthTypeHeaderKeys) {
                Operations.deleteHeader(rawApi, headerAuthKey);
            }
            List<String> customAuthTypePayloadKeys = customAuthType.getPayloadKeys();
            for (String payloadAuthKey: customAuthTypePayloadKeys) {
                Operations.deleteBodyParam(rawApi, payloadAuthKey);
            }
        }

        OriginalHttpResponse testResponse;
        try {
            testResponse = ApiExecutor.sendRequest(rawApi.getRequest(), true, testingRunConfig, debug, testLogs, Main.SKIP_SSRF_CHECK);
        } catch(Exception e) {
            return new ExecutionResult(false, "error running check auth " + e.getMessage(), rawApi.getRequest(), null);
        }
        
        return new ExecutionResult(true, "", rawApi.getRequest(), testResponse);

    }

}
