package com.akto.runtime.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.math.NumberUtils;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.graph.K8sDaemonsetGraphParams;
import com.akto.dto.graph.SvcToSvcGraphParams;
import com.akto.util.HttpRequestResponseUtils;
import com.akto.util.JSONUtils;
import com.google.gson.Gson;

public class SampleParser {
    
    private static final Gson gson = new Gson();


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
        
        String enableGraph = (String) json.getOrDefault("enable_graph", "false");
        SvcToSvcGraphParams graphParams = null; 
        if (enableGraph.equals("true")) {
            List<String> hostNameList = requestHeaders.getOrDefault("host", requestHeaders.getOrDefault(":authority", new ArrayList<>()));
            if (hostNameList != null && hostNameList.size()>0) {
                String processId = (String) json.get("process_id");
                String socketId = (String) json.get("socket_id");
                String daemonsetId = (String) json.get("daemonset_id");
                String hostname = hostNameList.get(0);
                if (hostname.charAt(0) >= 'a' && hostname.charAt(0) <= 'z') {
                    graphParams = new K8sDaemonsetGraphParams(hostNameList.get(0), processId, socketId, daemonsetId, direction);
                }   
            }
            
        }
        return new HttpResponseParams(
                type,statusCode, status, responseHeaders, payload, requestParams, time, accountId, isPending, source, message, sourceIP, destIP, direction, graphParams
        );

    }

}