package com.akto.runtime.parser;

import static com.akto.runtime.utils.Utils.printL;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.math.NumberUtils;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.OriginalHttpRequest;
import com.akto.util.HttpRequestResponseUtils;
import com.akto.util.JSONUtils;
import com.google.gson.Gson;

public class SampleParser {
    
    private static final Gson gson = new Gson();
    private  static final List<String> headerValues = new ArrayList<>();

    private static void injectTagsInHeaders(HttpRequestParams httpRequestParams, String tagsJson){
        if(tagsJson == null || tagsJson.isEmpty()){
            return;
        }

        Map<String, String> tagsMap = gson.fromJson(tagsJson, Map.class);
        for (String tagName: tagsMap.keySet()){
            headerValues.clear();
            headerValues.add(tagsMap.get(tagName));
            httpRequestParams.getHeaders().put("x-akto-k8s-"+ tagName, headerValues);
        }
    }

    public static HttpResponseParams parseSampleMessage(String message) throws Exception {
                //convert java object to JSON format
        Map<String, Object> json = gson.fromJson(message, Map.class);

        String method = (String) json.get("method");
        String url = (String) json.get("path");
        String type = (String) json.get("type");
        Map<String,List<String>> requestHeaders = OriginalHttpRequest.buildHeadersMap(json, "requestHeaders");

        String rawRequestPayload = (String) json.get("requestPayload");
        String requestPayload = HttpRequestResponseUtils.rawToJsonString(rawRequestPayload,requestHeaders);



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
        int time = Integer.parseInt(json.get("time").toString());
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
        if(!tags.isEmpty()){
            printL("K8 Pod Tags" + tags);
            injectTagsInHeaders(requestParams, tags);
        }

        return new HttpResponseParams(
                type,statusCode, status, responseHeaders, payload, requestParams, time, accountId, isPending, source, message, sourceIP, destIP, direction, tags
        );

    }

}