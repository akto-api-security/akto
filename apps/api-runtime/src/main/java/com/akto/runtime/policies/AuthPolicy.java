package com.akto.runtime.policies;

import com.akto.dto.ApiInfo;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.akto.dto.type.KeyTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class AuthPolicy {
    public static final String AUTHORIZATION_HEADER_NAME = "authorization";
    private static final Logger logger = LoggerFactory.getLogger(AuthPolicy.class);

    public static boolean findAuthType(HttpResponseParams httpResponseParams, ApiInfo apiInfo, RuntimeFilter filter) {
        Set<Set<ApiInfo.AuthType>> allAuthTypesFound = apiInfo.getAllAuthTypesFound();
        if (allAuthTypesFound == null) allAuthTypesFound = new HashSet<>();

        // TODO: from custom api-token
        // NOTE: custom api-token can be in multiple headers. For example twitter api sends 2 headers access-key and access-token


        // find Authorization header
        Map<String, List<String>> headers = httpResponseParams.getRequestParams().getHeaders();
        List<String> authHeadersList = headers.getOrDefault(AUTHORIZATION_HEADER_NAME, new ArrayList<>());
        Set<ApiInfo.AuthType> authTypes = new HashSet<>();

        // find bearer or basic tokens in "Authorization" header
        if (authHeadersList.size() > 0) {
            String value = authHeadersList.get(0);
            value = value.trim();
            boolean twoFields = value.split(" ").length ==2;
            if (twoFields && value.startsWith("Bearer")) {
                authTypes.add(ApiInfo.AuthType.BEARER);
            } else if (twoFields && value.startsWith("Basic")) {
                authTypes.add(ApiInfo.AuthType.BASIC);
            } else {
                // todo: check jwt first and then this
                authTypes.add(ApiInfo.AuthType.AUTHORIZATION_HEADER);
            }
        }


        // Find JWT in header values
        boolean flag = false;
        for (String headerName: headers.keySet()) {
            if (flag) break;
            if (headerName.equals(AUTHORIZATION_HEADER_NAME)){
                continue;
            }
            List<String> headerValues =headers.getOrDefault(headerName,new ArrayList<>());
            for (String header: headerValues) {
                if (KeyTypes.isJWT(header)){
                    authTypes.add(ApiInfo.AuthType.JWT);
                    flag = true;
                    break;
                }
            }
        }

        boolean returnValue = false;
        if (authTypes.isEmpty()) {
            authTypes.add(ApiInfo.AuthType.UNAUTHENTICATED);
            returnValue = true;
        }


        allAuthTypesFound.add(authTypes);
        apiInfo.setAllAuthTypesFound(allAuthTypesFound);

        return returnValue;
    }
}
