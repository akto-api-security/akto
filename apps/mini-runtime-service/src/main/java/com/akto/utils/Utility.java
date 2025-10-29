package com.akto.utils;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.OriginalHttpRequest;
import com.akto.runtime.utils.Utils;
import com.akto.util.HttpRequestResponseUtils;
import com.akto.util.JSONUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.akto.runtime.utils.Utils.printL;
import static com.akto.runtime.utils.Utils.printUrlDebugLog;

public class Utility {

    private static final Gson gson = new Gson();
    private final static ObjectMapper mapper = new ObjectMapper();

    public static HttpResponseParams parseData(IngestDataBatch payloadData) throws JsonProcessingException {

        String method = payloadData.getMethod();
        String url = payloadData.getPath();
        String type = payloadData.getType();
        Map<String, List<String>> requestHeaders = OriginalHttpRequest.buildHeadersMap(payloadData.getRequestHeaders());

        String rawRequestPayload = payloadData.getRequestPayload();
        String requestPayload = HttpRequestResponseUtils.rawToJsonString(rawRequestPayload,requestHeaders);


        String apiCollectionIdStr = (payloadData.getAkto_vxlan_id() != null ? payloadData.getAkto_vxlan_id() : "0");
        int apiCollectionId = 0;
        if (NumberUtils.isDigits(apiCollectionIdStr)) {
            apiCollectionId = NumberUtils.toInt(apiCollectionIdStr, 0);
        }

        HttpRequestParams requestParams = new HttpRequestParams(
                method,url,type, requestHeaders, requestPayload, apiCollectionId
        );

        int statusCode = Integer.parseInt(payloadData.getStatusCode());
        String status = payloadData.getStatus();
        Map<String,List<String>> responseHeaders = OriginalHttpRequest.buildHeadersMap(payloadData.getResponseHeaders());
        String payload = payloadData.getResponsePayload();
        payload = HttpRequestResponseUtils.rawToJsonString(payload, responseHeaders);
        payload = JSONUtils.parseIfJsonP(payload);
        int time = Integer.parseInt(payloadData.getTime());
        String accountId = payloadData.getAkto_account_id();
        String sourceIP = payloadData.getIp();
        String destIP = payloadData.getDestIp() != null ? payloadData.getDestIp() : "";
        String direction = payloadData.getDirection()!= null ? payloadData.getDirection() : "";


        String isPendingStr = payloadData.getIs_pending()!= null ? payloadData.getIs_pending() : "false";
        boolean isPending = !isPendingStr.toLowerCase().equals("false");
        String sourceStr = payloadData.getSource()!= null ? payloadData.getSource() : HttpResponseParams.Source.OTHER.name();
        HttpResponseParams.Source source = HttpResponseParams.Source.valueOf(sourceStr);

        // JSON string of K8 POD tags
        String tags = payloadData.getTag() != null ? payloadData.getTag() : "";
        List<String> parentMcpToolNames = payloadData.getParentMcpToolNames() != null ? payloadData.getParentMcpToolNames() : new ArrayList<>();
        HttpResponseParams httpResponseParams = new HttpResponseParams(
                type,statusCode, status, responseHeaders, payload, requestParams, time, accountId, isPending, source, mapper.writeValueAsString(payloadData), sourceIP, destIP, direction, tags, parentMcpToolNames
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
}
