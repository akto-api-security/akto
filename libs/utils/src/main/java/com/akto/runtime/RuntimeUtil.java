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
}
