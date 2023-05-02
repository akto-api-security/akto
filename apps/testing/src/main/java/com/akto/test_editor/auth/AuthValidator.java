package com.akto.test_editor.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.akto.dao.AuthMechanismsDao;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.Auth;
import com.akto.dto.test_editor.ExecutionResult;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.AuthParam;
import com.akto.testing.ApiExecutor;
import com.mongodb.client.model.Filters;

public class AuthValidator {
    
    public static boolean validate(Auth auth, RawApi rawApi) {

        if (auth == null) {
            return true;
        }

        List<String> headerKeys = getHeaders(auth);

        auth.setHeaders(headerKeys);

        if (headerKeys == null || headerKeys.size() == 0) {
            return false;
        }

        Map<String, List<String>> headers = rawApi.getRequest().getHeaders();
        boolean contains;
        boolean res;
        for (String header: headerKeys) {
            contains = headers.containsKey(header);
            res = auth.getAuthenticated() && contains;
            if (!res) {
                return res;
            }
        }
        return true;
    }

    public static List<String> getHeaders(Auth auth) {

        if (auth != null && auth.getHeaders() != null && auth.getHeaders().size() > 0) {
            return auth.getHeaders();
        }

        List<String> headerKeys = new ArrayList<>();

        AuthMechanism authMechanism = AuthMechanismsDao.instance.findOne(Filters.eq("type", "HARDCODED"));
        if (authMechanism == null || authMechanism.getAuthParams() == null || authMechanism.getAuthParams().size() == 0) {
            return null;
        }

        for (AuthParam authParam: authMechanism.getAuthParams()) {
            headerKeys.add(authParam.getKey());
        }

        return headerKeys;

    }
    
    public static ExecutionResult checkAuth(Auth auth, RawApi rawApi) {

        Map<String, List<String>> headers = rawApi.getRequest().getHeaders();
        for (String header : auth.getHeaders()) {
            headers.remove(header);
        }

        OriginalHttpResponse testResponse;
        try {
            testResponse = ApiExecutor.sendRequest(rawApi.getRequest(), true);
        } catch(Exception e) {
            return new ExecutionResult(false, "error running check auth " + e.getMessage(), rawApi.getRequest(), null);
        }
        
        return new ExecutionResult(true, "", rawApi.getRequest(), testResponse);

    }

}
