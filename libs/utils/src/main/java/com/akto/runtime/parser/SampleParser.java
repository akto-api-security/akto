package com.akto.runtime.parser;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.math.NumberUtils;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.OriginalHttpRequest;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.HttpRequestResponseUtils;
import com.akto.util.JSONUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import static com.akto.util.HttpRequestResponseUtils.GRPC_CONTENT_TYPE;


public class SampleParser {
    
    private static final LoggerMaker loggerMaker = new LoggerMaker(SampleParser.class, LogDb.RUNTIME);
    private static int GRPC_DEBUG_COUNTER = 50;

    public static HttpResponseParams parseSampleMessage(String message) throws Exception {
                //convert java object to JSON format
        //convert java object to JSON format
        JSONObject jsonObject = JSON.parseObject(message);

        String method = jsonObject.getString("method");
        String url = jsonObject.getString("path");
        String type = jsonObject.getString("type");
        Map<String,List<String>> requestHeaders = OriginalHttpRequest.buildHeadersMap(jsonObject, "requestHeaders");

        String rawRequestPayload = jsonObject.getString("requestPayload");
        String requestPayload = HttpRequestResponseUtils.rawToJsonString(rawRequestPayload,requestHeaders);

        if (GRPC_DEBUG_COUNTER > 0) {
            String acceptableContentType = HttpRequestResponseUtils.getAcceptableContentType(requestHeaders);
            if (acceptableContentType != null && rawRequestPayload.length() > 0) {
                // only if request payload is of FORM_URL_ENCODED_CONTENT_TYPE we convert it to json
                if (acceptableContentType.equals(GRPC_CONTENT_TYPE)) {
                    loggerMaker.infoAndAddToDb("grpc kafka payload:" + message,LogDb.RUNTIME);
                    GRPC_DEBUG_COUNTER--;
                }
            }
        }

        String apiCollectionIdStr = jsonObject.getOrDefault("akto_vxlan_id", "0").toString();
        int apiCollectionId = 0;
        if (NumberUtils.isDigits(apiCollectionIdStr)) {
            apiCollectionId = NumberUtils.toInt(apiCollectionIdStr, 0);
        }

        HttpRequestParams requestParams = new HttpRequestParams(
                method,url,type, requestHeaders, requestPayload, apiCollectionId
        );

        int statusCode = jsonObject.getInteger("statusCode");
        String status = jsonObject.getString("status");
        Map<String,List<String>> responseHeaders = OriginalHttpRequest.buildHeadersMap(jsonObject, "responseHeaders");
        String payload = jsonObject.getString("responsePayload");
        payload = HttpRequestResponseUtils.rawToJsonString(payload, responseHeaders);
        payload = JSONUtils.parseIfJsonP(payload);
        int time = jsonObject.getInteger("time");
        String accountId = jsonObject.getString("akto_account_id");
        String sourceIP = jsonObject.getString("ip");
        String destIP = (String) jsonObject.getOrDefault("destIp", "");
        String direction = (String) jsonObject.getOrDefault("direction", "");

        String isPendingStr = (String) jsonObject.getOrDefault("is_pending", "false");
        boolean isPending = !isPendingStr.toLowerCase().equals("false");
        String sourceStr = (String) jsonObject.getOrDefault("source", HttpResponseParams.Source.OTHER.name());
        HttpResponseParams.Source source = HttpResponseParams.Source.valueOf(sourceStr);
        
        return new HttpResponseParams(
                type,statusCode, status, responseHeaders, payload, requestParams, time, accountId, isPending, source, message, sourceIP, destIP, direction
        );

    }

}