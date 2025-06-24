package com.akto.dao.common;

import com.akto.dto.*;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.akto.dto.type.KeyTypes;
import com.akto.util.Constants;
import com.akto.util.HttpRequestResponseUtils;
import com.akto.util.JSONUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mongodb.BasicDBObject;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


public class AuthPolicy {

    public static final String AUTHORIZATION_HEADER_NAME = "authorization";
    public static final String COOKIE_NAME = "cookie";
    private static final Logger logger = LoggerFactory.getLogger(AuthPolicy.class);

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

    public static List<String> authHeaders = new ArrayList<>();
    public static Map<String, String> headersMap = new HashMap<>();

    public static boolean findAuthType(HttpResponseParams httpResponseParams, ApiInfo apiInfo, RuntimeFilter filter, List<CustomAuthType> customAuthTypes, RawApi rawApi) {
        authHeaders = new ArrayList<>();
        headersMap = new HashMap<>();
        Set<Set<ApiInfo.AuthType>> allAuthTypesFound = apiInfo.getAllAuthTypesFound();
        if (allAuthTypesFound == null) allAuthTypesFound = new HashSet<>();

        // TODO: from custom api-token
        // NOTE: custom api-token can be in multiple headers. For example twitter api sends 2 headers access-key and access-token


        // find Authorization header
        Map<String, List<String>> headers = rawApi.fetchReqHeaders();
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
            if(customAuthTypePayloadKeys != null && !customAuthTypePayloadKeys.isEmpty() ){
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
                headersMap.put(cookieKey, cookieMap.get(cookieKey));
                authHeaders.add(cookieKey);
                flag = true;
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
                    headersMap.put(headerName, header);
                    authHeaders.add(headerName);
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

        for(int i=0; i<headersMap.size(); i++) {
            logger.info("HeaderMap  found: {}", headersMap.get(i), headersMap.get(i));
        }
        return returnValue;
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

    public static HttpResponseParams parseSampleData(String sample) throws Exception {

        //convert java object to JSON format

        JSONObject jsonObject = JSON.parseObject(sample);

        String method = jsonObject.getString("method");
        String url = jsonObject.getString("path");
        String type = jsonObject.getString("type");
        Map<String,List<String>> requestHeaders = OriginalHttpRequest.buildHeadersMap(jsonObject, "requestHeaders");

        String rawRequestPayload = jsonObject.getString("requestPayload");
        String requestPayload = null;
        Map<String,String> decryptedRequestPayload = HttpRequestResponseUtils.decryptRequestPayload(rawRequestPayload);
        if(!decryptedRequestPayload.isEmpty() && decryptedRequestPayload.get("type") != null){
            requestPayload = decryptedRequestPayload.get("payload");
            logger.info("decrypted request payload: " + requestPayload);
            requestHeaders.put(
                    Constants.AKTO_DECRYPT_HEADER,
                    Arrays.asList(decryptedRequestPayload.get("type"))
            );
        }else{
            requestPayload = HttpRequestResponseUtils.rawToJsonString(rawRequestPayload,requestHeaders);
        }
        String apiCollectionIdStr = jsonObject.getOrDefault("akto_vxlan_id", "0").toString();
        int apiCollectionId = 0;
        if (NumberUtils.isDigits(apiCollectionIdStr)) {
            apiCollectionId = NumberUtils.toInt(apiCollectionIdStr, 0);
        }

        HttpRequestParams requestParams = new HttpRequestParams(
                method,url,type, requestHeaders, requestPayload, apiCollectionId
        );

        int statusCode = jsonObject.getInteger("statusCode");
        String status = jsonObject.getString("status");
        Map<String,List<String>> responseHeaders = OriginalHttpRequest.buildHeadersMap(jsonObject, "responseHeaders");
        String payload = jsonObject.getString("responsePayload");
        payload = HttpRequestResponseUtils.rawToJsonString(payload, responseHeaders);
        payload = JSONUtils.parseIfJsonP(payload);
        int time = jsonObject.getInteger("time");
        String accountId = jsonObject.getString("akto_account_id");
        String sourceIP = jsonObject.getString("ip");
        String destIP = jsonObject.getString("destIp");
        String direction = jsonObject.getString("direction");

        String isPendingStr = (String) jsonObject.getOrDefault("is_pending", "false");
        boolean isPending = !isPendingStr.toLowerCase().equals("false");
        String sourceStr = (String) jsonObject.getOrDefault("source", HttpResponseParams.Source.OTHER.name());
        HttpResponseParams.Source source = HttpResponseParams.Source.valueOf(sourceStr);

        return new HttpResponseParams(
                type,statusCode, status, responseHeaders, payload, requestParams, time, accountId, isPending, source, sample, sourceIP, destIP, direction
        );
    }
}
