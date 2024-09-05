package com.akto.runtime;

import com.akto.dto.HttpResponseParams;
import com.akto.dto.settings.DefaultPayload;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RuntimeUtil {
    private static final LoggerMaker loggerMaker = new LoggerMaker(RuntimeUtil.class);
    public static final String CONTENT_TYPE = "CONTENT-TYPE";

    public static boolean matchesDefaultPayload(HttpResponseParams httpResponseParams, Map<String, DefaultPayload> defaultPayloadMap) {
        try {
            Map<String, List<String>> reqHeaders = httpResponseParams.getRequestParams().getHeaders();
            List<String> host = reqHeaders.getOrDefault("host", new ArrayList<>());

            String testHost = "";
            if (host != null && !host.isEmpty() && host.get(0) != null) {
                testHost = host.get(0);
            } else {
                String urlStr = httpResponseParams.getRequestParams().getURL();
                URL url = new URL(urlStr);
                testHost = url.getHost();
            }

            testHost = Base64.getEncoder().encodeToString(testHost.getBytes());

            DefaultPayload defaultPayload = defaultPayloadMap.get(testHost);
            if (defaultPayload != null && defaultPayload.getRegexPattern().matcher(httpResponseParams.getPayload().replaceAll("\n", "")).matches()) {
                return true;
            }

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while filtering default payloads: " + e.getMessage(), LoggerMaker.LogDb.RUNTIME);
        }

        return false;
    }

    public static boolean looksLikeAttack(HttpResponseParams httpResponseParams) {
        try {
            Map<String, List<String>> reqHeaders = httpResponseParams.getRequestParams().getHeaders();
            List<String> host = reqHeaders.getOrDefault("host", new ArrayList<>());

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while filtering default payloads: " + e.getMessage(), LoggerMaker.LogDb.RUNTIME);
        }

        return false;
    }

    public static boolean hasSpecialCharacters(String input) {
        // Define the special characters
        String specialCharacters = "<>%/?#[]@!$&'()*+,;=";
        for (char c : input.toCharArray()) {
            if (specialCharacters.contains(Character.toString(c))) {
                return true;
            }
        }

        return false;
    }

    public static boolean isRedundantEndpoint(String url, List<String> discardedUrlList){
        StringJoiner joiner = new StringJoiner("|", ".*\\.(", ")(\\?.*)?");
        for (String extension : discardedUrlList) {
            if(extension.startsWith(RuntimeUtil.CONTENT_TYPE)){
                continue;
            }
            joiner.add(extension);
        }
        String regex = joiner.toString();

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(url);
        return matcher.matches();
    }

    private static boolean isInvalidContentType(String contentType){
        boolean res = false;
        if(contentType == null || contentType.length() == 0) return res;

        res = contentType.contains("javascript") || contentType.contains("png");
        return res;
    }

    private static boolean isBlankResponseBodyForGET(String method, String contentType, String matchContentType,
            String responseBody) {
        boolean res = true;
        if (contentType == null || contentType.length() == 0)
            return false;
        res &= contentType.contains(matchContentType);
        res &= "GET".equals(method.toUpperCase());

        /*
         * To be sure that the content type
         * header matches the actual payload.
         * 
         * We will need to add more type validation as needed.
         */
        if (matchContentType.contains("html")) {
            res &= responseBody.startsWith("<") && responseBody.endsWith(">");
        } else {
            res &= false;
        }
        return res;
    }


    public static boolean shouldIgnore(HttpResponseParams httpResponseParam, List<String> allowedRedundantList) {
        if(isRedundantEndpoint(httpResponseParam.getRequestParams().getURL(), allowedRedundantList)){
            return true;
        }
        List<String> contentTypeList = (List<String>) httpResponseParam.getRequestParams().getHeaders().getOrDefault("content-type", new ArrayList<>());
        String contentType = null;
        if(!contentTypeList.isEmpty()){
            contentType = contentTypeList.get(0);
        }
        if(isInvalidContentType(contentType)){
            return true;
        }

        try {
            List<String> responseContentTypeList = (List<String>) httpResponseParam.getHeaders().getOrDefault("content-type", new ArrayList<>());
            String allContentTypes = responseContentTypeList.toString();
            String method = httpResponseParam.getRequestParams().getMethod();
            String responseBody = httpResponseParam.getPayload();
            boolean ignore = false;
            for (String extension : allowedRedundantList) {
                if(extension.startsWith(CONTENT_TYPE)){
                    String matchContentType = extension.split(" ")[1];
                    if(isBlankResponseBodyForGET(method, allContentTypes, matchContentType, responseBody)){
                        ignore = true;
                        break;
                    }
                }
            }
            if(ignore){
                return true;
            }

        } catch(Exception e){
            loggerMaker.errorAndAddToDb(e, "Error while ignoring content-type redundant samples " + e.toString(), LogDb.RUNTIME);
        }

        return false;

    }
}
