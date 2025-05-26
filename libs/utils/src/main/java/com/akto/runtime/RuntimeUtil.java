package com.akto.runtime;

import com.akto.dto.HttpResponseParams;
import com.akto.dto.settings.DefaultPayload;
import com.akto.log.LoggerMaker;

import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class RuntimeUtil {
    private static final LoggerMaker loggerMaker = new LoggerMaker(RuntimeUtil.class);
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

    public static boolean isAlphanumericString(String s) {

        int intCount = 0;
        int charCount = 0;
        if (s.length() < 6) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {

            char c = s.charAt(i);
            if (Character.isDigit(c)) {
                intCount++;
            } else if (Character.isLetter(c)) {
                charCount++;
            }
        }
        return (intCount >= 3 && charCount >= 1);
    }

    public static boolean isValidVersionToken(String token){
        if(token == null || token.isEmpty()) return false;
        token = token.trim().toLowerCase();
        if(token.startsWith("v") && token.length() > 1 && token.length() < 4) {
            String versionString = token.substring(1, token.length());
            try {
                int version = Integer.parseInt(versionString);
                if (version > 0) {
                    return true;
                } 
            } catch (Exception e) {
                // TODO: handle exception
                return false;
            }
            return false;
        }
        return false;
    }


}
