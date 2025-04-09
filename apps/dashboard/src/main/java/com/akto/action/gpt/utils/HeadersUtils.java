package com.akto.action.gpt.utils;

import com.akto.dto.OriginalHttpRequest;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

public class HeadersUtils {

    public static final String AUTH_TOKEN = "%AUTH_TOKEN%";
    public static final String ACCESS_TOKEN = "%ACCESS_TOKEN%";
    public static final String COOKIE = "%COOKIE%";

    private static final LoggerMaker logger = new LoggerMaker(HeadersUtils.class, LogDb.DASHBOARD);;

    private static final Gson gson = new Gson();
    public static Pair<String, List<Pair<String, String>>> minifyHeaders(String sampleData){
        List<Pair<String, String>> headers = new ArrayList<>();
        try {
            Map<String, Object> json = gson.fromJson(sampleData, Map.class);
            Map<String, String> requestHeaders = convertMap(OriginalHttpRequest.buildHeadersMap(json, "requestHeaders"));
            for (Map.Entry<String, String> reqHeaderKeyValue : requestHeaders.entrySet()) {
                String key = reqHeaderKeyValue.getKey();
                if (StringUtils.containsAnyIgnoreCase(key, "Authorization")) {
                    headers.add(Pair.of(AUTH_TOKEN, reqHeaderKeyValue.getValue()));
                    requestHeaders.put(key, AUTH_TOKEN);
                } else if (StringUtils.containsAnyIgnoreCase(key, "access-token")) {
                    headers.add(Pair.of(ACCESS_TOKEN, reqHeaderKeyValue.getValue()));
                    requestHeaders.put(key, ACCESS_TOKEN);
                } else if (StringUtils.containsAnyIgnoreCase(key, "cookie")) {
                    headers.add(Pair.of(COOKIE, reqHeaderKeyValue.getValue()));
                    requestHeaders.put(key, COOKIE);
                }
            }
            String modifiedRequestHeaders = gson
                    .toJson(requestHeaders)
                    .replaceAll("\"", "\\\"");
            json.put("requestHeaders", modifiedRequestHeaders);
            return Pair.of(gson.toJson(json), headers);
        } catch (Exception e){
            logger.error("Error while minifying headers", e);
            return Pair.of(sampleData, headers);
        }
    }

    private static Map<String, String> convertMap(Map<String, List<String>> requestHeaders) {
        Map<String, String> result = new java.util.HashMap<>();
        for (Map.Entry<String, List<String>> entry : requestHeaders.entrySet()) {
            String key = entry.getKey();
            List<String> value = entry.getValue();
            if (value != null && !value.isEmpty()) {
                result.put(key, value.get(0));
            }
        }
        return result;
    }

    public static String replaceHeadersWithValues(Pair<String, List<Pair<String, String>>> response) {
        String curl = response.getLeft();
        for(Pair<String, String> header : response.getRight()) {
            curl = curl.replace(header.getLeft(), header.getRight());
        }
        return curl;
    }
}
