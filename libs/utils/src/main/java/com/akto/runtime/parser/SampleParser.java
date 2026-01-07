package com.akto.runtime.parser;

import static com.akto.runtime.utils.Utils.printL;
import static com.akto.runtime.utils.Utils.printUrlDebugLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.math.NumberUtils;

import com.akto.dao.context.Context;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.TrafficProducerLog;
import com.akto.runtime.utils.Utils;
import com.akto.util.HttpRequestResponseUtils;
import com.akto.util.JSONUtils;
import com.akto.utils.GzipUtils;
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
        if(json == null || json.isEmpty()) {
            return false;
        }

        if ((json.startsWith("{") && !json.endsWith("}")) || (json.startsWith("[") && !json.endsWith("]"))) {
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
        payload = decodeIfGzipEncoding(payload, responseHeaders);
        /*
         * TODO: handle for other encodings if needed, 
         * Ref: https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Content-Encoding
         */
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
        List<String> parentMcpToolNames;
        try {
            parentMcpToolNames = (List<String>) json.get("parentMcpToolNames");
        } catch (Exception e) {
            parentMcpToolNames = new ArrayList<>();
        }
        HttpResponseParams httpResponseParams = new HttpResponseParams(
                type,statusCode, status, responseHeaders, payload, requestParams, time, accountId, isPending, source, message, sourceIP, destIP, direction, tags, parentMcpToolNames
        );
        if(tags != null && !tags.isEmpty()){
            String tagLog = "K8 Pod Tags: " + tags + " Host: " + requestHeaders.getOrDefault("host", new ArrayList<>()) + " Url: " + url;
            printL(tagLog);
            if ((Utils.printDebugHostLog(httpResponseParams) != null) || Utils.printDebugUrlLog(url)) {
                printUrlDebugLog(tagLog);
            }
            injectTagsInHeaders(requestParams, tags);
        }

        return httpResponseParams;

    }

    private static final String CONTENT_ENCODING_HEADER = "content-encoding";
    private static final String _GZIP = "gzip";

    private static List<String> getHeaderIgnoreCase(Map<String, List<String>> headers, String headerName) {
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(headerName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public static String decodeIfGzipEncoding(String payload, Map<String, List<String>> headers) {
        try {
            if (payload == null || payload.isEmpty())
                return payload;
            List<String> contentEncoding = getHeaderIgnoreCase(headers, CONTENT_ENCODING_HEADER);
            if (contentEncoding != null) {
                for (String encoding : contentEncoding) {
                    if (_GZIP.equalsIgnoreCase(encoding)) {
                        String unzippedPayload = GzipUtils.unzipString(payload);
                        if (unzippedPayload != null) {
                            return unzippedPayload;
                        }
                    }
                }
            }
        } catch (Exception e) {
            printL("Failed to decompress gzip payload: " + e.getMessage());
        }
        return payload;
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

    public static ModuleInfo parseHeartbeatMessage(String message) throws Exception {
        Map<String, Object> json = gson.fromJson(message, new com.google.gson.reflect.TypeToken<Map<String, Object>>(){}.getType());

        String daemonId = (String) json.getOrDefault("daemonId", null);
        String moduleTypeStr = (String) json.getOrDefault("moduleType", "K8S");
        String daemonPodName = (String) json.getOrDefault("daemonPodName", null);
        String podName = (String) json.getOrDefault("podName", null);
        String aktoDaemonSet = (String) json.getOrDefault("aktoDaemonSet", null);
        String nodeName = (String) json.getOrDefault("nodeName", null);
        int timestamp = Integer.parseInt(json.getOrDefault("timestamp", String.valueOf(Context.now())).toString());


        ModuleInfo moduleInfo = new ModuleInfo();
        moduleInfo.setId(daemonId); 
        moduleInfo.setModuleType(ModuleInfo.ModuleType.valueOf(moduleTypeStr));
        moduleInfo.setName(daemonPodName);
        moduleInfo.setLastHeartbeatReceived(timestamp);
        moduleInfo.setStartedTs(timestamp); 

        Map<String, Object> additionalData = new HashMap<>();
        additionalData.put("aktoDaemonSet", aktoDaemonSet);
        additionalData.put("nodeName", nodeName);
        additionalData.put("podName", podName);
        moduleInfo.setAdditionalData(additionalData);

        return moduleInfo;
    }

}