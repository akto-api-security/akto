package com.akto.hybrid_runtime;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.alibaba.fastjson2.JSON;

import java.util.*;

public class WebSocketEventProcessor {
    
    private static final LoggerMaker loggerMaker = new LoggerMaker(WebSocketEventProcessor.class, LogDb.RUNTIME);
    private static final String WEBSOCKET_TYPE = "WEBSOCKET";
    
    public static List<HttpResponseParams> processWebSocketMessage(Map<String, Object> jsonMap) {
        List<HttpResponseParams> responseParamsList = new ArrayList<>();
        
        try {
            List<WebSocketMessageParser.WebSocketEvent> events = WebSocketMessageParser.parseWebSocketEvents(jsonMap);
            
            if (events.isEmpty()) {
                loggerMaker.warn("No valid WebSocket events found in message");
                return responseParamsList;
            }
            
            String basePath = (String) jsonMap.get("path");
            Object apiCollectionIdObj = jsonMap.get("akto_vxlan_id");
            String accountId = (String) jsonMap.get("akto_account_id");
            
            int apiCollectionId = 0;
            if (apiCollectionIdObj != null) {
                try {
                    apiCollectionId = Integer.parseInt(apiCollectionIdObj.toString());
                } catch (NumberFormatException ignored) {
                }
            }
            
            Map<String, List<String>> requestHeaders = buildHeadersMap(jsonMap, "requestHeaders");
            Map<String, List<String>> responseHeaders = buildHeadersMap(jsonMap, "responseHeaders");
            
            int statusCode = 101;
            if (jsonMap.get("statusCode") != null) {
                try {
                    statusCode = Integer.parseInt(jsonMap.get("statusCode").toString());
                } catch (NumberFormatException ignored) {
                }
            }
            
            String status = (String) jsonMap.getOrDefault("status", "Switching Protocols");
            
            Set<String> processedTypes = new HashSet<>();
            
            for (WebSocketMessageParser.WebSocketEvent event : events) {
                if (processedTypes.contains(event.messageType)) {
                    continue;
                }
                processedTypes.add(event.messageType);
                
                String wsUrl = WebSocketMessageParser.buildWebSocketUrl(basePath, event.messageType);
                
                HttpRequestParams requestParams = new HttpRequestParams(
                    "GET",
                    wsUrl,
                    WEBSOCKET_TYPE,
                    requestHeaders,
                    "",
                    apiCollectionId
                );
                
                int time = (int) (System.currentTimeMillis() / 1000);
                HttpResponseParams responseParams = new HttpResponseParams(
                    WEBSOCKET_TYPE,
                    statusCode,
                    status,
                    responseHeaders,
                    event.originalEventJson,
                    requestParams,
                    time,
                    accountId,
                    false,
                    HttpResponseParams.Source.HAR,
                    JSON.toJSONString(jsonMap),
                    ""
                );
                
                responseParamsList.add(responseParams);
            }
            
        } catch (Exception e) {
            loggerMaker.error("Error processing WebSocket message: " + e.getMessage());
        }
        
        return responseParamsList;
    }
    
    private static Map<String, List<String>> buildHeadersMap(Map<String, Object> jsonMap, String headersKey) {
        Map<String, List<String>> headers = new HashMap<>();
        
        try {
            Object headersObj = jsonMap.get(headersKey);
            if (headersObj == null) {
                return headers;
            }
            
            if (headersObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> headersMap = (Map<String, Object>) headersObj;
                for (Map.Entry<String, Object> entry : headersMap.entrySet()) {
                    List<String> values = new ArrayList<>();
                    if (entry.getValue() instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> list = (List<String>) entry.getValue();
                        values = list;
                    } else if (entry.getValue() != null) {
                        values.add(entry.getValue().toString());
                    }
                    headers.put(entry.getKey().toLowerCase(), values);
                }
            } else if (headersObj instanceof String) {
                Map<String, Object> parsedHeaders = JSON.parseObject((String) headersObj);
                for (Map.Entry<String, Object> entry : parsedHeaders.entrySet()) {
                    List<String> values = new ArrayList<>();
                    if (entry.getValue() instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> list = (List<String>) entry.getValue();
                        values = list;
                    } else if (entry.getValue() != null) {
                        values.add(entry.getValue().toString());
                    }
                    headers.put(entry.getKey().toLowerCase(), values);
                }
            }
        } catch (Exception e) {
            loggerMaker.warn("Error building headers map: " + e.getMessage());
        }
        
        return headers;
    }
}
