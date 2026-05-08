package com.akto.rest;

import com.akto.dto.HttpResponseParams;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RestMethodUtils {
    private static final LoggerMaker loggerMaker = new LoggerMaker(RestMethodUtils.class, LogDb.RUNTIME);
    private static final RestMethodUtils utils = new RestMethodUtils();

    public static final String METHOD = "method";
    public static final String NAME = "name";

    private RestMethodUtils() {
    }

    public static RestMethodUtils getUtils() {
        return utils;
    }

    private boolean isValidRestMethodPayload(Map jsonObject) {
        if (jsonObject == null) {
            return false;
        }

        // Check if "method" field exists
        Object methodObj = jsonObject.get(METHOD);
        if (methodObj == null) {
            return false;
        }

        // Check if "method" is a Map (object) and contains "name"
        if (methodObj instanceof Map) {
            Map methodMap = (Map) methodObj;
            Object nameObj = methodMap.get(NAME);
            // Verify "name" exists and is a String
            return nameObj instanceof String && !((String) nameObj).isEmpty();
        }

        return false;
    }

    public List<HttpResponseParams> parseRestMethodResponseParam(HttpResponseParams responseParams) {
        List<HttpResponseParams> responseParamsList = new ArrayList<>();

        String requestPayload = responseParams.getRequestParams().getPayload();
        if (requestPayload == null || requestPayload.isEmpty() || !requestPayload.contains(METHOD)) {
            return responseParamsList;
        }

        // Parse the request payload
        Map mapOfRequestPayload = null;
        try {
            JSONObject jsonObject = JSON.parseObject(requestPayload);
            Object obj = (Object) jsonObject;
            if (obj instanceof Map) {
                mapOfRequestPayload = (Map) obj;
            } else {
                return responseParamsList;
            }
        } catch (Exception e) {
            // Invalid JSON, return empty list
            return responseParamsList;
        }

        // Validate the payload structure
        if (!isValidRestMethodPayload(mapOfRequestPayload)) {
            return responseParamsList;
        }

        // Extract the method name
        try {
            Map methodMap = (Map) mapOfRequestPayload.get(METHOD);
            String methodName = (String) methodMap.get(NAME);

            if (methodName != null && !methodName.isEmpty()) {
                // Create a copy of the response params
                HttpResponseParams httpResponseParamsCopy = responseParams.copy();

                // Get the original URL and append the method name
                String originalUrl = responseParams.getRequestParams().getURL();
                String modifiedUrl = originalUrl;

                // Handle URL with query parameters
                if (originalUrl.contains("?")) {
                    String[] urlParts = originalUrl.split("\\?", 2);
                    String basePath = urlParts[0];
                    // Check if base path ends with slash
                    if (basePath.endsWith("/")) {
                        modifiedUrl = basePath + methodName + "?" + urlParts[1];
                    } else {
                        modifiedUrl = basePath + "/" + methodName + "?" + urlParts[1];
                    }
                } else {
                    // Check if URL ends with slash
                    if (originalUrl.endsWith("/")) {
                        modifiedUrl = originalUrl + methodName;
                    } else {
                        modifiedUrl = originalUrl + "/" + methodName;
                    }
                }

                httpResponseParamsCopy.requestParams.setUrl(modifiedUrl);
                responseParamsList.add(httpResponseParamsCopy);

                loggerMaker.infoAndAddToDb("Parsed REST method API: original URL=" + originalUrl +
                        ", modified URL=" + modifiedUrl + ", method name=" + methodName);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error while parsing REST method payload: " + e.getMessage());
            return responseParamsList;
        }

        return responseParamsList;
    }
}
