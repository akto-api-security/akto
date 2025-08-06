package com.akto.runtime.parser;

import static com.akto.runtime.utils.Utils.printL;
import static com.akto.runtime.utils.Utils.printUrlDebugLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.math.NumberUtils;

import com.akto.dao.context.Context;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.TrafficProducerLog;
import com.akto.runtime.utils.Utils;
import com.akto.util.HttpRequestResponseUtils;
import com.akto.util.JSONUtils;
import com.alibaba.fastjson2.JSON;
import com.google.gson.Gson;

public class SampleParser {
    
    private static final Gson gson = new Gson();

    private static void injectTagsInHeaders(HttpRequestParams httpRequestParams, String tagsJson){
        if(tagsJson == null || tagsJson.isEmpty()){
            return;
        }

        Map<String, String> tagsMap = gson.fromJson(tagsJson, Map.class);
        for (String tagName: tagsMap.keySet()){
            List<String> headerValues = new ArrayList<>();
            headerValues.add(tagsMap.get(tagName));
            httpRequestParams.getHeaders().put("x-akto-k8s-"+ tagName, headerValues);
        }
        if(Utils.printDebugUrlLog(httpRequestParams.getURL())) {
            printUrlDebugLog("Injecting K8 Pod Tags in Headers: " + tagsMap + " for URL: " + httpRequestParams.getURL());
        }
    }

    public static boolean isPossiblyIncompleteJson(String json) {
        if (json == null || json.trim().isEmpty()) return true;

        if (json.startsWith("{") && !json.endsWith("}") || json.endsWith("[") && !json.endsWith("]")) {
            return true;
        }
        return false;
    }

    public static HttpResponseParams parseSampleMessage(String message) throws Exception {
                //convert java object to JSON format
        Map<String, Object> json = JSON.parseObject(message);

        String method = (String) json.get("method");
        String url = (String) json.get("path");
        String type = (String) json.get("type");
        Map<String,List<String>> requestHeaders = OriginalHttpRequest.buildHeadersMap(json, "requestHeaders");

        String rawRequestPayload = (String) json.get("requestPayload");
        String requestPayload = HttpRequestResponseUtils.rawToJsonString(rawRequestPayload,requestHeaders);

        List<String> contentTypeList = requestHeaders.getOrDefault("content-type", new ArrayList<>());

        String contentTypeHeader = "application/json";
        if (contentTypeList.size() > 0) {
            contentTypeHeader = contentTypeList.get(0);
        }
        if (contentTypeHeader.equals("application/json") && isPossiblyIncompleteJson(requestPayload)) {
            return null;
        }

        String apiCollectionIdStr = json.getOrDefault("akto_vxlan_id", "0").toString();
        int apiCollectionId = 0;
        if (NumberUtils.isDigits(apiCollectionIdStr)) {
            apiCollectionId = NumberUtils.toInt(apiCollectionIdStr, 0);
        }

        HttpRequestParams requestParams = new HttpRequestParams(
                method,url,type, requestHeaders, requestPayload, apiCollectionId
        );

        int statusCode = Integer.parseInt(json.get("statusCode").toString());
        String status = (String) json.get("status");
        Map<String,List<String>> responseHeaders = OriginalHttpRequest.buildHeadersMap(json, "responseHeaders");
        String payload = (String) json.get("responsePayload");
        payload = HttpRequestResponseUtils.rawToJsonString(payload, responseHeaders);
        payload = JSONUtils.parseIfJsonP(payload);
        int time;
        try {
            time = Integer.parseInt(json.get("time").toString());
        } catch (Exception e) {
            time = Context.now();
        }
        String accountId = (String) json.get("akto_account_id");
        String sourceIP = (String) json.get("ip");
        String destIP = (String) json.getOrDefault("destIp", "");
        String direction = (String) json.getOrDefault("direction", "");

        String isPendingStr = (String) json.getOrDefault("is_pending", "false");
        boolean isPending = !isPendingStr.toLowerCase().equals("false");
        String sourceStr = (String) json.getOrDefault("source", HttpResponseParams.Source.OTHER.name());
        HttpResponseParams.Source source = HttpResponseParams.Source.valueOf(sourceStr);

        // JSON string of K8 POD tags
        String tags = (String) json.getOrDefault("tag", "");
        HttpResponseParams httpResponseParams = new HttpResponseParams(
                type,statusCode, status, responseHeaders, payload, requestParams, time, accountId, isPending, source, message, sourceIP, destIP, direction, tags
        );
        if(!tags.isEmpty()){
            String tagLog = "K8 Pod Tags: " + tags + " Host: " + requestHeaders.getOrDefault("host", new ArrayList<>()) + " Url: " + url;
            printL(tagLog);
            if ((Utils.printDebugHostLog(httpResponseParams) != null) || Utils.printDebugUrlLog(url)) {
                printUrlDebugLog(tagLog);
            }
            injectTagsInHeaders(requestParams, tags);
        }

        return httpResponseParams;

    }

    public static TrafficProducerLog parseLogMessage(String message) throws Exception {

        Map<String, Object> json = gson.fromJson(message, new com.google.gson.reflect.TypeToken<Map<String, Object>>(){}.getType());
        String logType = (String) json.getOrDefault("logType", "INFO");
        String source = (String) json.getOrDefault("source", "UNKNOWN");
        String logMessage = (String) json.getOrDefault("message", null);
        int time = Integer.parseInt(json.getOrDefault("time", Context.now()).toString());

        return new TrafficProducerLog(
                logMessage, source, logType, time);
    }

}