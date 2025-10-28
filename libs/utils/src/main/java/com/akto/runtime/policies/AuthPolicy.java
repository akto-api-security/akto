package com.akto.runtime.policies;

import com.akto.dto.ApiInfo;
import com.akto.dto.CustomAuthType;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.akto.dto.type.KeyTypes;
import com.akto.util.JSONUtils;

import com.mongodb.BasicDBObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.regex.Pattern;

import java.util.*;

public class AuthPolicy {

    public static final String AUTHORIZATION_HEADER_NAME = "authorization";
    public static final String COOKIE_NAME = "cookie";
    private static final Logger logger = LoggerFactory.getLogger(AuthPolicy.class);
    
    // Pre-compiled regex patterns for performance
    private static final Pattern API_KEY_PATTERN = Pattern.compile(".*(apikey|passkey).*");
    private static final Pattern MTLS_PATTERN = Pattern
            .compile(".*(clientcert|sslcert|clientdn|sslclientsdn|sslclientverify|forwardedclientcert).*");

    private static final Pattern SESSION_TOKEN_PATTERN = Pattern.compile("(?i)(session[_\\-.]?(id|key|token)?)");
    
    private static boolean isApiKeyHeader(String headerName) {
        // Matches variations: x-api-key, x_api_key, api_key, api-key, apiKey,
        // x-pass-key
        String normalized = headerName.toLowerCase().replaceAll("[_-]", "");
        return API_KEY_PATTERN.matcher(normalized).matches();
    }

    private static boolean isMtlsHeader(String headerName, String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        String normalized = headerName.toLowerCase().replaceAll("[_-]", "");
        // Check for common mTLS/client certificate headers
        return MTLS_PATTERN.matcher(normalized).matches();
    }

    private static boolean isSessionToken(String cookieName) {
        // Check for session token
        return SESSION_TOKEN_PATTERN.matcher(cookieName).find();
    }

    private static List<ApiInfo.AuthType> findBearerBasicAuth(String header, String value){
        value = value.trim();
        boolean twoFields = value.split(" ").length == 2;
        if (twoFields && value.substring(0, Math.min(6, value.length())).equalsIgnoreCase("bearer")) {
            return Collections.singletonList(ApiInfo.AuthType.BEARER);
        } else if (twoFields && value.substring(0, Math.min(5, value.length())).equalsIgnoreCase("basic")) {
            return Collections.singletonList(ApiInfo.AuthType.BASIC);
        } else if (header.equals(AUTHORIZATION_HEADER_NAME) || header.equals("auth")) {
            // todo: check jwt first and then this
            return Collections.singletonList(ApiInfo.AuthType.AUTHORIZATION_HEADER);
        }
        return new ArrayList<>();
    }

    public static Map<String,String> parseCookie(List<String> cookieList){
        Map<String,String> cookieMap = new HashMap<>();
        if(cookieList==null)return cookieMap;
        for (String cookieValues : cookieList) {
            String[] cookies = cookieValues.split(";");
            for (String cookie : cookies) {
                cookie=cookie.trim();
                String[] cookieFields = cookie.split("=");
                boolean twoCookieFields = cookieFields.length == 2;
                if (twoCookieFields) {
                    if(!cookieMap.containsKey(cookieFields[0])){
                        cookieMap.put(cookieFields[0], cookieFields[1]);
                    }
                }
            }
        }
        return cookieMap;
    }

    public static boolean findAuthType(HttpResponseParams httpResponseParams, ApiInfo apiInfo, RuntimeFilter filter, List<CustomAuthType> customAuthTypes) {
        Set<Set<ApiInfo.AuthType>> allAuthTypesFound = apiInfo.getAllAuthTypesFound();
        if (allAuthTypesFound == null) allAuthTypesFound = new HashSet<>();

        // TODO: from custom api-token
        // NOTE: custom api-token can be in multiple headers. For example twitter api sends 2 headers access-key and access-token


        // find Authorization header
        Map<String, List<String>> headers = httpResponseParams.getRequestParams().getHeaders();
        List<String> cookieList = headers.getOrDefault(COOKIE_NAME, new ArrayList<>());
        Map<String,String> cookieMap = parseCookie(cookieList);
        Set<ApiInfo.AuthType> authTypes = new HashSet<>();

        for (CustomAuthType customAuthType : customAuthTypes) {

            Set<String> headerAndCookieKeys = new HashSet<>();
            headerAndCookieKeys.addAll(headers.keySet());
            headerAndCookieKeys.addAll(cookieMap.keySet());

            // Find custom auth type in header and cookie
            List<String> customAuthTypeHeaderKeys = customAuthType.getHeaderKeys();
            if (!headerAndCookieKeys.isEmpty() && !customAuthTypeHeaderKeys.isEmpty() && headerAndCookieKeys.containsAll(customAuthTypeHeaderKeys)) {
                authTypes.add(ApiInfo.AuthType.CUSTOM);
                break;
            }

            // Find custom auth type in payload
            List<String> customAuthTypePayloadKeys = customAuthType.getPayloadKeys();
            if(!customAuthTypePayloadKeys.isEmpty() ){
                BasicDBObject flattenedPayload = null;
                try{
                    BasicDBObject basicDBObject = BasicDBObject.parse(httpResponseParams.getRequestParams().getPayload());
                    flattenedPayload = JSONUtils.flattenWithDots(basicDBObject);
                } catch (Exception e){
                }
                if(flattenedPayload != null && !flattenedPayload.isEmpty() && flattenedPayload.keySet().containsAll(customAuthTypePayloadKeys)){
                    authTypes.add(ApiInfo.AuthType.CUSTOM);
                    break;
                }
            }
        }

        // find bearer or basic tokens in any header
        for (String header : headers.keySet()) {
            List<String> headerValues = headers.getOrDefault(header, new ArrayList<>());
            if (!headerValues.isEmpty()) {
                for (String value : headerValues) {
                    authTypes.addAll(findBearerBasicAuth(header, value));

                    // Check for API_KEY
                    if (isApiKeyHeader(header)) {
                        authTypes.add(ApiInfo.AuthType.API_KEY);
                    }

                    // Check for MTLS
                    if (isMtlsHeader(header, value)) {
                        authTypes.add(ApiInfo.AuthType.MTLS);
                    }

                    if(isSessionToken(header)){
                        authTypes.add(ApiInfo.AuthType.SESSION_TOKEN);
                    }
                }
            } else {
                authTypes.addAll(findBearerBasicAuth(header, ""));
            }
        }

        boolean flag = false;
        for (String cookieKey : cookieMap.keySet()) {
            // Find bearer or basic token in cookie values
            authTypes.addAll(findBearerBasicAuth(cookieKey, cookieMap.get(cookieKey)));
            // Find JWT in cookie values
            if (KeyTypes.isJWT(cookieMap.get(cookieKey))) {
                authTypes.add(ApiInfo.AuthType.JWT);
                flag = true;
            }

            if(isSessionToken(cookieKey)){
                authTypes.add(ApiInfo.AuthType.SESSION_TOKEN);
            }
        }

        // Find JWT in header values
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
